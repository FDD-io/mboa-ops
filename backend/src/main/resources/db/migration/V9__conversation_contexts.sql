-- Mémoire conversationnelle par client : une ligne par téléphone, portant
-- le sujet de l'échange en cours pour interpréter les messages de suivi
-- comme des réponses au contexte plutôt que comme des sujets isolés.
CREATE TABLE conversation_contexts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_phone      VARCHAR(20) NOT NULL UNIQUE,
    statut            VARCHAR(30) NOT NULL,
    sujet             TEXT,
    produits_etablis  JSONB,
    commande_id       UUID,
    derniere_maj      TIMESTAMP NOT NULL,
    cree_le           TIMESTAMP NOT NULL
);

CREATE INDEX idx_conversation_contexts_phone ON conversation_contexts (client_phone);
