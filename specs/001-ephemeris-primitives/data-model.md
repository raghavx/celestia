# Phase 1 Data Model: Ephemeris & Longitude Primitives (SPEC-001)

All types are **immutable value objects** (Java `record` or `enum`). No identity,
no persistence, no framework annotations. Angles are ecliptic longitudes in
degrees unless stated otherwise.

## `ephemeris` module

### `Graha` (enum)

The nine KP planets/points, in Vimshottari order, each carrying its dasha-year
weight.

| Constant | Vimshottari years |
|----------|-------------------|
| `KETU` | 7 |
| `VENUS` | 20 |
| `SUN` | 6 |
| `MOON` | 10 |
| `MARS` | 7 |
| `RAHU` | 18 |
| `JUPITER` | 16 |
| `SATURN` | 19 |
| `MERCURY` | 17 |

- `int years()` — dasha-year weight.
- `Graha next()` — next in Vimshottari order, wraps `MERCURY → KETU`.
- `static List<Graha> vimshottariOrder()` — the nine in the order above.
- Invariant: `sum(years) == 120`.
- This enum replaces the scaffold's `core.dasha.Vimshottari` as the canonical
  graha list; `core` refactors to depend on it.

### `Ayanamsa` (enum)

- `KP_NEW` — the only supported value (ADR-0003). Maps to Swiss Ephemeris
  `SE_SIDM_KRISHNAMURTI` (5).
- Any request naming another ayanamsa is rejected upstream (FR-017); the enum
  exists so outputs can record which one was used and to leave room for a future
  spec.

### `Accuracy` (enum)

- `FULL` — instant within the supported ephemeris range (1800–2100).
- `REDUCED` — outside the range; analytical (Moshier) fallback used (FR-016).

### `JulianDay` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `jdUt` | `double` | Julian Day, Universal Time |
| `jdTt` | `double` | Julian Day, Terrestrial (Dynamical) Time |
| `deltaTSeconds` | `double` | ΔT applied (`jdTt − jdUt` expressed in seconds) |

- Derived from a UTC `Instant` by `TimeScales.of(Instant)` (FR-001).
- Invariant: `jdTt >= jdUt` for all dates after ~1900; `deltaTSeconds` matches the
  tagged ΔT model.

### `GrahaPosition` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `graha` | `Graha` | which body |
| `longitude` | `double` | sidereal ecliptic longitude, `[0, 360)` |
| `latitude` | `double` | ecliptic latitude, degrees |
| `speedPerDay` | `double` | longitude motion per day; negative ⇒ retrograde |
| `retrograde` | `boolean` | `speedPerDay < 0` |
| `accuracy` | `Accuracy` | `FULL` or `REDUCED` |

- Invariants: `longitude ∈ [0, 360)`; `retrograde == (speedPerDay < 0)`.
- For `KETU`: `longitude == (rahu.longitude + 180) mod 360`, and
  `retrograde == true` (mean node is always retrograde) (FR-004, FR-005).

### `EngineVersion` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `rules` | `String` | our algorithm version, e.g. `"kp-1"` — bumped on any rule change |
| `sePort` | `String` | Swiss Ephemeris port version |
| `deltaTModel` | `String` | ΔT model tag |
| `ephemerisData` | `String` | SHA-256 (short) of the `.se1` data manifest, or `"moseph"` |

- `String id()` — stable concatenation used in snapshots and, later, on stored
  `chart` rows (FR-015).

### `EphemerisResult` (record)

| Field | Type |
|-------|------|
| `instant` | `Instant` (the UTC input, echoed) |
| `julianDay` | `JulianDay` |
| `ayanamsa` | `Ayanamsa` |
| `positions` | `Map<Graha, GrahaPosition>` (all nine present) |
| `engineVersion` | `EngineVersion` |

- Invariant: `positions.keySet()` equals the full set of nine `Graha`.

### `PositionProvider` (interface)

```
EphemerisResult positions(Instant utcInstant);
```

