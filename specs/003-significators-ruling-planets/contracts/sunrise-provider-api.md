# Contract: `ephemeris` sunrise API

Consumer: `core.judgement.KpWeekday` (the day lord). Mirrors `PositionProvider` /
`HouseProvider`. No `de.thmac.swisseph` type in any public member (FR-018).

## `SunriseProvider`

```java
public interface SunriseProvider {
    Optional<Instant> sunriseBefore(Instant judgmentInstant, double latitude, double longitude);
}
```

### Input

| Parameter | Rule |
|-----------|------|
| `judgmentInstant` | non-null; UTC timeline |
| `latitude` | `|latitude| <= 90` — else `IllegalArgumentException` |
| `longitude` | `|longitude| <= 180` — else `IllegalArgumentException` |

### Output

- The **latest** local sunrise instant at or before `judgmentInstant` — i.e. the
  sunrise that began the KP day containing `judgmentInstant`.
- `Optional.empty()` when the Sun neither rises nor sets on that day at that
  latitude (polar day / night).
- Sunrise = the Sun's **upper limb** at the true horizon with standard
  atmospheric refraction (the Swiss Ephemeris default).

### Guarantees

| Property | Guarantee |
|----------|-----------|
| Determinism | equal inputs ⇒ equal `Optional<Instant>` |
| Thread-safety | safe to call concurrently |
| Purity | no network, no writes, no wall clock |
| No leakage | no Swiss Ephemeris type in the signature or exceptions |
| Bound | at most a ~2-day search window; two backend calls typical |

### Errors

`EphemerisException` only for a null instant or a backend initialisation failure.
An instant outside the ephemeris data range still returns a result (analytic Sun).
