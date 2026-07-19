package com.mboaops.backend.memoire;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PreferenceDto(
        UUID id,
        String clientNom,
        String typeDecision,
        BigDecimal plafond,
        int compteur,
        Instant dateApprentissage,
        String statut) {
}
