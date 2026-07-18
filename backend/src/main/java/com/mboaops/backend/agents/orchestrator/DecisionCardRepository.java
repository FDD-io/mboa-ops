package com.mboaops.backend.agents.orchestrator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DecisionCardRepository extends JpaRepository<DecisionCard, UUID> {

    List<DecisionCard> findByStatut(DecisionCardStatut statut);

    List<DecisionCard> findByStatutAndExpireLeBefore(DecisionCardStatut statut, Instant instant);
}
