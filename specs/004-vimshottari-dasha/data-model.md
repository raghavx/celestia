# Phase 1 Data Model: Vimshottari Dasha (SPEC-004)

All types are in `com.celestia.core.dasha`, immutable, pure. `Instant`s are on the
UTC timeline; period lengths derive from an exact `BigFraction` of seconds and are
rounded to nanosecond `Instant`s only at boundaries (research.md §3).

## `DashaLevel` (enum)

| Constant | `rank()` | Synonym |
|----------|----------|---------|
| `MAHADASHA` | 1 | — |
| `ANTARDASHA` | 2 | Bhukti |
| `PRATYANTARDASHA` | 3 | Antara |
| `SOOKSHMA` | 4 | — |
| `PRANA` | 5 | — |

`int rank()` = `ordinal() + 1`. `static DashaLevel ofRank(int)` (1–5, else
`IllegalArgumentException`). `Optional<DashaLevel> child()` / `parent()`.

## `Portion` (record) — internal to the split

`(Graha lord, BigFraction span)` — one slice of a `VimshottariSplit`. `span` is
in the same unit as the split input (degrees or seconds). Not part of the public
timeline API.

## `VimshottariSplit` (utility)

```java
public final class VimshottariSplit {
    // 9 portions, in Vimshottari order from fromLord, spans summing to `total` exactly
    public static List<Portion> of(BigFraction total, Graha fromLord);
}
```

- `total` must be `> 0`.
- Portion `i` span = `total × grahaᵢ.years() / 120`, `grahaᵢ` = `fromLord` advanced
  `i` times in Vimshottari order.
- **Invariant**: `Σ span == total` exactly (rational equality).

## `DashaPeriod` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `level` | `DashaLevel` | which level |
| `lord` | `Graha` | the ruling graha |
| `start` | `Instant` | inclusive |
| `end` | `Instant` | exclusive |
| `parentLords` | `List<Graha>` | Mahadasha lord … immediate parent's lord (size `rank() − 1`; empty for `MAHADASHA`) |

Compact constructor: `start` before `end`; `parentLords` size = `level.rank() −
1`; `List.copyOf`. `boolean contains(Instant t)` — half-open `[start, end)`.
`Duration duration()`.

## `DashaBalance` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `mahaLord` | `Graha` | lord of the Moon's nakshatra at birth |
| `elapsedFraction` | `double` | fraction of the Mahadasha already run at birth, `[0, 1)` |
| `elapsed` | `Duration` | `elapsedFraction × mahaLord.years()` |
| `balance` | `Duration` | remaining Mahadasha at birth |
| `mahaStart` | `Instant` | when this Mahadasha began (before the birth instant) |
| `mahaEnd` | `Instant` | when it ends (`birthInstant + balance`) |

Compact constructor: `0.0 ≤ elapsedFraction < 1.0`; `mahaStart` before `mahaEnd`.

## `RunningDasha` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `instant` | `Instant` | the query instant |
| `stack` | `List<DashaPeriod>` | index 0 = Mahadasha … index `depth−1`; each contains `instant`; each nested in the previous |

Compact constructor: `stack` non-empty, size ≤ 5; `stack.get(i).level.rank() == i
+ 1`; every period `contains(instant)`; each period's `[start,end)` within the
previous period's. `List.copyOf`.

Accessors: `DashaPeriod period(DashaLevel)` (or `IllegalArgumentException` if
deeper than the resolved depth); `Graha lord(DashaLevel)`; `int depth()`.

## `DashaTimeline` (aggregate)

```java
public final class DashaTimeline {
    public static DashaTimeline from(
        Instant birthInstant, double moonLongitude, Accuracy accuracy, EngineVersion engineVersion);

    public Instant birthInstant();
    public DashaBalance balanceAtBirth();
    public RunningDasha running(Instant query, int depth);          // depth 1..5
    public List<DashaPeriod> periods(DashaLevel level, Instant from, Instant to);
    public Accuracy accuracy();
    public EngineVersion engineVersion();
}
```

### `from(...)` — pure

- Computes `DashaBalance` (research.md §1).
- Deterministic: equal inputs ⇒ equal balance, running stacks, and period lists.
- No I/O, no wall clock.

### `running(query, depth)` — research.md §4

- `query < birthInstant` → `IllegalArgumentException`.
- `depth` outside 1–5 → `IllegalArgumentException`.
- Resolves the Mahadasha by fast-forwarding whole 120-year cycles, then splits to
  `depth`. Half-open at every level.

### `periods(level, from, to)` — research.md §5

- `from` after `to` → `IllegalArgumentException`; `from < birthInstant` →
  `IllegalArgumentException`.
- Returns every level-`level` period overlapping `[from, to)`, chronological,
  contiguous (`periods[i].end == periods[i+1].start`), each with `parentLords`.
- More than 10 000 periods would be returned → `IllegalArgumentException` naming
  the level and window (the window is mis-scaled for the level).

## `DashaTimelineFactory`

```java
public final class DashaTimelineFactory {
    public DashaTimelineFactory(PositionProvider positions);
    public DashaTimeline at(BirthData birth);   // Moon from positions.positions(birth.instant())
}
```

`at(...)` takes the Moon's longitude and `Accuracy` from the position result and
delegates to `DashaTimeline.from(...)` with the position result's `EngineVersion`.

## Cross-module invariants (property / golden tests)

1. `VimshottariSplit.of(total, lord)` — 9 portions, `Σ span == total` exactly, in
   Vimshottari order from `lord` (SC-003).
2. For any period, its nine children (one `VimshottariSplit` level down) are
   contiguous and partition it exactly — `child[0].start == parent.start`,
   `child[8].end == parent.end`, `child[i].end == child[i+1].start` (SC-003).
3. `running(q, d).stack` — size `d`, each period `contains(q)`, each nested in the
   previous (SC-002, SC-004).
4. `DashaTimeline.from` is deterministic: equal inputs ⇒ equal `balanceAtBirth()`,
   equal `running(q, d)`, equal `periods(level, from, to)` (SC-005). `DashaTimeline`
   is a class without `equals`; compare the accessor outputs (records with value
   equality).
5. `balanceAtBirth().elapsed + balanceAtBirth().balance` ≈ `mahaLord.years()` in
   365.25-day years (to the nanosecond rounding).
