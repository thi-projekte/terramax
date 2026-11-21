package de.thi.terramax;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * OpenAPI-Dokument und Swagger UI: Die Endpunkte für die process-engine sind beschrieben und haben Beispiele.
 */
@QuarkusTest
class OpenApiTest {

    @ParameterizedTest
    @CsvSource({ "/tours,post", "/tours/plan,get", "/tours/by-process/{processInstanceId},get",
            "/tours/{id}/status,get", "/tours/{id}/overview,get", "/tours/{id},patch",
            "/tours/{tourId}/stops/{customerId}/start,post", "/tours/{tourId}/stops/{customerId}/skip,post",
            "/tours/{tourId}/stops/{customerId}/complete,post", "/tours/{tourId}/stops/{customerId},delete",
            "/tours/{tourId}/protocols,post", "/protocols/{id},get", "/protocols/{id},put",
            "/protocols/{id},patch", "/protocols/{id},delete" })
    void engineEndpointsAreDocumented(String path, String method) {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths", hasKey(path)).body("paths.'" + path + "'." + method + ".summary", notNullValue());
    }

    @ParameterizedTest
    @CsvSource({ "/tours/plan,get,200", "/tours/{id}/status,get,200", "/tours/{id}/overview,get,200",
            "/tours/by-process/{processInstanceId},get,200", "/tours/{tourId}/stops/{customerId}/start,post,200",
            "/tours/{tourId}/stops/{customerId}/skip,post,200", "/tours/{tourId}/stops/{customerId}/complete,post,200",
            "/protocols/{id},get,200" })
    void engineResponsesHaveExamples(String path, String method, String status) {
        given().queryParam("format", "json").when().get("/q/openapi").then().body("paths.'" + path + "'." + method
                + ".responses.'" + status + "'.content.'application/json'.examples", notNullValue());
    }

    @ParameterizedTest
    @CsvSource({ "/tours,post", "/tours/{id},patch", "/tours/{tourId}/stops/{customerId}/start,post",
            "/tours/{tourId}/stops/{customerId}/skip,post", "/tours/{tourId}/stops/{customerId}/complete,post",
            "/tours/{tourId}/protocols,post", "/protocols/{id},put", "/protocols/{id},patch" })
    void engineRequestsHaveExamples(String path, String method) {
        given().queryParam("format", "json").when().get("/q/openapi").then()
                .body("paths.'" + path + "'." + method + ".requestBody.content.'application/json'.examples",
                        notNullValue());
    }

    @Test
    void swaggerUiIsAvailable() {
        given().when().get("/q/swagger-ui/").then().statusCode(200).body(containsString("swagger"));
    }
}
