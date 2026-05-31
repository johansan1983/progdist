package com.superchat.chat.web;

import com.superchat.chat.service.AttachmentService;
import com.superchat.chat.service.BusinessRuleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/chat/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;
    private final BusinessRuleClient businessRuleClient;

    public AttachmentController(AttachmentService attachmentService,
                                 BusinessRuleClient businessRuleClient) {
        this.attachmentService = attachmentService;
        this.businessRuleClient = businessRuleClient;
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, Object>> presign(
            @RequestHeader(value = "X-Org-Id", required = false) String orgId,
            @RequestBody PresignRequest request) {
        if (request.filename() == null || request.filename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (request.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }

        enforceAttachmentRules(orgId, request.contentType(), request.fileSizeBytes());

        try {
            AttachmentService.PresignResult result = attachmentService.presign(
                    request.filename(), request.contentType(), request.conversationId());
            return ResponseEntity.ok(Map.of(
                    "uploadUrl", result.uploadUrl(),
                    "objectKey", result.objectKey(),
                    "publicUrl", result.publicUrl(),
                    "attachmentType", result.attachmentType()
            ));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Presign failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MinIO unavailable: " + e.getMessage());
        }
    }

    private void enforceAttachmentRules(String orgId, String contentType, Long fileSizeBytes) {
        if (orgId == null || orgId.isBlank()) return;

        Map<String, String> rules = businessRuleClient.getRules(orgId);

        // File size check
        String maxMbStr = rules.get("max_file_size_mb");
        if (maxMbStr != null && fileSizeBytes != null) {
            try {
                long maxBytes = Long.parseLong(maxMbStr.trim()) * 1024L * 1024L;
                if (fileSizeBytes > maxBytes) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "File exceeds the maximum allowed size of " + maxMbStr + " MB");
                }
            } catch (NumberFormatException e) {
                log.warn("[BusinessRules] invalid max_file_size_mb value: {}", maxMbStr);
            }
        }

        // File type check
        String allowedTypes = rules.get("allowed_file_types");
        if (allowedTypes != null && contentType != null && !contentType.isBlank()) {
            boolean allowed = Arrays.stream(allowedTypes.split(","))
                    .map(String::trim)
                    .anyMatch(contentType::startsWith);
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File type '" + contentType + "' is not allowed by your organization");
            }
        }
    }

    public record PresignRequest(String filename, String contentType, Long conversationId, Long fileSizeBytes) {}
}
