package com.celestia.geo.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

/**
 * SC-002 — a sample of cities worldwide resolves to the reference tz database
 * assignment. The expected ids are hand-listed from the tz database and match the
 * independent {@code timezonefinder} check in {@code compute_golden.py}.
 */
class TimeZoneResolverGoldenTest {

    private static final TimeZoneResolver RESOLVER = new TimeshapeTimeZoneResolver();

    private record City(String name, double lat, double lon, String zone) {}

    private static final List<City> CITIES = List.of(
            new City("Pune", 18.5204, 73.8567, "Asia/Kolkata"),
            new City("Kolkata", 22.5726, 88.3639, "Asia/Kolkata"),
            new City("Karachi", 24.8607, 67.0011, "Asia/Karachi"),
            new City("Tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
            new City("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
            new City("Santiago", -33.4489, -70.6693, "America/Santiago"),
            new City("New York", 40.7128, -74.006, "America/New_York"),
            new City("Chicago", 41.8781, -87.6298, "America/Chicago"),
            new City("London", 51.5074, -0.1278, "Europe/London"),
            new City("Berlin", 52.52, 13.405, "Europe/Berlin"),
            new City("Oslo (Tromso)", 69.6492, 18.9553, "Europe/Oslo"),
            new City("Sao Paulo", -23.5505, -46.6333, "America/Sao_Paulo"),
            new City("Cairo", 30.0444, 31.2357, "Africa/Cairo"),
            new City("Nairobi", -1.2921, 36.8219, "Africa/Nairobi"),
            new City("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"),
            new City("Auckland", -36.8485, 174.7633, "Pacific/Auckland"));

    @TestFactory
    List<DynamicTest> citiesResolveToTheReferenceZone() {
        return CITIES.stream()
                .map(c -> DynamicTest.dynamicTest(c.name(), () -> {
                    ZoneResolution zr = RESOLVER.resolve(c.lat(), c.lon());
                    assertThat(zr.approximated()).as("%s approximated", c.name()).isFalse();
                    assertThat(zr.zone()).isEqualTo(ZoneId.of(c.zone()));
                    assertThat(zr.datasetVersion()).isNotBlank();
                }))
                .toList();
    }

    @Test
    void openOceanFallsBackToAnEtcGmtZoneAndIsFlaggedApproximated() {
        ZoneResolution zr = RESOLVER.resolve(0.0, -150.0);
        assertThat(zr.approximated()).isTrue();
        assertThat(zr.zone()).isEqualTo(ZoneId.of("Etc/GMT+10")); // round(-150/15) = -10
    }

    @Test
    void etcGmtSignIsInverted() {
        assertThat(TimeshapeTimeZoneResolver.etcGmtFor(75.0)).isEqualTo(ZoneId.of("Etc/GMT-5"));
        assertThat(TimeshapeTimeZoneResolver.etcGmtFor(-75.0)).isEqualTo(ZoneId.of("Etc/GMT+5"));
        assertThat(TimeshapeTimeZoneResolver.etcGmtFor(3.0)).isEqualTo(ZoneId.of("Etc/GMT"));
    }

    @Test
    void datasetVersionMatchesThePinnedArtifact() {
        assertThat(TimeshapeTimeZoneResolver.DATASET_VERSION).matches("\\d{4}[a-z]\\.\\d+");
    }
}
