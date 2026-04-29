package com.superchat.auth.web;

import java.time.Instant;
import java.util.Map;

import com.superchat.auth.domain.User;
import com.superchat.auth.security.JwtService;
import com.superchat.auth.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
    private final UserService userService;

    public AuthController(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        if (request.phone() == null || request.phone().isBlank() || 
            request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone and password are required"));
        }

        try {
            User user = userService.registerUser(request.phone(), request.password(), request.alias());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "phone", user.getPhone(),
                    "alias", user.getAlias() != null ? user.getAlias() : "",
                    "createdAt", user.getCreatedAt().toString(),
                    "message", "User registered successfully"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.username().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
        }

        // Auto-register the account if it does not exist yet.
        User user = userService.findByPhone(request.username())
                .orElseGet(() -> userService.registerUser(request.username(), request.password(), request.alias()));

        if (!userService.validatePassword(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid phone or password"));
        }

        // Use stored alias if available, otherwise use provided alias or empty
        String aliasToUse = user.getAlias() != null ? user.getAlias() : (request.alias() != null ? request.alias() : "");
        
        String token = jwtService.generateToken(request.username(), aliasToUse);
        Claims claims = jwtService.parseAndValidate(token);

        var response = Map.<String, Object>of(
                "token", token,
                "tokenType", "Bearer",
                "username", request.username(),
                "expiresAt", claims.getExpiration().toInstant().toString(),
                "issuedAt", claims.getIssuedAt().toInstant().toString()
        );

        if (aliasToUse != null && !aliasToUse.isBlank()) {
            response = Map.ofEntries(
                    Map.entry("token", token),
                    Map.entry("tokenType", "Bearer"),
                    Map.entry("username", request.username()),
                    Map.entry("alias", aliasToUse),
                    Map.entry("expiresAt", claims.getExpiration().toInstant().toString()),
                    Map.entry("issuedAt", claims.getIssuedAt().toInstant().toString())
            );
        }

        return ResponseEntity.ok(response);
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
            var map = new java.util.HashMap<String, Object>();
            map.put("valid", true);
            map.put("username", claims.getSubject());
            if (claims.get("alias") != null) {
                map.put("alias", String.valueOf(claims.get("alias")));
            }
            map.put("expiresAt", claims.getExpiration().toInstant().toString());
            map.put("issuedAt", claims.getIssuedAt().toInstant().toString());
            map.put("validatedAt", Instant.now().toString());
            return ResponseEntity.ok(map);
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

    public record LoginRequest(String username, String password, String alias) {}

    public record RegisterRequest(String phone, String password, String alias) {}
}
