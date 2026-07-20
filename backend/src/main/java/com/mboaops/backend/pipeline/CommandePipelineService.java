package com.mboaops.backend.pipeline;

import com.mboaops.backend.agents.business.BusinessDecision;
import com.mboaops.backend.agents.business.BusinessRulesAgent;
import com.mboaops.backend.agents.business.BusinessRulesInput;
import com.mboaops.backend.agents.business.DecisionType;
import com.mboaops.backend.agents.extraction.ExtractionAgent;
import com.mboaops.backend.agents.extraction.ExtractionLigne;
import com.mboaops.backend.agents.fusion.FusionResult;
import com.mboaops.backend.agents.fusion.FusionService;
import com.mboaops.backend.agents.orchestrator.OrchestrationResult;
import com.mboaops.backend.agents.orchestrator.OrchestratorAgent;
import com.mboaops.backend.agents.qwen.QwenClient;
import com.mboaops.backend.agents.router.Intention;
import com.mboaops.backend.agents.router.RouterAgent;
import com.mboaops.backend.agents.router.RouterDecision;
import com.mboaops.backend.conversation.ConversationContext;
import com.mboaops.backend.conversation.ConversationService;
import com.mboaops.backend.conversation.ConversationStatut;
import com.mboaops.backend.domain.client.Client;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Chaîne complète de traitement d'un message entrant, avec MÉMOIRE
 * CONVERSATIONNELLE. Avant tout routage, on vérifie s'il existe un contexte
 * actif pour ce client : si oui, le message est interprété comme une réponse
 * au sujet en cours (ex. "5 barres" après "vous avez du fer à béton ?"),
 * pas comme un nouveau sujet isolé. Sinon : le routeur classifie, une
 * commande n'est créée que pour une intention COMMANDE, une QUESTION reçoit
 * une réponse catalogue (et ouvre un contexte si elle porte sur un produit
 * précis). Ensuite : extraction -> fusion -> catalogue -> règles métier
 * (crédit + stock) -> politique HITL. Chaque étape journalise ses événements.
 *
 * Volontairement hors transaction globale : chaque étape persiste son propre
 * état pour ne pas tenir de connexion pendant les appels LLM.
 */
@Service
public class CommandePipelineService {

    private static final Set<String> STOPWORDS = Set.of(
            "chantier", "maison", "travaux", "pour", "avec", "votre", "vos", "avez");

    private static final String PROMPT_QUESTION = """
            Tu es le vendeur WhatsApp de MBOA-OPS, une quincaillerie camerounaise.
            Ton chaleureux de commerçant camerounais (maman, papa, mon frère...).

            CONTRAINTES STRICTES (style WhatsApp, jamais de pavé) :
            - Maximum 3 phrases courtes OU 6 lignes au total.
            - Ne JAMAIS lister tout le catalogue : cite au maximum 4-5 produits
              phares, en liste courte du type "- Ciment CIMENCAM : 6500 F/sac".
            - Si le client demande une catégorie précise (ex. la peinture),
              réponds UNIQUEMENT sur cette catégorie, rien d'autre.
            - Termine par une courte question d'engagement
              (ex. "Vous cherchez quoi exactement ?").
            Appuie-toi UNIQUEMENT sur le catalogue disponible ci-dessous.
            Réponds uniquement avec le message, sans markdown.

            Catalogue disponible :
            %s

            Question du client : "%s"
            """;

    private static final String PROMPT_PRODUIT_INCONNU = """
            Tu es le vendeur WhatsApp de MBOA-OPS, une quincaillerie camerounaise.
            Un client demande des produits que nous ne vendons PAS : %s.
            %s

            CONTRAINTES STRICTES (style WhatsApp, jamais de pavé) :
            - UN SEUL message, maximum 3 phrases courtes, ton chaleureux
              camerounais ("Désolé maman/papa...").
            - Dis qu'on ne vend pas ce(s) produit(s).
            - Si des produits connus sont gardés de côté, cite-les avec leurs
              QUANTITÉS EXACTES (ex. "vos 2 sacs de ciment sont mis de côté").
            - Propose au plus 2 produits proches du catalogue SI pertinent,
              sinon invite simplement à voir le catalogue.
            Réponds uniquement avec le message, sans guillemets ni markdown.

            Catalogue disponible :
            %s
            """;

