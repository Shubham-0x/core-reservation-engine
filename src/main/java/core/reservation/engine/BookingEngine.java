package core.reservation.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bounded FIFO queue and fixed worker pool.
 * FIFO applies to dequeue order; concurrent workers may finish out of order.
 */
public final class BookingEngine implements AutoCloseable {
    private final ArrayBlockingQueue<QueuedRequest> queue;
    private final List<Thread> workers = new ArrayList<>();
    private final BookingOperation bookingOperation;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final LongAdder total = new LongAdder();
    private final LongAdder successful = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder duplicates = new LongAdder();

    public BookingEngine(BookingOperation operation, int workerCount, int queueCapacity) {
        this.bookingOperation = Objects.requireNonNull(operation, "operation");
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be positive");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.queue = new ArrayBlockingQueue<>(queueCapacity, true);
        for (int i = 1; i <= workerCount; i++) {
            Thread worker = new Thread(this::workerLoop, "booking-worker-" + i);
            workers.add(worker);
            worker.start();
        }
    }

    /** Queue-full and shutdown submissions are rejected immediately as BOOKING_FAILED. */
    public CompletableFuture<BookingResult> submitRequest(Long userId, Long showSeatId) {
        total.increment();
        BookingRequest request;
        try {
            request = new BookingRequest(userId, showSeatId);
        } catch (IllegalArgumentException ex) {
            rejected.increment();
            return CompletableFuture.completedFuture(
                    new BookingResult(false, "BOOKING_FAILED", ex.getMessage(), null));
        }

        CompletableFuture<BookingResult> future = new CompletableFuture<>();
        if (!accepting.get()) {
            rejected.increment();
            future.complete(new BookingResult(false, "BOOKING_FAILED", "Engine is shutting down.", null));
            return future;
        }

        if (!queue.offer(new QueuedRequest(request, future))) {
            rejected.increment();
            future.complete(new BookingResult(false, "BOOKING_FAILED",
                    "Request queue is full; request was not processed.", null));
        }
        return future;
    }

    private void workerLoop() {
        while (accepting.get() || !queue.isEmpty()) {
            try {
                QueuedRequest item = queue.poll(100, TimeUnit.MILLISECONDS);
                if (item != null) process(item);
            } catch (InterruptedException ex) {
                if (!accepting.get()) return;
                // Ignore an incidental interrupt while the engine is still running.
            }
        }
    }

    private void process(QueuedRequest item) {
        BookingResult result;
        try {
            result = bookingOperation.bookSeat(item.request.userId(), item.request.showSeatId());
            if (result == null) {
                result = new BookingResult(false, "BOOKING_FAILED", "Booking operation returned no result.", null);
            }
        } catch (RuntimeException ex) {
            result = new BookingResult(false, "BOOKING_FAILED",
                    "Booking operation failed: " + ex.getClass().getSimpleName(), null);
        }

        if (result.success()) {
            successful.increment();
        } else {
            rejected.increment();
            if ("SEAT_ALREADY_BOOKED".equals(result.code())) duplicates.increment();
        }
        item.future.complete(result);
    }

    public BookingStats getStats() {
        return new BookingStats(total.sum(), successful.sum(), rejected.sum(), duplicates.sum());
    }

    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void close() {
        accepting.set(false);
        boolean interrupted = false;
        for (Thread worker : workers) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private record QueuedRequest(BookingRequest request, CompletableFuture<BookingResult> future) {}
}
