package com.mboaops.backend.api;

import com.mboaops.backend.api.dto.MessageRequest;
import com.mboaops.backend.api.dto.MessageResponse;
import com.mboaops.backend.domain.client.Client;
import com.mboaops.backend.domain.client.ClientRepository;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.eventstore.Event;
import com.mboaops.backend.eventstore.EventStore;
import com.mboaops.backend.pipeline.CommandePipelineService;
import com.mboaops.backend.pipeline.ResultatPipeline;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Point d'entrée d'un message client (WhatsApp texte/audio/image). Chaque
 * message ouvre une nouvelle commande à l'état RECUE, journalise
 * MESSAGE_RECU, puis déroule le pipeline complet (routeur -> extraction ->
 * fusion -> règles métier -> HITL). Volontairement hors transaction :
 * chaque étape du pipeline persiste son propre état pour ne pas tenir de
 * connexion pendant les appels LLM.
 */
@Service
public class MessageIngestionService {

    private final ClientRepository clientRepository;
    private final CommandeRepository commandeRepository;
    private final EventStore eventStore;
    private final CommandePipelineService pipeline;

    public MessageIngestionService(ClientRepository clientRepository,
                                    CommandeRepository commandeRepository,
                                    EventStore eventStore,
                                    CommandePipelineService pipeline) {
        this.clientRepository = clientRepository;
        this.commandeRepository = commandeRepository;
        this.eventStore = eventStore;
        this.pipeline = pipeline;
    }

    public MessageResponse ingest(MessageRequest request) {
        Client client = trouverOuCreerClient(request.getClientPhone());
        Commande commande = commandeRepository.save(
                new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));

        Map<String, Object> payload = Map.of(
                "clientPhone", request.getClientPhone(),
                "type", request.getType().name(),
                "content", request.getContent());
        Event event = eventStore.append(commande.getId(), "MESSAGE_RECU", payload);

        ResultatPipeline resultat = switch (request.getType()) {
            case TEXT -> pipeline.traiterTexte(commande, request.getContent());
            // En JSON, audio et image arrivent déjà encodés en base64 dans content.
            case AUDIO -> pipeline.traiterMultimodal(commande, null,
                    request.getContent(), "mp3", null, null);
            case IMAGE -> pipeline.traiterMultimodal(commande, null,
                    null, null, request.getContent(), "image/jpeg");
        };

        return construireReponse(commande, event, resultat);
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

        ResultatPipeline resultat = pipeline.traiterMultimodal(
                commande, texte, audioBase64, audioFormat, imageBase64, imageMimeType);

        return construireReponse(commande, event, resultat);
    }

    private MessageResponse construireReponse(Commande commande, Event event, ResultatPipeline resultat) {
        MessageResponse reponse = new MessageResponse(
                commande.getId(), event.getId(), resultat.statut(), resultat.clarification());
        reponse.setIntention(resultat.intention());
        reponse.setDecision(resultat.decision());
        reponse.setDecisionCardId(resultat.decisionCardId());
        return reponse;
    }

    private Client trouverOuCreerClient(String clientPhone) {
        return clientRepository.findByTelephone(clientPhone)
                .orElseGet(() -> clientRepository.save(
                        new Client(clientPhone, clientPhone, BigDecimal.ZERO)));
    }
}
