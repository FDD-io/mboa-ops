package com.mboaops.backend.agents.business;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboaops.backend.agents.JsonExtractionUtil;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.config.QwenProperties;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
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

            Le champ "messageClient" est le texte brut du client. Détecte s'il
            demande un CRÉDIT ou un PAIEMENT DIFFÉRÉ (ex. "je paye lundi", "je paye le
            mois prochain", "faites-moi crédit", "je paye après", "à crédit").

            Règles impératives (dans cet ordre de priorité) :
            1. PRÉFÉRENCE PATRON (prioritaire sur la règle de crédit) : si le champ
               "preferencePatron" est renseigné, le patron a déjà approuvé au moins
               3 fois ce type de commande pour ce client — il a donc déjà arbitré la
               question du crédit. Si le montant reste sous le plafond indiqué et que
               le stock est suffisant, choisis decision="AUTO_APPROVE" avec
               confidence >= 0.95 et mentionne la préférence apprise dans "reasoning".
               N'applique PAS la règle de crédit ci-dessous dans ce cas.
            2. CRÉDIT — CLIENT SANS HISTORIQUE (nombreCommandesHistorique == 0) : s'il
               a un creditEnCours > 0 OU demande un crédit/paiement différé, alors
               decision="REJECT". Motif clair dans "reasoning" : un historique de
               commandes payées est nécessaire avant tout crédit. (Le patron ne sera
               jamais sollicité pour un nouveau client.)
            3. CRÉDIT — CLIENT AVEC HISTORIQUE (nombreCommandesHistorique >= 1) : si
               creditEnCours > 30000 FCFA OU demande de crédit/paiement différé, alors
               decision="CLARIFY_CLIENT" (le patron tranchera), QUEL QUE SOIT le
               montant, et propose dans "proposition" un acompte via Mobile Money.
            4. STOCK (toujours vérifié) : si la quantité demandée dépasse le stock
               disponible pour un produit, signale le conflit dans "reasoning" et
               choisis decision="CLARIFY_CLIENT" (léger dépassement, quantité
               ajustable) ou decision="NEEDS_HUMAN" (rupture totale ou plusieurs
               conflits).
            5. Si aucun problème de crédit et le stock suffisant pour toutes les
               lignes, decision="AUTO_APPROVE".
            6. En cas de donnée manquante ou de doute, decision="NEEDS_HUMAN".

            Réponds UNIQUEMENT avec un JSON strict, sans texte autour ni balises markdown,
            au format exact :
            {"decision": "AUTO_APPROVE|NEEDS_HUMAN|REJECT|CLARIFY_CLIENT", "confidence": 0.0-1.0, "reasoning": "...", "proposition": "..."}

            Le champ "proposition" contient TOUJOURS une action concrète, courte et
            actionnable (une phrase impérative, ex. "Proposer un acompte de 50% via
            Mobile Money." ou "Approuver et envoyer le devis."). Ce n'est JAMAIS une
            répétition ni un résumé du champ "reasoning".
            """;

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final QwenProperties qwenProperties;

    public BusinessRulesAgent(QwenClient qwenClient, EventStore eventStore,
                              ObjectMapper objectMapper, QwenProperties qwenProperties) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.qwenProperties = qwenProperties;
    }

    public BusinessDecision evaluer(UUID commandeId, BusinessRulesInput input) {
        String prompt = buildPrompt(input);
        boolean complexe = estCasComplexe(input);
        String modele = complexe ? qwenProperties.getModelReasoning() : qwenProperties.getModelFast();

        long debut = System.nanoTime();
        String rawResponse;
        BusinessDecision decision;
        try {
            rawResponse = complexe ? qwenClient.callReasoning(prompt) : qwenClient.callFast(prompt);
            decision = parse(rawResponse);
        } catch (Exception e) {
            rawResponse = "Échec de l'appel Qwen : " + e.getMessage();
            decision = fallback(rawResponse);
        }
        long durationMs = (System.nanoTime() - debut) / 1_000_000;

        eventStore.append(commandeId, "BUSINESS_RULES_DECISION",
                java.util.Map.of(
                        "decision", decision.decision(),
                        "confidence", decision.confidence(),
                        "reasoning", decision.reasoning() == null ? "" : decision.reasoning(),
                        "proposition", decision.proposition() == null ? "" : decision.proposition(),
                        "modele", modele,
                        "durationMs", durationMs),
                decision.confidence(), rawResponse);
        return decision;
    }

    /**
     * Le modèle de raisonnement n'est sollicité que quand plusieurs règles
     * peuvent entrer en tension : crédit en cours, préférence patron à
     * arbitrer, stock insuffisant sur au moins une ligne, ou historique de
     * défauts. Le cas nominal (crédit 0, stock large, aucun signal) est
     * tranché par le modèle rapide.
     */
    private static final List<String> INDICES_CREDIT = List.of(
            "credit", "crédit", "paye", "payer", "paie", "acompte", "differe", "différé",
            "plus tard", "lundi", "mois prochain", "apres", "après", "dette", "avance");

    private boolean estCasComplexe(BusinessRulesInput input) {
        boolean stockInsuffisant = input.lignes().stream()
                .anyMatch(l -> l.quantiteDemandee() > l.stockDisponible());
        return input.creditEnCours().signum() > 0
                || input.preferencePatron() != null
                || stockInsuffisant
                || input.nombreDefautsHistorique() > 0
                || demandeCreditProbable(input.messageClient());
    }

    private boolean demandeCreditProbable(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return INDICES_CREDIT.stream().anyMatch(m::contains);
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
