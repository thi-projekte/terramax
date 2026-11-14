package de.thi.terramax.location;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte für Händlerstandorte. Alle Antworten sind GeoJSON; jeder Aufruf braucht einen gültigen Keycloak-Token.
 */
@Path("/locations")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Standorte", description = "Händlerstandorte als GeoJSON")
@SecurityRequirement(name = LocationServiceApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
public class LocationResource {

    private final LocationGeoJsonMapper mapper;

    @Inject
    public LocationResource(LocationGeoJsonMapper mapper) {
        this.mapper = mapper;
    }

    @GET
    @Operation(summary = "Standorte auflisten", description = "Liefert alle Standorte, sortiert nach Name. "
            + "Optional nach Kategorien gefiltert (kommagetrennt).")
    @APIResponse(responseCode = "200", description = "Standorte als FeatureCollection", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GeoJsonFeatureCollection.class)))
    @APIResponse(responseCode = "400", description = "Unbekannte Kategorie", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public GeoJsonFeatureCollection list(
            @Parameter(description = "Kategorien, kommagetrennt: competitor, mixed_competitor, john_deere", example = "competitor,mixed_competitor") @QueryParam("category") String category) {
        Set<LocationCategory> categories = parseCategories(category);
        return mapper.toFeatureCollection(
                categories.isEmpty() ? Location.listAllSortedByName() : Location.listByCategories(categories));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Einen Standort abrufen", description = "Liefert einen Standort über seine ID (properties.id).")
    @APIResponse(responseCode = "200", description = "Standort als Feature", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GeoJsonFeature.class)))
    @APIResponse(responseCode = "404", description = "Unbekannte ID", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public GeoJsonFeature get(
            @Parameter(description = "ID des Standorts", example = "res-21") @PathParam("id") String id) {
        return Location.findByExternalId(id).map(mapper::toFeature)
                .orElseThrow(
                        () -> new NotFoundException(error(Response.Status.NOT_FOUND, "Unbekannter Standort: " + id)));
    }

    /**
     * Zerlegt den Parameter {@code category} (kommagetrennt, Leerzeichen und leere Einträge werden ignoriert).
     *
     * @return die Kategorien; leer, wenn kein Filter gesetzt ist
     *
     * @throws BadRequestException
     *             bei einer unbekannten Kategorie
     */
    static Set<LocationCategory> parseCategories(String category) {
        Set<LocationCategory> categories = EnumSet.noneOf(LocationCategory.class);
        if (category == null) {
            return categories;
        }
        for (String value : Arrays.stream(category.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .toList()) {
            try {
                categories.add(LocationCategory.fromDbValue(value));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(error(Response.Status.BAD_REQUEST, e.getMessage()));
            }
        }
        return categories;
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorResponse(message)).build();
    }
}
