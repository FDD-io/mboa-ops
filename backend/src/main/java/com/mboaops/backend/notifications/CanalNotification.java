package com.mboaops.backend.notifications;

/**
 * Canaux de notification, dans l'ordre de la cascade de fallback :
 * WHATSAPP (nominal) -> SMS (dégradé) -> OUTBOX (retry différé).
 */
public enum CanalNotification {
    WHATSAPP,
    SMS,
    OUTBOX
}
