package de.thi.terramax.speech;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;

/**
 * Einstellungen für die Spracherkennung über die OpenAI-API (transcription.* in application.properties). Die Adresse
 * steht unter quarkus.rest-client.openai-transcription.url, der API-Key ist derselbe wie für die Protokolle
 * (OPENAI_API_KEY).
 */
@ConfigMapping(prefix = "transcription")
public interface TranscriptionConfig {

    /** Modell-ID bei OpenAI, z. B. gpt-transcribe. */
    String model();

    /** Mögliche Sprachen der Aufnahmen als ISO-639-1-Codes, z. B. de. */
    List<String> languages();

    /** Kontext für die Erkennung, z. B. worum es in den Aufnahmen geht. */
    Optional<String> prompt();

    /** Fachbegriffe, die richtig geschrieben werden sollen, z. B. Modellnamen von John Deere. */
    Optional<List<String>> keywords();
}
