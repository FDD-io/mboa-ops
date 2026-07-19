package com.mboaops.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.api.dto.EventDto;
import com.mboaops.backend.eventstore.EventRepository;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventStore eventStore;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventController(EventStore eventStore, EventRepository eventRepository,
                           ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    /** Flux global des derniers événements (du plus récent au plus ancien),
     *  pour la timeline du dashboard. */
    @GetMapping
    public List<EventDto> latest(@RequestParam(defaultValue = "100") int limit) {
        int borne = Math.max(1, Math.min(limit, 500));
        return eventRepository.findAllByOrderByCreatedAtDesc().stream()
                .limit(borne)
                .map(event -> EventDto.from(event, objectMapper))
                .toList();
    }

    @GetMapping("/{aggregateId}")
    public List<EventDto> replay(@PathVariable UUID aggregateId) {
        return eventStore.replay(aggregateId).stream()
                .map(event -> EventDto.from(event, objectMapper))
                .toList();
    }
}
