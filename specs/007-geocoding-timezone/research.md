# Phase 0 Research: Geocoding & Timezone (SPEC-007)

Every non-obvious rule below is either cited or marked a **v1 design choice**, and
each lands in `geo/src/main/java/com/celestia/geo/REFERENCES.md` — the same
discipline `core/REFERENCES.md` applies to KP rules (Constitution III).

## 1. The geocoder scope boundary

**Decision**: SPEC-007 delivers, in the pure `geo` module —

- the `Geocoder` **port** and its value objects (`PlaceQuery`, `PlaceCandidate`,
  `GeocodeResult`),
- deterministic query **normalisation** (`QueryNormalizer`) and candidate
  **ranking** (`CandidateRanking`),
- a `GeocodeCache` **port**, an `InMemoryGeocodeCache`, and a `CachingGeocoder`
  decorator,
- a `FixtureGeocoder` — a `Geocoder` backed by bundled data, **no network** —
  for local development and every test.

The **OpenCage HTTP adapter is deferred** to its consumer (SPEC-009 channel /
SPEC-010 onboarding). Reasons:

- It is edge I/O with an API key per environment (ADR-0013) — Constitution IX
  keeps it out of `geo` entirely, so it is a separate component regardless.
- It needs live-HTTP contract tests and a recorded-response fixture harness —
  a body of work that belongs with the module that owns the HTTP stack.
- **Nothing calls `geocodePlace` yet.** The tool is exposed to the LLM in the
  `agent` module (SPEC-014+) and driven by the onboarding FSM (SPEC-010). The
  `geo` deliverables here are exactly what those specs consume; the fixture
  geocoder satisfies SPEC-008's Phase 3 exit ("given name/DOB/TOB/place text,
  store `birth_data`").

This is **consistent with the spec as written** — its Assumptions already state
"`geo` ships only the port, the value objects, and a fixture/cache-backed
`Geocoder`; the HTTP adapter and the API key live outside `geo`."

**ADR-0013** is moved `proposed → accepted` by the plan (dated 2026-09-10): the
decision — OpenCage behind a `geo` port, every result cached — is locked; the
consequence note records that the adapter implementation lands with SPEC-009/010
and that until then the `FixtureGeocoder` is the only binding.

## 2. lat/lon → IANA `ZoneId`

**Decision**: `timeshape` (`net.iakovlev:timeshape`), per ADR-0014 — embedded
GeoJSON timezone polygons, a pure offline lookup.

- **API**: `TimeZoneEngine.initialize()` builds an in-memory spatial index from
  the jar's bundled data (~50 MB, ~1–3 s); `engine.query(lat, lng)` →
  `Optional<ZoneId>`. The engine is immutable and thread-safe once built.
- **Lazy singleton**: `TimeshapeTimeZoneResolver` holds a lazily-initialised
  static engine (holder idiom) so the build cost is paid once per JVM and never
  in a hot path. This is offline classpath-resource loading — the same category
  as `ephemeris` reading `.se1` files — not a Constitution II "network / DB"
  violation.
- **Version pinning**: the `timeshape` version string encodes the tzdata release
  it bundles (e.g. `2025x.NN`). Pin an exact version in the parent
  `dependencyManagement`; `/speckit-tasks` records the confirmed version from
  Maven Central. `ZoneResolution.datasetVersion` carries it (FR-016).
- **No polygon** (mid-ocean, Antarctic gaps): fall back to
  `ZoneId.ofOffset("Etc/GMT", ZoneOffset.ofHours(-round(lon / 15)))` — note the
  `Etc/GMT` sign convention is inverted — and set `ZONE_APPROXIMATED`. This is a
  **v1 choice**: a fixed-offset zone with no DST is the safest guess when there
  is genuinely no civil zone; the flag tells the caller to confirm.
- **ArchUnit**: a new rule keeps `net.iakovlev..` types out of every public
  member of `com.celestia.geo` (mirrors the Swiss Ephemeris rule) — `timeshape`
  stays behind `TimeZoneResolver`, which returns only `java.time.ZoneId`.

**Independent check**: Python `timezonefinder` (a *different* polygon dataset)
over ≥ 15 world cities → the same IANA id; each city's expected id is also listed
by hand from the tz database.

## 3. local date/time + zone → UTC instant

