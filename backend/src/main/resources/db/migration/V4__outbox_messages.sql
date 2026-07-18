CREATE TABLE outbox_messages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id            UUID NOT NULL,
    telephone               VARCHAR(20) NOT NULL,
    contenu                 TEXT NOT NULL,
    statut                  VARCHAR(20) NOT NULL,
    tentatives              INTEGER NOT NULL DEFAULT 0,
    cree_le                 TIMESTAMP NOT NULL,
    prochaine_tentative_le  TIMESTAMP NOT NULL
);

CREATE INDEX idx_outbox_statut_prochaine ON outbox_messages (statut, prochaine_tentative_le);
