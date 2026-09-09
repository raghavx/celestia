# Phase 0 Research: Significators & Ruling Planets (SPEC-003)

## 1. The four-step significator algorithm (formalised)

For a `NatalChart` and house `H` (1..12):

Let `starLord(g)` = the nakshatra lord of graha `g`'s sidereal longitude
(`KpLordage.chainFor(chart.position(g).longitude()).starLord()`).
Let `bhava(g)` = `chart.placement(g).bhava()`.
Let `owner(H)` = `chart.cusp(H).lordChain().signLord()` — always one of the seven
non-nodes.

1. **Occupants** `O(H)` = `{ g : bhava(g) == H }` (may include Rahu / Ketu).
2. **Effective occupants** `EO(H)` = `O(H)` ∪ (for each node `N ∈ O(H)` its
   **agents**): `agents(N)` = `{ g : bhava(g) == bhava(N), g ≠ N, g not a node }`
   (conjunction — same bhava) ∪ `{ signLord(N) }` ∪ `{ starLord(N) }`, where
   `signLord(N)` is the lord of the sign `N` occupies and `starLord(N)` its
   nakshatra lord. (A node in a house acts as the agent of the planets it
   represents — standard KP.)
3. **Step 2 — occupants**: `EO(H)`.
4. **Step 1 — grahas in the star of the (effective) occupants**:
   `{ g : starLord(g) ∈ EO(H) }`. Star lords are the nine Vimshottari lords, so a
   graha can be in a node's star.
5. **Step 4 — owner**: `{ owner(H) }`.
6. **Step 3 — grahas in the star of the owner**: `{ g : starLord(g) == owner(H) }`.

**Merge & order**: union the four sets; a graha keeps the set of steps that
qualified it; sort by the graha's **minimum** step (1 strongest), then by `Graha`
enum order (Ketu, Venus, Sun, …) as a stable tie-break.

**Self-reference is allowed**: a graha in its own star is a step-1 (or step-3)
significator of the houses it occupies / owns — KP counts it.

**Owner is also an occupant**: it appears in steps 2 and 4, listed once tagged
`{OCCUPANT, OWNER}`, ordered by step 2.

**Empty house**: `O(H)` and step 1 are empty; steps 3 and 4 still yield the owner
and the grahas in the owner's star.

**Alternatives considered**: the "planets in the star of the *sub* lord" 4th-level
refinement, and dasha-filtered significators — both are later (SPEC-004). The
Sripati "bhava madhya" occupant definition is not KP and is not used (SPEC-002
research §3).

## 2. Per-graha significator table

`grahaSignificators(g)` = `{ H : g appears in significators(H) }`, each `H` tagged
with the steps by which `g` qualified for it. It is the **strict inverse** of the
twelve per-house lists (SC-002 is exactly this identity), computed by transposing
the per-house `Significator` records — no separate rule.

**Per-node view**: because a node's agents were folded into `EO(H)` (step 2 above),
the per-house lists already credit a house to a node when the node occupies it or
is in the star of one of its effective occupants. The node's own agency
significations are therefore `grahaSignificators(node)` with no extra step.

## 3. Ruling planets — sources and node rule

For a judgment `BirthData` (instant + place):

- Ascendant longitude → `KpLordage.chainFor` → `LAGNA_SIGN` (sign lord),
  `LAGNA_STAR` (star lord), `LAGNA_SUB` (sub lord).
- Moon longitude (from `PositionProvider` for the judgment instant) → likewise
  `MOON_SIGN`, `MOON_STAR`, `MOON_SUB`.
- `DAY_LORD` — the lord of the KP weekday (§4).
- `LAGNA_SUB` / `MOON_SUB` are included only when `includeSubLords` is true
  (default true — modern KP; false = classic KSK).

**Node addition** (`NODE` source): Rahu or Ketu is added when **any** of —
- its occupied-sign lord is already a ruling planet, or
- its occupied-star lord is already a ruling planet, or
- it is in the same sign as the Moon or the Ascendant, or
- it is in the same nakshatra as the Moon or the Ascendant.

The `includeNodeAspects` flag (default off) would additionally add a node when a
graha aspecting it is a ruling planet; the aspect scheme is out of scope for v1
(the flag and extension point are defined; enabling it is a later decision).

