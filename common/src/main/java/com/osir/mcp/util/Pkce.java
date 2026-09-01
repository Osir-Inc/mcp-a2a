package com.osir.mcp.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (RFC 7636) helpers for the device login flow.
 * Keycloak enforces S256 PKCE on all public clients (client policy, 2026-09-01),
 * including the device authorization grant.
 */
public final class Pkce {

    public static final String METHOD_S256 = "S256";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pkce() {
    }

    /** 43-char base64url code_verifier from 32 random bytes. */
    public static String newVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** code_challenge = BASE64URL(SHA256(verifier)). */
    public static String challengeS256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
