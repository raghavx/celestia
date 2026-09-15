package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-app-secret";
    private static final byte[] BODY = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void validSignatureIsAccepted() throws Exception {
        assertThat(WebhookSignatureVerifier.valid(BODY, sign(BODY, SECRET), SECRET)).isTrue();
    }

    @Test
    void aTamperedBodyIsRejected() throws Exception {
        String sig = sign(BODY, SECRET);
        byte[] tampered = "{\"hello\":\"WORLD\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(WebhookSignatureVerifier.valid(tampered, sig, SECRET)).isFalse();
    }

    @Test
    void aWrongSecretIsRejected() throws Exception {
        assertThat(WebhookSignatureVerifier.valid(BODY, sign(BODY, "wrong-secret"), SECRET)).isFalse();
    }

    @Test
    void aMissingHeaderIsRejected() {
        assertThat(WebhookSignatureVerifier.valid(BODY, null, SECRET)).isFalse();
    }

    @Test
    void aMalformedHeaderIsRejectedNotThrown() {
        assertThat(WebhookSignatureVerifier.valid(BODY, "not-a-signature", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.valid(BODY, "sha256=not-hex-zz", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.valid(BODY, "sha256=", SECRET)).isFalse();
    }

    @Test
    void aBlankOrNullSecretFailsClosed() throws Exception {
        assertThat(WebhookSignatureVerifier.valid(BODY, sign(BODY, SECRET), "")).isFalse();
        assertThat(WebhookSignatureVerifier.valid(BODY, sign(BODY, SECRET), null)).isFalse();
    }
}
