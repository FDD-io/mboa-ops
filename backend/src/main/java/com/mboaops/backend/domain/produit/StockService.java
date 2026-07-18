package com.mboaops.backend.domain.produit;

import com.mboaops.backend.resilience.CircuitNames;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Consultation du stock, protégée par le circuit breaker "stock". Quand le
 * circuit est ouvert (panne simulée via /api/chaos ou incident réel), les
 * appels lèvent CallNotPermittedException : l'appelant doit alors traiter
 * l'information stock comme indisponible/périmée, ce qui déclenche la
 * dégradation de confiance dans l'orchestrateur.
 */
@Service
public class StockService {

    private final ProduitRepository produitRepository;
    private final CircuitBreaker circuitBreaker;

    public StockService(ProduitRepository produitRepository,
                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.produitRepository = produitRepository;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CircuitNames.STOCK);
    }

    public Optional<Produit> chercherProduit(String nom) {
        return circuitBreaker.executeSupplier(() -> {
            List<Produit> produits = produitRepository.findByNomContainingIgnoreCase(nom);
            return produits.isEmpty() ? Optional.empty() : Optional.of(produits.get(0));
        });
    }

    public int stockDisponible(String nomProduit) {
        return chercherProduit(nomProduit).map(Produit::getStock).orElse(0);
    }

    public boolean estDisponible(String nomProduit, int quantiteDemandee) {
        return stockDisponible(nomProduit) >= quantiteDemandee;
    }
}
