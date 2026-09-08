# Contract: `core` natal-chart API

Consumers: SPEC-003 (significators), SPEC-004 (dasha), SPEC-005 (horary), and
chart display.

## `NatalChartFactory`

```java
public final class NatalChartFactory {
    public static NatalChart assemble(BirthData birthData, EphemerisResult positions, HouseResult houses);

    public NatalChartFactory(PositionProvider positionProvider, HouseProvider houseProvider);
    public NatalChart cast(BirthData birthData);
}
```

- `assemble(...)` is **pure** — no I/O; the unit-testable core. Preconditions:
  `positions` and `houses` were computed for the same `birthData`.
- `cast(...)` wires the two providers; propagates `PlacidusUndefinedException` /
  `IllegalArgumentException` from `HouseProvider`.

## `NatalChart`

```java
GrahaPosition position(Graha g);
Cusp cusp(int house);            // house in 1..12
Graha cuspSubLord(int house);    // == cusp(house).lordChain().subLord()
AnglePoint ascendant();
AnglePoint midheaven();
HousePlacement placement(Graha g);
List<Cusp> cusps();              // 12, house order, immutable
Map<Graha, HousePlacement> placements();
Ayanamsa ayanamsa();             // KP_NEW
Accuracy accuracy();
EngineVersion engineVersion();
```

### Invariants

| Invariant | |
|-----------|--|
| `cusps().size() == 12`, houses 1..12 in order | |
| `cusp(1).longitude() == ascendant().longitude()` | bit-identical (SC-003) |
| `cuspSubLord(h) == cusp(h).lordChain().subLord()` for all `h` | FR-005 |
| `placements().keySet()` == all nine `Graha` | |
| `placement(g).bhava() ∈ 1..12`, `placement(g).rasiHouse() ∈ 1..12` | |
| `placement(g).bhava()` == unique `n` with `position(g).longitude() ∈ [cusp(n), cusp(n+1))` on the circle | SC-004 |
| equal `BirthData` ⇒ equal `NatalChart` | SC-005 |

## Rules (also in `core/REFERENCES.md`)

### Bhava (cusp-to-cusp)

Graha in **bhava _n_** iff its sidereal longitude is in the forward arc
`[cusp[n], cusp[n+1])` around the circle (half-open, boundary to higher longitude;
`cusp[13] = cusp[1]`). Not the Sripati midpoint method. Source: K. S. Krishnamurti,
*KP Readers* (cuspal system).

### Rasi house (whole sign)

`rasiHouse = 1 + ((graha.sign.ordinal() − ascendant.sign.ordinal()) mod 12)`.
Ascendant's sign is house 1. Source: standard whole-sign Rasi layout.

## Supporting types (`com.celestia.core.chart`)

`Cusp`, `AnglePoint`, `HousePlacement`, `NatalChart`, `Bhavas` (assignment helper).
