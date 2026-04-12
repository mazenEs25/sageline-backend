package com.pfe.sageline.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize on methods
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;

    public SecurityConfig(KeycloakJwtConverter keycloakJwtConverter) {
        this.keycloakJwtConverter = keycloakJwtConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF (stateless JWT)
                .csrf(csrf -> csrf.disable())

                // Stateless session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // URL-based authorization
                .authorizeHttpRequests(auth -> auth
                        // WebSocket endpoint
                        .requestMatchers("/ws/**").permitAll()

                        // API Messagerie
                        .requestMatchers("/api/messages/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        // Public endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // ─── ADMIN ENDPOINTS ───
                        // Users: only ADMIN_IT can create/update/delete
                        .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN_IT")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN_IT")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN_IT")

                        // Lines: ADMIN_IT and CHEF_SECTEUR can manage
                        .requestMatchers(HttpMethod.POST, "/api/lines/**").hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")
                        .requestMatchers(HttpMethod.PUT, "/api/lines/**").hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")
                        .requestMatchers(HttpMethod.DELETE, "/api/lines/**").hasRole("ADMIN_IT")
                        .requestMatchers(HttpMethod.PATCH, "/api/lines/**").hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")

                        // Zones: ADMIN_IT and CHEF_SECTEUR can manage
                        .requestMatchers(HttpMethod.POST, "/api/zones/**").hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")
                        .requestMatchers(HttpMethod.PUT, "/api/zones/**").hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")
                        .requestMatchers(HttpMethod.DELETE, "/api/zones/**").hasRole("ADMIN_IT")

                        // ─── VALIDATION ENDPOINTS ───
                        // Create/close validations: TECH_VALIDATION, CHEF_SECTEUR, ADMIN_IT
                        .requestMatchers(HttpMethod.POST, "/api/validations/**")
                        .hasAnyRole("ADMIN_IT", "CHEF_SECTEUR", "TECH_VALIDATION")
                        .requestMatchers(HttpMethod.PATCH, "/api/validations/*/close")
                        .hasAnyRole("ADMIN_IT", "CHEF_SECTEUR", "TECH_VALIDATION")
                        .requestMatchers(HttpMethod.DELETE, "/api/validations/**")
                        .hasAnyRole("ADMIN_IT", "CHEF_SECTEUR")

                        // Validation results: TECH_VALIDATION and above
                        .requestMatchers(HttpMethod.POST, "/api/validation-results/**")
                        .hasAnyRole("ADMIN_IT", "CHEF_SECTEUR", "TECH_VALIDATION")
                        .requestMatchers(HttpMethod.DELETE, "/api/validation-results/**")
                        .hasAnyRole("ADMIN_IT", "CHEF_SECTEUR", "TECH_VALIDATION")

                        // KPIs: recalculate is admin-only
                        .requestMatchers(HttpMethod.POST, "/api/kpis/**").hasRole("ADMIN_IT")
                        .requestMatchers(HttpMethod.DELETE, "/api/kpis/**").hasRole("ADMIN_IT")

                        // ─── READ ACCESS ───
                        // All authenticated users can read (GET)
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                        // Everything else requires auth
                        .anyRequest().authenticated()
                )

                // JWT Resource Server with Keycloak role converter
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtConverter)
                        )
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}