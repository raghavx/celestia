# `geo` rule sources (SPEC-007)

Constitution I / III: every non-obvious rule names its source or is marked a
**v1 design choice**. Reproducibility rests on the two dataset versions carried on
every `ResolvedBirth` (`DatasetVersions`) — `geo` has no `EngineVersion`.

| Rule | Where | Source |
|------|-------|--------|
| lat/lon → IANA `ZoneId`, offline (embedded polygons) | `time.TimeshapeTimeZoneResolver` | ADR-0014; `net.iakovlev:timeshape` (bundled OSM timezone-boundary data) |
| A coordinate whose dataset hit is a fixed-offset ocean tile (`Etc/GMT±h`) is reported `approximated` | `time.TimeshapeTimeZoneResolver` | **v1 choice** — an ocean tile is not a civil zone; the flag asks the caller to confirm the place |
| No polygon at all → `Etc/GMT` from `round(longitude / 15)` (note the `Etc/GMT` sign is inverted) + `ZONE_APPROXIMATED` | `time.TimeshapeTimeZoneResolver.etcGmtFor` | **v1 choice** (defensive — the current dataset always returns a zone) |
| local date/time + zone → UTC via historical rules | `time.LocalToUtc` | ADR-0014; `java.time` `ZoneRules` (the JRE's bundled IANA `tzdb`) |
| DST **gap** (local time never occurred) → shift forward by the gap; `DST_GAP` | `time.LocalToUtc` | `java.time` documented `LocalDateTime.atZone` behaviour; forward is the conventional correction for a clock error |
| DST **fold** (local time occurred twice) → the **earlier** offset; `DST_FOLD` | `time.LocalToUtc` | **v1 choice** — `withEarlierOffsetAtOverlap()`; the flag is what matters, SPEC-010 can ask |
| Birth time unknown → `12:00` local; `TIME_NOT_KNOWN`; `birthTimeKnown = false` | `time.LocalToUtc`, `BirthMomentResolver` | **v1 choice** — noon minimises worst-case error in the slow chart elements and keeps the instant inside the civil date at every zone |
| `tzdb` version = `ZoneRulesProvider.getVersions("Etc/UTC").lastKey()` | `time.LocalToUtc.currentTzdbVersion` | global to the JRE; recorded for FR-016 |
| Query normalisation: strip, collapse internal whitespace, lower-case (`Locale.ROOT`), then a whole-token exonym→endonym alias map | `place.QueryNormalizer` | **v1**, curated. Renames: Bombay→Mumbai (1995), Calcutta→Kolkata (2001), Madras→Chennai (1996), Poona→Pune, Bangalore→Bengaluru (2014), Baroda→Vadodara, Trivandrum→Thiruvananthapuram, Cochin→Kochi, Gauhati→Guwahati, Benares→Varanasi, Peking→Beijing (postal romanisation), Saigon→Ho Chi Minh City. The geocoder resolves most historical names; the map keeps the **cache key** stable across spellings. Extend by adding a row |
| A query matching `<lat><sep><lon>` (signed decimals, comma or whitespace) → one `direct-input` candidate; bypasses normalisation, ranking, and the cache | `place.CoordinateQuery`, `place.CachingGeocoder` | FR-008; **v1 choice** |
| Candidate ranking: exact normalised-name match first (that group ordered by `id`), then the provider's order preserved (stable), never collapsed to one | `place.CandidateRanking` | **v1 choice** — run-to-run determinism for a query comes from `CachingGeocoder` (the provider is called once and frozen), not from re-sorting |
| Cache policy: `InMemoryGeocodeCache` is **permanent for the process lifetime, no TTL** — a place's coordinates do not move | `place.InMemoryGeocodeCache` | **v1 choice**; a forced re-fetch (e.g. a provider correction) is a SPEC-008 concern, keyed on the stored `geocode_source`. The persistent cache over `geocode_cache` is SPEC-008 |
| Geocoder HTTP adapter (OpenCage) lives **outside** `geo` | — | ADR-0013 (accepted 2026-09-10); Constitution IX. Lands with SPEC-009/010; `FixtureGeocoder` is the binding shipped here |
| Invalid coordinates (`|lat| > 90`, `|lon| > 180`) rejected | `place.PlaceCandidate`, `ephemeris.BirthData` | FR-013 |
| `|latitude| >= 66.0°` → `POLAR_LATITUDE` flag; **not rejected** — the chart pipeline rejects a polar birth at cast time | `BirthMomentResolver.POLAR_LIMIT_DEG` | ADR-0004 (Placidus undefined above ~66°34′); mirrors `ephemeris.swisseph.SwissEphemerisConfig`'s default polar limit. SPEC-002 raises `PlacidusUndefinedException` |
| A caller-stated zone ≠ the geocoded zone → `ZONE_CONFLICT`; the **geocoded** zone is used and the stated one recorded | `BirthMomentResolver` | ADR-0014 ("if the user's stated zone contradicts the geocoded one, ask rather than guess"); `geo` resolves with its best guess and flags, SPEC-010 asks |

## Verification

- The birth-moment corpus (`src/test/resources/birthmoments/corpus.json`) is
  recomputed independently by `tools/ephe-crosscheck/compute_golden.py
  --birthmoments` — Python `zoneinfo` (the system IANA `tzdata`, a separate copy
  from the JRE's bundled `tzdb`, forced to the bundled `tzdata` package when
  present) and `timezonefinder` (a different polygon dataset from `timeshape`).
  `BirthMomentCorpusTest` asserts the engine matches, to the second.
- SC-006: for 3 births a human checks the computed instant against an outside
  source; see the `verification` block in `corpus.json`.
- Dataset versions in effect when the corpus was last generated are recorded in
  its `generated_by` string.
