package com.mboaops.backend.agents.orchestrator;

import com.mboaops.backend.agents.business.BusinessDecision;

public record OrchestrationResult(Type type, BusinessDecision decision, DecisionCard decisionCard) {

    public enum Type {
        AUTO,
        DECISION_CARD,
        ESCALADE
    }

    public static OrchestrationResult auto(BusinessDecision decision) {
        return new OrchestrationResult(Type.AUTO, decision, null);
    }

    public static OrchestrationResult decisionCard(BusinessDecision decision, DecisionCard card) {
        return new OrchestrationResult(Type.DECISION_CARD, decision, card);
    }

    public static OrchestrationResult escalade(BusinessDecision decision) {
        return new OrchestrationResult(Type.ESCALADE, decision, null);
    }
}
