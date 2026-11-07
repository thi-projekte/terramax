package de.thi.terramax.location;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LocationResourceTest {

    private static final double DELTA = 1e-6;

    @Test
    void listWithoutTokenReturnsUnauthorized() {
        given().when().get("/locations").then().statusCode(401);
    }

    @Test
    void getWithoutTokenReturnsUnauthorized() {
        given().when().get("/locations/res-21").then().statusCode(401);
    }

    /** Läuft gegen den echten OIDC-Mechanismus (Dev-Services-Keycloak), daher ohne @TestSecurity. */
    @Test
    void listWithInvalidTokenReturnsUnauthorized() {
        given().header("Authorization", "Bearer abc.def.ghi").when().get("/locations").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice")
    void listReturnsAllLocationsAsFeatureCollectionSortedByName() {
        JsonPath json = given().when().get("/locations").then().statusCode(200).contentType("application/json")
                .body("type", is("FeatureCollection")).body("features.size()", is(197))
                .body("features.type", everyItem(is("Feature")))
                .body("features.geometry.type", everyItem(is("Point"))).extract().jsonPath();

        List<String> expectedOrder = Location.listAllSortedByName().stream().map(l -> l.externalId).toList();
        assertEquals(expectedOrder, json.getList("features.properties.id"));
    }

    @Test
    @TestSecurity(user = "alice")
    void listFiltersByOneCategory() {
        given().queryParam("category", "competitor").when().get("/locations").then().statusCode(200)
                .body("features.size()", is(133)).body("features.properties.category", everyItem(is("competitor")));
    }

    @Test
    @TestSecurity(user = "alice")
    void listFiltersByTwoCategoriesIgnoringWhitespace() {
        given().queryParam("category", " competitor , mixed_competitor ").when().get("/locations").then()
                .statusCode(200).body("features.size()", is(197))
                .body("features.properties.category", hasItems("competitor", "mixed_competitor"));
    }

    @Test
    @TestSecurity(user = "alice")
    void listFiltersByCategoryWithoutLocations() {
        given().queryParam("category", "john_deere").when().get("/locations").then().statusCode(200)
                .body("type", is("FeatureCollection")).body("features.size()", is(0));
    }

    @Test
    @TestSecurity(user = "alice")
    void listWithEmptyCategoryReturnsAllLocations() {
        given().queryParam("category", " , ").when().get("/locations").then().statusCode(200)
                .body("features.size()", is(197));
    }

    @Test
    @TestSecurity(user = "alice")
    void listWithUnknownCategoryReturnsBadRequest() {
        given().queryParam("category", "competitor,dealer").when().get("/locations").then().statusCode(400)
                .contentType("application/json").body("error", containsString("dealer"));
    }

    @Test
    @TestSecurity(user = "alice")
    void getReturnsFeatureWithLongitudeFirst() {
        JsonPath json = given().when().get("/locations/res-21").then().statusCode(200)
                .contentType("application/json").body("type", is("Feature")).body("geometry.type", is("Point"))
                .body("geometry.coordinates.size()", is(2)).body("properties.id", is("res-21"))
                .body("properties.name", is("Christoph Heindl Landtechnik GmbH – Filiale Inning"))
                .body("properties.category", is("competitor")).body("properties.brands", is("CLAAS"))
                .body("properties.brand_known", is(true)).body("properties.street", is("Betriebsgebiet Inning 1/6"))
                .body("properties.postcode", is("3383")).body("properties.city", is("Hürm"))
                .body("properties.source_type", is("web_research"))
                .body("properties.source_url", is("https://www.landtechnik-heindl.at/kontakt/"))
                .body("properties", hasKey("more_sources")).body("properties.more_sources", nullValue())
                .body("properties.retrieved_at", is("2026-11-02")).body("properties.verified", is(false))
                .body("properties.note", is("Koordinate manuell auf OSM ermittelt")).extract().jsonPath()
                // RestAssured liest Zahlen sonst als float (zu ungenau für 6 Nachkommastellen)
                .using(JsonPathConfig.jsonPathConfig().numberReturnType(NumberReturnType.DOUBLE));

        // GeoJSON: [Länge, Breite]
        assertEquals(15.410884, json.getDouble("geometry.coordinates[0]"), DELTA);
        assertEquals(48.183382, json.getDouble("geometry.coordinates[1]"), DELTA);
    }

    @Test
    @TestSecurity(user = "alice")
    void getWithUnknownIdReturnsNotFound() {
        given().when().get("/locations/does-not-exist").then().statusCode(404).contentType("application/json")
                .body("error", containsString("does-not-exist"));
    }

    @Test
    void openApiDocumentsEndpointsAndBearerScheme() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths", hasKey("/locations")).body("paths", hasKey("/locations/{id}"))
                .body("components.securitySchemes.bearerAuth.type", is("http"))
                .body("components.securitySchemes.bearerAuth.scheme", is("bearer"))
                .body("paths.'/locations'.get.responses", hasKey("400"))
                .body("paths.'/locations/{id}'.get.responses", hasKey("404"));
    }

    @Test
    void swaggerUiIsAvailable() {
        given().when().get("/q/swagger-ui/").then().statusCode(200);
    }
}
