package com.superchat.moderation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Tenant-isolation guard for @PreAuthorize SpEL. Resolves the caller's organization from
 * user-service's internal profile endpoint and verifies it matches the path {orgId}.
 * FAILS CLOSED: any error → access denied.
 */
@Component("orgAccess")
public class OrgAccess {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String internalToken;

    public OrgAccess(RestTemplate restTemplate,
                     @Value("${user.service.url:http://user-service:8083}") String userServiceUrl,
                     @Value("${internal.api.token:internal-secret}") String internalToken) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
        this.internalToken = internalToken;
    }

    public boolean belongsTo(Authentication authentication, UUID orgId) {
        if (authentication == null || orgId == null) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/internal/export/profile/" + authentication.getName(),
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Object callerOrg = resp.getBody() != null ? resp.getBody().get("orgId") : null;
            return callerOrg != null && orgId.toString().equals(callerOrg.toString());
        } catch (Exception e) {
            return false; // fail closed
        }
    }
}
