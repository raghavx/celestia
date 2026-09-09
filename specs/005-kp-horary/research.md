# Phase 0 Research: KP Horary 1–249 (SPEC-005)

## 1. The 243 → 249 split

`VimshottariPartition.subDivisions()` returns 243 `Span`s (exact `BigFraction`
degrees) — the 27 nakshatras × 9 sub-lord divisions — tiling `[0°, 360°)` in
longitude order, each `Span.lord()` being the **sub lord** of that arc.

The 249 table splits any sub-span that **crosses** a 30° sign boundary:

- Sign cusps are at `30k` for `k = 1..11` (0 and 360 are the ends).
- A span `[s, e)` is split at cusp `c` iff `s < c < e` (strict — a span that
  *starts* or *ends* exactly on a sign cusp is not split there).
- Splitting `[s, e)` at `c` yields `[s, c)` and `[c, e)`, each within one sign.

**The count is exactly 249**: 243 base spans + 6 spans that genuinely straddle a
sign cusp (the other six sign cusps fall on a sub-lord boundary). This 249 is
K. S. Krishnamurti's published horary table; a test asserts the computed count is
249 and `arcs()` tiles `[0, 360)` exactly.

**Numbering**: the split arcs, sorted by `start`, are numbered 1–249. `arc(1)`
starts at 0°, `arc(249)` ends at 360°.

**Alternatives considered**: transcribing KSK's printed table verbatim — rejected,
the derivation is exact and a transcription would need the same SC-006 human check
anyway *plus* be a second source of typos. Splitting at nakshatra or pada
boundaries too — not the KP-249 definition (only sign boundaries).

## 2. The horary Ascendant

For number `N`, the horary Ascendant longitude is the **midpoint** of arc `N`:
`(arc.start + arc.end) / 2`.

- The **sub lord** is constant across the whole arc (the arc is a subset of one
  sub-lord division), so the midpoint's sub lord == the arc's sub lord — the KP
  determinant of the horary answer is independent of where in the arc the
  Ascendant is placed. The midpoint is chosen because it is unambiguous, stable,
  and interior (so the sub-sub lord and the derived house cusps are well defined).
- A short "sliver" arc (a sub-span cut close to a sign cusp) still has a midpoint
  strictly inside one sign.
- Two numbers that share a sub lord (an arc split at a sign cusp) give two
  Ascendants with the same sub lord in different signs — both are valid horary
  Ascendants (US2 scenario 2).

The Ascendant is presented as an `AnglePoint(Angle.ASCENDANT, midpoint,
KpLordage.chainFor(midpoint))`.

## 3. RAMC from a given Ascendant

`swe_houses_armc(armc, geolat, eps, 'P', cusp, ascmc)` computes the twelve
Placidus cusps from the **right ascension of the Midheaven** (`armc`, degrees),
the geographic latitude and the obliquity — all **tropical**. So the task is:
given the desired tropical Ascendant longitude `λ`, the latitude `φ` and the
obliquity `ε`, find `armc`.

**Closed form** (inversion of the standard Ascendant equation):

```
armc = atan2( −cos λ ,  sin λ · cos ε + tan φ · sin ε )    (degrees, normalised [0, 360))
```

**Verification**: for a grid of `(instant, latitude)` we call the ordinary
`swe_houses(jd, SEFLG_SIDEREAL, φ, lon, 'P', cusp, ascmc)`; `ascmc[0]` is the
Ascendant and `ascmc[2]` is the ARMC. Feeding `ascmc[0] + ayanamsa` (tropical
Ascendant) into the formula above must reproduce `ascmc[2]` to ~1e-6°. A property
test does this round-trip over random latitudes and instants.

**Bisection fallback**: the Ascendant is a continuous, strictly monotone function
of `armc` on the correct branch, so if the closed form proves numerically fragile
near `φ → 0` or `λ` near a solstice point, a 60-iteration bisection on
`armc ∈ [asc-branch]` reaches 1e-9°. The closed form is tried first; the fallback
is documented and kept behind the same interface.

**Polar limit**: `|φ| ≥ config.polarLimit()` (66°, as SPEC-002) →
`PlacidusUndefinedException`, before any computation.

## 4. Sidereal ↔ tropical

The engine works in the **sidereal KP** frame; `swe_houses_armc` is tropical.

