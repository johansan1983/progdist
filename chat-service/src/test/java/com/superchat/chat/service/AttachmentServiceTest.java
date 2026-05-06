package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

    private AttachmentService attachmentService;
    private MinioClient internalClient;
    private MinioClient externalClient;

    @BeforeEach
    void setUp() {
        internalClient = mock(MinioClient.class);
        externalClient = mock(MinioClient.class);
        attachmentService = new AttachmentService(internalClient, externalClient, "superchat-attachments", "http://localhost:9000");
    }

    @Test
    void testPresign_ValidInput_ReturnsPresignResult() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("photo.jpg", "image/jpeg", 1L);

        assertNotNull(result.uploadUrl());
        assertNotNull(result.objectKey());
        assertNotNull(result.publicUrl());
        assertTrue(result.uploadUrl().contains("localhost:9000"));
        assertTrue(result.objectKey().endsWith("photo.jpg"));
        assertEquals("IMAGE", result.attachmentType());
    }

    @Test
    void testPresign_AudioFile_ReturnsAudioType() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("voice.mp3", "audio/mpeg", 1L);

        assertEquals("AUDIO", result.attachmentType());
    }

    @Test
    void testPresign_GenericFile_ReturnsFileType() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("report.pdf", "application/pdf", 1L);

        assertEquals("FILE", result.attachmentType());
    }

    @Test
    void testPresign_PublicUrlUsesExternalHost() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("img.png", "image/png", 1L);

        assertTrue(result.publicUrl().startsWith("http://localhost:9000/superchat-attachments/"));
        assertFalse(result.publicUrl().contains("X-Amz-Signature"));
    }
}
