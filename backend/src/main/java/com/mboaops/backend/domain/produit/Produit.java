package com.mboaops.backend.domain.produit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "produits")
public class Produit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "prix_unitaire", nullable = false, precision = 12, scale = 2)
    private BigDecimal prixUnitaire;

    protected Produit() {
    }

    public Produit(String nom, Integer stock, BigDecimal prixUnitaire) {
        this.nom = nom;
        this.stock = stock;
        this.prixUnitaire = prixUnitaire;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }
}
