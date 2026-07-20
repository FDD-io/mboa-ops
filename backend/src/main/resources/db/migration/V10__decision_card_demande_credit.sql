-- Distingue une escalade née d'une demande de crédit/délai EXPLICITE du
-- client, d'une escalade sur commande normale (crédit en cours, stock
-- incertain, confiance basse). Conditionne le langage des messages client.
ALTER TABLE decision_cards ADD COLUMN demande_credit BOOLEAN NOT NULL DEFAULT FALSE;
