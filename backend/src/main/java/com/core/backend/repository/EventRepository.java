
package com.core.backend.repository;

import com.core.backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository
        extends JpaRepository<Event, Long> {
}
