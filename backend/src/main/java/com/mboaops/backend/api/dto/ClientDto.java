package com.mboaops.backend.api.dto;

import com.mboaops.backend.domain.client.Client;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientDto(UUID id, String nom, String telephone, BigDecimal creditEnCours) {

    public static ClientDto from(Client client) {
        return new ClientDto(client.getId(), client.getNom(),
                client.getTelephone(), client.getCreditEnCours());
    }
}
