package core.reservation.engine;

public record BookingRequest(Long userId, Long showSeatId) {
    public BookingRequest {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (showSeatId == null || showSeatId <= 0) throw new IllegalArgumentException("showSeatId must be positive");
    }
}
