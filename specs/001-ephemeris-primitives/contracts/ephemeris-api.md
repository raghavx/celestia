# Contract: `ephemeris` public API

Consumers: `core` (this feature), chart assembly (SPEC-002), and — indirectly —
every later KP spec. The Swiss Ephemeris library type MUST NOT appear in any
public signature (FR-018, Principle IX).

## `PositionProvider`

```java
public interface PositionProvider {
    EphemerisResult positions(Instant utcInstant);
}
```

### Input

| Parameter | Rules |
|-----------|-------|
| `utcInstant` | non-null; interpreted as an instant on the UTC timeline (FR-001). Any `Instant` is accepted — range is handled by the `Accuracy` flag, not by rejection. |

### Output — `EphemerisResult`

- `positions` contains **exactly nine** entries, one per `Graha`.
- Each `GrahaPosition.longitude ∈ [0, 360)`, sidereal, KP-New ayanamsa (FR-002, FR-003).
- `RAHU` uses the **mean** node; `KETU.longitude == (RAHU.longitude + 180) mod 360`;
  both `retrograde == true` (FR-004).
- `retrograde == (speedPerDay < 0)` for every graha (FR-005).
- `accuracy == FULL` iff the instant is within the supported range (1800–2100),
  else `REDUCED` (FR-016) — and then **every** position in the result carries
  `REDUCED`.
- `ayanamsa == Ayanamsa.KP_NEW`.
- `engineVersion` is fully populated and stable for a given build + data set.

### Errors — `EphemerisException` (unchecked)

Thrown ONLY for:
- `null` instant.
- Failure to initialise the Swiss Ephemeris backend (missing/corrupt library,
  unreadable data directory when non-Moshier).

NOT thrown for:
- Out-of-range dates → `REDUCED` result instead.

### Guarantees

| Property | Guarantee |
|----------|-----------|
| Determinism | Equal `utcInstant` ⇒ `equals()`-equal `EphemerisResult`, on every run and every supported platform (FR-014, SC-005). |
| Thread-safety | `positions(...)` is safe to call concurrently from many threads. |
| Purity | No network. No writes. No ambient clock. Reads only the bundled read-only `.se1` data (or nothing, in Moshier mode). |
| Performance | Nine positions in well under the 50 ms budget that SC-006 allows for positions **plus** lord chains. |
| No leakage | No `de.thmac.swisseph.*` (or any SE) type in a public signature, field, or exception. |

## Supporting types

- `Graha` — enum, nine constants, `years()`, `next()`, `vimshottariOrder()`.
- `Ayanamsa` — enum, `KP_NEW` only.
- `Accuracy` — enum, `FULL` | `REDUCED`.
- `JulianDay` — record `(double jdUt, double jdTt, double deltaTSeconds)`.
- `GrahaPosition` — record (see data-model.md).
- `EngineVersion` — record; `String id()`.
- `TimeScales` — `static JulianDay of(Instant utcInstant)`; the ΔT model is an
  implementation detail tagged in `EngineVersion`.

## Versioning

Any change to: the ayanamsa mapping, the node choice, the ΔT model, the SE port
version, or the `.se1` data set MUST change `EngineVersion.id()`. Adding a field
to a record is a source-compatible change but still bumps `rules` if it reflects a
computation change.
