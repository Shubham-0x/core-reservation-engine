-- CORE Reservation Engine: demo seed data.
--
-- Run once, after schema.sql, on a fresh database:
--
--   mysql -u root -p core_db < backend/src/main/resources/db/seed.sql
--
-- This creates two events, one show per event, twelve seats
-- (A1-A12) and a show_seats row for every seat in each show.
-- All seats start AVAILABLE. No users or bookings are seeded;
-- register a user through the frontend or POST /api/auth/register.

USE core_db;

INSERT INTO events (id, name, description) VALUES
    (1, 'Live Concert - Demo Night',
        'A demo concert used to try the full reservation journey.'),
    (2, 'Movie Premiere - Demo Screening',
        'A demo movie screening used to try the full reservation journey.')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description);

INSERT INTO shows (id, event_id, show_time) VALUES
    (1, 1, '2026-12-05 19:00:00'),
    (2, 2, '2026-12-06 18:30:00')
ON DUPLICATE KEY UPDATE
    event_id = VALUES(event_id),
    show_time = VALUES(show_time);

INSERT INTO seats (id, seat_number) VALUES
    (1, 'A1'), (2, 'A2'), (3, 'A3'), (4, 'A4'),
    (5, 'A5'), (6, 'A6'), (7, 'A7'), (8, 'A8'),
    (9, 'A9'), (10, 'A10'), (11, 'A11'), (12, 'A12')
ON DUPLICATE KEY UPDATE
    seat_number = VALUES(seat_number);

INSERT INTO show_seats (id, show_id, seat_id, status) VALUES
    (1, 1, 1, 'AVAILABLE'), (2, 1, 2, 'AVAILABLE'),
    (3, 1, 3, 'AVAILABLE'), (4, 1, 4, 'AVAILABLE'),
    (5, 1, 5, 'AVAILABLE'), (6, 1, 6, 'AVAILABLE'),
    (7, 1, 7, 'AVAILABLE'), (8, 1, 8, 'AVAILABLE'),
    (9, 1, 9, 'AVAILABLE'), (10, 1, 10, 'AVAILABLE'),
    (11, 1, 11, 'AVAILABLE'), (12, 1, 12, 'AVAILABLE'),
    (13, 2, 1, 'AVAILABLE'), (14, 2, 2, 'AVAILABLE'),
    (15, 2, 3, 'AVAILABLE'), (16, 2, 4, 'AVAILABLE'),
    (17, 2, 5, 'AVAILABLE'), (18, 2, 6, 'AVAILABLE'),
    (19, 2, 7, 'AVAILABLE'), (20, 2, 8, 'AVAILABLE'),
    (21, 2, 9, 'AVAILABLE'), (22, 2, 10, 'AVAILABLE'),
    (23, 2, 11, 'AVAILABLE'), (24, 2, 12, 'AVAILABLE')
ON DUPLICATE KEY UPDATE
    status = VALUES(status);
