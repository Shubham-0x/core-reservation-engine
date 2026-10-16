package core.reservation.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/** Demo: 100 requests compete for 10 show-seats. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Set<Long> users = LongStream.rangeClosed(1, 100).boxed().collect(Collectors.toSet());
        Set<Long> seats = LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toSet());
        MockBookingOperation mock = new MockBookingOperation(users, seats);
        List<CompletableFuture<BookingResult>> futures = new ArrayList<>();

        try (BookingEngine engine = new BookingEngine(mock, 4, 200)) {
            for (long userId = 1; userId <= 100; userId++) {
                long showSeatId = ((userId - 1) % 10) + 1;
                futures.add(engine.submitRequest(userId, showSeatId));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            long confirmed = futures.stream().map(CompletableFuture::join).filter(BookingResult::success).count();
            long duplicateAttempts = futures.stream().map(CompletableFuture::join)
                    .filter(r -> "SEAT_ALREADY_BOOKED".equals(r.code())).count();

            System.out.println("=== Concurrent Booking Engine Demo ===");
            System.out.println("Requests submitted: " + futures.size());
            System.out.println("Successful bookings: " + confirmed);
            System.out.println("Duplicate attempts rejected: " + duplicateAttempts);
            System.out.println("Confirmed seats in mock: " + mock.confirmedSeatCount());
            System.out.println("Engine stats: " + engine.getStats());
            System.out.println("Expected: 10 successful bookings, 90 duplicate rejections.");
        }
    }
}
