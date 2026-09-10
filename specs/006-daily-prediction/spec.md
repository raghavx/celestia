# Feature Specification: Daily Prediction Ruleset (SPEC-006)

**Feature Branch**: `006-daily-prediction`

**Created**: 2026-09-10

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-006 Daily prediction ruleset (define the
transit + dasha rules explicitly). Depends on: 003, 004." Engine tools
`getDailyPrediction(userId, date)` → "transit hits vs natal significators, dasha
context, activated house groups" and `explainHouseGrouping(matter)` → "KP house
set". Risk note: _"Daily-prediction ruleset under-specified in KP literature —
treat SPEC-006 as a design doc; ship documented v1, iterate."_

_A KP daily reading is deterministic bookkeeping, not divination: the running
Vimshottari periods say **which houses of the natal chart are active now**; the
transiting Moon (and Sun) say **which of those the sky is currently pointing at**;
a fixed table says **which houses each life matter needs**. This feature produces
the structured intersection — per matter, a verdict and the lords / houses behind
it. The LLM turns that structure into a sentence; it invents nothing._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - House-group taxonomy (Priority: P1)

Given a life **matter** (marriage, career, finance, education, travel, relocation,
health-timing, litigation, children, …), the engine returns the KP **house
group**: the set of houses that must be favourably signified for the matter to go
well, the set of houses that **obstruct** it, and the source for each.

**Why this priority**: `explainHouseGrouping` is a standalone tool. Every KP
judgement — Q&A, horary, and this feature's daily reading — starts from "which
houses does this matter live in?".

**Independent Test**: Each matter returns a non-empty favourable set and an
obstructive set; the sets are disjoint; each matches a published KP source.

**Acceptance Scenarios**:

1. **Given** the matter "marriage", **When** its house group is requested,
   **Then** the favourable set is {2, 7, 11} and the obstructive set includes {6}.
2. **Given** the matter "career/profession", **When** its house group is
   requested, **Then** the favourable set is {2, 6, 10, 11}.
3. **Given** an unknown matter string, **When** its house group is requested,
   **Then** the request is rejected (the taxonomy is a closed, versioned set).

---

### User Story 2 - Dasha significators for a date (Priority: P1)

Given a natal chart and a date, the engine returns the running Vimshottari period
lords (Mahadasha → Prana) for that date, and — for each — the houses it signifies
in the natal chart (SPEC-003), with the qualifying steps. It also returns the
**activated house set**: every house signified by any running lord, each tagged
with how many of the five levels signify it (its "activation strength").

**Why this priority**: The dasha is the primary KP timing filter. "House 7's sub
lord is a significator of 2, 7, 11 *and* the running Bhukti lord signifies 7" is
what makes an event *due* now.

**Independent Test**: For a golden chart and a fixed date, the running lords and
their per-house significations match the SPEC-003 / SPEC-004 outputs; the
activated house set is exactly the union.

**Acceptance Scenarios**:

1. **Given** a chart and a date, **When** the dasha significators are computed,
   **Then** each running lord's houses equal `grahaSignificators(lord)` from the
   natal `SignificatorTable`.
2. **Given** a house signified by three of the five running lords, **When** the
   activated set is read, **Then** that house has activation strength 3.
3. **Given** a house signified by no running lord, **When** the activated set is
   read, **Then** it is absent from the set.

---

### User Story 3 - Transit contribution for a date (Priority: P1)

Given a natal chart, a date and a place, the engine returns, for the reference
instant of that date, the KP lord chain of the **transiting Moon** and the
**transiting Sun**, and — applying the documented v1 transit rule — which natal
significator houses each transit **supports**: a transiting body supports house H
when its **sub lord** is a natal significator of H.

**Why this priority**: The transit is the trigger. The dasha says a house is ripe
for months; the Moon's sub lord says which of those days it fires.

