
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

    public synchronized List<ShowSeatDto> get(Long showId) {
        List<ShowSeatDto> seats = cache.get(showId);

        if (seats == null) {
            misses++;
        } else {
            hits++;
        }

        return seats;
    }

    public synchronized void put(
            Long showId, List<ShowSeatDto> seats) {
        cache.put(showId, List.copyOf(seats));
    }

    public synchronized void invalidate(Long showId) {
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
