package com.celestia.geo.place;

/**
 * Place query to a ranked candidate list.
 *
 * <p>Contract (see {@code specs/007-geocoding-timezone/contracts/geo-api.md}):
 *
 * <ul>
 *   <li>Never returns {@code null}; a query with no match yields a
 *       {@link GeocodeResult} with an empty candidate list, not an exception.</li>
 *   <li>Deterministic for a given backing data set.</li>
 *   <li>The HTTP implementation ({@code OpenCageGeocoder}, SPEC-009/010) lives
 *       <b>outside</b> the {@code geo} module — no provider SDK / type in this
 *       package (Constitution IX). {@code geo} ships {@link FixtureGeocoder} and
 *       {@link CachingGeocoder}.</li>
 * </ul>
 */
public interface Geocoder {

    GeocodeResult geocode(PlaceQuery query);
}
