package de.thi.terramax.customer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
@OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
        @Claim(key = "dealer_id", value = TestData.DEALER) })
class CustomerResourceTest {

    @Inject
    TestData testData;

    private TestData.Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = testData.reset();
    }

    @Test
    void listReturnsOnlyCustomersOfOwnDealerSortedByName() {
        given().when().get("/customers").then().statusCode(200).contentType("application/json")
                .body("name", contains("Hof Huber", "Hof Maier", "Hof ohne Koordinaten"));
    }

    @Test
    void listAsGeoJsonSkipsCustomersWithoutCoordinates() {
        given().accept(CustomerResource.GEO_JSON).when().get("/customers").then().statusCode(200)
                .contentType(CustomerResource.GEO_JSON).body("type", is("FeatureCollection"))
                .body("features.properties.name", contains("Hof Huber", "Hof Maier"))
                .body("features[0].geometry.type", is("Point"))
                .body("features[0].geometry.coordinates", contains(14.10f, 48.20f))
                .body("features[0].properties.customer_id", is("CRM-1"))
                .body("features[0].properties.id", is((int) fixture.huber()));
    }

    @Test
    void leadsAreNotListedAsCustomers() {
        testData.addLead("Messe-Lead", 48.25, 14.20);
        given().when().get("/customers").then().statusCode(200)
                .body("name", contains("Hof Huber", "Hof Maier", "Hof ohne Koordinaten"))
                .body("status", everyItem(is("CUSTOMER")));
        given().accept(CustomerResource.GEO_JSON).when().get("/customers").then().statusCode(200)
                .body("features.properties.name", contains("Hof Huber", "Hof Maier"));
    }

    @Test
    void getOwnCustomer() {
        given().when().get("/customers/{id}", fixture.huber()).then().statusCode(200).body("name", is("Hof Huber"))
                .body("externalId", is("CRM-1"));
    }

    @Test
    void customerOfOtherDealerIsNotFound() {
        given().when().get("/customers/{id}", fixture.fremd()).then().statusCode(404).body("error",
                is("Unbekannter Kunde: " + fixture.fremd()));
    }

    @Test
    void createCustomerForOwnDealer() {
        int id = given().contentType("application/json")
                .body(Map.of("externalId", "CRM-10", "name", " Hof Neu ", "latitude", 48.1, "longitude", 14.2)).when()
                .post("/customers").then().statusCode(201).header("Location", containsString("/customers/"))
                .body("name", is("Hof Neu")).extract().path("id");

        given().when().get("/customers").then().body("id", org.hamcrest.Matchers.hasItem(id));
    }

    @Test
    void createWithoutNameIsBadRequest() {
        given().contentType("application/json").body(Map.of("region", "Mühlviertel")).when().post("/customers")
                .then().statusCode(400).body("error", is("Der Name fehlt."));
    }

    @Test
    void createWithOnlyLatitudeIsBadRequest() {
        given().contentType("application/json").body(Map.of("name", "Hof", "latitude", 48.1)).when()
                .post("/customers").then().statusCode(400);
    }

    @Test
    void createWithTakenExternalIdIsConflict() {
        given().contentType("application/json").body(Map.of("externalId", "CRM-9", "name", "Hof")).when()
                .post("/customers").then().statusCode(409);
    }

    @Test
    void updateReplacesFields() {
        given().contentType("application/json")
                .body(Map.of("externalId", "CRM-1", "name", "Hof Huber KG", "segment", "Grünland")).when()
                .put("/customers/{id}", fixture.huber()).then().statusCode(200).body("name", is("Hof Huber KG"))
                .body("segment", is("Grünland")).body("latitude", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void updateCustomerOfOtherDealerIsNotFound() {
        given().contentType("application/json").body(Map.of("name", "Übernommen")).when()
                .put("/customers/{id}", fixture.fremd()).then().statusCode(404);

        given().when().get("/customers").then().body("name", everyItem(not(is("Übernommen"))));
    }
}
