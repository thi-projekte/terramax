package de.thi.terramax.speech;

import dev.langchain4j.model.output.structured.Description;

/**
 * Daten von einer fotografierten Visitenkarte. Die AI füllt sie aus dem Foto vor, der Sales Professional prüft und
 * bearbeitet sie vor dem Speichern. Die Beschreibungen gehen als JSON-Schema an das LLM.
 */
public record BusinessCard(
        @Description("Vor- und Nachname der Person, null wenn nicht auf der Karte") String contactName,
        @Description("Firma oder Betrieb, null wenn nicht auf der Karte") String company,
        @Description("Funktion oder Titel der Person, null wenn nicht auf der Karte") String role,
        @Description("Telefonnummer (bevorzugt Mobil) wie auf der Karte, null wenn keine") String phone,
        @Description("E-Mail-Adresse, null wenn keine") String email,
        @Description("Straße und Hausnummer, null wenn nicht auf der Karte") String street,
        @Description("Postleitzahl, null wenn nicht auf der Karte") String postalCode,
        @Description("Ort, null wenn nicht auf der Karte") String city) {
}
