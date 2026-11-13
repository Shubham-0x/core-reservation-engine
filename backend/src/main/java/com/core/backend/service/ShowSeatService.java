
package com.core.backend.service;

import com.core.backend.cache.SeatAvailabilityCache;
import com.core.backend.dto.ShowSeatDto;
import com.core.backend.entity.ShowSeat;
import com.core.backend.repository.SeatRepository;
import com.core.backend.repository.ShowSeatRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShowSeatService {

    private final ShowSeatRepository showSeatRepository;
    private final SeatRepository seatRepository;
    private final SeatAvailabilityCache seatAvailabilityCache;

    public ShowSeatService(
            ShowSeatRepository showSeatRepository,
            SeatRepository seatRepository,
            SeatAvailabilityCache seatAvailabilityCache) {
        this.showSeatRepository = showSeatRepository;
        this.seatRepository = seatRepository;
        this.seatAvailabilityCache = seatAvailabilityCache;
    }

    @Transactional(readOnly = true)
    public List<ShowSeatDto> getSeats(Long showId) {

        List<ShowSeatDto> cachedSeats =
                seatAvailabilityCache.get(showId);

        if (cachedSeats != null) {
            return cachedSeats;
        }

        // Record the cache generation before reading the database.
        long generation = seatAvailabilityCache.getGeneration();

        // Seat numbers live in the seats table, so load them
        // once for this show instead of once per seat.
        Map<Long, String> seatNumbers = seatRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        seat -> seat.getId(),
                        seat -> seat.getSeatNumber()));

        List<ShowSeatDto> seats = showSeatRepository
                .findByShowId(showId)
                .stream()
                .map(seat -> toDto(seat, seatNumbers))
                .toList();

        // Do not cache the result if an invalidation happened
        // while the database query was running.
        seatAvailabilityCache.putIfGenerationMatches(
                showId,
                generation,
                seats
        );

        return seats;
    }

    private ShowSeatDto toDto(ShowSeat seat, Map<Long, String> seatNumbers) {
        return new ShowSeatDto(
                seat.getId(),
                seat.getShowId(),
                seat.getSeatId(),
                seat.getStatus(),
                seatNumbers.getOrDefault(
                        seat.getSeatId(), "S" + seat.getSeatId())
        );
    }
}
