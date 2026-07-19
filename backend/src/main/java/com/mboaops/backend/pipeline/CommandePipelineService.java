package com.mboaops.backend.pipeline;

import com.mboaops.backend.agents.business.BusinessDecision;
import com.mboaops.backend.agents.business.BusinessRulesAgent;
import com.mboaops.backend.agents.business.BusinessRulesInput;
import com.mboaops.backend.agents.extraction.ExtractionAgent;
import com.mboaops.backend.agents.extraction.ExtractionLigne;
import com.mboaops.backend.agents.fusion.FusionResult;
import com.mboaops.backend.agents.fusion.FusionService;
import com.mboaops.backend.agents.orchestrator.OrchestrationResult;
import com.mboaops.backend.agents.orchestrator.OrchestratorAgent;
import com.mboaops.backend.agents.router.Intention;
import com.mboaops.backend.agents.router.RouterAgent;
import com.mboaops.backend.agents.router.RouterDecision;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.domain.commande.LigneCommande;
import com.mboaops.backend.domain.produit.Produit;
import com.mboaops.backend.domain.produit.ProduitRepository;
import com.mboaops.backend.domain.produit.StockService;
import com.mboaops.backend.eventstore.EventStore;
import com.mboaops.backend.memoire.MemoryService;
import com.mboaops.backend.notifications.NotificationService;
import com.mboaops.backend.paiements.PaiementService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chaîne complète de traitement d'un message entrant :
 * MESSAGE_RECU -> RouterAgent -> [si COMMANDE] extraction (texte ou
 * multimodal) -> fusion/conflits -> BusinessRulesAgent (stock + crédit)
 * -> politique HITL de l'orchestrateur -> application du verdict
 * (auto-approbation + devis, DecisionCard, ou escalade). Chaque étape
 * journalise ses événements dans l'event store.
 *
 * Volontairement hors transaction globale : chaque étape persiste son
 * propre état pour ne pas tenir de connexion pendant les appels LLM.
 */
@Service
public class CommandePipelineService {

    private final RouterAgent routerAgent;
    private final ExtractionAgent extractionAgent;
    private final FusionService fusionService;
    private final BusinessRulesAgent businessRulesAgent;
    private final OrchestratorAgent orchestratorAgent;
    private final StockService stockService;
    private final ProduitRepository produitRepository;
    private final CommandeRepository commandeRepository;
    private final EventStore eventStore;
    private final NotificationService notificationService;
    private final PaiementService paiementService;
    private final MemoryService memoryService;

    public CommandePipelineService(RouterAgent routerAgent,
                                   ExtractionAgent extractionAgent,
                                   FusionService fusionService,
                                   BusinessRulesAgent businessRulesAgent,
                                   OrchestratorAgent orchestratorAgent,
                                   StockService stockService,
                                   ProduitRepository produitRepository,
                                   CommandeRepository commandeRepository,
                                   EventStore eventStore,
                                   NotificationService notificationService,
                                   PaiementService paiementService,
                                   MemoryService memoryService) {
        this.routerAgent = routerAgent;
        this.extractionAgent = extractionAgent;
        this.fusionService = fusionService;
        this.businessRulesAgent = businessRulesAgent;
        this.orchestratorAgent = orchestratorAgent;
        this.stockService = stockService;
        this.produitRepository = produitRepository;
        this.commandeRepository = commandeRepository;
        this.eventStore = eventStore;
        this.notificationService = notificationService;
        this.paiementService = paiementService;
        this.memoryService = memoryService;
    }

    public ResultatPipeline traiterTexte(Commande commande, String texte) {
        RouterDecision routage = routerAgent.classifier(commande.getId(), texte);
        if (routage.intention() != Intention.COMMANDE) {
            return new ResultatPipeline(commande.getStatut().name(),
                    routage.intention().name(), null, null, null);
        }
        List<ExtractionLigne> lignes = extractionAgent.extractFromTexte(commande.getId(), texte);
        return pipelineCommande(commande, routage.intention().name(), lignes, List.of());
    }

