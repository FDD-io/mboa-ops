package com.mboaops.backend.memoire;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pattern de décision du patron : (client, type de décision, plafond de
 * montant) avec compteur d'approbations identiques. À partir de 3
 * approbations, le pattern devient une préférence apprise injectée dans le
 * contexte du BusinessRulesAgent.
 */
@Entity
@Table(name = "patron_decisions")
public class PatronDecisionPattern {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "type_decision", nullable = false, length = 30)
    private String typeDecision;

    @Column(name = "plafond", nullable = false, precision = 12, scale = 2)
    private BigDecimal plafond;

    @Column(name = "compteur", nullable = false)
    private int compteur;

    @Column(name = "derniere_maj", nullable = false)
    private Instant derniereMaj;

    protected PatronDecisionPattern() {
    }

    public PatronDecisionPattern(UUID clientId, String typeDecision, BigDecimal plafond) {
        this.clientId = clientId;
        this.typeDecision = typeDecision;
        this.plafond = plafond;
        this.compteur = 0;
        this.derniereMaj = Instant.now();
    }

    public void incrementer() {
        this.compteur++;
        this.derniereMaj = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getTypeDecision() {
        return typeDecision;
    }

    public void setTypeDecision(String typeDecision) {
        this.typeDecision = typeDecision;
    }

    public BigDecimal getPlafond() {
        return plafond;
    }

    public void setPlafond(BigDecimal plafond) {
        this.plafond = plafond;
    }

    public int getCompteur() {
        return compteur;
    }

    public void setCompteur(int compteur) {
        this.compteur = compteur;
    }

    public Instant getDerniereMaj() {
        return derniereMaj;
    }

    public void setDerniereMaj(Instant derniereMaj) {
        this.derniereMaj = derniereMaj;
    }
}
