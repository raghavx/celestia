# Feature Specification: Significators & Ruling Planets (SPEC-003)

**Feature Branch**: `003-significators-ruling-planets`

**Created**: 2026-09-08

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-003 Significators (4-step) + Rahu/Ketu
agency + Ruling Planets. Depends on: 002."

_The heart of KP judgement. Given a natal chart, which planets carry the affairs
of each house (significators)? And at a given moment, which planets are "ruling"
(ruling planets)? SPEC-004 (dasha) and SPEC-005 (horary) select from these
outputs; the LLM agent turns them into an answer._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Four-step significators of a house (Priority: P1)

Given a natal chart and a house number (1–12), the engine returns the grahas that
signify that house, each tagged with which of the four KP steps produced it, and
ordered by KP strength:

1. grahas in the **star** (nakshatra) of an **occupant** of the house
2. **occupants** of the house (grahas in that bhava)
3. grahas in the **star** of the **owner** of the house (the sign lord of the house cusp)
4. the **owner** of the house

**Why this priority**: Every KP prediction is "the sub lord of the relevant cusp
must be a significator of the favourable houses for the matter". Without the
significator list there is no judgement.

**Independent Test**: For each golden chart, the four-step significators of all 12
houses match a reference table.

**Acceptance Scenarios**:

1. **Given** a chart and a house with occupants, **When** its significators are
   requested, **Then** all four steps are populated and each returned graha
   carries the step(s) it came from.
2. **Given** a chart and a house with **no** occupants, **When** its significators
   are requested, **Then** steps 1 and 2 are empty and steps 3 and 4 still yield
   the owner and the grahas in the owner's star.
3. **Given** a graha that qualifies via more than one step for the same house,
   **When** the list is returned, **Then** it appears once, tagged with all the
   steps that qualified it, ordered by its strongest step.

---

### User Story 2 - Per-graha significator table (Priority: P1)

For each of the nine grahas, the engine returns the set of houses it signifies —
the inverse of User Story 1. A graha signifies house H iff it appears in the
four-step significators of H.

**Why this priority**: KP judgement asks "does the 7th cuspal sub lord signify
2, 7 or 11?" — that is a per-graha lookup. Both views are needed.

**Independent Test**: For every graha G and house H, `G signifies H` in the
per-graha table iff `G` is in the four-step significators of `H`.

**Acceptance Scenarios**:

1. **Given** a chart, **When** the per-graha table is built, **Then** it is the
   exact inverse of the twelve per-house lists.
2. **Given** a graha, **When** its significations are requested, **Then** each
   house is tagged with the step(s) by which the graha signifies it.

---

### User Story 3 - Rahu / Ketu agency (Priority: P2)

Rahu and Ketu have no sign rulership, so they signify by **agency**: a node
signifies the houses signified by (a) any graha it is **conjoined** with in the
same bhava, (b) the lord of the **sign** it occupies, and (c) the lord of the
**star** it occupies. Graha aspects to the nodes are **not** used by default (KP
practice varies); this is a configuration flag, default off.

**Why this priority**: The nodes are frequently the cuspal sub lord and are
treated by KP as very strong; getting their significations right is essential, but
the four-step machinery for the other seven must exist first.

**Independent Test**: For each golden chart, each node's agency-derived
significations match the reference.

**Acceptance Scenarios**:

1. **Given** a node conjoined with a graha in the same bhava, **When** its
   significations are computed, **Then** they include every house that graha signifies.
2. **Given** a node alone in its bhava, **When** its significations are computed,
   **Then** they are the union of the houses signified by its sign lord and its
   star lord.
3. **Given** a node whose sign lord signifies houses {2, 11} and whose star lord
   signifies {6, 10}, **When** its significations are computed, **Then** they are
   {2, 6, 10, 11} (graha aspects to the node are not used in v1).

---

### User Story 4 - Ruling Planets for a moment (Priority: P1)

Given a judgment instant (UTC) and a place (latitude, longitude), the engine
returns the **ruling planets**: the sign lord, star lord and sub lord of the
Ascendant; the sign lord, star lord and sub lord of the Moon; and the **day
lord** (the lord of the KP weekday, which runs from local sunrise to the next
local sunrise). Rahu / Ketu are added when a node occupies the sign or star of any
ruling planet, or is conjoined with the Moon or the Ascendant lord.

**Why this priority**: Ruling planets are the KP timing filter — the fructifying
planets at the moment of judgement, and the primary tool for horary and event
timing. Wanted alongside the significators.

**Independent Test**: For a golden chart plus a fixed judgment instant and place,
the ruling-planet set matches the reference; a judgement one minute before local
sunrise uses the previous day's lord.

**Acceptance Scenarios**:

1. **Given** a judgment instant and place, **When** ruling planets are requested,
   **Then** the result names each ruling planet with its source (lagna sign /
   lagna star / lagna sub / moon sign / moon star / moon sub / day lord / node).
