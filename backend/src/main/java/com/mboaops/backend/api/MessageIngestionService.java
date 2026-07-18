package com.mboaops.backend.api;

import com.mboaops.backend.agents.extraction.ExtractionAgent;
import com.mboaops.backend.agents.extraction.ExtractionLigne;
import com.mboaops.backend.agents.fusion.FusionResult;
import com.mboaops.backend.agents.fusion.FusionService;
import com.mboaops.backend.api.dto.MessageRequest;
import com.mboaops.backend.api.dto.MessageResponse;
import com.mboaops.backend.domain.client.Client;
import com.mboaops.backend.domain.client.ClientRepository;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.eventstore.Event;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entrée d'un message client (WhatsApp texte/audio/image). Chaque
 * message ouvre une nouvelle commande à l'état RECUE et journalise
 * l'événement correspondant dans l'event store. Le flux multimodal
 * (extraction image/audio + fusion) s'exécute hors transaction : chaque
 * étape persiste son propre état pour ne pas tenir une connexion pendant
 * les appels LLM.
 */
@Service
public class MessageIngestionService {

    private final ClientRepository clientRepository;
    private final CommandeRepository commandeRepository;
    private final EventStore eventStore;
    private final ExtractionAgent extractionAgent;
    private final FusionService fusionService;

    public MessageIngestionService(ClientRepository clientRepository,
                                    CommandeRepository commandeRepository,
                                    EventStore eventStore,
                                    ExtractionAgent extractionAgent,
                                    FusionService fusionService) {
        this.clientRepository = clientRepository;
        this.commandeRepository = commandeRepository;
        this.eventStore = eventStore;
        this.extractionAgent = extractionAgent;
        this.fusionService = fusionService;
    }

    @Transactional
    public MessageResponse ingest(MessageRequest request) {
        Client client = trouverOuCreerClient(request.getClientPhone());

        Commande commande = commandeRepository.save(
                new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));

        Map<String, Object> payload = Map.of(
                "clientPhone", request.getClientPhone(),
                "type", request.getType().name(),
                "content", request.getContent());

        Event event = eventStore.append(commande.getId(), "MESSAGE_RECU", payload);

        return new MessageResponse(commande.getId(), event.getId(), commande.getStatut().name());
    }

    public MessageResponse ingestMultimodal(String clientPhone,
                                            String texte,
                                            String audioBase64,
                                            String audioFormat,
                                            String imageBase64,
                                            String imageMimeType) {
        Client client = trouverOuCreerClient(clientPhone);
        Commande commande = commandeRepository.save(
                new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientPhone", clientPhone);
        payload.put("type", "MULTIPART");
        payload.put("texte", texte);
        payload.put("hasAudio", audioBase64 != null);
        payload.put("hasImage", imageBase64 != null);
        Event event = eventStore.append(commande.getId(), "MESSAGE_RECU", payload);

        List<ExtractionLigne> depuisAudio = List.of();
        if (audioBase64 != null) {
            String transcript = extractionAgent.transcribeAudio(commande.getId(), audioBase64, audioFormat);
            if (!transcript.isBlank()) {
                depuisAudio = extractionAgent.extractFromTexte(commande.getId(), transcript);
            }
        }

        List<ExtractionLigne> depuisImage = List.of();
        if (imageBase64 != null) {
            depuisImage = extractionAgent.extractFromImage(commande.getId(), imageBase64, imageMimeType);
        }

        FusionResult fusion;
        if (depuisAudio.isEmpty() && depuisImage.isEmpty()) {
            // Rien d'exploitable extrait : la commande reste RECUE, un humain
            // ou une clarification ultérieure prendra le relais.
            fusion = new FusionResult(List.of(), List.of(), null);
        } else {
            fusion = fusionService.fusionner(commande, depuisAudio, depuisImage);
        }

        return new MessageResponse(commande.getId(), event.getId(),
                commande.getStatut().name(), fusion.messageClarification());
    }

    private Client trouverOuCreerClient(String clientPhone) {
        return clientRepository.findByTelephone(clientPhone)
                .orElseGet(() -> clientRepository.save(
                        new Client(clientPhone, clientPhone, BigDecimal.ZERO)));
    }
}
