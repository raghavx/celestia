# Feature Specification: Geocoding & Timezone (SPEC-007)

**Feature Branch**: `007-geocoding-timezone`

**Created**: 2026-09-10

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-007 Geocoding + timezone: place→lat/lon,
lat/lon→zone, local birth time→UTC (historical rules), disambiguation. Depends on:
—." Decisions D13 (geocoder — ADR-0013, *proposed*) and D14 (birth-time→UTC —
ADR-0014, *accepted*). Engine tool `geocodePlace(free-text place)` → "candidate
list (name, country, lat, lon)". Persistence: `birth_data` (`place_query`,
`place_name`, `country`, `lat`, `lon`, `zone_id`, `birth_utc`, `geocode_source`),
`geocode_cache` (`query_norm`, `results_json`).

_Every KP number the engine produces starts from one instant in UTC and one pair
of coordinates. Users give a colloquial place name and a local civil time. This
feature turns "Bandra, Bombay" + "14 August 1962, 9:47 pm" into the exact
`(instant, latitude, longitude)` the chart engine needs — resolving the place to
coordinates, the coordinates to an IANA time zone, and the local time to UTC
using the **historical** offset rules for that date. A wrong pre-1970 offset or a
mishandled DST transition shifts every cusp and the dasha balance._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Local birth time to UTC (Priority: P1)

Given a local birth date, a local birth time, and coordinates, the engine returns
the **UTC instant**, the resolved **IANA zone**, and the exact **offset applied**,
using the historical rules for that date (pre-1970 offsets, DST transitions,
one-off war-time changes). A local time that does not exist (a DST spring-forward
gap) or occurs twice (a fall-back fold) is **flagged with a documented
resolution**, never silently guessed.

**Why this priority**: The instant is the single input to every chart. This
step is fully deterministic and offline — it is the MVP and it does not need the
geocoder.

**Independent Test**: A corpus of births spanning pre-1970, war-time offsets, DST
boundaries and both hemispheres — each computed instant matches a hand-verified
reference to the second.

**Acceptance Scenarios**:

1. **Given** a birth at 21:47 on 1962-08-14 in Asia/Kolkata, **When** the instant
   is resolved, **Then** it is `1962-08-14T16:17:00Z` (offset +05:30, the
   historical value for that date) and the zone is `Asia/Kolkata`.
2. **Given** a local time inside a DST spring-forward gap, **When** the instant is
   resolved, **Then** the result carries a "non-existent local time" flag and the
   documented forward shift is applied.
3. **Given** a local time inside a DST fall-back fold, **When** the instant is
   resolved, **Then** the result carries an "ambiguous local time" flag and the
   documented offset (the earlier one) is applied.
4. **Given** the birth time is marked unknown, **When** the instant is resolved,
   **Then** it uses the documented convention (local noon) and carries a
   "time not known" flag.

---

### User Story 2 - Geocode a place name (Priority: P1)

Given a free-text place query, the engine returns a **ranked list of candidates**
— each with a display name, country, an administrative region, latitude,
longitude, the source, and a stable candidate id — via a `Geocoder` port. The
query is normalised first; results are cached so a repeated query is deterministic
and costs nothing.

**Why this priority**: KP cusps need accurate coordinates, and users rarely give
an unambiguous place. `geocodePlace` is a standalone tool.

**Independent Test**: A checked-in fixture geocoder returns a stable candidate
list for a set of queries; the normalisation and ranking rules are covered.

**Acceptance Scenarios**:

1. **Given** the query "  bombay ", **When** it is geocoded, **Then** it is
   normalised to `bombay` and returns candidates (Mumbai, India, …) in a stable,
   documented order.
2. **Given** a query with multiple real matches (e.g. "Springfield"), **When** it
   is geocoded, **Then** every match is returned as a candidate — the engine does
   not pick one.
3. **Given** a query that has been geocoded before, **When** it is geocoded again,
   **Then** the result is byte-identical and no provider call is made.
