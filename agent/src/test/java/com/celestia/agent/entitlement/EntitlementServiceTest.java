package com.celestia.agent.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SPEC-012 FR-001–FR-003: who is answered, and what happens when we cannot tell. */
class EntitlementServiceTest {

    private static final String OWNER = "+919876543210";
    private static final String STRANGER = "+447700900123";

    private static EntitlementService with(String... entries) {
        return new EntitlementService(new EntitlementProperties(List.of(entries)));
    }

    @Test
    void aListedNumberIsAllowed() {
        assertThat(with(OWNER).check(OWNER)).isEqualTo(EntitlementDecision.OWNER_ALLOW_LIST);
    }

    @Test
    void anUnlistedNumberIsDenied() {
        assertThat(with(OWNER).check(STRANGER)).isEqualTo(EntitlementDecision.DENIED_NOT_LISTED);
    }

    @Test
    @DisplayName("an empty allow-list denies everyone — the case worth re-reading")
    void anEmptyListDeniesEveryone() {
        // An empty list meaning "allow all" is how a configuration slip becomes an
        // open service. It means the opposite, and it is the committed default.
        assertThat(with().check(OWNER)).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
        assertThat(with().check(STRANGER)).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
    }

    @Test
    @DisplayName("an unreadable entry makes the whole list unusable, including for the owner")
    void aMalformedEntryDeniesEveryone() {
        // A typo in the owner's own number is a broken config, not a stranger at
        // the door. Dropping the bad entry would answer the wrong question safely.
        EntitlementService service = with(OWNER, "not-a-phone-number");
        assertThat(service.check(OWNER)).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
        assertThat(service.check(STRANGER)).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
    }

    @Test
    void theTwoDenialsAreDistinguishableInTheDecisionAndIdenticalToTheSender() {
        assertThat(EntitlementDecision.DENIED_NOT_LISTED)
                .isNotEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
        assertThat(EntitlementDecision.DENIED_NOT_LISTED.allowed()).isFalse();
        assertThat(EntitlementDecision.DENIED_UNAVAILABLE.allowed()).isFalse();
    }

    @Test
    @DisplayName("a bare-digits entry works: config and channel agree on one spelling")
    void bareDigitsAreNormalised() {
        assertThat(with("919876543210").check(OWNER)).isEqualTo(EntitlementDecision.OWNER_ALLOW_LIST);
    }

    @Test
    void aBlankSenderIsDeniedRatherThanMatched() {
        assertThat(with(OWNER).check("")).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
        assertThat(with(OWNER).check(null)).isEqualTo(EntitlementDecision.DENIED_UNAVAILABLE);
    }

    @Test
    @DisplayName("the guard of ADR-0026 condition 1: exactly one, or the application stops")
    void aThirdPartyProviderIsBoundedToOneAllowListedNumber() {
        // Constitution v3.0.0 permits a third-party inference API only for a
        // deployment serving one person — the developer himself, who can consent
        // for his own data. Cardinality is what code can check; whose number it is
        // stays a human obligation under the ADR.
        assertThat(new OwnerOnlyAllowListGuard("anthropic", new EntitlementProperties(List.of(OWNER))))
                .isNotNull();

        assertThatThrownBy(() -> new OwnerOnlyAllowListGuard(
                        "anthropic", new EntitlementProperties(List.of(OWNER, STRANGER))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one")
                .hasMessageContaining("needs a constitution amendment");

        assertThatThrownBy(() -> new OwnerOnlyAllowListGuard(
                        "anthropic", new EntitlementProperties(List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void itNeverThrows() {
        // A configuration fault is a denial, not an exception out of the reply path.
        assertThat(new EntitlementService(new EntitlementProperties(null)).check(OWNER).allowed()).isFalse();
    }
}
