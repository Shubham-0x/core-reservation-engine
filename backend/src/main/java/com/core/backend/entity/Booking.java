package com.core.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "show_seat_id", nullable = false)
    private Long showSeatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "booked_at", insertable = false,
            updatable = false)
    private LocalDateTime bookedAt;

    public Booking() {
    }

    public Booking(Long userId, Long showSeatId) {
        this.userId = userId;
        this.showSeatId = showSeatId;
        this.status = BookingStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getShowSeatId() {
        return showSeatId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}