# Phase 1 Data Model: Daily Prediction Ruleset (SPEC-006)

All types are in `com.celestia.core.prediction`, immutable, pure. House numbers
are 1..12; graha references are SPEC-001 `Graha`.

## `Matter` (enum)

One row per `research.md` §1. Each constant carries:

| Accessor | Type | Meaning |
|----------|------|---------|
| `favourable()` | `Set<Integer>` | houses that must be well signified (non-empty) |
| `obstructive()` | `Set<Integer>` | negation houses (disjoint from `favourable()`) |
| `source()` | `String` | the KP citation |
| `key()` | `String` | lower-case stable key for the tool (`"marriage"`, …) |

Compact / static init validates: `favourable` non-empty, `favourable ∩
obstructive == ∅`, all houses in 1..12.

## `HouseGroups` (utility)

```java
public final class HouseGroups {
    public static Matter fromKey(String key);      // else IllegalArgumentException
    public static Set<Matter> all();
}
```

`explainHouseGrouping(key)` = `HouseGroups.fromKey(key)` then read `favourable()`
/ `obstructive()` / `source()`.

## `Verdict` (enum)

`FAVOURABLE`, `MIXED`, `UNFAVOURABLE`, `QUIET`.

## `TransitBody` (enum)

`MOON`, `SUN`.

## `ActivatedHouse` (record)

`(int house, int strength, Set<Graha> lords)` — a natal house signified by ≥ 1
running dasha lord. `strength` = `lords.size()`, in 1..5; `house` in 1..12.

## `DashaSignificators` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `running` | `RunningDasha` | the five running lords at the reference instant (SPEC-004) |
| `significationsByLord` | `Map<Graha, GrahaSignificators>` | for each running lord, its natal significations (SPEC-003) |
| `activated` | `List<ActivatedHouse>` | the union, ordered by house; `strength(h)` helper |
| `lordChangesWithinDay` | `boolean` | a running lord differs at `t ± 12 h` |

`int strengthOf(int house)` — 0 if not activated.

## `TransitContribution` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `moonChain` | `LordChain` | transiting Moon's KP lord chain at the reference instant |
| `sunChain` | `LordChain` | transiting Sun's KP lord chain |
| `moonSupports` | `Set<Integer>` | houses whose natal significators include `moonChain.subLord()` |
| `sunSupports` | `Set<Integer>` | likewise for the Sun |
| `moonSubLordChangesWithinDay` | `boolean` | the Moon's sub lord differs at `t ± 12 h` |

`Set<Integer> supported()` = `moonSupports ∪ sunSupports`.

## `MatterVerdict` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `matter` | `Matter` | |
| `verdict` | `Verdict` | |
| `favourableHit` | `Set<Integer>` | activated favourable houses (`favActive`) |
| `obstructiveHit` | `Set<Integer>` | activated obstructive houses (`obsActive`) |
| `lords` | `Set<Graha>` | running lords that activated the hit houses |
| `transits` | `Set<TransitBody>` | bodies that triggered a favourable hit |

Every field is the raw material for reconstructing `verdict` by hand
(`research.md` §5, §6).

## `DailyPrediction` (record / aggregate)

| Field | Type | Meaning |
|-------|------|---------|
| `referenceInstant` | `Instant` | local noon of the date (`research.md` §4) |
| `dasha` | `DashaSignificators` | |
| `transit` | `TransitContribution` | |
| `verdicts` | `List<MatterVerdict>` | one per `Matter`, in enum order |
| `accuracy` | `Accuracy` | mirrors the transit positions |
| `engineVersion` | `EngineVersion` | |

`MatterVerdict verdictFor(Matter)` accessor.

## `DailyPredictionFactory`

```java
public final class DailyPredictionFactory {
    public DailyPredictionFactory(PositionProvider positions);

    public DailyPrediction predict(NatalChart chart, LocalDate date, double longitude);

    public static DailyPrediction compute(
        NatalChart chart, Instant referenceInstant,
        EphemerisResult transitAtReference, EphemerisResult transitTwelveHoursBefore,
        EphemerisResult transitTwelveHoursAfter);
}
```

### `predict(chart, date, longitude)`

- `referenceInstant = date.atTime(12,0).toInstant(UTC) − round(longitude/15·3600) s`.
- `date` before `chart.birthData().instant()`'s date → `IllegalArgumentException`.
- Fetches the transit `EphemerisResult` at `referenceInstant` and `± 12 h`
  (three `positions(...)` calls).
- delegates to `compute(...)`.

### `compute(...)` — pure

- Builds `SignificatorTable.of(chart)` and `DashaTimeline.from(chart …)` (both
  pure), then §2–§5 of `research.md`.
- `accuracy` = the reference transit's Moon accuracy.
- Deterministic: equal inputs ⇒ equal `DailyPrediction`.

## Cross-module invariants (property / golden tests)

1. `significationsByLord.get(L)` equals `SignificatorTable.of(chart)
   .grahaSignificators(L)` for every running lord `L` (SC-002).
2. `activated` = `{ h : ∃ running L with h ∈ significationsByLord.get(L)
   .houses() }`, and `strengthOf(h)` = the count of such `L` (SC-002).
3. `moonSupports` = `SignificatorTable.of(chart).grahaSignificators(
   transit.moonChain().subLord()).houses().keySet()` (SC-003).
4. Every `Verdict` in `verdicts` is the row-5-table outcome of its inputs
   (`research.md` §5) — a property test enumerates the table.
5. `Matter.favourable()` ∩ `Matter.obstructive()` == ∅ for every matter; every
   `key()` round-trips through `HouseGroups.fromKey` (SC-001).
6. `DailyPredictionFactory.compute` is deterministic (equal inputs ⇒ equal
   `DailyPrediction`, which has value equality).
