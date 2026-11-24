package com.celestia.agent.entitlement;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constitution v3.0.0's bounded exception, enforced rather than trusted
 * (ADR-0026 condition 1).
 *
 * <p>A deployment may use a third-party inference API <b>only</b> while its
 * entitlement allow-list holds exactly one number — the developer's own. This
 * class exists because that sentence in a document is not enough: a working
 * preview on a third-party API creates pressure to add a second number, and that
 * is precisely the transition the deployment rule exists to stop. So
 * <b>the application refuses to start</b> instead.
 *
 * <p>It is constructed only when a third-party provider is selected, so a
 * self-hosted deployment is unaffected and may allow-list whoever it likes.
 *
 * <p><b>What this can and cannot check.</b> Cardinality is mechanical: exactly
 * one entry, or the context fails. <em>Whose</em> number it is cannot be checked
 * by code — nothing here knows who the developer is — so that half remains a
 * human obligation under ADR-0026. The check still does the work that matters:
 * admitting a second person now requires either moving to a self-hosted model or
 * returning for another amendment.
 *
 * <p>The permission also lapses when public sign-up opens. Nothing here can see
 * that, and SPEC-018 will replace this allow-list with a subscription lookup —
 * at which point this guard must fail, loudly, because an allow-list of one is no
 * longer what entitlement means.
 */
public final class OwnerOnlyAllowListGuard {

    private static final Logger log = LoggerFactory.getLogger(OwnerOnlyAllowListGuard.class);

    public OwnerOnlyAllowListGuard(String provider, EntitlementProperties entitlement) {
        List<String> allowList = entitlement.allowList();
        if (allowList.size() != 1) {
            throw new IllegalStateException(
                    "Constitution v3.0.0 permits a third-party inference API (celestia.llm.provider="
                            + provider + ") only for a deployment whose allow-list holds exactly one "
                            + "number — the developer's own. It holds " + allowList.size()
                            + ". Either set celestia.entitlement.allow-list to that one number, or move "
                            + "to a self-hosted model (celestia.llm.provider=ollama). Admitting a second "
                            + "person on a third-party API needs a constitution amendment, not a "
                            + "property change (ADR-0026 condition 1).");
        }
        log.info("third-party inference provider '{}' permitted: allow-list holds exactly one number "
                + "(Constitution v3.0.0, ADR-0026). This lapses the moment a second is added.", provider);
    }
}