    private static final String PROMPT_REFUS = """
            Tu es le vendeur WhatsApp de MBOA-OPS, une quincaillerie camerounaise.
            Rédige UN SEUL message court (max 3 phrases), ton chaleureux camerounais,
            pour refuser poliment une commande. Motif à expliquer simplement : %s.
            Reste positif et invite à revenir. Réponds uniquement avec le message,
            sans guillemets ni markdown.
            """;

    private static final String PROMPT_REFORMULATION = """
            Tu es le vendeur WhatsApp de MBOA-OPS. Le patron a répondu à une demande
            client (action=%s, commentaire du patron="%s").
            Reformule cette décision en UN SEUL message WhatsApp chaleureux et naturel
            POUR LE CLIENT, à la première personne du vendeur, maximum 3 phrases.
            Ne cite JAMAIS le patron mot pour mot, n'emploie aucun terme interne.
            Si une condition (acompte, pourcentage) est mentionnée, explique-la
            simplement. Réponds uniquement avec le message, sans guillemets ni markdown.
            """;

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
    private final ConversationService conversationService;
    private final QwenClient qwenClient;

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
                                   MemoryService memoryService,
                                   ConversationService conversationService,
                                   QwenClient qwenClient) {
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
        this.conversationService = conversationService;
        this.qwenClient = qwenClient;
    }

    public ResultatPipeline traiterMessageTexte(Client client, UUID messageId, String texte) {
        Optional<ConversationContext> contexte = conversationService.contexteActif(client.getTelephone());
        if (contexte.isPresent()) {
            ResultatPipeline suite = poursuivreConversation(client, messageId, texte, contexte.get());
            if (suite != null) {
                return suite;
            }
        }
        return routerEtTraiter(client, messageId, texte);
    }

    public ResultatPipeline traiterMessageMultimodal(Client client,
                                                     UUID messageId,
                                                     String texte,
                                                     String audioBase64,
                                                     String audioFormat,
                                                     String imageBase64,
                                                     String imageMimeType) {
        // Transcription (ASR) et lecture de l'image (VL) sont indépendantes :
        // elles partent en parallèle, leurs événements sur l'agrégat du message.
        CompletableFuture<String> futurTranscript = audioBase64 != null
                ? CompletableFuture.supplyAsync(
                        () -> extractionAgent.transcribeAudio(messageId, audioBase64, audioFormat))
                : CompletableFuture.completedFuture(null);
        CompletableFuture<List<ExtractionLigne>> futurImage = imageBase64 != null
                ? CompletableFuture.supplyAsync(
                        () -> extractionAgent.extractFromImage(messageId, imageBase64, imageMimeType))
                : CompletableFuture.completedFuture(List.of());

        String transcript = futurTranscript.join();

        // Le routeur classifie le texte disponible (message écrit ou
        // transcription du vocal). Une photo seule est traitée comme une
        // commande : c'est l'usage attendu d'une liste manuscrite.
        String texteRouter = (texte != null && !texte.isBlank()) ? texte : transcript;
        String intention = Intention.COMMANDE.name();
        if (texteRouter != null && !texteRouter.isBlank()) {
            RouterDecision routage = routerAgent.classifier(messageId, texteRouter);
            intention = routage.intention().name();
            if (routage.intention() == Intention.QUESTION) {
                String reponse = repondreQuestionEtOuvrirContexte(messageId, client, texteRouter);
                return new ResultatPipeline(null, null, intention, null, null, reponse);
            }
            if (routage.intention() != Intention.COMMANDE) {
                return new ResultatPipeline(null, null, intention, null, null, null);
            }
        }

        Commande commande = commandeRepository.save(
                new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));

        List<ExtractionLigne> depuisAudio = List.of();
        if (transcript != null && !transcript.isBlank()) {
            depuisAudio = extractionAgent.extractFromTexte(commande.getId(), transcript);
        }
        List<ExtractionLigne> depuisImage = futurImage.join();

        return pipelineCommande(commande, intention, depuisAudio, depuisImage, texteRouter);
    }

    // --- Mémoire conversationnelle -----------------------------------------

    /**
     * Poursuit un échange en cours. Retourne null si le contexte n'implique
     * aucune poursuite particulière (le routage normal reprend la main).
     */
    private ResultatPipeline poursuivreConversation(Client client, UUID messageId,
                                                    String texte, ConversationContext contexte) {
        if (contexte.getStatut() == ConversationStatut.EN_ATTENTE_CONFIRMATION) {
            String msg = "Votre demande est toujours chez le patron, je reviens vers vous "
                    + "dès qu'il me répond 🙏";
            eventStore.append(messageId, "MESSAGE_ATTENTE_ENVOYE",
                    Map.of("message", msg, "clientPhone", client.getTelephone()), null,
                    "Client relance pendant l'attente patron");
            notificationService.envoyer(messageId, client.getTelephone(), msg);
            return new ResultatPipeline(null, null, "SUIVI", null, null, msg);
        }

        if (contexte.getStatut() == ConversationStatut.EN_PRECISION) {
            eventStore.append(messageId, "MESSAGE_INTERPRETE_COMME_SUIVI",
                    Map.of("clientPhone", client.getTelephone(),
                            "sujet", contexte.getSujet() == null ? "" : contexte.getSujet(),
                            "message", texte),
                    null, "Message interprété comme réponse au sujet en cours");

            // Si le contexte référence déjà une commande (conflit, produit
            // inconnu, proposition d'acompte), on la RÉUTILISE : la résolution
            // reste sur la même commande. Sinon (suite d'une simple question),
            // on en crée une.
            Commande commande = reutiliserOuCreerCommande(client, contexte.getCommandeId());
            List<ExtractionLigne> lignes = extractionAgent.extractAvecContexte(
                    commande.getId(), contexte.getSujet(), texte);
            if (lignes.isEmpty()) {
                // Réponse inexploitable : on garde le sujet ouvert et on relance.
                commande.changerStatut(CommandeStatut.REJETEE);
                commandeRepository.save(commande);
                String msg = "Pardon, je n'ai pas bien saisi. Vous voulez combien exactement ?";
                eventStore.append(commande.getId(), "MESSAGE_CLARIFICATION_GENERE",
                        Map.of("message", msg), null, "Réponse de suivi inexploitable");
                notificationService.envoyer(commande.getId(), client.getTelephone(), msg);
                return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                        "COMMANDE", null, null, msg);
            }
            return pipelineCommande(commande, Intention.COMMANDE.name(), lignes, List.of(), texte);
        }

        return null;
    }

    /**
     * Réutilise la commande référencée par le contexte (résolution sur la même
     * commande, sans doublon), en réinitialisant ses lignes pour qu'elles
     * soient reconstruites à partir de la réponse du client. À défaut, en crée
     * une nouvelle.
     */
    private Commande reutiliserOuCreerCommande(Client client, UUID commandeId) {
        if (commandeId != null) {
            // Chargement avec lignes initialisées : le pipeline est hors
            // transaction, un accès LAZY échouerait.
            Optional<Commande> existante = commandeRepository.findByIdWithLignes(commandeId);
            if (existante.isPresent()) {
                Commande commande = existante.get();
                commande.getLignes().clear();
                commande.setMontantTotal(BigDecimal.ZERO);
                return commandeRepository.save(commande);
            }
        }
        return commandeRepository.save(new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));
    }

    /** Sujet riche décrivant la commande en conflit : produits confirmés +
     *  ligne(s) à clarifier, pour que la réponse du client reconstruise toute
     *  la commande. */
    private String construireSujetConflit(FusionResult fusion) {
        Set<String> enConflit = fusion.conflits().stream()
                .map(c -> normaliser(c.produit()))
                .collect(Collectors.toSet());
        String confirmes = fusion.lignes().stream()
                .filter(l -> !enConflit.contains(normaliser(l.produit())))
                .map(l -> l.quantite() + " x " + l.produit())
                .collect(Collectors.joining(", "));
        String aClarifier = fusion.conflits().stream()
                .map(c -> c.produit() + " (quantité à confirmer : vocale=" + c.quantiteAudio()
                        + ", liste=" + c.quantiteImage() + ")")
                .collect(Collectors.joining(", "));
        StringBuilder sujet = new StringBuilder("Commande en cours. ");
        if (!confirmes.isBlank()) {
            sujet.append("Produits confirmés à garder : ").append(confirmes).append(". ");
        }
        sujet.append("À clarifier : ").append(aClarifier)
                .append(". Le client doit préciser la quantité en conflit ; "
                        + "conserve les produits confirmés.");
        return sujet.toString();
    }

    private List<String> lignesConfirmees(FusionResult fusion) {
        Set<String> enConflit = fusion.conflits().stream()
                .map(c -> normaliser(c.produit()))
                .collect(Collectors.toSet());
        return fusion.lignes().stream()
                .filter(l -> !enConflit.contains(normaliser(l.produit())))
                .map(l -> l.quantite() + " x " + l.produit())
                .toList();
    }

    private ResultatPipeline routerEtTraiter(Client client, UUID messageId, String texte) {
        RouterDecision routage = routerAgent.classifier(messageId, texte);
        if (routage.intention() == Intention.QUESTION) {
            String reponse = repondreQuestionEtOuvrirContexte(messageId, client, texte);
            return new ResultatPipeline(null, null, Intention.QUESTION.name(), null, null, reponse);
        }
        if (routage.intention() != Intention.COMMANDE) {
            return new ResultatPipeline(null, null, routage.intention().name(), null, null, null);
        }

        Commande commande = commandeRepository.save(
                new Commande(client, CommandeStatut.RECUE, BigDecimal.ZERO));
        List<ExtractionLigne> lignes = extractionAgent.extractFromTexte(commande.getId(), texte);
        return pipelineCommande(commande, Intention.COMMANDE.name(), lignes, List.of(), texte);
    }

    /**
     * Répond à une question client puis, si elle porte sur un produit précis
     * du catalogue, ouvre un contexte EN_PRECISION : la prochaine réponse du
     * client (une quantité) complètera la commande sur ce produit.
     */
    private String repondreQuestionEtOuvrirContexte(UUID messageId, Client client, String question) {
        String reponse = repondreQuestion(messageId, client, question);
        Optional<Produit> produit = identifierProduitQuestion(question);
        if (produit.isPresent()) {
            String sujet = produit.get().getNom() + " (" + produit.get().getPrixUnitaire()
                    + " FCFA) — en attente de la quantité souhaitée par le client";
            conversationService.ouvrirPrecision(client.getTelephone(), sujet,
                    List.of(produit.get().getNom()), null);
        }
        return reponse;
    }

    /**
     * Répond directement à une question client (prix, disponibilité...) avec
     * le catalogue en stock, sans jamais créer de commande.
     */
    private String repondreQuestion(UUID messageId, Client client, String question) {
        long debut = System.nanoTime();
        String reponse;
        String reasoning = null;
        try {
            reponse = qwenClient
                    .callFast(PROMPT_QUESTION.formatted(catalogueDisponible(), question))
                    .trim();
        } catch (Exception e) {
            reponse = "Bonjour ! Nous avons tout pour la maison et le chantier "
                    + "(ciment, tôles, peinture, savon...). Passez voir le catalogue "
                    + "ou dites-moi ce que vous cherchez !";
            reasoning = "Fallback statique, échec de l'appel Qwen : " + e.getMessage();
        }
        long durationMs = (System.nanoTime() - debut) / 1_000_000;

        eventStore.append(messageId, "REPONSE_QUESTION_ENVOYEE",
                Map.of("clientPhone", client.getTelephone(),
                        "question", question,
                        "reponse", reponse,
                        "durationMs", durationMs),
                null, reasoning);
        notificationService.envoyer(messageId, client.getTelephone(), reponse);
        return reponse;
    }

    // --- Pipeline commande --------------------------------------------------

    private ResultatPipeline pipelineCommande(Commande commande,
                                              String intention,
                                              List<ExtractionLigne> depuisAudio,
                                              List<ExtractionLigne> depuisImage,
                                              String messageClient) {
        if (depuisAudio.isEmpty() && depuisImage.isEmpty()) {
            // Rien d'exploitable : la commande reste RECUE, un humain prendra
            // le relais.
            return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                    intention, null, null, null);
        }

        FusionResult fusion = fusionService.fusionner(commande, depuisAudio, depuisImage);
        if (fusion.aDesConflits()) {
            notificationService.envoyer(commande.getId(),
                    commande.getClient().getTelephone(), fusion.messageClarification());
            // Le contexte porte la commande complète (produits confirmés +
            // ligne(s) en conflit) et l'id de la commande, pour que la réponse
            // du client reconstruise toute la commande sur la MÊME commande,
            // sans perdre les produits non conflictuels ni créer de doublon.
            conversationService.ouvrirPrecision(commande.getClient().getTelephone(),
                    construireSujetConflit(fusion), lignesConfirmees(fusion), commande.getId());
            return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                    intention, null, null, fusion.messageClarification());
        }

        return evaluerEtOrchestrer(commande, intention, fusion.lignes(), messageClient);
    }

    private ResultatPipeline evaluerEtOrchestrer(Commande commande,
                                                 String intention,
                                                 List<ExtractionLigne> lignes,
                                                 String messageClient) {
        List<BusinessRulesInput.LigneDemandee> demandes = new ArrayList<>();
        List<ExtractionLigne> inconnues = new ArrayList<>();
        List<String> connuesNoms = new ArrayList<>();
        BigDecimal montantTotal = BigDecimal.ZERO;

        for (ExtractionLigne ligne : lignes) {
            Optional<Produit> produit = chercherProduitAvecFallback(ligne.produit());
            if (produit.isEmpty()) {
                inconnues.add(ligne);
                continue;
            }
            demandes.add(new BusinessRulesInput.LigneDemandee(
                    ligne.produit(), ligne.quantite(),
                    produit.get().getStock(), produit.get().getPrixUnitaire()));
            connuesNoms.add(ligne.quantite() + " x " + produit.get().getNom());
            commande.getLignes().add(new LigneCommande(
                    commande, produit.get(), ligne.quantite(), produit.get().getPrixUnitaire()));
            montantTotal = montantTotal.add(
                    produit.get().getPrixUnitaire().multiply(BigDecimal.valueOf(ligne.quantite())));
        }

        commande.setMontantTotal(montantTotal);
        Commande sauvegardee = commandeRepository.save(commande);

        // Produit hors catalogue : clarification directe avec le client, le
        // patron n'est pas sollicité. Les produits connus restent persistés
        // sur la commande, en attente de la réponse du client.
        if (!inconnues.isEmpty()) {
            return clarifierProduitsInconnus(sauvegardee, intention, inconnues, connuesNoms);
        }

        Optional<String> preference = memoryService.preferencePour(
                sauvegardee.getClient().getId(), montantTotal);

        List<Commande> historique = commandeRepository.findByClientId(sauvegardee.getClient().getId());
        int nbCommandes = (int) historique.stream()
                .filter(c -> !c.getId().equals(sauvegardee.getId()))
                .count();
        int nbDefauts = (int) historique.stream()
                .filter(c -> c.getStatut() == CommandeStatut.REJETEE
                        && !c.getId().equals(sauvegardee.getId()))
                .count();
        boolean nouveauClient = nbCommandes == 0;

        BusinessRulesInput input = new BusinessRulesInput(
                sauvegardee.getClient().getNom(),
                sauvegardee.getClient().getCreditEnCours(),
                nbCommandes,
                nbDefauts,
                demandes,
                preference.orElse(null),
                messageClient);

        BusinessDecision decision = businessRulesAgent.evaluer(sauvegardee.getId(), input);

        // Garde-fou déterministe : un nouveau client (aucun historique) qui a
        // un crédit en cours ou en demande un est refusé sans jamais solliciter
        // le patron, quelle que soit la sortie du modèle.
        boolean demandeCredit = demandeCreditDetectee(messageClient);
        boolean creditNouveauClient = nouveauClient
                && (sauvegardee.getClient().getCreditEnCours().signum() > 0 || demandeCredit);
        if (creditNouveauClient || (nouveauClient && decision.decision() != DecisionType.AUTO_APPROVE)) {
            return rejeterNouveauClient(sauvegardee, intention);
        }

        OrchestrationResult orchestration = orchestratorAgent.orchestrer(
                sauvegardee, decision, preference.isPresent());

        return appliquerVerdict(sauvegardee, intention, decision, orchestration);
    }

    private ResultatPipeline clarifierProduitsInconnus(Commande commande,
                                                       String intention,
                                                       List<ExtractionLigne> inconnues,
                                                       List<String> connuesNoms) {
        List<String> nomsInconnus = inconnues.stream()
                .map(l -> l.quantite() + " x " + l.produit())
                .toList();
        eventStore.append(commande.getId(), "PRODUIT_INCONNU",
                Map.of("produitsInconnus", nomsInconnus, "produitsConserves", connuesNoms),
                null, "Produit(s) hors catalogue : clarification directe avec le client, "
                        + "sans solliciter le patron");

        commande.changerStatut(CommandeStatut.EN_CLARIFICATION);
        commandeRepository.save(commande);

        String contexteConnus = connuesNoms.isEmpty()
                ? "Aucun autre produit dans la demande."
                : "Le client a aussi commandé des produits que nous VENDONS, gardés de "
                        + "côté avec ces quantités exactes : "
                        + String.join(", ", connuesNoms) + ".";

        long debut = System.nanoTime();
        String message;
        String reasoning = null;
        try {
            message = qwenClient.callFast(PROMPT_PRODUIT_INCONNU.formatted(
                    String.join(", ", nomsInconnus), contexteConnus, catalogueDisponible())).trim();
        } catch (Exception e) {
            String produits = inconnues.stream().map(ExtractionLigne::produit)
                    .collect(Collectors.joining(", "));
            message = "Désolé maman, nous ne vendons pas de " + produits
                    + ". Jetez un œil à notre catalogue et dites-moi ce qu'il vous faut !";
            reasoning = "Fallback statique, échec de l'appel Qwen : " + e.getMessage();
        }
        long durationMs = (System.nanoTime() - debut) / 1_000_000;

        eventStore.append(commande.getId(), "MESSAGE_CLARIFICATION_GENERE",
                Map.of("message", message, "durationMs", durationMs), null, reasoning);
        notificationService.envoyer(commande.getId(),
                commande.getClient().getTelephone(), message);
        conversationService.ouvrirPrecision(commande.getClient().getTelephone(),
                "Produit(s) inconnu(s) à clarifier ; produits conservés : "
                        + String.join(", ", connuesNoms), connuesNoms, commande.getId());

        return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                intention, null, null, message);
    }

    /**
     * Nouveau client (aucun historique) refusé pour une demande de crédit : le
     * patron n'est jamais sollicité. Message poli expliquant qu'un historique
     * de commandes payées est nécessaire.
     */
    private ResultatPipeline rejeterNouveauClient(Commande commande, String intention) {
        eventStore.append(commande.getId(), "CREDIT_REFUSE_NOUVEAU_CLIENT",
                Map.of("clientNom", commande.getClient().getNom(),
                        "clientPhone", commande.getClient().getTelephone()),
                null, "Nouveau client sans historique : crédit refusé automatiquement, "
                        + "patron non sollicité");

        commande.changerStatut(CommandeStatut.REJETEE);
        commandeRepository.save(commande);

        String message = genererMessageRefus(
                "c'est une première commande et nous avons besoin d'un historique de "
                        + "commandes payées avant d'accorder un crédit ; un paiement comptant "
                        + "cette fois permet de bâtir la confiance");
        envoyerMessageRefus(commande, message);
        conversationService.fermer(commande.getClient().getTelephone(),
                "Crédit refusé (nouveau client), sujet clos");

        return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                intention, DecisionType.REJECT.name(), null, message);
    }

    private ResultatPipeline appliquerVerdict(Commande commande,
                                              String intention,
                                              BusinessDecision decision,
                                              OrchestrationResult orchestration) {
        UUID cardId = orchestration.decisionCard() != null
                ? orchestration.decisionCard().getId() : null;
        String phone = commande.getClient().getTelephone();

        switch (orchestration.type()) {
            case AUTO -> {
                switch (decision.decision()) {
                    case AUTO_APPROVE -> {
                        commande.changerStatut(CommandeStatut.VALIDEE_STOCK);
                        commande.changerStatut(CommandeStatut.EN_ATTENTE_PATRON);
                        commande.changerStatut(CommandeStatut.APPROUVEE);
                        commandeRepository.save(commande);
                        genererEtEnvoyerDevis(commande);
                        conversationService.fermer(phone, "Commande auto-approuvée, devis envoyé");
                    }
                    case REJECT -> {
                        commande.changerStatut(CommandeStatut.REJETEE);
                        commandeRepository.save(commande);
                        String message = genererMessageRefus(
                                decision.reasoning() == null || decision.reasoning().isBlank()
                                        ? "cette commande ne peut pas être validée pour le moment"
                                        : decision.reasoning());
                        envoyerMessageRefus(commande, message);
                        conversationService.fermer(phone, "Commande refusée, sujet clos");
                    }
                    case CLARIFY_CLIENT -> {
                        commande.changerStatut(CommandeStatut.EN_CLARIFICATION);
                        commandeRepository.save(commande);
                        if (decision.proposition() != null && !decision.proposition().isBlank()) {
                            notificationService.envoyer(commande.getId(), phone, decision.proposition());
                        }
                        conversationService.ouvrirPrecision(phone,
                                "En attente de la réponse du client à : " + decision.proposition(),
                                List.of(), commande.getId());
                    }
                    // Décision NEEDS_HUMAN malgré une confiance élevée :
                    // on respecte la demande du modèle et on route vers l'humain.
                    case NEEDS_HUMAN -> mettreEnAttentePatron(commande);
                }
            }
            case DECISION_CARD, ESCALADE -> mettreEnAttentePatron(commande);
        }

        return new ResultatPipeline(commande.getId(), commande.getStatut().name(),
                intention, decision.decision().name(), cardId, null);
    }

    /**
     * Transmet la commande au patron et prévient IMMÉDIATEMENT le client qu'il
     * attend une réponse ; le contexte passe en attente de confirmation.
     */
    private void mettreEnAttentePatron(Commande commande) {
        commande.changerStatut(CommandeStatut.VALIDEE_STOCK);
        commande.changerStatut(CommandeStatut.EN_ATTENTE_PATRON);
        commandeRepository.save(commande);

        String phone = commande.getClient().getTelephone();
        String message = "Bien reçu ! Je transmets votre demande au patron, "
                + "je reviens vers vous très vite 🙏";
        eventStore.append(commande.getId(), "MESSAGE_ATTENTE_ENVOYE",
                Map.of("message", message, "clientPhone", phone), null, null);
        notificationService.envoyer(commande.getId(), phone, message);
        conversationService.passerEnAttenteConfirmation(phone,
                "En attente de la décision du patron sur la commande " + commande.getId(),
                commande.getId());
    }

    /**
     * Reformule la réponse du patron en message naturel pour le client (jamais
     * le texte brut du commentaire), l'envoie, et clôt/adapte le contexte.
     * Appelé par le contrôleur de décision après enregistrement de l'action.
     */
    public void reformulerEtEnvoyerReponsePatron(Commande commande, String actionLabel, String commentaire) {
        String phone = commande.getClient().getTelephone();
        String message;
        String reasoning = null;
        try {
            message = qwenClient.callFast(
                    PROMPT_REFORMULATION.formatted(actionLabel,
                            commentaire == null ? "" : commentaire)).trim();
        } catch (Exception e) {
            message = switch (actionLabel) {
                case "APPROVE" -> "Bonne nouvelle ! Le patron a validé votre commande. "
                        + "Je vous prépare le devis tout de suite.";
                case "REJECT" -> "Merci de votre patience. Le patron ne peut pas donner suite "
                        + "à cette commande cette fois, mais on reste à votre service !";
                default -> "Le patron souhaite ajuster quelques détails, je reviens vers vous très vite.";
            };
            reasoning = "Fallback statique, échec de l'appel Qwen : " + e.getMessage();
        }

        eventStore.append(commande.getId(), "REPONSE_PATRON_REFORMULEE",
                Map.of("message", message, "clientPhone", phone, "action", actionLabel), null, reasoning);
        notificationService.envoyer(commande.getId(), phone, message);

        if ("MODIFY".equals(actionLabel)) {
            conversationService.passerEnAttenteConfirmation(phone,
                    "Le patron ajuste la commande " + commande.getId(), commande.getId());
        } else {
            conversationService.fermer(phone, "Décision patron transmise au client");
        }
    }

    private void envoyerMessageRefus(Commande commande, String message) {
        eventStore.append(commande.getId(), "MESSAGE_REFUS_ENVOYE",
                Map.of("message", message, "clientPhone", commande.getClient().getTelephone()),
                null, null);
        notificationService.envoyer(commande.getId(),
                commande.getClient().getTelephone(), message);
    }

    private String genererMessageRefus(String motif) {
        try {
            return qwenClient.callFast(PROMPT_REFUS.formatted(motif)).trim();
        } catch (Exception e) {
            return "Désolé, nous ne pouvons pas valider cette commande pour le moment. "
                    + "N'hésitez pas à revenir vers nous !";
        }
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

    private String catalogueDisponible() {
        return produitRepository.findAll().stream()
                .filter(p -> p.getStock() > 0)
                .map(p -> "- " + p.getNom() + " : " + p.getPrixUnitaire() + " FCFA")
                .collect(Collectors.joining("\n"));
    }

    private static final List<String> INDICES_CREDIT = List.of(
            "credit", "crédit", "paye", "payer", "paie", "acompte", "differe", "différé",
            "plus tard", "lundi", "mois prochain", "apres", "après", "dette", "avance");

    private boolean demandeCreditDetectee(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return INDICES_CREDIT.stream().anyMatch(m::contains);
    }

    /**
     * Identifie le produit du catalogue évoqué par une question, s'il est
     * unique. Retourne vide pour une question large ("quels sont vos
     * produits ?") ou ambiguë, afin de n'ouvrir un contexte que lorsque le
     * client vise clairement un produit précis.
     */
    private Optional<Produit> identifierProduitQuestion(String question) {
        String q = normaliser(question);
        List<Produit> matches = produitRepository.findAll().stream()
                .filter(p -> p.getStock() > 0)
                .filter(p -> motsSignificatifs(p.getNom()).stream().anyMatch(q::contains))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private List<String> motsSignificatifs(String nom) {
        String base = nom;
        int par = base.indexOf('(');
        if (par >= 0) {
            base = base.substring(0, par);
        }
        return Arrays.stream(normaliser(base).split("\\s+"))
                .filter(w -> w.length() >= 4)
                .filter(w -> w.chars().allMatch(Character::isLetter))
                .filter(w -> !STOPWORDS.contains(w))
                .toList();
    }

    private String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
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
