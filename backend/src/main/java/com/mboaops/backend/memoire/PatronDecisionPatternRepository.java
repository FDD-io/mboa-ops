package com.mboaops.backend.memoire;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatronDecisionPatternRepository extends JpaRepository<PatronDecisionPattern, UUID> {

    Optional<PatronDecisionPattern> findByClientIdAndTypeDecisionAndPlafond(
            UUID clientId, String typeDecision, BigDecimal plafond);

    List<PatronDecisionPattern> findByClientIdAndTypeDecisionAndCompteurGreaterThanEqual(
            UUID clientId, String typeDecision, int compteur);
}
