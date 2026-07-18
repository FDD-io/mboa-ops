package com.mboaops.backend.agents.business;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vue agrégée transmise au LLM : commande extraite, fiche client et état du
 * stock au moment de la décision. Construite par l'appelant à partir des
 * entités JPA pour ne pas coupler le prompt à leur forme de persistance.
 */
public record BusinessRulesInput(
        String clientNom,
        BigDecimal creditEnCours,
        int nombreCommandesHistorique,
        int nombreDefautsHistorique,
        List<LigneDemandee> lignes) {

    public record LigneDemandee(String produitNom, int quantiteDemandee, int stockDisponible, BigDecimal prixUnitaire) {
    }
}
