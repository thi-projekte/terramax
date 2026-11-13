package de.thi.terramax.process.security;

import org.cibseven.bpm.engine.IdentityService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Absicherung der REST-API: Jeder Aufruf von /engine-rest braucht ein gültiges Keycloak-Token mit der Rolle
 * sales_professional (sonst 403, ohne Token 401). Nur der Health-Check ist offen.
 */
@Configuration
public class SecurityConfig {

    /**
     * Erlaubte Browser-Ursprünge für das React-Frontend, z. B. http://localhost:5173.
     */
    @ConfigurationProperties("terramax.cors")
    public record CorsProperties(List<String> origins) {
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityService identityService,
            CorsProperties cors) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/engine-rest/**").hasAuthority(Roles.SALES_PROFESSIONAL)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Die REST-API nutzt ausschließlich Bearer-Tokens und keine Cookies; CSRF-Schutz ist dort unnötig
                .csrf(csrf -> csrf.ignoringRequestMatchers("/engine-rest/**"))
                .cors(c -> c.configurationSource(corsSource(cors)))
                .addFilterAfter(new EngineAuthenticationFilter(identityService), AuthorizationFilter.class);
        return http.build();
    }

    private static JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> KeycloakJwt.realmRoles(jwt).stream().<GrantedAuthority> map(SimpleGrantedAuthority::new)
                        .toList());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private static CorsConfigurationSource corsSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.origins() == null ? List.of() : cors.origins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Für alle Pfade: Die REST-API läuft in einem eigenen Servlet (Jersey), dort sieht der Filter nur den Pfad
        // innerhalb von /engine-rest. Erlaubt sind trotzdem nur die Ursprünge aus terramax.cors.origins.
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
