package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import de.thi.terramax.speech.ProtocolGenerationException.Reason;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Visitenkarte lesen mit simulierter Anmeldung und simuliertem LLM ({@link FakeBusinessCardReader}).
 */
@QuarkusTest
class BusinessCardResourceTest {

    private static final byte[] IMAGE = { 1, 2, 3, 4 };

    @Inject
    FakeBusinessCardReader reader;

    @Inject
    EventSink events;

    @BeforeEach
    void reset() {
        reader.reset();
        events.clear();
    }

    private static RequestSpecification upload(String mediaType) {
        return given()
                .multiPart("image", "karte.jpg", IMAGE, mediaType)
                .multiPart("captureId", "lead:abc");
    }

    // --- Erfolg ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void returnsDraftFromCard() {
        upload("image/jpeg")
                .when().post("/business-cards")
                .then().statusCode(200)
                .body("captureId", is("lead:abc"))
                .body("card.contactName", is("Josef Maier"))
                .body("card.company", is("Hof Maier GbR"))
                .body("card.email", is("maier@example.at"))
                .body("card.city", is("Wels"));

        assertArrayEquals(IMAGE, reader.lastImage());
        assertEquals("image/jpeg", reader.lastMediaType());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void publishesReadEventWithoutContactData() {
        upload("image/png").when().post("/business-cards").then().statusCode(200);

        assertEquals(List.of("business-card.read"), events.types());
        assertEquals(List.of("lead:abc"), events.keys());
        JsonNode event = events.events().getFirst();
        assertEquals("lead:abc", event.get("subject").asText());
        assertEquals("lead:abc", event.get("data").get("captureId").asText());
        assertFalse(event.toString().contains("Maier"), "keine Kontaktdaten im Event");
    }

    // --- Rollen ---

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleIsForbidden() {
        upload("image/jpeg").when().post("/business-cards").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        upload("image/jpeg").when().post("/business-cards").then().statusCode(401);
    }

    // --- Ungültige Anfragen ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingImageIsBadRequest() {
        given().multiPart("captureId", "lead:abc")
                .when().post("/business-cards")
                .then().statusCode(400)
                .body("message", notNullValue());
        assertNull(reader.lastImage());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingCaptureIdIsBadRequest() {
        given().multiPart("image", "karte.jpg", IMAGE, "image/jpeg")
                .when().post("/business-cards")
                .then().statusCode(400)
                .body("message", containsString("captureId"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void otherFileTypesAreUnsupported() {
        upload("application/pdf").when().post("/business-cards").then().statusCode(415);
        upload("image/heic").when().post("/business-cards").then().statusCode(415);
        assertNull(reader.lastImage());
    }

    // --- Fehler der AI ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void unavailableAiIsBadGateway() {
        reader.failWith(Reason.UNAVAILABLE);
        upload("image/jpeg").when().post("/business-cards").then().statusCode(502)
                .body("message", containsString("von Hand"));

        assertEquals(List.of("business-card.failed"), events.types());
        assertEquals(502, events.events().getFirst().get("data").get("status").asInt());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rateLimitIsServiceUnavailable() {
        reader.failWith(Reason.RATE_LIMITED);
        upload("image/jpeg").when().post("/business-cards").then().statusCode(503);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void unreadableCardAsksForBetterPhoto() {
        reader.failWith(Reason.INVALID_RESPONSE);
        upload("image/jpeg").when().post("/business-cards").then().statusCode(502)
                .body("message", containsString("schärferes Foto"));
    }
}
