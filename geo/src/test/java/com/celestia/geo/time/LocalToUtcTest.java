package com.celestia.geo.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.geo.BirthMomentFlag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LocalToUtcTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void normalDateHasNoFlagsAndTheRightOffset() {
        InstantResolution ir =
                LocalToUtc.resolve(LocalDate.of(1985, 6, 15), LocalTime.of(14, 30), KOLKATA);

        assertThat(ir.instant()).isEqualTo(Instant.parse("1985-06-15T09:00:00Z"));
        assertThat(ir.offsetApplied()).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
        assertThat(ir.flags()).isEmpty();
        assertThat(ir.tzdbVersion()).isNotBlank();
    }

    @Test
    void historicalWartimeOffsetIsApplied() {
        // British Double Summer Time, +02:00.
        InstantResolution ir = LocalToUtc.resolve(
                LocalDate.of(1944, 6, 1), LocalTime.NOON, ZoneId.of("Europe/London"));
        assertThat(ir.offsetApplied()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(ir.instant()).isEqualTo(Instant.parse("1944-06-01T10:00:00Z"));
    }

    @Test
    void springForwardGapIsFlaggedAndShiftedForward() {
        // 2021-03-14 02:30 does not exist in New York (02:00 -> 03:00).
        InstantResolution ir =
                LocalToUtc.resolve(LocalDate.of(2021, 3, 14), LocalTime.of(2, 30), NEW_YORK);

        assertThat(ir.flags()).containsExactly(BirthMomentFlag.DST_GAP);
        assertThat(ir.offsetApplied()).isEqualTo(ZoneOffset.ofHours(-4)); // the later offset
        assertThat(ir.instant()).isEqualTo(Instant.parse("2021-03-14T07:30:00Z"));
    }

    @Test
    void fallBackFoldIsFlaggedAndUsesTheEarlierOffset() {
        // 2021-10-31 02:30 occurs twice in Berlin (03:00 -> 02:00).
        InstantResolution ir =
                LocalToUtc.resolve(LocalDate.of(2021, 10, 31), LocalTime.of(2, 30), BERLIN);

        assertThat(ir.flags()).containsExactly(BirthMomentFlag.DST_FOLD);
        assertThat(ir.offsetApplied()).isEqualTo(ZoneOffset.ofHours(2)); // CEST, the earlier one
        assertThat(ir.instant()).isEqualTo(Instant.parse("2021-10-31T00:30:00Z"));
    }

    @Test
    void unknownTimeUsesLocalNoonAndIsFlagged() {
        InstantResolution ir = LocalToUtc.resolve(LocalDate.of(1970, 2, 20), null, KOLKATA);

        assertThat(ir.flags()).containsExactly(BirthMomentFlag.TIME_NOT_KNOWN);
        assertThat(ir.instant()).isEqualTo(Instant.parse("1970-02-20T06:30:00Z"));
    }

    @Test
    void rejectsNullDateOrZone() {
        assertThatThrownBy(() -> LocalToUtc.resolve(null, LocalTime.NOON, KOLKATA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalToUtc.resolve(LocalDate.of(2000, 1, 1), LocalTime.NOON, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tzdbVersionLooksLikeAnIanaRelease() {
        assertThat(LocalToUtc.currentTzdbVersion()).matches("\\d{4}[a-z]");
    }
}
