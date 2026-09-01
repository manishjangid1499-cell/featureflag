package com.featureflag.flag_service.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SdkKeyCredentialService {

    public static final String KEY_PREFIX = "ff_sdk_";
    public static final int SECRET_BYTES = 32;
    public static final int ENCODED_SECRET_LENGTH = 43;
    public static final int DISPLAY_SECRET_CHARACTERS = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedSdkKey generate() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String rawKey = KEY_PREFIX
                + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret);

        return new GeneratedSdkKey(
                rawKey,
                hash(rawKey),
                rawKey.substring(
                        0,
                        KEY_PREFIX.length()
                                + DISPLAY_SECRET_CHARACTERS
                )
        );
    }

    public String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    public boolean hasValidFormat(String rawKey) {
        if (rawKey == null
                || rawKey.length()
                != KEY_PREFIX.length() + ENCODED_SECRET_LENGTH
                || !rawKey.startsWith(KEY_PREFIX)) {
            return false;
        }

        for (int index = KEY_PREFIX.length();
             index < rawKey.length();
             index++) {
            if (!isBase64UrlCharacter(rawKey.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBase64UrlCharacter(char value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '_';
    }

    public record GeneratedSdkKey(
            String rawKey,
            String keyHash,
            String keyPrefix
    ) {
    }
}
