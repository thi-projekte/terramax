package de.thi.terramax.security;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.dealer.DealerEmployee;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonString;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Der angemeldete Nutzer aus dem Keycloak-Token: {@code sub}, {@code dealer_id} und die TerraMax-Rollen.
 * <p>
 * Regeln für die Händler-Endpunkte:
 * <ul>
 * <li>{@code dealer_principal} sieht alle Kunden, Touren und Protokolle seines Händlers.</li>
 * <li>{@code sales_professional} sieht die Kunden seines Händlers, aber nur eigene Touren (über {@code tour_employee})
 * und deren Protokolle.</li>
 * <li>Ohne {@code dealer_id} im Token: 403.</li>
 * </ul>
 */
@RequestScoped
public class CurrentUser {

    static final String DEALER_ID_CLAIM = "dealer_id";

    private final SecurityIdentity identity;
    private final JsonWebToken jwt;

    @Inject
    public CurrentUser(SecurityIdentity identity, JsonWebToken jwt) {
        this.identity = identity;
        this.jwt = jwt;
    }

    /** Anzeigename aus dem Token ({@code preferred_username}). */
    public String username() {
        return identity.getPrincipal().getName();
    }

    /** Claim {@code sub}, die Keycloak-ID des Nutzers. */
    public Optional<String> subject() {
        return nonBlank(jwt.getSubject());
    }

    /** Claim {@code dealer_id}; nicht jeder Nutzer ist einem Händler zugeordnet. */
    public Optional<String> dealerId() {
        Object claim = jwt.getClaim(DEALER_ID_CLAIM);
        if (claim instanceof JsonString json) {
            return nonBlank(json.getString());
        }
        return claim == null ? Optional.empty() : nonBlank(claim.toString());
    }

    /** TerraMax-Rollen des Nutzers, sortiert. Andere Realm-Rollen werden ausgeblendet. */
    public List<String> roles() {
        return identity.getRoles().stream().filter(Roles.ALL::contains).sorted().toList();
    }

    public boolean isDealerPrincipal() {
        return identity.hasRole(Roles.DEALER_PRINCIPAL);
    }

    /** Fachrolle für den Mitarbeiter-Eintrag: dealer_principal vor sales_professional, sonst leer. */
    public Optional<String> businessRole() {
        if (identity.hasRole(Roles.DEALER_PRINCIPAL)) {
            return Optional.of(Roles.DEALER_PRINCIPAL);
        }
        return identity.hasRole(Roles.SALES_PROFESSIONAL) ? Optional.of(Roles.SALES_PROFESSIONAL) : Optional.empty();
    }

    /** Anzeigename: Claim {@code name}, sonst {@code preferred_username}. */
    public String displayName() {
        Object name = jwt.getClaim("name");
        if (name instanceof JsonString json) {
            name = json.getString();
        }
        return name == null ? username() : nonBlank(name.toString()).orElse(username());
    }

    /**
     * @throws jakarta.ws.rs.ForbiddenException
     *             wenn das Token keinen Claim {@code dealer_id} enthält
     */
    public String requireDealerId() {
        return dealerId().orElseThrow(() -> ApiErrors.forbidden("Kein Händler im Token (dealer_id)"));
    }

    /**
     * @throws jakarta.ws.rs.ForbiddenException
     *             wenn das Token keinen Claim {@code sub} enthält
     */
    public String requireSubject() {
        return subject().orElseThrow(() -> ApiErrors.forbidden("Kein Nutzer im Token (sub)"));
    }

    /**
     * Der Mitarbeiter-Eintrag des Nutzers, aber nur, wenn er zum Händler aus dem Token gehört. Angelegt wird er beim
     * ersten Aufruf automatisch ({@link EmployeeProvisioningFilter}), sofern der Händler in der Datenbank steht.
     */
    public Optional<DealerEmployee> employee() {
        Optional<String> dealerId = dealerId();
        if (dealerId.isEmpty()) {
            return Optional.empty();
        }
        return subject().flatMap(DealerEmployee::findByKeycloakUserId)
                .filter(e -> e.dealer.id.equals(dealerId.get()));
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
