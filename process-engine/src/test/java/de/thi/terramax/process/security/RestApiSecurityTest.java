package de.thi.terramax.process.security;

import de.thi.terramax.process.TestPlans;
import de.thi.terramax.process.tour.TourPlanClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prüft die REST-API so, wie das Frontend sie nutzt: Rollen (200/403/401) und dass die Engine den Benutzer aus dem
 * Token übernimmt (initiator, Zuweisung der Aufgaben). Keycloak wird durch einen Test-Decoder ersetzt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestApiSecurityTest {

    private static final String SALES_TOKEN = "sales-token";
    private static final String NO_ROLE_TOKEN = "no-role-token";

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    TourPlanClient tourPlanClient;

    @BeforeEach
    void tokens() {
        when(jwtDecoder.decode(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case SALES_TOKEN -> jwt(SALES_TOKEN, "sales", List.of(Roles.SALES_PROFESSIONAL));
            case NO_ROLE_TOKEN -> jwt(NO_ROLE_TOKEN, "testuser", List.of());
            default -> throw new BadJwtException("ungültig");
        });
        when(tourPlanClient.fetchPlan(any())).thenReturn(TestPlans.withCustomers(2));
    }

    @Test
    void anonymousIsUnauthorized() {
        assertThat(get("/engine-rest/process-definition", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenIsUnauthorized() {
        assertThat(get("/engine-rest/process-definition", "kaputt").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userWithoutSalesRoleIsForbidden() {
        assertThat(get("/engine-rest/process-definition", NO_ROLE_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void salesProfessionalSeesBesuchstourDefinition() {
        ResponseEntity<String> response = get("/engine-rest/process-definition?key=Besuchstour", SALES_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"key\":\"Besuchstour\"");
    }

    @Test
    void healthCheckIsOpen() {
        assertThat(get("/actuator/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void startedTourBelongsToLoggedInUserAndPlanIsReadableAsJson() {
        HttpHeaders headers = bearer(SALES_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("variables",
                Map.of("tourDatum", Map.of("value", "2026-11-10", "type", "String")));
        ResponseEntity<Map<String, Object>> started = rest.exchange(
                "/engine-rest/process-definition/key/Besuchstour/start", HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() {
                });
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        String instanceId = (String) started.getBody().get("id");

        ResponseEntity<List<Map<String, Object>>> tasks = rest.exchange(
                "/engine-rest/task?processInstanceId=" + instanceId, HttpMethod.GET,
                new HttpEntity<>(bearer(SALES_TOKEN)), new ParameterizedTypeReference<>() {
                });
        assertThat(tasks.getBody()).singleElement().satisfies(task -> {
            assertThat(task).containsEntry("taskDefinitionKey", "Task_BesuchsplanPruefen");
            assertThat(task).containsEntry("assignee", "sales");
        });

        ResponseEntity<Map<String, Object>> plan = rest.exchange(
                "/engine-rest/process-instance/" + instanceId + "/variables/besuchsplan", HttpMethod.GET,
                new HttpEntity<>(bearer(SALES_TOKEN)), new ParameterizedTypeReference<>() {
                });
        assertThat(plan.getBody().get("value")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(token == null ? new HttpHeaders()
                : bearer(token)), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static Jwt jwt(String value, String username, List<String> roles) {
        return Jwt.withTokenValue(value)
                .header("alg", "none")
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }
}
