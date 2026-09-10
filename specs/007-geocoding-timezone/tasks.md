---
description: "Task list for SPEC-007 — Geocoding & Timezone"
---

# Tasks: Geocoding & Timezone (SPEC-007)

**Input**: Design documents in `specs/007-geocoding-timezone/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 5 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US3
(the whole resolve path; US3 composes US1 + a chosen US2 candidate).

First implementation of the `geo` module. Depends only on SPEC-001's `BirthData`
(new `geo → ephemeris` dependency). New external dependency
`net.iakovlev:timeshape` (ADR-0014). ADR-0013 already moved to *accepted* by the
plan. No new module.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [ ] T001 Parent `pom.xml` — add a `timeshape.version` property (the exact
  current `net.iakovlev:timeshape` release from Maven Central; the string encodes
  the bundled tzdata vintage) and a `dependencyManagement` entry for it. `geo/pom.xml`
  — add compile deps `com.celestia:ephemeris` and `net.iakovlev:timeshape`; add
  test deps `com.fasterxml.jackson.core:jackson-databind` and `net.jqwik:jqwik`;
  drop the `TODO(D13/D14)` comment.
- [ ] T002 Extend `tools/ephe-crosscheck/compute_golden.py` with a
  `--birthmoments PATH` mode (independent of the golden-chart loop, research.md
  §8): for each entry compute lat/lon→zone via `timezonefinder` and local→UTC via
  `zoneinfo` (replicating research.md §3 — `fold` handling, gap forward-shift),
  emit `expected` (`zone_id`, `offset_applied`, `birth_utc`, `flags`,
  `tzdb_version`, `timezone_boundary_version`); `--write` fills, bare verifies
  (non-zero exit on drift). Bump the header to `kp-crosscheck 0.7`; add
  `timezonefinder` to `tools/ephe-crosscheck/requirements.txt`.
- [ ] T003 Author `geo/src/test/resources/birthmoments/corpus.json` — ≥ 12
  entries covering: pre-1970 births in ≥ 3 zones; the India 1955 standard-offset
  change (`Asia/Kolkata`, a 1940s birth); a US spring-forward **gap**; an EU
  fall-back **fold**; a southern-hemisphere DST birth (Australia or Chile); an
  **unknown-time** birth (`birth_time` omitted); a **no-polygon** ocean
  coordinate; a **polar-latitude** birth (|lat| ≥ 66); a **stated-vs-geocoded
  zone conflict**. Run `python compute_golden.py --birthmoments … --write`, then
  re-run without `--write` to confirm determinism.
- [ ] T004 [P] Author `geo/src/test/resources/geocode/fixtures.json` (~20
  normalised query → candidate-list entries: Indian metros incl. the alias
  targets, a couple of ambiguous names e.g. "springfield", "london") and a
  test-scope `geo/src/test/java/com/celestia/geo/place/FixtureGeocoderLoader.java`
  that parses it into `Map<String, List<PlaceCandidate>>`.
- [ ] T005 [P] SC-006: for 3 corpus births (one pre-1970, one DST-boundary, one
  ordinary) check the computed local→UTC instant against an independent source
  (an online tz converter, or a published birth-time record); record the result
  and the source in a `verification` block in `corpus.json`.

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [ ] T006 [P] `BirthMomentFlag` enum (`TIME_NOT_KNOWN`, `DST_GAP`, `DST_FOLD`,
  `ZONE_APPROXIMATED`, `POLAR_LATITUDE`, `ZONE_CONFLICT`) in
  `geo/src/main/java/com/celestia/geo/BirthMomentFlag.java`
- [ ] T007 [P] `DatasetVersions` record (`String tzdbVersion, String
  timezoneBoundaryVersion`; both non-blank) in
  `geo/src/main/java/com/celestia/geo/DatasetVersions.java`
- [ ] T008 [P] `FoundationalTypesTest` in
  `geo/src/test/java/com/celestia/geo/FoundationalTypesTest.java` — the flag enum
  has all six constants; `DatasetVersions` rejects blank fields

**Checkpoint**: `./mvnw -pl geo -am test` green (module now builds with the new deps).

---

## Phase 3: User Story 1 — Local birth time to UTC (P1) 🎯 MVP

**Goal**: coordinates → IANA zone (offline); local date/time + zone → UTC instant
with historical rules; DST gap / fold / unknown-time flagged, never guessed.

**Independent Test**: the birth-moment corpus — every instant to the second, every
zone id, every flag; cross-checked by the independent tool.

### Tests (write first)

- [ ] T009 [P] [US1] `LocalToUtcTest` in
  `geo/src/test/java/com/celestia/geo/time/LocalToUtcTest.java` — a normal date
  (`getValidOffsets` size 1); a spring-forward local time (size 0) →
  `DST_GAP` + forward-shifted instant; a fall-back local time (size 2) →
  `DST_FOLD` + earlier-offset instant; `time == null` → `LocalTime.NOON` +
  `TIME_NOT_KNOWN`; `offsetApplied` is the actual offset; `tzdbVersion`
  non-blank; a pre-1970 `Asia/Kolkata` datetime → the historical offset
- [ ] T010 [P] [US1] `TimeZoneResolverGoldenTest` in
  `geo/src/test/java/com/celestia/geo/time/TimeZoneResolverGoldenTest.java` — a
  shared static `TimeshapeTimeZoneResolver`; ≥ 15 cities worldwide → the expected
  IANA id (hand-listed from the tz database, aligned with the tool's
  `timezonefinder` output); a mid-Pacific coordinate → `approximated == true`
  and an `Etc/GMT±h` zone matching `round(lon/15)`
- [ ] T011 [P] [US1] `ZoneResolutionTest` — `ZoneResolution` rejects a null zone
  / blank version

### Implementation

- [ ] T012 [US1] `TimeZoneResolver` interface (`ZoneResolution resolve(double
  latitude, double longitude)`) and `ZoneResolution` record (`ZoneId zone,
  boolean approximated, String datasetVersion`) in
  `geo/src/main/java/com/celestia/geo/time/`
- [ ] T013 [US1] `TimeshapeTimeZoneResolver` in
  `geo/src/main/java/com/celestia/geo/time/TimeshapeTimeZoneResolver.java` —
  lazily-initialised singleton `timeshape` engine (holder idiom); `resolve`
  returns the polygon hit, or the `Etc/GMT` longitude fallback
  (`h = Math.round(longitude / 15.0)`, inverted `Etc/GMT` sign) with
  `approximated = true`; `datasetVersion` from the pinned `timeshape` version.
  **No `net.iakovlev` type in any public member.**
- [ ] T014 [US1] `InstantResolution` record (`Instant instant, ZoneOffset
  offsetApplied, EnumSet<BirthMomentFlag> flags, String tzdbVersion`; compact
  ctor copies the set) and `LocalToUtc.resolve(LocalDate, LocalTime, ZoneId)` in
  `geo/src/main/java/com/celestia/geo/time/` — `getValidOffsets` size branching
  (research.md §3), `null` time → noon + `TIME_NOT_KNOWN`, `tzdbVersion` from
  `ZoneRulesProvider.getVersions(zone.getId()).lastKey()`

**Checkpoint**: `LocalToUtc` + `TimeZoneResolver` computable; corpus instants
resolvable once US3 wires them.

---

## Phase 4: User Story 2 — Geocode a place name (P1)

**Goal**: free-text query → a ranked, cached candidate list via a `Geocoder`
port; normalisation, coordinate parsing, deterministic ranking.

**Independent Test**: `FixtureGeocoder` returns a stable candidate list; the
normalisation and ranking rules are covered; a cache hit makes no delegate call.

### Tests (write first)

- [ ] T015 [P] [US2] `QueryNormalizerTest` + `QueryNormalizerPropertyTest`
  (jqwik) in `geo/src/test/java/com/celestia/geo/place/` — `"  Bombay "` →
  `"mumbai"`; internal whitespace collapsed; case-folded (`Locale.ROOT`); every
  alias key maps to its endonym; `normalize` is **idempotent**; two spelling
  variants sharing an alias target normalise equal
- [ ] T016 [P] [US2] `CoordinateQueryTest` — `"18.52, 73.85"`,
  `"-33.87 151.21"`, `"+40.7,-74.0"` → a `direct-input` candidate with the right
  lat/lon and a stable id; `"200, 0"` and `"not a place"` → `Optional.empty()`
- [ ] T017 [P] [US2] `CandidateRankingTest` + `CandidateRankingPropertyTest`
  (jqwik) — an exact normalised-name match sorts first; otherwise provider order
  is preserved (stable); `id` breaks ties; `rank` is idempotent and (for inputs
  with distinct ids) independent of the input permutation
- [ ] T018 [P] [US2] `CachingGeocoderTest` — a counting fake `Geocoder` delegate:
  first `geocode` delegates + stores; second returns an **equal** `GeocodeResult`
  with **zero** further delegate calls; a coordinate query is answered directly
  (no delegate, no cache write)
- [ ] T019 [P] [US2] `FixtureGeocoderTest` (uses the T004 loader) — a known query
  returns its candidates ranked; an unknown query → empty `candidates`, no
  exception

### Implementation

- [ ] T020 [P] [US2] `PlaceQuery` (`raw`, `normalized`; `static of(String)`),
  `PlaceCandidate` (`id, displayName, country, adminRegion, latitude, longitude,
  source`; compact ctor: `|lat| <= 90`, `|lon| <= 180`, `id`/`displayName`/
  `source` non-blank), `GeocodeResult` (`PlaceQuery`, `List<PlaceCandidate>` via
  `List.copyOf`; value equality) in `geo/src/main/java/com/celestia/geo/place/`
- [ ] T021 [P] [US2] `Geocoder` interface (`GeocodeResult geocode(PlaceQuery)`)
  and `GeocodeCache` interface (`Optional<GeocodeResult> get(String)`, `void
  put(String, GeocodeResult)`) in `geo/src/main/java/com/celestia/geo/place/`
- [ ] T022 [US2] `QueryNormalizer` in
  `geo/src/main/java/com/celestia/geo/place/QueryNormalizer.java` — `normalize`
  (strip, collapse whitespace, lower-case `Locale.ROOT`, whole-token alias map)
  and `aliases()` (the curated exonym→endonym map, research.md §5)
- [ ] T023 [US2] `CoordinateQuery.parse(String)` in
  `geo/src/main/java/com/celestia/geo/place/CoordinateQuery.java` — signed
  decimals, `,` or whitespace separator; out-of-range → empty
- [ ] T024 [US2] `CandidateRanking.rank(PlaceQuery, List<PlaceCandidate>)` in
  `geo/src/main/java/com/celestia/geo/place/CandidateRanking.java` — the total
  order of research.md §6
- [ ] T025 [US2] `InMemoryGeocodeCache` (`ConcurrentHashMap`) and `CachingGeocoder`
  (`CachingGeocoder(Geocoder delegate, GeocodeCache cache)`; coordinate-query
  bypass; key = `PlaceQuery.normalized()`) in
  `geo/src/main/java/com/celestia/geo/place/`
- [ ] T026 [US2] `FixtureGeocoder` (`FixtureGeocoder(Map<String,
  List<PlaceCandidate>>)`; looks up `normalized()`, ranks, wraps) in
  `geo/src/main/java/com/celestia/geo/place/FixtureGeocoder.java`

**Checkpoint**: `geocodePlace(text)` =
`new CachingGeocoder(fixture, new InMemoryGeocodeCache()).geocode(PlaceQuery.of(text)).candidates()`.

---

## Phase 5: User Story 3 — Resolve a birth moment (P1)

**Goal**: a chosen candidate + a local date/time (+ optional stated zone) → a
`ResolvedBirth` carrying every `birth_data` field, with `birthData()` feeding
SPEC-001.

**Independent Test**: for a fixture candidate + a local date/time, `ResolvedBirth`
's instant / zone / coordinates match a reference and every persisted field is
populated; the corpus passes.

### Tests (write first)

- [ ] T027 [P] [US3] `ResolvedBirthTest` in
  `geo/src/test/java/com/celestia/geo/ResolvedBirthTest.java` — `birthData()` ==
  `new BirthData(instant, latitude, longitude)`; compact-ctor invariants
  (`birthTimeKnown == !flags.contains(TIME_NOT_KNOWN)`; `statedZoneId != null` ⇔
  `ZONE_CONFLICT`); `flags` defensively copied
- [ ] T028 [P] [US3] `BirthMomentResolverTest` in
  `geo/src/test/java/com/celestia/geo/BirthMomentResolverTest.java` — with a fake
  `TimeZoneResolver`: a Pune candidate + a local date/time → `zoneId ==
  Asia/Kolkata`, the historical `instant`, the candidate's lat/lon/label/country/
  source; `versions` carries both dataset versions; local datetime = date at the
  given time (noon when null)
- [ ] T029 [P] [US3] `BirthMomentCorpusTest` (`@TestFactory`) in
  `geo/src/test/java/com/celestia/geo/BirthMomentCorpusTest.java` — reads
  `birthmoments/corpus.json`, runs `BirthMomentResolver` (real
  `TimeshapeTimeZoneResolver`), asserts each entry's `birth_utc` **to the
  second**, `zone_id`, `offset_applied`, and `flags` == `expected`

### Implementation

- [ ] T030 [US3] `ResolvedBirth` record + `birthData()` in
  `geo/src/main/java/com/celestia/geo/ResolvedBirth.java` (fields per
  data-model.md; compact-ctor invariants)
- [ ] T031 [US3] `BirthMomentResolver` in
  `geo/src/main/java/com/celestia/geo/BirthMomentResolver.java` —
  `BirthMomentResolver(TimeZoneResolver zones)`; `resolve(PlaceCandidate,
  LocalDate, LocalTime /*nullable*/, ZoneId /*nullable*/)` follows the 6 steps of
  data-model §`BirthMomentResolver` (zone → `ZONE_APPROXIMATED`; stated ≠
  geocoded → `ZONE_CONFLICT`, geocoded wins; `|lat| >= POLAR_LIMIT_DEG` →
  `POLAR_LATITUDE`; `LocalToUtc.resolve`; merge flags; build `ResolvedBirth`).
  Define `POLAR_LIMIT_DEG = 66.0` (cite ADR-0004; mirrors
  `SwissEphemerisConfig`'s default)

**Checkpoint**: the full resolve path works; `getResolvedBirth` /
`geocodePlace` answerable; corpus green.

---

## Phase 6: User Story 4 — Coordinate and zone guardrails (P2)

**Goal**: invalid coordinates rejected; polar latitude flagged consistently with
a chart cast there; a stated-vs-geocoded zone conflict surfaced, not overridden.

### Tests (write first)

- [ ] T032 [P] [US4] `PlaceCandidateTest` — `latitude 95` / `longitude 200` →
  `IllegalArgumentException` (FR-013)
- [ ] T033 [P] [US4] `BirthMomentResolverTest` (guardrail cases, extends T028) —
  a 70°N candidate → `POLAR_LATITUDE` in `flags`, **no exception** (the chart
  pipeline rejects later, SPEC-002); a stated `Asia/Kolkata` with a geocoded
  `Asia/Karachi` → `ZONE_CONFLICT`, `zoneId == Asia/Karachi` (geocoded wins),
  `statedZoneId == Asia/Kolkata`; a no-polygon candidate → `ZONE_APPROXIMATED`

### Implementation

- [ ] T034 [US4] Confirm the guardrail branches from T031 cover all three cases;
  add the `POLAR_LIMIT_DEG` / `ZONE_CONFLICT` / `ZONE_APPROXIMATED` rationale to
  `geo/REFERENCES.md` (cite ADR-0004 for the polar threshold, ADR-0014 for
  "ask, don't guess" on the zone conflict)

**Checkpoint**: every guardrail is a flag the caller (SPEC-010) can act on.

---

## Phase 7: User Story 5 — Reproducible correctness (P2)

- [ ] T035 [P] [US5] `GeoDeterminismTest` in
  `geo/src/test/java/com/celestia/geo/GeoDeterminismTest.java` — `resolve(...)`
  twice on a corpus entry → equal `ResolvedBirth`; `QueryNormalizer.normalize`
  and `CandidateRanking.rank` are idempotent; `CachingGeocoder` twice → equal
  `GeocodeResult`
- [ ] T036 [P] [US5] Add a rule to
  `agent/src/test/java/com/celestia/agent/architecture/DeterminismArchitectureTest.java`
  — no `net.iakovlev..` type in a public method return / parameter / field of
  `com.celestia.geo..` (mirror `swissEphemerisTypesDoNotLeakFromEphemerisPublicApi`)
- [ ] T037 [US5] `.github/workflows/ci.yml` — add `BirthMomentCorpusTest`,
  `TimeZoneResolverGoldenTest`, `GeoDeterminismTest` to the OS-matrix determinism
  step
- [ ] T038 [US5] Re-run `python compute_golden.py --birthmoments … ` (no
  `--write`) and confirm zero drift; note the confirmed `timeshape` +
  `zoneinfo`/`tzdb` versions in `corpus.json` and `geo/REFERENCES.md`

**Checkpoint**: the birth-moment resolve is a CI gate.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T039 [P] `geo/src/main/java/com/celestia/geo/REFERENCES.md` (new) — the
  alias map + rationale, the candidate-ranking rule, the DST gap (forward shift)
  and fold (earlier offset) conventions, the no-polygon `Etc/GMT` fallback, the
  unknown-time = noon convention, the polar threshold (cite ADR-0004), and the
  `tzdb` + `timeshape` dataset-versioning policy (FR-018)
- [ ] T040 [P] `geo/src/main/java/com/celestia/geo/package-info.java` (update) +
  `place/package-info.java` + `time/package-info.java` — point to SPEC-007, note
  purity and the deferred HTTP adapter
- [ ] T041 [P] `BirthMomentResolverPerformanceTest` `@Tag("perf")` in
  `geo/src/test/java/com/celestia/geo/BirthMomentResolverPerformanceTest.java` —
  a pre-warmed resolver; a warm `resolve(...)` under a 60 ms guard (SC-005 target
  20 ms); excluded from the default run
- [ ] T042 [P] `geo/README.md` (new) — the module's three capabilities, the
  `Geocoder` port + the deferred OpenCage adapter (ADR-0013), how to regenerate
  the corpus
- [ ] T043 Run `specs/007-geocoding-timezone/quickstart.md` end to end; fix drift
- [ ] T044 `./mvnw -q verify` — full reactor green incl.
  `DeterminismArchitectureTest` / `LayeringArchitectureTest` (now exercising
  `com.celestia.geo..`) and Spotless

---

## Dependencies & Execution Order

1. **Setup (Phase 1)** — T001 (poms) blocks everything that compiles `geo` with
   the new deps; T002 → T003 serial (tool before corpus); T003 blocks T029 /
   T035 / T038; T004 blocks T019 / T026; T005 is SC-006 (parallel, human).
2. **Foundational (Phase 2)** — `BirthMomentFlag`, `DatasetVersions`; block every
   user story.
3. **US1 (Phase 3)** — `TimeZoneResolver` / `TimeshapeTimeZoneResolver` /
   `LocalToUtc`; depends on Foundational only. Independent of US2.
4. **US2 (Phase 4)** — `geo.place.*`; depends on Foundational only. Independent
   of US1. T020/T021 → T022–T026.
5. **US3 (Phase 5)** — `ResolvedBirth` + `BirthMomentResolver`; depends on
   US1 (both resolvers) + US2 (`PlaceCandidate`). T030 → T031.
6. **US4 (Phase 6)** — tests + doc over US3's code; depends on US3.
7. **US5 (Phase 7)** — depends on US3 (+ US2 for the cache test).
8. **Polish (Phase 8)** — after the stories you intend to ship.

### Parallel opportunities

- Setup: T004 / T005 parallel; T001 first; T002 → T003 serial.
- Foundational: T006 / T007 parallel; T008 after.
- **US1 and US2 whole phases run in parallel** (disjoint packages).
- US1: T009 / T010 / T011 parallel; T012 → T013, T012 → T014.
- US2: T015–T019 (tests) parallel; T020 / T021 parallel; T022 / T023 / T024
  parallel; T025 after T021; T026 after T020+T024.
- US3: T027 / T028 / T029 (tests) parallel; T030 → T031.
- US5: T035 / T036 parallel; T037 / T038 after.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US3. Delivers the full resolve path —
`geocodePlace` (fixture-backed + cached) and a `ResolvedBirth` whose
`birthData()` feeds the chart engine. Stop, run `quickstart.md`, open the PR.

**Blocked-work note**: T029 / T035 / T038 need the corpus (T003), which needs the
tool (T002). US1/US2 implementation is otherwise unblocked once T001 lands. The
OpenCage HTTP adapter is **out of scope** (ADR-0013 — lands with SPEC-009/010);
`FixtureGeocoder` is the only `Geocoder` binding this spec ships.

---

## Notes

- No new module. New deps: internal `ephemeris` (the `BirthData` type only) and
  `net.iakovlev:timeshape` (ADR-0014, accepted). ADR-0013 accepted by the plan.
- `geo` stays **pure** — `timeshape` reads only its bundled classpath data (the
  `.se1` category), `LocalToUtc` / `BirthMomentResolver` take every input
  explicitly, no `now()`. `DeterminismArchitectureTest` /
  `LayeringArchitectureTest` already list `com.celestia.geo..`; T036 adds the
  `net.iakovlev` non-leak rule.
- Reproducibility rests on `DatasetVersions` (`tzdb` + `timeshape`) carried on
  `ResolvedBirth` — `geo` has no `EngineVersion`. A dataset bump changes the
  recorded version and the corpus flags every affected instant (FR-016).
- Independent verification is real: the tool uses Python `zoneinfo` (system IANA
  tzdata, a separate copy from the JRE) + `timezonefinder` (a different polygon
  dataset). SC-006 adds a human check of 3 births against an outside source.
- Output for the LLM / FSM is **structured only** — a candidate list, a
  `ResolvedBirth` with flags; the disambiguation *interaction* and any
  user-confirmation prompt are SPEC-010.
- Commit per task or tight group; keep `master` releasable (work on `007-…`).
