package de.thi.terramax.process.tour;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Prüft den Aufruf des tour-service: richtige URL, Token des Benutzers wird weitergegeben, Antwort wird gelesen.
 */
class RestTourPlanClientTest {

    private static final String RESPONSE = """
            {
              "date": "2026-11-10",
              "start": { "latitude": 48.1575, "longitude": 14.0289 },
              "visits": [
                {
                  "position": 1,
                  "customer": {
                    "id": "K-1001", "name": "Hofgut Aigner", "location": "Gunskirchen",
                    "position": { "latitude": 48.1333, "longitude": 13.95 },
                    "lastVisit": "2026-03-12"
                  },
                  "distanceFromPreviousKm": 6.4
                }
              ],
              "totalDistanceKm": 6.4
            }
            """;

    private MockRestServiceServer server;
    private RestTourPlanClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTourPlanClient(builder, new RestTourPlanClient.TourServiceProperties("http://tour"));
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsUserTokenAndReadsPlan() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tour/tours/plan?date=2026-11-10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        TourPlan plan = client.fetchPlan("2026-11-10");

        server.verify();
        assertThat(plan.date()).isEqualTo("2026-11-10");
        assertThat(plan.visits()).singleElement().satisfies(visit -> {
            assertThat(visit.customer().id()).isEqualTo("K-1001");
            assertThat(visit.customer().lastVisit()).isEqualTo("2026-03-12");
            assertThat(visit.distanceFromPreviousKm()).isEqualTo(6.4);
        });
    }

    @Test
    void omitsDateWhenNotGiven() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tour/tours/plan"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        client.fetchPlan(null);

        server.verify();
    }

    @Test
    void failsClearlyWithoutLoggedInUser() {
        assertThatThrownBy(() -> client.fetchPlan("2026-11-10")).hasMessageContaining("Kein Keycloak-Token");
    }

    @Test
    void failsClearlyWhenTourServiceFails() {
        loginWithToken("user-token");
        server.expect(requestTo("http://tour/tours/plan?date=2026-11-10")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchPlan("2026-11-10")).hasMessageContaining("tour-service");
    }

    private static void loginWithToken(String value) {
        Jwt jwt = Jwt.withTokenValue(value).header("alg", "none").claim("preferred_username", "sales").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
