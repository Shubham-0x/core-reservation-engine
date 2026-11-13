package core.reservation.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BookingEngineStressTests {

    @Test
    void oneHundredConcurrentRequestsBookTenSeatsExactlyOnce() throws Exception {
        Set<Long> bookedSeats = ConcurrentHashMap.newKeySet();

        BookingOperation operation = (userId, seatId) -> {
            if (bookedSeats.add(seatId)) {
                return new BookingResult(
                        true, "BOOKING_CONFIRMED", "Success", seatId);
            }

            return new BookingResult(
                    false, "SEAT_ALREADY_BOOKED",
                    "Seat is already booked.", null);
        };

        BookingEngine engine = new BookingEngine(operation, 4, 200);

        try {
            List<CompletableFuture<BookingResult>> futures = new ArrayList<>();

            for (int i = 0; i < 100; i++) {
                long seatId = 101L + (i % 10);
                futures.add(engine.submitRequest((long) i + 1, seatId));
            }

            long successful = 0;
            long duplicates = 0;

            for (CompletableFuture<BookingResult> future : futures) {
                BookingResult result = future.get(20, TimeUnit.SECONDS);

                if (result.success()) {
                    successful++;
                } else if ("SEAT_ALREADY_BOOKED".equals(result.code())) {
                    duplicates++;
                }
            }

            assertEquals(10, successful);
            assertEquals(90, duplicates);
            assertEquals(10, bookedSeats.size());
        } finally {
            engine.close();
        }
    }

    @Test
    void queueSaturationRejectsOverflowImmediately() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        BookingOperation operation = (userId, seatId) -> {
            started.countDown();

            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            return new BookingResult(
                    true, "BOOKING_CONFIRMED", "Success", seatId);
        };

        // One worker busy, five places in the queue.
        BookingEngine engine = new BookingEngine(operation, 1, 5);

        try {
            var first = engine.submitRequest(1L, 101L);
            assertTrue(started.await(2, TimeUnit.SECONDS));

            List<CompletableFuture<BookingResult>> futures = new ArrayList<>();

            for (int i = 0; i < 14; i++) {
                futures.add(engine.submitRequest(2L, 200L + i));
            }

            // Five fit in the queue, the rest must be rejected at once,
            // without waiting for the worker to free up.
            long rejected = futures.stream()
                    .filter(CompletableFuture::isDone)
                    .map(CompletableFuture::join)
                    .filter(result -> !result.success())
                    .count();

            assertEquals(9, rejected);

            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).success());
        } finally {
            release.countDown();
            engine.close();
        }
    }
}
