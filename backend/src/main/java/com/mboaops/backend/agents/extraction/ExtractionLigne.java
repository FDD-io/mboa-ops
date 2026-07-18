package com.mboaops.backend.agents.extraction;

/**
 * Une ligne de produit extraite d'une image, d'une transcription audio ou
 * d'un texte : {produit, quantite, confidence}. La confidence est celle
 * annoncée par le modèle (< 0.7 = illisible/incertain).
 */
public record ExtractionLigne(String produit, int quantite, double confidence) {
}
