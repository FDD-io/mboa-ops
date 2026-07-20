package com.mboaops.backend.domain.commande;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository extends JpaRepository<Commande, UUID> {

    List<Commande> findByStatut(CommandeStatut statut);

    List<Commande> findByClientId(UUID clientId);

    /**
     * Charge la commande avec ses lignes initialisées : nécessaire pour la
     * réutiliser hors transaction (le pipeline n'ouvre pas de session, une
     * collection LAZY provoquerait sinon une LazyInitializationException).
     */
    @Query("select c from Commande c left join fetch c.lignes where c.id = :id")
    Optional<Commande> findByIdWithLignes(@Param("id") UUID id);
}
