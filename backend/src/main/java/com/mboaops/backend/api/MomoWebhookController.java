package com.mboaops.backend.api;

import com.mboaops.backend.paiements.ReconciliationResult;
import com.mboaops.backend.paiements.ReconciliationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook de réception des SMS Mobile Money bruts (passerelle SMS). Accepte
 * le SMS en texte brut ou en JSON {"sms": "..."}.
 */
@RestController
@RequestMapping("/api/momo")
public class MomoWebhookController {

    private final ReconciliationService reconciliationService;

    public MomoWebhookController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ReconciliationResult webhookTexte(@RequestBody String smsBrut) {
        return reconciliationService.reconcilier(smsBrut);
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReconciliationResult webhookJson(@RequestBody Map<String, String> body) {
        return reconciliationService.reconcilier(body.getOrDefault("sms", ""));
    }
}
