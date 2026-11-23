package de.thi.terramax;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Health-Check und OpenAPI sind ohne Token erreichbar.
 */
@QuarkusTest
class PlatformEndpointsTest {

    @Test
    void healthIsUpWithoutToken() {
        given().when().get("/q/health").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    void openApiDocumentsAllEndpointsWithBearerScheme() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("components.securitySchemes", hasKey("bearerAuth"))
                .body("paths", hasKey("/me"))
                .body("paths", hasKey("/dealers/me"))
                .body("paths", hasKey("/customers"))
                .body("paths", hasKey("/customers/{id}"))
                .body("paths", hasKey("/customers/{customerId}/protocols"))
                .body("paths", hasKey("/tours"))
                .body("paths", hasKey("/tours/{id}"))
                .body("paths", hasKey("/tours/{tourId}/protocols"))
                .body("paths", hasKey("/locations"));
    }
}
