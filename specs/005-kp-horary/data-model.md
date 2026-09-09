# Phase 1 Data Model: KP Horary 1–249 (SPEC-005)

All `core` types are in `com.celestia.core.horary`, immutable, pure. The 249
boundaries are exact `BigFraction` degrees (as `Span` in SPEC-001).

## `HoraryArc` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `number` | `int` | 1..249 |
| `start` | `BigFraction` | inclusive sidereal longitude, degrees |
| `end` | `BigFraction` | exclusive sidereal longitude, degrees |
| `sign` | `Sign` | the single sign the arc lies in |
| `lordChain` | `LordChain` | the KP lord chain of the arc's midpoint |

Compact constructor: `1 ≤ number ≤ 249`; `start < end`; `Sign.at(midpoint) ==
sign`. Methods: `double startDeg()`, `double endDeg()`, `double midpointDeg()`,
`Graha subLord()` (= `lordChain.subLord()`), `boolean contains(double
longitudeDeg)` (half-open).

## `Horary249` (utility)

```java
public final class Horary249 {
    public static List<HoraryArc> arcs();     // the 249, in number order, cached
    public static HoraryArc arc(int number);  // 1..249, else IllegalArgumentException
    public static int count();                // 249
}
```

- `arcs()` = `VimshottariPartition.subDivisions()` split at `{30, 60, …, 330}`
  wherever a span strictly crosses one, sorted by `start`, numbered from 1.
- **Invariants** (property / snapshot tests): `arcs().size() == 249`;
  `arcs().get(0).start == 0`; `arcs().get(248).end == 360`;
  `arcs().get(i).end == arcs().get(i+1).start` (exact); every arc's `subLord`
  equals the `subDivisions()` span it came from; every arc lies in one sign.

## `HoraryChartFactory`

```java
public final class HoraryChartFactory {
    public HoraryChartFactory(PositionProvider positions, HoraryHouseProvider houses);

    public NatalChart cast(int number, BirthData judgment);        // number 1..249
    public AnglePoint ascendant(int number);                       // clock-independent

    public static NatalChart assemble(
        int number, BirthData judgment, EphemerisResult positions, HouseResult horaryHouses);
}
```

### `cast(number, judgment)`

- `ascLon = Horary249.arc(number).midpointDeg()`.
- planets = `positions.positions(judgment.instant())`.
- houses = `houses.housesFor(judgment, ascLon)` — cusp 1 = `ascLon`.
- returns `NatalChartFactory.assemble(judgment, planets, houses)`.
- propagates `PlacidusUndefinedException` for a polar judgment latitude.
- `Accuracy` mirrors the judgment-instant positions (`REDUCED` out of range).

### `ascendant(number)`

`new AnglePoint(Angle.ASCENDANT, Horary249.arc(number).midpointDeg(),
Horary249.arc(number).lordChain())`.

### `assemble(...)` — pure

Validates `horaryHouses.cusp(1) == Horary249.arc(number).midpointDeg()` (to
`Double.compare`), then delegates to `NatalChartFactory.assemble`. Deterministic.

## `HoraryRulingPlanets` (utility)

```java
public final class HoraryRulingPlanets {
    public static RulingPlanets at(
        int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise);
    public static RulingPlanets at(
        int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise,
        RulingPlanets.Options options);
}
```

`RulingPlanetsFactory.compute(judgment, arc(number).midpointDeg(),
moonLongitude, rahuLongitude, weekday, weekdayFallback, moonAccuracy,
engineVersion, options)` — the lagna lords come from the **number's** Ascendant,
the Moon lords and day lord from the judgment moment.

## `HoraryHouseProvider` (`com.celestia.ephemeris`)

```java
public interface HoraryHouseProvider {
    HouseResult housesFor(BirthData judgment, double ascendantLongitude);
}
```

| Item | Rule |
|------|------|
| `judgment` | instant + latitude + longitude of the judgment moment |
| `ascendantLongitude` | sidereal (KP-New) longitude to place at cusp 1, `[0, 360)` |
| cusp 1 | set bit-identically to `ascendantLongitude` |
| cusps 2–12 | Placidus, from the RAMC that yields `ascendantLongitude` at
  `judgment.latitude()` with the instant's obliquity, converted sidereal↔tropical
  via the instant's ayanamsa (research.md §3, §4) |
| `birthData` | the returned `HouseResult.birthData()` is `judgment` (its cusps are
  Ascendant-seeded, not instant-seeded — documented) |
| polar | `|latitude| ≥ polar limit` → `PlacidusUndefinedException` |
| accuracy / version | mirror the instant (obliquity / ayanamsa); `REDUCED` out of range |
| no leakage | no `de.thmac.swisseph` type in the signature or exceptions |

Impl: `SwissEphemerisHoraryHouseProvider` (synchronized handle, mirrors
`SwissEphemerisHouseProvider`).

## Cross-module invariants (property / golden tests)

1. `Horary249.arcs()` — 249 arcs, exact tiling of `[0°, 360°)`, one sign each,
   sub lord matching `VimshottariPartition.subDivisions()` (SC-001).
2. `HoraryChartFactory.cast(n, j).cusp(1) == HoraryChartFactory.ascendant(n)
   .longitude()` for every `n` (SC-002, FR-007).
3. `HoraryHouseProvider.housesFor` round-trips `swe_houses`: seeding the Ascendant
   that a normal cast produces reproduces that cast's twelve cusps to ≤ 1′.
4. A horary chart is a valid `NatalChart` — `SignificatorTable.of(horaryChart)`
   and `RulingPlanetsFactory` accept it (SC-003).
5. `HoraryChartFactory.cast` / `assemble` are deterministic (equal inputs ⇒ equal
   `NatalChart`, which has value equality).
