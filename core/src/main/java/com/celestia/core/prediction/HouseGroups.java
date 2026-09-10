package com.celestia.core.prediction;

import java.util.Locale;
import java.util.Set;

/**
 * The KP house-group taxonomy — the {@code explainHouseGrouping} tool. Closed and
 * versioned: a new matter or a changed house set is an {@code EngineVersion} bump.
 * Source: {@code core/REFERENCES.md}.
 */
public final class HouseGroups {

    private HouseGroups() {}

    /** The matter for a lower-case key (`"marriage"`, …). */
    public static Matter fromKey(String key) {
        String normalised = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Matter m : Matter.values()) {
            if (m.key().equals(normalised)) {
                return m;
            }
        }
        throw new IllegalArgumentException("unknown matter: " + key);
    }

    public static Set<Matter> all() {
        return Set.of(Matter.values());
    }
}
