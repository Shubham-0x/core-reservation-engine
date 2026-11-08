
package com.core.backend.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeatAvailabilityCacheTests {

    @Test
    void staleDatabaseResultMustNotBeCachedAfterInvalidation() {
        SeatAvailabilityCache cache = new SeatAvailabilityCache();

        Long showId = 1L;

        // Capture the generation before the database read.
        long originalGeneration = cache.getGeneration();

        // A booking commits and invalidates the cache.
        cache.invalidate(showId);

        // The older database result must now be rejected.
        boolean cached = cache.putIfGenerationMatches(
                showId,
                originalGeneration,
                List.of()
        );

        assertFalse(cached);
        assertNull(cache.get(showId));
    }

    @Test
    void resultCanBeCachedWhenGenerationHasNotChanged() {
        SeatAvailabilityCache cache = new SeatAvailabilityCache();

        Long showId = 1L;
        long generation = cache.getGeneration();

        boolean cached = cache.putIfGenerationMatches(
                showId,
                generation,
                List.of()
        );

        assertTrue(cached);
        assertNotNull(cache.get(showId));
    }
}
