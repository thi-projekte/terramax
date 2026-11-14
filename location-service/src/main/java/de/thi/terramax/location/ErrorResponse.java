package de.thi.terramax.location;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Einheitliche Fehlerantwort der API.
 */
@Schema(name = "ErrorResponse", description = "Fehlermeldung")
public record ErrorResponse(@Schema(example = "Unbekannte Kategorie: foo") String error) {
}
