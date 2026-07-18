package com.mboaops.backend.api;

import com.mboaops.backend.api.dto.CommandeDto;
import com.mboaops.backend.domain.commande.CommandeRepository;
import com.mboaops.backend.domain.commande.CommandeStatut;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Commandes bloquées à l'état EN_ATTENTE_PATRON, c'est-à-dire en attente
 * d'une décision humaine (le patron) avant de pouvoir avancer vers APPROUVEE
 * ou REJETEE.
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final CommandeRepository commandeRepository;

    public DecisionController(CommandeRepository commandeRepository) {
        this.commandeRepository = commandeRepository;
    }

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public List<CommandeDto> pending() {
        return commandeRepository.findByStatut(CommandeStatut.EN_ATTENTE_PATRON).stream()
                .map(CommandeDto::from)
                .toList();
    }
}
