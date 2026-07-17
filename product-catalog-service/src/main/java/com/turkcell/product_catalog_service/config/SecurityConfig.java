package com.turkcell.product_catalog_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

/**
 * Dokuman Bolum 13: JWT dogrulama + rol tabanli yetkilendirme.
 * Customer/billing servislerindeki kalibin aynisi (Keycloak realm_access.roles ->
 * ROLE_* donusumu). Ek olarak /hello, actuator ve Swagger anonim birakildi:
 * ops paneli health'i yokluyor, ekip Swagger UI ile test ediyor.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/hello", "/actuator/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Katalog okumalari acik: order-service Feign ile token'siz cagiriyor
                        // (edge auth gateway'de). Yazma islemleri @PreAuthorize(ADMIN) ile korunur.
                        .requestMatchers(HttpMethod.GET, "/api/v1/tariffs/**", "/api/v1/addons/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) return List.of();
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return List.of();
            return roles.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        });
        return converter;
    }
}
