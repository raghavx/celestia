/**
 * Coordinate to IANA zone, and local civil time to UTC (SPEC-007 User Story 1).
 *
 * <p>{@link com.celestia.geo.time.TimeshapeTimeZoneResolver} keeps the
 * {@code timeshape} library — embedded, offline polygon data (ADR-0014) — behind
 * {@link com.celestia.geo.time.TimeZoneResolver}; no {@code net.iakovlev} type
 * crosses a public boundary (ArchUnit-enforced).
 * {@link com.celestia.geo.time.LocalToUtc} uses {@code java.time}'s historical
 * rules and flags DST gaps, folds, and unknown times.
 *
 * <p>Pure — every date, time, and zone is an explicit argument; no wall clock.
 */
package com.celestia.geo.time;
