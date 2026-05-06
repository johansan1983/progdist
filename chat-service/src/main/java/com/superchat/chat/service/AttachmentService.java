package com.superchat.chat.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AttachmentService {

    private final MinioClient internalClient;
    private final MinioClient externalClient;
    private final String bucket;
    private final String externalUrl;

    public AttachmentService(
            @Qualifier("minioClient") MinioClient internalClient,
            @Qualifier("externalMinioClient") MinioClient externalClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.external-url}") String externalUrl
    ) {
        this.internalClient = internalClient;
        this.externalClient = externalClient;
        this.bucket = bucket;
        this.externalUrl = externalUrl;
    }

    public PresignResult presign(String filename, String contentType, Long conversationId) throws Exception {
        String objectKey = conversationId + "/" + UUID.randomUUID() + "/" + filename;

        // Generate using the internal client (reachable inside Docker), then swap the host
        // to the external URL so the browser can reach MinIO directly.
        String internalUrl = internalClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(15, TimeUnit.MINUTES)
                        .build()
        );

        String uploadUrl = internalUrl.replaceFirst(
                "^(https?://)[^/?#]+(.*)", "$1" + externalUrl.replaceFirst("^https?://", "") + "$2"
        );

        String publicUrl = externalUrl + "/" + bucket + "/" + objectKey;
        String attachmentType = resolveType(contentType);

        return new PresignResult(uploadUrl, objectKey, publicUrl, attachmentType);
    }

    private String resolveType(String contentType) {
        if (contentType == null) return "FILE";
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "FILE";
    }

    public record PresignResult(String uploadUrl, String objectKey, String publicUrl, String attachmentType) {}
}
