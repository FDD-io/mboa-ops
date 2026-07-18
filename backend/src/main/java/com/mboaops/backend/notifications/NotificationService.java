package com.mboaops.backend.notifications;

import com.mboaops.backend.eventstore.EventStore;
import com.mboaops.backend.resilience.CircuitNames;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Envoi de notifications client avec fallback en cascade :
 * WHATSAPP -> (circuit ouvert ou échec) -> SMS -> (idem) -> OUTBOX.
 * Chaque bascule de canal journalise un événement CANAL_DEGRADE.
 * Les canaux WhatsApp et SMS sont simulés (log) : en production, on y
 * brancherait les vrais fournisseurs sans toucher à la cascade.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EventStore eventStore;
    private final OutboxMessageRepository outboxMessageRepository;
    private final CircuitBreaker whatsappBreaker;
    private final CircuitBreaker smsBreaker;

    public NotificationService(EventStore eventStore,
                               OutboxMessageRepository outboxMessageRepository,
                               CircuitBreakerRegistry circuitBreakerRegistry) {
        this.eventStore = eventStore;
        this.outboxMessageRepository = outboxMessageRepository;
        this.whatsappBreaker = circuitBreakerRegistry.circuitBreaker(CircuitNames.WHATSAPP);
        this.smsBreaker = circuitBreakerRegistry.circuitBreaker(CircuitNames.SMS);
    }

    public NotificationResult envoyer(UUID aggregateId, String telephone, String contenu) {
        Optional<CanalNotification> canal = tenterEnvoiDirect(aggregateId, telephone, contenu);
        if (canal.isPresent()) {
            return new NotificationResult(canal.get(), null);
        }

        OutboxMessage outbox = outboxMessageRepository.save(
                new OutboxMessage(aggregateId, telephone, contenu));
        eventStore.append(aggregateId, "NOTIFICATION_MISE_EN_OUTBOX",
                Map.of("outboxMessageId", outbox.getId(), "telephone", telephone), null,
                "Tous les canaux directs indisponibles, retry différé programmé");
        return new NotificationResult(CanalNotification.OUTBOX, outbox.getId());
    }

    /**
     * Tente les canaux directs dans l'ordre de la cascade, sans mise en
     * outbox. Utilisé par l'envoi nominal et par le retry différé.
     */
    Optional<CanalNotification> tenterEnvoiDirect(UUID aggregateId, String telephone, String contenu) {
        try {
            whatsappBreaker.executeRunnable(() -> envoyerViaCanal(CanalNotification.WHATSAPP, telephone, contenu));
            journaliserEnvoi(aggregateId, CanalNotification.WHATSAPP, telephone);
            return Optional.of(CanalNotification.WHATSAPP);
        } catch (Exception e) {
            journaliserBascule(aggregateId, CanalNotification.WHATSAPP, CanalNotification.SMS, e);
        }

        try {
            smsBreaker.executeRunnable(() -> envoyerViaCanal(CanalNotification.SMS, telephone, contenu));
            journaliserEnvoi(aggregateId, CanalNotification.SMS, telephone);
            return Optional.of(CanalNotification.SMS);
        } catch (Exception e) {
            journaliserBascule(aggregateId, CanalNotification.SMS, CanalNotification.OUTBOX, e);
        }

        return Optional.empty();
    }

    private void envoyerViaCanal(CanalNotification canal, String telephone, String contenu) {
        // Canal simulé : en production, appel au fournisseur WhatsApp Business
        // API ou SMS. Les pannes se simulent via le kill switch /api/chaos.
        log.info("[{}] -> {} : {}", canal, telephone, contenu);
    }

    private void journaliserEnvoi(UUID aggregateId, CanalNotification canal, String telephone) {
        eventStore.append(aggregateId, "NOTIFICATION_ENVOYEE",
                Map.of("canal", canal, "telephone", telephone), null, null);
    }

    private void journaliserBascule(UUID aggregateId, CanalNotification de, CanalNotification vers, Exception cause) {
        eventStore.append(aggregateId, "CANAL_DEGRADE",
                Map.of("de", de, "vers", vers), null,
                "Bascule de canal : " + cause.getMessage());
    }
}
