package com.mboaops.backend.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Append-only event store. There is intentionally no update() or delete()
 * method here — the events table itself rejects UPDATE/DELETE at the
 * database level.
 */
@Service
public class EventStore {

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventStore(EventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public Event append(UUID aggregateId, String type, Object payload, Double confidence, String reasoning) {
        String payloadJson = toJson(payload);
        Event event = new Event(aggregateId, type, payloadJson, confidence, reasoning);
        return eventRepository.save(event);
    }

    public Event append(UUID aggregateId, String type, Object payload) {
        return append(aggregateId, type, payload, null, null);
    }

    public List<Event> replay(UUID aggregateId) {
        return eventRepository.findByAggregateIdOrderByCreatedAtAsc(aggregateId);
    }

    private String toJson(Object payload) {
        // Une chaîne déjà en JSON valide passe telle quelle ; du texte brut
        // (ex. une transcription audio) doit être sérialisé en chaîne JSON,
        // sinon l'insertion dans la colonne jsonb échoue. readTree("") renvoie
        // un MissingNode sans exception, d'où le test isMissingNode.
        if (payload instanceof String s) {
            try {
                if (!objectMapper.readTree(s).isMissingNode()) {
                    return s;
                }
            } catch (JsonProcessingException notJson) {
                // texte brut : sérialisation normale ci-dessous
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Impossible de sérialiser le payload de l'événement", e);
        }
    }
}
