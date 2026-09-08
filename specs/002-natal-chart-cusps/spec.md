# Feature Specification: Natal Chart & Placidus Cusps (SPEC-002)

**Feature Branch**: `002-natal-chart-cusps`

**Created**: 2026-09-08

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-002 Natal chart + Placidus cusps + Rasi/Bhava
+ cuspal sub-lords. Depends on: 001."

_Builds the natal chart on top of SPEC-001's positions: the twelve KP house cusps,
the Ascendant, where each graha sits by house, and — the heart of KP — the sub
lord of every cusp. The consumers are the KP judgment specs (significators, ruling
planets, dasha, horary) and, ultimately, every answer a subscriber receives._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The twelve cusps and the Ascendant (Priority: P1)

Given a birth instant (UTC) and a birth location (latitude, longitude), the engine
returns the twelve **Placidus** house cusps and the Ascendant and Midheaven, all
as **sidereal** ecliptic longitudes under the KP-New ayanamsa, each carrying its
full KP lord chain — in particular the **cuspal sub lord**.

**Why this priority**: The cuspal sub lord is *the* KP determinant of a matter
(7th cusp sub lord for marriage, 10th for career, 11th for gains, …). Nothing in
KP judgement works without it.

**Independent Test**: Feed the golden charts' birth data (instant + lat/lon) and
compare the twelve cusp longitudes and their sub lords to the reference.

**Acceptance Scenarios**:

1. **Given** a golden chart's birth data, **When** the chart is cast, **Then**
   each of the twelve cusp longitudes matches the reference within tolerance and
   each cuspal sub lord matches exactly.
2. **Given** any valid birth data, **When** the chart is cast, **Then** cusp 1
   equals the Ascendant longitude, and cusp _n_ and cusp _n+1_ are ordered
   forward around the zodiac (allowing one wrap through 360°).
3. **Given** a birth exactly at the equator at local noon on an equinox, **Then**
   a chart is still produced (no special-case failure).

---

### User Story 2 - Bhava placement of the grahas (Priority: P1)

For each of the nine grahas, the engine reports which **bhava** (KP house) it
occupies — determined by which two consecutive cusps its longitude falls between,
**not** by sign.

**Why this priority**: KP significators are built from "planets in a house" and
"planets in the star of the occupants of a house"; that "in a house" is the bhava
placement, and it often differs from the sign-based house.

**Independent Test**: For each golden chart, assert each graha's bhava against the
reference, and assert consistency (a graha is in bhava _n_ iff its longitude is in
`[cusp n, cusp n+1)` on the circle).

**Acceptance Scenarios**:

1. **Given** a cast chart, **When** bhava placement is requested, **Then** every
   graha is assigned exactly one bhava in 1..12.
2. **Given** a graha whose longitude equals a cusp longitude exactly, **Then** it
   is placed in the bhava that the cusp *starts* (half-open `[cusp n, cusp n+1)`).
3. **Given** a golden chart, **Then** each graha's bhava matches the published /
   reference value.

---

### User Story 3 - Rasi (sign-based) chart (Priority: P2)

The engine also reports the traditional sign-based house of each graha: house 1 is
the Ascendant's whole sign, house 2 the next sign, and so on; a graha's rasi-house
is counted in whole signs from the Ascendant's sign to its own sign.

**Why this priority**: Some KP practitioners and most cross-checking tools show
the Rasi chart; it is useful for verification and for the natal-chart display, but
KP judgement itself uses the bhava/cuspal view.

**Independent Test**: Ascendant sign → house 1; a graha in the same sign as the
Ascendant is in house 1; a graha one sign ahead is in house 2.

**Acceptance Scenarios**:

1. **Given** a cast chart, **When** the Rasi chart is requested, **Then** every
   graha has a rasi-house in 1..12 equal to `1 + (graha.sign − ascendant.sign) mod 12`.
2. **Given** a golden chart, **Then** the rasi-house of each graha matches the
   reference.

---

### User Story 4 - The natal chart as one object (Priority: P2)

The engine assembles a single immutable `NatalChart` bundling: the SPEC-001
positions, the twelve cusps + Ascendant/MC with their lord chains, each graha's
bhava and rasi house, the cuspal sub lords, the ayanamsa, and the engine version.

**Why this priority**: SPEC-003 (significators), SPEC-004 (dasha) and SPEC-005
(horary) all consume "the chart"; a stable aggregate keeps them from re-deriving
pieces and drifting.

**Independent Test**: Cast a golden chart; assert the aggregate exposes every
piece and that `engineVersion` is populated and stable.

**Acceptance Scenarios**:

