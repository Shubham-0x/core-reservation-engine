package com.core.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "show_seats")
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    public ShowSeat() {
    }

    public Long getId() {
        return id;
    }

    public Long getShowId() {
        return showId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public SeatStatus getStatus() {
        return status;
    }
public void setStatus(SeatStatus status) {
    this.status = status;
}
}

