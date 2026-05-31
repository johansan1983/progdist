package com.superchat.compliance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls internal (no-JWT) export endpoints on chat-service and user-service
 * to collect all data for a given user for GDPR right-of-access export.
 */
@Component
public class DataExportClient {

    private static final Logger log = LoggerFactory.getLogger(DataExportClient.class);

    private final RestTemplate restTemplate;
    private final String internalToken;
    private final String chatServiceUrl;
    private final String userServiceUrl;

    public DataExportClient(RestTemplate restTemplate,
                             @Value("${internal.api.token:internal-secret}") String internalToken,
                             @Value("${chat.service.url:http://chat-service:8082}") String chatServiceUrl,
                             @Value("${user.service.url:http://user-service:8083}") String userServiceUrl) {
        this.restTemplate = restTemplate;
        this.internalToken = internalToken;
        this.chatServiceUrl = chatServiceUrl;
        this.userServiceUrl = userServiceUrl;
    }

    public List<Map<String, Object>> fetchMessages(String userId) {
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    chatServiceUrl + "/internal/export/messages/" + userId,
                    HttpMethod.GET,
                    tokenRequest(),
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[DataExport] could not fetch messages for user={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> fetchProfile(String userId) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/internal/export/profile/" + userId,
                    HttpMethod.GET,
                    tokenRequest(),
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    ? resp.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[DataExport] could not fetch profile for user={}: {}", userId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** GDPR erasure: anonymize a user's messages and profile across services. Returns true if both succeed. */
    public boolean eraseUserData(String userId) {
        boolean messagesOk = postErase(chatServiceUrl + "/internal/export/erase/" + userId);
        boolean profileOk  = postErase(userServiceUrl + "/internal/export/erase/profile/" + userId);
        return messagesOk && profileOk;
    }

    private boolean postErase(String url) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.POST, tokenRequest(),
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[DataExport] erase call failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private HttpEntity<Void> tokenRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        return new HttpEntity<>(headers);
    }
}
