# Implementation Plan: Geocoding & Timezone (SPEC-007)

**Branch**: `007-geocoding-timezone` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-geocoding-timezone/spec.md`

## Summary

The first build-out of the `geo` module — pure, deterministic, offline. Three
capabilities plus the record that composes them:

- **`lat/lon → IANA zone`** — `TimeZoneResolver`, backed by `timeshape`'s embedded
  polygon data (ADR-0014). Offline classpath data, the same treatment as the
  `.se1` ephemeris files. A coordinate with no polygon falls back to a
  longitude-derived `Etc/GMT` zone plus a "zone approximated" flag.
- **`local date/time + zone → UTC instant`** — `LocalToUtc`, `java.time`'s
  historical rules. A DST **gap** or **fold**, or an unknown birth time, gets a
  documented resolution **and** a flag — never a silent guess. The offset applied
  and the `tzdb` version are returned.
- **`place name → ranked candidates`** — a `Geocoder` **port** in `geo` with
  `PlaceQuery` / `PlaceCandidate` value objects, deterministic query
  normalisation + candidate ranking, an in-memory `GeocodeCache`, and a
  `CachingGeocoder` decorator. `geo` ships a `FixtureGeocoder` (bundled data, no
  network) for tests and local dev. **The OpenCage HTTP adapter is deferred to
  its consumer** (SPEC-009/010) — it is edge I/O with an API key and live-HTTP
  contract tests, and nothing calls `geocodePlace` until the WhatsApp channel and
  the onboarding FSM exist. ADR-0013 is moved *proposed → accepted* by this plan.
- **`BirthMomentResolver`** — composes a chosen candidate + a local date/time
  (+ an optional user-stated zone) into a `ResolvedBirth` carrying every field
  `birth_data` persists, with a `birthData()` accessor for the chart pipeline.

No Spring, no database, no network, no clock. The `geo` module gains two
dependencies: internal `ephemeris` (for the `BirthData` type) and `timeshape`
(named in ADR-0014).

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: **new** — `net.iakovlev:timeshape` (embedded IANA
timezone polygons, offline, deterministic; ADR-0014). **New internal** — `geo`
now depends on `ephemeris` (the `BirthData` record only). Test:
JUnit 5 / AssertJ (present) + jackson-databind (corpus + fixture loading, test
scope) + jqwik (normalisation / ranking properties).

**Storage**: N/A — immutable value objects + an in-memory `GeocodeCache`. The
`geocode_cache` and `birth_data` tables are SPEC-008.

**Testing**: `./mvnw verify`. New: a checked-in **birth-moment corpus**
(`geo/src/test/resources/birthmoments/corpus.json`) — ≥ 12 births spanning
pre-1970 offsets, a historical standard-offset change, DST gap and fold, and both
hemispheres — recomputed independently by
`tools/ephe-crosscheck/compute_golden.py` using Python `zoneinfo` (system IANA
`tzdata`, independent of the JRE's bundled copy) and `timezonefinder` (a
different polygon dataset from `timeshape`). Because the independent tool uses a
different implementation **and** different data, this is a real cross-check;
SC-006 adds a human check of 3 births against an outside source.

**Target Platform**: JVM library. Deterministic across Linux/macOS, x86-64/arm64.

**Project Type**: Library — the `geo` module, first implementation. New packages
`com.celestia.geo` (root: `ResolvedBirth`, `BirthMomentResolver`, flags),
`com.celestia.geo.place` (geocoding port + value objects + cache), and
`com.celestia.geo.time` (zone resolution + local→UTC).

**Performance Goals**: SC-005 — resolve a birth moment from a cached candidate in
< 20 ms warm (a `timeshape` polygon query + a `java.time` conversion, both
sub-millisecond once warm). `timeshape`'s one-time index build (~1–3 s, ~50 MB)
happens behind a lazily-initialised singleton and is measured only under
`-Pperf`.

**Constraints**:
- Constitution II — `geo` is pure: no Spring, no DB, no network, **no wall
  clock**. `timeshape` reads only its own bundled classpath resources — the same
  category as `ephemeris` reading `.se1` files. `LocalToUtc` takes every date /
  time / zone explicitly; `DeterminismArchitectureTest` already covers
  `com.celestia.geo..`.
- Constitution IX — the `Geocoder` port lives in `geo`; no OpenCage SDK / HTTP
  type ever enters `geo` (FR-011). A new ArchUnit rule keeps `net.iakovlev`
  types out of the `geo` public API (mirrors the Swiss Ephemeris rule).
- FR-016 — the `timeshape` dataset version and the `tzdb` version are recorded on
  every `ResolvedBirth`.
- Constitution III discipline — every non-obvious rule (gap/fold resolution,
  no-polygon fallback, unknown-time convention, ranking, alias map) is documented
  with a citation in a new `geo/src/main/java/com/celestia/geo/REFERENCES.md`.

**Scale/Scope**: one birth per resolve; a few hundred cached queries in memory;
the corpus is ≥ 12 births + ≥ 15 cities for the zone sample; ~9 matters is not
relevant here.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code; spec + `research.md` are the design doc |
| II Deterministic pure core | **yes, central** | PASS — `geo` stays pure; `timeshape` is offline classpath data (like `.se1`); `LocalToUtc` reads no clock; `DeterminismArchitectureTest` / `LayeringArchitectureTest` already list `com.celestia.geo..` |
| III Calculation Correctness Is Gated | **yes, central** | PASS — a birth-moment corpus joins CI, cross-checked by an independent tool (Python `zoneinfo` + `timezonefinder`); every rule cited in `geo/REFERENCES.md`; SC-006 adds a human check |
| IV LLM interprets, never calculates | partial | PASS — `geocodePlace` returns a structured candidate list; the FSM / LLM never computes an offset or picks a candidate |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | partial | PASS — disambiguation is a decision the *caller* (SPEC-010 FSM) makes; `geo` returns the ranked list and, given a pick, the resolved birth. No LLM in the resolve path |
| VII Identity / consent / PII | partial | PASS — birth place/time is personal data, but `geo` only computes; encryption, persistence, and "forget me" are SPEC-008 / SPEC-010 |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | **yes, central** | PASS — `Geocoder` port in `geo`; the OpenCage HTTP adapter is **outside** `geo` and deferred to its consumer; no provider type in `geo` (FR-011, new ArchUnit rule) |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money; no DB (SPEC-008). New dep `timeshape` is named in ADR-0014 |
| ADR gate (D1–D23) | **yes** | PASS — ADR-0014 (D14) accepted. **This plan moves ADR-0013 (D13) `proposed → accepted`** (dated 2026-09-10), recording that the HTTP adapter lands with SPEC-009/010. No new ADR needed |

**Result: PASS.** One internal + one external dependency, both ADR-covered; no
new module; one ADR promoted to accepted.

## Project Structure

### Documentation (this feature)

```text
specs/007-geocoding-timezone/
├── plan.md
├── research.md          # the geocoder scope boundary; timeshape; the DST gap/fold + unknown-time rules; the no-polygon fallback; the ranking rule; the corpus + independent cross-check
├── data-model.md        # PlaceQuery, PlaceCandidate, GeocodeResult, Geocoder, GeocodeCache, ZoneResolution, InstantResolution, ResolvedBirth, BirthMomentFlag, invariants
├── contracts/
│   └── geo-api.md        # geo: Geocoder / GeocodeCache / TimeZoneResolver / LocalToUtc / BirthMomentResolver
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
geo/src/main/java/com/celestia/geo/
├── ResolvedBirth.java            # record: local datetime, zoneId, instant, lat, lon, label, country, geocodeSource, offsetApplied, EnumSet<BirthMomentFlag>, DatasetVersions; birthData() -> ephemeris.BirthData
├── BirthMomentFlag.java          # enum: TIME_NOT_KNOWN, DST_GAP, DST_FOLD, ZONE_APPROXIMATED, POLAR_LATITUDE, ZONE_CONFLICT
├── DatasetVersions.java          # record: String tzdbVersion, String timezoneBoundaryVersion
├── BirthMomentResolver.java      # ctor(TimeZoneResolver, LocalToUtc); resolve(PlaceCandidate, LocalDate, LocalTime|null, ZoneId statedZone|null) -> ResolvedBirth
├── REFERENCES.md                 # the alias map; ranking; gap/fold resolution; no-polygon fallback; unknown-time = noon; polar threshold (ADR-0004); dataset versioning
├── package-info.java             # updated
├── place/
│   ├── PlaceQuery.java           # record(String raw, String normalized); static of(String)
│   ├── PlaceCandidate.java       # record(String id, String displayName, String country, String adminRegion, double latitude, double longitude, String source)
│   ├── GeocodeResult.java        # record(PlaceQuery query, List<PlaceCandidate> candidates) — ranked, immutable
│   ├── Geocoder.java             # interface: GeocodeResult geocode(PlaceQuery)
│   ├── GeocodeCache.java         # interface: Optional<GeocodeResult> get(String queryNorm); void put(String, GeocodeResult)
│   ├── InMemoryGeocodeCache.java # ConcurrentHashMap-backed
│   ├── CachingGeocoder.java      # decorator(Geocoder delegate, GeocodeCache cache)
│   ├── QueryNormalizer.java      # static normalize(String): trim, collapse whitespace, case-fold, apply the alias map
│   ├── CoordinateQuery.java      # static Optional<PlaceCandidate> parse(String)  — "18.52, 73.85"
│   ├── CandidateRanking.java     # static List<PlaceCandidate> rank(PlaceQuery, List<PlaceCandidate>) — deterministic
│   └── FixtureGeocoder.java      # Geocoder over a Map<String,List<PlaceCandidate>> — no network; dev + tests
└── time/
    ├── TimeZoneResolver.java     # interface: ZoneResolution resolve(double lat, double lon)
    ├── ZoneResolution.java       # record(ZoneId zone, boolean approximated, String datasetVersion)
    ├── TimeshapeTimeZoneResolver.java  # net.iakovlev.timeshape behind the port; lazy singleton engine; Etc/GMT fallback
    ├── LocalToUtc.java           # static resolve(LocalDate, LocalTime, ZoneId) -> InstantResolution
    └── InstantResolution.java    # record(Instant instant, ZoneOffset offsetApplied, EnumSet<BirthMomentFlag> flags, String tzdbVersion)

