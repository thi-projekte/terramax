package de.thi.terramax.tour;

/**
 * Einheitliche Fehlerantwort, z. B. bei ungültigen Parametern (400).
 */
public record ErrorResponse(String message) {
}
