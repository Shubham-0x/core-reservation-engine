package core.reservation.engine;

public record BookingResult(boolean success, String code, String message, Long bookingId) {
    public BookingResult {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (message == null) message = "";
        if (success && bookingId == null) throw new IllegalArgumentException("Successful booking needs bookingId");
        if (!success && bookingId != null) throw new IllegalArgumentException("Rejected booking must have null bookingId");
    }
}
