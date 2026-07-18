package com.mboaops.backend.domain.commande;

import com.mboaops.backend.domain.client.Client;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commandes")
public class Commande {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private CommandeStatut statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Column(name = "montant_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantTotal;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignes = new ArrayList<>();

    protected Commande() {
    }

    public Commande(Client client, CommandeStatut statut, BigDecimal montantTotal) {
        this.client = client;
        this.statut = statut;
        this.montantTotal = montantTotal;
        this.dateCreation = Instant.now();
    }

    public void changerStatut(CommandeStatut nouveauStatut) {
        if (!this.statut.peutTransitionnerVers(nouveauStatut)) {
            throw new IllegalStateException(
                    "Transition invalide : " + this.statut + " -> " + nouveauStatut);
        }
        this.statut = nouveauStatut;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public CommandeStatut getStatut() {
        return statut;
    }

    public void setStatut(CommandeStatut statut) {
        this.statut = statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(Instant dateCreation) {
        this.dateCreation = dateCreation;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public List<LigneCommande> getLignes() {
        return lignes;
    }

    public void setLignes(List<LigneCommande> lignes) {
        this.lignes = lignes;
    }
}
