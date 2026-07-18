package com.mboaops.backend.agents.orchestrator;

import com.mboaops.backend.agents.business.BusinessDecision;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Politique HITL (human-in-the-loop) : décide si une décision produite par
 * {@link com.mboaops.backend.agents.business.BusinessRulesAgent} peut être
 * appliquée automatiquement, doit être soumise au patron via une
 * DecisionCard, ou nécessite une escalade immédiate.
 *
 * Une commande est considérée irréversible dès que le client porte déjà un
 * crédit en cours : l'approuver alourdit son exposition financière, ce qui
 * ne peut pas être défait par une simple annulation applicative.
 */
@Service
public class OrchestratorAgent {

    private static final double SEUIL_AUTO = 0.9;
    private static final double SEUIL_HUMAIN = 0.6;

    private final DecisionCardRepository decisionCardRepository;
    private final EventStore eventStore;

    public OrchestratorAgent(DecisionCardRepository decisionCardRepository, EventStore eventStore) {
        this.decisionCardRepository = decisionCardRepository;
        this.eventStore = eventStore;
    }

    public OrchestrationResult orchestrer(Commande commande, BusinessDecision decision) {
        boolean actionIrreversible = commande.getClient().getCreditEnCours().compareTo(BigDecimal.ZERO) > 0;

        if (decision.confidence() > SEUIL_AUTO && !actionIrreversible) {
            eventStore.append(commande.getId(), "ORCHESTRATION_AUTO", decision, decision.confidence(), decision.reasoning());
            return OrchestrationResult.auto(decision);
        }

        boolean zoneHumaine = decision.confidence() >= SEUIL_HUMAIN && decision.confidence() <= SEUIL_AUTO;
        if (zoneHumaine || actionIrreversible) {
            DecisionCard card = creerDecisionCard(commande, decision);
            eventStore.append(commande.getId(), "DECISION_CARD_CREEE", decision, decision.confidence(), decision.reasoning());
            return OrchestrationResult.decisionCard(decision, card);
        }

        eventStore.append(commande.getId(), "ESCALADE_HUMAINE", decision, decision.confidence(), decision.reasoning());
        return OrchestrationResult.escalade(decision);
    }

    private DecisionCard creerDecisionCard(Commande commande, BusinessDecision decision) {
        String resume = String.join("\n",
                "Commande " + commande.getId() + " - Client " + commande.getClient().getNom()
                        + " - Montant " + commande.getMontantTotal() + " FCFA",
                "Décision suggérée : " + decision.decision() + " (confiance " + Math.round(decision.confidence() * 100) + "%)",
                decision.reasoning());
        String recommandation = (decision.proposition() != null && !decision.proposition().isBlank())
                ? decision.proposition()
                : decision.reasoning();

        DecisionCard card = new DecisionCard(commande, resume, recommandation);
        return decisionCardRepository.save(card);
    }
}
