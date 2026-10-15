package com.core.backend.service;

import com.core.backend.entity.Booking;
import com.core.backend.entity.BookingStatus;
import com.core.backend.entity.SeatStatus;
import com.core.backend.entity.ShowSeat;
import com.core.backend.repository.BookingRepository;
import com.core.backend.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;

    public BookingService(
            ShowSeatRepository showSeatRepository,
            BookingRepository bookingRepository) {
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking bookSeat(Long userId, Long showSeatId) {

        ShowSeat seat = showSeatRepository
                .findByIdForUpdate(showSeatId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Seat not found"));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Seat is already booked");
        }

        seat.setStatus(SeatStatus.BOOKED);

        return bookingRepository.save(
                new Booking(userId, showSeatId));
    }

    @Transactional
public Booking cancelBooking(Long bookingId) {

    Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Booking not found"));

    if (booking.getStatus() == BookingStatus.CANCELLED) {
        throw new IllegalStateException(
                "Booking is already cancelled");
    }

    ShowSeat seat = showSeatRepository
            .findByIdForUpdate(booking.getShowSeatId())
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Seat not found"));

    booking.setStatus(BookingStatus.CANCELLED);
    seat.setStatus(SeatStatus.AVAILABLE);

    return bookingRepository.save(booking);
}
}