- `ayanamsa = swe_get_ayanamsa_ut(jdUt)` for the judgment instant (KP-New — the
  sid mode is already set to `SE_SIDM_KRISHNAMURTI`).
- `obliquity ε = swe_calc_ut(jdUt, SE_ECL_NUT, iflag, xx, serr)`, `xx[0]` (true
  obliquity of date, degrees).
- Tropical horary Ascendant `= normalise(siderealHoraryAscendant + ayanamsa)`.
- Compute the twelve tropical cusps via `armc` + `swe_houses_armc`.
- Sidereal cusp `= normalise(tropicalCusp − ayanamsa)`; likewise the MC/Asc in
  `ascmc`.
- Cusp 1 is then set **bit-identically** to the sidereal horary Ascendant
  (`HouseResult` requires `cusp[0] == angles[ASCENDANT]`), overriding any
  round-trip drift of ~1e-9°.

## 5. Assembling the chart

`HoraryChartFactory.cast(int number, BirthData judgment)`:

1. `arc = Horary249.arc(number)`; `ascLon = arc.midpointDeg()`.
2. `EphemerisResult positions = positionProvider.positions(judgment.instant())` —
   the same sidereal KP planets a natal chart for that instant would have.
3. `HouseResult houses = horaryHouseProvider.housesFor(judgment, ascLon)` — §3/§4.
4. `NatalChart chart = NatalChartFactory.assemble(judgment, positions, houses)` —
   **unchanged** from SPEC-002.

The horary `HouseResult`'s `birthData` is the judgment moment even though its
cusps are number-seeded rather than instant-seeded — documented on
`HoraryHouseProvider`. `NatalChartFactory.assemble` only reads the cusp ring and
the angles, so this is sound.

`assemble` throws nothing new; polar rejection happens in step 3.

## 6. Horary ruling planets

`RulingPlanetsFactory.compute(judgment, ascendantLongitude, moonLongitude,
rahuLongitude, weekday, weekdayFallback, accuracy, engineVersion, options)`
already takes an **explicit** Ascendant longitude. So horary RP =

```
HoraryRulingPlanets.at(int number, BirthData judgment, SunriseProvider sunrise):
  ascLon  = Horary249.arc(number).midpointDeg()
  eph     = positions.positions(judgment.instant())
  weekday = KpWeekday.resolve(judgment.instant(), lat, lon, sunrise)
  return RulingPlanetsFactory.compute(judgment, ascLon,
             eph.position(MOON).longitude(), eph.position(RAHU).longitude(),
             weekday.weekday(), weekday.fallback(),
             eph.position(MOON).accuracy(), eph.engineVersion(), Options.defaults())
```

No change to SPEC-003.

## 7. Edge handling

| Case | Handling |
|------|----------|
| number ∉ 1..249 | `IllegalArgumentException` |
| sub-span boundary exactly on a sign cusp | no split there (strict `s < c < e`) |
| very short "sliver" arc | still has an interior midpoint in one sign |
| judgment latitude ≥ polar limit | `PlacidusUndefinedException` (as natal) |
| judgment instant outside ephemeris range | planets + obliquity + ayanamsa `REDUCED`; chart still cast (FR-011) |
| two instants minutes apart, same number/place | identical cusps (Asc fixed; obliquity/ayanamsa move ~1e-7°/min); Moon differs |

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | 249 table | derived: `subDivisions()` split at the 12 sign cusps (strict crossing); count == 249; longitude-order numbering |
| 2 | Horary Ascendant | arc midpoint; sub lord constant over the arc so the choice is judgement-neutral |
| 3 | RAMC | closed-form inversion `armc = atan2(−cos λ, sin λ·cos ε + tan φ·sin ε)`; verified vs `swe_houses`; bisection fallback |
| 4 | Frame | convert Asc to tropical via `swe_get_ayanamsa_ut`, compute tropical cusps, convert back; cusp 1 forced to the sidereal Asc |
| 5 | Chart | reuse `NatalChart` + `NatalChartFactory.assemble`; only the `HouseResult` is new |
| 6 | Horary RP | `RulingPlanetsFactory.compute` with the number's Ascendant; no SPEC-003 change |
| 7 | New capability | `HoraryHouseProvider` — houses from an Ascendant, not an instant |
