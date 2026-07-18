package com.mboaops.backend.agents.router;

public record RouterDecision(Intention intention, int urgence, Langue langue, double confidence) {
}
