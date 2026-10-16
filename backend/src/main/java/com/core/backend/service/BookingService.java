
package com.core.backend.service;

import com.core.backend.cache.SeatAvailabilityCache;
import com.core.backend.entity.Booking;
import com.core.backend.entity.BookingStatus;
import com.core.backend.entity.SeatStatus;
import com.core.backend.entity.ShowSeat;
import com.core.backend.repository.BookingRepository;
import com.core.backend.repository.ShowSeatRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BookingService {

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final SeatAvailabilityCache seatAvailabilityCache;

    public BookingService(
            ShowSeatRepository showSeatRepository,
            BookingRepository bookingRepository,
            SeatAvailabilityCache seatAvailabilityCache) {

        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.seatAvailabilityCache = seatAvailabilityCache;
    }

    @Transactional
    public Booking bookSeat(Long userId, Long showSeatId) {

        ShowSeat seat = showSeatRepository
                .findByIdForUpdate(showSeatId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Seat not found"));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is already booked");
        }

        seat.setStatus(SeatStatus.BOOKED);

        Booking booking = bookingRepository.save(
                new Booking(userId, showSeatId)
        );

        invalidateCacheAfterCommit(seat.getShowId());

        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {

        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
    .orElseThrow(() -> new IllegalArgumentException(
        "Booking not found or you are not authorized to cancel it"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Booking is already cancelled");
        }

        ShowSeat seat = showSeatRepository
                .findByIdForUpdate(booking.getShowSeatId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Seat not found"));

        booking.setStatus(BookingStatus.CANCELLED);
        seat.setStatus(SeatStatus.AVAILABLE);

        Booking updatedBooking = bookingRepository.save(booking);

        invalidateCacheAfterCommit(seat.getShowId());

        return updatedBooking;
    }

    private void invalidateCacheAfterCommit(Long showId) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        seatAvailabilityCache.invalidate(showId);
                    }
                }
        );
    }
}