1. **Given** birth data, **When** `NatalChart` is built, **Then** it contains
   nine graha placements and twelve cusps, and `chart.cuspSubLord(7)` returns the
   same graha as `chart.cusp(7).lordChain().subLord()`.
2. **Given** identical birth data, **When** a chart is built twice, **Then** the
   two `NatalChart` values are equal.

---

### User Story 5 - High-latitude births (Priority: P3)

Placidus house cusps are mathematically undefined above the polar circle
(~66°34′). The engine defines a single behaviour: a birth latitude at or beyond a
configurable limit (default 66.0°) is **rejected** with a specific error that
names the problem; the Ascendant and MC (which remain defined) are still
obtainable through a separate call.

**Why this priority**: Rare for birth places, but the behaviour must be defined,
not a silent wrong answer or an opaque crash (ADR-0004).

**Independent Test**: A birth at 70°N → the documented rejection; a birth at 65°N
→ a normal chart.

**Acceptance Scenarios**:

1. **Given** a birth latitude ≥ the limit, **When** a full chart is cast, **Then**
   a `PlacidusUndefinedException` (or equivalent) is thrown, naming the latitude.
2. **Given** the same birth, **When** only the Ascendant/MC is requested, **Then**
   they are returned normally.
3. **Given** a birth latitude just below the limit, **When** a chart is cast,
   **Then** it succeeds.

---

### User Story 6 - Reproducible correctness (Priority: P2)

The golden charts from SPEC-001 are extended with the twelve cusp longitudes,
cuspal sub lords, each graha's bhava, and the rasi houses; the natal-chart
snapshot suite reproduces every value on every run and platform.

**Independent Test**: The extended golden-chart suite in CI; a determinism test
that casts the same chart twice.

**Acceptance Scenarios**:

1. **Given** the extended golden set, **When** the suite runs, **Then** zero
   values differ from the recorded snapshots.
2. **Given** a change to a cusp or bhava rule, **Then** the engine version changes
   and the suite flags every affected value.

---

### Edge Cases

- A graha longitude exactly equal to a cusp longitude → half-open
  `[cusp n, cusp n+1)`; it belongs to bhava _n_.
- The cusp ring wraps through 0°/360° (it always contains exactly one wrap);
  "between cusp n and n+1" must handle that wrap.
- Two adjacent cusps very close together (intercepted-sign situations at high but
  sub-polar latitudes) — bhava assignment must still be unambiguous.
- Birth latitude beyond ±66° → rejection (US5); latitude beyond ±90° or longitude
  beyond ±180° → invalid input, a plain `IllegalArgumentException`.
- Southern hemisphere and the far east/west — no special handling; the same
  Placidus computation applies.
- A birth instant outside the supported ephemeris range → cusps are still returned
  with `Accuracy.REDUCED`, consistent with SPEC-001 (the sidereal time and houses
  do not themselves need the data files, but the ayanamsa and nutation do).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST accept a UTC instant plus a birth latitude and
  longitude and compute the twelve **Placidus** house cusps as sidereal ecliptic
  longitudes under the KP-New ayanamsa (ADR-0004, ADR-0003).
- **FR-002**: The engine MUST compute the sidereal **Ascendant** and **Midheaven**
  for the same instant and location.
- **FR-003**: Cusp 1 MUST equal the Ascendant longitude.
- **FR-004**: The engine MUST attach the full KP lord chain (sign, sign lord,
  nakshatra, pada, star lord, sub lord, sub-sub lord) to every cusp and to the
  Ascendant and Midheaven, reusing the SPEC-001 decomposition.
- **FR-005**: The engine MUST expose the **cuspal sub lord** of each of the twelve
  cusps as a first-class value (it is the KP determinant).
- **FR-006**: The engine MUST assign each of the nine grahas to exactly one
  **bhava** in 1..12, defined by the half-open arc `[cusp n, cusp n+1)` around the
  zodiac (FR-011 boundary convention from SPEC-001 applies).
- **FR-007**: The engine MUST assign each graha a **rasi house** in 1..12, counted
  in whole signs from the Ascendant's sign. (A cusp's whole-sign house is trivial
  and unused; the Bhava chart, FR-006, is the judgement view.)
- **FR-008**: The engine MUST assemble an immutable `NatalChart` aggregate
  containing the SPEC-001 positions, the cusps + Ascendant/MC with lord chains,
  the per-graha bhava and rasi house, the cuspal sub lords, the ayanamsa, and the
  engine version.
- **FR-009**: A birth latitude whose absolute value is at or above the configured
  polar limit (default 66.0°) MUST cause a full-chart cast to fail with a specific,
  named exception; the Ascendant/MC MUST still be obtainable separately.
- **FR-010**: A latitude with absolute value > 90° or a longitude with absolute
  value > 180° MUST be rejected as invalid input.
