package com.mboaops.backend.agents.fusion;

import com.mboaops.backend.agents.extraction.ExtractionLigne;

import java.util.List;

public record FusionResult(
        List<ExtractionLigne> lignes,
        List<ConflitQuantite> conflits,
        String messageClarification) {

    public boolean aDesConflits() {
        return !conflits.isEmpty();
    }
}
