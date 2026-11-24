package com.celestia.guardrail;

import java.util.Objects;
import java.util.Optional;

/**
 * What to send the user, and whether anything was withheld.
 *
 * @param delivered false only when the leak scan withheld the reply
 * @param text the decorated reply, the fixed redirect, or the fixed safe message
 * @param withheldReason which disclosure family fired, for the operator; present
 *     only when {@code delivered} is false
 */
public record OutputResult(boolean delivered, String text, Optional<LeakRule.LeakFamily> withheldReason) {

    public OutputResult {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(withheldReason, "withheldReason");
        if (delivered == withheldReason.isPresent()) {
            throw new IllegalArgumentException(
                    "a withheld result must name its family, and a delivered one must not");
        }
    }

    static OutputResult delivered(String text) {
        return new OutputResult(true, text, Optional.empty());
    }

    static OutputResult withheld(LeakRule.LeakFamily family) {
        return new OutputResult(false, FixedMessages.safeReplacement(), Optional.of(family));
    }
}
