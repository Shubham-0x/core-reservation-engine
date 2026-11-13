package com.core.backend;

import com.core.backend.service.BookingService;
import com.core.backend.service.ShowSeatService;
import core.reservation.engine.BookingEngine;

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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CacheConsistencyIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingEngine bookingEngine;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ShowSeatService showSeatService;

    @Test
    void seatsAreFreshAfterBookingAndCancellation() throws Exception {
        TestData data = createTestData();

        try {
            // First read fills the cache with AVAILABLE.
            assertEquals("AVAILABLE",
                    showSeatService.getSeats(data.showId()).get(0).status().name());

            var result = bookingEngine
                    .submitRequest(data.userId(), data.showSeatId())
                    .get(20, TimeUnit.SECONDS);

            assertTrue(result.success());

            // The booking must have invalidated the cache,
            // so the next read shows BOOKED, not the stale AVAILABLE.
            assertEquals("BOOKED",
                    showSeatService.getSeats(data.showId()).get(0).status().name());

            bookingService.cancelBooking(result.bookingId(), data.userId());

            // The cancellation must also invalidate the cache,
            // so the next read shows AVAILABLE again.
            assertEquals("AVAILABLE",
                    showSeatService.getSeats(data.showId()).get(0).status().name());
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
                "Cache Test",
                "cache-" + unique + "@example.com"
        );

        Long eventId = insertAndGetId(
                "INSERT INTO events (name, description) VALUES (?, ?)",
                "Test Event " + unique,
                "Temporary cache test event"
        );

        Long showId = insertAndGetId(
                "INSERT INTO shows (event_id, show_time) VALUES (?, ?)",
                eventId,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        );

        Long seatId = insertAndGetId(
                "INSERT INTO seats (seat_number) VALUES (?)",
                "C" + unique
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
