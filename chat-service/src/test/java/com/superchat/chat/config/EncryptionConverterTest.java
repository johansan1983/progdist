package com.superchat.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionConverterTest {

    // Valid 32-byte key (64 hex chars)
    private static final String VALID_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private EncryptionConverter converter() {
        return new EncryptionConverter(VALID_KEY);
    }

    @Test
    void roundtrip_plaintext_survives_encrypt_decrypt() {
        var c = converter();
        String original = "Hello, enterprise world!";
        String encrypted = c.convertToDatabaseColumn(original);
        String decrypted = c.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void roundtrip_unicode_content() {
        var c = converter();
        String original = "Hola 🌍 こんにちは árbol";
        assertEquals(original, c.convertToEntityAttribute(c.convertToDatabaseColumn(original)));
    }

    @Test
    void null_in_returns_null_out_for_encrypt() {
        assertNull(converter().convertToDatabaseColumn(null));
    }

    @Test
    void null_in_returns_null_out_for_decrypt() {
        assertNull(converter().convertToEntityAttribute(null));
    }

    @Test
    void different_encryptions_of_same_plaintext_produce_different_ciphertexts() {
        var c = converter();
        String plain = "same message";
        String enc1 = c.convertToDatabaseColumn(plain);
        String enc2 = c.convertToDatabaseColumn(plain);
        // Each call uses a random IV, so ciphertexts must differ
        assertNotEquals(enc1, enc2);
    }

    @Test
    void encrypted_value_is_base64_encoded() {
        String encrypted = converter().convertToDatabaseColumn("test");
        // Must be valid Base64 (no exception when decoded)
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(encrypted));
    }

    @Test
    void wrong_key_length_throws_on_construction() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionConverter("tooshort"));
    }

    @Test
    void tampered_ciphertext_throws_on_decrypt() {
        var c = converter();
        String encrypted = c.convertToDatabaseColumn("secret");
        // Flip one byte in the base64 payload
        byte[] bytes = java.util.Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0xFF;
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> c.convertToEntityAttribute(tampered));
    }
}
