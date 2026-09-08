# Contract: `core` lordage & partition API

Consumers: chart assembly (SPEC-002), significators & ruling planets (SPEC-003),
dasha timeline (SPEC-004), horary (SPEC-005).

## `KpLordage`

```java
public final class KpLordage {
    public static LordChain chainFor(double longitude);
}
```

### Input

| Parameter | Rules |
|-----------|-------|
| `longitude` | any `double`; normalised to `[0, 360)` before use (FR-012). `NaN` / `±Infinity` → `IllegalArgumentException`. |

### Output — `LordChain`

For the normalised longitude `λ`:

| Field | Rule |
|-------|------|
| `sign` | `λ / 30`, half-open (FR-011) |
| `signLord` | `sign.lord()` |
| `nakshatra` | `λ / (13°20′)`, half-open |
| `pada` | `1 + floor((λ − nakshatra.start) / 3°20′)`, in `{1,2,3,4}` (FR-007) |
| `starLord` | `nakshatra.lord()` |
| `subLord` | `lord` of the unique sub `Span` containing `λ` (half-open) |
| `subSubLord` | `lord` of the unique sub-sub `Span` containing `λ` within that sub |

### Guarantees

- Total function over all finite `double` inputs (after normalisation).
- Deterministic and pure.
- A longitude exactly on any boundary resolves to the **higher** division
  (FR-011); `chainFor(0.0)` ⇒ Aries / Ashwini / pada 1.
- Consistency: `chainFor(λ).subLord` always equals the sub-partition result for
  the same `λ` (cross-checked by property test).

## `VimshottariPartition`

```java
public final class VimshottariPartition {
    public static List<Span> subs(Nakshatra nakshatra);
    public static List<Span> subSubs(Nakshatra nakshatra, Graha subLord);
}
```

### Output — `List<Span>` (always nine, in Vimshottari order)

| Property | Guarantee |
|----------|-----------|
| Order | starts from `nakshatra.lord()` (for `subs`) or `subLord` (for `subSubs`), then `Graha.next()` … |
| Width | `span.end − span.start == BigFraction(lord.years(), 120) × parentWidth`, **exact** (FR-009, FR-010) |
| Coverage | `subs(n)` exactly covers `[n.start, n.start + 13°20′)`; `subSubs(n, s)` exactly covers that sub's `[start, end)` |
| Exactness | `Σ widths == parentWidth` as `BigFraction` equality — no epsilon (SC-003) |
| Contiguity | `subs(i).end == subs(i+1).start` for `i = 0..7`; likewise sub-subs |

### `Span`

```java
public record Span(Graha lord, BigFraction start, BigFraction end) {
    public double startDeg();   // start.doubleValue()
    public double endDeg();     // end.doubleValue()
    public boolean contains(double longitudeDeg); // half-open [start, end)
}
```

## Zodiac-wide guarantee (SC-004)

Concatenating `subs(n)` for `n` in nakshatra order produces 243 contiguous spans
covering `[0°, 360°)` with shared boundaries and no gap or overlap. The 249-entry
KP horary table (SPEC-005) is derived by additionally splitting these spans at the
12 sign boundaries; `VimshottariPartition` must expose enough (exact boundaries)
for that split to be lossless — it does not perform the split itself.

## Supporting types

- `Sign` — enum, 12 constants, `lord()`, 30° each.
- `Nakshatra` — enum, 27 constants, `lord()`, `startLongitude()`, 13°20′ each.
- `Longitudes.normalize(double)` — `[0, 360)`.
- `LordChain` — record (see data-model.md).
