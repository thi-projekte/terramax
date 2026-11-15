package de.thi.terramax.location;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * OpenAPI-Beschreibung des Service. Die Doku liegt unter {@code /q/openapi}, Swagger UI unter {@code /q/swagger-ui}.
 */
@OpenAPIDefinition(info = @Info(title = "TerraMax location-service", version = "1.0.0", description = "Händlerstandorte als GeoJSON"))
@SecurityScheme(securitySchemeName = LocationServiceApplication.BEARER_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", description = "Keycloak-Access-Token: Authorization: Bearer <token>")
public class LocationServiceApplication extends Application {

    public static final String BEARER_SCHEME = "bearerAuth";
}
