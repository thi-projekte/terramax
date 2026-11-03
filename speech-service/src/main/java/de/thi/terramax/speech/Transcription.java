package de.thi.terramax.speech;

/**
 * Ergebnis der Spracherkennung, unabhängig vom verwendeten Dienst.
 */
public record Transcription(String transcript, String language, double durationSeconds) {
}
