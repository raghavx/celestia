package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.geo.time.InstantResolution;
import com.celestia.geo.time.ZoneResolution;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    @Test
    void flagEnumHasEverySpecifiedConstant() {
        assertThat(BirthMomentFlag.values())
                .containsExactlyInAnyOrder(
                        BirthMomentFlag.TIME_NOT_KNOWN,
                        BirthMomentFlag.DST_GAP,
                        BirthMomentFlag.DST_FOLD,
                        BirthMomentFlag.ZONE_APPROXIMATED,
                        BirthMomentFlag.POLAR_LATITUDE,
                        BirthMomentFlag.ZONE_CONFLICT);
    }

    @Test
    void datasetVersionsRejectsBlankFields() {
        assertThatThrownBy(() -> new DatasetVersions("", "2025b.26"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatasetVersions("2025b", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DatasetVersions("2025b", "2025b.26").tzdbVersion()).isEqualTo("2025b");
    }

    @Test
    void zoneResolutionRejectsNullZoneAndBlankVersion() {
        assertThatThrownBy(() -> new ZoneResolution(null, false, "v"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ZoneResolution(ZoneId.of("UTC"), false, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instantResolutionCopiesItsFlagSet() {
        EnumSet<BirthMomentFlag> src = EnumSet.of(BirthMomentFlag.DST_FOLD);
        InstantResolution ir =
                new InstantResolution(Instant.EPOCH, ZoneOffset.ofHours(1), src, "2025b");
        src.add(BirthMomentFlag.DST_GAP);
        assertThat(ir.flags()).containsExactly(BirthMomentFlag.DST_FOLD);
        ir.flags().add(BirthMomentFlag.POLAR_LATITUDE);
        assertThat(ir.flags()).containsExactly(BirthMomentFlag.DST_FOLD);
    }
}
