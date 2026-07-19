package com.mboaops.backend.paiements;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationResult(
        boolean reconcilie,
        UUID commandeId,
        BigDecimal montant,
        String expediteur,
        String reference,
        boolean acompte,
        String motif) {

    public static ReconciliationResult succes(UUID commandeId, SmsMomoParse parse, boolean acompte) {
        return new ReconciliationResult(true, commandeId, parse.montant(),
                parse.expediteur(), parse.reference(), acompte, null);
    }

    public static ReconciliationResult echec(SmsMomoParse parse, String motif) {
        if (parse == null) {
            return new ReconciliationResult(false, null, null, null, null, false, motif);
        }
        return new ReconciliationResult(false, null, parse.montant(),
                parse.expediteur(), parse.reference(), false, motif);
    }
}
