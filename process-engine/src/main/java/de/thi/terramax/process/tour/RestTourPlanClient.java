package de.thi.terramax.process.tour;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Ruft {@code GET /tours/plan} des tour-service auf.
 * <p>
 * Der Aufruf läuft im Namen des angemeldeten Benutzers: Das Keycloak-Token aus dem aktuellen Request wird
 * weitergereicht. Das funktioniert, weil "Besuchsplan erzeugen" direkt nach dem Start der Tour im selben Request
 * ausgeführt wird. Läuft der Schritt später einmal asynchron, braucht die Engine ein eigenes Service-Konto in Keycloak.
 */
@Component
public class RestTourPlanClient implements TourPlanClient {

    /**
     * @param url
     *            Basis-URL des tour-service, z. B. http://localhost:8084
     */
    @ConfigurationProperties("terramax.tour-service")
    public record TourServiceProperties(String url) {
    }

    private final RestClient restClient;

    public RestTourPlanClient(RestClient.Builder builder, TourServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.url()).build();
    }

    @Override
    public TourPlan fetchPlan(String tourDatum) {
        try {
            return restClient.get()
                    .uri(uri -> {
                        uri.path("/tours/plan");
                        if (tourDatum != null && !tourDatum.isBlank()) {
                            uri.queryParam("date", tourDatum);
                        }
                        return uri.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken())
                    .retrieve()
                    .body(TourPlan.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Besuchsplan konnte nicht vom tour-service geladen werden: "
                    + e.getMessage(), e);
        }
    }

    private static String currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getTokenValue();
        }
        throw new IllegalStateException("Kein Keycloak-Token im aktuellen Request; der tour-service verlangt eine"
                + " Anmeldung mit Rolle sales_professional");
    }
}
