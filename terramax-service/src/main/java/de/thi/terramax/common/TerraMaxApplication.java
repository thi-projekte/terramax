package de.thi.terramax.common;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * OpenAPI-Beschreibung des Service. Die Doku liegt unter {@code /q/openapi}, Swagger UI unter {@code /q/swagger-ui}.
 */
@OpenAPIDefinition(info = @Info(title = "TerraMax terramax-service", version = "1.0.0", description = "Fachlicher Kern von TerraMax: Händler, Kunden, Touren, Besuchsprotokolle und Händlerstandorte"))
@SecurityScheme(securitySchemeName = TerraMaxApplication.BEARER_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", description = "Keycloak-Access-Token: Authorization: Bearer <token>")
public class TerraMaxApplication extends Application {

    public static final String BEARER_SCHEME = "bearerAuth";
}
