package com.celestia.agent.entitlement;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who may be answered (SPEC-012 T003). An allow-list of one for the preview;
 * SPEC-018's plans and quotas replace the source without changing the reply path
 * (FR-002, contracts/entitlement-api.md).
 *
 * <p><b>Deliberately permissive at bind time.</b> A malformed entry does not fail
 * startup — {@code EntitlementService} treats an unusable list as
 * {@code DENIED_UNAVAILABLE} and answers nobody, which is FR-003's fail-closed
 * behaviour rather than a dead application.
 *
 * @param allowList verified E.164 numbers; <b>empty denies everyone</b>, the safe
 *     default for a key someone will forget to set (FR-003)
 */
@ConfigurationProperties("celestia.entitlement")
public record EntitlementProperties(List<String> allowList) {

    public EntitlementProperties {
        // A blank entry is an unset environment variable, not a malformed number:
        // it means "nobody is listed" (deny everyone), which is not the same as
        // "the list is broken" (also deny everyone, but as DENIED_UNAVAILABLE).
        allowList = allowList == null
                ? List.of()
                : allowList.stream().filter(e -> e != null && !e.isBlank()).map(String::trim).toList();
    }
}
