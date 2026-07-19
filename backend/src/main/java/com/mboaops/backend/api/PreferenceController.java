package com.mboaops.backend.api;

import com.mboaops.backend.memoire.MemoryService;
import com.mboaops.backend.memoire.PreferenceDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Préférences apprises du patron : consultation et révocation. Le patron
 * enseigne une règle par ses approbations répétées ; il peut la retirer à
 * tout moment — l'humain garde toujours le dernier mot.
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final MemoryService memoryService;

    public PreferenceController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<PreferenceDto> lister() {
        return memoryService.listerPreferences();
    }

    @DeleteMapping("/{id}")
    public PreferenceDto revoquer(@PathVariable UUID id) {
        try {
            return memoryService.revoquer(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
