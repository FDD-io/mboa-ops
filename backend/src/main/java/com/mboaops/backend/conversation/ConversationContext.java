package com.mboaops.backend.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Mémoire conversationnelle d'un client (une ligne par téléphone). Porte le
 * sujet de l'échange en cours — produits déjà mentionnés et ce qui manque
 * encore pour compléter la commande — afin qu'un message de suivi ("5
 * barres") soit interprété comme une réponse au sujet établi, et non comme
 * un nouveau sujet isolé.
 */
@Entity
@Table(name = "conversation_contexts")
public class ConversationContext {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_phone", nullable = false, unique = true, length = 20)
    private String clientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private ConversationStatut statut;

    @Column(name = "sujet", columnDefinition = "TEXT")
    private String sujet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "produits_etablis", columnDefinition = "jsonb")
    private String produitsEtablis;

    @Column(name = "commande_id")
    private UUID commandeId;

    @Column(name = "derniere_maj", nullable = false)
    private Instant derniereMaj;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected ConversationContext() {
    }

    public ConversationContext(String clientPhone) {
        this.clientPhone = clientPhone;
        this.statut = ConversationStatut.AUCUNE;
        this.creeLe = Instant.now();
        this.derniereMaj = this.creeLe;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public ConversationStatut getStatut() {
        return statut;
    }

    public void setStatut(ConversationStatut statut) {
        this.statut = statut;
    }

    public String getSujet() {
        return sujet;
    }

    public void setSujet(String sujet) {
        this.sujet = sujet;
    }

    public String getProduitsEtablis() {
        return produitsEtablis;
    }

    public void setProduitsEtablis(String produitsEtablis) {
        this.produitsEtablis = produitsEtablis;
    }

    public UUID getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(UUID commandeId) {
        this.commandeId = commandeId;
    }

    public Instant getDerniereMaj() {
        return derniereMaj;
    }

    public void setDerniereMaj(Instant derniereMaj) {
        this.derniereMaj = derniereMaj;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public void setCreeLe(Instant creeLe) {
        this.creeLe = creeLe;
    }
}
