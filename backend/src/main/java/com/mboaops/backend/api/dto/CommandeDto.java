package com.mboaops.backend.api.dto;

import com.mboaops.backend.domain.commande.Commande;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandeDto {

    private UUID id;
    private UUID clientId;
    private String clientNom;
    private String clientTelephone;
    private String statut;
    private Instant dateCreation;
    private BigDecimal montantTotal;
    private List<LigneCommandeDto> lignes;

    public CommandeDto() {
    }

    public static CommandeDto from(Commande commande) {
        CommandeDto dto = new CommandeDto();
        dto.id = commande.getId();
        dto.clientId = commande.getClient().getId();
        dto.clientNom = commande.getClient().getNom();
        dto.clientTelephone = commande.getClient().getTelephone();
        dto.statut = commande.getStatut().name();
        dto.dateCreation = commande.getDateCreation();
        dto.montantTotal = commande.getMontantTotal();
        dto.lignes = commande.getLignes().stream()
                .map(LigneCommandeDto::from)
                .collect(Collectors.toList());
        return dto;
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

    public String getClientNom() {
        return clientNom;
    }

    public void setClientNom(String clientNom) {
        this.clientNom = clientNom;
    }

    public String getClientTelephone() {
        return clientTelephone;
    }

    public void setClientTelephone(String clientTelephone) {
        this.clientTelephone = clientTelephone;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
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

    public List<LigneCommandeDto> getLignes() {
        return lignes;
    }

    public void setLignes(List<LigneCommandeDto> lignes) {
        this.lignes = lignes;
    }
}
