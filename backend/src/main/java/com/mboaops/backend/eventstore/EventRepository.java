package com.mboaops.backend.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByAggregateIdOrderByCreatedAtAsc(UUID aggregateId);

    List<Event> findAllByOrderByCreatedAtDesc();
}
