package com.mboaops.backend.paiements;

import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rapproche un paiement Mobile Money entrant (SMS parsé) d'une commande en
 * attente de paiement (DEVIS_ENVOYE) : correspondance sur le client
 * (tokens du nom présents dans l'expéditeur du SMS) et sur le montant
 * (total exact, ou acompte de 50% du total). Succès -> PAIEMENT_RECONCILIE
 * + statut PAYEE.
 */
@Service
public class ReconciliationService {

    /** Flux d'événements des SMS MoMo non rattachés à une commande. */
    static final UUID MOMO_STREAM =
            UUID.nameUUIDFromBytes("momo-webhook".getBytes(StandardCharsets.UTF_8));

    private static final Set<String> TITRES = Set.of("mme", "m.", "m", "mr", "mlle", "madame", "monsieur");

    private final PaiementService paiementService;
    private final CommandeRepository commandeRepository;
    private final EventStore eventStore;

    public ReconciliationService(PaiementService paiementService,
                                 CommandeRepository commandeRepository,
                                 EventStore eventStore) {
        this.paiementService = paiementService;
        this.commandeRepository = commandeRepository;
        this.eventStore = eventStore;
    }

    public ReconciliationResult reconcilier(String smsBrut) {
        eventStore.append(MOMO_STREAM, "SMS_MOMO_RECU", Map.of("sms", smsBrut), null, null);

        SmsMomoParse parse = paiementService.parserSmsMomo(smsBrut);
        if (parse == null || parse.montant() == null) {
            eventStore.append(MOMO_STREAM, "PAIEMENT_NON_RECONCILIE",
                    Map.of("sms", smsBrut), null, "SMS inexploitable par le parseur");
            return ReconciliationResult.echec(parse, "SMS inexploitable");
        }

        // Un paiement peut solder un devis envoyé OU une commande à crédit
        // accordé (le client à crédit finit par régler, éventuellement par MoMo).
        List<Commande> enAttente = new java.util.ArrayList<>(
                commandeRepository.findByStatut(CommandeStatut.DEVIS_ENVOYE));
        enAttente.addAll(commandeRepository.findByStatut(CommandeStatut.CREDIT_ACCORDE));
        for (Commande commande : enAttente) {
            if (!clientCorrespond(parse.expediteur(), commande.getClient().getNom())) {
                continue;
            }
            BigDecimal total = commande.getMontantTotal();
            BigDecimal moitie = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            boolean totalExact = parse.montant().compareTo(total) == 0;
            boolean acompte = !totalExact && parse.montant().compareTo(moitie) == 0;
            if (!totalExact && !acompte) {
                continue;
            }

            eventStore.append(commande.getId(), "PAIEMENT_RECONCILIE",
                    Map.of("montant", parse.montant(),
                            "expediteur", parse.expediteur(),
                            "reference", parse.reference(),
                            "acompte", acompte),
                    null,
                    acompte ? "Acompte de 50% rapproché du devis de " + total + " FCFA"
                            : "Paiement total rapproché du devis");
            commande.changerStatut(CommandeStatut.PAYEE);
            commandeRepository.save(commande);
            return ReconciliationResult.succes(commande.getId(), parse, acompte);
        }

        eventStore.append(MOMO_STREAM, "PAIEMENT_NON_RECONCILIE",
                Map.of("montant", parse.montant(),
                        "expediteur", parse.expediteur(),
                        "reference", parse.reference()),
                null, "Aucune commande DEVIS_ENVOYE ne correspond (client + montant)");
        return ReconciliationResult.echec(parse, "Aucune commande correspondante");
    }

    /**
     * "NGO MARIE" doit matcher la cliente "Mme Ngo" : on vérifie qu'au moins
     * un token significatif du nom client (hors titres de civilité) apparaît
     * dans l'expéditeur du SMS.
     */
    private boolean clientCorrespond(String expediteur, String nomClient) {
        if (expediteur == null) {
            return false;
        }
        String exp = normaliser(expediteur);
        for (String token : normaliser(nomClient).split("\\s+")) {
            if (token.length() >= 3 && !TITRES.contains(token) && exp.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
