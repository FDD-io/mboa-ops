package com.mboaops.backend.paiements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.agents.JsonExtractionUtil;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Paiement Mobile Money simulé : génération du lien de paiement joint au
 * devis, et parsing des SMS MoMo bruts (format réel MTN) via le modèle
 * rapide Qwen. En production, le lien viendrait de l'API MTN MoMo
 * Collections ; la réconciliation resterait identique.
 */
@Service
public class PaiementService {

    private static final String PROMPT_SMS = """
            Tu parses des SMS Mobile Money camerounais (MTN MoMo, Orange Money).
            Extrais en JSON strict :
            {"montant": N, "expediteur": "...", "reference": "..."}
            - montant : le montant reçu, en FCFA (nombre, sans séparateurs)
            - expediteur : le nom de la personne qui a envoyé l'argent
            - reference : l'identifiant de transaction (ex. "MP240717.1234.A12345")
            Réponds UNIQUEMENT avec le JSON, sans texte autour ni balises markdown.

            SMS : "%s"
            """;

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public PaiementService(QwenClient qwenClient, EventStore eventStore, ObjectMapper objectMapper) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    public String genererLienPaiement(Commande commande) {
        String lien = "https://pay.momo.mtn.cm/mboa-ops/" + commande.getId()
                + "?montant=" + commande.getMontantTotal();
        eventStore.append(commande.getId(), "LIEN_PAIEMENT_GENERE",
                Map.of("lien", lien, "montant", commande.getMontantTotal()), null, null);
        return lien;
    }

    /** Retourne null si le SMS est inexploitable (appel ou parsing échoué). */
    public SmsMomoParse parserSmsMomo(String smsBrut) {
        try {
            String raw = qwenClient.callFast(PROMPT_SMS.formatted(smsBrut));
            String clean = JsonExtractionUtil.stripCodeFences(raw);
            return objectMapper.readValue(clean, SmsMomoParse.class);
        } catch (Exception e) {
            return null;
        }
    }
}
