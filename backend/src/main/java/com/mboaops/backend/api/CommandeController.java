package com.mboaops.backend.api;

import com.mboaops.backend.api.dto.CommandeDto;
import com.mboaops.backend.domain.commande.CommandeRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commandes")
public class CommandeController {

    private final CommandeRepository commandeRepository;

    public CommandeController(CommandeRepository commandeRepository) {
        this.commandeRepository = commandeRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CommandeDto> listAll() {
        return commandeRepository.findAll().stream()
                .map(CommandeDto::from)
                .toList();
    }
}