- **FR-011**: For identical input the engine MUST produce identical output across
  runs and platforms; no wall clock, locale, or environment dependence.
- **FR-012**: The engine version MUST change when any cusp, bhava, or rasi rule or
  the ayanamsa changes, and MUST be carried on `NatalChart`. (The house system is
  compile-time fixed to Placidus, so it cannot vary at runtime.)
- **FR-013**: An instant outside the supported ephemeris range MUST still yield a
  chart, with `Accuracy.REDUCED` on the positions, consistent with SPEC-001
  (FR-016 there).
- **FR-014**: The Swiss Ephemeris library type MUST NOT appear in this feature's
  public API (as in SPEC-001, FR-018).
- **FR-015**: Every astrological rule added MUST cite a source in
  `core/REFERENCES.md`.
- **FR-016**: The implementing code MUST remain in the pure modules
  (`ephemeris`, `core`) — no framework, database, or network dependency.

### Key Entities *(include if feature involves data)*

- **Birth data**: a UTC instant plus a geographic latitude and longitude (the
  place → lat/lon step is SPEC-007).
- **Cusp**: a house number (1..12), a sidereal longitude, and its lord chain.
- **Angle point**: the Ascendant or Midheaven — a longitude and its lord chain
  (the type `AnglePoint`; `Angle` is the enum naming which one).
- **House placement**: for one graha — its bhava (cusp-based) and its rasi house
  (sign-based).
- **NatalChart**: the immutable aggregate of positions, cusps, angles, placements,
  cuspal sub lords, ayanamsa, and engine version.
- **Polar limit**: the configurable latitude beyond which Placidus is refused.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every golden chart, the twelve **cuspal sub lords** match the
  reference with **zero** discrepancies, and each cusp longitude matches within
  **1 arc-minute** of the reference.
- **SC-002**: For every golden chart, every graha's **bhava** and **rasi house**
  match the reference with zero discrepancies.
- **SC-003**: Cusp 1 equals the Ascendant longitude exactly (bit-identical), for
  every chart.
- **SC-004**: Bhava assignment is internally consistent for every graha of every
  chart: graha in bhava _n_ ⇔ its longitude lies in `[cusp n, cusp n+1)` on the
  circle (property test).
- **SC-005**: Casting the same chart twice yields an equal `NatalChart`; the
  extended golden-chart snapshot suite passes unchanged in CI on every supported
  platform.
- **SC-006**: A full natal chart (positions + cusps + placements + lord chains)
  for one birth completes in under **75 ms** on a developer machine.
- **SC-007**: A birth at |lat| ≥ 66° is refused with the named exception; a birth
  at |lat| = 65° succeeds; a birth at |lat| = 91° is an `IllegalArgumentException`.

## Assumptions

- Birth latitude/longitude are inputs (decimal degrees, + = North / East). The
  place-name → lat/lon step and the local-time → UTC step are SPEC-007; SPEC-002's
  golden charts pre-compute both and document them (as SPEC-001's do).
- House system is **Placidus**, sidereal, KP-New ayanamsa — fixed, not
  configurable (ADR-0004, ADR-0003).
- The Ascendant, MC and cusps use the Swiss Ephemeris `swe_houses` computation
  with `hsys = 'P'` and the sidereal flag; the library binding stays behind an
  interface, as SPEC-001's `PositionProvider` does.
- Polar limit default is **66.0°** and is configuration, not a magic number in the
  algorithm. (66°34′ is the true Arctic/Antarctic circle; 66.0° is a safe margin.)
- "Bhava" here is the KP cusp-to-cusp definition (a planet is *in* the house whose
  cusp it has most recently passed), not the Sripati/equal-bhava midpoint method.
- The golden-chart reference for cusps and bhava is produced by extending
  `tools/ephe-crosscheck` (pyswisseph `swe_houses`), the same independent path
  used for SPEC-001 positions. A textbook cross-check of at least one chart's
  cuspal sub lords is a follow-up, as in SPEC-001.
- Rasi-house counting is whole-sign from the Ascendant's sign (the common KP
  convention for the Rasi chart); the Bhava chart is the judgement view.

## Dependencies

- **SPEC-001** — `PositionProvider`, `KpLordage`/`LordChain`, `Graha`, `Sign`,
  `Nakshatra`, `Longitudes`, `EngineVersion`, `Accuracy`. Merged to `master`.
- **ADR-0003** (ayanamsa), **ADR-0004** (Placidus, sidereal) — accepted.
- **Golden-chart reference** extended with cusp / bhava / rasi values (a
  prerequisite for the correctness suite; the birth lat/lon are already in the
  golden files).
- SPEC-003, SPEC-004, SPEC-005 depend on this feature's `NatalChart`.
