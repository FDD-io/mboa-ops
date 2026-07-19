package com.mboaops.backend.memoire;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatronDecisionPatternRepository extends JpaRepository<PatronDecisionPattern, UUID> {

    Optional<PatronDecisionPattern> findByClientIdAndTypeDecisionAndPlafondAndStatut(
            UUID clientId, String typeDecision, BigDecimal plafond, PreferenceStatut statut);

    List<PatronDecisionPattern> findByClientIdAndTypeDecisionAndStatutAndCompteurGreaterThanEqual(
            UUID clientId, String typeDecision, PreferenceStatut statut, int compteur);

    List<PatronDecisionPattern> findByCompteurGreaterThanEqualOrderByDerniereMajDesc(int compteur);
}
