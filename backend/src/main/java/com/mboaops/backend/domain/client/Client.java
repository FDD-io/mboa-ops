package com.mboaops.backend.domain.client;

import com.mboaops.backend.domain.commande.Commande;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "telephone", nullable = false, unique = true, length = 20)
    private String telephone;

    @Column(name = "credit_en_cours", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditEnCours;

    @OneToMany(mappedBy = "client")
    @OrderBy("dateCreation DESC")
    private List<Commande> historique = new ArrayList<>();

    protected Client() {
    }

    public Client(String nom, String telephone, BigDecimal creditEnCours) {
        this.nom = nom;
        this.telephone = telephone;
        this.creditEnCours = creditEnCours;
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

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public BigDecimal getCreditEnCours() {
        return creditEnCours;
    }

    public void setCreditEnCours(BigDecimal creditEnCours) {
        this.creditEnCours = creditEnCours;
    }

    public List<Commande> getHistorique() {
        return historique;
    }

    public void setHistorique(List<Commande> historique) {
        this.historique = historique;
    }
}
