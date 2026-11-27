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
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Übergabe eines bestätigten Leads an TerraMax. Der terramax-service wird durch {@link FakeTerraMaxLeads} im selben
 * Prozess ersetzt; so laufen der echte REST-Client und die Weitergabe des Tokens mit.
 */
@QuarkusTest
class LeadResourceTest {

    private static final String TOKEN = "Bearer token-der-spa";
    private static final Map<String, Object> LEAD = Map.of(
            "captureId", "lead:abc",
            "source", "MESSE",
            "eventName", "Agraria Wels 2026",
            "contact", Map.of("name", "Josef Maier", "company", "Hof Maier GbR", "email", "maier@example.at",
                    "city", "Wels"),
            "latitude", 48.16,
            "longitude", 14.03,
            "comment", "Interesse an 8R 410",
            "transcript", "Interesse an acht R");

    @Inject
    FakeTerraMaxLeads terraMax;

    @Inject
    EventSink events;

    @BeforeEach
    void reset() {
        terraMax.reset();
        events.clear();
    }

    private static ValidatableResponse submit(Object body) {
        return given()
                .header("Authorization", TOKEN)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/leads")
                .then();
    }

    // --- Erfolg ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void forwardsLeadWithTokenOfTheSpa() {
        submit(LEAD)
                .statusCode(201)
                .body("customerId", is((int) FakeTerraMaxLeads.CUSTOMER_ID))
                .body("newCustomer", is(true))
                .body("contact.name", is("Josef Maier"))
                .body("note.createdBy", is("sub-of-sales"));

        assertEquals(TOKEN, terraMax.lastAuthorization(), "TerraMax muss das Token der SPA bekommen");
        JsonNode body = terraMax.lastBody();
        assertEquals("MESSE", body.get("source").asText());
        assertEquals("Agraria Wels 2026", body.get("eventName").asText());
        assertEquals("Hof Maier GbR", body.get("contact").get("company").asText());
        assertEquals(48.16, body.get("latitude").asDouble());
        assertEquals("Interesse an 8R 410", body.get("comment").asText());
        assertEquals("Interesse an acht R", body.get("transcript").asText());
        assertFalse(body.has("captureId"), "die captureId bleibt im speech-service");
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void publishesCapturedEventWithoutContactData() {
        submit(LEAD).statusCode(201);

        assertEquals(List.of("lead.captured"), events.types());
        assertEquals(List.of("lead:abc"), events.keys());
        JsonNode data = events.events().getFirst().get("data");
        assertEquals(FakeTerraMaxLeads.CUSTOMER_ID, data.get("customerId").asLong());
        assertTrue(data.get("newCustomer").asBoolean());
        assertFalse(events.events().getFirst().toString().contains("Maier"), "keine Kontaktdaten im Event");
    }

    // --- Ungültige Anfragen ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingCaptureIdOrContactIsBadRequest() {
        submit(Map.of("contact", Map.of("name", "X"))).statusCode(400).body("message", containsString("captureId"));
        submit(Map.of("captureId", "lead:abc")).statusCode(400);
        assertEquals(0, terraMax.calls());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void tooLongCommentIsBadRequest() {
        submit(Map.of("captureId", "lead:abc", "contact", Map.of("name", "X"), "comment",
                "a".repeat(LeadResource.MAX_COMMENT_LENGTH + 1))).statusCode(400);
        assertEquals(0, terraMax.calls());
    }

    // --- Fehler von TerraMax ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void badRequestFromTerraMaxKeepsItsMessage() {
        terraMax.failWith(400, "Name oder Firma fehlt.");
        submit(LEAD).statusCode(400).body("message", is("Name oder Firma fehlt."));
        assertTrue(events.types().isEmpty(), "kein Event, wenn nichts gespeichert wurde");
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void expiredTokenIsUnauthorized() {
        terraMax.failWith(401, "egal");
        submit(LEAD).statusCode(401).body("message", containsString("neu anmelden"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void unknownCustomerIsNotFound() {
        terraMax.failWith(404, "Unbekannter Kunde: 3");
        submit(LEAD).statusCode(404).body("message", containsString("nicht bekannt"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void serverErrorIsBadGateway() {
        terraMax.failWith(500, "kaputt");
        submit(LEAD).statusCode(502).body("message", containsString("Lead nicht speichern"));
    }

    // --- Rollen ---

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleIsForbidden() {
        submit(LEAD).statusCode(403);
        assertEquals(0, terraMax.calls());
    }
}
