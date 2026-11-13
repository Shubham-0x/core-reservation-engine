package com.core.backend.repository;

import com.core.backend.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository
        extends JpaRepository<Show, Long> {

    List<Show> findByEventIdOrderByShowTimeAsc(Long eventId);
}