    public ResultatPipeline traiterMultimodal(Commande commande,
                                              String texte,
                                              String audioBase64,
                                              String audioFormat,
                                              String imageBase64,
                                              String imageMimeType) {
        String transcript = null;
        if (audioBase64 != null) {
            transcript = extractionAgent.transcribeAudio(commande.getId(), audioBase64, audioFormat);
        }

        // Le routeur classifie le texte disponible (message écrit ou
        // transcription du vocal). Une photo seule est traitée comme une
        // commande : c'est l'usage attendu d'une liste manuscrite.
        String texteRouter = (texte != null && !texte.isBlank()) ? texte : transcript;
        String intention = Intention.COMMANDE.name();
        if (texteRouter != null && !texteRouter.isBlank()) {
            RouterDecision routage = routerAgent.classifier(commande.getId(), texteRouter);
            intention = routage.intention().name();
            if (routage.intention() != Intention.COMMANDE) {
                return new ResultatPipeline(commande.getStatut().name(), intention, null, null, null);
            }
        }

        List<ExtractionLigne> depuisAudio = List.of();
        if (transcript != null && !transcript.isBlank()) {
            depuisAudio = extractionAgent.extractFromTexte(commande.getId(), transcript);
        }
        List<ExtractionLigne> depuisImage = List.of();
        if (imageBase64 != null) {
            depuisImage = extractionAgent.extractFromImage(commande.getId(), imageBase64, imageMimeType);
        }

        return pipelineCommande(commande, intention, depuisAudio, depuisImage);
    }

    private ResultatPipeline pipelineCommande(Commande commande,
                                              String intention,
                                              List<ExtractionLigne> depuisAudio,
                                              List<ExtractionLigne> depuisImage) {
        if (depuisAudio.isEmpty() && depuisImage.isEmpty()) {
            // Rien d'exploitable : la commande reste RECUE, un humain prendra
            // le relais.
            return new ResultatPipeline(commande.getStatut().name(), intention, null, null, null);
        }

        FusionResult fusion = fusionService.fusionner(commande, depuisAudio, depuisImage);
        if (fusion.aDesConflits()) {
            notificationService.envoyer(commande.getId(),
                    commande.getClient().getTelephone(), fusion.messageClarification());
            return new ResultatPipeline(commande.getStatut().name(), intention,
                    null, null, fusion.messageClarification());
        }

        return evaluerEtOrchestrer(commande, intention, fusion.lignes());
    }

    private ResultatPipeline evaluerEtOrchestrer(Commande commande,
                                                 String intention,
                                                 List<ExtractionLigne> lignes) {
        List<BusinessRulesInput.LigneDemandee> demandes = new ArrayList<>();
        BigDecimal montantTotal = BigDecimal.ZERO;

        for (ExtractionLigne ligne : lignes) {
            Optional<Produit> produit = chercherProduitAvecFallback(ligne.produit());
            int stock = produit.map(Produit::getStock).orElse(0);
            BigDecimal prix = produit.map(Produit::getPrixUnitaire).orElse(BigDecimal.ZERO);

            demandes.add(new BusinessRulesInput.LigneDemandee(
                    ligne.produit(), ligne.quantite(), stock, prix));

            if (produit.isPresent()) {
                commande.getLignes().add(new LigneCommande(
                        commande, produit.get(), ligne.quantite(), produit.get().getPrixUnitaire()));
                montantTotal = montantTotal.add(
                        prix.multiply(BigDecimal.valueOf(ligne.quantite())));
            }
        }

        commande.setMontantTotal(montantTotal);
        Commande sauvegardee = commandeRepository.save(commande);

        List<Commande> historique = commandeRepository.findByClientId(sauvegardee.getClient().getId());
        int nbCommandes = (int) historique.stream()
                .filter(c -> !c.getId().equals(sauvegardee.getId()))
                .count();
        int nbDefauts = (int) historique.stream()
                .filter(c -> c.getStatut() == CommandeStatut.REJETEE)
                .count();

        Optional<String> preference = memoryService.preferencePour(
                sauvegardee.getClient().getId(), montantTotal);

        BusinessRulesInput input = new BusinessRulesInput(
                sauvegardee.getClient().getNom(),
                sauvegardee.getClient().getCreditEnCours(),
                nbCommandes,
                nbDefauts,
                demandes,
                preference.orElse(null));

        BusinessDecision decision = businessRulesAgent.evaluer(sauvegardee.getId(), input);
        OrchestrationResult orchestration = orchestratorAgent.orchestrer(
                sauvegardee, decision, preference.isPresent());

        return appliquerVerdict(sauvegardee, intention, decision, orchestration);
    }

