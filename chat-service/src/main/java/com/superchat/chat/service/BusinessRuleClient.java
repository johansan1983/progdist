package com.superchat.chat.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches per-org business rules from admin-service with a 5-minute TTL cache.
 *
 * Resilience: bounded timeouts + a circuit breaker. The fallback serves the last cached
 * rules for the org if available (so a brief admin-service blip doesn't drop enforcement),
 * and only falls back to an empty map (no restrictions) when nothing is cached.
 */
@Component
public class BusinessRuleClient {

    private static final Logger log = LoggerFactory.getLogger(BusinessRuleClient.class);
    private static final long TTL_MS = 5 * 60 * 1000L;

    private record CacheEntry(Map<String, String> rules, long expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final String adminServiceUrl;

    public BusinessRuleClient(RestTemplate restTemplate,
                               @Value("${admin.service.url:http://admin-service:8086}") String adminServiceUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    @CircuitBreaker(name = "businessRules", fallbackMethod = "getRulesFallback")
    public Map<String, String> getRules(String orgId) {
        if (orgId == null || orgId.isBlank()) return Collections.emptyMap();

        CacheEntry entry = cache.get(orgId);
        if (entry != null && System.currentTimeMillis() < entry.expiresAt()) {
            return entry.rules();
        }

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                adminServiceUrl + "/admin/organizations/" + orgId + "/rules",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        Map<String, String> rules = new ConcurrentHashMap<>();
        if (response.getBody() != null) {
            for (Map<String, Object> rule : response.getBody()) {
                Object k = rule.get("key");
                Object v = rule.get("value");
                if (k instanceof String key && v instanceof String value) {
                    rules.put(key, value);
                }
            }
        }
        cache.put(orgId, new CacheEntry(rules, System.currentTimeMillis() + TTL_MS));
        return rules;
    }

    /** Fail-open fallback: serve the last cached rules for the org, else no restrictions. */
    @SuppressWarnings("unused")
    Map<String, String> getRulesFallback(String orgId, Throwable t) {
        log.warn("[BusinessRules] admin-service unavailable for org={}, using {}: {}",
                orgId, cache.containsKey(orgId) ? "stale cache" : "empty rules", t.toString());
        CacheEntry entry = orgId != null ? cache.get(orgId) : null;
        return entry != null ? entry.rules() : Collections.emptyMap();
    }

    public void invalidate(String orgId) {
        cache.remove(orgId);
    }
}
