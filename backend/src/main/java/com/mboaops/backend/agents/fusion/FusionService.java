package com.mboaops.backend.agents.fusion;

import com.mboaops.backend.agents.extraction.ExtractionLigne;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.eventstore.EventStore;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fusionne les extractions issues des différents canaux d'un même message
 * (vocal transcrit et liste photographiée). En cas de conflit de quantité
 * sur un même produit : événement CONFLIT_DETECTE, passage de la commande
 * en EN_CLARIFICATION et génération d'un message de clarification au client
 * (ton poli camerounais) via le modèle rapide.
 */
@Service
public class FusionService {

    private static final String PROMPT_CLARIFICATION = """
            Tu es l'assistant d'une quincaillerie camerounaise. Un client a envoyé un
            vocal ET une liste écrite, mais les quantités ne correspondent pas.
            Rédige UN SEUL court message WhatsApp (2 phrases max) pour lui demander
            poliment la bonne quantité, sur un ton chaleureux camerounais.
            Exemple de ton : "Maman, sur la vocale j'entends 3 cartons mais sur la
            liste je vois 5 — c'est combien finalement ?"
            Réponds uniquement avec le message, sans guillemets ni explication.

            Conflits : %s
            """;

    private final QwenClient qwenClient;
    private final EventStore eventStore;
    private final CommandeRepository commandeRepository;

    public FusionService(QwenClient qwenClient, EventStore eventStore, CommandeRepository commandeRepository) {
        this.qwenClient = qwenClient;
        this.eventStore = eventStore;
        this.commandeRepository = commandeRepository;
    }

    public FusionResult fusionner(Commande commande,
                                  List<ExtractionLigne> depuisAudio,
                                  List<ExtractionLigne> depuisImage) {
        List<ConflitQuantite> conflits = new ArrayList<>();
        Map<String, ExtractionLigne> fusionnees = new LinkedHashMap<>();

        for (ExtractionLigne ligne : depuisAudio) {
            fusionnees.put(normaliser(ligne.produit()), ligne);
        }
        for (ExtractionLigne ligneImage : depuisImage) {
            String cle = trouverCleCorrespondante(fusionnees, ligneImage.produit());
            if (cle == null) {
                fusionnees.put(normaliser(ligneImage.produit()), ligneImage);
                continue;
            }
            ExtractionLigne ligneAudio = fusionnees.get(cle);
            if (ligneAudio.quantite() != ligneImage.quantite()) {
                conflits.add(new ConflitQuantite(ligneImage.produit(),
                        ligneAudio.quantite(), ligneImage.quantite()));
            } else if (ligneImage.confidence() > ligneAudio.confidence()) {
                fusionnees.put(cle, ligneImage);
            }
        }

        List<ExtractionLigne> lignes = new ArrayList<>(fusionnees.values());

        if (conflits.isEmpty()) {
            avancerVers(commande, CommandeStatut.EXTRAITE);
            eventStore.append(commande.getId(), "FUSION_REUSSIE", lignes, null, null);
            return new FusionResult(lignes, List.of(), null);
        }

        eventStore.append(commande.getId(), "CONFLIT_DETECTE", conflits, null,
                "Quantités divergentes entre le vocal et la liste écrite");
        avancerVers(commande, CommandeStatut.EXTRAITE);
        avancerVers(commande, CommandeStatut.EN_CLARIFICATION);

        String message = genererMessageClarification(commande, conflits);
        return new FusionResult(lignes, conflits, message);
    }

    private String genererMessageClarification(Commande commande, List<ConflitQuantite> conflits) {
        StringBuilder detail = new StringBuilder();
        for (ConflitQuantite c : conflits) {
            detail.append(c.produit())
                    .append(" : vocal=").append(c.quantiteAudio())
                    .append(", liste=").append(c.quantiteImage())
                    .append(". ");
        }

        String message;
        String reasoning = null;
        try {
            message = qwenClient.callFast(PROMPT_CLARIFICATION.formatted(detail)).trim();
        } catch (Exception e) {
            ConflitQuantite premier = conflits.get(0);
            message = "Maman, sur la vocale j'entends " + premier.quantiteAudio()
                    + " mais sur la liste je vois " + premier.quantiteImage()
                    + " pour " + premier.produit() + " — c'est combien finalement ?";
            reasoning = "Fallback statique, échec de l'appel Qwen : " + e.getMessage();
        }

        eventStore.append(commande.getId(), "MESSAGE_CLARIFICATION_GENERE", message, null, reasoning);
        return message;
    }

    private void avancerVers(Commande commande, CommandeStatut statut) {
        commande.changerStatut(statut);
        commandeRepository.save(commande);
    }

    /**
     * Les deux canaux ne nomment pas toujours le produit à l'identique
     * ("savon" vs "cartons de savon") : on considère qu'il s'agit du même
     * produit si un nom normalisé contient l'autre.
     */
    private String trouverCleCorrespondante(Map<String, ExtractionLigne> fusionnees, String produit) {
        String cible = normaliser(produit);
        for (String cle : fusionnees.keySet()) {
            if (cle.contains(cible) || cible.contains(cle)) {
                return cle;
            }
        }
        return null;
    }

    private String normaliser(String produit) {
        String sansAccents = Normalizer.normalize(produit, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT).trim();
    }
}
