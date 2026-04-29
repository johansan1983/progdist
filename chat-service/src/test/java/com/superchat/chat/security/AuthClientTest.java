package com.superchat.chat.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class AuthClientTest {

    private AuthClient authClient;
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        jwtSecret = "ChangeThisSecretToAtLeast32CharsLong12345";
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);
        when(builder.build()).thenReturn(restClient);

        authClient = new AuthClient(builder, "http://localhost:8081/auth/validate", jwtSecret);
    }

    @Test
    void testValidateLocalJWT_ValidToken_ReturnsUsername() {
        String token = Jwts.builder()
                .claim("username", "testuser")
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        String result = authClient.validateAndGetUsername("Bearer " + token);
        assertEquals("testuser", result);
    }

    @Test
    void testValidateLocalJWT_NoBearer_ReturnsUsername() {
        String token = Jwts.builder()
                .claim("username", "testuser")
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        String result = authClient.validateAndGetUsername(token);
        assertEquals("testuser", result);
    }

    @Test
    void testValidateLocalJWT_InvalidSignature_ThrowsException() {
        String wrongSecret = "WrongSecretToAt32CharsLongForTesting";
        String token = Jwts.builder()
                .claim("username", "testuser")
                .signWith(Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(ResponseStatusException.class, () -> authClient.validateAndGetUsername("Bearer " + token));
    }

    @Test
    void testValidateJWT_NoAuthHeader_ThrowsException() {
        assertThrows(ResponseStatusException.class, () -> authClient.validateAndGetUsername(null));
    }

    @Test
    void testValidateJWT_BlankAuthHeader_ThrowsException() {
        assertThrows(ResponseStatusException.class, () -> authClient.validateAndGetUsername("  "));
    }

    @Test
    void testValidateLocalJWT_WithSubject_ReturnsSubject() {
        String token = Jwts.builder()
                .subject("subjuser")
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        String result = authClient.validateAndGetUsername("Bearer " + token);
        assertEquals("subjuser", result);
    }
}
