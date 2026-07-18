CREATE TABLE decision_cards (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id       UUID NOT NULL REFERENCES commandes (id),
    resume            TEXT NOT NULL,
    recommandation    TEXT NOT NULL,
    statut            VARCHAR(20) NOT NULL,
    action_appliquee  VARCHAR(20),
    cree_le           TIMESTAMP NOT NULL,
    expire_le         TIMESTAMP NOT NULL
);

CREATE INDEX idx_decision_cards_statut ON decision_cards (statut);
CREATE INDEX idx_decision_cards_commande_id ON decision_cards (commande_id);
