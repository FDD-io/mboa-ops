package com.mboaops.backend.paiements;

import java.math.BigDecimal;

/** Champs extraits d'un SMS Mobile Money brut par le modèle rapide Qwen. */
public record SmsMomoParse(BigDecimal montant, String expediteur, String reference) {
}
