package com.core.backend.repository;

import com.core.backend.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository
        extends JpaRepository<Seat, Long> {
}