2. **Given** a judgment instant just after local sunrise, **When** the day lord is
   computed, **Then** it is the lord of that weekday; **given** an instant just
   before local sunrise on the same date, **Then** it is the previous day's lord.
3. **Given** a node in the star of the lagna star lord, **When** ruling planets
   are requested, **Then** that node is included.
4. **Given** the sub-lord inclusion flag is off (classic KSK), **When** ruling
   planets are requested, **Then** only sign lords, star lords and the day lord
   (plus nodes) are returned.

---

### User Story 5 - Reproducible correctness (Priority: P2)

The golden charts are extended with the twelve per-house significator lists, the
per-graha table, the node agencies, and one worked ruling-planet example (chart +
judgment instant + place → RP set). The snapshot suite reproduces every value on
every run and platform.

**Independent Test**: The extended golden suite in CI; a determinism test.

**Acceptance Scenarios**:

1. **Given** the extended golden set, **When** the suite runs, **Then** zero
   values differ from the recorded snapshots.
2. **Given** a change to a significator or ruling-planet rule, **Then** the engine
   version changes and the suite flags every affected value.

---

### Edge Cases

- A house with an occupant that is a node → the node is a step-2 occupant, and its
  own agency (US3) also contributes; the node's *agency* significations are added
  to the house's significators as step 2.
- Two grahas in exact conjunction (same bhava, near-identical longitude) → both
  are step-1 sources for each other's star only if in each other's star; conjunction
  matters for node agency, not for steps 1–4.
- A graha in its **own** star → it is a step-1 significator of the houses it
  occupies and owns (self-reference is allowed; KP counts it).
- The owner of a house is also an occupant of it → appears in steps 2 and 4,
  listed once tagged with both.
- Ruling planets: judgement exactly at the computed sunrise instant → belongs to
  the new day (sunrise starts the day; half-open, consistent with SPEC-001/002).
- Polar latitude for ruling planets → the Ascendant is via `anglesOnly` (SPEC-002),
  but if the Sun does not rise/set that day, the day lord falls back to the civil
  (midnight-boundary) weekday and the result is flagged.
- Judgment instant outside the supported ephemeris range → computed with
  `Accuracy.REDUCED`, consistent with SPEC-001.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Given a `NatalChart` and a house 1–12, the engine MUST return the
  four-step significators: (1) grahas in the star of the house's occupants,
  (2) the occupants, (3) grahas in the star of the house owner, (4) the owner.
- **FR-002**: The **owner** of a house MUST be the sign lord of that house's cusp
  (`chart.cusp(house).lordChain().signLord()`).
- **FR-003**: **Occupants** of a house MUST be the grahas whose bhava equals that
  house (`chart.placement(g).bhava()`), i.e. the cuspal (bhava) view, not the rasi view.
- **FR-004**: A graha's **star lord** for step 1 / step 3 MUST be the nakshatra
  lord of the graha's own sidereal longitude (SPEC-001 `KpLordage`).
- **FR-005**: Each returned significator MUST carry the set of steps (1–4) that
  qualified it; the per-house list MUST be de-duplicated and ordered by each
  graha's strongest (lowest-numbered) qualifying step, then by a stable
  tie-break.
- **FR-006**: The engine MUST produce the inverse **per-graha significator table**:
  for each graha, the houses it signifies with the qualifying steps, consistent
  with the per-house lists.
- **FR-007**: For **Rahu** and **Ketu**, significations MUST be derived by agency —
  the union of the houses signified by: any graha conjoined with the node in the
  same bhava, the lord of the node's occupied sign, and the lord of the node's
  occupied star. Graha aspects to the nodes are **not** used in v1; the extension
  point is a later `SignificatorTable.of(chart, options)` overload (there is no
  aspect scheme yet).
- **FR-008**: A node MUST also appear as a step-2 occupant of its own bhava, and
  its agency significations MUST be merged into that bhava's significator list.
- **FR-009**: Given a judgment instant (UTC) and a place, the engine MUST return
  the ruling planets: sign lord, star lord and sub lord of the Ascendant; sign
  lord, star lord and sub lord of the Moon; and the day lord.
- **FR-010**: The **day lord** MUST be the lord of the KP weekday, where the day
  runs from local **sunrise** to the next local sunrise (Sun = Sunday, Moon =
  Monday, Mars = Tuesday, Mercury = Wednesday, Jupiter = Thursday, Venus = Friday,
  Saturn = Saturday).
- **FR-011**: Rahu / Ketu MUST be added to the ruling planets when the lord of the
  sign or the star (nakshatra) the node occupies is already a ruling planet, or
  when the node shares the sign or the nakshatra of the Moon or the Ascendant.
  (Sign/star sharing — no orb-based conjunction.)
- **FR-012**: Each ruling planet MUST carry its source(s) (lagna-sign, lagna-star,
  lagna-sub, moon-sign, moon-star, moon-sub, day-lord, node).
