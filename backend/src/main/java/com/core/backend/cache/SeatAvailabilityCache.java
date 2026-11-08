package com.core.backend.cache;

import com.core.backend.dto.ShowSeatDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeatAvailabilityCache {

    private static final int CAPACITY = 100;

    private final Map<Long, List<ShowSeatDto>> cache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Long, List<ShowSeatDto>> eldest) {
                    return size() > CAPACITY;
                }
            };

    private long hits;
    private long misses;

    // Changes whenever a booking or cancellation invalidates the cache.
    private long generation;

    public synchronized List<ShowSeatDto> get(Long showId) {
        List<ShowSeatDto> seats = cache.get(showId);

        if (seats == null) {
            misses++;
        } else {
            hits++;
        }

        return seats;
    }

    public synchronized long getGeneration() {
        return generation;
    }

    public synchronized boolean putIfGenerationMatches(
            Long showId,
            long expectedGeneration,
            List<ShowSeatDto> seats) {

        if (generation != expectedGeneration) {
            return false;
        }

        cache.put(showId, List.copyOf(seats));
        return true;
    }

    public synchronized void invalidate(Long showId) {
        generation++;
        cache.remove(showId);
    }

    public synchronized Map<String, Object> getStats() {
        return Map.of(
                "hits", hits,
                "misses", misses,
                "entries", cache.size(),
                "capacity", CAPACITY
        );
    }
}
