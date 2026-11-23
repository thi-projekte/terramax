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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProtocolResourceTest {

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
    void protocolsOfOwnTour() {
        given().when().get("/tours/{id}/protocols", f.ownTour()).then().statusCode(200)
                .body("id", contains((int) f.ownProtocol()))
                .body("[0].topics", contains("Neuer Traktor", "Finanzierung"))
                .body("[0].createdBy", is(TestData.SALES_SUB));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void protocolsOfColleagueTourAreNotFound() {
        given().when().get("/tours/{id}/protocols", f.colleagueTour()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesSeesOnlyCustomerProtocolsFromOwnTours() {
        given().when().get("/customers/{id}/protocols", f.huber()).then().statusCode(200).body("id",
                contains((int) f.ownProtocol()));
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalSeesAllCustomerProtocolsNewestFirst() {
        given().when().get("/customers/{id}/protocols", f.huber()).then().statusCode(200).body("id",
                contains((int) f.colleagueProtocol(), (int) f.ownProtocol()));
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void customerProtocolsOfOtherDealerAreNotFound() {
        given().when().get("/customers/{id}/protocols", f.fremd()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "nobody", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = "sub-unknown"),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void newSalesWithoutOwnToursSeesNoCustomerProtocols() {
        given().when().get("/customers/{id}/protocols", f.huber()).then().statusCode(200).body("$", empty());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createProtocolInFormatOfSpeechService() {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", f.maier());
        body.put("contactPerson", "Frau Maier");
        body.put("topics", List.of("Mähdrescher", " ", "Service\nVertrag"));
        body.put("outcome", "Probefahrt vereinbart");
        body.put("nextSteps", List.of());
        body.put("followUpDate", "2026-12-03");
        body.put("transcript", "War heute bei Frau Maier ...");

        given().contentType("application/json").body(body).when().post("/tours/{id}/protocols", f.ownTour()).then()
                .statusCode(201).body("tourId", is((int) f.ownTour())).body("customerId", is((int) f.maier()))
                .body("topics", contains("Mähdrescher", "Service Vertrag")).body("nextSteps", empty())
                .body("followUpDate", is("2026-12-03")).body("createdBy", is(TestData.SALES_SUB));

        given().when().get("/customers/{id}/protocols", f.maier()).then().body("[0].outcome",
                is("Probefahrt vereinbart"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createWithoutOptionalFields() {
        given().contentType("application/json").body(Map.of("customerId", f.huber())).when()
                .post("/tours/{id}/protocols", f.ownTour()).then().statusCode(201).body("topics", empty())
                .body("transcript", nullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createForCustomerNotOnTourIsBadRequest() {
        given().contentType("application/json").body(Map.of("customerId", f.ohneKoordinaten())).when()
                .post("/tours/{id}/protocols", f.ownTour()).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createWithoutCustomerIsBadRequest() {
        given().contentType("application/json").body(Map.of("outcome", "x")).when()
                .post("/tours/{id}/protocols", f.ownTour()).then().statusCode(400)
                .body("error", is("Die customerId fehlt."));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createOnColleagueTourIsNotFound() {
        given().contentType("application/json").body(Map.of("customerId", f.huber())).when()
                .post("/tours/{id}/protocols", f.colleagueTour()).then().statusCode(404);
    }
}
