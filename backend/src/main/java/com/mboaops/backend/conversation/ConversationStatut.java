package com.mboaops.backend.conversation;

/**
 * État d'une conversation active avec un client :
 * - AUCUNE : pas d'échange en cours, chaque message est un nouveau sujet.
 * - EN_PRECISION : l'agent attend une précision du client (quantité,
 *   clarification d'un conflit ou d'un produit inconnu).
 * - EN_ATTENTE_CONFIRMATION : l'agent a transmis au patron et attend, le
 *   client a été prévenu.
 */
public enum ConversationStatut {
    AUCUNE,
    EN_PRECISION,
    EN_ATTENTE_CONFIRMATION
}
