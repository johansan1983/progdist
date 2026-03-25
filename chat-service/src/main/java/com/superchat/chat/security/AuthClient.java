package com.superchat.chat.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class AuthClient {

    private final RestClient restClient;
    private final String validateUrl;

    public AuthClient(RestClient.Builder restClientBuilder, @Value("${auth.validate-url}") String validateUrl) {
        this.restClient = restClientBuilder.build();
        this.validateUrl = validateUrl;
    }

    public String validateAndGetUsername(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }

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
}
