package com.mboaops.backend.agents.orchestrator;

import com.mboaops.backend.domain.commande.Commande;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decision_cards")
public class DecisionCard {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Column(name = "resume", nullable = false, columnDefinition = "TEXT")
    private String resume;

    @Column(name = "recommandation", nullable = false, columnDefinition = "TEXT")
    private String recommandation;

    @Column(name = "confidence")
    private Double confidence;

    // true si l'escalade vient d'une demande de crédit/délai EXPLICITE du
    // client ; false pour une commande normale escaladée (crédit en cours,
    // stock incertain...). Conditionne le langage des messages client.
    @Column(name = "demande_credit", nullable = false)
    private boolean demandeCredit;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private DecisionCardStatut statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_appliquee", length = 20)
    private DecisionCardAction actionAppliquee;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    protected DecisionCard() {
    }

    public DecisionCard(Commande commande, String resume, String recommandation) {
        this.commande = commande;
        this.resume = resume;
        this.recommandation = recommandation;
        this.statut = DecisionCardStatut.PENDING;
        this.creeLe = Instant.now();
        this.expireLe = this.creeLe.plus(TIMEOUT);
    }

    public List<DecisionCardAction> optionsDisponibles() {
        return List.of(DecisionCardAction.APPROUVER, DecisionCardAction.REJETER, DecisionCardAction.MODIFIER);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Commande getCommande() {
        return commande;
    }

    public void setCommande(Commande commande) {
        this.commande = commande;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public String getRecommandation() {
        return recommandation;
    }

    public void setRecommandation(String recommandation) {
        this.recommandation = recommandation;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isDemandeCredit() {
        return demandeCredit;
    }

    public void setDemandeCredit(boolean demandeCredit) {
        this.demandeCredit = demandeCredit;
    }

    public DecisionCardStatut getStatut() {
        return statut;
    }

    public void setStatut(DecisionCardStatut statut) {
        this.statut = statut;
    }

    public DecisionCardAction getActionAppliquee() {
        return actionAppliquee;
    }

    public void setActionAppliquee(DecisionCardAction actionAppliquee) {
        this.actionAppliquee = actionAppliquee;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public void setCreeLe(Instant creeLe) {
        this.creeLe = creeLe;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public void setExpireLe(Instant expireLe) {
        this.expireLe = expireLe;
    }
}
