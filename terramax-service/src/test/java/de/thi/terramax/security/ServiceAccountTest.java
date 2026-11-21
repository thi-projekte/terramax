package de.thi.terramax.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import de.thi.terramax.TestData;
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
 * Service-Konto der process-engine: Rolle process_engine, kein dealer_id. Es sieht jede Tour; der Händler kommt aus der
 * Tour bzw. beim Anlegen aus dem Request.
 */
@QuarkusTest
@TestSecurity(user = "service-account-terramax-process-engine", roles = Roles.PROCESS_ENGINE)
@OidcSecurity(claims = @Claim(key = "sub", value = TestData.ENGINE_SUB))
class ServiceAccountTest {

    private static final String PID = "engine-pid-1";

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    void readsToursOfAnyDealerWithoutDealerId() {
        given().when().get("/tours/{id}", f.colleagueTour()).then().statusCode(200).body("dealerId",
                is(TestData.DEALER));
        given().when().get("/tours/{id}", f.foreignTour()).then().statusCode(200).body("dealerId",
                is(TestData.OTHER_DEALER));
        given().when().get("/tours/{id}/status", f.ownTour()).then().statusCode(200);
        given().when().get("/tours/{id}/overview", f.ownTour()).then().statusCode(200);
    }

    @Test
    void findsTourByProcessInstance() {
        testData.setProcessInstanceId(f.foreignTour(), PID);

        given().when().get("/tours/by-process/{pid}", PID).then().statusCode(200).body("id",
                is((int) f.foreignTour()));
    }

    @Test
    void createNeedsDealerIdAndIsIdempotent() {
        Map<String, Object> body = Map.of("tourDate", "2026-11-24", "customerIds", List.of(f.maier()),
                "processInstanceId", PID);
        given().contentType("application/json").body(body).when().post("/tours").then().statusCode(400)
                .body("error", is("Das Service-Konto muss dealerId angeben."));

        Map<String, Object> withDealer = Map.of("tourDate", "2026-11-24", "customerIds", List.of(f.maier()),
                "employeeIds", List.of(f.salesId()), "processInstanceId", PID, "dealerId", TestData.DEALER);
        int id = given().contentType("application/json").body(withDealer).when().post("/tours").then()
                .statusCode(201).body("dealerId", is(TestData.DEALER))
                .body("employeeIds", contains((int) f.salesId())).extract().path("id");
        given().contentType("application/json").body(withDealer).when().post("/tours").then().statusCode(200)
                .body("id", is(id));
    }

    @Test
    void createWithUnknownDealerOrForeignCustomerIsBadRequest() {
        given().contentType("application/json").body(Map.of("tourDate", "2026-11-24", "dealerId", "D-999")).when()
                .post("/tours").then().statusCode(400);
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-11-24", "dealerId", TestData.DEALER, "customerIds",
                        List.of(f.fremd())))
                .when().post("/tours").then().statusCode(400);
    }

    @Test
    void changesStopsOfAnyTour() {
        given().contentType("application/json").when()
                .post("/tours/{id}/stops/{c}/start", f.colleagueTour(), f.huber()).then().statusCode(200);
        given().contentType("application/json").body(Map.of("reason", "Kein Termin")).when()
                .post("/tours/{id}/stops/{c}/skip", f.foreignTour(), f.fremd()).then().statusCode(200)
                .body("status", is("UEBERSPRUNGEN"));
    }

    @Test
    void abortsTourOfAnyDealer() {
        given().contentType("application/json").body(Map.of("reason", "TourAbbrechen")).when()
                .post("/tours/{id}/abort", f.foreignTour()).then().statusCode(200).body("status", is("aborted"));
    }

    @Test
    void savesProtocolAsServiceAccount() {
        given().contentType("application/json").body(Map.of("customerId", f.maier(), "outcome", "per Engine")).when()
                .post("/tours/{id}/protocols", f.ownTour()).then().statusCode(201)
                .body("createdBy", is(TestData.ENGINE_SUB));
    }

    @Test
    void mayEditAndDeleteProtocolsOfUsers() {
        given().contentType("application/json").body(Map.of("outcome", "korrigiert")).when()
                .patch("/protocols/{id}", f.ownProtocol()).then().statusCode(200).body("outcome", is("korrigiert"))
                .body("createdBy", is(TestData.SALES_SUB));
        given().when().delete("/protocols/{id}", f.colleagueProtocol()).then().statusCode(204);
    }

    @Test
    void userOnlyEndpointsAreForbidden() {
        given().when().get("/tours").then().statusCode(403);
        given().when().get("/tours/plan").then().statusCode(403);
        given().when().get("/customers").then().statusCode(403);
        given().when().get("/customers/{id}/protocols", f.huber()).then().statusCode(403);
    }

    @Test
    void meShowsEngineRoleWithoutDealer() {
        given().when().get("/me").then().statusCode(200).body("roles", contains(Roles.PROCESS_ENGINE))
                .body("dealerId", nullValue()).body("employeeId", nullValue());
    }
}
