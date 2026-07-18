package com.mboaops.backend.api;

import com.mboaops.backend.resilience.CircuitHealthService;
import com.mboaops.backend.resilience.CircuitStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** État des circuit breakers pour le dashboard. */
@RestController
@RequestMapping("/api/health")
public class CircuitHealthController {

    private final CircuitHealthService circuitHealthService;

    public CircuitHealthController(CircuitHealthService circuitHealthService) {
        this.circuitHealthService = circuitHealthService;
    }

    @GetMapping("/circuits")
    public List<CircuitStatus> circuits() {
        return circuitHealthService.etatDeTous();
    }
}
