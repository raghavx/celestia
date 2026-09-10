# Phase 1 Data Model: Geocoding & Timezone (SPEC-007)

All types are in `com.celestia.geo` (root), `com.celestia.geo.place`, or
`com.celestia.geo.time`; immutable, pure. Coordinates are decimal degrees,
east-positive longitude. `geo` depends on `ephemeris` for `BirthData` only.

## `com.celestia.geo.place`

### `PlaceQuery` (record)

`(String raw, String normalized)`

- `static PlaceQuery of(String raw)` — `normalized = QueryNormalizer.normalize(raw)`.
- Compact ctor: `raw` non-blank; `normalized` non-blank.

### `PlaceCandidate` (record)

`(String id, String displayName, String country, String adminRegion,
double latitude, double longitude, String source)`

- `id` non-blank and stable for a given place + source (used as a cache-safe
  reference and a ranking tie-break).
- `source` — e.g. `"opencage"`, `"fixture"`, `"direct-input"`.
- Compact ctor: `|latitude| <= 90`, `|longitude| <= 180`; `id` / `displayName` /
  `source` non-blank; `country` / `adminRegion` may be `""`.

### `GeocodeResult` (record)

`(PlaceQuery query, List<PlaceCandidate> candidates)`

- `candidates` is `List.copyOf`, already **ranked** (`CandidateRanking`), possibly
  empty.
- Value equality — two results with equal query + equal candidate list are equal
  (backs the "byte-identical on a cache hit" claim, SC-003).

### `Geocoder` (interface)

```java
GeocodeResult geocode(PlaceQuery query);
```

- Implementations: `FixtureGeocoder` (this spec), `CachingGeocoder` (decorator),
  `OpenCageGeocoder` (SPEC-009/010, outside `geo`).
- Contract: deterministic for a given backing data set; never returns `null`;
  an empty match is an empty `candidates` list, not an exception.

### `GeocodeCache` (interface)

```java
Optional<GeocodeResult> get(String queryNorm);
void put(String queryNorm, GeocodeResult result);
```

- Keyed on `PlaceQuery.normalized()`. `InMemoryGeocodeCache` wraps a
  `ConcurrentHashMap`. The DB-backed implementation is SPEC-008.

### `CachingGeocoder` (class, `Geocoder`)

`CachingGeocoder(Geocoder delegate, GeocodeCache cache)` — `geocode` returns a
cache hit unchanged (no delegate call); on a miss, calls `delegate`, stores, and
returns. A coordinate query (`CoordinateQuery.parse` non-empty) is answered
directly and neither cached nor delegated.

### `QueryNormalizer` (utility)

`static String normalize(String raw)` — strip, collapse internal whitespace,
lower-case (`Locale.ROOT`), apply the whole-token exonym→endonym alias map
(`research.md` §5). `static Map<String,String> aliases()` exposes the map for
tests / docs.

### `CoordinateQuery` (utility)

`static Optional<PlaceCandidate> parse(String raw)` — recognises
`"<lat><sep><lon>"` (optional sign, `.` decimal, `,` or whitespace separator);
returns a `PlaceCandidate` with `source = "direct-input"`, `id` derived from the
rounded pair, empty `country` / `adminRegion`. Out-of-range → `Optional.empty()`
(the string was not a valid coordinate, fall through to geocoding).

### `CandidateRanking` (utility)

`static List<PlaceCandidate> rank(PlaceQuery query, List<PlaceCandidate> raw)` —
the total order of `research.md` §6 (exact normalised-name match first, then a
stable sort preserving provider order, then `id` tie-break).

### `FixtureGeocoder` (class, `Geocoder`)

`FixtureGeocoder(Map<String,List<PlaceCandidate>> byNormalizedQuery)` — pure,
no I/O. `geocode` looks up `query.normalized()`, ranks the hit list, wraps it.
A test-scope `FixtureGeocoderLoader` parses `geo/src/test/resources/geocode/
fixtures.json` into the map.

## `com.celestia.geo.time`

### `TimeZoneResolver` (interface)

```java
ZoneResolution resolve(double latitude, double longitude);
```

### `ZoneResolution` (record)

`(ZoneId zone, boolean approximated, String datasetVersion)` — `approximated` is
true when no polygon contained the point and the `Etc/GMT` longitude fallback was
used (`research.md` §2).

### `TimeshapeTimeZoneResolver` (class, `TimeZoneResolver`)

Wraps a lazily-initialised singleton `timeshape` engine. `resolve` →
`engine.query(lat, lon)`; present → `ZoneResolution(zone, false, version)`;
empty → `ZoneResolution(Etc/GMT±h, true, version)` with
`h = Math.round(longitude / 15.0)` (note the `Etc/GMT` inverted sign). No
`net.iakovlev` type appears in any public member (ArchUnit-enforced).

### `LocalToUtc` (utility)

```java
static InstantResolution resolve(LocalDate date, LocalTime time, ZoneId zone);
```

- `time == null` → use `LocalTime.NOON`, add `TIME_NOT_KNOWN`.
- `zone.getRules().getValidOffsets(ldt)`:
  - size 1 → normal.
  - size 0 → `DST_GAP`; instant = `ldt.atZone(zone).toInstant()` (forward shift).
  - size 2 → `DST_FOLD`; instant = `ldt.atZone(zone).withEarlierOffsetAtOverlap()
    .toInstant()`.
