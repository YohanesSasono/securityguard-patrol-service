package com.securityguard.patrol.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Forwards the caller's JWT (set by SgJwtAuthenticationConverter as credentials) to identity-service. */
final class CurrentBearerToken {

    private CurrentBearerToken() {
    }

    static HttpHeaders headers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String token = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            token = jwtAuthentication.getToken().getTokenValue();
        } else if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            token = jwt.getTokenValue();
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No authenticated caller token to forward to identity-service");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
