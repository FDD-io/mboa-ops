package com.mboaops.backend.resilience;

/**
 * Vue sérialisable de l'état d'un circuit breaker, exposée par
 * GET /api/health/circuits pour le dashboard.
 */
public record CircuitStatus(
        String nom,
        String etat,
        float tauxEchec,
        int appelsBufferises,
        int appelsEchoues,
        long appelsNonAutorises) {
}
