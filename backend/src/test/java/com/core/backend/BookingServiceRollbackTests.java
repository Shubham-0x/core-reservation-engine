package com.core.backend;

import com.core.backend.service.BookingService;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingServiceRollbackTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @Test
    void duplicateBookingLeavesExactlyOneConfirmedBooking() {
        TestData data = createTestData();

        try {
            bookingService.bookSeat(data.userId(), data.showSeatId());

            assertThrows(IllegalStateException.class, () ->
                    bookingService.bookSeat(data.userId(), data.showSeatId()));

            // The failed second booking must not create a row
            // and must not change the seat.
            assertEquals(1, countBookings(data.showSeatId(), "CONFIRMED"));
            assertEquals("BOOKED", getSeatStatus(data.showSeatId()));
        } finally {
            cleanup(data);
        }
    }

    @Test
    void cancelByWrongUserLeavesBookingUnchanged() {
        TestData data = createTestData();

        try {
            var booking = bookingService.bookSeat(
                    data.userId(), data.showSeatId());

            assertThrows(IllegalArgumentException.class, () ->
                    bookingService.cancelBooking(
                            booking.getId(), data.userId() + 100000L));

            assertEquals(1, countBookings(data.showSeatId(), "CONFIRMED"));
            assertEquals(0, countBookings(data.showSeatId(), "CANCELLED"));
            assertEquals("BOOKED", getSeatStatus(data.showSeatId()));
        } finally {
            cleanup(data);
        }
    }

    @Test
    void cancelMissingBookingChangesNothing() {
        TestData data = createTestData();

        try {
            assertThrows(IllegalArgumentException.class, () ->
                    bookingService.cancelBooking(999999999L, data.userId()));

            assertEquals(0, countBookings(data.showSeatId(), "CONFIRMED"));
            assertEquals("AVAILABLE", getSeatStatus(data.showSeatId()));
        } finally {
            cleanup(data);
        }
    }

    private TestData createTestData() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        Long userId = insertAndGetId(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                "Rollback Test",
                "rollback-" + unique + "@example.com"
        );

        Long eventId = insertAndGetId(
                "INSERT INTO events (name, description) VALUES (?, ?)",
                "Test Event " + unique,
                "Temporary rollback test event"
        );

        Long showId = insertAndGetId(
                "INSERT INTO shows (event_id, show_time) VALUES (?, ?)",
                eventId,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        );

        Long seatId = insertAndGetId(
                "INSERT INTO seats (seat_number) VALUES (?)",
                "T" + unique
        );

        Long showSeatId = insertAndGetId(
                "INSERT INTO show_seats (show_id, seat_id, status) " +
                "VALUES (?, ?, 'AVAILABLE')",
                showId,
                seatId
        );

        return new TestData(userId, eventId, showId, seatId, showSeatId);
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

        return keyHolder.getKey().longValue();
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
