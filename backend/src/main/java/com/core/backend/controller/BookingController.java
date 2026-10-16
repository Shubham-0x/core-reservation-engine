
package com.core.backend.controller;

import com.core.backend.entity.Booking;
import com.core.backend.entity.User;
import com.core.backend.repository.UserRepository;
import com.core.backend.service.BookingService;
import core.reservation.engine.BookingEngine;
import core.reservation.engine.BookingResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingEngine bookingEngine;
    private final UserRepository userRepository;
    private final BookingService bookingService;

    public BookingController(
            BookingEngine bookingEngine,
            UserRepository userRepository,
            BookingService bookingService) {
        this.bookingEngine = bookingEngine;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
    }

    public record BookingRequest(Long showSeatId) {}

    @PostMapping
    public ResponseEntity<?> bookSeat(
            @RequestBody BookingRequest request,
            Authentication authentication) {

        if (request.showSeatId() == null
                || request.showSeatId() <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "A valid showSeatId is required.")
            );
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found"));

        try {
            BookingResult result = bookingEngine
                    .submitRequest(user.getId(), request.showSeatId())
                    .get(10, TimeUnit.SECONDS);

            if (result.success()) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(result);
            }

            HttpStatus status =
                    "SEAT_ALREADY_BOOKED".equals(result.code())
                            ? HttpStatus.CONFLICT
                            : HttpStatus.BAD_REQUEST;

            return ResponseEntity.status(status).body(result);

        } catch (TimeoutException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "message",
                            "Booking is still processing. Check availability before retrying."
                    ));

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "message",
                            "Booking request interrupted."
                    ));

        } catch (ExecutionException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Booking processing failed."
                    ));
        }
    }

    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found"));

        try {
            Booking booking = bookingService.cancelBooking(
                    bookingId, user.getId());

            return ResponseEntity.ok(booking);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", ex.getMessage()));

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", ex.getMessage()));
        }
    }
}
