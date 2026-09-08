# Phase 0 Research: Natal Chart & Placidus Cusps (SPEC-002)

## 1. `swe_houses` in the Java port

**Decision**: use `SwissEph.swe_houses(double tjd_ut, int iflag, double geolat,
double geolon, int hsys, double[] cusp, double[] ascmc)` with `iflag =
SEFLG_SIDEREAL`, `hsys = (int) 'P'` (Placidus). `cusp` has length 13 (indices
1..12 used); `ascmc` has length ≥ 10 (`ascmc[0]` = Ascendant, `ascmc[1]` = MC).
Sidereal mode is set once via `swe_set_sid_mode(SE_SIDM_KRISHNAMURTI, 0, 0)` as in
`SwissEphemerisPositionProvider`.

**Verified with pyswisseph** (`houses_ex`, same underlying C code):

- `ascmc[0]` equals `cusp[1]` **bit-identically** for Placidus. The engine will
  set cusp 1 = the Ascendant value from `ascmc[0]` to guarantee SC-003.
- `ascmc[1]` (MC) equals `cusp[10]` (the 10th house cusp).
- The twelve cusps run forward around the zodiac with exactly one wrap through
  360° — every forward arc `cusp[n] → cusp[n+1]` is > 0.
- Example (Obama, Honolulu 21.30694 N, −157.85833 E, 1961-08-05T05:24Z, KP sid):
  Ascendant ≈ 294.83° (Capricorn ~24°50′), MC ≈ 215.67°.

**Return value**: `swe_houses` returns `int`; negative is an error (e.g. Placidus
failing near the pole). We do **not** interpret that — see §2.

## 2. High-latitude behaviour

**Decision**: **pre-check the latitude ourselves** — if `|lat| >= polarLimit`
(default **66.0°**, configurable via `SwissEphemerisConfig` / the provider), throw
`PlacidusUndefinedException` (a subclass of `EphemerisException`) that names the
latitude, before calling `swe_houses` at all. A separate `anglesOnly(BirthData)`
call returns just the Ascendant and MC (`swe_houses` still gives those, or they
can be computed from ARMC) so callers that only need the Lagna are not blocked.

**Rationale**: pyswisseph raises `swisseph.Error` at 78°N; the Java port returns a
negative rc and fills the cusp array with a Porphyry fallback. Neither is a stable
contract to build on. A latitude pre-check is deterministic, testable without the
backend, and matches ADR-0004 ("warns or rejects; `core` has a defined
behaviour"). 66.0° is a safe margin below the true 66°34′ Arctic/Antarctic circle;
Placidus becomes numerically unstable before the exact limit.

**Alternatives considered**: silently substitute Porphyry or Equal houses above
the limit — rejected: it would return a *different house system* under the same
API with no signal, and KP significators computed from non-Placidus cusps are
wrong, not approximate.

## 3. Bhava assignment (KP cusp-to-cusp)

**Decision**: a graha is in **bhava _n_** iff its sidereal longitude lies in the
forward arc `[cusp[n], cusp[n+1])` around the circle (half-open, boundary to the
higher-longitude side — the same `[start, end)` convention as SPEC-001's
`Longitudes`). `cusp[13]` wraps to `cusp[1]`.

Algorithm: for `n` in 1..12, `arc(a,b) = (b - a) mod 360`. If
`arc(cusp[n], lambda) < arc(cusp[n], cusp[n+1])` then bhava = `n`. Exactly one `n`
matches. `lambda == cusp[n]` → `arc = 0` → bhava `n` (that cusp starts it);
`lambda == cusp[n+1]` → not `n`, falls to `n+1`.

**Rationale**: this is the KP "Bhava" — a planet is *in* the house whose cusp it
has most recently crossed. Source: K. S. Krishnamurti, *KP Readers* (cuspal
system). It is **not** the Sripati/equal-bhava midpoint method (that splits the
house at the midpoints between cusps), which KP does not use for placement.

**Intercepted signs**: at high sub-polar latitudes two adjacent cusps can be
close, and a whole sign can be "intercepted" (contained within one house). The
arc test above is still unambiguous — a graha between the two close cusps is in
the earlier house; a graha in the intercepted sign but outside that narrow arc is
in the neighbouring house. No special case needed.

## 4. Rasi-house convention

**Decision**: the rasi (sign-based) house of a graha is
`1 + ((graha.sign.ordinal() - ascendant.sign.ordinal()) mod 12)` — whole signs
counted from the Ascendant's sign, Ascendant's sign = house 1. The same formula
gives each cusp a rasi house.

**Rationale**: this is the standard whole-sign Rasi-chart layout used by KP
software for display and by cross-checking tools. Source: standard Jyotisha
(whole-sign / *bhāva chalit* distinction). The **Bhava** chart (§3) is the KP
judgement view; the Rasi chart is for display and verification.

## 5. Do cusps need the `.se1` data?

**Finding**: `swe_houses` needs the sidereal time (from `jd_ut`), the obliquity of
the ecliptic, nutation, and the ayanamsa. The port computes obliquity and nutation
from built-in series and the KP ayanamsa analytically — **no `.se1` file access**.
So cusp longitudes are available at full precision even in "no data" / Moshier
mode.

**Decision**: `HouseResult` still carries an `Accuracy`, taken from the companion
`EphemerisResult` for the same instant, so a `NatalChart` has one consistent
accuracy flag. `core/REFERENCES.md` notes that cusps are analytic and do not
themselves degrade outside 1800–2100; the flag reflects the *positions*.

## 6. Golden-chart reference extension

**Decision**: extend `tools/ephe-crosscheck/compute_golden.py` with a
`swe.houses_ex(jd, lat, lon, b'P', FLG_SIDEREAL)` pass. For each chart add:

- `expected.cusps`: 12 entries, each `{ longitude, sign, sign_lord, nakshatra,
  pada, star_lord, sub_lord, sub_sub_lord }` (reusing the existing `lord_chain`).
- `expected.angles`: `{ ascendant: {...}, midheaven: {...} }`.
- `expected.grahas[g].bhava` and `expected.grahas[g].rasi_house`.

The birth `latitude` / `longitude` are already in every golden file. Regenerate
the three files; this is the one prerequisite before `/speckit-implement`.

**Follow-up (as in SPEC-001)**: a textbook cross-check of at least one chart's
cuspal sub lords, since the tool and the engine share the KP division rule.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | House call | `swe_houses` sidereal, `hsys='P'`; cusp 1 := Ascendant (`ascmc[0]`) |
| 2 | High latitude | pre-check `|lat| >= 66.0°` (config) → `PlacidusUndefinedException`; angles-only call available |
| 3 | Bhava | cusp-to-cusp, half-open `[cusp n, cusp n+1)`, wrap-aware; not Sripati |
| 4 | Rasi house | whole signs from the Ascendant's sign |
| 5 | Data files | cusps are analytic; `Accuracy` mirrors the positions |
| 6 | Golden data | extend `ephe-crosscheck` with a `swe_houses` pass; regenerate 3 files |
