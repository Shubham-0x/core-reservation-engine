
package com.core.backend.service;

import com.core.backend.cache.SeatAvailabilityCache;
import com.core.backend.dto.ShowSeatDto;
import com.core.backend.entity.ShowSeat;
import com.core.backend.repository.ShowSeatRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShowSeatService {

    private final ShowSeatRepository showSeatRepository;
    private final SeatAvailabilityCache seatAvailabilityCache;

    public ShowSeatService(
            ShowSeatRepository showSeatRepository,
            SeatAvailabilityCache seatAvailabilityCache) {
        this.showSeatRepository = showSeatRepository;
        this.seatAvailabilityCache = seatAvailabilityCache;
    }

    @Transactional(readOnly = true)
    public List<ShowSeatDto> getSeats(Long showId) {

        List<ShowSeatDto> cachedSeats =
                seatAvailabilityCache.get(showId);

        if (cachedSeats != null) {
            return cachedSeats;
        }

        List<ShowSeatDto> seats = showSeatRepository
                .findByShowId(showId)
                .stream()
                .map(this::toDto)
                .toList();

        seatAvailabilityCache.put(showId, seats);

        return seats;
    }

    private ShowSeatDto toDto(ShowSeat seat) {
        return new ShowSeatDto(
                seat.getId(),
                seat.getShowId(),
                seat.getSeatId(),
                seat.getStatus()
        );
    }
}
