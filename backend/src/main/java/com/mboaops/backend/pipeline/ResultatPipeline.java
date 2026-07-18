package com.mboaops.backend.pipeline;

import java.util.UUID;

/**
 * Résultat du pipeline de traitement d'un message : statut final de la
 * commande, intention détectée par le routeur, décision métier éventuelle,
 * DecisionCard créée (si politique HITL), message de clarification envoyé
 * au client (si conflit).
 */
public record ResultatPipeline(
        String statut,
        String intention,
        String decision,
        UUID decisionCardId,
        String clarification) {
}
