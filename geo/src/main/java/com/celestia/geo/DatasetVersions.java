package com.celestia.geo;

/**
 * The versions of the two external data sets a resolved birth depends on, so a
 * later change to either is detectable (FR-016).
 *
 * @param tzdbVersion the IANA {@code tzdb} release the JRE's {@code java.time}
 *     rules came from (e.g. {@code "2025b"})
 * @param timezoneBoundaryVersion the embedded timezone-boundary dataset version
 *     (the {@code timeshape} artifact version, e.g. {@code "2025b.26"})
 */
public record DatasetVersions(String tzdbVersion, String timezoneBoundaryVersion) {

    public DatasetVersions {
        if (tzdbVersion == null || tzdbVersion.isBlank()) {
            throw new IllegalArgumentException("tzdbVersion must not be blank");
        }
        if (timezoneBoundaryVersion == null || timezoneBoundaryVersion.isBlank()) {
            throw new IllegalArgumentException("timezoneBoundaryVersion must not be blank");
        }
    }
}
