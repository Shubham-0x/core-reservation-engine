
package com.core.backend;

import com.core.backend.service.BookingService;
import core.reservation.engine.BookingEngine;
import core.reservation.engine.BookingResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingConcurrencyIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingEngine bookingEngine;

    @Autowired
    private BookingService bookingService;

    @Test
    void onlyOneRequestCanBookTheSameSeatConcurrently() throws Exception {

        TestData data = createTestData();

        try {
            List<CompletableFuture<BookingResult>> futures = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(bookingEngine.submitRequest(
                        data.userId(), data.showSeatId()));
            }

            List<BookingResult> results = new ArrayList<>();

            for (CompletableFuture<BookingResult> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }

            long successful = results.stream()
                    .filter(BookingResult::success)
                    .count();

            long duplicates = results.stream()
                    .filter(result ->
                            "SEAT_ALREADY_BOOKED".equals(result.code()))
                    .count();

            assertEquals(1, successful);
            assertEquals(19, duplicates);

            assertEquals(1, countBookings(
                    data.showSeatId(), "CONFIRMED"));

            assertEquals("BOOKED", getSeatStatus(data.showSeatId()));

        } finally {
            cleanup(data);
        }
    }

    @Test
    void cancelledSeatCanBeBookedAgain() throws Exception {

        TestData data = createTestData();

        try {
            // First booking.
            BookingResult firstResult = bookingEngine
                    .submitRequest(data.userId(), data.showSeatId())
                    .get(20, TimeUnit.SECONDS);

            assertTrue(firstResult.success(),
                    "The first booking should succeed");

            assertEquals(1, countBookings(
                    data.showSeatId(), "CONFIRMED"));

            assertEquals("BOOKED", getSeatStatus(data.showSeatId()));

            // Retrieve the first booking ID.
            Long firstBookingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM bookings " +
                    "WHERE show_seat_id = ? AND status = 'CONFIRMED'",
                    Long.class,
                    data.showSeatId()
            );

            assertNotNull(firstBookingId);

            // Cancel the first booking through the actual service.
            bookingService.cancelBooking(
                    firstBookingId, data.userId());

            assertEquals(0, countBookings(
                    data.showSeatId(), "CONFIRMED"));

            assertEquals(1, countBookings(
                    data.showSeatId(), "CANCELLED"));

            assertEquals("AVAILABLE", getSeatStatus(data.showSeatId()));

            // Book the same seat again.
            BookingResult secondResult = bookingEngine
                    .submitRequest(data.userId(), data.showSeatId())
                    .get(20, TimeUnit.SECONDS);

            assertTrue(secondResult.success(),
                    "The seat should be available for rebooking");

            assertEquals(1, countBookings(
                    data.showSeatId(), "CONFIRMED"));

            assertEquals(1, countBookings(
                    data.showSeatId(), "CANCELLED"));

            assertEquals("BOOKED", getSeatStatus(data.showSeatId()));

        } finally {
            cleanup(data);
        }
    }

    private TestData createTestData() {

        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        Long userId = null;
        Long eventId = null;
        Long showId = null;
        Long seatId = null;
        Long showSeatId = null;

        try {
            userId = insertAndGetId(
                    "INSERT INTO users (name, email) VALUES (?, ?)",
                    "Integration Test",
                    "integration-" + unique + "@example.com"
            );

            eventId = insertAndGetId(
                    "INSERT INTO events (name, description) VALUES (?, ?)",
                    "Test Event " + unique,
                    "Temporary integration test event"
            );

            showId = insertAndGetId(
                    "INSERT INTO shows (event_id, show_time) VALUES (?, ?)",
                    eventId,
                    Timestamp.valueOf(LocalDateTime.now().plusDays(1))
            );

            seatId = insertAndGetId(
                    "INSERT INTO seats (seat_number) VALUES (?)",
                    "T" + unique
            );

            showSeatId = insertAndGetId(
                    "INSERT INTO show_seats (show_id, seat_id, status) " +
                    "VALUES (?, ?, 'AVAILABLE')",
                    showId,
                    seatId
            );

            return new TestData(
                    userId, eventId, showId, seatId, showSeatId);

        } catch (RuntimeException ex) {
            cleanup(new TestData(
                    userId, eventId, showId, seatId, showSeatId));
            throw ex;
        }
    }

    private Long insertAndGetId(String sql, Object... parameters) {

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql, Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }

            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Database did not return a generated ID");
        }

        return key.longValue();
    }

    private int countBookings(Long showSeatId, String status) {

        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings " +
                "WHERE show_seat_id = ? AND status = ?",
                Integer.class,
                showSeatId,
                status
        );
    }

    private String getSeatStatus(Long showSeatId) {

        return jdbcTemplate.queryForObject(
                "SELECT status FROM show_seats WHERE id = ?",
                String.class,
                showSeatId
        );
    }

    private void cleanup(TestData data) {

        if (data.showSeatId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM bookings WHERE show_seat_id = ?",
                    data.showSeatId());

            jdbcTemplate.update(
                    "DELETE FROM show_seats WHERE id = ?",
                    data.showSeatId());
        }

        if (data.seatId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM seats WHERE id = ?",
                    data.seatId());
        }

        if (data.showId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM shows WHERE id = ?",
                    data.showId());
        }

        if (data.eventId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM events WHERE id = ?",
                    data.eventId());
        }

        if (data.userId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    data.userId());
        }
    }

    private record TestData(
            Long userId,
            Long eventId,
            Long showId,
            Long seatId,
            Long showSeatId) {
    }
}
