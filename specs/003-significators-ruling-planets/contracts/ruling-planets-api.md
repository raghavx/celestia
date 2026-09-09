# Contract: `core.judgement` ruling-planets API

Consumers: SPEC-005 (horary), event-timing in the LLM agent tools.

## `RulingPlanetsFactory`

```java
public final class RulingPlanetsFactory {
    public RulingPlanetsFactory(PositionProvider positions, HouseProvider houses,
                                SunriseProvider sunrise);
    public RulingPlanetsFactory(PositionProvider positions, HouseProvider houses,
                                SunriseProvider sunrise, RulingPlanets.Options options);

    public RulingPlanets at(BirthData judgment);

    public static RulingPlanets compute(
        BirthData judgment, double ascendantLongitude, double moonLongitude,
        KpWeekday weekday, boolean weekdayFallback, Accuracy accuracy,
        EngineVersion engineVersion, RulingPlanets.Options options);
}
```

### `at(judgment)`

- Ascendant from `houses.anglesOnly(judgment)` (works at any latitude);
  Moon from `positions.positions(judgment.instant())`.
- `KpWeekday.resolve(judgment.instant(), lat, lon, sunrise)` for the day lord.
- Propagates `IllegalArgumentException` for out-of-range lat/lon.

### `compute(...)` — pure

Builds the ruling planets from already-resolved inputs. Deterministic.

### `RulingPlanets` output

| Item | Rule |
|------|------|
| lagna sign / star / sub lord | from `KpLordage.chainFor(ascendantLongitude)` |
| moon sign / star / sub lord | from `KpLordage.chainFor(moonLongitude)` |
| sub lords (`LAGNA_SUB`, `MOON_SUB`) | included only when `options.includeSubLords()` (default true) |
| `DAY_LORD` | `weekday.lord()` |
| `dayLordFallback` | `weekdayFallback` — true when there was no sunrise and the civil weekday was used (FR-014) |
| `NODE` | Rahu / Ketu added when its sign lord or star lord is already ruling, or it shares the sign or nakshatra of the Moon or the Ascendant (research.md §3). `options.includeNodeAspects()` (default false) is the deferred extension point. |
| each `RulingPlanet` | carries the non-empty set of `RpSource`s that put it in the list |
| `accuracy` | mirrors the Moon position for the instant |

### Guarantees

- Deterministic across runs and platforms.
- No Swiss Ephemeris type in this API.
- `isRuling(g)` ⇔ `g` appears in `planets` with a non-empty source set.

## Supporting types

`RpSource`, `RulingPlanet`, `KpWeekday` (weekday ↔ day lord, sunrise resolution),
`RulingPlanets.Options`.
