package com.mboaops.backend.agents.orchestrator;

/**
 * Options proposées au patron sur une DecisionCard (✅ approuver,
 * ❌ rejeter, ✏️ modifier), plus l'action par défaut appliquée
 * automatiquement si le timeout de 30 minutes expire sans réponse.
 */
public enum DecisionCardAction {
    APPROUVER,
    REJETER,
    MODIFIER,
    METTRE_EN_ATTENTE
}
