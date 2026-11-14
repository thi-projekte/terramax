package de.thi.terramax.speech;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Einstellungen für die Spracherkennung mit Whisper (whisper.* in application.properties). Die Adresse des Dienstes
 * steht unter quarkus.rest-client.whisper.url.
 */
@ConfigMapping(prefix = "whisper")
public interface WhisperConfig {

    /** Modell-ID in Speaches, z. B. Systran/faster-whisper-small. */
    String model();

    /** Sprache der Aufnahmen als ISO-Code, z. B. de. */
    String language();

    /** Fachbegriffe als Hinweis für die Erkennung, z. B. Modellnamen von John Deere. */
    Optional<String> prompt();

    /** Lädt das Modell beim Start herunter, falls es noch fehlt. */
    @WithDefault("false")
    boolean preloadModel();
}