**Independent Test**: For a golden chart + a fixed instant, the transiting Moon /
Sun lord chains match a SPEC-001 computation; the supported-house sets are exactly
the natal significations of each transit sub lord.

**Acceptance Scenarios**:

1. **Given** a date/place, **When** the transit contribution is computed, **Then**
   the transiting Moon's sub lord is taken from `KpLordage.chainFor` of the Moon's
   sidereal longitude at the reference instant.
2. **Given** the Moon's transit sub lord is a natal significator of houses {3, 9},
   **When** the transit contribution is read, **Then** the Moon supports {3, 9}.
3. **Given** the Moon crosses a sub-lord boundary during the calendar day,
   **When** the contribution is computed, **Then** it carries a flag that the
   Moon's transit sub lord changes that day (the reading is for the reference
   instant).

---

### User Story 4 - The daily reading (Priority: P1)

Given a natal chart, a date and a place, `getDailyPrediction` returns a
**structured** reading: for each matter in the taxonomy, a **verdict** —
`FAVOURABLE`, `MIXED`, `UNFAVOURABLE`, or `QUIET` — plus the houses and the
running lords / transit bodies that produced it, following one documented v1 rule.
No prose.

**Why this priority**: This is the tool the agent calls. Everything above feeds it.

**Independent Test**: For a golden chart + a fixed date/place, every matter's
verdict and its supporting houses / lords match the recorded reference, and each
verdict is reproducible from the stated rule.

**Acceptance Scenarios**:

1. **Given** a matter whose favourable houses are activated by the dasha **and**
   supported by a transit, with its obstructive houses not dominant, **When** the
   reading is computed, **Then** the verdict is `FAVOURABLE`.
2. **Given** a matter whose favourable houses are dasha-activated but no transit
   supports them today, **When** the reading is computed, **Then** the verdict is
   `MIXED` (ripe but not triggered).
3. **Given** a matter whose obstructive houses are more strongly activated than
   its favourable houses, **When** the reading is computed, **Then** the verdict
   is `UNFAVOURABLE`.
4. **Given** a matter with no dasha activation of either its favourable or its
   obstructive houses, **When** the reading is computed, **Then** the verdict is
   `QUIET`.
5. **Given** the reading, **When** any verdict is inspected, **Then** it carries
   the exact houses, running lords and transit bodies that determined it (the LLM
   needs the "because").

---

### User Story 5 - Reproducible correctness (Priority: P2)

The golden set is extended with one daily-prediction case: a chart + a fixed
date + place → the dasha significators, the transit contribution, and the full
per-matter reading. A determinism test and a "ruleset is fully documented" check
run in CI.

**Independent Test**: The daily-prediction golden case in CI; a determinism test;
a check that every verdict in the golden reading is derivable from the rules in
`core/REFERENCES.md`.

**Acceptance Scenarios**:

1. **Given** the golden daily-prediction case, **When** the suite runs, **Then**
   zero values differ from the recorded snapshot.
2. **Given** a change to the house-group taxonomy, the transit rule, or the
   verdict rule, **Then** the engine version changes and the suite flags every
   affected value.

---

### Edge Cases

- A date before the birth date → rejected (no natal chart applies).
- A date far beyond the first Vimshottari cycle → the running lords resolve in a
  later cycle (SPEC-004 already handles this); the reading is still produced.
- The reference instant lands exactly on a dasha or a Moon sub-lord boundary →
  half-open `[start, end)` as everywhere; the boundary flag is set.
- A matter whose favourable and obstructive houses are activated with equal
  strength → `MIXED`, with both sides listed.
- Birth instant or the date outside the supported ephemeris range → computed with
  `Accuracy.REDUCED`, consistent with SPEC-001; the reading is still produced.
- A natal chart with no significators for a matter's houses at all (rare) → those
  matters are always `QUIET`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST expose a closed, versioned **house-group taxonomy**:
  each matter maps to a **favourable** house set and an **obstructive** house set
  (disjoint, non-empty favourable), with a cited source per matter.
