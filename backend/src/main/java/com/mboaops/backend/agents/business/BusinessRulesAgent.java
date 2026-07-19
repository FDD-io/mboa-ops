package com.mboaops.backend.agents.business;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.agents.JsonExtractionUtil;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Applique les règles métier de MBOA-OPS à une commande extraite en
 * s'appuyant sur le modèle de raisonnement Qwen. Toute réponse
 * inexploitable (échec d'appel ou JSON invalide) retombe sur NEEDS_HUMAN
 * plutôt que de bloquer le flux.
 */
@Service
public class BusinessRulesAgent {

    private static final String SYSTEM_PROMPT = """
            Tu es l'agent de règles métier de MBOA-OPS, une quincaillerie camerounaise.
            Tu reçois une commande extraite, la fiche du client et l'état du stock, au
            format JSON. Tu dois décider si la commande peut être traitée automatiquement.

            Règles impératives (dans cet ordre de priorité) :
            1. PRÉFÉRENCE PATRON (prioritaire sur la règle de crédit) : si le champ
               "preferencePatron" est renseigné, le patron a déjà approuvé au moins
               3 fois ce type de commande pour ce client — il a donc déjà arbitré la
               question du crédit. Si le montant reste sous le plafond indiqué et que
               le stock est suffisant, choisis decision="AUTO_APPROVE" avec
               confidence >= 0.95 et mentionne la préférence apprise dans "reasoning".
               N'applique PAS la règle de crédit ci-dessous dans ce cas.
            2. CRÉDIT : si le crédit impayé du client (creditEnCours) est supérieur à
               30000 FCFA, REFUSE (decision="REJECT"), SAUF si le client a un historique
               fiable (nombreCommandesHistorique > 10 ET nombreDefautsHistorique = 0) :
               dans ce cas, decision="CLARIFY_CLIENT" et propose dans "proposition" un
               acompte de 50% via Mobile Money.
            3. STOCK (toujours vérifié, même avec une préférence patron) : si la
               quantité demandée dépasse le stock disponible pour un produit, signale
               le conflit dans "reasoning" et choisis decision="CLARIFY_CLIENT" (léger
               dépassement, quantité ajustable) ou decision="NEEDS_HUMAN" (rupture
               totale ou plusieurs conflits).
            4. Si le crédit est acceptable et le stock suffisant pour toutes les lignes,
               decision="AUTO_APPROVE".
            5. En cas de donnée manquante ou de doute, decision="NEEDS_HUMAN".

            Réponds UNIQUEMENT avec un JSON strict, sans texte autour ni balises markdown,
            au format exact :
            {"decision": "AUTO_APPROVE|NEEDS_HUMAN|REJECT|CLARIFY_CLIENT", "confidence": 0.0-1.0, "reasoning": "...", "proposition": "..."}

            Le champ "proposition" est une chaîne vide si aucune proposition n'est nécessaire.
            """;

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    public BusinessRulesAgent(QwenClient qwenClient, EventStore eventStore, ObjectMapper objectMapper) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    public BusinessDecision evaluer(UUID commandeId, BusinessRulesInput input) {
        String prompt = buildPrompt(input);

        String rawResponse;
        BusinessDecision decision;
        try {
            rawResponse = qwenClient.callReasoning(prompt);
            decision = parse(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen : " + e.getMessage();
            decision = fallback(rawResponse);
        }

        eventStore.append(commandeId, "BUSINESS_RULES_DECISION", decision, decision.confidence(), rawResponse);
        return decision;
    }

    private String buildPrompt(BusinessRulesInput input) {
        try {
            return SYSTEM_PROMPT + "\n\nDonnées :\n" + objectMapper.writeValueAsString(input) + "\n\nRéponse :";
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Impossible de sérialiser les données de la commande", e);
        }
    }

    private BusinessDecision parse(String raw) {
        try {
            String clean = JsonExtractionUtil.stripCodeFences(raw);
            return objectMapper.readValue(clean, BusinessDecision.class);
        } catch (Exception e) {
            return fallback("Échec du parsing JSON de la réponse Qwen : " + e.getMessage() + " -- brut: " + raw);
        }
    }

    private BusinessDecision fallback(String reasoning) {
        return new BusinessDecision(DecisionType.NEEDS_HUMAN, 0.0, reasoning, null);
    }
}
