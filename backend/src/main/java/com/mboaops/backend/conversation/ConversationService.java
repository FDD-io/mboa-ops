package com.mboaops.backend.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gère l'état conversationnel par client. Chaque changement est journalisé
 * (CONTEXTE_CONVERSATION_MISE_A_JOUR) sur un flux d'événements propre au
 * client, pour tracer l'ouverture, la mise à jour et la fermeture du sujet.
 * Un sujet inactif depuis plus de 15 minutes est considéré abandonné.
 */
@Service
public class ConversationService {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final ConversationContextRepository repository;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationContextRepository repository,
                               EventStore eventStore,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Contexte actif du client, ou vide si aucun sujet en cours. Un sujet
     * expiré (>15 min d'inactivité) est fermé et considéré abandonné.
     */
    public Optional<ConversationContext> contexteActif(String clientPhone) {
        Optional<ConversationContext> opt = repository.findByClientPhone(clientPhone);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        ConversationContext ctx = opt.get();
        if (ctx.getStatut() == ConversationStatut.AUCUNE) {
            return Optional.empty();
        }
        if (ctx.getDerniereMaj().isBefore(Instant.now().minus(TIMEOUT))) {
            ctx.setStatut(ConversationStatut.AUCUNE);
            ctx.setDerniereMaj(Instant.now());
            repository.save(ctx);
            journaliser(ctx, "Sujet abandonné après 15 min d'inactivité");
            return Optional.empty();
        }
        return Optional.of(ctx);
    }

    public void ouvrirPrecision(String clientPhone, String sujet, List<String> produits, UUID commandeId) {
        majContexte(clientPhone, ConversationStatut.EN_PRECISION, sujet, produits, commandeId, null);
    }

    public void passerEnAttenteConfirmation(String clientPhone, String sujet, UUID commandeId) {
        majContexte(clientPhone, ConversationStatut.EN_ATTENTE_CONFIRMATION, sujet, null, commandeId, null);
    }

    public void fermer(String clientPhone, String raison) {
        repository.findByClientPhone(clientPhone).ifPresent(ctx -> {
            ctx.setStatut(ConversationStatut.AUCUNE);
            ctx.setSujet(null);
            ctx.setProduitsEtablis(null);
            ctx.setCommandeId(null);
            ctx.setDerniereMaj(Instant.now());
            repository.save(ctx);
            journaliser(ctx, raison);
        });
    }

    private void majContexte(String clientPhone, ConversationStatut statut, String sujet,
                             List<String> produits, UUID commandeId, String raison) {
        ConversationContext ctx = repository.findByClientPhone(clientPhone)
                .orElseGet(() -> new ConversationContext(clientPhone));
        ctx.setStatut(statut);
        ctx.setSujet(sujet);
        ctx.setProduitsEtablis(produits == null ? null : toJson(produits));
        ctx.setCommandeId(commandeId);
        ctx.setDerniereMaj(Instant.now());
        repository.save(ctx);
        journaliser(ctx, raison);
    }

    private void journaliser(ConversationContext ctx, String raison) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientPhone", ctx.getClientPhone());
        payload.put("statut", ctx.getStatut().name());
        payload.put("sujet", ctx.getSujet() == null ? "" : ctx.getSujet());
        eventStore.append(fluxClient(ctx.getClientPhone()),
                "CONTEXTE_CONVERSATION_MISE_A_JOUR", payload, null, raison);
    }

    private UUID fluxClient(String clientPhone) {
        return UUID.nameUUIDFromBytes(("conversation-" + clientPhone).getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(List<String> produits) {
        try {
            return objectMapper.writeValueAsString(produits);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
