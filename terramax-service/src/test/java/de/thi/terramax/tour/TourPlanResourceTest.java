package de.thi.terramax.tour;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thi.terramax.TestData;
import de.thi.terramax.common.ClockProducer;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GET /tours/plan: Rollen, Parameter (wie im tour-service) und Kompatibilität mit {@code TourPlan} der process-engine.
 */
@QuarkusTest
class TourPlanResourceTest {

    private static final String DATE = "2026-12-01";

    /**
     * Kopie von {@code process-engine/src/main/java/de/thi/terramax/process/tour/TourPlan.java}. Wird die Engine-Klasse
     * geändert, muss diese Kopie mit.
     */
    record EngineTourPlan(String date, List<Visit> visits, double totalDistanceKm) {

        record Visit(int position, Customer customer, double distanceFromPreviousKm) {
        }

        record Customer(String id, String name, String location, Position position, String lastVisit) {
        }

        record Position(double latitude, double longitude) {
        }
    }

    @Inject
    TestData testData;

    @Inject
    ObjectMapper objectMapper;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void responseHasExactlyTheFieldsOfEngineTourPlan() throws Exception {
        String json = given().queryParam("date", DATE).when().get("/tours/plan").then().statusCode(200).extract()
                .asString();
        JsonNode plan = objectMapper.readTree(json);

        assertEquals(Set.of("date", "visits", "totalDistanceKm"), fields(plan));
        JsonNode visit = plan.get("visits").get(0);
        assertEquals(Set.of("position", "customer", "distanceFromPreviousKm"), fields(visit));
        assertEquals(Set.of("id", "name", "location", "position", "lastVisit"), fields(visit.get("customer")));
        assertEquals(Set.of("latitude", "longitude"), fields(visit.get("customer").get("position")));

        // Strenger als Spring Boot in der Engine (dort werden unbekannte Felder ignoriert)
        EngineTourPlan parsed = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(json, EngineTourPlan.class);
        assertEquals(DATE, parsed.date());
        assertEquals(List.of(String.valueOf(f.huber()), String.valueOf(f.maier())),
                parsed.visits().stream().map(v -> v.customer().id()).toList());
        assertEquals("Hof Huber", parsed.visits().get(0).customer().name());
        assertEquals("Hausruckviertel", parsed.visits().get(0).customer().location());
        assertEquals(48.20, parsed.visits().get(0).customer().position().latitude());
        assertNull(parsed.visits().get(1).customer().lastVisit(), "maier wurde nie besucht");
        double sum = parsed.visits().stream().mapToDouble(EngineTourPlan.Visit::distanceFromPreviousKm).sum();
        assertEquals(sum, parsed.totalDistanceKm(), 0.11);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void customerIdIsStringAndUsableForCreatingTheTour() {
        List<String> ids = given().queryParam("date", DATE).when().get("/tours/plan").then()
                .body("visits.customer.id", everyItem(instanceOf(String.class))).extract()
                .path("visits.customer.id");

        given().contentType("application/json")
                .body(Map.of("tourDate", DATE, "customerIds", ids.stream().map(Long::valueOf).toList(),
                        "processInstanceId", "pid-plan"))
                .when().post("/tours").then().statusCode(201)
                .body("customers.customerId", contains((int) f.huber(), (int) f.maier()));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void positionsAndDistancesLikeTourService() {
        given().queryParam("date", DATE).when().get("/tours/plan").then().statusCode(200).body("date", is(DATE))
                .body("visits.position", contains(1, 2))
                .body("visits.distanceFromPreviousKm", everyItem(greaterThan(0f)))
                .body("totalDistanceKm", greaterThan(0f));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void usesTodayAndFiveVisitsByDefault() {
        for (int i = 1; i <= 6; i++) {
            testData.addCustomer("CRM-X" + i, "Zusatzhof " + i, 48.0 + i / 100.0, 14.0);
        }
        given().when().get("/tours/plan").then().statusCode(200)
                .body("date", is(LocalDate.now(ClockProducer.ZONE).toString()))
                .body("visits", hasSize(TourPlanResource.DEFAULT_VISITS));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void leadsAreNotPlanned() {
        testData.addLead("Messe-Lead", 48.25, 14.20);
        given().queryParam("date", DATE).when().get("/tours/plan").then().statusCode(200)
                .body("visits.customer.name", contains("Hof Huber", "Hof Maier"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void maxVisitsLimitsPlan() {
        for (int i = 1; i <= 12; i++) {
            testData.addCustomer("CRM-X" + i, "Zusatzhof " + i, 48.0 + i / 100.0, 14.0);
        }
        given().queryParam("date", DATE).queryParam("maxVisits", TourPlanResource.MIN_VISITS).when()
                .get("/tours/plan").then().statusCode(200).body("visits", hasSize(2));
        given().queryParam("date", DATE).queryParam("maxVisits", TourPlanResource.MAX_VISITS).when()
                .get("/tours/plan").then().statusCode(200).body("visits", hasSize(10));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void rejectsInvalidParameters() {
        given().queryParam("date", "10.11.2026").when().get("/tours/plan").then().statusCode(400).body("error",
                containsString("JJJJ-MM-TT"));
        given().queryParam("maxVisits", 1).when().get("/tours/plan").then().statusCode(400).body("error",
                containsString("maxVisits"));
        given().queryParam("maxVisits", 11).when().get("/tours/plan").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = "sub-unknown"), @Claim(key = "dealer_id", value = "D-999") })
    void unknownDealerIsNotFound() {
        given().when().get("/tours/plan").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = @Claim(key = "sub", value = TestData.SALES_SUB))
    void withoutDealerIdIsForbidden() {
        given().when().get("/tours/plan").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void onlySalesProfessionalRole() {
        // In Keycloak enthält dealer_principal sales_professional (Composite); hier bewusst ohne
        given().when().get("/tours/plan").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        given().when().get("/tours/plan").then().statusCode(401);
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new TreeSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            names.add(it.next());
        }
        return names;
    }
}
