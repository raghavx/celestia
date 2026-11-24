package com.celestia.guardrail;

import java.util.Objects;

/**
 * What the gate is asked about: the message text and an opaque reference to
 * whoever sent it.
 *
 * <p><b>Deliberately not a channel type</b> (Constitution IX). The gate takes
 * plain text, so {@code guardrail} never imports {@code channel-whatsapp} and
 * the same gate serves any channel. {@code agent} adapts its own inbound type to
 * this one.
 *
 * @param text the message body as received; may be blank
 * @param senderRef an opaque reference to the sender, used only to tell "another
 *     account" from "birth data supplied in this message". It is never parsed as
 *     a phone number for any other purpose, and never appears in a decision
 */
public record GateRequest(String text, String senderRef) {

    public GateRequest {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(senderRef, "senderRef");
    }
}