- Deterministic: equal inputs ⇒ equal outputs (FR-014).
- Thread-safe (implementation guards the non-thread-safe SE handle).
- Throws `EphemerisException` only for genuinely unusable input (e.g. a null
  instant, or the SE library failing to initialise) — an out-of-range date is
  **not** an error; it returns a result with `Accuracy.REDUCED` (FR-016).

## `core` module

### `Sign` (enum)

Twelve signs `ARIES … PISCES`, each with `Graha lord()`. Lords: Ari/Sco→Mars,
Tau/Lib→Venus, Gem/Vir→Mercury, Can→Moon, Leo→Sun, Sag/Pis→Jupiter,
Cap/Aqu→Saturn. Each sign spans exactly 30°.

### `Nakshatra` (enum)

Twenty-seven nakshatras `ASHWINI … REVATI`, each with:
- `Graha lord()` — the Vimshottari lord (Ashwini→Ketu, Bharani→Venus, …, repeating
  the nine-lord cycle three times).
- `double startLongitude()` — `(ordinal) × 13°20′`.
- Each nakshatra spans exactly `13°20′` (= 800′); each of its four padas spans
  exactly `3°20′` (FR-007).

### `Longitudes` (utility)

- `double normalize(double deg)` — into `[0, 360)`; handles negatives, `360.0`,
  `> 360` (FR-012).
- Boundary convention constant/doc: half-open `[start, end)`, boundary belongs to
  the higher division (FR-011).

### `LordChain` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `longitude` | `double` | the normalised input |
| `sign` | `Sign` | |
| `signLord` | `Graha` | `sign.lord()` |
| `nakshatra` | `Nakshatra` | |
| `pada` | `int` | 1–4 |
| `starLord` | `Graha` | `nakshatra.lord()` |
| `subLord` | `Graha` | from the Vimshottari sub partition |
| `subSubLord` | `Graha` | from the Vimshottari sub-sub partition |

- Produced by `KpLordage.chainFor(double longitude)`.
- Invariants: `pada ∈ {1,2,3,4}`; `signLord == sign.lord()`;
  `starLord == nakshatra.lord()`; the sub/sub-sub lords are consistent with
  `VimshottariPartition` for the same longitude.

### `Span` (record)

| Field | Type | Meaning |
|-------|------|---------|
| `lord` | `Graha` | the ruling graha of this span |
| `start` | `BigFraction` | start longitude in degrees, **exact** |
| `end` | `BigFraction` | end longitude in degrees, **exact** |

- `double startDeg()` / `double endDeg()` — `double` views for callers that don't
  need exactness.
- Invariant: `start < end`; `end − start == (lord.years() / 120) × parentWidth`.

### `VimshottariPartition`

- `List<Span> subs(Nakshatra nakshatra)` — nine spans, in Vimshottari order
  starting from `nakshatra.lord()`, widths `(years/120) × 13°20′`, covering the
  nakshatra exactly (FR-009).
- `List<Span> subSubs(Nakshatra nakshatra, Graha subLord)` — nine spans within the
  given sub, order starting from `subLord`, widths `(years/120) × subWidth`,
  covering the sub exactly (FR-010).
- Invariants (SC-003): `Σ (span.end − span.start)` over `subs(n)` equals exactly
  `BigFraction(40, 3)` (13°20′); likewise sub-subs sum exactly to the sub width.
- Invariant (SC-004): concatenating `subs(n)` for `n = 0..26` tiles `[0, 360)`
  with shared boundaries and no gap/overlap.

## Cross-module invariants (property tests)

1. For every `double λ ∈ [0, 360)`, `KpLordage.chainFor(λ).subLord` equals the
   `lord` of the unique `Span` in `subs(chainFor(λ).nakshatra)` that contains `λ`
   under the half-open rule; same for sub-sub.
2. The 27 × 9 = 243 sub spans, concatenated in longitude order, are contiguous and
   cover `[0, 360)` exactly. (The **249**-entry KP horary table of SPEC-005 is
   this set further split at the 12 sign boundaries — out of scope here, but the
   partition must support that split cleanly.)
3. `Graha.vimshottariOrder()` starting from any graha and taking `years()` sums to
   120 over one full cycle.
