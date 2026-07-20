package com.mboaops.backend.agents.extraction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.agents.JsonExtractionUtil;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extraction multimodale : produits/quantités depuis une image de liste
 * manuscrite (Qwen-VL), transcription d'un vocal en français camerounais
 * (Qwen ASR), et extraction depuis un texte libre (modèle rapide) pour
 * pouvoir comparer les canaux entre eux. Chaque appel journalise un
 * événement dans l'event store.
 */
@Service
public class ExtractionAgent {

    private static final String PROMPT_IMAGE = """
            Extrais les produits et quantités de cette liste manuscrite en JSON strict
            [{"produit": "...", "quantite": N, "confidence": 0.0-1.0}].
            "produit" est le nom générique du produit, au singulier, sans quantité ni
            emballage (ex. "ciment", "savon", "tôle").
            Marque confidence < 0.7 si illisible.
            Réponds UNIQUEMENT avec le tableau JSON, sans texte autour ni balises markdown.
            """;

    private static final String PROMPT_TEXTE = """
            Extrais les produits et quantités de ce message client en JSON strict
            [{"produit": "...", "quantite": N, "confidence": 0.0-1.0}].
            "produit" est le nom générique du produit, au singulier, sans quantité ni
            emballage (ex. "ciment", "savon", "tôle").
            Le message peut être en français camerounais ou camfranglais.
            Marque confidence < 0.7 si la quantité ou le produit est ambigu.
            Réponds UNIQUEMENT avec le tableau JSON, sans texte autour ni balises markdown.

            Message : "%s"
            """;

    private static final String PROMPT_CONTEXTE = """
            Conversation en cours avec un client de la quincaillerie MBOA-OPS.
            Sujet déjà établi : %s.
            Le client vient de répondre : "%s".
            Déduis la commande COMPLÈTE en combinant le sujet établi et cette réponse.
            Si le sujet est un produit et la réponse une quantité, associe-les.
            JSON strict [{"produit": "...", "quantite": N, "confidence": 0.0-1.0}].
            "produit" = nom générique du produit (ex. "fer à béton", "ciment"),
            au singulier, sans quantité ni emballage.
            Réponds UNIQUEMENT avec le tableau JSON, sans texte autour ni balises markdown.
            """;

    private static final String CONTEXTE_ASR =
            "Transcris fidèlement ce message vocal en français camerounais. "
            + "Il peut contenir du camfranglais et des noms de produits de quincaillerie "
            + "(ciment, tôles, savon, clous, peinture...).";

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public ExtractionAgent(QwenClient qwenClient, EventStore eventStore, ObjectMapper objectMapper) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    public List<ExtractionLigne> extractFromImage(UUID aggregateId, String base64Image, String mimeType) {
        long debut = System.nanoTime();
        String rawResponse;
        List<ExtractionLigne> lignes;
        try {
            rawResponse = qwenClient.callVision(PROMPT_IMAGE, base64Image, mimeType);
            lignes = parseLignes(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen-VL : " + e.getMessage();
            lignes = List.of();
        }

        eventStore.append(aggregateId, "EXTRACTION_IMAGE",
                Map.of("lignes", lignes, "durationMs", dureeMs(debut)),
                confidenceMoyenne(lignes), rawResponse);
        return lignes;
    }

    public String transcribeAudio(UUID aggregateId, String base64Audio, String format) {
        long debut = System.nanoTime();
        String transcript;
        Double confidence = null;
        try {
            transcript = qwenClient.callAsr(base64Audio, format, CONTEXTE_ASR);
            confidence = transcript.isBlank() ? 0.0 : null;
        } catch (Exception e) {
            transcript = "";
            confidence = 0.0;
            eventStore.append(aggregateId, "TRANSCRIPTION_AUDIO",
                    Map.of("transcript", "", "durationMs", dureeMs(debut)), confidence,
                    "Échec de l'appel Qwen ASR : " + e.getMessage());
            return transcript;
        }

        eventStore.append(aggregateId, "TRANSCRIPTION_AUDIO",
                Map.of("transcript", transcript, "durationMs", dureeMs(debut)), confidence, null);
        return transcript;
    }

    public List<ExtractionLigne> extractFromTexte(UUID aggregateId, String texte) {
        long debut = System.nanoTime();
        String rawResponse;
        List<ExtractionLigne> lignes;
        try {
            rawResponse = qwenClient.callFast(PROMPT_TEXTE.formatted(texte));
            lignes = parseLignes(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen : " + e.getMessage();
            lignes = List.of();
        }

        eventStore.append(aggregateId, "EXTRACTION_TEXTE",
                Map.of("lignes", lignes, "durationMs", dureeMs(debut)),
                confidenceMoyenne(lignes), rawResponse);
        return lignes;
    }

    /**
     * Extraction guidée par le contexte conversationnel : le message de suivi
     * ("5 barres") est combiné au sujet établi ("Fer à béton 12mm") pour
     * reconstituer la commande complète, au lieu d'être extrait isolément.
     */
    public List<ExtractionLigne> extractAvecContexte(UUID aggregateId, String sujet, String message) {
        long debut = System.nanoTime();
        String rawResponse;
        List<ExtractionLigne> lignes;
        try {
            rawResponse = qwenClient.callFast(PROMPT_CONTEXTE.formatted(sujet, message));
            lignes = parseLignes(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen : " + e.getMessage();
            lignes = List.of();
        }

        eventStore.append(aggregateId, "EXTRACTION_CONTEXTUELLE",
                Map.of("lignes", lignes, "sujet", sujet, "durationMs", dureeMs(debut)),
                confidenceMoyenne(lignes), rawResponse);
        return lignes;
    }

    private long dureeMs(long debutNanos) {
        return (System.nanoTime() - debutNanos) / 1_000_000;
    }

    private List<ExtractionLigne> parseLignes(String raw) {
        try {
            String clean = JsonExtractionUtil.stripCodeFences(raw);
            return objectMapper.readValue(clean, new TypeReference<List<ExtractionLigne>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Double confidenceMoyenne(List<ExtractionLigne> lignes) {
        if (lignes.isEmpty()) {
            return 0.0;
        }
        return lignes.stream().mapToDouble(ExtractionLigne::confidence).average().orElse(0.0);
    }
}
