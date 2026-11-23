package de.thi.terramax.security;

import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.dealer.DealerDto;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Wer angemeldet ist, mit Rollen und Händler. Jeder angemeldete Nutzer darf das abfragen, auch ohne Fachrolle.
 */
@Path("/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Nutzer")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
public class MeResource {

    @Inject
    CurrentUser user;

    @Schema(name = "Me", description = "Angemeldeter Nutzer")
    public record MeDto(@Schema(example = "sales") String username,
            @Schema(description = "Claim sub (Keycloak-ID)") String subject,
            @Schema(description = "TerraMax-Rollen", example = "[\"sales_professional\"]") List<String> roles,
            @Schema(description = "Claim dealer_id, null ohne Händlerzuordnung", example = "D-100") String dealerId,
            @Schema(description = "Händler, null wenn nicht zugeordnet oder noch nicht angelegt") DealerDto dealer,
            @Schema(description = "ID des Mitarbeiter-Eintrags, null wenn keiner angelegt ist") Long employeeId) {
    }

    @GET
    @Operation(summary = "Angemeldeter Nutzer", description = "Nutzer aus dem Token mit Rollen, Händler und Mitarbeiter-Eintrag.")
    @APIResponse(responseCode = "200", description = "Nutzer", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MeDto.class)))
    @APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
    public MeDto me() {
        String dealerId = user.dealerId().orElse(null);
        DealerDto dealer = dealerId == null ? null
                : Dealer.<Dealer> findByIdOptional(dealerId).map(DealerDto::from).orElse(null);
        Long employeeId = user.employee().map(e -> e.id).orElse(null);
        return new MeDto(user.username(), user.subject().orElse(null), user.roles(), dealerId, dealer, employeeId);
    }
}
