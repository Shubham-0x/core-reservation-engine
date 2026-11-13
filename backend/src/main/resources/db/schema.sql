-- CORE Reservation Engine: database schema for MySQL 8.
--
-- Run once on a fresh database. This file creates the tables only;
-- it does not delete or change existing data.
--
--   mysql -u root -p < backend/src/main/resources/db/schema.sql
--
-- The tables below are the ones the JPA entities and the
-- integration tests already assume:
--   users, events, shows, seats, show_seats, bookings
-- Column names, lengths and nullability follow the entity classes.
-- `bookings.booked_at` has a database default because the entity
-- marks it insertable = false, so the database must supply it.
--
-- Duplicate protection: there is deliberately no unique index on
-- bookings here. A unique index on show_seat_id alone would be
-- wrong, because a seat is booked again after a cancellation and
-- the cancelled booking must stay recorded. In this project a
-- duplicate active booking is prevented by the pessimistic write
-- lock on the show_seats row inside a database transaction
-- (BookingService.bookSeat), not by a database unique constraint.
-- Do not claim a unique constraint exists unless one is added
-- and verified against a running database.

CREATE DATABASE IF NOT EXISTS core_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE core_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    password_hash VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS shows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    show_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_shows_event_id (event_id),
    CONSTRAINT fk_shows_event
        FOREIGN KEY (event_id) REFERENCES events (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS seats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seat_number VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS show_seats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_show_seats_show_id (show_id),
    CONSTRAINT fk_show_seats_show
        FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_show_seats_seat
        FOREIGN KEY (seat_id) REFERENCES seats (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    show_seat_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    booked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bookings_user_id (user_id),
    KEY idx_bookings_show_seat_id (show_seat_id),
    CONSTRAINT fk_bookings_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_show_seat
        FOREIGN KEY (show_seat_id) REFERENCES show_seats (id)
) ENGINE = InnoDB;
