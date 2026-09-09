# Feature Specification: KP Horary 1–249 (SPEC-005)

**Feature Branch**: `005-kp-horary`

**Created**: 2026-09-10

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-005 KP Horary 1–249: number→sub map, fixed
ascendant, judgment inputs. Depends on: 002, 003." Engine tool
`castHoraryChart(number 1..249, judgment datetime, lat/lon)` → "horary chart +
cuspal sub-lords"; persistence `horary_query(id, chart_id, number_1_249,
question, judged_utc)`.

_KP horary (Prashna) answers a question from the **moment it is asked** plus a
number 1–249 the querent gives. The number fixes the Ascendant's sub-division —
and therefore the Ascendant sub lord, the KP determinant of the answer. The
planets and the other house cusps come from the judgment moment and place. The
result is an ordinary natal-chart structure, so the SPEC-003 significators and
ruling planets apply unchanged; the LLM agent turns "does the 11th cuspal sub lord
signify 2, 6, 10, 11" into a yes/no._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The 1–249 map (Priority: P1)

Given a number 1–249, the engine returns the **arc** of the zodiac it denotes: its
start and end sidereal longitude, its sign, and its full KP lord chain (sign lord,
nakshatra, star lord, sub lord, sub-sub lord). The 249 arcs are the 243 nakshatra
sub-lord divisions, each further split wherever it crosses a sign boundary, in
longitude order.

**Why this priority**: The number → sub-lord mapping is the whole point of KP
horary. Everything else (cusps, planets) is a normal chart; the number is what
makes it *this querent's* horary.

**Independent Test**: The 249 arcs tile `[0°, 360°)` with no gap or overlap; a
sample of numbers matches K. S. Krishnamurti's published 249 table (sub lord and
degree range).

**Acceptance Scenarios**:

1. **Given** any number 1–249, **When** its arc is requested, **Then** the arc has
   a start `<` end, a single sign, and a complete lord chain, and the sub lord is
   constant across the whole arc.
2. **Given** the 249 arcs in order, **When** they are concatenated, **Then**
   `arc[n].end == arc[n+1].start` and `arc[1].start == 0°`, `arc[249].end == 360°`.
3. **Given** a number outside 1–249, **When** its arc is requested, **Then** the
   request is rejected.

---

### User Story 2 - The horary Ascendant (Priority: P1)

Given a number 1–249, the engine returns the **horary Ascendant**: a fixed
sidereal longitude (the midpoint of arc *N*) with its KP lord chain, as an
Ascendant angle. This is independent of the clock.

**Why this priority**: The Ascendant sub lord *is* the horary answer's primary
significator. It must be available without computing a full chart.

**Independent Test**: The horary Ascendant of number *N* lies inside arc *N* and
has the same sub lord as arc *N*.

**Acceptance Scenarios**:

1. **Given** a number, **When** the horary Ascendant is requested, **Then** its
   longitude is the midpoint of that number's arc and its sub lord equals the
   arc's sub lord.
2. **Given** two numbers with the same sub lord (an arc split at a sign boundary),
   **When** their Ascendants are requested, **Then** both carry that sub lord but
   sit in different signs.

---

### User Story 3 - The full horary chart (Priority: P1)

Given a number 1–249, a judgment instant (UTC) and a place (latitude, longitude),
the engine returns a **horary chart** — the same structure as a natal chart —
where:

- the **Ascendant** (cusp 1) is fixed by the number (User Story 2), **not** by the
  clock;
- the other **eleven house cusps** are Placidus cusps consistent with that
  Ascendant at the judgment latitude (and the judgment instant's obliquity and
  ayanamsa);
- the **planets** are the sidereal (KP) positions for the judgment instant;
- every cusp carries its **cuspal sub lord**, and every graha its bhava and rasi
  house.

**Why this priority**: `castHoraryChart` returns this. The SPEC-003
`SignificatorTable` and the LLM's judgement consume it directly.

**Independent Test**: For a golden horary case, cusp 1 equals the number's
Ascendant; the twelve cuspal sub lords and the planetary placements match a
reference; feeding the chart to the SPEC-003 significators produces the reference
significators.

**Acceptance Scenarios**:

1. **Given** a number, instant and place, **When** the horary chart is cast,
   **Then** `chart.cusp(1)` longitude equals the number's horary Ascendant and
   `chart.ascendant()` matches it.
2. **Given** the same number and place but two different instants, **When** two
   charts are cast, **Then** cusp 1 is identical and the planetary positions
   differ.
3. **Given** the same instant and place but two different numbers, **When** two
   charts are cast, **Then** the planets are identical and the twelve cusps
   differ.
4. **Given** a judgment latitude at or beyond the Placidus limit (~66°), **When**
   a chart is cast, **Then** the request is rejected (Placidus undefined), the
   same as a natal chart.

---

### User Story 4 - Horary ruling planets (Priority: P2)

Given a number, a judgment instant and a place, the engine returns the **ruling
planets** for that moment — using the **number's** Ascendant (not the clock's) for
the lagna sign / star / sub lords, and the judgment instant for the Moon and the
day lord.

**Why this priority**: Ruling planets are the KP horary confirmation filter. They
must use the horary Ascendant to be meaningful.

**Independent Test**: The horary ruling planets match `RulingPlanetsFactory`
computed with the number's Ascendant longitude and the judgment Moon / day lord.

**Acceptance Scenarios**:

1. **Given** a number and a judgment moment, **When** the horary ruling planets
   are requested, **Then** the lagna sign / star / sub lords are those of the
   number's Ascendant, and the Moon lords and day lord are those of the judgment
   moment.

---

### User Story 5 - Reproducible correctness (Priority: P2)

The golden set is extended with horary cases: a number + instant + place → the
Ascendant, the twelve cusps, the planetary placements, the significators and the
ruling planets. A determinism test and the 249-tiling property run in CI.

**Independent Test**: The horary golden suite in CI; a determinism test; the
249-arc tiling property.

**Acceptance Scenarios**:

1. **Given** the horary golden set, **When** the suite runs, **Then** zero values
   differ from the recorded snapshots.
2. **Given** a change to the 249 table or the Ascendant-to-cusps rule, **Then**
   the engine version changes and the suite flags every affected value.

---

### Edge Cases

- A sub-lord division that begins or ends exactly on a sign boundary → **no**
  split there (it does not straddle the boundary); the 249 count comes only from
  divisions that genuinely cross a sign cusp.
- A number whose arc is very short (a sliver left after a sign-boundary split) →
  still has a well-defined midpoint, sign and lord chain.
- The horary Ascendant midpoint falling within one arc-second of a sub-sub-lord
  boundary → the sub lord (what is judged) is unaffected; the sub-sub lord is
  taken at the midpoint and is stable.
- Judgment instant outside the supported ephemeris range → planets and obliquity /
  ayanamsa computed with `Accuracy.REDUCED`, consistent with SPEC-001; the chart
  is still produced.
- Judgment latitude beyond the Placidus limit → rejected, as for a natal chart
  (SPEC-002).
- Two judgment instants a few minutes apart → the same cusps (cusp 1 fixed by the
  number, the rest by the number + latitude + the slowly-varying obliquity /
  ayanamsa) but different fast-moving planets (Moon).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST expose the **249 arcs**: the 243 nakshatra
  sub-lord divisions (SPEC-001 `VimshottariPartition`), each split wherever it
  crosses one of the twelve 30° sign boundaries, numbered 1–249 in increasing
  longitude order.
- **FR-002**: Each arc MUST carry a start and end sidereal longitude
  (`start < end`), a single sign, and the full KP lord chain (sign lord,
  nakshatra, star lord, sub lord, sub-sub lord) of its interior.
- **FR-003**: The 249 arcs MUST tile `[0°, 360°)` exactly — `arc[1].start = 0`,
  `arc[249].end = 360`, `arc[n].end = arc[n+1].start` — with exact (`BigFraction`)
  boundaries.
- **FR-004**: A number outside 1–249 MUST be rejected.
- **FR-005**: The **horary Ascendant** of a number MUST be the midpoint of that
  number's arc, with the KP lord chain of that longitude, presented as an
  Ascendant angle.
- **FR-006**: `castHoraryChart(number, judgmentInstant, latitude, longitude)` MUST
  return a chart with the same shape as a natal chart (SPEC-002 `NatalChart`):
  twelve cusps, the Ascendant and Midheaven, planetary positions, bhava and rasi
  placements, cuspal sub lords.
- **FR-007**: In a horary chart, **cusp 1 (the Ascendant) MUST equal the number's
  horary Ascendant longitude** — not the longitude the judgment instant and place
  would produce.
- **FR-008**: The other eleven cusps MUST be **Placidus** cusps consistent with
  the horary Ascendant at the judgment latitude, using the obliquity and ayanamsa
  of the judgment instant (KP-New ayanamsa, sidereal).
- **FR-009**: The **planets** in a horary chart MUST be the sidereal KP positions
  for the judgment instant, identical to those a natal chart for that instant
  would contain.
- **FR-010**: A judgment latitude at or beyond the configured Placidus limit MUST
  be rejected the same way a natal chart is (SPEC-002 `PlacidusUndefinedException`).
- **FR-011**: A judgment instant outside the supported ephemeris range MUST still
  yield a chart, with `Accuracy.REDUCED`, consistent with SPEC-001.
- **FR-012**: The **horary ruling planets** MUST be the SPEC-003 ruling planets
  computed with the number's Ascendant longitude for the lagna lords and the
  judgment instant for the Moon lords and the day lord.
- **FR-013**: The SPEC-003 `SignificatorTable` and the SPEC-004 dasha (if a birth
  chart is also present) MUST consume a horary chart without modification — a
  horary chart *is* a `NatalChart`.
- **FR-014**: For identical inputs the engine MUST produce identical output across
  runs and platforms.
- **FR-015**: The engine version MUST be carried on the outputs and MUST change
  when the 249 table, the Ascendant-to-cusps rule, or an upstream rule changes.
- **FR-016**: The implementing code MUST keep the pure modules pure
  (`core`, and `ephemeris` for the positions and the Ascendant-seeded houses); the
  Swiss Ephemeris type MUST NOT appear in any public API.
- **FR-017**: Every KP rule added MUST cite a source in `core/REFERENCES.md`.

### Key Entities *(include if data involved)*

- **Horary number**: an integer 1–249.
- **Horary arc**: for one number — start / end sidereal longitude, sign, and the
  KP lord chain of the arc.
- **Horary Ascendant**: the fixed Ascendant angle (midpoint longitude + lord
  chain) for a number.
- **Horary chart**: a natal-chart structure whose Ascendant is fixed by the
  number and whose planets and other cusps are for the judgment moment and place;
  carries the accuracy flag and the engine version.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The 249 arcs tile `[0°, 360°)` with exact boundaries — zero gap,
  zero overlap — and there are exactly **249** of them.
- **SC-002**: For a horary golden case, `chart.cusp(1)` equals the number's horary
  Ascendant, and the twelve cuspal sub lords and the nine planetary placements
  (bhava, rasi) match the reference with zero discrepancies.
- **SC-003**: Feeding a horary golden chart to the SPEC-003 `SignificatorTable`
  reproduces the reference significators exactly (the horary chart is a valid
  `NatalChart`).
- **SC-004**: Two horary charts for the same number and place at instants five
  minutes apart have identical cusps and a Moon longitude that differs by the
  Moon's five-minute motion (~2.5′).
- **SC-005**: Casting a horary chart is deterministic and completes in under
  **75 ms** on a developer machine; the horary golden snapshot suite passes
  unchanged in CI on every supported platform.
- **SC-006**: For at least one number, the arc (sub lord and degree range) is
  checked against K. S. Krishnamurti's published 249 table and recorded as
  human-verified; for at least one horary case, cusp 1 and one other cuspal sub
  lord are checked against a mainstream KP horary tool.

## Assumptions

- **The 249 table is derived, not transcribed**: it is `VimshottariPartition`'s
  243 sub-spans split at the twelve sign cusps. Because the derivation and KSK's
  published table encode the same rule, **SC-006 requires a human cross-check** of
  a sample of rows.
- **The horary Ascendant is the arc midpoint** — an unambiguous, stable choice.
  The sub lord (the judged quantity) is constant over the whole arc, so the exact
  degree within the arc does not change the judgement; the midpoint also gives a
  well-defined sub-sub lord and house cusps.
- **The other cusps come from a solved RAMC**: given the tropical Ascendant
  (sidereal horary Ascendant + the instant's ayanamsa), the latitude and the
  instant's obliquity, the right ascension of the Midheaven is obtained by a
  direct closed-form inversion of the Ascendant formula, then the twelve Placidus
  cusps are computed from that RAMC and converted back to sidereal.
- **The judgment moment supplies** the planets, the obliquity, the ayanamsa and
  the day lord — exactly the inputs a natal chart and the SPEC-003 ruling planets
  already use.
- **A horary chart is represented as a SPEC-002 `NatalChart`** — no new aggregate
  type — so all downstream KP machinery works unchanged.
- **Horary judgement** (which cusp is relevant for the question, whether its sub
  lord signifies the favourable houses, the yes/no) is **not** this feature — it
  is the LLM agent reading the significators, or a later rules spec.
- The querent's own natal chart, question text, and the entitlement / quota checks
  are SPEC-008+ concerns.

## Dependencies

- **SPEC-001** — `VimshottariPartition.subDivisions()` (the 243 sub-spans),
  `KpLordage` / `LordChain`, `Sign`, `PositionProvider`, `BirthData`,
  `EngineVersion`, `Accuracy`, `TimeScales`, `Span`.
- **SPEC-002** — `NatalChart`, `Cusp`, `AnglePoint`, `HousePlacement`,
  `HouseResult`, `Angle`, `PlacidusUndefinedException`, the golden-chart harness.
- **SPEC-003** — `SignificatorTable`, `RulingPlanetsFactory` / `RulingPlanets`
  (consume the horary chart / Ascendant unchanged).
- **New in `ephemeris`** — houses from a **given Ascendant longitude** (RAMC
  inversion + Placidus), not from an instant.
- **Consumed by** SPEC-006 (daily-prediction / judgement rules) and SPEC-008
  (persistence: `chart` kind `HORARY`, `horary_query`).
