package com.core.backend.repository;

import com.core.backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {
                Optional<Booking> findByIdAndUserId(Long id, Long userId);
}