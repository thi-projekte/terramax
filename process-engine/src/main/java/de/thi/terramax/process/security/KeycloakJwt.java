package de.thi.terramax.process.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Liest die für TerraMax wichtigen Angaben aus einem Keycloak-Token.
 */
public final class KeycloakJwt {

    private KeycloakJwt() {
    }

    /** Realm-Rollen aus {@code realm_access.roles}, z. B. sales_professional. */
    public static List<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> access && access.get("roles") instanceof Collection<?> roles) {
            return roles.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** Benutzername aus {@code preferred_username}; wird in der Engine zur Benutzer-ID (z. B. für initiator). */
    public static String username(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }
}
