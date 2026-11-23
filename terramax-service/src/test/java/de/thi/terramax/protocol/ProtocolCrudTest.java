package de.thi.terramax.protocol;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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
 * GET, PUT, PATCH und DELETE /protocols/{id}: lesen, wer die Tour sieht; ändern und löschen nur der Ersteller (oder das
 * Service-Konto, siehe ServiceAccountTest).
 */
@QuarkusTest
class ProtocolCrudTest {

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesReadsOwnProtocolButNotOneOfColleague() {
        given().when().get("/protocols/{id}", f.ownProtocol()).then().statusCode(200)
                .body("id", is((int) f.ownProtocol())).body("topics", contains("Neuer Traktor", "Finanzierung"));
        given().when().get("/protocols/{id}", f.colleagueProtocol()).then().statusCode(404);
        given().when().get("/protocols/{id}", 999_999).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void creatorReplacesProtocolWithPut() {
        given().contentType("application/json")
                .body(Map.of("contactPerson", "Frau Huber", "topics", List.of("Ersatzteile"), "outcome", "Bestellt"))
                .when().put("/protocols/{id}", f.ownProtocol()).then().statusCode(200)
                .body("contactPerson", is("Frau Huber")).body("topics", contains("Ersatzteile"))
                .body("outcome", is("Bestellt")).body("nextSteps", empty()).body("followUpDate", nullValue())
                .body("createdBy", is(TestData.SALES_SUB)).body("tourId", is((int) f.ownTour()));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void creatorPatchesOnlyGivenFields() {
        given().contentType("application/json").body(Map.of("outcome", "Probefahrt", "nextSteps", List.of())).when()
                .patch("/protocols/{id}", f.ownProtocol()).then().statusCode(200).body("outcome", is("Probefahrt"))
                .body("nextSteps", empty()).body("contactPerson", is("Herr Huber"))
                .body("topics", contains("Neuer Traktor", "Finanzierung")).body("followUpDate", is("2026-11-27"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void protocolMayMoveToOtherCustomerOfSameTourOnly() {
        given().contentType("application/json").body(Map.of("customerId", f.maier())).when()
                .patch("/protocols/{id}", f.ownProtocol()).then().statusCode(200)
                .body("customerId", is((int) f.maier()));
        given().contentType("application/json").body(Map.of("customerId", f.ohneKoordinaten())).when()
                .patch("/protocols/{id}", f.ownProtocol()).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void creatorDeletesProtocol() {
        given().when().delete("/protocols/{id}", f.ownProtocol()).then().statusCode(204);
        given().when().delete("/protocols/{id}", f.ownProtocol()).then().statusCode(404);
        given().when().get("/tours/{id}/protocols", f.ownTour()).then().body("$", empty());
    }

    @Test
    @TestSecurity(user = "dealer", roles = { Roles.DEALER_PRINCIPAL, Roles.SALES_PROFESSIONAL })
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalReadsButMayNotChangeProtocolsOfOthers() {
        given().when().get("/protocols/{id}", f.colleagueProtocol()).then().statusCode(200);
        given().contentType("application/json").body(Map.of("outcome", "x")).when()
                .put("/protocols/{id}", f.colleagueProtocol()).then().statusCode(403);
        given().contentType("application/json").body(Map.of("outcome", "x")).when()
                .patch("/protocols/{id}", f.colleagueProtocol()).then().statusCode(403);
        given().when().delete("/protocols/{id}", f.colleagueProtocol()).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "other", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.OTHER_SUB),
            @Claim(key = "dealer_id", value = TestData.OTHER_DEALER) })
    void protocolOfOtherDealerIsNotFound() {
        given().when().get("/protocols/{id}", f.ownProtocol()).then().statusCode(404);
        given().when().delete("/protocols/{id}", f.ownProtocol()).then().statusCode(404);
    }

    /** Sub stimmt mit dem Ersteller überein, aber der Nutzer sieht die Tour nicht mehr (anderer Händler): 404. */
    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.OTHER_DEALER) })
    void creatorWithoutAccessToTourGetsNotFound() {
        given().when().delete("/protocols/{id}", f.ownProtocol()).then().statusCode(404);
    }
}