**Decision**: `java.time`, historical rules, with explicit gap/fold **detection**.

```
ZoneRules rules = zone.getRules();
List<ZoneOffset> valid = rules.getValidOffsets(localDateTime);
```

| `valid.size()` | Situation | v1 resolution | Flag |
|---|---|---|---|
| 1 | normal | `localDateTime.atZone(zone).toInstant()` | — |
| 0 | DST **gap** — the local time never occurred | `atZone` shifts forward by the transition length (`ZoneOffsetTransition.getDuration()`); take that instant | `DST_GAP` |
| 2 | DST **fold** — the local time occurred twice | `zdt.withEarlierOffsetAtOverlap()` — the **earlier** (pre-transition, usually DST) offset | `DST_FOLD` |

- The **gap** rule is `java.time`'s documented `atZone` behaviour (shift by the
  gap); we adopt it rather than reject, and flag it — a birth registered inside a
  spring-forward gap is a clock error, and forward is the conventional fix.
- The **fold** rule (earlier offset) is a **v1 choice**: without more information
  the earlier instant is as defensible as the later; the flag is what matters so
  SPEC-010 can ask.
- `InstantResolution.offsetApplied` returns the `ZoneOffset` actually used, so a
  reviewer sees exactly which side of a transition was taken.
- **`tzdb` version**: `ZoneRulesProvider.getVersions(zone.getId()).lastKey()` →
  e.g. `"2025b"`. Recorded on `InstantResolution` and `ResolvedBirth`
  (`DatasetVersions.tzdbVersion`) so a JRE upgrade that moves a rule is caught by
  the corpus (SC-001, FR-016).

**Historical correctness**: pre-1970 offsets, one-off war-time changes, and
countries that shifted their *standard* offset (India +05:53:20 → +05:30 in 1955,
Portugal, parts of Alaska) all come from the bundled `tzdb`. The corpus (§8)
includes at least one standard-offset change.

**Independent check**: Python `zoneinfo` uses the **system** IANA `tzdata`, a
separate implementation *and* a separate copy of the data from the JRE's bundled
`tzdb` — a genuine cross-check, not a mirror. Matches must hold to the second.

## 4. Unknown birth time

**Decision**: `12:00` local, `TIME_NOT_KNOWN` flag, `birthTimeKnown = false` on
`ResolvedBirth`.

