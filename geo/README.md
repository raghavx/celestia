# Celestia Geo

Place name → coordinates, coordinates → IANA time zone, and a local civil birth
time → the exact UTC instant a chart needs. Pure — no Spring, no database, no
network, no wall clock (Constitution II). SPEC-007.

## What it does

| Capability | Entry point | Notes |
|---|---|---|
| Geocode a place name | `place.Geocoder` (port) → `GeocodeResult` (ranked `PlaceCandidate` list) | `place.QueryNormalizer` (+ alias map), `place.CandidateRanking`, `place.CoordinateQuery` ("18.52, 73.85" → direct candidate), `place.CachingGeocoder` + `place.InMemoryGeocodeCache` |
| lat/lon → IANA zone | `time.TimeZoneResolver` → `time.TimeshapeTimeZoneResolver` | offline `timeshape` polygons (ADR-0014); ocean tiles flagged `ZONE_APPROXIMATED` |
| local time → UTC | `time.LocalToUtc.resolve(date, time, zone)` → `InstantResolution` | `java.time` historical rules; DST gap / fold / unknown-time flagged, never silently guessed |
| compose the above | `BirthMomentResolver.resolve(candidate, date, time, statedZone)` → `ResolvedBirth` | `ResolvedBirth.birthData()` feeds the chart engine (SPEC-001); carries every place-and-time-derived `birth_data` field + the flags + the dataset versions |

## The geocoder adapter

`place.Geocoder` is a **port**. The real hosted implementation (OpenCage,
ADR-0013) is an HTTP adapter that lives **outside** this module and lands with
its consumer (SPEC-009 channel / SPEC-010 onboarding). Until then
`place.FixtureGeocoder` — backed by `src/test/resources/geocode/fixtures.json` —
is the only binding, and it is enough for SPEC-008's persistence work.

## Flags (`BirthMomentFlag`)

`TIME_NOT_KNOWN`, `DST_GAP`, `DST_FOLD`, `ZONE_APPROXIMATED`, `POLAR_LATITUDE`,
`ZONE_CONFLICT`. `geo` never blocks on a flag — it resolves and records; the
onboarding FSM decides whether a flag needs a user confirmation before persisting.

## Rules & verification

- Every non-obvious rule is cited in [`src/main/java/com/celestia/geo/REFERENCES.md`](src/main/java/com/celestia/geo/REFERENCES.md).
- The birth-moment corpus (`src/test/resources/birthmoments/corpus.json`) is
  cross-checked by an independent tool. Regenerate:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  python compute_golden.py --birthmoments \
    ../../geo/src/test/resources/birthmoments/corpus.json --write
  ```

- `./mvnw -pl geo -am test` runs the module. `-Pperf` adds
  `BirthMomentResolverPerformanceTest`.

## Dependencies

`com.celestia:ephemeris` (the `BirthData` type only) and `net.iakovlev:timeshape`
(offline timezone polygons, ADR-0014). Both inward / ADR-covered.
