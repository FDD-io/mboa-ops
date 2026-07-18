package com.mboaops.backend.agents.fusion;

/**
 * Conflit détecté entre les deux canaux d'extraction : même produit,
 * quantités différentes sur le vocal et sur la liste écrite.
 */
public record ConflitQuantite(String produit, int quantiteAudio, int quantiteImage) {
}
