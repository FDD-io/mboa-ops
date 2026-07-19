package com.mboaops.backend.pipeline;

import java.util.UUID;

/**
 * Résultat du pipeline de traitement d'un message. commandeId et statut
 * sont null quand aucune commande n'a été créée (QUESTION, RECLAMATION,
 * PAIEMENT) : seule une intention COMMANDE ouvre une commande.
 * clarification porte soit le message de clarification (conflit, produit
 * inconnu), soit la réponse envoyée à une question.
 */
public record ResultatPipeline(
        UUID commandeId,
        String statut,
        String intention,
        String decision,
        UUID decisionCardId,
        String clarification) {
}
