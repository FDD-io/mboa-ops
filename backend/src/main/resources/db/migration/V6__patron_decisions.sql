-- Mémoire des décisions du patron : un pattern par (client, type de
-- décision, plafond de montant). Le compteur d'approbations identiques
-- alimente l'apprentissage de préférences (3 approbations -> préférence).
CREATE TABLE patron_decisions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id      UUID NOT NULL REFERENCES clients (id),
    type_decision  VARCHAR(30) NOT NULL,
    plafond        NUMERIC(12, 2) NOT NULL,
    compteur       INTEGER NOT NULL DEFAULT 0,
    derniere_maj   TIMESTAMP NOT NULL,
    CONSTRAINT uq_patron_decisions UNIQUE (client_id, type_decision, plafond)
);

CREATE INDEX idx_patron_decisions_client ON patron_decisions (client_id);
