package com.mboaops.backend.api;

import com.mboaops.backend.resilience.CircuitHealthService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Kill switch de démo : force l'ouverture (FORCED_OPEN) ou la fermeture
 * d'un circuit breaker pour simuler une panne de service en direct.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final CircuitHealthService circuitHealthService;

    public ChaosController(CircuitHealthService circuitHealthService) {
        this.circuitHealthService = circuitHealthService;
    }

    @PostMapping("/{service}/toggle")
    public Map<String, String> toggle(@PathVariable String service) {
        CircuitBreaker circuit;
        try {
            circuit = circuitHealthService.circuit(service);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        if (circuit.getState() == CircuitBreaker.State.FORCED_OPEN) {
            circuit.transitionToClosedState();
        } else {
            circuit.transitionToForcedOpenState();
        }

        return Map.of("service", service, "etat", circuit.getState().name());
    }
}
