package com.celestia.geo.time;

import java.time.ZoneId;

/**
 * The outcome of resolving a coordinate to an IANA time zone.
 *
 * @param zone the IANA zone id
 * @param approximated {@code true} when no timezone polygon contained the point
 *     and {@code zone} is a longitude-derived {@code Etc/GMT} fallback
 *     (research.md §2)
 * @param datasetVersion the embedded timezone-boundary dataset version
 */
public record ZoneResolution(ZoneId zone, boolean approximated, String datasetVersion) {

    public ZoneResolution {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
    }
}
