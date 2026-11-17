package de.thi.terramax.speech;

/**
 * Ergebnis der Spracherkennung, unabhängig vom verwendeten Dienst.
 *
 * @param durationSeconds
 *            Länge der Aufnahme, {@code null} wenn der Dienst sie nicht liefert (gpt-transcribe)
 */
public record Transcription(String transcript, String language, Double durationSeconds) {
}
