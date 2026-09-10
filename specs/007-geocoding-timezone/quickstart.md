# Quickstart: verifying SPEC-007

First implementation of the `geo` module. Depends only on SPEC-001 (the
`BirthData` type). Branch `007-geocoding-timezone`.

## Prerequisites

- JDK 25, `./mvnw`.
- `net.iakovlev:timeshape` resolves from Maven Central (the ~50 MB data jar is a
  transitive artifact — first build downloads it).
- Birth-moment corpus generated / verified by the independent tool:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt   # now also pulls timezonefinder
  python compute_golden.py --birthmoments ../../geo/src/test/resources/birthmoments/corpus.json --write
  ```

  (`--birthmoments` recomputes each entry with Python `zoneinfo` — the system
  IANA `tzdata`, independent of the JRE's bundled `tzdb` — and `timezonefinder`
  — a different polygon dataset from `timeshape`. `--write` fills `expected`;
  without it, verify only, non-zero exit on drift.)

## Build & test

```bash
./mvnw -q -pl ephemeris,geo -am verify
```

New coverage:

- `geo.place` — `QueryNormalizer` (trim / collapse / case-fold / alias map,
  idempotent), `CoordinateQuery` ("lat, lon" → direct-input candidate),
  `CandidateRanking` (total order), `CachingGeocoder` (hit ⇒ equal result, no
  delegate call), `FixtureGeocoder`.
- `geo.time` — `TimeshapeTimeZoneResolver` (16-city sample; ocean `Etc/GMT`
  tiles flagged `ZONE_APPROXIMATED`), `LocalToUtc` (normal / DST gap / DST fold /
  unknown-time), `tzdb` version read.
- `geo` — `BirthMomentResolver` (composition, all six flags), `ResolvedBirth
  .birthData()`.
- birth-moment corpus — `@TestFactory`, every entry's instant to the second +
  zone, cross-checked against the tool's `expected`.
- determinism — equal inputs ⇒ equal `ResolvedBirth`.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| foundational value types (`ZoneResolution` / `InstantResolution` / `DatasetVersions` validation) | `geo.FoundationalTypesTest` |
| US1 normal / gap / fold / unknown-time (SC-001, SC-004) | `geo.time.LocalToUtcTest`, `geo.BirthMomentCorpusTest` |
| US1 historical offsets — pre-1970, a wartime deviation (SC-001) | `geo.time.LocalToUtcTest`, `geo.BirthMomentCorpusTest` (`kolkata-1943`, `london-1944`) |
| US2 normalisation + alias map | `geo.place.QueryNormalizerTest`, `QueryNormalizerPropertyTest` (jqwik) |
| US2 coordinate query | `geo.place.CoordinateQueryTest` |
| US2 ranking, never collapsed to one (SC-005 ordering) | `geo.place.CandidateRankingTest`, `CandidateRankingPropertyTest` |
| US2 cache hit ⇒ identical, no provider call (SC-003) | `geo.place.CachingGeocoderTest` (counting fake delegate) |
| US2 empty match | `geo.place.FixtureGeocoderTest` |
| US3 resolve → `birthData()` feeds SPEC-001 (SC-001) | `geo.BirthMomentResolverTest`, `BirthMomentCorpusTest` |
| US3 dataset versions recorded (FR-016) | `geo.BirthMomentResolverTest` |
| US4 invalid lat/lon rejected (FR-013) | `geo.place.PlaceCandidateTest` |
| US4 polar latitude flagged (FR-014, ADR-0004) | `geo.BirthMomentResolverTest` |
| US4 stated ≠ geocoded zone → conflict (FR-015) | `geo.BirthMomentResolverTest` |
| US1 lat/lon → zone vs reference (SC-002) | `geo.time.TimeZoneResolverGoldenTest` (16 cities) |
| ocean tile → `ZONE_APPROXIMATED` + `Etc/GMT` id | `geo.time.TimeZoneResolverGoldenTest`, corpus `ocean-pacific` |
| US5 determinism | `geo.GeoDeterminismTest` |
| SC-005 performance (< 20 ms warm) | `geo.BirthMomentResolverPerformanceTest` — `@Tag("perf")` |
| SC-006 3 births vs an independent source | `verification` note in `corpus.json` |

## Determinism / architecture gates

- `DeterminismArchitectureTest` already covers `com.celestia.geo..` — no wall
  clock. `LocalToUtc` / `BirthMomentResolver` take every date / time / zone
  explicitly; `TimeshapeTimeZoneResolver`'s engine build reads only bundled
  classpath data.
- **New rule** in `DeterminismArchitectureTest` — no `net.iakovlev` type in a
  public member of `com.celestia.geo` (mirrors the Swiss Ephemeris rule);
  `timeshape` stays behind `TimeZoneResolver`.
- `LayeringArchitectureTest` already forbids `com.celestia.geo..` → Spring / edge
  adapters. The `geo → ephemeris` dependency is inward (allowed).

## Interpreting a failure

- **instant off by a whole hour** → a DST transition mis-detected: check
  `getValidOffsets` size branching in `LocalToUtc`, or the corpus row's
  `stated_zone` vs the geocoded zone.
- **instant off by minutes** → a pre-1970 / wartime row: the JRE `tzdb` and the
  tool's `tzdata` disagree on a historical rule. Check `corpus.json`'s
  `generated_by` vs `LocalToUtc.currentTzdbVersion()`; regenerate the corpus and
  review the diff.
- **zone id mismatch** → `timeshape` vs `timezonefinder` disagree at a border;
  pick a city well inside the zone, or accept the border ambiguity and pin the
  expected id with a note.
- **cache test: delegate called on a hit** → `CachingGeocoder` is keying on the
  raw query, not `PlaceQuery.normalized()`.
- **ranking non-deterministic** → the sort is not stable, or the `id` tie-break
  is missing.
