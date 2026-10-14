package core.reservation.engine;

public interface BookingOperation {
    BookingResult bookSeat(Long userId, Long showSeatId);
}
