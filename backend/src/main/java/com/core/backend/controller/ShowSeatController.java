
package com.core.backend.controller;

import com.core.backend.cache.SeatAvailabilityCache;
import com.core.backend.dto.ShowSeatDto;
import com.core.backend.service.ShowSeatService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shows")
public class ShowSeatController {

    private final ShowSeatService showSeatService;
    private final SeatAvailabilityCache seatAvailabilityCache;

    public ShowSeatController(
            ShowSeatService showSeatService,
            SeatAvailabilityCache seatAvailabilityCache) {
        this.showSeatService = showSeatService;
        this.seatAvailabilityCache = seatAvailabilityCache;
    }

    @GetMapping("/{showId}/seats")
    public List<ShowSeatDto> getSeats(
            @PathVariable Long showId) {
        return showSeatService.getSeats(showId);
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> getCacheStats() {
        return seatAvailabilityCache.getStats();
    }
}
