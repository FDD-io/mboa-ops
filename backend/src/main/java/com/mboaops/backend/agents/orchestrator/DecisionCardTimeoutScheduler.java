package com.mboaops.backend.agents.orchestrator;

import com.mboaops.backend.eventstore.EventStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Applique le timeout de 30 minutes sur les DecisionCard : passé ce délai
 * sans réponse humaine, l'action par défaut est METTRE_EN_ATTENTE (la
 * commande reste simplement en attente, aucune approbation ni rejet
 * automatique).
 */
@Component
public class DecisionCardTimeoutScheduler {

    private final DecisionCardRepository decisionCardRepository;
    private final EventStore eventStore;

    public DecisionCardTimeoutScheduler(DecisionCardRepository decisionCardRepository, EventStore eventStore) {
        this.decisionCardRepository = decisionCardRepository;
        this.eventStore = eventStore;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expirerLesDecisionCardsEnRetard() {
        List<DecisionCard> expirees = decisionCardRepository.findByStatutAndExpireLeBefore(
                DecisionCardStatut.PENDING, Instant.now());

        for (DecisionCard card : expirees) {
            card.setStatut(DecisionCardStatut.EXPIREE);
            card.setActionAppliquee(DecisionCardAction.METTRE_EN_ATTENTE);

            eventStore.append(
                    card.getCommande().getId(),
                    "DECISION_CARD_EXPIREE",
                    Map.of("decisionCardId", card.getId(), "action", DecisionCardAction.METTRE_EN_ATTENTE),
                    null,
                    "Timeout de 30 minutes atteint sans réponse humaine, mise en attente par défaut");
        }
    }
}
