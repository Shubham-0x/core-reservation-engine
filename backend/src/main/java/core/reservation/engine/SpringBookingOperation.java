
package core.reservation.engine;

import com.core.backend.entity.Booking;
import com.core.backend.service.BookingService;
import org.springframework.stereotype.Component;

@Component
public class SpringBookingOperation implements BookingOperation {

    private final BookingService bookingService;

    public SpringBookingOperation(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public BookingResult bookSeat(Long userId, Long showSeatId) {
        try {
            Booking booking =
                    bookingService.bookSeat(userId, showSeatId);

            return new BookingResult(
                    true,
                    "BOOKING_CONFIRMED",
                    "Seat booked successfully.",
                    booking.getId()
            );

        } catch (IllegalStateException ex) {
            return new BookingResult(
                    false,
                    "SEAT_ALREADY_BOOKED",
                    ex.getMessage(),
                    null
            );

        } catch (IllegalArgumentException ex) {
            return new BookingResult(
                    false,
                    "BOOKING_FAILED",
                    ex.getMessage(),
                    null
            );
        }
    }
}
