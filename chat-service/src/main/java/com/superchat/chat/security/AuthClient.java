package com.superchat.chat.security;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class AuthClient {

    private final RestClient restClient;
    private final String validateUrl;
    private final String jwtSecret;

    public AuthClient(RestClient.Builder restClientBuilder,
                      @Value("${auth.validate-url}") String validateUrl,
                      @Value("${auth.jwt-secret:}") String jwtSecret) {
        this.restClient = restClientBuilder.build();
        this.validateUrl = validateUrl;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
    }

    public String validateAndGetUsername(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }

        String token = extractToken(authorizationHeader);

        if (jwtSecret != null && !jwtSecret.isBlank()) {
            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                Object username = claims.get("username");
                if (username == null) {
                    username = claims.getSubject();
                }
                if (username == null || String.valueOf(username).isBlank()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token does not contain username");
                }
                return String.valueOf(username);
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }
        }

        // Fallback to remote validation
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(validateUrl)
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
                    })
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("valid"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }

            Object username = response.get("username");
            if (username == null || String.valueOf(username).isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token does not contain username");
            }

            return String.valueOf(username);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed");
        }
    }

    private String extractToken(String header) {
        String h = header.trim();
        if (h.toLowerCase().startsWith("bearer ")) {
            return h.substring(7).trim();
        }
        return h;
    }
}
