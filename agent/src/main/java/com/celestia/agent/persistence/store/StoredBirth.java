package com.celestia.agent.persistence.store;

import com.celestia.ephemeris.BirthData;
import com.celestia.geo.ResolvedBirth;

/**
 * A birth as reloaded from {@code birth_data}: the caller-supplied {@code name}
 * and {@code placeQuery} plus a reconstructed {@link ResolvedBirth}.
 * {@link #birthData()} is identical to the value saved.
 */
public record StoredBirth(String name, String placeQuery, ResolvedBirth resolved) {

    public BirthData birthData() {
        return resolved.birthData();
    }
}
