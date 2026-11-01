
package com.core.backend;

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

    @Test
    void onlyOneRequestCanBookTheSameSeatConcurrently() throws Exception {

        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Long userId = null;
        Long eventId = null;
        Long showId = null;
        Long seatId = null;
        Long showSeatId = null;

        try {
            userId = insertAndGetId(
                    "INSERT INTO users (name, email) VALUES (?, ?)",
                    "Concurrency Test",
                    "concurrency-" + unique + "@example.com"
            );

            eventId = insertAndGetId(
                    "INSERT INTO events (name, description) VALUES (?, ?)",
                    "Concurrency Test " + unique,
                    "Temporary integration test event"
            );

            showId = insertAndGetId(
                    "INSERT INTO shows (event_id, show_time) VALUES (?, ?)",
                    eventId,
                    Timestamp.valueOf(LocalDateTime.now().plusDays(1))
            );

            // Keep the seat number short to fit the database column.
            seatId = insertAndGetId(
                    "INSERT INTO seats (seat_number) VALUES (?)",
                    "T" + unique
            );

            showSeatId = insertAndGetId(
                    "INSERT INTO show_seats (show_id, seat_id, status) VALUES (?, ?, 'AVAILABLE')",
                    showId,
                    seatId
            );

            List<CompletableFuture<BookingResult>> futures = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(bookingEngine.submitRequest(userId, showSeatId));
            }

            List<BookingResult> results = new ArrayList<>();

            for (CompletableFuture<BookingResult> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }

            long successful = results.stream()
                    .filter(BookingResult::success)
                    .count();

            long duplicates = results.stream()
                    .filter(result -> "SEAT_ALREADY_BOOKED".equals(result.code()))
                    .count();

            assertEquals(1, successful, "Exactly one request should book the seat");
            assertEquals(19, duplicates, "The other 19 requests should be rejected as duplicates");

            Integer activeBookings = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bookings WHERE show_seat_id = ? AND status = 'CONFIRMED'",
                    Integer.class,
                    showSeatId
            );

            assertEquals(1, activeBookings, "Only one active booking should exist");

            String seatStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM show_seats WHERE id = ?",
                    String.class,
                    showSeatId
            );

            assertEquals("BOOKED", seatStatus);

        } finally {
            if (showSeatId != null) {
                jdbcTemplate.update("DELETE FROM bookings WHERE show_seat_id = ?", showSeatId);
                jdbcTemplate.update("DELETE FROM show_seats WHERE id = ?", showSeatId);
            }

            if (seatId != null) {
                jdbcTemplate.update("DELETE FROM seats WHERE id = ?", seatId);
            }

            if (showId != null) {
                jdbcTemplate.update("DELETE FROM shows WHERE id = ?", showId);
            }

            if (eventId != null) {
                jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
            }

            if (userId != null) {
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            }
        }
    }

    private Long insertAndGetId(String sql, Object... parameters) {

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }

            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("Database did not return a generated ID");
        }

        return key.longValue();
    }
}
