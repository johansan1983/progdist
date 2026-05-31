package com.superchat.chat.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls moderation-service /moderation/check before saving a message.
 *
 * Resilience: bounded RestTemplate timeouts + a circuit breaker. On a downstream failure
 * the breaker records it and routes to the fail-open fallback (allow the message). Once the
 * breaker is OPEN, subsequent calls fail open INSTANTLY instead of paying the read timeout —
 * preventing a latency cascade on the message hot path during a moderation outage.
 */
@Component
public class ModerationClient {

    private static final Logger log = LoggerFactory.getLogger(ModerationClient.class);

    private final RestTemplate restTemplate;
    private final String moderationUrl;

    public ModerationClient(RestTemplate restTemplate,
                             @Value("${moderation.service.url:http://moderation-service:8087}") String moderationUrl) {
        this.restTemplate = restTemplate;
        this.moderationUrl = moderationUrl;
    }

    public record CheckResult(String verdict, String sanitizedContent) {}

    @CircuitBreaker(name = "moderation", fallbackMethod = "checkFallback")
    public CheckResult check(String orgId, String userId, Long conversationId, String content) {
        if (orgId == null || content == null || content.isBlank()) {
            return new CheckResult("PASS", content);
        }
        Map<String, Object> body = Map.of(
                "orgId", orgId,
                "userId", userId != null ? userId : "",
                "conversationId", conversationId != null ? conversationId : 0L,
                "content", content
        );
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                moderationUrl + "/moderation/check", body, (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> resp = response.getBody();
        if (resp == null) return new CheckResult("PASS", content);
        String verdict = (String) resp.getOrDefault("verdict", "PASS");
        String sanitized = (String) resp.getOrDefault("sanitizedContent", content);
        return new CheckResult(verdict, sanitized);
    }

    /** Fail-open fallback: when moderation is unavailable or the breaker is open, allow the message. */
    @SuppressWarnings("unused")
    CheckResult checkFallback(String orgId, String userId, Long conversationId, String content, Throwable t) {
        log.warn("Moderation unavailable, failing open: {}", t.toString());
        return new CheckResult("PASS", content);
    }
}
