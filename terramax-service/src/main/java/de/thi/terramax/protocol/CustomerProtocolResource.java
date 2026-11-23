package de.thi.terramax.protocol;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Besuchsprotokolle eines Kunden: {@code dealer_principal} alle, {@code sales_professional} nur die aus eigenen Touren.
 */
@Path("/customers/{customerId}/protocols")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Besuchsprotokolle")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class CustomerProtocolResource {

    @Inject
    CurrentUser user;

    @GET
    @Operation(summary = "Protokolle eines Kunden", description = "Neueste zuerst.")
    @APIResponse(responseCode = "200", description = "Protokolle", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class, type = SchemaType.ARRAY)))
    @APIResponse(responseCode = "404", description = "Unbekannter Kunde oder Kunde eines anderen Händlers", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public List<ProtocolDto> list(
            @Parameter(description = "ID des Kunden", example = "1") @PathParam("customerId") long customerId) {
        Customer customer = Customer.findForDealer(customerId, user.requireDealerId())
                .orElseThrow(() -> ApiErrors.notFound("Unbekannter Kunde: " + customerId));
        List<VisitorProtocol> protocols;
        if (user.isDealerPrincipal()) {
            protocols = VisitorProtocol.listByCustomer(customer.id);
        } else {
            protocols = user.employee().map(e -> VisitorProtocol.listByCustomerAndEmployee(customer.id, e.id))
                    .orElse(List.of());
        }
        return protocols.stream().map(ProtocolDto::from).toList();
    }
}
