# Contract: `ephemeris` house API

Consumers: `core` (`NatalChartFactory`), and later chart-display code. Mirrors
`PositionProvider` from SPEC-001. No `de.thmac.swisseph` type in any public member
(FR-014).

## `HouseProvider`

```java
public interface HouseProvider {
    HouseResult houses(BirthData birthData);
    Map<Angle, Double> anglesOnly(BirthData birthData);
}
```

### Input — `BirthData(Instant instant, double latitude, double longitude)`

| Field | Rule |
|-------|------|
| `instant` | non-null; UTC timeline |
| `latitude` | `|latitude| <= 90` — else `IllegalArgumentException` |
| `longitude` | `|longitude| <= 180` — else `IllegalArgumentException` |

### `houses(...)` output — `HouseResult`

- `cuspLongitudes` — an immutable `List<Double>` of 12 finite values in `[0, 360)`,
  sidereal (KP-New), Placidus. (`List<Double>`, not `double[]`, so `HouseResult`
  has value equality — SC-005.)
- `cuspLongitudes.get(0)` (house 1) is **bit-identical** to `angles.get(ASCENDANT)`.
- `angles` contains `ASCENDANT` and `MIDHEAVEN`.
- Consecutive forward arcs `cuspLongitudes.get(n) → cuspLongitudes.get((n+1)%12)`
  are all strictly > 0 (one wrap through 360°).
- `houseSystem == PLACIDUS`; `accuracy` mirrors the positions for the same instant;
  `engineVersion` fully populated.

### `houses(...)` errors

| Condition | Result |
|-----------|--------|
| `|latitude| >= polarLimit` (default 66.0°) | `PlacidusUndefinedException` naming the latitude — thrown **before** any backend call |
| `birthData` null / `|lat|>90` / `|lon|>180` | `IllegalArgumentException` |
| backend initialisation failure | `EphemerisException` |

An instant outside 1800–2100 is **not** an error — cusps are analytic; the
result carries `Accuracy.REDUCED` (mirroring the positions).

### `anglesOnly(...)`

Returns just `ASCENDANT` and `MIDHEAVEN`, computed from the ARMC (sidereal time),
**not** via Placidus — so it is **defined at any latitude** below ±90°. Same input
validation for `|lat|>90` / `|lon|>180`; never throws `PlacidusUndefinedException`.

### Guarantees

| Property | Guarantee |
|----------|-----------|
| Determinism | equal `BirthData` ⇒ `equals`-equal `HouseResult`, every run and platform |
| Thread-safety | safe to call concurrently |
| Purity | no network, no writes, no wall clock; reads only bundled read-only data (and not even that for cusps) |
| No leakage | no Swiss Ephemeris type in any public signature, field, or exception |

## Supporting types (all in `com.celestia.ephemeris`)

`BirthData`, `Angle` (`ASCENDANT`, `MIDHEAVEN`), `HouseSystem` (`PLACIDUS`),
`HouseResult`, `PlacidusUndefinedException`.

## Versioning

`EngineVersion` changes if the house system, the ayanamsa, the cusp algorithm, or
the SE port version changes (as in SPEC-001).
