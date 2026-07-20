package com.mboaops.backend.agents.qwen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * enableThinking=false coupe le raisonnement interne des modèles texte
 * (latence divisée par 5 à 10). Laisser null pour les modèles vision/ASR
 * qui ne connaissent pas ce paramètre (il est alors omis du JSON).
 */
public record QwenChatRequest(
        String model,
        List<QwenChatMessage> messages,
        Double temperature,
        @JsonProperty("enable_thinking") @JsonInclude(JsonInclude.Include.NON_NULL) Boolean enableThinking) {
}
