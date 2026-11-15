package de.thi.terramax.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Eigenschaften ({@code properties}) eines Standort-Features. Feldnamen exakt wie im API-Vertrag (snake_case). Fehlende
 * Werte werden als {@code null} ausgegeben, damit jedes Feature dieselben Felder hat.
 */
@Schema(name = "LocationProperties", description = "Eigenschaften eines Standorts")
public record LocationProperties(
        @Schema(description = "Eindeutige ID", example = "res-21") @JsonProperty("id") String id,
        @Schema(description = "Firmenname", example = "Christoph Heindl Landtechnik GmbH – Filiale Inning") @JsonProperty("name") String name,
        @Schema(description = "Kategorie", enumeration = {
                "competitor", "mixed_competitor",
                "john_deere" }, example = "competitor") @JsonProperty("category") String category,
        @Schema(description = "Marken, kommagetrennt", example = "CLAAS") @JsonProperty("brands") String brands,
        @Schema(description = "false = Marke unbekannt", example = "true") @JsonProperty("brand_known") Boolean brandKnown,
        @Schema(example = "Betriebsgebiet Inning 1/6") @JsonProperty("street") String street,
        @Schema(example = "3383") @JsonProperty("postcode") String postcode,
        @Schema(example = "Hürm") @JsonProperty("city") String city,
        @Schema(description = "osm, web_research oder web_research+osm", example = "web_research") @JsonProperty("source_type") String sourceType,
        @Schema(description = "Quelle", example = "https://www.landtechnik-heindl.at/kontakt/") @JsonProperty("source_url") String sourceUrl,
        @Schema(description = "Weitere Quellen") @JsonProperty("more_sources") String moreSources,
        @Schema(description = "Abrufdatum (YYYY-MM-DD)", format = "date", example = "2026-11-02") @JsonProperty("retrieved_at") String retrievedAt,
        @Schema(description = "Manuell bestätigt", example = "false") @JsonProperty("verified") Boolean verified,
        @Schema(description = "Hinweis", example = "Koordinate manuell auf OSM ermittelt") @JsonProperty("note") String note){
}