- `offsetApplied` = the `ZoneOffset` of the resolved `ZonedDateTime`.
- `tzdbVersion` = `ZoneRulesProvider.getVersions(zone.getId()).lastKey()`.

### `InstantResolution` (record)

`(Instant instant, ZoneOffset offsetApplied, EnumSet<BirthMomentFlag> flags,
String tzdbVersion)` — compact ctor copies the `EnumSet`.

## `com.celestia.geo` (root)

### `BirthMomentFlag` (enum)

`TIME_NOT_KNOWN`, `DST_GAP`, `DST_FOLD`, `ZONE_APPROXIMATED`, `POLAR_LATITUDE`,
`ZONE_CONFLICT`.

### `DatasetVersions` (record)

`(String tzdbVersion, String timezoneBoundaryVersion)` — recorded on every
`ResolvedBirth` (FR-016).

### `ResolvedBirth` (record / aggregate)

| Field | Type | Meaning |
|---|---|---|
| `localDateTime` | `LocalDateTime` | the civil birth date + time (noon if unknown) |
| `zoneId` | `ZoneId` | the zone used (geocoded, unless approximated) |
| `instant` | `Instant` | the UTC instant |
| `offsetApplied` | `ZoneOffset` | the offset at `instant` |
| `latitude` / `longitude` | `double` | from the chosen candidate |
| `placeLabel` | `String` | `PlaceCandidate.displayName` |
| `country` | `String` | `PlaceCandidate.country` |
| `geocodeSource` | `String` | `PlaceCandidate.source` |
| `statedZoneId` | `ZoneId` (nullable) | the user-stated zone, if any and if it differed |
| `birthTimeKnown` | `boolean` | false ⇔ `TIME_NOT_KNOWN` |
| `flags` | `EnumSet<BirthMomentFlag>` | all flags raised |
| `versions` | `DatasetVersions` | `tzdb` + `timeshape` dataset versions |

- `BirthData birthData()` → `new BirthData(instant, latitude, longitude)`.
- **Not carried**: `place_query` (the raw user text) and `name` — these
  `birth_data` columns are not place-and-time-derived; the caller (SPEC-010)
  threads them to persistence directly (FR-012).
- Compact ctor: non-null instant / zone / label / versions; `|latitude| <= 90`,
  `|longitude| <= 180`; `birthTimeKnown == !flags.contains(TIME_NOT_KNOWN)`;
  `statedZoneId != null` ⇔ `flags.contains(ZONE_CONFLICT)`; `flags` copied.

### `BirthMomentResolver` (class)

```java
public BirthMomentResolver(TimeZoneResolver zones, LocalToUtc /* static */ ...);
// or: BirthMomentResolver(TimeZoneResolver zones)  — LocalToUtc is static

public ResolvedBirth resolve(
    PlaceCandidate place, LocalDate birthDate,
    LocalTime birthTime /* nullable */, ZoneId statedZone /* nullable */);
```

Steps:

1. `ZoneResolution zr = zones.resolve(place.latitude(), place.longitude())`.
2. `EnumSet<BirthMomentFlag> flags = ...`; if `zr.approximated()` add
   `ZONE_APPROXIMATED`.
3. `ZoneId effective = zr.zone()`; if `statedZone != null && !statedZone.equals
   (effective)` add `ZONE_CONFLICT` (keep `effective` = geocoded; record
   `statedZone`).
4. `if (Math.abs(place.latitude()) >= POLAR_LIMIT_DEG)` add `POLAR_LATITUDE`
   (`POLAR_LIMIT_DEG` aligned with SPEC-002's constant, cite ADR-0004).
5. `InstantResolution ir = LocalToUtc.resolve(birthDate, birthTime, effective)`;
   merge `ir.flags()`.
6. build `ResolvedBirth` (local = `birthDate.atTime(birthTime != null ? birthTime
   : NOON)`, versions = `(ir.tzdbVersion(), zr.datasetVersion())`).

Pure — no provider beyond `TimeZoneResolver`, no clock. Deterministic: equal
inputs ⇒ equal `ResolvedBirth` (value equality).

## Cross-module invariants (property / golden / determinism tests)

1. `ResolvedBirth.birthData()` is exactly the `(instant, lat, lon)` a natal chart
   for that birth would consume (SC-001, feeds SPEC-001).
2. For every corpus entry, `resolve(...)` reproduces `expected.birth_utc` to the
   second and `expected.zone_id` (SC-001, SC-002); the independent
   `zoneinfo` + `timezonefinder` computation agrees.
3. A cache hit on `CachingGeocoder` returns a `GeocodeResult` **equal** to the
   first call's and makes **no** delegate call (SC-003) — asserted with a
   counting fake delegate.
4. Every DST gap / fold / unknown-time corpus entry has the matching flag; none
   is silently resolved (SC-004).
5. `CandidateRanking.rank` is a total order — idempotent, and independent of the
   input permutation for equal-`id`-free inputs (property test).
6. `QueryNormalizer.normalize` is idempotent and maps every alias key to its
   endonym; `PlaceQuery.of(a).normalized().equals(PlaceQuery.of(b).normalized())`
   for spelling variants that share an alias target.
7. `TimeshapeTimeZoneResolver` never exposes a `net.iakovlev` type (ArchUnit).
8. `BirthMomentResolver.resolve` is deterministic (equal inputs ⇒ equal
   `ResolvedBirth`).