- **FR-002**: `explainHouseGrouping(matter)` MUST return that matter's favourable
  and obstructive house sets; an unknown matter MUST be rejected.
- **FR-003**: Given a `NatalChart` and a date, the engine MUST compute the
  **running Vimshottari lords** (Mahadasha → Prana) for the reference instant of
  that date (SPEC-004), and for each lord the houses it signifies in the natal
  chart with steps (SPEC-003 `grahaSignificators`).
- **FR-004**: The engine MUST compute the **activated house set** — the union of
  houses signified by any running lord — each tagged with its **activation
  strength** (the count of running levels, 1–5, that signify it).
- **FR-005**: Given a `NatalChart`, a date and a place, the engine MUST compute
  the KP lord chain of the **transiting Moon** and the **transiting Sun** at the
  reference instant (sidereal, KP-New).
- **FR-006**: The **v1 transit rule** MUST be: a transiting body **supports**
  house H iff its **sub lord** is a natal significator of H. The engine MUST
  return the supported-house set for the Moon and for the Sun. (This rule is
  labelled v1 and cited; refinements — star-lord agreement, retrograde, aspects —
  are out of scope.)
- **FR-007**: The **reference instant** of a date MUST be a documented, fixed
  choice (local noon of that date, i.e. `date` at `12:00` minus `longitude/15 h`);
  the engine MUST flag when the running dasha lord or the Moon's transit sub lord
  changes within the ±12 h day window.
- **FR-008**: `getDailyPrediction(chart, date, place)` MUST return a
  **structured** reading: for every matter in the taxonomy, a **verdict** in
  {`FAVOURABLE`, `MIXED`, `UNFAVOURABLE`, `QUIET`} plus the houses, running lords
  and transit bodies that produced it. No natural-language text.
- **FR-009**: The **v1 verdict rule** MUST be documented and deterministic. It
  MUST be a total function of: the matter's favourable / obstructive house sets,
  the activated house set (with strengths), and the Moon / Sun supported-house
  sets. The exact thresholds MUST be stated in `core/REFERENCES.md`.
- **FR-010**: The verdict rule's four outcomes MUST partition the input space:
  `QUIET` when neither side is dasha-activated; `UNFAVOURABLE` when the obstructive
  side outweighs the favourable; `FAVOURABLE` when the favourable side is
  dasha-activated and transit-supported and not outweighed; `MIXED` otherwise.
- **FR-011**: A date before the natal birth date MUST be rejected.
- **FR-012**: A birth instant or a date outside the supported ephemeris range
  MUST still yield a reading, with `Accuracy.REDUCED`, consistent with SPEC-001.
- **FR-013**: For identical inputs the engine MUST produce identical output across
  runs and platforms.
- **FR-014**: The engine version MUST be carried on the outputs and MUST change
  when the house-group taxonomy, the transit rule, the reference-instant rule, or
  the verdict rule changes.
- **FR-015**: The implementing code MUST stay in the pure modules (`core`, and
  `ephemeris` only for the transit positions); no wall-clock reads; the Swiss
  Ephemeris type MUST NOT appear in any public API.
- **FR-016**: Every KP rule added — the house groups, the transit rule, the
  reference-instant choice, and the verdict rule — MUST cite a source (or be
  explicitly labelled a documented v1 design choice) in `core/REFERENCES.md`.
- **FR-017**: The v1 ruleset MUST be **fully traceable**: every verdict in a
  reading MUST be reconstructable by hand from the stated rules and the
  intermediate values the reading carries.

### Key Entities *(include if data involved)*

- **Matter**: a life topic in the closed taxonomy (marriage, career, …).
- **House group**: for one matter — the favourable house set, the obstructive
  house set, and the source.
- **Dasha significators**: for one chart + date — the running lords and each
  lord's natal significations; the activated house set with strengths.