4. **Given** a query with no match, **When** it is geocoded, **Then** an empty
   candidate list is returned (not an error).

---

### User Story 3 - Resolve a birth moment (Priority: P1)

Given a chosen place candidate and a local birth date/time, the engine returns a
**resolved birth** — the local date-time, the zone, the UTC instant, the
latitude, longitude, a place label, the offset applied, and any DST / unknown-time
flags — the exact record that feeds `BirthData` and is persisted on `birth_data`.

**Why this priority**: This is what the onboarding flow (SPEC-010) calls once the
user has picked a candidate. It composes User Story 1 and User Story 2.

**Independent Test**: For a fixture candidate + a local date/time, the resolved
birth's instant, zone and coordinates match a reference, and every persisted
`birth_data` field is populated.

**Acceptance Scenarios**:

1. **Given** a candidate for Pune, India and a local birth date/time, **When** the
   birth is resolved, **Then** the result carries `zone_id = Asia/Kolkata`, the
   correct historical `birth_utc`, and the candidate's `lat` / `lon` / label /
   source.
2. **Given** a resolved birth, **When** its coordinates and instant are read,
   **Then** they are exactly the values a chart cast for that birth would use.

---

### User Story 4 - Coordinate and zone guardrails (Priority: P2)

The engine rejects invalid coordinates, rejects or clearly flags coordinates
beyond the Placidus polar limit (~66°, ADR-0004), and — when the user states a
time zone that differs from the geocoded one — surfaces the conflict rather than
silently choosing.

**Why this priority**: A bad coordinate or a silently-overridden zone produces a
plausible-looking but wrong chart. Better to stop and ask.

**Independent Test**: Out-of-range coordinates and polar coordinates are rejected
/ flagged; a stated-vs-geocoded zone mismatch is reported.

**Acceptance Scenarios**:

1. **Given** latitude 95° or longitude 200°, **When** a birth is resolved,
   **Then** the request is rejected.
2. **Given** coordinates at 70°N, **When** a birth is resolved, **Then** the
   result carries a "polar latitude" flag; the birth moment still resolves (only
   the *chart* is undefined at that latitude — the chart pipeline rejects it at
   cast time, SPEC-002).
3. **Given** the user says "Asia/Kolkata" but the geocoded zone is "Asia/Karachi",
   **When** the birth is resolved, **Then** the result reports the conflict and
   does not silently pick one.

---

### User Story 5 - Reproducible correctness (Priority: P2)

A checked-in geocode fixture set and a timezone/DST test corpus (pre-1970,
war-time, DST-boundary births, both hemispheres) produce deterministic snapshots.
The offline timezone-boundary dataset version and the `java.time` tz-rules version
are recorded on every result.

**Independent Test**: The corpus in CI; a determinism test; the recorded dataset
versions.

**Acceptance Scenarios**:

1. **Given** the corpus, **When** the suite runs, **Then** zero instants differ
   from the recorded references.
2. **Given** a change to the timezone dataset or the resolution rules, **Then**
   the recorded version changes and the suite flags every affected value.

---

### Edge Cases

- Coordinates over open ocean / a location with no timezone polygon → a defined
  fallback (the nearest zone, or an `Etc/GMT±n` zone from the longitude) plus a
  "zone approximated" flag.
- A place name that is historical or colloquial ("Bombay", "Madras", "Peking") →
  resolved via the geocoder (which handles most) with a small checked-in alias map
  for the rest.
- A birth exactly at a DST transition instant → half-open convention, documented.
- A country that changed its standard offset historically (e.g. India before
  1955, Portugal, Alaska) → `java.time`'s historical rules apply; the corpus
  includes at least one.
- The same normalised query hitting the cache with a stale provider result →
  cache policy (TTL or permanent) is documented; the stored `geocode_source` lets
  a later re-fetch be forced.
- A query that is only coordinates ("18.52, 73.85") → parsed directly to a
  candidate, no provider call.
