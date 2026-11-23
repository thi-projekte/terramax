package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Übergabe eines bestätigten Protokolls an TerraMax. Der terramax-service wird durch {@link FakeTerraMax} im selben
 * Prozess ersetzt; so laufen der echte REST-Client und die Weitergabe des Tokens mit.
 */
@QuarkusTest
class TourProtocolResourceTest {

    private static final String TOKEN = "Bearer token-der-spa";
    private static final Map<String, Object> PROTOCOL = Map.of(
            "customerId", 5,
            "contactPerson", "Herr Maier (Betriebsleiter)",
            "topics", List.of("8R 410", "Finanzierung"),
            "outcome", "Interesse an einer Vorführung.",
            "nextSteps", List.of("Angebot schicken"),
            "followUpDate", "2026-11-28",
            "transcript", "Besuch beim Hof Maier.");

    @Inject
    FakeTerraMax terraMax;

    @Inject
    EventSink events;

    @BeforeEach
    void reset() {
        terraMax.reset();
        events.clear();
    }

    private static ValidatableResponse submit(long tourId, Object body) {
        return given()
                .header("Authorization", TOKEN)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/tours/{tourId}/protocols", tourId)
                .then();
    }

    // --- Erfolg ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void forwardsProtocolWithTokenOfTheSpa() {
        submit(7, PROTOCOL)
                .statusCode(201)
                .body("id", is((int) FakeTerraMax.PROTOCOL_ID))
                .body("tourId", is(7))
                .body("customerId", is(5))
                .body("createdBy", is("sub-of-sales"));

        assertEquals(7L, terraMax.lastTourId());
        assertEquals(TOKEN, terraMax.lastAuthorization(), "TerraMax muss das Token der SPA bekommen");
        JsonNode body = terraMax.lastBody();
        assertEquals(5, body.get("customerId").asInt());
        assertEquals("Herr Maier (Betriebsleiter)", body.get("contactPerson").asText());
        assertEquals("Finanzierung", body.get("topics").get(1).asText());
        assertEquals("Angebot schicken", body.get("nextSteps").get(0).asText());
        assertEquals("2026-11-28", body.get("followUpDate").asText(), "Datum als JJJJ-MM-TT");
        assertEquals("Besuch beim Hof Maier.", body.get("transcript").asText());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void publishesSubmittedEvent() {
        submit(7, PROTOCOL).statusCode(201);

        assertEquals(List.of("protocol.submitted"), events.types());
        assertEquals(List.of("7:5"), events.keys());
        JsonNode event = events.events().getFirst();
        assertEquals("7:5", event.get("subject").asText());
        assertEquals("sales", event.get("userid").asText());
        assertEquals(FakeTerraMax.PROTOCOL_ID, event.get("data").get("protocolId").asLong());
        assertTrue(event.get("data").get("transcript") == null, "Inhalte gehören nicht ins Event");
    }

    // --- Fehler von TerraMax ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void passesValidationErrorOfTerraMax() {
        terraMax.failWith(400, "Kunde nicht auf der Tour: 5");

        submit(7, PROTOCOL).statusCode(400).body("message", is("Kunde nicht auf der Tour: 5"));

        assertEquals(List.of(), events.types(), "Kein Event ohne gespeichertes Protokoll");
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void reportsExpiredToken() {
        terraMax.failWith(401, "unauthorized");

        submit(7, PROTOCOL).statusCode(401);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void reportsForbiddenTour() {
        terraMax.failWith(403, "Kein dealer_id im Token");

        submit(7, PROTOCOL).statusCode(403);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void reportsUnknownTour() {
        terraMax.failWith(404, "Tour nicht gefunden");

        submit(99, PROTOCOL).statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void reportsServerErrorOfTerraMaxAsBadGateway() {
        terraMax.failWith(500, "boom");

        submit(7, PROTOCOL).statusCode(502).body("message", is(
                "TerraMax konnte das Protokoll nicht speichern. Bitte erneut versuchen."));
    }

    // --- Eingaben ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rejectsMissingCustomer() {
        submit(7, Map.of("contactPerson", "Herr Maier")).statusCode(400);

        assertEquals(0, terraMax.calls());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rejectsTooLongTranscript() {
        submit(7, Map.of("customerId", 5, "transcript", "x".repeat(TourProtocolResource.MAX_TRANSCRIPT_LENGTH + 1)))
                .statusCode(400);

        assertEquals(0, terraMax.calls());
    }

    // --- Anmeldung ---

    @Test
    void rejectsAnonymous() {
        given().contentType(ContentType.JSON).body(PROTOCOL)
                .when().post("/tours/7/protocols")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "dealer", roles = "dealer_principal")
    void rejectsOtherRoles() {
        submit(7, PROTOCOL).statusCode(403);

        assertEquals(0, terraMax.calls());
    }
}
