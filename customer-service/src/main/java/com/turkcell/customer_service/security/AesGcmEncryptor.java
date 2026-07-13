package com.turkcell.customer_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AesGcmEncryptor {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKey secretKey;

    public AesGcmEncryptor(@Value("${app.security.pii-encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = deriveIv(plaintext);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivB64 = Base64.getEncoder().encodeToString(iv);
            String cipherB64 = Base64.getEncoder().encodeToString(ciphertext);
            return ivB64 + ":" + cipherB64;
        } catch (Exception e) {
            throw new IllegalStateException("PII encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII decryption failed", e);
        }
    }

    // Deterministic IV so the same plaintext always produces the same ciphertext,
    // allowing the DB unique constraint on identity_number to keep working.
    private byte[] deriveIv(String plaintext) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(secretKey);
        byte[] hash = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(hash, IV_BYTES);
    }
}
