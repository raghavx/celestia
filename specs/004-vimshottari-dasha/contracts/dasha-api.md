# Contract: `core.dasha` Vimshottari dasha API

Consumers: SPEC-006 (daily-prediction ruleset — dasha lords ∩ significators),
SPEC-008 (persistence: `dasha_period` rows), the LLM agent tool
`getDashaPeriods(userId, atDate, depth)`.

No `de.thmac.swisseph` type in any public member. Pure — no I/O, no wall clock.

## `DashaTimelineFactory`

```java
public final class DashaTimelineFactory {
    public DashaTimelineFactory(PositionProvider positions);

    public DashaTimeline at(BirthData birth);
}
```

- `at(birth)` — the Moon's sidereal longitude and `Accuracy` come from
  `positions.positions(birth.instant())`; `birth`'s latitude/longitude are not
  used by the dasha. Propagates nothing beyond `EphemerisException` from the
  provider.

## `DashaTimeline`

```java
public final class DashaTimeline {
    public static DashaTimeline from(
        Instant birthInstant, double moonLongitude, Accuracy accuracy, EngineVersion engineVersion);

    public Instant birthInstant();
    public DashaBalance balanceAtBirth();
    public RunningDasha running(Instant query, int depth);          // 1 <= depth <= 5
    public List<DashaPeriod> periods(DashaLevel level, Instant from, Instant to);
    public Accuracy accuracy();
    public EngineVersion engineVersion();
}
```

### `from(...)` — pure core

Builds the timeline from already-resolved inputs. Deterministic: equal inputs ⇒
equal `balanceAtBirth()`, `running(...)`, `periods(...)`.

### `balanceAtBirth()` → `DashaBalance`

| Item | Rule |
|------|------|
| `mahaLord` | lord of `Nakshatra.at(moonLongitude)` |
| `elapsedFraction` | `(moonLongitude − nakshatraStart) / (40/3)`, in `[0, 1)` |
| `elapsed` / `balance` | `elapsedFraction` / `(1 − elapsedFraction)` × `mahaLord.years()`, in 365.25-day years |
| `mahaStart` | `birthInstant − elapsed` |
| `mahaEnd` | `birthInstant + balance` |

### `running(query, depth)` → `RunningDasha`

| Item | Rule |
|------|------|
| `query < birthInstant` | `IllegalArgumentException` |
| `depth ∉ [1, 5]` | `IllegalArgumentException` |
| Mahadasha | the Vimshottari sequence from `balanceAtBirth().mahaStart` is periodic with a 120-year period; the Maha containing `query` is found by fast-forwarding whole cycles then ≤ 9 steps |
| levels 2…depth | recursive nine-way weight split of the parent period (`VimshottariSplit`) |
| boundary | half-open `[start, end)` at **every** level — `query` on a boundary belongs to the later period |
| `stack` | `[Mahadasha, …]` of length `depth`, each `contains(query)`, each nested in the previous |

### `periods(level, from, to)` → `List<DashaPeriod>`

| Item | Rule |
|------|------|
| `from` after `to`, or `from < birthInstant` | `IllegalArgumentException` |
| result | every level-`level` period overlapping `[from, to)`, chronological |
| contiguity | `result[i].end == result[i+1].start` (exact) |
| `parentLords` | each period carries Mahadasha-lord … immediate-parent-lord |
| safety | a request that would return > 10 000 periods (window mis-scaled for the level) → `IllegalArgumentException` |

**Intended scale per level**: Mahadashas over a lifetime; Antardashas over years;
Pratyantardashas over months; Sookshmas over weeks; Pranas over hours to days.

### `accuracy()`

Mirrors the Moon position — `REDUCED` for a birth outside the supported ephemeris
range (FR-016), otherwise `FULL`.

## Supporting types (`com.celestia.core.dasha`)

`DashaLevel` (rank 1–5; Bhukti/Antara synonyms), `DashaPeriod`, `DashaBalance`,
`RunningDasha`, `VimshottariSplit` (+ `Portion`).

## Versioning

`EngineVersion` (from the position result) is carried on the timeline. It changes
if the year-length convention, the level count, the Vimshottari ordering/weights,
or any upstream SPEC-001 rule changes (`core/REFERENCES.md` bump procedure).
