package com.mboaops.backend.notifications;

import java.util.UUID;

/** Canal effectivement utilisé ; outboxMessageId non nul uniquement si le
 *  message a fini en file d'attente. */
public record NotificationResult(CanalNotification canal, UUID outboxMessageId) {
}