Noon minimises the worst-case error in the slow chart elements (the Moon moves
~½° from noon to either boundary of the day) and keeps the instant inside the
civil date at every zone. The chart is still cast; the agent surfaces that the
Ascendant, the cusps, and fine dasha timing are unreliable (a downstream, LLM-copy
concern, not `geo`'s).

## 5. Query normalisation + the alias map

`QueryNormalizer.normalize(raw)`:

1. `strip()`, then collapse every internal whitespace run to a single space;
2. lower-case with `Locale.ROOT`;
3. apply a small curated **exonym → endonym** map, whole-token:

| From | To | Note |
|---|---|---|
| bombay | mumbai | renamed 1995 |
| calcutta | kolkata | renamed 2001 |
| madras | chennai | renamed 1996 |
| poona | pune | colonial spelling |
| bangalore | bengaluru | renamed 2014 |
| baroda | vadodara | |
| peking | beijing | postal romanisation |
| bangkok | bangkok | (identity — placeholder for future entries) |

The geocoder itself resolves most historical names; the map is the safety net
for the ones a bare provider query misses, and it makes the **cache key** stable
across spellings. **v1**, curated, cited here; extend by adding a row (no
`EngineVersion` — `geo` has none; the map is documented data).

A query that `CoordinateQuery.parse` recognises as `"<lat>, <lon>"` (optionally
signed, `.` decimal, comma or whitespace separator) becomes a single
`PlaceCandidate` with `source = "direct-input"` and **bypasses** normalisation,
the alias map, ranking, and the cache.

## 6. Candidate ranking

`CandidateRanking.rank(query, providerCandidates)` — a deterministic total order:

1. an exact match of `PlaceCandidate.displayName` (normalised) to
   `query.normalized()` sorts first;
2. otherwise the provider's own order is preserved (a stable sort);
3. ties break on `PlaceCandidate.id` (lexicographic) so the order is total and
   reproducible even if the provider reorders equal-confidence hits.

The engine **never collapses the list to one** — disambiguation is SPEC-010's
interaction (FR-009, spec US2 scenario 2).

## 7. Coordinate + zone guardrails

| Input | Behaviour | Where |
|---|---|---|
| `|lat| > 90` or `|lon| > 180` | reject (`IllegalArgumentException`) | `PlaceCandidate` ctor + `BirthData` already enforce |
| `|lat| ≥` the SPEC-002 polar constant (~66°, ADR-0004) | resolve normally but set `POLAR_LATITUDE`; the chart pipeline still rejects at cast time (SPEC-002 `PlacidusUndefinedException`) | `BirthMomentResolver` |
| user-stated `ZoneId` ≠ geocoded zone | set `ZONE_CONFLICT`; **use the geocoded zone**; `ResolvedBirth` records both so SPEC-010 can ask | `BirthMomentResolver` |
| user-stated zone **==** geocoded zone, or no stated zone | no flag; geocoded zone used | `BirthMomentResolver` |

`geo` flags and proceeds with its best guess; it never blocks. The FSM (SPEC-010)
decides whether a flag needs a user confirmation before persisting.

## 8. The corpus + independent cross-check

`geo/src/test/resources/birthmoments/corpus.json` — ≥ 12 entries, each:
`{ id, place_query, latitude, longitude, stated_zone?, birth_date, birth_time?,
expected: { zone_id, offset_applied, birth_utc, flags[], tzdb_version,
timezone_boundary_version } }`.

Coverage: pre-1970 births in ≥ 3 zones; the India 1955 standard-offset change; a
US spring-forward gap; a EU fall-back fold; a southern-hemisphere DST birth
(Australia / Chile); an unknown-time birth; a no-polygon (ocean) coordinate; a
polar-latitude birth; a stated-vs-geocoded zone conflict.

**Tooling** — extend `tools/ephe-crosscheck/compute_golden.py`:

- a `--birthmoments PATH` mode, independent of the golden-chart loop;
- for each entry: `zoneinfo.ZoneInfo(zone_id)` for local→UTC (with
  `fold=0` / gap handling replicating §3), `timezonefinder.TimezoneFinder` for
  lat/lon→zone;
- `--write` fills `expected`; without it, verify (non-zero exit on drift);
- header bumped to `kp-crosscheck 0.7`; `timezonefinder` added to
  `requirements.txt`.

**Java** — `BirthMomentCorpusTest` (`@TestFactory`) reads the same JSON and runs
`BirthMomentResolver`; `TimeZoneResolverGoldenTest` covers the city sample;
`GeoDeterminismTest` asserts equal inputs ⇒ equal `ResolvedBirth`.

**SC-006** (human): for 3 births — one pre-1970, one DST-boundary, one ordinary —
a person checks the computed local→UTC instant against an independent source (an
online tz converter, or a published birth-time record) and records it verified.
The `geo` code and the Python tool both lean on IANA data; an outside reference
is the only fully independent verification.

## Summary of decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Geocoder scope | port + value objects + normalisation + ranking + in-memory cache + `FixtureGeocoder` now; OpenCage HTTP adapter deferred to SPEC-009/010; ADR-0013 → accepted |
| 2 | lat/lon → zone | `timeshape`, lazy singleton engine, exact version pin; no-polygon → `Etc/GMT` from longitude + `ZONE_APPROXIMATED` |
| 3 | local → UTC | `java.time` historical rules; `getValidOffsets` size 0 → `DST_GAP` (shift forward), 2 → `DST_FOLD` (earlier offset); `offsetApplied` + `tzdb` version returned |
| 4 | Unknown time | 12:00 local + `TIME_NOT_KNOWN` |
| 5 | Normalisation | strip / collapse / case-fold + a curated exonym→endonym map; coordinate queries bypass everything |
| 6 | Ranking | exact-name-match first, else stable provider order, id tie-break; never collapse to one |
| 7 | Guardrails | invalid coords reject; polar → `POLAR_LATITUDE` (chart rejects later); stated≠geocoded zone → `ZONE_CONFLICT`, geocoded wins, both recorded |
| 8 | Verification | ≥ 12-birth corpus; independent `zoneinfo` + `timezonefinder` cross-check in `compute_golden.py`; human check of 3 (SC-006) |
