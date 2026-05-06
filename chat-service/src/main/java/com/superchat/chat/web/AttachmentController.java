package com.superchat.chat.web;

import com.superchat.chat.service.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/chat/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, Object>> presign(@RequestBody PresignRequest request) {
        if (request.filename() == null || request.filename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (request.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }
        try {
            AttachmentService.PresignResult result = attachmentService.presign(
                    request.filename(), request.contentType(), request.conversationId());
            return ResponseEntity.ok(Map.of(
                    "uploadUrl", result.uploadUrl(),
                    "objectKey", result.objectKey(),
                    "publicUrl", result.publicUrl(),
                    "attachmentType", result.attachmentType()
            ));
        } catch (Exception e) {
            log.error("Presign failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MinIO unavailable: " + e.getMessage());
        }
    }

    public record PresignRequest(String filename, String contentType, Long conversationId) {}
}
