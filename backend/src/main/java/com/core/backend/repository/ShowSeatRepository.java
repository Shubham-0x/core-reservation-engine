package com.core.backend.repository;

import com.core.backend.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;



public interface ShowSeatRepository
        extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowId(Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ShowSeat s WHERE s.id = :id")
Optional<ShowSeat> findByIdForUpdate(
        @Param("id") Long id);
}

