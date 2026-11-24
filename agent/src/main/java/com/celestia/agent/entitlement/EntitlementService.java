package com.celestia.agent.entitlement;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * May this sender be answered? (SPEC-012 FR-001–FR-003,
 * contracts/entitlement-api.md).
 *
 * <p><b>Consulted before the gate, before any chart work, before the model.</b>
 * Work done for someone not entitled to it is work that should not have been
 * done — and a model that answers anyone is a model answering strangers at the
 * owner's cost.
 *
 * <p><b>Never throws, and denies when it cannot decide</b> (FR-003). A
 * configuration fault is a denial, not an exception out of the reply path.
 *
 * <p>This is the seam SPEC-018 replaces when plans and quotas arrive: it keeps
 * {@link #check(String)} as the only entry point, so the reply path does not
 * change when the allow-list becomes a subscription lookup.
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    /** E.164: a {@code +}, a non-zero country digit, then 7–14 more. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    /** Bare digits, as WhatsApp itself sends them before the channel prepends the {@code +}. */
    private static final Pattern BARE_DIGITS = Pattern.compile("^[1-9]\\d{7,14}$");

    private final Set<String> allowed;
    private final boolean usable;

    public EntitlementService(EntitlementProperties properties) {
        List<String> entries = properties.allowList();
        Set<String> parsed = new java.util.HashSet<>();
        boolean ok = !entries.isEmpty();
        for (String entry : entries) {
            String normalised = normalise(entry);
            if (E164.matcher(normalised).matches()) {
                parsed.add(normalised);
            } else {
                // One unreadable entry makes the whole list unusable. A typo in the
                // owner's own number is a broken config, not a stranger at the door,
                // and quietly dropping it would answer the wrong question safely.
                log.error("celestia.entitlement.allow-list holds an entry that is not an E.164 number; "
                        + "the list is unusable and nobody will be answered (FR-003)");
                ok = false;
            }
        }
        this.allowed = Set.copyOf(parsed);
        this.usable = ok;
        if (entries.isEmpty()) {
            log.warn("celestia.entitlement.allow-list is empty — nobody will be answered. "
                    + "This is the safe default, not a failure (FR-003)");
        }
    }

    /**
     * @param senderE164 the verified sender, and the only input — Constitution V
     *     forbids reading identity from message text
     */
    public EntitlementDecision check(String senderE164) {
        if (!usable) {
            return EntitlementDecision.DENIED_UNAVAILABLE;
        }
        if (senderE164 == null || senderE164.isBlank()) {
            return EntitlementDecision.DENIED_UNAVAILABLE;
        }
        return allowed.contains(normalise(senderE164))
                ? EntitlementDecision.OWNER_ALLOW_LIST
                : EntitlementDecision.DENIED_NOT_LISTED;
    }

    /**
     * Accepts a bare-digits entry by prepending {@code +}, the same normalisation
     * {@code InboundPayloadParser} applies to the sender, so configuration and
     * channel agree on one spelling. Anything else is left alone and will fail
     * the E.164 check.
     */
    private static String normalise(String value) {
        String trimmed = value.trim();
        return BARE_DIGITS.matcher(trimmed).matches() ? "+" + trimmed : trimmed;
    }
}
