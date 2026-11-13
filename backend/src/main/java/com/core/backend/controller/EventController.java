
package com.core.backend.controller;

import com.core.backend.entity.Event;
import com.core.backend.entity.Show;
import com.core.backend.repository.EventRepository;
import com.core.backend.repository.ShowRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;

    public EventController(
            EventRepository eventRepository,
            ShowRepository showRepository) {
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
    }

    @GetMapping
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    @GetMapping("/{eventId}/shows")
    public List<Show> getShowsForEvent(@PathVariable Long eventId) {
        return showRepository.findByEventIdOrderByShowTimeAsc(eventId);
    }
}
