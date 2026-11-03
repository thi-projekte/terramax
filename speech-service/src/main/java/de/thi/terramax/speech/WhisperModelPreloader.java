package de.thi.terramax.speech;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Lädt beim Start das Whisper-Modell, damit niemand es von Hand installieren muss.
 * Läuft im Hintergrund, weil der erste Download mehrere Minuten dauert.
 * Bis dahin antwortet der Endpunkt mit 503.
 */
@ApplicationScoped
public class WhisperModelPreloader {

    private static final Logger LOG = Logger.getLogger(WhisperModelPreloader.class);

    @Inject
    @RestClient
    WhisperModelsApi models;

    @Inject
    WhisperConfig config;

    void onStart(@Observes StartupEvent event) {
        if (!config.preloadModel()) {
            return;
        }
        Thread.ofVirtual().name("whisper-model-preload").start(() -> {
            LOG.infof("Lade Whisper-Modell %s (beim ersten Start einige Minuten)", config.model());
            try {
                models.download(config.model());
                LOG.infof("Whisper-Modell %s ist bereit", config.model());
            } catch (RuntimeException e) {
                LOG.warnf("Whisper-Modell %s konnte nicht geladen werden: %s", config.model(), e.getMessage());
            }
        });
    }
}
