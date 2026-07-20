package com.mboaops.backend.agents.qwen;

import com.mboaops.backend.config.QwenProperties;
import com.mboaops.backend.resilience.CircuitNames;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client HTTP pour l'API Qwen (compatible OpenAI chat completions).
 * Quatre profils de modèle : rapide (classification, routage),
 * raisonnement (décisions métier), vision (extraction d'images) et
 * ASR (transcription audio). Chaque appel a un timeout de 30s,
 * 2 tentatives de retry avec backoff exponentiel, et passe par le
 * circuit breaker "qwen" (3 échecs -> OPEN 60s).
 */
@Service
public class QwenClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** Le modèle de raisonnement "réfléchit" avant de répondre : ~30s sur un
     *  prompt métier, d'où un timeout dédié plus large. */
    private static final Duration TIMEOUT_REASONING = Duration.ofSeconds(90);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(500);

    private final WebClient qwenWebClient;
    private final QwenProperties qwenProperties;
    private final CircuitBreaker circuitBreaker;

    public QwenClient(WebClient qwenWebClient, QwenProperties qwenProperties,
                      CircuitBreakerRegistry circuitBreakerRegistry) {
        this.qwenWebClient = qwenWebClient;
        this.qwenProperties = qwenProperties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CircuitNames.QWEN);
    }

    public String callFast(String prompt) {
        return call(qwenProperties.getModelFast(),
                List.of(new QwenChatMessage("user", prompt)), TIMEOUT, false);
    }

    public String callReasoning(String prompt) {
        return call(qwenProperties.getModelReasoning(),
                List.of(new QwenChatMessage("user", prompt)), TIMEOUT_REASONING, false);
    }

    /**
     * Appel vision : l'image est transmise en data URL base64 via une partie
     * de contenu {@code image_url}, accompagnée du prompt texte.
     */
    public String callVision(String prompt, String base64Image, String mimeType) {
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;
        List<Map<String, Object>> parts = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", prompt));
        return call(qwenProperties.getModelVl(),
                List.of(new QwenChatMessage("user", parts)));
    }

    /**
     * Appel ASR : l'audio est transmis en data URL base64 via une partie de
     * contenu {@code input_audio}. Le contexte système oriente le modèle vers
     * le français camerounais (camfranglais possible). La tâche ASR n'accepte
     * le message système qu'au format "content parts", pas en texte simple.
     */
    public String callAsr(String base64Audio, String format, String contexte) {
        String dataUrl = "data:audio/" + format + ";base64," + base64Audio;
        List<Map<String, Object>> systemParts = List.of(
                Map.of("type", "text", "text", contexte));
        List<Map<String, Object>> userParts = List.of(
                Map.of("type", "input_audio", "input_audio", Map.of("data", dataUrl, "format", format)));
        return call(qwenProperties.getModelAsr(), List.of(
                new QwenChatMessage("system", systemParts),
                new QwenChatMessage("user", userParts)));
    }

    private String call(String model, List<QwenChatMessage> messages) {
        // Modèles vision/ASR : enable_thinking omis (paramètre inconnu chez eux).
        return call(model, messages, TIMEOUT, null);
    }

    private String call(String model, List<QwenChatMessage> messages, Duration timeout,
                        Boolean enableThinking) {
        try {
            return circuitBreaker.executeSupplier(() -> doCall(model, messages, timeout, enableThinking));
        } catch (CallNotPermittedException e) {
            throw new QwenClientException(
                    "Circuit 'qwen' ouvert : appels LLM suspendus (modèle '" + model + "')", e);
        }
    }

    private String doCall(String model, List<QwenChatMessage> messages, Duration timeout,
                          Boolean enableThinking) {
        QwenChatRequest request = new QwenChatRequest(model, messages, 0.1, enableThinking);

        QwenChatResponse response;
        try {
            response = qwenWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(QwenChatResponse.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_BACKOFF))
                    .block();
        } catch (Exception e) {
            throw new QwenClientException("Échec de l'appel au modèle Qwen '" + model + "'", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new QwenClientException("Réponse vide du modèle Qwen '" + model + "'");
        }
        return response.choices().get(0).message().contentAsText();
    }
}
