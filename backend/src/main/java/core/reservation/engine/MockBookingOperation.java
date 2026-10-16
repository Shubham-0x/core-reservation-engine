package core.reservation.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe in-memory mock for local testing only. */
public final class MockBookingOperation implements BookingOperation {
    private final Set<Long> validUserIds;
    private final Set<Long> validShowSeatIds;
    private final ConcurrentHashMap<Long, Long> confirmedBookingByShowSeat = new ConcurrentHashMap<>();
    private final AtomicLong nextBookingId = new AtomicLong(1);

    public MockBookingOperation(Set<Long> validUserIds, Set<Long> validShowSeatIds) {
        this.validUserIds = Set.copyOf(validUserIds);
        this.validShowSeatIds = Set.copyOf(validShowSeatIds);
    }

    @Override
    public BookingResult bookSeat(Long userId, Long showSeatId) {
        if (!validUserIds.contains(userId))
            return new BookingResult(false, "USER_NOT_FOUND", "User " + userId + " does not exist in the mock.", null);
        if (!validShowSeatIds.contains(showSeatId))
            return new BookingResult(false, "SEAT_NOT_FOUND", "Show-seat " + showSeatId + " does not exist in the mock.", null);

        // Atomic claim: only one concurrent request can claim a given show-seat.
        long candidateId = nextBookingId.getAndIncrement();
        Long existing = confirmedBookingByShowSeat.putIfAbsent(showSeatId, candidateId);
        if (existing != null)
            return new BookingResult(false, "SEAT_ALREADY_BOOKED",
                    "Show-seat " + showSeatId + " already has a confirmed booking.", null);

        return new BookingResult(true, "BOOKING_CONFIRMED",
                "Booking confirmed for show-seat " + showSeatId + ".", candidateId);
    }

    public int confirmedSeatCount() {
        return confirmedBookingByShowSeat.size();
    }
}
