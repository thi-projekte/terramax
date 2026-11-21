package de.thi.terramax.tour;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Endpunkte für die process-engine mit Nutzer-Token: idempotentes POST /tours, GET /tours/by-process, /status und
 * /overview.
 */
@QuarkusTest
class TourProcessEndpointsTest {

    private static final String PID = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002";

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    private Map<String, Object> createBody() {
        return Map.of("tourDate", "2026-11-24", "customerIds", List.of(f.maier(), f.huber()), "processInstanceId",
                PID);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createWithProcessInstanceIdIsIdempotent() {
        int id = given().contentType("application/json").body(createBody()).when().post("/tours").then()
                .statusCode(201).body("processInstanceId", is(PID)).body("dealerId", is(TestData.DEALER))
                .body("customers.status", contains("OFFEN", "OFFEN")).extract().path("id");

        // Zweiter Aufruf (z. B. Wiederholung des Service Tasks), auch mit anderem Inhalt: bestehende Tour, 200
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-12-24", "customerIds", List.of(f.huber()), "processInstanceId", PID))
                .when().post("/tours").then().statusCode(200).body("id", is(id)).body("tourDate", is("2026-11-24"))
                .body("customers.customerId", contains((int) f.maier(), (int) f.huber()));

        given().when().get("/tours").then().body("id.findAll { it == " + id + " }.size()", is(1));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void processInstanceIdOfInvisibleTourIsConflict() {
        testData.setProcessInstanceId(f.foreignTour(), PID);

        given().contentType("application/json").body(createBody()).when().post("/tours").then().statusCode(409)
                .body("error", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createWithTooLongProcessInstanceIdIsBadRequest() {
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-11-24", "processInstanceId", "x".repeat(65))).when().post("/tours")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void userMayNotCreateTourForOtherDealer() {
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-11-24", "dealerId", TestData.OTHER_DEALER)).when().post("/tours")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void findTourByProcessInstance() {
        testData.setProcessInstanceId(f.ownTour(), PID);

        given().when().get("/tours/by-process/{pid}", PID).then().statusCode(200).body("id", is((int) f.ownTour()));
        given().when().get("/tours/by-process/{pid}", "unbekannt").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void tourOfColleagueByProcessInstanceIsNotFound() {
        testData.setProcessInstanceId(f.colleagueTour(), PID);

        given().when().get("/tours/by-process/{pid}", PID).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void statusForGateways() {
        given().when().get("/tours/{id}/status", f.ownTour()).then().statusCode(200)
                .body("tourId", is((int) f.ownTour())).body("tourStatus", is("planned")).body("anzahlHalte", is(2))
                .body("offeneHalte", is(2)).body("dokumentiert", is(0)).body("uebersprungen", is(0))
                .body("alleErledigt", is(false)).body("naechsterHalt.kundeId", is((int) f.huber()))
                .body("naechsterHalt.position", is(1)).body("naechsterHalt.begonnen", is(false));

        given().contentType("application/json").when().post("/tours/{id}/stops/{c}/start", f.ownTour(), f.huber())
                .then().statusCode(200);
        given().when().get("/tours/{id}/status", f.ownTour()).then().body("tourStatus", is("in_progress"))
                .body("naechsterHalt.begonnen", is(true));

        given().contentType("application/json").when().post("/tours/{id}/stops/{c}/complete", f.ownTour(), f.huber())
                .then().statusCode(200);
        given().contentType("application/json").body(Map.of("reason", "Niemand da")).when()
                .post("/tours/{id}/stops/{c}/skip", f.ownTour(), f.maier()).then().statusCode(200);

        given().when().get("/tours/{id}/status", f.ownTour()).then().body("offeneHalte", is(0))
                .body("dokumentiert", is(1)).body("uebersprungen", is(1)).body("alleErledigt", is(true))
                .body("naechsterHalt", nullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void overviewReplacesProcessVariable() {
        given().contentType("application/json").body(Map.of("at", "2026-11-20T09:00:00+01:00")).when()
                .post("/tours/{id}/stops/{c}/start", f.ownTour(), f.huber()).then().statusCode(200);
        given().contentType("application/json").body(Map.of("at", "2026-11-20T09:45:00+01:00")).when()
                .post("/tours/{id}/stops/{c}/complete", f.ownTour(), f.huber()).then().statusCode(200);

        given().when().get("/tours/{id}/overview", f.ownTour()).then().statusCode(200)
                .body("tourId", is((int) f.ownTour())).body("tourDatum", is("2026-11-20"))
                .body("besuche.kundeId", contains((int) f.huber(), (int) f.maier()))
                .body("besuche.status", contains("DOKUMENTIERT", "OFFEN"))
                .body("besuche[0].ort", is("Hausruckviertel"))
                .body("besuche[0].besuchBegonnenAm", notNullValue())
                .body("besuche[0].besuchAbgeschlossenAm", notNullValue())
                .body("besuche[0].protokollIds", contains((int) f.ownProtocol()))
                .body("besuche[1].protokollIds", empty()).body("anzahlDokumentiert", is(1))
                .body("anzahlUebersprungen", is(0)).body("anzahlOffen", is(1));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void statusAndOverviewOfColleagueTourAreNotFound() {
        given().when().get("/tours/{id}/status", f.colleagueTour()).then().statusCode(404);
        given().when().get("/tours/{id}/overview", f.colleagueTour()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void completingTourViaPatch() {
        given().contentType("application/json").body(Map.of("status", "completed")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(200).body("status", is("completed"));
        given().when().get("/tours/{id}/status", f.ownTour()).then().body("tourStatus", is("completed"))
                .body("alleErledigt", is(false));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createReturnsLocationOfNewTour() {
        String location = given().contentType("application/json").body(createBody()).when().post("/tours").then()
                .statusCode(201).extract().header("Location");
        int id = given().when().get("/tours/by-process/{pid}", PID).then().extract().path("id");
        assertEquals(true, location.endsWith("/tours/" + id), location);
    }
}
