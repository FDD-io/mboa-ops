package com.mboaops.backend.notifications;

import com.mboaops.backend.eventstore.EventStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rejoue les messages OUTBOX en attente dès qu'un canal direct redevient
 * disponible. Backoff : 60s * nombre de tentatives entre chaque essai.
 */
@Component
public class OutboxRetryScheduler {

    private final OutboxMessageRepository outboxMessageRepository;
    private final NotificationService notificationService;
    private final EventStore eventStore;

    public OutboxRetryScheduler(OutboxMessageRepository outboxMessageRepository,
                                NotificationService notificationService,
                                EventStore eventStore) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.notificationService = notificationService;
        this.eventStore = eventStore;
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void rejouerLesMessagesEnAttente() {
        List<OutboxMessage> enAttente = outboxMessageRepository
                .findByStatutAndProchaineTentativeLeBefore(OutboxStatut.PENDING, Instant.now());

        for (OutboxMessage message : enAttente) {
            Optional<CanalNotification> canal = notificationService.tenterEnvoiDirect(
                    message.getAggregateId(), message.getTelephone(), message.getContenu());

            if (canal.isPresent()) {
                message.marquerEnvoye();
                eventStore.append(message.getAggregateId(), "NOTIFICATION_OUTBOX_REJOUEE",
                        Map.of("outboxMessageId", message.getId(), "canal", canal.get(),
                                "tentatives", message.getTentatives()),
                        null, null);
            } else {
                message.echecTentative();
            }
        }
    }
}
