# Contract: `core.horary` KP horary API

Consumers: the LLM agent tool `castHoraryChart(number, judgment datetime,
lat/lon)`, SPEC-006 (judgement rules), SPEC-008 (persistence: `chart` kind
`HORARY`, `horary_query`).

No `de.thmac.swisseph` type in any public member. Pure — no I/O, no wall clock.

## `Horary249`

```java
public final class Horary249 {
    public static List<HoraryArc> arcs();     // 249, in number order, cached
    public static HoraryArc arc(int number);  // 1..249
    public static int count();                // 249
}
```

- `arcs()` = `VimshottariPartition.subDivisions()` (243 exact sub-spans) split at
  each `30k°` (`k = 1..11`) a span **strictly crosses**, sorted by `start`,
  numbered from 1.
- `arc(number)` with `number ∉ [1, 249]` → `IllegalArgumentException`.
- Invariants: `count() == 249`; `arcs().get(0).start() == 0` (exact);
  `arcs().get(248).end()` == `360` (exact); `arc[n].end() == arc[n+1].start()`
  (exact `BigFraction`); each arc lies in one `Sign`; each arc's `subLord()` ==
  the sub lord of the `subDivisions()` span it derives from.

## `HoraryArc`

```java
public record HoraryArc(
    int number, BigFraction start, BigFraction end, Sign sign, LordChain lordChain) {

    double startDeg();
    double endDeg();
    double midpointDeg();
    Graha subLord();                     // == lordChain.subLord()
    boolean contains(double longitudeDeg);   // half-open [startDeg, endDeg)
}
```

`lordChain` is `KpLordage.chainFor(midpointDeg())` — the sub lord is constant over
the whole arc, so it is the arc's KP determinant regardless of the midpoint.

## `HoraryChartFactory`

```java
public final class HoraryChartFactory {
    public HoraryChartFactory(PositionProvider positions, HoraryHouseProvider houses);

    public NatalChart cast(int number, BirthData judgment);
    public AnglePoint ascendant(int number);

    public static NatalChart assemble(
        int number, BirthData judgment, EphemerisResult positions, HouseResult horaryHouses);
}
```

### `cast(number, judgment)`

| Item | Rule |
|------|------|
| `number ∉ [1, 249]` | `IllegalArgumentException` |
| cusp 1 | `Horary249.arc(number).midpointDeg()` — **not** the instant/place Ascendant |
| cusps 2–12, MC | `houses.housesFor(judgment, cusp1)` (Placidus, RAMC-seeded) |
| planets | `positions.positions(judgment.instant())` — sidereal KP, identical to a natal chart for that instant |
| bhava / rasi / cuspal sub lords | from `NatalChartFactory.assemble` unchanged |
| polar `judgment` latitude | `PlacidusUndefinedException` |
| out-of-range instant | chart still cast, `Accuracy.REDUCED` |
| result | a `NatalChart` — consumable by `SignificatorTable.of(...)` and (with a birth chart) SPEC-004 dasha |

### `ascendant(number)` — clock-independent

`new AnglePoint(Angle.ASCENDANT, Horary249.arc(number).midpointDeg(),
Horary249.arc(number).lordChain())`.

### `assemble(...)` — pure

Asserts `horaryHouses.cusp(1)` equals the number's Ascendant, then
`NatalChartFactory.assemble(judgment, positions, horaryHouses)`. Deterministic:
equal inputs ⇒ equal `NatalChart`.

## `HoraryRulingPlanets`

```java
public final class HoraryRulingPlanets {
    public static RulingPlanets at(
        int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise);
    public static RulingPlanets at(
        int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise,
        RulingPlanets.Options options);
}
```

The SPEC-003 ruling planets with the lagna sign / star / sub lords taken from the
**number's** Ascendant (`Horary249.arc(number).midpointDeg()`) and the Moon lords
+ day lord from the judgment moment. No change to `RulingPlanetsFactory`.

## Versioning

`EngineVersion` (from the position result) is carried on every cast chart. It
changes if the 249 table derivation, the Ascendant-midpoint rule, the RAMC
inversion, or any upstream rule changes (`core/REFERENCES.md` bump procedure).