- Latitude/longitude given to more precision than the geocoder returns → stored
  as given; the chart uses the exact stored value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Given a latitude and longitude, the engine MUST resolve the **IANA
  time-zone id** offline and deterministically (an embedded timezone-boundary
  dataset), returning the zone and the dataset version.
- **FR-002**: A coordinate with no timezone polygon MUST resolve to a documented
  fallback zone (nearest zone or a longitude-derived `Etc/GMT` zone) and carry a
  "zone approximated" flag.
- **FR-003**: Given a local date, a local time and an IANA zone, the engine MUST
  compute the **UTC instant** using `java.time`'s historical rules for that date,
  and MUST return the exact **offset applied** and the tz-rules version.
- **FR-004**: A local time that falls in a DST **gap** (does not exist) MUST be
  flagged and resolved by a documented rule (shift forward by the gap length).
- **FR-005**: A local time that falls in a DST **fold** (occurs twice) MUST be
  flagged and resolved by a documented rule (the earlier offset).
- **FR-006**: When the birth time is not known, the engine MUST use a documented
  convention (local noon) and set a "time not known" flag; the instant is still
  produced.
- **FR-007**: Given a free-text query, the engine MUST **normalise** it (trim,
  collapse internal whitespace, case-fold) and MUST support an optional
  checked-in alias map for historical / colloquial names.
- **FR-008**: A query that is a coordinate pair MUST be parsed directly to a
  candidate with no provider call.
- **FR-009**: The `Geocoder` port MUST return **zero or more ranked candidates**,
  each with a display name, country, administrative region, latitude, longitude,
  the source identifier, and a stable candidate id; the ranking rule MUST be
  documented and deterministic given the provider response.
- **FR-010**: Geocode results MUST be **cached** behind a `GeocodeCache` port
  keyed on the normalised query; a cache hit MUST return the identical result with
  no provider call. The cache policy (TTL or permanent, and how a re-fetch is
  forced) MUST be documented.
- **FR-011**: The `Geocoder` HTTP implementation MUST be an **adapter outside the
  `geo` module**; no network call and no provider SDK/type in the `geo` public
  API (Constitution II, IX). `geo` MUST ship a deterministic fixture `Geocoder`
  for tests and local development.
- **FR-012**: Given a chosen candidate and a local date/time, the engine MUST
  produce a **resolved birth** carrying every **place-and-time-derived** field
  `birth_data` persists: the local date-time, `zone_id`, `birth_utc`,
  `latitude`, `longitude`, place label, country, `geocode_source`, the offset
  applied, and the DST / unknown-time flags. The non-derived columns —
  `place_query` (the raw text the user typed) and `name` — are threaded to
  persistence by the caller (SPEC-010), not echoed back by `geo`.
- **FR-013**: Invalid coordinates (`|lat| > 90` or `|lon| > 180`) MUST be
  rejected.
- **FR-014**: Coordinates at or beyond the Placidus polar limit (ADR-0004) MUST
  raise a "polar latitude" flag on the resolved birth. `geo` does **not** reject
  them — a birth moment is well-defined at any latitude; the Placidus *chart* is
  not, and the chart pipeline rejects a polar birth at cast time (SPEC-002,
  `PlacidusUndefinedException`). The flag lets SPEC-010 warn before persisting.
- **FR-015**: A user-stated zone that differs from the geocoded zone MUST be
  **reported as a conflict**, not silently overridden.
- **FR-016**: For identical inputs the engine MUST produce identical output
  across runs and platforms; the timezone-boundary dataset version and the
  tz-rules version MUST be recorded on every result.
- **FR-017**: The `geo` domain code MUST be pure — no Spring, no database, no
  network, no wall clock (Constitution II).
- **FR-018**: Every non-obvious rule (the no-polygon fallback, the DST gap/fold
  resolutions, the unknown-time convention, the candidate ranking) MUST be
  documented in a `geo/REFERENCES.md` (or the module README), citing the tz
  database / ADR where applicable.

### Key Entities *(include if data involved)*

