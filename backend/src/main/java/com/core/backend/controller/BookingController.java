package com.core.backend.controller;

import com.core.backend.entity.Booking;
import com.core.backend.service.BookingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public Booking bookSeat(@RequestBody BookingRequest request) {
        return bookingService.bookSeat(
                request.userId(),
                request.showSeatId());
    }

    public record BookingRequest(
            Long userId,
            Long showSeatId) {
    }

    @PutMapping("/{bookingId}/cancel")
public Booking cancelBooking(
        @PathVariable Long bookingId) {
    return bookingService.cancelBooking(bookingId);
}
}