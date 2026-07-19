package com.mboaops.backend.api.dto;

import com.mboaops.backend.agents.orchestrator.DecisionCard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DecisionCardDto(
        UUID id,
        UUID commandeId,
        String clientNom,
        String clientTelephone,
        BigDecimal montantTotal,
        String resume,
        String recommandation,
        Double confidence,
        String statut,
        Instant creeLe,
        Instant expireLe) {

    public static DecisionCardDto from(DecisionCard card) {
        return new DecisionCardDto(
                card.getId(),
                card.getCommande().getId(),
                card.getCommande().getClient().getNom(),
                card.getCommande().getClient().getTelephone(),
                card.getCommande().getMontantTotal(),
                card.getResume(),
                card.getRecommandation(),
                card.getConfidence(),
                card.getStatut().name(),
                card.getCreeLe(),
                card.getExpireLe());
    }
}