geo/src/test/java/com/celestia/geo/...   # unit + property + corpus + determinism tests, FixtureGeocoderLoader
geo/src/test/resources/birthmoments/corpus.json     # the ≥12-birth corpus (expected instants filled by the tool)
geo/src/test/resources/geocode/fixtures.json        # FixtureGeocoder data
geo/pom.xml                                          # + ephemeris, + timeshape, + test deps
pom.xml                                              # + timeshape.version property & dependencyManagement entry
tools/ephe-crosscheck/compute_golden.py             # + --birthmoments mode (zoneinfo + timezonefinder); header -> kp-crosscheck 0.7
tools/ephe-crosscheck/requirements.txt              # + timezonefinder
agent/src/test/java/com/celestia/agent/architecture/DeterminismArchitectureTest.java   # + net.iakovlev must not leak from geo public API
.github/workflows/ci.yml                            # + BirthMomentCorpusTest, TimeZoneResolverGoldenTest, GeoDeterminismTest
docs/adr/0013-geocoding.md                          # proposed -> accepted (this plan)
```

**Structure Decision**: no new module — the `geo` module already exists as a
placeholder. Everything lands as three packages under `com.celestia.geo`.
`BirthMomentResolver` takes only pure collaborators (`TimeZoneResolver`,
`LocalToUtc`); it needs no provider and no clock. `ResolvedBirth` is a distinct
type from `BirthData` (it carries the zone, the local time, the label, and the
flags) but exposes `birthData()` so the chart pipeline consumes it directly —
hence the new `geo → ephemeris` dependency (inward, allowed).

## Phase 0 — Research

See [research.md](./research.md). Items:

1. **The geocoder scope boundary** — why `geo` ships the `Geocoder` port + a
   `FixtureGeocoder` + caching now, and the OpenCage HTTP adapter is deferred to
   SPEC-009/010 (edge I/O, API key, live contract tests, no consumer yet).
   ADR-0013 `proposed → accepted`.
2. **`lat/lon → ZoneId`** — `timeshape` API and version pinning; the lazy
   singleton engine; the no-polygon fallback (`Etc/GMT±h` from `round(lon/15)`)
   and its flag.
3. **`local → UTC`** — `java.time` `ZoneRules.getValidOffsets` / `getTransition`
   to *detect* a gap (0 offsets) or fold (2 offsets); the resolution rules
   (gap → shift forward by the transition duration, the `atZone` default;
   fold → the earlier offset, `withEarlierOffsetAtOverlap`); reading the `tzdb`
   version (`ZoneRulesProvider.getVersions(id).lastKey()`).
4. **Unknown birth time** — the convention (12:00 local) + `TIME_NOT_KNOWN`; the
   downstream caveat is the agent's to surface.
5. **Query normalisation + the alias map** — trim / collapse / case-fold; a small
   curated exonym→endonym map (Bombay→Mumbai, Calcutta→Kolkata, Poona→Pune,
   Madras→Chennai, Bangalore→Bengaluru, Peking→Beijing, …) with a source note.
6. **Candidate ranking** — a deterministic total order given the provider's
   candidates (exact normalised-name match first, then provider order, then a
   stable tie-break on id); coordinate-only queries bypass it.
7. **Coordinate + zone guardrails** — invalid lat/lon (reject, `BirthData`
   already enforces); the polar threshold (align with SPEC-002's constant, cite
   ADR-0004) → `POLAR_LATITUDE` flag; a stated zone ≠ geocoded zone →
   `ZONE_CONFLICT` flag, resolver uses the geocoded zone and records both.
8. **The corpus + the independent cross-check** — the ≥ 12 births; extending
   `compute_golden.py` with a `--birthmoments` mode (Python `zoneinfo` +
   `timezonefinder`); SC-006's human check.

**Output**: research.md with the geocoder scope boundary resolved (not a
`[NEEDS CLARIFICATION]` — a documented decision) and ADR-0013 promoted.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — the value objects, the flags, the invariants.
- [contracts/geo-api.md](./contracts/geo-api.md).
- [quickstart.md](./quickstart.md).

### Post-design Constitution re-check

Unchanged — `geo` stays pure; `timeshape` is offline classpath data; no SE type
and no `net.iakovlev` type crosses a public boundary (a new ArchUnit rule pins
the latter); the determinism / layering ArchUnit rules already cover
`com.celestia.geo..`. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty. (The ~50 MB `timeshape` dataset
is a deliberate, ADR-0014-accepted trade for offline determinism — the same
call made for the `.se1` ephemeris data — not an unjustified complexity.)
