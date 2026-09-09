# Contract: `ephemeris` horary houses API

Consumer: `core.horary.HoraryChartFactory`. Mirrors `HouseProvider` /
`SunriseProvider`. No `de.thmac.swisseph` type in any public member (FR-016).

## `HoraryHouseProvider`

```java
public interface HoraryHouseProvider {
    HouseResult housesFor(BirthData judgment, double ascendantLongitude);
}
```

### Input

| Parameter | Rule |
|-----------|------|
| `judgment` | non-null; supplies the instant (→ obliquity, ayanamsa) and the latitude / longitude |
| `ascendantLongitude` | sidereal KP-New longitude for cusp 1; normalised to `[0, 360)` if slightly out |

### Output — `HouseResult`

- `cuspLongitudes.get(0)` is **bit-identical** to `ascendantLongitude` (after
  normalisation), and `angles.get(ASCENDANT)` equals it.
- The other eleven cusps are **Placidus**, sidereal, consistent with that
  Ascendant at `judgment.latitude()` for the judgment instant's obliquity and
  ayanamsa: convert the Ascendant to tropical (`+ ayanamsa`), find the RAMC by a
  closed-form inversion of the Ascendant formula (bisection fallback), call
  `swe_houses_armc`, convert the cusps back (`− ayanamsa`).
- `angles.get(MIDHEAVEN)` is the sidereal MC from the same computation.
- `houseSystem == PLACIDUS`.
- `birthData()` is `judgment` (the cusps are Ascendant-seeded, not
  instant-seeded — this `HouseResult` is only valid as a horary chart's houses).
- `accuracy` mirrors the instant (`REDUCED` outside the supported range);
  `engineVersion` carries the rules + data identity.

### Errors

- `|judgment.latitude()| ≥ polar limit` (66°, configurable) →
  `PlacidusUndefinedException` (same as a natal chart).
- null `judgment` or a backend init failure → `EphemerisException`.
- An instant outside the ephemeris data range still returns a result (analytic
  obliquity / Moshier).

### Guarantees

| Property | Guarantee |
|----------|-----------|
| Determinism | equal inputs ⇒ equal `HouseResult` |
| Thread-safety | safe to call concurrently (synchronized handle) |
| Purity | no network, no writes, no wall clock |
| No leakage | no Swiss Ephemeris type in the signature or exceptions |
| Round-trip | seeding the Ascendant a normal `swe_houses` cast produces reproduces that cast's twelve cusps to ≤ 1′ |

## Supporting types

`BirthData`, `HouseResult`, `Angle`, `HouseSystem`, `Accuracy`, `EngineVersion`,
`PlacidusUndefinedException` — all existing (SPEC-001 / SPEC-002).
