package com.mboaops.backend.api;

import com.mboaops.backend.agents.orchestrator.DecisionCard;
import com.mboaops.backend.agents.orchestrator.DecisionCardAction;
import com.mboaops.backend.agents.orchestrator.DecisionCardRepository;
import com.mboaops.backend.agents.orchestrator.DecisionCardStatut;
import com.mboaops.backend.api.dto.CommandeDto;
import com.mboaops.backend.api.dto.DecisionActionRequest;
import com.mboaops.backend.api.dto.DecisionActionResponse;
import com.mboaops.backend.domain.commande.Commande;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import com.mboaops.backend.eventstore.EventStore;
import com.mboaops.backend.pipeline.CommandePipelineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Décisions humaines (patron) : commandes bloquées à EN_ATTENTE_PATRON et
 * réponse à une DecisionCard (✅ approuver / ❌ rejeter / ✏️ modifier).
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final CommandeRepository commandeRepository;
    private final DecisionCardRepository decisionCardRepository;
    private final EventStore eventStore;
    private final CommandePipelineService pipeline;

    public DecisionController(CommandeRepository commandeRepository,
                              DecisionCardRepository decisionCardRepository,
                              EventStore eventStore,
                              CommandePipelineService pipeline) {
        this.commandeRepository = commandeRepository;
        this.decisionCardRepository = decisionCardRepository;
        this.eventStore = eventStore;
        this.pipeline = pipeline;
    }

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public List<CommandeDto> pending() {
        return commandeRepository.findByStatut(CommandeStatut.EN_ATTENTE_PATRON).stream()
                .map(CommandeDto::from)
                .toList();
    }

    @PostMapping("/{id}/respond")
    @Transactional
    public DecisionActionResponse respond(@PathVariable UUID id,
                                          @Valid @RequestBody DecisionActionRequest request) {
        DecisionCard card = decisionCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DecisionCard introuvable : " + id));
        if (card.getStatut() != DecisionCardStatut.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DecisionCard déjà traitée (statut " + card.getStatut() + ")");
        }

        Commande commande = card.getCommande();
        String commentaire = request.getCommentaire() != null ? request.getCommentaire() : "";
        eventStore.append(commande.getId(), "DECISION_PATRON",
                Map.of("decisionCardId", card.getId(),
                        "action", request.getAction(),
                        "commentaire", commentaire),
                null, commentaire.isBlank() ? null : commentaire);

        switch (request.getAction()) {
            case APPROVE -> {
                card.setStatut(DecisionCardStatut.APPROUVEE);
                card.setActionAppliquee(DecisionCardAction.APPROUVER);
                commande.changerStatut(CommandeStatut.APPROUVEE);
                commandeRepository.save(commande);
                pipeline.genererEtEnvoyerDevis(commande);
            }
            case REJECT -> {
                card.setStatut(DecisionCardStatut.REJETEE);
                card.setActionAppliquee(DecisionCardAction.REJETER);
                commande.changerStatut(CommandeStatut.REJETEE);
                commandeRepository.save(commande);
            }
            // MODIFY : le patron ajuste la commande hors ligne ; la commande
            // reste EN_ATTENTE_PATRON en attendant une nouvelle décision.
            case MODIFY -> {
                card.setStatut(DecisionCardStatut.MODIFIEE);
                card.setActionAppliquee(DecisionCardAction.MODIFIER);
            }
        }
        decisionCardRepository.save(card);

        return new DecisionActionResponse(card.getId(), request.getAction(),
                commande.getStatut().name());
    }
}
