# Contract: `geo` geocoding + timezone API

Consumers: the LLM agent tool `geocodePlace(free-text place)` (SPEC-014+), the
onboarding FSM (SPEC-010 — normalises, geocodes, shows candidates, resolves the
pick, echoes the local time back), and the persistence layer (SPEC-008 —
`birth_data`, `geocode_cache`).

Pure — no Spring, no DB, no network, **no wall clock**. No `de.thmac.swisseph`
and no `net.iakovlev` type in any public member (both ArchUnit-enforced).

## Geocoding — `com.celestia.geo.place`

```java
public record PlaceQuery(String raw, String normalized) {
    public static PlaceQuery of(String raw);        // normalized = QueryNormalizer.normalize(raw)
}

public record PlaceCandidate(
    String id, String displayName, String country, String adminRegion,
    double latitude, double longitude, String source) {}

public record GeocodeResult(PlaceQuery query, List<PlaceCandidate> candidates) {}  // ranked, immutable

public interface Geocoder {
    GeocodeResult geocode(PlaceQuery query);        // never null; empty match -> empty candidates
}

public interface GeocodeCache {
    Optional<GeocodeResult> get(String queryNorm);
    void put(String queryNorm, GeocodeResult result);
}

public final class CachingGeocoder implements Geocoder {
    public CachingGeocoder(Geocoder delegate, GeocodeCache cache);
}

public final class InMemoryGeocodeCache implements GeocodeCache { public InMemoryGeocodeCache(); }

public final class FixtureGeocoder implements Geocoder {
    public FixtureGeocoder(Map<String, List<PlaceCandidate>> byNormalizedQuery);
}

public final class QueryNormalizer {
    public static String normalize(String raw);
    public static Map<String, String> aliases();
}

public final class CoordinateQuery {
    public static Optional<PlaceCandidate> parse(String raw);   // "18.52, 73.85" -> direct-input candidate
}

public final class CandidateRanking {
    public static List<PlaceCandidate> rank(PlaceQuery query, List<PlaceCandidate> raw);
}
```

`geocodePlace(text)` = `geocoder.geocode(PlaceQuery.of(text)).candidates()` where
`geocoder` is a `CachingGeocoder(delegate, cache)`.

| Rule | Behaviour |
|------|-----------|
| normalisation | trim, collapse whitespace, case-fold, whole-token alias map (`research.md` §5) |
| coordinate query | `"<lat>, <lon>"` → one `direct-input` candidate; bypasses normalisation, ranking, cache |
| ranking | exact normalised-name match first, then stable provider order, then `id` tie-break; never collapsed to one |
| cache | keyed on `PlaceQuery.normalized()`; a hit returns an **equal** `GeocodeResult` with **no** delegate call |
| empty match | empty `candidates` list, not an exception |
| the HTTP adapter | **not in `geo`** — `OpenCageGeocoder` lands with SPEC-009/010 (ADR-0013) |

## Timezone — `com.celestia.geo.time`

```java
public interface TimeZoneResolver {
    ZoneResolution resolve(double latitude, double longitude);
}

public record ZoneResolution(ZoneId zone, boolean approximated, String datasetVersion) {}

public final class TimeshapeTimeZoneResolver implements TimeZoneResolver {
    public TimeshapeTimeZoneResolver();             // lazy singleton engine
}

public final class LocalToUtc {
    public static InstantResolution resolve(LocalDate date, LocalTime time /* nullable */, ZoneId zone);
}

public record InstantResolution(
    Instant instant, ZoneOffset offsetApplied,
    EnumSet<BirthMomentFlag> flags, String tzdbVersion) {}
```

| Input | Result |
|-------|--------|
| point inside a polygon | `ZoneResolution(zone, false, datasetVersion)` |
| point with no polygon | `ZoneResolution(Etc/GMT±round(lon/15), true, datasetVersion)` |
| `getValidOffsets(ldt)` size 1 | normal instant |
| size 0 (DST gap) | forward-shifted instant, `DST_GAP` |
| size 2 (DST fold) | earlier-offset instant, `DST_FOLD` |
| `time == null` | noon local, `TIME_NOT_KNOWN` |

## Composition — `com.celestia.geo`

```java
public enum BirthMomentFlag {
    TIME_NOT_KNOWN, DST_GAP, DST_FOLD, ZONE_APPROXIMATED, POLAR_LATITUDE, ZONE_CONFLICT
}

public record DatasetVersions(String tzdbVersion, String timezoneBoundaryVersion) {}

public record ResolvedBirth(
    LocalDateTime localDateTime, ZoneId zoneId, Instant instant, ZoneOffset offsetApplied,
    double latitude, double longitude, String placeLabel, String country, String geocodeSource,
    ZoneId statedZoneId /* nullable */, boolean birthTimeKnown,
    EnumSet<BirthMomentFlag> flags, DatasetVersions versions) {

    public BirthData birthData();                   // new BirthData(instant, latitude, longitude)
}

public final class BirthMomentResolver {
    public BirthMomentResolver(TimeZoneResolver zones);
    public ResolvedBirth resolve(
        PlaceCandidate place, LocalDate birthDate,
        LocalTime birthTime /* nullable */, ZoneId statedZone /* nullable */);
}
```

`resolve(...)` — `research.md` §7 and data-model §`BirthMomentResolver`:

| Step | Rule |
|------|------|
| zone | `zones.resolve(place.lat, place.lon)`; `approximated` → `ZONE_APPROXIMATED` |
| stated zone | differs from geocoded → `ZONE_CONFLICT`; geocoded zone wins; `statedZone` recorded |
| polar | `|lat| ≥` the SPEC-002 constant (ADR-0004) → `POLAR_LATITUDE`; not blocked here |
| instant | `LocalToUtc.resolve(birthDate, birthTime, effectiveZone)`; flags merged |
| versions | `(tzdbVersion, timeZoneBoundaryVersion)` on the result (FR-016) |
| determinism | equal inputs ⇒ equal `ResolvedBirth` |

## Versioning

`geo` has no `EngineVersion`. Reproducibility rests on `DatasetVersions`
(`tzdb` + `timeshape`) carried on `ResolvedBirth` and (SPEC-008) persisted. A
dataset bump changes the recorded version and the corpus flags every affected
instant (SC-001, FR-016). The alias map, ranking rule, and gap/fold conventions
are documented data in `geo/REFERENCES.md`; a change there is a normal reviewed
edit.
