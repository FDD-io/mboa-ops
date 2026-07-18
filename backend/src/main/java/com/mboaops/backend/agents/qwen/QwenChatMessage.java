package com.mboaops.backend.agents.qwen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message du protocole chat completions. Le contenu est soit une chaîne
 * (texte simple), soit une liste de parties typées (image_url,
 * input_audio, text) pour les appels multimodaux.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QwenChatMessage(String role, Object content) {

    public String contentAsText() {
        return content == null ? "" : String.valueOf(content);
    }
}
