package com.mboaops.backend.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.eventstore.Event;

import java.time.Instant;
import java.util.UUID;

public class EventDto {

    private UUID id;
    private UUID aggregateId;
    private String type;
    private JsonNode payload;
    private Double confidence;
    private String reasoning;
    private Instant createdAt;

    public EventDto() {
    }

    public static EventDto from(Event event, ObjectMapper objectMapper) {
        EventDto dto = new EventDto();
        dto.id = event.getId();
        dto.aggregateId = event.getAggregateId();
        dto.type = event.getType();
        try {
            dto.payload = objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            throw new IllegalStateException("Payload d'événement invalide en base : " + event.getId(), e);
        }
        dto.confidence = event.getConfidence();
        dto.reasoning = event.getReasoning();
        dto.createdAt = event.getCreatedAt();
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
