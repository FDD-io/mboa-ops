package com.mboaops.backend.memoire;

import com.mboaops.backend.domain.client.ClientRepository;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Apprentissage des préférences du patron. Chaque approbation d'une
 * DecisionCard incrémente le pattern ACTIF (client, APPROVE, plafond) où le
 * plafond est la tranche de 25 000 FCFA couvrant le montant. À la 3e
 * approbation identique, la préférence est apprise (PREFERENCE_APPRISE)
 * et injectée dans le contexte du BusinessRulesAgent pour les commandes
 * suivantes du même client sous le même plafond.
 *
 * L'humain garde le dernier mot : une préférence peut être révoquée à tout
 * moment (statut REVOQUEE, jamais de DELETE physique). Le ré-apprentissage
 * reste possible : 3 nouvelles approbations créent une NOUVELLE ligne
 * ACTIVE, l'ancienne restant en historique.
 */
@Service
public class MemoryService {

    private static final String TYPE_APPROVE = "APPROVE";
    private static final BigDecimal TRANCHE = BigDecimal.valueOf(25_000);
    private static final int SEUIL_PREFERENCE = 3;

    private final PatronDecisionPatternRepository patternRepository;
    private final ClientRepository clientRepository;
    private final EventStore eventStore;

    public MemoryService(PatronDecisionPatternRepository patternRepository,
                         ClientRepository clientRepository,
                         EventStore eventStore) {
        this.patternRepository = patternRepository;
        this.clientRepository = clientRepository;
        this.eventStore = eventStore;
    }

    public void enregistrerApprobation(Commande commande) {
        UUID clientId = commande.getClient().getId();
        BigDecimal plafond = plafondPour(commande.getMontantTotal());

        PatronDecisionPattern pattern = patternRepository
                .findByClientIdAndTypeDecisionAndPlafondAndStatut(
                        clientId, TYPE_APPROVE, plafond, PreferenceStatut.ACTIVE)
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
     * Préférence ACTIVE applicable à une commande de ce montant, sous forme
     * de contexte texte pour le BusinessRulesAgent. Vide si aucun pattern
     * actif n'a atteint le seuil ou si le montant dépasse tous les plafonds.
     */
    public Optional<String> preferencePour(UUID clientId, BigDecimal montant) {
        return patternRepository
                .findByClientIdAndTypeDecisionAndStatutAndCompteurGreaterThanEqual(
                        clientId, TYPE_APPROVE, PreferenceStatut.ACTIVE, SEUIL_PREFERENCE)
                .stream()
                .filter(p -> p.getPlafond().compareTo(montant) >= 0)
                .min(Comparator.comparing(PatronDecisionPattern::getPlafond))
                .map(p -> "Le patron a déjà approuvé " + p.getCompteur()
                        + " commandes similaires de ce client sous un plafond de "
                        + p.getPlafond() + " FCFA. Cette commande (" + montant
                        + " FCFA) est sous ce plafond.");
    }

    /** Toutes les préférences apprises (compteur >= seuil), actives et révoquées. */
    public List<PreferenceDto> listerPreferences() {
        return patternRepository
                .findByCompteurGreaterThanEqualOrderByDerniereMajDesc(SEUIL_PREFERENCE)
                .stream()
                .map(this::versDto)
                .toList();
    }

    /**
     * Révoque une préférence (statut REVOQUEE, aucune suppression physique)
     * et journalise PREFERENCE_REVOQUEE sur le flux du client.
     *
     * @throws NoSuchElementException si l'id est inconnu (-> 404)
     * @throws IllegalStateException  si déjà révoquée (-> 409)
     */
    public PreferenceDto revoquer(UUID preferenceId) {
        PatronDecisionPattern pattern = patternRepository.findById(preferenceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Préférence introuvable : " + preferenceId));
        if (pattern.getStatut() == PreferenceStatut.REVOQUEE) {
            throw new IllegalStateException("Préférence déjà révoquée : " + preferenceId);
        }

        pattern.revoquer();
        patternRepository.save(pattern);

        String clientNom = nomClient(pattern.getClientId());
        eventStore.append(pattern.getClientId(), "PREFERENCE_REVOQUEE",
                Map.of("preferenceId", pattern.getId(),
                        "clientNom", clientNom,
                        "plafond", pattern.getPlafond(),
                        "compteurAuMomentDeLaRevocation", pattern.getCompteur()),
                null,
                "Préférence révoquée par le patron : les commandes de ce client repassent "
                        + "par la politique HITL normale");
        return versDto(pattern);
    }

    private PreferenceDto versDto(PatronDecisionPattern pattern) {
        return new PreferenceDto(
                pattern.getId(),
                nomClient(pattern.getClientId()),
                pattern.getTypeDecision(),
                pattern.getPlafond(),
                pattern.getCompteur(),
                pattern.getDerniereMaj(),
                pattern.getStatut().name());
    }

    private String nomClient(UUID clientId) {
        return clientRepository.findById(clientId)
                .map(client -> client.getNom())
                .orElse("Client inconnu");
    }

    private BigDecimal plafondPour(BigDecimal montant) {
        BigDecimal tranches = montant.divide(TRANCHE, 0, RoundingMode.CEILING);
        if (tranches.compareTo(BigDecimal.ONE) < 0) {
            tranches = BigDecimal.ONE;
        }
        return tranches.multiply(TRANCHE).setScale(2, RoundingMode.UNNECESSARY);
    }
}
