package com.mboaops.backend.memoire;

import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Apprentissage des préférences du patron. Chaque approbation d'une
 * DecisionCard incrémente le pattern (client, APPROVE, plafond) où le
 * plafond est la tranche de 25 000 FCFA couvrant le montant. À la 3e
 * approbation identique, la préférence est apprise (PREFERENCE_APPRISE)
 * et sera injectée dans le contexte du BusinessRulesAgent pour les
 * commandes suivantes du même client sous le même plafond.
 */
@Service
public class MemoryService {

    private static final String TYPE_APPROVE = "APPROVE";
    private static final BigDecimal TRANCHE = BigDecimal.valueOf(25_000);
    private static final int SEUIL_PREFERENCE = 3;

    private final PatronDecisionPatternRepository patternRepository;
    private final EventStore eventStore;

    public MemoryService(PatronDecisionPatternRepository patternRepository, EventStore eventStore) {
        this.patternRepository = patternRepository;
        this.eventStore = eventStore;
    }

    public void enregistrerApprobation(Commande commande) {
        UUID clientId = commande.getClient().getId();
        BigDecimal plafond = plafondPour(commande.getMontantTotal());

        PatronDecisionPattern pattern = patternRepository
                .findByClientIdAndTypeDecisionAndPlafond(clientId, TYPE_APPROVE, plafond)
                .orElseGet(() -> new PatronDecisionPattern(clientId, TYPE_APPROVE, plafond));
        pattern.incrementer();
        patternRepository.save(pattern);

        if (pattern.getCompteur() == SEUIL_PREFERENCE) {
            eventStore.append(commande.getId(), "PREFERENCE_APPRISE",
                    Map.of("clientId", clientId,
                            "clientNom", commande.getClient().getNom(),
                            "typeDecision", TYPE_APPROVE,
                            "plafond", plafond,
                            "compteur", pattern.getCompteur()),
                    null,
                    "Le patron a approuvé " + pattern.getCompteur()
                            + " commandes de ce client sous le plafond de " + plafond
                            + " FCFA : les prochaines commandes similaires pourront être auto-approuvées");
        }
    }

    /**
     * Préférence applicable à une commande de ce montant, sous forme de
     * contexte texte pour le BusinessRulesAgent. Vide si aucun pattern n'a
     * atteint le seuil ou si le montant dépasse tous les plafonds appris.
     */
    public Optional<String> preferencePour(UUID clientId, BigDecimal montant) {
        return patternRepository
                .findByClientIdAndTypeDecisionAndCompteurGreaterThanEqual(
                        clientId, TYPE_APPROVE, SEUIL_PREFERENCE)
                .stream()
                .filter(p -> p.getPlafond().compareTo(montant) >= 0)
                .min(Comparator.comparing(PatronDecisionPattern::getPlafond))
                .map(p -> "Le patron a déjà approuvé " + p.getCompteur()
                        + " commandes similaires de ce client sous un plafond de "
                        + p.getPlafond() + " FCFA. Cette commande (" + montant
                        + " FCFA) est sous ce plafond.");
    }

    private BigDecimal plafondPour(BigDecimal montant) {
        BigDecimal tranches = montant.divide(TRANCHE, 0, RoundingMode.CEILING);
        if (tranches.compareTo(BigDecimal.ONE) < 0) {
            tranches = BigDecimal.ONE;
        }
        return tranches.multiply(TRANCHE).setScale(2, RoundingMode.UNNECESSARY);
    }
}
