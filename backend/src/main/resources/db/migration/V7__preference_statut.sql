-- Révocation des préférences apprises : pas de DELETE physique (event
-- sourcing), on marque la ligne REVOQUEE. L'unicité ne porte plus que sur
-- les patterns ACTIFS : après révocation, le ré-apprentissage crée une
-- NOUVELLE ligne ACTIVE, l'ancienne restant en historique.
ALTER TABLE patron_decisions ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE patron_decisions DROP CONSTRAINT uq_patron_decisions;

CREATE UNIQUE INDEX uq_patron_decisions_active
    ON patron_decisions (client_id, type_decision, plafond)
    WHERE statut = 'ACTIVE';
