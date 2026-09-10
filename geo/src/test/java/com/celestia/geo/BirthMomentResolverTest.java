package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.time.TimeZoneResolver;
import com.celestia.geo.time.ZoneResolution;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BirthMomentResolverTest {

    /** A {@link TimeZoneResolver} that returns a fixed zone (optionally "approximated"). */
    private static TimeZoneResolver fixedZone(String zoneId, boolean approximated) {
        return (lat, lon) -> new ZoneResolution(ZoneId.of(zoneId), approximated, "test-ds-1");
    }

    private static PlaceCandidate candidate(double lat, double lon) {
        return new PlaceCandidate("id", "Pune", "India", "Maharashtra", lat, lon, "fixture");
    }

    @Test
    void composesZoneAndInstantAndCarriesEveryPersistedField() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Asia/Kolkata", false));

        ResolvedBirth rb = resolver.resolve(
                candidate(18.5204, 73.8567), LocalDate.of(1985, 6, 15), LocalTime.of(14, 30), null);

        assertThat(rb.zoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(rb.instant()).isEqualTo(Instant.parse("1985-06-15T09:00:00Z"));
        assertThat(rb.latitude()).isEqualTo(18.5204);
        assertThat(rb.longitude()).isEqualTo(73.8567);
        assertThat(rb.placeLabel()).isEqualTo("Pune");
        assertThat(rb.country()).isEqualTo("India");
        assertThat(rb.geocodeSource()).isEqualTo("fixture");
        assertThat(rb.birthTimeKnown()).isTrue();
        assertThat(rb.flags()).isEmpty();
        assertThat(rb.statedZoneId()).isNull();
        assertThat(rb.localDateTime()).isEqualTo("1985-06-15T14:30");
    }

    @Test
    void recordsBothDatasetVersions() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Asia/Kolkata", false));
        ResolvedBirth rb = resolver.resolve(
                candidate(18.52, 73.85), LocalDate.of(2000, 1, 1), LocalTime.NOON, null);

        assertThat(rb.versions().timezoneBoundaryVersion()).isEqualTo("test-ds-1");
        assertThat(rb.versions().tzdbVersion()).isNotBlank();
    }

    @Test
    void unknownTimeYieldsNoonAndTheFlag() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Asia/Kolkata", false));
        ResolvedBirth rb = resolver.resolve(
                candidate(18.52, 73.85), LocalDate.of(1970, 2, 20), null, null);

        assertThat(rb.birthTimeKnown()).isFalse();
        assertThat(rb.flags()).containsExactly(BirthMomentFlag.TIME_NOT_KNOWN);
        assertThat(rb.localDateTime().toLocalTime()).isEqualTo(LocalTime.NOON);
        assertThat(rb.instant()).isEqualTo(Instant.parse("1970-02-20T06:30:00Z"));
    }

    @Test
    void polarLatitudeIsFlaggedButNotRejected() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Europe/Oslo", false));
        ResolvedBirth rb = resolver.resolve(
                candidate(69.6492, 18.9553), LocalDate.of(1980, 6, 1), LocalTime.NOON, null);

        assertThat(rb.flags()).contains(BirthMomentFlag.POLAR_LATITUDE);
        assertThat(rb.instant()).isNotNull();
    }

    @Test
    void aStatedZoneThatDiffersFromTheGeocodedOneIsAConflict_geocodedWins() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Asia/Karachi", false));
        ResolvedBirth rb = resolver.resolve(
                candidate(24.8607, 67.0011),
                LocalDate.of(1990, 3, 15),
                LocalTime.of(8, 0),
                ZoneId.of("Asia/Kolkata"));

        assertThat(rb.flags()).contains(BirthMomentFlag.ZONE_CONFLICT);
        assertThat(rb.zoneId()).isEqualTo(ZoneId.of("Asia/Karachi"));
        assertThat(rb.statedZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(rb.instant()).isEqualTo(Instant.parse("1990-03-15T03:00:00Z"));
    }

    @Test
    void aStatedZoneEqualToTheGeocodedOneRaisesNoConflict() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Asia/Kolkata", false));
        ResolvedBirth rb = resolver.resolve(
                candidate(18.52, 73.85),
                LocalDate.of(2000, 1, 1),
                LocalTime.NOON,
                ZoneId.of("Asia/Kolkata"));

        assertThat(rb.flags()).doesNotContain(BirthMomentFlag.ZONE_CONFLICT);
        assertThat(rb.statedZoneId()).isNull();
    }

    @Test
    void anApproximatedZoneIsFlagged() {
        BirthMomentResolver resolver = new BirthMomentResolver(fixedZone("Etc/GMT+10", true));
        ResolvedBirth rb = resolver.resolve(
                candidate(0.0, -150.0), LocalDate.of(2000, 1, 1), LocalTime.NOON, null);

        assertThat(rb.flags()).contains(BirthMomentFlag.ZONE_APPROXIMATED);
    }
}
