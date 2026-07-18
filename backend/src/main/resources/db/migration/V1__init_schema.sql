CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE clients (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom              VARCHAR(150) NOT NULL,
    telephone        VARCHAR(20)  NOT NULL UNIQUE,
    credit_en_cours  NUMERIC(12, 2) NOT NULL DEFAULT 0
);

CREATE TABLE produits (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom            VARCHAR(150) NOT NULL,
    stock          INTEGER NOT NULL DEFAULT 0,
    prix_unitaire  NUMERIC(12, 2) NOT NULL
);

CREATE TABLE commandes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id      UUID NOT NULL REFERENCES clients (id),
    statut         VARCHAR(30) NOT NULL,
    date_creation  TIMESTAMP NOT NULL DEFAULT now(),
    montant_total  NUMERIC(12, 2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_commandes_client_id ON commandes (client_id);
CREATE INDEX idx_commandes_statut ON commandes (statut);

CREATE TABLE lignes_commande (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id    UUID NOT NULL REFERENCES commandes (id) ON DELETE CASCADE,
    produit_id     UUID NOT NULL REFERENCES produits (id),
    quantite       INTEGER NOT NULL,
    prix_unitaire  NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_lignes_commande_commande_id ON lignes_commande (commande_id);

-- Event store : append-only. Aucune UPDATE/DELETE n'est autorisée sur cette
-- table, y compris par erreur applicative — le trigger ci-dessous rejette
-- ces opérations au niveau base de données.
CREATE TABLE events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID NOT NULL,
    type          VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL,
    confidence    DOUBLE PRECISION,
    reasoning     TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_aggregate_id ON events (aggregate_id);
CREATE INDEX idx_events_created_at ON events (created_at);

CREATE OR REPLACE FUNCTION prevent_events_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'events est append-only : % interdit', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_events_no_update
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION prevent_events_mutation();

CREATE TRIGGER trg_events_no_delete
    BEFORE DELETE ON events
    FOR EACH ROW EXECUTE FUNCTION prevent_events_mutation();
