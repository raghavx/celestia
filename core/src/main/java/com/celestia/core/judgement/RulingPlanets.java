package com.celestia.core.judgement;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.util.Set;

/**
 * The KP ruling planets for a judgment moment: the lagna and Moon sign / star /
 * (optionally) sub lords, the day lord, plus Rahu / Ketu added by agency.
 * Source: {@code core/REFERENCES.md}; algorithm {@code research.md} §3.
 *
 * @param judgment the judgment instant + place, echoed back
 * @param planets the ruling planets, each with its non-empty source set
 * @param dayLord lord of the KP weekday
 * @param weekday the resolved KP weekday
 * @param dayLordFallback true when there was no sunrise and the civil weekday was used (FR-014)
 * @param includeSubLords the {@link Options#includeSubLords()} value used
 * @param accuracy mirrors the Moon position for the instant
 * @param engineVersion the engine identity
 */
public record RulingPlanets(
        BirthData judgment,
        Set<RulingPlanet> planets,
        Graha dayLord,
        KpWeekday weekday,
        boolean dayLordFallback,
        boolean includeSubLords,
        Accuracy accuracy,
        EngineVersion engineVersion) {

    public RulingPlanets {
        planets = Set.copyOf(planets);
    }

    /** The sources that make {@code graha} a ruling planet, or an empty set. */
    public Set<RpSource> sourcesFor(Graha graha) {
        return planets.stream()
                .filter(p -> p.graha() == graha)
                .findFirst()
                .map(RulingPlanet::sources)
                .orElse(Set.of());
    }

    public boolean isRuling(Graha graha) {
        return planets.stream().anyMatch(p -> p.graha() == graha);
    }

    /**
     * Switches for the ruling-planet rules.
     *
     * @param includeSubLords include {@code LAGNA_SUB} / {@code MOON_SUB} (default
     *     true — modern KP; false = classic KSK)
     * @param includeNodeAspects deferred extension point — add a node when a graha
     *     aspecting it is ruling. No aspect scheme in v1; default false.
     */
    public record Options(boolean includeSubLords, boolean includeNodeAspects) {

        public static Options defaults() {
            return new Options(true, false);
        }
    }
}
