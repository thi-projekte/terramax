package de.thi.terramax.speech;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Lädt beim Start das Whisper-Modell, damit niemand es von Hand installieren muss. Läuft im Hintergrund, weil der erste
 * Download mehrere Minuten dauert. Bis dahin antwortet der Endpunkt mit 503.
 */
@ApplicationScoped
public class WhisperModelPreloader {

    private static final Logger LOG = Logger.getLogger(WhisperModelPreloader.class);
    private static final int MAX_ATTEMPTS = 30;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    @Inject
    @RestClient
    WhisperModelsApi models;

    @Inject
    WhisperConfig config;

    void onStart(@Observes StartupEvent event) {
        if (!config.preloadModel()) {
            return;
        }
        Thread.ofVirtual().name("whisper-model-preload").start(this::preload);
    }

    // Whisper startet auf dem Server oft langsamer als dieser Service, deshalb mehrere Versuche
    private void preload() {
        LOG.infof("Lade Whisper-Modell %s (beim ersten Start einige Minuten)", config.model());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                models.download(config.model());
                LOG.infof("Whisper-Modell %s ist bereit", config.model());
                return;
            } catch (RuntimeException e) {
                LOG.warnf("Whisper-Modell %s konnte nicht geladen werden (Versuch %d von %d): %s",
                        config.model(), attempt, MAX_ATTEMPTS, e.getMessage());
            }
            try {
                Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOG.errorf("Whisper-Modell %s wurde nicht geladen. Transkriptionen antworten mit 503.", config.model());
    }
}
