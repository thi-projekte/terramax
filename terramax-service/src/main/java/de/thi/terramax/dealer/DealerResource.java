package de.thi.terramax.dealer;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Händler des angemeldeten Nutzers.
 */
@Path("/dealers")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Händler")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class DealerResource {

    @Inject
    CurrentUser user;

    @GET
    @Path("/me")
    @Operation(summary = "Eigener Händler", description = "Händler aus dem Claim dealer_id des Tokens.")
    @APIResponse(responseCode = "200", description = "Händler", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DealerDto.class)))
    @APIResponse(responseCode = "404", description = "Händler ist noch nicht angelegt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public DealerDto me() {
        String dealerId = user.requireDealerId();
        Dealer dealer = Dealer.<Dealer> findByIdOptional(dealerId)
                .orElseThrow(() -> ApiErrors.notFound("Unbekannter Händler: " + dealerId));
        return DealerDto.from(dealer);
    }
}
