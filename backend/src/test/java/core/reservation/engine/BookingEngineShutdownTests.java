package core.reservation.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BookingEngineShutdownTests {

    @Test
    void queuedRequestsAreDrainedOnClose() throws Exception {
        BookingOperation operation = (userId, seatId) ->
                new BookingResult(
                        true, "BOOKING_CONFIRMED", "Success", seatId);

        BookingEngine engine = new BookingEngine(operation, 1, 10);

        var first = engine.submitRequest(1L, 101L);
        var second = engine.submitRequest(1L, 102L);
        var third = engine.submitRequest(1L, 103L);

        engine.close();

        assertTrue(first.get(3, TimeUnit.SECONDS).success());
        assertTrue(second.get(3, TimeUnit.SECONDS).success());
        assertTrue(third.get(3, TimeUnit.SECONDS).success());
    }

    @Test
    void requestsSubmittedAfterCloseAreRejected() {
        BookingOperation operation = (userId, seatId) ->
                new BookingResult(
                        true, "BOOKING_CONFIRMED", "Success", seatId);

        BookingEngine engine = new BookingEngine(operation, 1, 10);
        engine.close();

        BookingResult result = engine.submitRequest(1L, 101L).join();

        assertFalse(result.success());
        assertEquals("BOOKING_FAILED", result.code());
    }

    @Test
    void closeWaitsForTheRequestInProgress() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        BookingOperation operation = (userId, seatId) -> {
            started.countDown();

            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            return new BookingResult(
                    true, "BOOKING_CONFIRMED", "Success", seatId);
        };

        BookingEngine engine = new BookingEngine(operation, 1, 10);

        var future = engine.submitRequest(1L, 101L);

        assertTrue(started.await(2, TimeUnit.SECONDS));

        Thread closer = new Thread(engine::close);
        closer.start();

        // The close must not return while the worker is busy.
        closer.join(500);
        assertTrue(closer.isAlive());

        release.countDown();
        closer.join(3000);

        assertFalse(closer.isAlive());
        assertTrue(future.get(3, TimeUnit.SECONDS).success());
    }
}