- **Transit contribution**: for one chart + date + place — the transiting Moon /
  Sun lord chains and the houses each supports; the within-day change flags.
- **Matter verdict**: for one matter — the verdict, the houses / lords / transit
  bodies behind it.
- **Daily prediction**: the reference instant, the dasha significators, the
  transit contribution, and the per-matter verdicts; carries the accuracy flag
  and the engine version.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every matter in the taxonomy has a non-empty favourable set, a
  disjoint obstructive set, and a citation; a sample matches a published KP source.
- **SC-002**: For a golden chart + a fixed date, the running lords and their
  per-house significations equal the SPEC-003 / SPEC-004 outputs exactly, and the
  activated house set is exactly their union with correct strengths.
- **SC-003**: For a golden chart + a fixed date/place, the transiting Moon / Sun
  lord chains match a direct SPEC-001 computation, and each supported-house set is
  exactly the natal significations of that transit sub lord.
- **SC-004**: For a golden chart + a fixed date/place, every matter's verdict and
  its supporting houses / lords match the recorded reference with zero
  discrepancies, and each verdict is reproducible by hand from
  `core/REFERENCES.md`.
- **SC-005**: A full daily reading is deterministic and completes in under
  **50 ms** on a developer machine (given a cast `NatalChart`); the
  daily-prediction golden snapshot passes unchanged in CI on every platform.
- **SC-006**: For one golden chart + one date, the structured reading is
  additionally checked by a human against a manual KP walk-through (running lords
  → significations → transit → verdict per matter) and recorded as reviewed; the
  house-group taxonomy is checked against K. S. Krishnamurti's *KP Readers* house
  significations.

## Assumptions

- **The daily-prediction ruleset is a documented v1**, not a claim to be "the" KP
  method (which is under-specified in the literature). Every rule is either cited
  or explicitly marked a v1 design choice; the whole thing is versioned so a v2
  is a clean change.
- **The transit rule is the sub-lord rule only** (a transiting body supports a
  house iff its sub lord signifies that house). Star-lord agreement, the
  "transit through the star of a significator" refinement, retrogression, aspects,
  and the transiting Jupiter/Saturn slow cycle are **v2**.
- **Only the Moon and the Sun transits** are used in v1 — the Moon for the day,
  the Sun for the fortnight. Fast planets (Mercury, Venus, Mars) and the slow
  planets are v2.
- **The reference instant is local noon of the date** — a fixed, documented
  choice. The true local civil time is SPEC-007; v1 uses `longitude/15 h` as the
  LMT offset (as SPEC-003's `KpWeekday`). The reading flags any within-day change
  of a running lord or the Moon's sub lord.
- **The natal chart is supplied by the caller** (SPEC-002 `NatalChart`), already
  cast for the person. This feature does not cast it.
- **The house-group taxonomy is checked-in reference data with citations**, not
  runtime config in v1 (a configurable taxonomy is a later concern; the guardrail
  keeps its own in-scope category list per D23).
- **Verdicts are structured only.** Turning `{marriage: FAVOURABLE, houses [2,7,11],
  lords [Bhukti Venus, Antara Jupiter], transit Moon}` into "a good day to move
  things forward on the relationship" is the LLM's job (Constitution IV).
- **Remedies / gemstones / muhurta selection** are out of scope.

## Dependencies

- **SPEC-003** — `SignificatorTable` / `GrahaSignificators` (natal significations
  per graha), `KpLordage` / `LordChain`.
- **SPEC-004** — `DashaTimeline` / `RunningDasha` (the running lord stack for a
  date).
- **SPEC-002** — `NatalChart` (the natal input).
- **SPEC-001** — `PositionProvider` (the transiting Moon / Sun), `Graha`, `Sign`,
  `BirthData`, `EngineVersion`, `Accuracy`, `TimeScales`.
- **Consumed by** the LLM agent (`getDailyPrediction`, `explainHouseGrouping`
  tools) and SPEC-017 (the optional daily-prediction push).
