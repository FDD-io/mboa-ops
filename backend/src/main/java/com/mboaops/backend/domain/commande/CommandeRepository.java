package com.mboaops.backend.domain.commande;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, UUID> {

    List<Commande> findByStatut(CommandeStatut statut);

    List<Commande> findByClientId(UUID clientId);
}
