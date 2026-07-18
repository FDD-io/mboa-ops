package com.mboaops.backend.agents;

/**
 * Les modèles Qwen enveloppent parfois leur JSON dans des blocs
 * ```json ... ``` malgré la consigne de ne renvoyer que du JSON brut.
 * Ce nettoyage minimal retire ces balises avant la désérialisation.
 */
public final class JsonExtractionUtil {

    private JsonExtractionUtil() {
    }

    public static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline != -1) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence != -1) {
            trimmed = trimmed.substring(0, lastFence);
        }
        return trimmed.trim();
    }
}
