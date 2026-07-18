package com.mboaops.backend.api.dto;

import java.util.UUID;

public class MessageResponse {

    private UUID commandeId;
    private UUID eventId;
    private String statut;
    private String clarification;
    private String intention;
    private String decision;
    private UUID decisionCardId;

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

    public String getIntention() {
        return intention;
    }

    public void setIntention(String intention) {
        this.intention = intention;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public UUID getDecisionCardId() {
        return decisionCardId;
    }

    public void setDecisionCardId(UUID decisionCardId) {
        this.decisionCardId = decisionCardId;
    }
}
