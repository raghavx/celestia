# Contract: `core.prediction` daily-prediction API

Consumers: the LLM agent tools `getDailyPrediction(userId, date)` and
`explainHouseGrouping(matter)`; SPEC-017 (the optional daily push).

No `de.thmac.swisseph` type in any public member. Pure — no I/O, no wall clock.
Output is **structured only** (Constitution IV) — verdicts plus the houses /
lords / transit bodies behind them; the LLM writes the sentence.

## `HouseGroups` / `Matter`

```java
public enum Matter {
    MARRIAGE, CAREER, WEALTH, EDUCATION, CHILDREN, PROPERTY, TRAVEL, LITIGATION,
    HEALTH_RECOVERY;

    public Set<Integer> favourable();   // non-empty, houses 1..12
    public Set<Integer> obstructive();  // disjoint from favourable()
    public String source();             // KP citation
    public String key();                // "marriage", …
}

public final class HouseGroups {
    public static Matter fromKey(String key);   // unknown -> IllegalArgumentException
    public static Set<Matter> all();
}
```

`explainHouseGrouping(key)`: `HouseGroups.fromKey(key)`, then its `favourable()` /
`obstructive()` / `source()`. The taxonomy is closed and versioned — a new matter
or a changed house set is an `EngineVersion` bump.

## `DailyPredictionFactory`

```java
public final class DailyPredictionFactory {
    public DailyPredictionFactory(PositionProvider positions);

    public DailyPrediction predict(NatalChart chart, LocalDate date, double longitude);

    public static DailyPrediction compute(
        NatalChart chart, Instant referenceInstant,
        EphemerisResult transitAtReference,
        EphemerisResult transitTwelveHoursBefore,
        EphemerisResult transitTwelveHoursAfter);
}
```

### `predict(chart, date, longitude)`

| Item | Rule |
|------|------|
| reference instant | `date` at `12:00` local mean time = `date.atTime(12,0)` UTC `−` `round(longitude/15 · 3600)` s (`research.md` §4) |
| `date` before the birth date | `IllegalArgumentException` |
| transit positions | `positions.positions(referenceInstant)` and at `± 12 h` |
| result | delegates to `compute(...)` |

### `compute(...)` — pure core

Builds `SignificatorTable.of(chart)` and `DashaTimeline.from(chart …)`, then:

| Output | Rule (`research.md`) |
|--------|---------------------|
| `dasha.running` | `DashaTimeline.running(referenceInstant, 5)` |
| `dasha.significationsByLord` | `grahaSignificators(L)` for each running lord (§2) |
| `dasha.activated` / strengths | union of the running lords' signified houses; strength = count of the 5 levels (§2) |
| `dasha.lordChangesWithinDay` | a running lord differs at `± 12 h` (§2) |
| `transit.moonChain` / `sunChain` | `KpLordage.chainFor` of the transiting Moon / Sun longitude at the reference instant (§3) |
| `transit.moonSupports` / `sunSupports` | houses whose natal significators include the transit sub lord (§3) |
| `transit.moonSubLordChangesWithinDay` | the Moon's sub lord differs at `± 12 h` (§3) |
| `verdicts` | one `MatterVerdict` per `Matter`, by the 4-row total function (§5), carrying `favourableHit` / `obstructiveHit` / `lords` / `transits` |
| `accuracy` | mirrors the reference transit's Moon position (`REDUCED` out of range) |

Deterministic: equal inputs ⇒ equal `DailyPrediction`.

## Supporting types (`com.celestia.core.prediction`)

`Verdict`, `TransitBody`, `ActivatedHouse`, `DashaSignificators`,
`TransitContribution`, `MatterVerdict`, `DailyPrediction`.

## Versioning

`EngineVersion` (from the chart / transit result) is carried on `DailyPrediction`.
It changes if the house-group taxonomy, the v1 transit rule, the reference-instant
rule, the verdict rule, or any upstream KP rule changes (`core/REFERENCES.md` bump
procedure).
