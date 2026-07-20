package com.mboaops.backend.agents.qwen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Pré-chauffe la connexion vers l'API Qwen au démarrage (DNS + TLS + pool
 * HTTP) : le premier appel à froid coûte ~35s sur cette liaison, payés ici
 * en asynchrone plutôt que par le premier vrai message client. Échec
 * silencieux : un warm-up raté n'a aucun impact fonctionnel.
 */
@Component
public class QwenWarmup {

    private static final Logger log = LoggerFactory.getLogger(QwenWarmup.class);

    private final QwenClient qwenClient;

    public QwenWarmup(QwenClient qwenClient) {
        this.qwenClient = qwenClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void prechaufferConnexion() {
        CompletableFuture.runAsync(() -> {
            long debut = System.nanoTime();
            try {
                qwenClient.callFast("ping — réponds uniquement : pong");
                log.info("Warm-up Qwen effectué en {} ms : connexion prête",
                        (System.nanoTime() - debut) / 1_000_000);
            } catch (Exception e) {
                log.warn("Warm-up Qwen échoué (sans impact fonctionnel) : {}", e.getMessage());
            }
        });
    }
}
