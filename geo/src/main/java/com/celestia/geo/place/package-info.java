/**
 * Place name to ranked candidates (SPEC-007 User Story 2).
 *
 * <p>{@link com.celestia.geo.place.Geocoder} is a port; the HTTP implementation
 * (OpenCage, ADR-0013) is an adapter <b>outside</b> {@code geo} that lands with
 * its consumer (SPEC-009/010). This package ships the port, the value objects,
 * {@link com.celestia.geo.place.QueryNormalizer}, {@link com.celestia.geo.place.CandidateRanking},
 * {@link com.celestia.geo.place.CoordinateQuery}, a {@link com.celestia.geo.place.GeocodeCache}
 * with an in-memory implementation, {@link com.celestia.geo.place.CachingGeocoder},
 * and {@link com.celestia.geo.place.FixtureGeocoder}.
 *
 * <p>Pure — no network, no provider SDK / type.
 */
package com.celestia.geo.place;