**Rationale**: this matches the common KP ruling-planet worksheet (KSK's *Readers*
and the standard KP horary method); the sub-lord inclusion and node handling are
the two points where practitioners differ, so both are switches.

## 4. Day lord — sunrise boundary

**`swe_rise_trans`**: `swe_rise_trans(double tjd_ut, int ipl=SE_SUN, StringBuffer
starname=null, int epheflag, int rsmi, double[] geopos={lon,lat,alt}, double
atpress=0, double attemp=0, DblObj tret, StringBuffer serr)` returns the **next**
rise (`rsmi = SE_CALC_RISE`) at or after `tjd_ut`; `tret.val` is the rise JD(UT).
KP sunrise = Sun's **upper limb** at the true horizon with standard refraction —
the port's default (no `SE_BIT_DISC_CENTER`, no `SE_BIT_NO_REFRACTION`).

**"The sunrise that started the current KP day"** = the latest sunrise `≤` the
judgment instant. Implementation: `r = nextRise(t − 1.05 days)`; while
`nextRise(r + ε) ≤ t`, advance `r`. Bounded (≤ 2 iterations); `SunriseProvider`
exposes this as `sunriseBefore(Instant, lat, lon)`.

**Weekday of that sunrise**: the KP weekday is the local civil day the sunrise
falls in. The true civil timezone is SPEC-007; here approximate the local date as
`sunriseInstant + longitude/15 hours`, take its UTC `LocalDate`, then
`getDayOfWeek()`. Sunrise is ~06:00 local and civil offsets from LMT are a few
hours at most, so the weekday from this approximation is essentially always
correct; note the caveat.

**Weekday → day lord**: Sunday→Sun, Monday→Moon, Tuesday→Mars, Wednesday→Mercury,
Thursday→Jupiter, Friday→Venus, Saturday→Saturn.

**No sunrise (polar / midnight sun)**: `swe_rise_trans` returns `−2` (no rise
within the search window). `SunriseProvider.sunriseBefore` returns
`Optional.empty()`; `KpWeekday` falls back to the **civil** (UTC-midnight-boundary,
LMT-adjusted) weekday and `RulingPlanets.dayLordFallback` is set true (FR-014).

**`swe_houses_armc` obliquity / ARMC** are not needed — `swe_rise_trans` is a
direct call.

## 5. Judgment context type

**Decision**: reuse `BirthData` (instant + latitude + longitude, already
validated) for the judgment moment. A distinct `JudgmentContext` record would be
structurally identical and add no value. The `RulingPlanetsFactory` parameter and
Javadoc name it "judgment", not "birth".

## 6. Golden reference extension

Extend `tools/ephe-crosscheck/compute_golden.py`:

- Replicate §1's four-step algorithm and §3's ruling-planet rules in Python
  (reusing the existing `lord_chain` helper and the SPEC-002 `bhava` / cusp
  computation).
- Add to each golden file: `expected.significators.by_house` (12 lists of
  `{graha, steps}`), `expected.significators.by_graha` (9 entries), and
  `expected.node_agency` (Rahu / Ketu agents + significations).
- Add **one** `ruling_planets` example to one golden file: a fixed judgment
  instant + place (independent of the birth data) → the RP set with sources and
  the resolved day lord + sunrise instant.

**SC-006**: transcribe the twelve per-house significator lists for **one** golden
chart from a KP textbook worked example (or two agreeing mainstream KP tools set
to KP-New ayanamsa + mean node + Placidus) and record it in
`verification.significators_human_check`.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | Four-step rule | occupants / star-of-occupants / owner / star-of-owner; nodes fold their agents into "effective occupants" |
| 2 | Per-graha table | strict inverse of the per-house lists (no separate rule) |
| 3 | Ruling planets | lagna+moon sign/star/sub lords + day lord; node added by sign/star/conjunction; `includeSubLords` default on |
| 4 | Day lord | `swe_rise_trans` sunrise-before; weekday from LMT date; no-sunrise → civil fallback + flag |
| 5 | Judgment context | reuse `BirthData` |
| 6 | Golden data | replicate the rules in `ephe-crosscheck`; one RP example; one chart human-verified (SC-006) |
| 7 | Node aspects | `includeNodeAspects` flag, default off; aspect scheme deferred |
