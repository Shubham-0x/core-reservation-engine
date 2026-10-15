package com.core.backend.controller;

import com.core.backend.entity.ShowSeat;
import com.core.backend.repository.ShowSeatRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class ShowSeatController {

    private final ShowSeatRepository showSeatRepository;

    public ShowSeatController(
            ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    @GetMapping("/{showId}/seats")
    public List<ShowSeat> getSeats(
            @PathVariable Long showId) {
        return showSeatRepository.findByShowId(showId);
    }
}
