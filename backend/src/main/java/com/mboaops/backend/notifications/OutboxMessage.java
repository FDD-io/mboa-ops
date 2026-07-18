package com.mboaops.backend.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Message en file d'attente OUTBOX : dernier échelon de la cascade de
 * notification, rejoué en différé par {@link OutboxRetryScheduler} avec un
 * backoff proportionnel au nombre de tentatives.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    private static final Duration DELAI_BASE = Duration.ofSeconds(60);

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "telephone", nullable = false, length = 20)
    private String telephone;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private OutboxStatut statut;

    @Column(name = "tentatives", nullable = false)
    private int tentatives;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "prochaine_tentative_le", nullable = false)
    private Instant prochaineTentativeLe;

    protected OutboxMessage() {
    }

    public OutboxMessage(UUID aggregateId, String telephone, String contenu) {
        this.aggregateId = aggregateId;
        this.telephone = telephone;
        this.contenu = contenu;
        this.statut = OutboxStatut.PENDING;
        this.tentatives = 0;
        this.creeLe = Instant.now();
        this.prochaineTentativeLe = this.creeLe.plus(DELAI_BASE);
    }

    public void echecTentative() {
        this.tentatives++;
        this.prochaineTentativeLe = Instant.now().plus(DELAI_BASE.multipliedBy(tentatives));
    }

    public void marquerEnvoye() {
        this.statut = OutboxStatut.SENT;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public OutboxStatut getStatut() {
        return statut;
    }

    public void setStatut(OutboxStatut statut) {
        this.statut = statut;
    }

    public int getTentatives() {
        return tentatives;
    }

    public void setTentatives(int tentatives) {
        this.tentatives = tentatives;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public void setCreeLe(Instant creeLe) {
        this.creeLe = creeLe;
    }

    public Instant getProchaineTentativeLe() {
        return prochaineTentativeLe;
    }

    public void setProchaineTentativeLe(Instant prochaineTentativeLe) {
        this.prochaineTentativeLe = prochaineTentativeLe;
    }
}
