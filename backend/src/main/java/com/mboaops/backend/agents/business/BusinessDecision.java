package com.mboaops.backend.agents.business;

public record BusinessDecision(DecisionType decision, double confidence, String reasoning, String proposition) {
}
