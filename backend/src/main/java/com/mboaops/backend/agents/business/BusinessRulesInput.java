package com.mboaops.backend.agents.business;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vue agrégée transmise au LLM : commande extraite, fiche client, état du
 * stock au moment de la décision, et préférence patron apprise le cas
 * échéant (null sinon). Construite par l'appelant à partir des entités JPA
 * pour ne pas coupler le prompt à leur forme de persistance.
 */
public record BusinessRulesInput(
        String clientNom,
        BigDecimal creditEnCours,
        int nombreCommandesHistorique,
        int nombreDefautsHistorique,
        List<LigneDemandee> lignes,
        String preferencePatron) {

    public record LigneDemandee(String produitNom, int quantiteDemandee, int stockDisponible, BigDecimal prixUnitaire) {
    }
}
