package com.celestia.geo.place;

/**
 * One geocoder result — enough to disambiguate and to cast a chart.
 *
 * @param id stable id for this place + source; a ranking tie-break and a
 *     cache-safe reference (SPEC-010 echoes it back when the user picks)
 * @param displayName human-readable label, e.g. {@code "Pune, Maharashtra, India"}
 * @param country country name or ISO code; may be {@code ""}
 * @param adminRegion state / province; may be {@code ""}
 * @param latitude decimal degrees, + = North; {@code |latitude| <= 90}
 * @param longitude decimal degrees, + = East; {@code |longitude| <= 180}
 * @param source the geocoder that produced it — {@code "opencage"}, {@code "fixture"},
 *     {@code "direct-input"}
 */
public record PlaceCandidate(
        String id,
        String displayName,
        String country,
        String adminRegion,
        double latitude,
        double longitude,
        String source) {

    public PlaceCandidate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (!(Math.abs(latitude) <= 90.0)) {
            throw new IllegalArgumentException("latitude out of [-90, 90]: " + latitude);
        }
        if (!(Math.abs(longitude) <= 180.0)) {
            throw new IllegalArgumentException("longitude out of [-180, 180]: " + longitude);
        }
        country = country == null ? "" : country;
        adminRegion = adminRegion == null ? "" : adminRegion;
    }
}
