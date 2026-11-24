package de.thi.terramax.process.terramax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link TerraMaxClient} über REST.
 * <p>
 * Anmeldung: Das Keycloak-Token aus dem aktuellen Request wird weitergereicht (Token Propagation). Alle Aufrufe der
 * Engine laufen synchron im Request des Sales Professional (Tour starten, Aufgabe abschließen, Tour abbrechen), daher
 * gibt es immer ein Token. Läuft ein Schritt später einmal asynchron (Job Executor), braucht die Engine das
 * Service-Konto {@code terramax-process-engine} aus der Schnittstellen-Doku.
 */
@Component
public class RestTerraMaxClient implements TerraMaxClient {

    /**
     * @param url
     *            Basis-URL des terramax-service, lokal http://localhost:8088, im Stack http://terramax-service:8080
     */
    @ConfigurationProperties("terramax.terramax-service")
    public record TerraMaxServiceProperties(String url) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public RestTerraMaxClient(RestClient.Builder builder, TerraMaxServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.url()).build();
    }

    @Override
    public TourPlan planHolen(String tourDatum) {
        return aufruf("Besuchsplan laden", () -> restClient.get().uri(uri -> {
            uri.path("/tours/plan");
            if (tourDatum != null && !tourDatum.isBlank()) {
                uri.queryParam("date", tourDatum);
            }
            return uri.build();
        }).headers(this::anmelden).retrieve().body(TourPlan.class));
    }

    @Override
    public Tour tourAnlegen(String tourDatum, List<Long> kundenIds, String processInstanceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tourDate", tourDatum);
        body.put("customerIds", kundenIds);
        body.put("processInstanceId", processInstanceId);
        return aufruf("Tour anlegen", () -> restClient.post().uri("/tours").headers(this::anmelden)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Tour.class));
    }

    @Override
    public Tour tour(long tourId) {
        return aufruf("Tour " + tourId + " laden", () -> restClient.get().uri("/tours/{id}", tourId)
                .headers(this::anmelden).retrieve().body(Tour.class));
    }

    @Override
    public Touruebersicht uebersicht(long tourId) {
        return aufruf("Tourübersicht " + tourId + " laden", () -> restClient.get()
                .uri("/tours/{id}/overview", tourId).headers(this::anmelden).retrieve().body(Touruebersicht.class));
    }

    @Override
    public void haltEntfernen(long tourId, long kundeId) {
        aufruf("Kunde " + kundeId + " aus Tour " + tourId + " streichen", () -> restClient.delete()
                .uri("/tours/{id}/stops/{kundeId}", tourId, kundeId).headers(this::anmelden).retrieve()
                .toBodilessEntity());
    }

    @Override
    public void reihenfolgeAendern(long tourId, List<Long> kundenIds) {
        patch(tourId, Map.of("customerOrder", kundenIds), "Reihenfolge der Tour " + tourId + " ändern");
    }

    @Override
    public void tourStatusSetzen(long tourId, String status) {
        patch(tourId, Map.of("status", status), "Tour " + tourId + " auf " + status + " setzen");
    }

    @Override
    public void tourAbbrechen(long tourId, String grund) {
        Map<String, Object> body = grund == null || grund.isBlank() ? Map.of() : Map.of("reason", grund);
        post("/tours/{id}/abort", body, "Tour " + tourId + " abbrechen", tourId);
    }

    @Override
    public void besuchBeginnen(long tourId, long kundeId, String zeitpunkt) {
        post("/tours/{id}/stops/{kundeId}/start", zeitpunkt(zeitpunkt), "Besuch bei Kunde " + kundeId + " beginnen",
                tourId, kundeId);
    }

    @Override
    public void besuchUeberspringen(long tourId, long kundeId, String grund) {
        post("/tours/{id}/stops/{kundeId}/skip", Map.of("reason", grund),
                "Besuch bei Kunde " + kundeId + " überspringen", tourId, kundeId);
    }

    @Override
    public void besuchAbschliessen(long tourId, long kundeId, String zeitpunkt) {
        post("/tours/{id}/stops/{kundeId}/complete", zeitpunkt(zeitpunkt),
                "Besuch bei Kunde " + kundeId + " abschließen", tourId, kundeId);
    }

    // --- Hilfsmethoden ---

    private void patch(long tourId, Map<String, Object> body, String beschreibung) {
        aufruf(beschreibung, () -> restClient.patch().uri("/tours/{id}", tourId).headers(this::anmelden)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity());
    }

    private void post(String path, Map<String, Object> body, String beschreibung, Object... uriVariablen) {
        aufruf(beschreibung, () -> restClient.post().uri(path, uriVariablen).headers(this::anmelden)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity());
    }

    private static Map<String, Object> zeitpunkt(String zeitpunkt) {
        return zeitpunkt == null || zeitpunkt.isBlank() ? Map.of() : Map.of("at", zeitpunkt);
    }

    /**
     * Führt den Aufruf aus und ordnet Fehler ein: 400, 404 und 409 sind fachlich, alles andere technisch.
     */
    private static <T> T aufruf(String beschreibung, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String message = beschreibung + " fehlgeschlagen (HTTP " + status.value() + "): "
                    + fehlertext(e.getResponseBodyAsString());
            if (istFachlich(status)) {
                throw new TerraMaxFachlicherFehler(status.value(), message);
            }
            throw new TerraMaxTechnischerFehler(status.value(), message, e);
        } catch (RestClientException e) {
            throw new TerraMaxTechnischerFehler(0,
                    beschreibung + " fehlgeschlagen, terramax-service nicht erreichbar: " + e.getMessage(), e);
        }
    }

    static boolean istFachlich(HttpStatusCode status) {
        return status.isSameCodeAs(HttpStatus.BAD_REQUEST) || status.isSameCodeAs(HttpStatus.NOT_FOUND)
                || status.isSameCodeAs(HttpStatus.CONFLICT);
    }

    /** TerraMax antwortet mit {@code {"error": "…"}}; sonst den Text so wie er ist. */
    private static String fehlertext(String body) {
        if (body == null || body.isBlank()) {
            return "ohne Meldung";
        }
        try {
            JsonNode node = JSON.readTree(body);
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // kein JSON
        }
        return body;
    }

    private void anmelden(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            headers.setBearerAuth(token.getToken().getTokenValue());
            return;
        }
        throw new TerraMaxTechnischerFehler(0, "Kein Keycloak-Token im aktuellen Request; der terramax-service"
                + " verlangt eine Anmeldung", null);
    }
}
