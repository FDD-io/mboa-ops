package com.mboaops.backend.agents.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.agents.JsonExtractionUtil;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Classifie chaque message client entrant (texte, transcription audio ou
 * OCR d'image) en {intention, urgence, langue, confidence}, en s'appuyant
 * sur le modèle rapide Qwen. Le prompt système inclut des exemples réels
 * de messages camerounais, camfranglais compris.
 */
@Service
public class RouterAgent {

    private static final String SYSTEM_PROMPT = """
            Tu es le routeur d'un agent d'opérations commercial pour une quincaillerie
            camerounaise (MBOA-OPS). Ta tâche : classifier CHAQUE message client entrant.

            Réponds UNIQUEMENT avec un JSON strict, sans texte autour ni balises markdown,
            au format exact :
            {"intention": "COMMANDE|QUESTION|RECLAMATION|PAIEMENT", "urgence": 1-5, "langue": "FR|CAMFRANGLAIS", "confidence": 0.0-1.0}

            Exemples réels de messages clients camerounais :

            1. Message : "Bonjour, je veux 5 sacs de ciment pour lundi prochain svp"
               Réponse : {"intention": "COMMANDE", "urgence": 3, "langue": "FR", "confidence": 0.95}

            2. Message : "Boss wusai mon commande?? Ça fait 3 jours mia, je souffre way!"
               Réponse : {"intention": "RECLAMATION", "urgence": 4, "langue": "CAMFRANGLAIS", "confidence": 0.9}

            3. Message : "Combien coûte le sac de ciment 50kg CIMENCAM chez vous?"
               Réponse : {"intention": "QUESTION", "urgence": 2, "langue": "FR", "confidence": 0.92}

            4. Message : "J'ai déjà fait le Mobile Money là, vérifiez SVP c'est urgent!!"
               Réponse : {"intention": "PAIEMENT", "urgence": 5, "langue": "FR", "confidence": 0.88}

            5. Message : "Chef I want tôles 10 pieces, mais no be today today, small time no wahala"
               Réponse : {"intention": "COMMANDE", "urgence": 2, "langue": "CAMFRANGLAIS", "confidence": 0.8}

            Ne renvoie jamais de texte explicatif : uniquement le JSON.
            """;

    private static final RouterDecision FALLBACK =
            new RouterDecision(Intention.QUESTION, 3, Langue.FR, 0.0);

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public RouterAgent(QwenClient qwenClient, EventStore eventStore, ObjectMapper objectMapper) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    public RouterDecision classifier(UUID aggregateId, String message) {
        String prompt = SYSTEM_PROMPT + "\n\nMessage : \"" + message + "\"\nRéponse :";

        String rawResponse;
        RouterDecision decision;
        try {
            rawResponse = qwenClient.callFast(prompt);
            decision = parse(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen : " + e.getMessage();
            decision = FALLBACK;
        }

        eventStore.append(aggregateId, "ROUTER_CLASSIFICATION", decision, decision.confidence(), rawResponse);
        return decision;
    }

    private RouterDecision parse(String raw) {
        try {
            String clean = JsonExtractionUtil.stripCodeFences(raw);
            return objectMapper.readValue(clean, RouterDecision.class);
        } catch (Exception e) {
            return FALLBACK;
        }
    }
}
