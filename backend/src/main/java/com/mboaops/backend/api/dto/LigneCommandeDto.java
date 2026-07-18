package com.mboaops.backend.api.dto;

import com.mboaops.backend.domain.commande.LigneCommande;

import java.math.BigDecimal;
import java.util.UUID;

public class LigneCommandeDto {

    private UUID produitId;
    private String produitNom;
    private Integer quantite;
    private BigDecimal prixUnitaire;

    public LigneCommandeDto() {
    }

    public static LigneCommandeDto from(LigneCommande ligne) {
        LigneCommandeDto dto = new LigneCommandeDto();
        dto.produitId = ligne.getProduit().getId();
        dto.produitNom = ligne.getProduit().getNom();
        dto.quantite = ligne.getQuantite();
        dto.prixUnitaire = ligne.getPrixUnitaire();
        return dto;
    }

    public UUID getProduitId() {
        return produitId;
    }

    public void setProduitId(UUID produitId) {
        this.produitId = produitId;
    }

    public String getProduitNom() {
        return produitNom;
    }

    public void setProduitNom(String produitNom) {
        this.produitNom = produitNom;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public void setQuantite(Integer quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }
}
