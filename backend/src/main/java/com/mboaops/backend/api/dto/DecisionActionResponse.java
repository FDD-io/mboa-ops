package com.mboaops.backend.api.dto;

import java.util.UUID;

public record DecisionActionResponse(UUID decisionCardId, ActionPatron action, String commandeStatut) {
}
