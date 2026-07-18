package com.mboaops.backend.domain.commande;

/**
 * Machine à états d'une commande. Les transitions valides sont exposées par
 * {@link #peutTransitionnerVers(CommandeStatut)} ; toute autre transition
 * doit être rejetée par le code appelant.
 */
public enum CommandeStatut {
    RECUE,
    EXTRAITE,
    EN_CLARIFICATION,
    VALIDEE_STOCK,
    EN_ATTENTE_PATRON,
    APPROUVEE,
    DEVIS_ENVOYE,
    PAYEE,
    LIVREE,
    REJETEE;

    public boolean peutTransitionnerVers(CommandeStatut cible) {
        return switch (this) {
            case RECUE -> cible == EXTRAITE || cible == REJETEE;
            case EXTRAITE -> cible == EN_CLARIFICATION || cible == VALIDEE_STOCK || cible == REJETEE;
            case EN_CLARIFICATION -> cible == EXTRAITE || cible == VALIDEE_STOCK || cible == REJETEE;
            case VALIDEE_STOCK -> cible == EN_ATTENTE_PATRON || cible == REJETEE;
            case EN_ATTENTE_PATRON -> cible == APPROUVEE || cible == REJETEE;
            case APPROUVEE -> cible == DEVIS_ENVOYE || cible == REJETEE;
            case DEVIS_ENVOYE -> cible == PAYEE || cible == REJETEE;
            case PAYEE -> cible == LIVREE;
            case LIVREE, REJETEE -> false;
        };
    }
}