- **Place query**: the raw free-text (or coordinate) input and its normalised form.
- **Place candidate**: one geocoder result — display name, country, admin region,
  latitude, longitude, source, stable id.
- **Zone resolution**: for a coordinate — the IANA zone id, whether it was
  approximated, and the dataset version.
- **Instant resolution**: for a local date/time + zone — the UTC instant, the
  offset applied, the DST gap/fold/unknown flags, and the tz-rules version.
- **Resolved birth**: the composed record that feeds `BirthData` and `birth_data`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a corpus of ≥ 12 births spanning pre-1970 offsets, at least one
  pre-1970 offset that differs from the zone's modern one (e.g. India's wartime
  +06:30, or British Double Summer Time), DST gap and fold cases, and both
  hemispheres, every computed UTC instant matches an **independent reference** —
  Python `zoneinfo` + `timezonefinder`, a separate implementation *and* a
  separate copy of the IANA data from the JRE's bundled `tzdb` — **to the
  second**. (≥ 3 of these are additionally checked by hand, SC-006.)
- **SC-002**: For a sample of ≥ 15 cities worldwide, `lat/lon → zone` matches the
  reference tz database's assignment.
- **SC-003**: A cached query returns an **equal** candidate list (value equality
  on `GeocodeResult`) across runs and platforms; no provider call is made on a
  hit. (Byte-level stability of the serialised `results_json` is SPEC-008.)
- **SC-004**: Every DST gap / fold / unknown-time birth in the corpus is
  **flagged**; none is silently resolved.
- **SC-005**: Resolving a birth moment from a cached candidate is deterministic
  and completes in under **20 ms** on a developer machine; the corpus snapshot
  passes unchanged in CI on every supported platform.
- **SC-006**: For at least three births, the computed local→UTC instant is
  additionally checked against an independent source (an online tz converter or a
  published birth-time record) and recorded as verified.

## Assumptions

- **The timezone-boundary dataset is embedded and offline** (chosen in
  `/speckit-plan`, per ADR-0014's `timeshape` direction), pinned by version and
  bumped deliberately — the same treatment as the `.se1` ephemeris data.
- **Historical offsets come from `java.time`'s bundled tz rules** (the JRE
  `tzdb`); its version is recorded on every instant resolution so a JRE upgrade
  that changes a rule is detectable.
- **The real geocoder is a hosted API behind the `Geocoder` port** (ADR-0013,
  currently *proposed* — to be **accepted** in `/speckit-plan`). `geo` ships only
  the port, the value objects, and a fixture/cache-backed `Geocoder`; the HTTP
  adapter and the API key live outside `geo`.
- **The `GeocodeCache` port is defined in `geo`; its persistent implementation is
  SPEC-008** (`geocode_cache` table). This feature uses an in-memory cache.
- **Disambiguation is an interaction, not a decision** — SPEC-007 returns the
  candidate list and the resolver; the WhatsApp interactive list and the user's
  pick are SPEC-010.
- **"Birth time unknown" → local noon**, flagged; the chart is still cast and the
  agent surfaces the caveat (fine Ascendant / cuspal detail is unreliable).
- **Reverse geocoding, full address parsing, map rendering, routing** — out of
  scope.
- **The produced coordinates + instant feed SPEC-001 `BirthData`**; whether `geo`
  depends on `ephemeris` to return `BirthData` directly or returns its own record
  is a `/speckit-plan` decision.

## Dependencies

- **SPEC-001** — `BirthData` (the produced instant + latitude + longitude);
  ADR-0004 (the Placidus polar limit).
- **New** — an embedded timezone-boundary dataset; `java.time`'s `tzdb`.
- **ADRs** — ADR-0014 (accepted); **ADR-0013 must be moved to accepted** in
  planning.
- **Consumed by** SPEC-010 (onboarding FSM — asks name / DOB / TOB / POB, shows
  the candidate list, confirms the echoed local time) and SPEC-008 (persistence:
  `birth_data`, `geocode_cache`).
