package de.thi.terramax.lead;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
@OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
        @Claim(key = "dealer_id", value = TestData.DEALER) })
class LeadResourceTest {

    private static final String CARD = """
            {"source": "MESSE", "eventName": "Agraria Wels 2026",
             "contact": {"name": " Josef Maier ", "company": "Hof Maier GbR", "role": "Betriebsleiter",
                         "phone": "+43 664 1234567", "email": "maier@example.at",
                         "street": "Hauptstraße 1", "postalCode": "4600", "city": "Wels"},
             "latitude": 48.16, "longitude": 14.03,
             "comment": "Interesse an 8R 410", "transcript": "Interesse an acht R vierhundertzehn"}""";

    @Inject
    TestData testData;

    private TestData.Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = testData.reset();
    }

    @Test
    void createsNewLeadFromBusinessCard() {
        given().contentType("application/json").body(CARD).when().post("/leads").then().statusCode(201)
                .body("customerId", notNullValue()).body("newCustomer", is(true)).body("status", is("LEAD"))
                .body("name", is("Hof Maier GbR")).body("source", is("MESSE")).body("city", is("Wels"))
                .body("latitude", is(48.16f)).body("contact.name", is("Josef Maier"))
                .body("contact.email", is("maier@example.at")).body("note.comment", is("Interesse an 8R 410"))
                .body("note.eventName", is("Agraria Wels 2026")).body("note.createdBy", is(TestData.SALES_SUB))
                .body("note.createdAt", notNullValue());

        given().when().get("/leads").then().statusCode(200).body("$", hasSize(1))
                .body("[0].name", is("Hof Maier GbR")).body("[0].newCustomer", is(false))
                .body("[0].contact.name", is("Josef Maier")).body("[0].note.comment", is("Interesse an 8R 410"));
    }

    @Test
    void withoutCompanyTheNameBecomesTheCustomerAndSourceDefaultsToMesse() {
        given().contentType("application/json").body("""
                {"contact": {"name": "Anna Berger"}}""").when().post("/leads").then().statusCode(201)
                .body("name", is("Anna Berger")).body("source", is("MESSE")).body("note.comment", nullValue());
    }

    @Test
    void onlyCompanyCreatesLeadWithoutContact() {
        given().contentType("application/json").body("""
                {"contact": {"company": "Agrar Huber KG"}, "comment": "Prospekt mitgenommen"}""").when()
                .post("/leads").then().statusCode(201).body("name", is("Agrar Huber KG")).body("contact", nullValue())
                .body("note.comment", is("Prospekt mitgenommen"));
    }

    @Test
    void attachesToExistingCustomerAndKeepsItsStatus() {
        given().contentType("application/json").body("""
                {"contact": {"name": "Herr Huber", "street": "Feldweg 2", "city": "Wels"},
                 "latitude": 1.0, "longitude": 1.0, "comment": "Auf der Messe getroffen",
                 "existingCustomerId": %d}""".formatted(fixture.huber())).when().post("/leads").then()
                .statusCode(201).body("customerId", is((int) fixture.huber())).body("newCustomer", is(false))
                .body("status", is("CUSTOMER")).body("name", is("Hof Huber")).body("street", is("Feldweg 2"))
                // Vorhandene Koordinaten bleiben
                .body("latitude", is(48.20f)).body("contact.name", is("Herr Huber"));

        given().when().get("/leads").then().statusCode(200).body("$", hasSize(0));
        given().when().get("/customers").then().statusCode(200)
                .body("name", contains("Hof Huber", "Hof Maier", "Hof ohne Koordinaten"));
    }

    @Test
    void customerOfOtherDealerIsNotFound() {
        given().contentType("application/json").body("""
                {"contact": {"name": "X"}, "existingCustomerId": %d}""".formatted(fixture.fremd())).when()
                .post("/leads").then().statusCode(404);
    }

    @Test
    void invalidRequestsAreBadRequest() {
        given().contentType("application/json").body("{}").when().post("/leads").then().statusCode(400)
                .body("error", containsString("Name oder Firma"));
        given().contentType("application/json").body("""
                {"contact": {"name": " ", "company": ""}}""").when().post("/leads").then().statusCode(400);
        given().contentType("application/json").body("""
                {"contact": {"name": "X"}, "latitude": 48.1}""").when().post("/leads").then().statusCode(400)
                .body("error", containsString("gemeinsam"));
        given().contentType("application/json").body("""
                {"contact": {"name": "X"}, "comment": "%s"}""".formatted("a".repeat(2_001))).when().post("/leads")
                .then().statusCode(400).body("error", containsString("Kommentar"));
        given().contentType("application/json").body("""
                {"contact": {"name": "X"}, "source": "UNBEKANNT"}""").when().post("/leads").then().statusCode(400);
    }

    @Test
    void leadsOfOtherDealersAreNotListed() {
        testData.addLead("Eigener Lead", null, null);
        given().when().get("/leads").then().statusCode(200).body("name", contains("Eigener Lead"))
                .body("[0].note", nullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = @Claim(key = "sub", value = TestData.SALES_SUB))
    void withoutDealerIdIsForbidden() {
        given().contentType("application/json").body(CARD).when().post("/leads").then().statusCode(403);
        given().when().get("/leads").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "engine", roles = Roles.PROCESS_ENGINE)
    @OidcSecurity(claims = @Claim(key = "sub", value = TestData.ENGINE_SUB))
    void processEngineHasNoAccess() {
        given().contentType("application/json").body(CARD).when().post("/leads").then().statusCode(403);
    }
}
