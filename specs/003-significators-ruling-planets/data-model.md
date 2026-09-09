# Phase 1 Data Model: Significators & Ruling Planets (SPEC-003)

All types are immutable value objects. Reuses SPEC-001/002: `Graha`, `Sign`,
`Nakshatra`, `LordChain`, `KpLordage`, `Accuracy`, `EngineVersion`, `BirthData`,
`NatalChart`, `PositionProvider`, `HouseProvider`.

## `ephemeris` module

### `SunriseProvider` (interface)

```java
Optional<Instant> sunriseBefore(Instant judgmentInstant, double latitude, double longitude);
```

- Returns the latest local sunrise at or before `judgmentInstant`, or empty if the
  Sun does not rise on that day at that latitude (polar).
- Deterministic, thread-safe; no Swiss Ephemeris type in the signature.
- Latitude / longitude validated as in `BirthData` (`|lat| <= 90`, `|lon| <= 180`).

### `SwissEphemerisSunriseProvider`

`swe_rise_trans`, `SE_SUN`, `SE_CALC_RISE`, upper limb + standard refraction.
`de.thmac.swisseph` confined here.

## `core.judgement` module

### `Step` (enum)

| Constant | Rank | Meaning |
|----------|------|---------|
| `STAR_OF_OCCUPANT` | 1 | graha is in the star of a (effective) occupant of the house |
| `OCCUPANT` | 2 | graha is an (effective) occupant of the house |
| `STAR_OF_OWNER` | 3 | graha is in the star of the house owner |
| `OWNER` | 4 | graha is the house owner |

`int rank()` returns 1..4.

### `Significator` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `graha` | `Graha` | the significating graha |
| `house` | `int` | 1..12 |
| `steps` | `Set<Step>` | the steps that qualified it (non-empty, immutable) |

`Step strongestStep()` = the lowest-ranked step present.

### `HouseSignificators` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `house` | `int` | 1..12 |
| `significators` | `List<Significator>` | de-duplicated, ordered by `strongestStep().rank()` then `Graha` ordinal |

`Set<Step> stepsFor(Graha)` ; `boolean signifies(Graha)`.

### `GrahaSignificators` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `graha` | `Graha` | |
| `houses` | `Map<Integer, Set<Step>>` | house → steps, immutable |

`boolean signifies(int house)`.

### `NodeAgency` (record)

For `RAHU` or `KETU`:

| Field | Type | Meaning |
|-------|------|---------|
| `node` | `Graha` | `RAHU` or `KETU` |
| `conjunctGrahas` | `Set<Graha>` | non-node grahas in the same bhava |
| `signLord` | `Graha` | lord of the sign the node occupies |
| `starLord` | `Graha` | nakshatra lord of the node's longitude |
| `agents` | `Set<Graha>` | `conjunctGrahas ∪ {signLord, starLord}` |

### `SignificatorTable` (aggregate)

`static SignificatorTable of(NatalChart chart)`.

| Accessor | Returns |
|----------|---------|
| `houseSignificators(int house)` | `HouseSignificators` |
| `grahaSignificators(Graha)` | `GrahaSignificators` |
| `nodeAgency(Graha node)` | `NodeAgency` |
| `engineVersion()` | `EngineVersion` (from the chart) |

Invariants:
- `grahaSignificators(g).signifies(h)` ⇔ `houseSignificators(h).signifies(g)`
  (SC-002) — the per-graha view is the transpose.
- every `Significator.steps` is non-empty; ordering is stable and deterministic.
- `houseSignificators(h).significators` has no duplicate `graha`.

### `RpSource` (enum)

`LAGNA_SIGN`, `LAGNA_STAR`, `LAGNA_SUB`, `MOON_SIGN`, `MOON_STAR`, `MOON_SUB`,
`DAY_LORD`, `NODE`.

### `RulingPlanet` (record)

`(Graha graha, Set<RpSource> sources)` — `sources` non-empty, immutable.

### `KpWeekday` (enum + resolution)

`SUNDAY..SATURDAY`, each with `Graha lord()`. `static KpWeekday resolve(Instant
judgmentInstant, double latitude, double longitude, SunriseProvider)` → the KP
weekday (sunrise boundary), plus a `boolean fallback` when there is no sunrise.

### `RulingPlanets` (record / aggregate)

| Field | Type | Meaning |
|-------|------|---------|
| `judgment` | `BirthData` | the judgment instant + place (echoed) |
| `planets` | `Set<RulingPlanet>` | the ruling planets, immutable |
| `dayLord` | `Graha` | |
| `weekday` | `KpWeekday` | |
| `dayLordFallback` | `boolean` | true when the civil-day fallback was used (FR-014) |
| `includeSubLords` | `boolean` | the flag value used |
| `accuracy` | `Accuracy` | mirrors the Moon position for the instant |
| `engineVersion` | `EngineVersion` | |

`Set<RpSource> sourcesFor(Graha)` ; `boolean isRuling(Graha)`.

### `RulingPlanetsFactory`

```java
RulingPlanets at(BirthData judgment);              // instance, wired with 3 providers
static RulingPlanets compute(                      // pure core
    BirthData judgment, double ascendantLongitude, double moonLongitude,
    KpWeekday weekday, boolean weekdayFallback, Accuracy accuracy,
    EngineVersion engineVersion, Options options);
record Options(boolean includeSubLords, boolean includeNodeAspects) {
    static Options defaults();  // includeSubLords = true, includeNodeAspects = false
}
```

## Cross-module invariants (property / golden tests)

1. `grahaSignificators` is the exact transpose of the twelve `houseSignificators`
   (SC-002).
2. `SignificatorTable.of` is deterministic: equal `NatalChart` ⇒ equal table.
3. `RulingPlanetsFactory.compute` is deterministic.
4. `KpWeekday.resolve` at `sunrise + ε` and `sunrise − ε` differ by one day
   (SC-004).
