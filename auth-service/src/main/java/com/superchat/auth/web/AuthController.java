package com.superchat.auth.web;

import java.time.Instant;
import java.util.Map;

import com.superchat.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.username().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
        }

        String token = jwtService.generateToken(request.username());
        Claims claims = jwtService.parseAndValidate(token);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "username", request.username(),
                "expiresAt", claims.getExpiration().toInstant().toString(),
                "issuedAt", claims.getIssuedAt().toInstant().toString()
        ));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestParam(required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String resolvedToken = resolveToken(token, authorization);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", "token is required"
            ));
        }

        try {
            Claims claims = jwtService.parseAndValidate(resolvedToken);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", claims.getSubject(),
                    "expiresAt", claims.getExpiration().toInstant().toString(),
                    "issuedAt", claims.getIssuedAt().toInstant().toString(),
                    "validatedAt", Instant.now().toString()
            ));
        } catch (JwtException ex) {
            return ResponseEntity.status(401).body(Map.of(
                    "valid", false,
                    "error", "invalid or expired token"
            ));
        }
    }

    private String resolveToken(String token, String authorization) {
        if (token != null && !token.isBlank()) {
            return token;
        }

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        return null;
    }

    public record LoginRequest(String username, String password) {}
}
