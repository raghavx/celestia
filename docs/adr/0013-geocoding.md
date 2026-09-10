# ADR 0013: Geocoding of birth place

- **Status:** accepted
- **Date:** 2026-09-02
- **Accepted:** 2026-09-10 (SPEC-007 `/speckit-plan`)
- **Decision ref:** D13 (PROJECT_PLAN.md §2)

## Context

KP cusps need accurate coordinates. Users give free-text places ("Pune",
"Bandra, Bombay"). We need place → lat/lon with disambiguation, and the result
also feeds the timezone lookup (ADR-0014).

## Options considered

1. Self-host **Nominatim** (OSM data) — no per-call cost or limits; ~30 GB planet
   database plus hardware, or a regional extract.
2. Self-host **Photon** (search-optimised, built on Nominatim data) — better fuzzy
   search; similar footprint.
3. **Hosted API** (OpenCage, LocationIQ, Google) — trivial to operate; per-call
   cost; ToS and caching constraints; an external dependency.

## Decision

Option 3 (**OpenCage**) for v1 speed-to-market, behind a `Geocoder` interface in
`geo`, with every result cached in `geocode_cache` so repeat places cost nothing
and stay deterministic. Migrate to self-hosted Photon if call volume or ToS
becomes a problem.

## Consequences

- An API key per environment (env var, not committed).
- The cache layer is required, not optional.
- Disambiguation UX (WhatsApp interactive list) is part of SPEC-010.
- Historical or colloquial place names may need a small manual alias map.
- **Adapter timing (SPEC-007 `/speckit-plan`, 2026-09-10):** the `Geocoder`
  *port*, the value objects, query normalisation + ranking, the `GeocodeCache`
  port + an in-memory cache, and a bundled-data `FixtureGeocoder` are delivered
  in SPEC-007's pure `geo` module. The **OpenCage HTTP adapter itself is
  deferred to its consumer** (SPEC-009 channel / SPEC-010 onboarding) — it is
  edge I/O with an API key and live-HTTP contract tests, Constitution IX keeps
  it out of `geo`, and nothing calls `geocodePlace` until the WhatsApp channel
  and the onboarding FSM exist. Until then the `FixtureGeocoder` is the only
  binding and is sufficient for SPEC-008's Phase 3 exit.
