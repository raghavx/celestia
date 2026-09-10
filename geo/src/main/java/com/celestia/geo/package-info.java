/**
 * Geocoding and timezone resolution — pure, deterministic, offline (SPEC-007).
 *
 * <p>Three capabilities and the record that composes them:
 *
 * <ul>
 *   <li>{@link com.celestia.geo.place} — a {@link com.celestia.geo.place.Geocoder}
 *       port, place-query value objects, normalisation, ranking, and an in-memory
 *       cache. The OpenCage HTTP adapter lives outside this module (ADR-0013,
 *       SPEC-009/010); {@link com.celestia.geo.place.FixtureGeocoder} is the
 *       binding shipped here.</li>
 *   <li>{@link com.celestia.geo.time} —
 *       {@link com.celestia.geo.time.TimeZoneResolver} (lat/lon → IANA zone,
 *       offline) and {@link com.celestia.geo.time.LocalToUtc} (local civil time →
 *       UTC with historical rules; DST gap / fold / unknown-time flagged).</li>
 *   <li>{@link com.celestia.geo.BirthMomentResolver} → {@link com.celestia.geo.ResolvedBirth},
 *       whose {@link com.celestia.geo.ResolvedBirth#birthData()} feeds the chart
 *       engine (SPEC-001).</li>
 * </ul>
 *
 * <p>No Spring, no database, no network, no wall clock (Constitution II). Rules
 * are documented in {@code REFERENCES.md}.
 */
package com.celestia.geo;
