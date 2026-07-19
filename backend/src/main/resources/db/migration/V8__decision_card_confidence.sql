-- Score de confiance (déjà dégradé le cas échéant) au moment de la
-- création de la carte, pour affichage direct dans la vue patron.
ALTER TABLE decision_cards ADD COLUMN confidence DOUBLE PRECISION;
