package de.thi.terramax.process.terramax;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Prüft die Aufrufe an den terramax-service: URL, Body, weitergereichtes Token und die Einordnung der Fehler.
 */
class RestTerraMaxClientTest {

    private static final String PLAN = """
            {
              "date": "2026-11-10",
              "start": { "latitude": 48.1575, "longitude": 14.0289 },
              "visits": [
                {
                  "position": 1,
                  "customer": {
                    "id": "7", "name": "Hofgut Aigner", "location": "Gunskirchen",
                    "position": { "latitude": 48.1333, "longitude": 13.95 },
                    "lastVisit": "2026-03-12"
                  },
                  "distanceFromPreviousKm": 6.4
                }
              ],
              "totalDistanceKm": 6.4
            }
            """;

    private static final String TOUR = """
            {
              "id": 1, "dealerId": "D-100", "tourDate": "2026-11-10", "status": "planned",
              "processInstanceId": "pi-1", "employeeIds": [1],
              "customers": [
                { "customerId": 7, "externalId": "MOCK-007", "name": "Hofgut Aigner", "position": 1,
                  "status": "OFFEN", "skipReason": null, "startedAt": null, "finishedAt": null,
                  "latitude": 48.1333, "longitude": 13.95, "region": "Hausruckviertel", "lastVisit": "2026-03-12" }
              ]
            }
            """;

    private MockRestServiceServer server;
    private RestTerraMaxClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTerraMaxClient(builder, new RestTerraMaxClient.TerraMaxServiceProperties("http://tm"));
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsUserTokenAndReadsPlan() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/plan?date=2026-11-10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess(PLAN, MediaType.APPLICATION_JSON));

        TourPlan plan = client.planHolen("2026-11-10");

        server.verify();
        assertThat(plan.visits()).singleElement().satisfies(visit -> {
            assertThat(visit.customer().id()).isEqualTo("7");
            assertThat(visit.customer().lastVisit()).isEqualTo("2026-03-12");
        });
    }

    @Test
    void createsTourWithProcessInstanceAndReadsIds() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"tourDate": "2026-11-10", "customerIds": [7], "processInstanceId": "pi-1"}"""))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(TOUR));

        TerraMaxClient.Tour tour = client.tourAnlegen("2026-11-10", List.of(7L), "pi-1");

        server.verify();
        assertThat(tour.id()).isEqualTo(1);
        assertThat(tour.kundenIds()).containsExactly(7L);
        assertThat(tour.customers().get(0).latitude()).isEqualTo(48.1333);
        assertThat(tour.customers().get(0).region()).isEqualTo("Hausruckviertel");
        assertThat(tour.customers().get(0).lastVisit()).isEqualTo("2026-03-12");
    }

    @Test
    void skipSendsReason() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/1/stops/7/skip")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"reason\": \"Betrieb geschlossen\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.besuchUeberspringen(1, 7, "Betrieb geschlossen");

        server.verify();
    }

    @Test
    void conflictIsABusinessError() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/1/stops/7/complete")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"Halt ist schon UEBERSPRUNGEN\"}"));

        assertThatThrownBy(() -> client.besuchAbschliessen(1, 7, null)).isInstanceOf(TerraMaxFachlicherFehler.class)
                .hasMessageContaining("Halt ist schon UEBERSPRUNGEN")
                .satisfies(e -> assertThat(((TerraMaxException) e).status()).isEqualTo(409));
    }

    @Test
    void notFoundIsABusinessError() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/9")).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.tour(9)).isInstanceOf(TerraMaxFachlicherFehler.class);
    }

    @Test
    void serverErrorIsTechnical() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/plan?date=2026-11-10")).andRespond(withServerError());

        assertThatThrownBy(() -> client.planHolen("2026-11-10")).isInstanceOf(TerraMaxTechnischerFehler.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void forbiddenIsTechnical() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tm/tours/1")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.tour(1)).isInstanceOf(TerraMaxTechnischerFehler.class);
    }

    @Test
    void failsClearlyWithoutLoggedInUser() {
        assertThatThrownBy(() -> client.planHolen("2026-11-10")).isInstanceOf(TerraMaxTechnischerFehler.class)
                .hasMessageContaining("Kein Keycloak-Token");
    }

    private static void loginWithToken(String value) {
        Jwt jwt = Jwt.withTokenValue(value).header("alg", "none").claim("preferred_username", "sales").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
