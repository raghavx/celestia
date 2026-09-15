package com.celestia.channel.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Meta's {@code X-Hub-Signature-256} over the exact raw request body
 * (FR-002). Fail-closed: any malformed input or internal error is a rejection,
 * never an exception.
 */
public final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {}

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    public static boolean valid(byte[] rawBody, String signatureHeader, String appSecret) {
        if (rawBody == null || signatureHeader == null || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        if (!signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String providedHex = signatureHeader.substring(PREFIX.length());
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedHex);
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            expected = mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException | RuntimeException e) {
            return false;
        }

        return MessageDigest.isEqual(expected, provided);
    }
}
