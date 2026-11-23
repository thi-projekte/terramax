package de.thi.terramax.seed;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.TestData;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Import der Testdaten. Andere Testklassen leeren die Tabellen, deshalb importiert jeder Test selbst neu, genau wie es
 * beim Start passiert.
 */
@QuarkusTest
class SeedImporterTest {

    private static final String TEST_DEALER = "D-100";
    private static final String JD_DEALER = "jd-ltc-korneuburg";

    @Inject
    TestData testData;

    @Inject
    SeedImporter importer;

    @BeforeEach
    void setUp() {
        testData.clear();
        importer.importAll();
    }

    @Test
    void importsJohnDeereDealersTestDealerAndMockCustomers() {
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(17, Dealer.count());
            assertEquals(18, Customer.count());
            assertEquals(18, Customer.count("dealer.id", TEST_DEALER));
            assertEquals(0, Customer.count("dealer.id <> ?1", TEST_DEALER));
        });
    }

    @Test
    void johnDeereDealerIsLinkedToItsLocation() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dealer dealer = Dealer.findById(JD_DEALER);
            assertEquals("Lagerhaus Technik-Center – Standort Korneuburg", dealer.name);
            assertEquals("Korneuburg", dealer.city);
            assertEquals(48.3365145, dealer.latitude, 1e-9);
            assertEquals(JD_DEALER, dealer.location.externalId);

            Dealer testDealer = Dealer.findById(TEST_DEALER);
            assertEquals("Testhändler (fiktiv)", testDealer.name);
            assertNull(testDealer.location);
            assertNotNull(testDealer.latitude);
        });
    }

    @Test
    void mockCustomerFieldsAreImported() {
        QuarkusTransaction.requiringNew().run(() -> {
            Customer customer = Customer.findByExternalId("MOCK-018").orElseThrow();
            assertEquals("Testbetrieb 18 (fiktiv)", customer.name);
            assertEquals("Unterinntal", customer.region);
            assertEquals("Grünland", customer.segment);
            assertNull(customer.crmUrl);
            assertEquals(12.05, customer.longitude, 1e-9);
            assertEquals(47.47, customer.latitude, 1e-9);
        });
    }

    @Test
    void secondImportCreatesNothing() {
        importer.importAll();

        assertEquals(new SeedImporter.ImportResult(0, 16, 0), importer.importDealers("data/john_deere_at.geojson"));
        assertEquals(new SeedImporter.ImportResult(0, 18, 0),
                importer.importCustomers("data/mock_customers.geojson", TEST_DEALER));
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(17, Dealer.count());
            assertEquals(18, Customer.count());
        });
    }

    @Test
    void existingEntriesAreNotOverwritten() {
        QuarkusTransaction.requiringNew().run(() -> {
            Dealer.<Dealer> findById(TEST_DEALER).name = "Umbenannt";
            Customer.findByExternalId("MOCK-001").orElseThrow().name = "Geändert";
        });

        importer.importAll();

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals("Umbenannt", Dealer.<Dealer> findById(TEST_DEALER).name);
            assertEquals("Geändert", Customer.findByExternalId("MOCK-001").orElseThrow().name);
        });
    }

    @Test
    void invalidDealersAreSkippedWithoutAbort() {
        // T-1 und T-6 gültig; doppelte ID und Wettbewerber übersprungen; ohne Name, kaputte Koordinaten, zu lange ID
        // ungültig
        assertEquals(new SeedImporter.ImportResult(2, 2, 3), importer.importDealers("data/test-seed-dealers.geojson"));
        QuarkusTransaction.requiringNew().run(() -> {
            assertNotNull(Dealer.findById("T-1"));
            assertNull(Dealer.<Dealer> findById("T-6").latitude);
        });
    }

    @Test
    void invalidCustomersAreSkippedWithoutAbort() {
        // TC-1 und TC-2 (nur id) gültig; Duplikat übersprungen; ohne Name, ohne ID, Koordinaten außerhalb ungültig
        assertEquals(new SeedImporter.ImportResult(2, 1, 3),
                importer.importCustomers("data/test-seed-customers.geojson", TEST_DEALER));
        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(Customer.findByExternalId("TC-2").isPresent());
            assertEquals(20, Customer.count());
        });
    }

    @Test
    void customersForUnknownDealerAreNotImported() {
        assertEquals(new SeedImporter.ImportResult(0, 0, 6),
                importer.importCustomers("data/test-seed-customers.geojson", "D-999"));
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TEST_DEALER) })
    void principalOfTestDealerSeesAllMockCustomers() {
        List<String> ids = given().when().get("/customers").then().statusCode(200).body("$", hasSize(18))
                .body("externalId", everyItem(startsWith("MOCK-"))).extract().path("externalId");
        assertEquals(18, ids.stream().distinct().count());
    }

    @Test
    @TestSecurity(user = "jd", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = "sub-jd"), @Claim(key = "dealer_id", value = JD_DEALER) })
    void principalOfJohnDeereDealerSeesNoCustomers() {
        given().when().get("/customers").then().statusCode(200).body("$", empty());
    }
}
