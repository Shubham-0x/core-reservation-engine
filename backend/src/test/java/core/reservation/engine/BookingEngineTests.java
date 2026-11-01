
package core.reservation.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BookingEngineTests {

    @Test
    void processesRequestsInFifoOrder() throws Exception {
        List<Long> processed = Collections.synchronizedList(
                new ArrayList<>());

        BookingOperation operation = (userId, seatId) -> {
            processed.add(seatId);
            return new BookingResult(
                    true, "BOOKING_CONFIRMED", "Success", seatId);
        };

        BookingEngine engine = new BookingEngine(operation, 1, 10);

        try {
            var first = engine.submitRequest(1L, 101L);
            var second = engine.submitRequest(1L, 102L);
            var third = engine.submitRequest(1L, 103L);

            assertTrue(first.get(3, TimeUnit.SECONDS).success());
            assertTrue(second.get(3, TimeUnit.SECONDS).success());
            assertTrue(third.get(3, TimeUnit.SECONDS).success());

            assertEquals(List.of(101L, 102L, 103L), processed);
        } finally {
            engine.close();
        }
    }

    @Test
    void rejectsRequestsWhenQueueIsFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        BookingOperation operation = (userId, seatId) -> {
            started.countDown();

            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test interrupted", ex);
            }

            return new BookingResult(
                    true, "BOOKING_CONFIRMED", "Success", seatId);
        };

        BookingEngine engine = new BookingEngine(operation, 1, 1);

        try {
            var first = engine.submitRequest(1L, 101L);

            assertTrue(started.await(2, TimeUnit.SECONDS));

            var second = engine.submitRequest(1L, 102L);
            var third = engine.submitRequest(1L, 103L);

            BookingResult rejected = third.get(1, TimeUnit.SECONDS);

            assertFalse(rejected.success());
            assertEquals("BOOKING_FAILED", rejected.code());

            release.countDown();

            assertTrue(first.get(3, TimeUnit.SECONDS).success());
            assertTrue(second.get(3, TimeUnit.SECONDS).success());
        } finally {
            release.countDown();
            engine.close();
        }
    }

    @Test
    void rejectsInvalidBookingRequests() {
        BookingOperation operation = (userId, seatId) ->
                new BookingResult(
                        true, "BOOKING_CONFIRMED", "Success", seatId);

        BookingEngine engine = new BookingEngine(operation, 1, 10);

        try {
            BookingResult result = engine
                    .submitRequest(0L, 101L)
                    .join();

            assertFalse(result.success());
            assertEquals("BOOKING_FAILED", result.code());
        } finally {
            engine.close();
        }
    }

    @Test
    void recordsBookingStatistics() throws Exception {
        BookingOperation operation = (userId, seatId) ->
                new BookingResult(
                        true, "BOOKING_CONFIRMED", "Success", seatId);

        BookingEngine engine = new BookingEngine(operation, 1, 10);

        try {
            engine.submitRequest(1L, 101L)
                    .get(3, TimeUnit.SECONDS);

            engine.submitRequest(0L, 102L)
                    .get(3, TimeUnit.SECONDS);

            BookingStats stats = engine.getStats();

            assertEquals(2, stats.totalRequests());
            assertEquals(1, stats.successfulBookings());
            assertEquals(1, stats.rejectedBookings());
        } finally {
            engine.close();
        }
    }
}
