package de.thi.terramax.process.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.cibseven.bpm.engine.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Meldet den Benutzer aus dem geprüften Keycloak-Token für die Dauer eines Requests bei der Engine an.
 * <p>
 * Dadurch setzt die Engine z. B. {@code initiator} (Startereignis der Besuchstour) auf den Benutzernamen, und die
 * Keycloak-Rollen stehen der Engine als Gruppen zur Verfügung. So ist keine eigene Gruppen-Struktur in Keycloak nötig.
 */
public class EngineAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityService identityService;

    public EngineAuthenticationFilter(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }
        identityService.setAuthentication(KeycloakJwt.username(token.getToken()),
                KeycloakJwt.realmRoles(token.getToken()));
        try {
            chain.doFilter(request, response);
        } finally {
            identityService.clearAuthentication();
        }
    }
}
