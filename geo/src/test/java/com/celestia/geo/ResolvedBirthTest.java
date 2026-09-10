package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.BirthData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ResolvedBirthTest {

    private static ResolvedBirth sample(
            EnumSet<BirthMomentFlag> flags, ZoneId statedZone, boolean birthTimeKnown) {
        return new ResolvedBirth(
                LocalDateTime.of(1985, 6, 15, 14, 30),
                ZoneId.of("Asia/Kolkata"),
                Instant.parse("1985-06-15T09:00:00Z"),
                ZoneOffset.ofHoursMinutes(5, 30),
                18.5204,
                73.8567,
                "Pune",
                "India",
                "fixture",
                statedZone,
                birthTimeKnown,
                flags,
                new DatasetVersions("2025b", "2025b.26"));
    }

    @Test
    void birthDataIsExactlyInstantLatLon() {
        BirthData bd = sample(EnumSet.noneOf(BirthMomentFlag.class), null, true).birthData();
        assertThat(bd).isEqualTo(new BirthData(
                Instant.parse("1985-06-15T09:00:00Z"), 18.5204, 73.8567));
    }

    @Test
    void birthTimeKnownMustBeTheNegationOfTheFlag() {
        assertThatThrownBy(() -> sample(EnumSet.of(BirthMomentFlag.TIME_NOT_KNOWN), null, true))
                .isInstanceOf(IllegalArgumentException.class);
        // consistent: flag present + birthTimeKnown false
        assertThat(sample(EnumSet.of(BirthMomentFlag.TIME_NOT_KNOWN), null, false).birthTimeKnown())
                .isFalse();
    }

    @Test
    void statedZoneIsSetIffZoneConflictFlagPresent() {
        assertThatThrownBy(() -> sample(EnumSet.noneOf(BirthMomentFlag.class), ZoneId.of("UTC"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sample(EnumSet.of(BirthMomentFlag.ZONE_CONFLICT), null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flagsAreDefensivelyCopied() {
        EnumSet<BirthMomentFlag> flags = EnumSet.of(BirthMomentFlag.POLAR_LATITUDE);
        ResolvedBirth rb = sample(flags, null, true);
        flags.add(BirthMomentFlag.DST_GAP);
        assertThat(rb.flags()).containsExactly(BirthMomentFlag.POLAR_LATITUDE);
        rb.flags().clear();
        assertThat(rb.flags()).containsExactly(BirthMomentFlag.POLAR_LATITUDE);
    }
}