- **FR-013**: Sub-lord inclusion in the ruling planets MUST be controlled by a
  flag (`includeSubLords`), default **on** (modern KP); off gives the classic KSK
  set.
- **FR-014**: If the Sun neither rises nor sets on the judgment date at the given
  latitude, the day lord MUST fall back to the civil (midnight-boundary) weekday
  and the result MUST carry a flag indicating the fallback.
- **FR-015**: For identical inputs the engine MUST produce identical output across
  runs and platforms.
- **FR-016**: The engine version MUST change when any significator or
  ruling-planet rule changes, and MUST be carried on the outputs.
- **FR-017**: A judgment instant outside the supported ephemeris range MUST still
  yield a result, with `Accuracy.REDUCED`, consistent with SPEC-001.
- **FR-018**: The implementing code MUST stay in the pure modules
  (`ephemeris`, `core`); the Swiss Ephemeris type MUST NOT appear in any public
  API (as in SPEC-001 / SPEC-002).
- **FR-019**: Every KP rule added MUST cite a source in `core/REFERENCES.md`.

### Key Entities *(include if feature involves data)*

- **Significator**: a graha, the house it signifies, and the set of steps (1–4)
  that qualified it.
- **House significators**: for one house — the ordered, de-duplicated list of
  significators.
- **Graha significators**: for one graha — the houses it signifies, with steps.
- **Node agency**: for Rahu or Ketu — the sources (conjunct graha, sign lord, star
  lord) and the resulting significations.
- **Ruling planet**: a graha and its source(s) at a judgment moment.
- **Ruling planets**: the set for one judgment instant + place, with an
  accuracy flag and a day-lord-fallback flag.
- **KP weekday**: the weekday determined by the local sunrise boundary.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every golden chart, the four-step significators of all 12
  houses match the reference with **zero** discrepancies (membership and the step
  tags), and the ordering is stable.
- **SC-002**: The per-graha significator table is the exact inverse of the twelve
  per-house lists for every golden chart (property check).
- **SC-003**: For the worked ruling-planet example (a golden chart + a fixed
  judgment instant + place), the ruling-planet set and each planet's sources
  match the reference exactly.
- **SC-004**: The day lord respects the sunrise boundary — for a judgment instant
  one minute before the computed local sunrise, the day lord is the previous
  day's lord; one minute after, the current day's.
- **SC-005**: Computing all 12 houses' significators plus the per-graha table for
  a chart is deterministic and completes in under **20 ms** on a developer
  machine; the extended golden snapshot suite passes unchanged in CI on every
  supported platform.
- **SC-006**: For at least one golden chart, the twelve per-house significator
  lists are additionally checked against a published KP source (textbook worked
  example, or two agreeing mainstream KP tools) and recorded as human-verified.

## Assumptions

- **Significators** operate on a `NatalChart` (SPEC-002) — bhava occupants, cuspal
  owners, and per-graha lord chains derived from the stored positions via
  `KpLordage`.
- **House owner** = sign lord of the house cusp (the standard KP definition);
  a house with an intercepted sign still has exactly one cusp and one owner.
- **Occupant** = bhava (cusp-to-cusp) placement, per SPEC-002; not the rasi house.
- **Node agency** default = conjunction (same bhava) + occupied-sign lord +
  occupied-star lord. Graha aspects to the nodes are off by default; the aspect
  scheme, if ever enabled, is a later decision.
- **Ruling planets** are computed for the **judgment** moment (which may be
  "now", or a horary instant), not the birth moment — they need a fresh Ascendant
  and Moon for that instant and place.
- **Day lord** uses local **sunrise** (Sun's upper limb at the horizon with
  standard refraction) via a new `SunriseProvider` (Swiss Ephemeris
  `swe_rise_trans`). Sub-lord inclusion in RP defaults on (modern KP).
- **Sub lord** (of lagna / Moon) is the KP sub lord from `KpLordage`.
- The golden reference for significators and RP is produced by extending
  `tools/ephe-crosscheck` with the same four-step and RP rules; because the tool
  and the engine then share those rules, **SC-006 requires a human/textbook
  cross-check** of at least one chart's significator table.
- Vimshottari dasha significators (the intersection of dasha lords and house
  significators) and the horary ruling-planet workflow are SPEC-004 / SPEC-005.

## Dependencies

- **SPEC-002** — `NatalChart`, `Cusp`, `HousePlacement`, `AnglePoint`. Merged to `master`.
- **SPEC-001** — `KpLordage` / `LordChain`, `PositionProvider`, `HouseProvider`,
  `Graha`, `Sign`, `Accuracy`, `EngineVersion`.
- **New in `ephemeris`** — a `SunriseProvider` (`swe_rise_trans`) for the day lord.
- **Golden-chart reference** extended with significator tables + an RP example.
- SPEC-004 and SPEC-005 depend on this feature's significator and ruling-planet outputs.
