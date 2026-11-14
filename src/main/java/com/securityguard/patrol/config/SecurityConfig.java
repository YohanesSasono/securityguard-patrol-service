package com.securityguard.patrol.config;

import com.securityguard.security.RestAccessDeniedHandler;
import com.securityguard.security.RestAuthenticationEntryPoint;
import com.securityguard.security.SgJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth2 resource server. Access tokens are RS256 JWTs issued by identity-service and verified against
 * its JWKS ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}); the issuer is checked too when
 * {@code issuer-uri} is set. {@link SgJwtAuthenticationConverter} maps the claims to
 * {@code AuthenticatedUser} + {@code ROLE_*}, so {@code @PreAuthorize} and {@code CurrentUserProvider} work.
 *
 * <p>No {@code @Order}: this is the catch-all chain. sg-observability's actuator chain ({@code @Order(1)},
 * enabled with {@code sg.observability.actuator-security.enabled=true}) takes precedence for /actuator/**.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SgJwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
