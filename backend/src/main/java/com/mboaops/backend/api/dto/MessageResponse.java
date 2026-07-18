package com.mboaops.backend.api.dto;

import java.util.UUID;

public class MessageResponse {

    private UUID commandeId;
    private UUID eventId;
    private String statut;
    private String clarification;

    public MessageResponse() {
    }

    public MessageResponse(UUID commandeId, UUID eventId, String statut) {
        this.commandeId = commandeId;
        this.eventId = eventId;
        this.statut = statut;
    }

    public MessageResponse(UUID commandeId, UUID eventId, String statut, String clarification) {
        this.commandeId = commandeId;
        this.eventId = eventId;
        this.statut = statut;
        this.clarification = clarification;
    }

    public UUID getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(UUID commandeId) {
        this.commandeId = commandeId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getClarification() {
        return clarification;
    }

    public void setClarification(String clarification) {
        this.clarification = clarification;
    }
}
