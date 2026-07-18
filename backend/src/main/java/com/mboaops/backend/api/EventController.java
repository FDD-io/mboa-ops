package com.mboaops.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.api.dto.EventDto;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public EventController(EventStore eventStore, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{aggregateId}")
    public List<EventDto> replay(@PathVariable UUID aggregateId) {
        return eventStore.replay(aggregateId).stream()
                .map(event -> EventDto.from(event, objectMapper))
                .toList();
    }
}
