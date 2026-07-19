package com.mboaops.backend.agents.orchestrator;

import com.mboaops.backend.agents.business.BusinessDecision;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.eventstore.EventStore;
import com.mboaops.backend.resilience.CircuitHealthService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Politique HITL (human-in-the-loop) : décide si une décision produite par
 * {@link com.mboaops.backend.agents.business.BusinessRulesAgent} peut être
 * appliquée automatiquement, doit être soumise au patron via une
 * DecisionCard, ou nécessite une escalade immédiate.
 *
 * Une commande est considérée irréversible dès que le client porte déjà un
 * crédit en cours : l'approuver alourdit son exposition financière, ce qui
 * ne peut pas être défait par une simple annulation applicative.
 *
 * Résilience : si un circuit breaker est ouvert pendant le traitement, les
 * données sous-jacentes (stock, LLM, notifications) sont potentiellement
 * périmées — la confiance est multipliée par 0.7 avant application des
 * seuils, ce qui pousse davantage de décisions vers l'humain.
 */
@Service
public class OrchestratorAgent {

    private static final double SEUIL_AUTO = 0.9;
    private static final double SEUIL_HUMAIN = 0.6;
    private static final double FACTEUR_DEGRADATION = 0.7;

    private final DecisionCardRepository decisionCardRepository;
    private final EventStore eventStore;
    private final CircuitHealthService circuitHealthService;

    public OrchestratorAgent(DecisionCardRepository decisionCardRepository,
                             EventStore eventStore,
                             CircuitHealthService circuitHealthService) {
        this.decisionCardRepository = decisionCardRepository;
        this.eventStore = eventStore;
        this.circuitHealthService = circuitHealthService;
    }

    public OrchestrationResult orchestrer(Commande commande, BusinessDecision decision) {
        return orchestrer(commande, decision, false);
    }

    /**
     * Variante avec préférence patron apprise : quand le patron a déjà
     * approuvé 3 fois ce pattern (client + plafond), l'irréversibilité liée
     * au crédit ne force plus le passage par une DecisionCard — c'est
     * précisément ce que l'apprentissage vise à automatiser.
     */
    public OrchestrationResult orchestrer(Commande commande, BusinessDecision decision,
                                          boolean preferencePatronApplicable) {
        double confidence = appliquerDegradation(commande, decision);
        boolean actionIrreversible = commande.getClient().getCreditEnCours().compareTo(BigDecimal.ZERO) > 0
                && !preferencePatronApplicable;

        if (confidence > SEUIL_AUTO && !actionIrreversible) {
            eventStore.append(commande.getId(), "ORCHESTRATION_AUTO", decision, confidence, decision.reasoning());
            return OrchestrationResult.auto(decision);
        }

        boolean zoneHumaine = confidence >= SEUIL_HUMAIN && confidence <= SEUIL_AUTO;
        if (zoneHumaine || actionIrreversible) {
            DecisionCard card = creerDecisionCard(commande, decision, confidence);
            eventStore.append(commande.getId(), "DECISION_CARD_CREEE", decision, confidence, decision.reasoning());
            return OrchestrationResult.decisionCard(decision, card);
        }

        eventStore.append(commande.getId(), "ESCALADE_HUMAINE", decision, confidence, decision.reasoning());
        return OrchestrationResult.escalade(decision);
    }

    /**
     * Multiplie la confiance par 0.7 si au moins un circuit est ouvert, et
     * journalise CONFIANCE_DEGRADEE avec la raison (liste des circuits).
     */
    private double appliquerDegradation(Commande commande, BusinessDecision decision) {
        List<String> circuitsOuverts = circuitHealthService.circuitsOuverts();
        if (circuitsOuverts.isEmpty()) {
            return decision.confidence();
        }

        double degradee = decision.confidence() * FACTEUR_DEGRADATION;
        eventStore.append(commande.getId(), "CONFIANCE_DEGRADEE",
                Map.of("confidenceInitiale", decision.confidence(),
                        "confidenceDegradee", degradee,
                        "circuitsOuverts", circuitsOuverts),
                degradee,
                "Circuits ouverts pendant le traitement (" + String.join(", ", circuitsOuverts)
                        + ") : données potentiellement périmées, prudence renforcée");
        return degradee;
    }

    private DecisionCard creerDecisionCard(Commande commande, BusinessDecision decision, double confidence) {
        String resume = String.join("\n",
                "Commande " + commande.getId() + " - Client " + commande.getClient().getNom()
                        + " - Montant " + commande.getMontantTotal() + " FCFA",
                "Décision suggérée : " + decision.decision() + " (confiance " + Math.round(confidence * 100) + "%)",
                decision.reasoning());
        String recommandation = (decision.proposition() != null && !decision.proposition().isBlank())
                ? decision.proposition()
                : decision.reasoning();

        DecisionCard card = new DecisionCard(commande, resume, recommandation);
        card.setConfidence(confidence);
        return decisionCardRepository.save(card);
    }
}
