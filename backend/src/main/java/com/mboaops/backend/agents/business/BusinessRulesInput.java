package com.mboaops.backend.agents.business;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vue agrégée transmise au LLM : commande extraite, fiche client, état du
 * stock au moment de la décision, préférence patron apprise le cas échéant
 * (null sinon), et le message brut du client (pour détecter une demande de
 * crédit ou de paiement différé). Construite par l'appelant à partir des
 * entités JPA pour ne pas coupler le prompt à leur forme de persistance.
 */
public record BusinessRulesInput(
        String clientNom,
        BigDecimal creditEnCours,
        int nombreCommandesHistorique,
        int nombreDefautsHistorique,
        List<LigneDemandee> lignes,
        String preferencePatron,
        String messageClient) {

    public record LigneDemandee(String produitNom, int quantiteDemandee, int stockDisponible, BigDecimal prixUnitaire) {
    }
}
