package com.mboaops.backend.domain.produit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProduitRepository extends JpaRepository<Produit, UUID> {

    List<Produit> findByNomContainingIgnoreCase(String nom);
}
