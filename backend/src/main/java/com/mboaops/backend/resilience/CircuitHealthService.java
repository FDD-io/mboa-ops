package com.mboaops.backend.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Point d'accès unique à l'état des circuit breakers. Les quatre circuits
 * sont créés dès le démarrage (et non à la première utilisation) pour que
 * le dashboard et le kill switch les voient immédiatement.
 */
@Service
public class CircuitHealthService {

    private final CircuitBreakerRegistry registry;

    public CircuitHealthService(CircuitBreakerRegistry registry) {
        this.registry = registry;
        CircuitNames.TOUS.forEach(registry::circuitBreaker);
    }

    public CircuitBreaker circuit(String nom) {
        if (!CircuitNames.TOUS.contains(nom)) {
            throw new IllegalArgumentException("Circuit inconnu : " + nom
                    + " (connus : " + CircuitNames.TOUS + ")");
        }
        return registry.circuitBreaker(nom);
    }

    /** Circuits qui ne laissent pas passer les appels (OPEN ou FORCED_OPEN). */
    public List<String> circuitsOuverts() {
        return CircuitNames.TOUS.stream()
                .filter(nom -> estOuvert(registry.circuitBreaker(nom).getState()))
                .toList();
    }

    public List<CircuitStatus> etatDeTous() {
        return CircuitNames.TOUS.stream()
                .map(registry::circuitBreaker)
                .map(cb -> new CircuitStatus(
                        cb.getName(),
                        cb.getState().name(),
                        cb.getMetrics().getFailureRate(),
                        cb.getMetrics().getNumberOfBufferedCalls(),
                        cb.getMetrics().getNumberOfFailedCalls(),
                        cb.getMetrics().getNumberOfNotPermittedCalls()))
                .toList();
    }

    private boolean estOuvert(CircuitBreaker.State state) {
        return state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;
    }
}