    private ResultatPipeline appliquerVerdict(Commande commande,
                                              String intention,
                                              BusinessDecision decision,
                                              OrchestrationResult orchestration) {
        UUID cardId = orchestration.decisionCard() != null
                ? orchestration.decisionCard().getId() : null;

        switch (orchestration.type()) {
            case AUTO -> {
                switch (decision.decision()) {
                    case AUTO_APPROVE -> {
                        commande.changerStatut(CommandeStatut.VALIDEE_STOCK);
                        commande.changerStatut(CommandeStatut.EN_ATTENTE_PATRON);
                        commande.changerStatut(CommandeStatut.APPROUVEE);
                        commandeRepository.save(commande);
                        genererEtEnvoyerDevis(commande);
                    }
                    case REJECT -> {
                        commande.changerStatut(CommandeStatut.REJETEE);
                        commandeRepository.save(commande);
                    }
                    case CLARIFY_CLIENT -> {
                        commande.changerStatut(CommandeStatut.EN_CLARIFICATION);
                        commandeRepository.save(commande);
                        if (decision.proposition() != null && !decision.proposition().isBlank()) {
                            notificationService.envoyer(commande.getId(),
                                    commande.getClient().getTelephone(), decision.proposition());
                        }
                    }
                    // Décision NEEDS_HUMAN malgré une confiance élevée :
                    // on respecte la demande du modèle et on route vers l'humain.
                    case NEEDS_HUMAN -> mettreEnAttentePatron(commande);
                }
            }
            case DECISION_CARD, ESCALADE -> mettreEnAttentePatron(commande);
        }

        return new ResultatPipeline(commande.getStatut().name(), intention,
                decision.decision().name(), cardId, null);
    }

    private void mettreEnAttentePatron(Commande commande) {
        commande.changerStatut(CommandeStatut.VALIDEE_STOCK);
        commande.changerStatut(CommandeStatut.EN_ATTENTE_PATRON);
        commandeRepository.save(commande);
    }

    /**
     * Génère le devis (déterministe, à partir des lignes persistées),
     * journalise DEVIS_GENERE, l'envoie via la cascade de notification et
     * passe la commande à DEVIS_ENVOYE. Appelé après APPROUVEE (auto ou
     * décision patron).
     */
    public void genererEtEnvoyerDevis(Commande commande) {
        String lienPaiement = paiementService.genererLienPaiement(commande);

        StringBuilder corps = new StringBuilder("Devis MBOA-OPS\n");
        for (LigneCommande ligne : commande.getLignes()) {
            corps.append("- ").append(ligne.getProduit().getNom())
                    .append(" x ").append(ligne.getQuantite())
                    .append(" = ").append(ligne.getPrixUnitaire()
                            .multiply(BigDecimal.valueOf(ligne.getQuantite())))
                    .append(" FCFA\n");
        }
        corps.append("Total : ").append(commande.getMontantTotal()).append(" FCFA");
        corps.append("\nPayer par Mobile Money : ").append(lienPaiement);

        eventStore.append(commande.getId(), "DEVIS_GENERE",
                Map.of("montantTotal", commande.getMontantTotal(), "devis", corps.toString()),
                null, null);
        notificationService.envoyer(commande.getId(),
                commande.getClient().getTelephone(), corps.toString());

        commande.changerStatut(CommandeStatut.DEVIS_ENVOYE);
        commandeRepository.save(commande);
    }

    /**
     * Recherche produit protégée par le circuit "stock". Si le circuit est
     * ouvert, on retombe sur une lecture directe façon "cache local
     * potentiellement périmé" : le pipeline continue, et l'orchestrateur
     * dégradera la confiance puisqu'un circuit est ouvert.
     */
    private Optional<Produit> chercherProduitAvecFallback(String nomProduit) {
        try {
            return chercherAvecSingulier(nom -> stockService.chercherProduit(nom), nomProduit);
        } catch (Exception circuitOuvert) {
            return chercherAvecSingulier(nom -> {
                List<Produit> produits = produitRepository.findByNomContainingIgnoreCase(nom);
                return produits.isEmpty() ? Optional.empty() : Optional.of(produits.get(0));
            }, nomProduit);
        }
    }

    /**
     * L'extraction LLM et le catalogue ne s'accordent pas toujours sur le
     * pluriel ou les mots complémentaires ("tôle ondulée" vs "Tôles ondulées
     * (feuille 2m)"). On essaie des variantes de plus en plus courtes :
     * nom complet, nom sans "s" final, premier mot, premier mot au singulier.
     */
    private Optional<Produit> chercherAvecSingulier(
            java.util.function.Function<String, Optional<Produit>> recherche, String nomProduit) {
        String nom = nomProduit.toLowerCase(Locale.ROOT).trim();
        List<String> variantes = new ArrayList<>();
        variantes.add(nom);
        if (nom.endsWith("s")) {
            variantes.add(nom.substring(0, nom.length() - 1));
        }
        String premierMot = nom.split("\\s+")[0];
        if (!premierMot.equals(nom)) {
            variantes.add(premierMot);
            if (premierMot.endsWith("s")) {
                variantes.add(premierMot.substring(0, premierMot.length() - 1));
            }
        }

        for (String variante : variantes) {
            Optional<Produit> produit = recherche.apply(variante);
            if (produit.isPresent()) {
                return produit;
            }
        }
        return Optional.empty();
    }
}
