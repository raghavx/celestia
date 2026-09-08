# Phase 1 Data Model: Natal Chart & Placidus Cusps (SPEC-002)

All types are immutable value objects. Angles are ecliptic longitudes in degrees,
`[0, 360)`, sidereal (KP-New). Reuses SPEC-001: `Graha`, `Sign`, `Nakshatra`,
`LordChain`, `EngineVersion`, `Accuracy`, `EphemerisResult`, `GrahaPosition`.

## `ephemeris` module

### `BirthData` (record)

| Field | Type | Rules |
|-------|------|-------|
| `instant` | `Instant` | non-null; a moment on the UTC timeline |
| `latitude` | `double` | decimal degrees, + = North; `|latitude| <= 90` (else `IllegalArgumentException`) |
| `longitude` | `double` | decimal degrees, + = East; `|longitude| <= 180` (else `IllegalArgumentException`) |

The place-name → lat/lon and local-time → UTC steps are SPEC-007.

### `Angle` (enum)

`ASCENDANT`, `MIDHEAVEN`.

### `HouseSystem` (enum)

`PLACIDUS` — the only value (ADR-0004).

### `HouseResult` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `birthData` | `BirthData` | echoed input |
| `cuspLongitudes` | `double[12]` | sidereal longitudes of houses 1..12 (index 0 = house 1); `cuspLongitudes[0]` == the Ascendant, bit-identical |
| `angles` | `Map<Angle, Double>` | Ascendant and Midheaven longitudes, immutable |
| `houseSystem` | `HouseSystem` | always `PLACIDUS` |
| `accuracy` | `Accuracy` | mirrors the companion `EphemerisResult` for the same instant |
| `engineVersion` | `EngineVersion` | the rules + data identity |

Invariants: 12 finite longitudes in `[0, 360)`; consecutive forward arcs all > 0
(monotone around the circle with exactly one wrap); `angles.get(ASCENDANT)` ==
`cuspLongitudes[0]`.

### `HouseProvider` (interface)

```java
HouseResult houses(BirthData birthData);          // full Placidus chart
Map<Angle, Double> anglesOnly(BirthData birthData); // Ascendant + MC, defined at any latitude
```

- `houses(...)` throws `PlacidusUndefinedException` when `|latitude| >= polarLimit`
  (default 66.0°).
- Deterministic, thread-safe, no SE type in the signature (FR-014).

### `PlacidusUndefinedException` (unchecked)

`extends EphemerisException`. Message names the latitude and the limit.

## `core` module

### `Cusp` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `house` | `int` | 1..12 |
| `longitude` | `double` | sidereal, `[0, 360)` |
| `lordChain` | `LordChain` | the full KP lordage of this cusp |

`Graha subLord()` → `lordChain.subLord()` (the KP determinant, FR-005).

### `AnglePoint` (record)

| Field | Type |
|-------|------|
| `angle` | `Angle` |
| `longitude` | `double` |
| `lordChain` | `LordChain` |

### `HousePlacement` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `graha` | `Graha` | |
| `bhava` | `int` | 1..12, cusp-to-cusp (research.md §3) |
| `rasiHouse` | `int` | 1..12, whole signs from the Ascendant (research.md §4) |

### `NatalChart` (record / aggregate)

| Field | Type | Meaning |
|-------|------|---------|
| `birthData` | `BirthData` | |
| `positions` | `Map<Graha, GrahaPosition>` | from SPEC-001 |
| `cusps` | `List<Cusp>` | 12, house order |
| `ascendant` / `midheaven` | `AnglePoint` | |
| `placements` | `Map<Graha, HousePlacement>` | 9 |
| `ayanamsa` | `Ayanamsa` | `KP_NEW` |
| `accuracy` | `Accuracy` | |
| `engineVersion` | `EngineVersion` | |

Accessors: `Cusp cusp(int house)`, `Graha cuspSubLord(int house)`,
`HousePlacement placement(Graha)`, `GrahaPosition position(Graha)`.

Invariants:
- `cusps` has 12 entries, houses 1..12 in order.
- `cusp(1).longitude()` == `ascendant.longitude()`.
- `cuspSubLord(h)` == `cusp(h).lordChain().subLord()` for all `h`.
- `placements.keySet()` == all nine `Graha`.
- for every graha `g`, `placement(g).bhava()` == the `n` such that
  `position(g).longitude()` ∈ `[cusp(n), cusp(n+1))` on the circle.

### `Bhavas` (utility)

`static int bhavaOf(double longitude, double[] cuspLongitudes)` — the wrap-aware
half-open assignment; `static int rasiHouseOf(Sign grahaSign, Sign ascendantSign)`.

### `NatalChartFactory`

`static NatalChart assemble(BirthData, EphemerisResult, HouseResult)` — pure
assembly (no I/O), the unit-testable core.
`NatalChart cast(BirthData)` on an instance wired with a `PositionProvider` + a
`HouseProvider` — the convenience path.

## Cross-module invariants (property tests)

1. For a cast chart, every graha's `bhava` equals `Bhavas.bhavaOf(longitude,
   cuspLongitudes)`, and that is the unique `n` whose forward arc contains it.
2. `NatalChartFactory.assemble` is deterministic: equal inputs ⇒ equal `NatalChart`.
3. `cusp(1).longitude()` is bit-identical to the Ascendant across runs (SC-003).
