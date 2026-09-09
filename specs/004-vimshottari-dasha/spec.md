# Feature Specification: Vimshottari Dasha (SPEC-004)

**Feature Branch**: `004-vimshottari-dasha`

**Created**: 2026-09-09

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-004 Vimshottari dasha tree (5 levels) +
balance-of-dasha at birth. Depends on: 001." Engine tool `getDashaPeriods(userId,
atDate, depth)` → "running Maha/Bhukti/Antara/… with start–end dates"; persistence
`dasha_period(id, chart_id, level, lord, start_utc, end_utc, parent_id)` (SPEC-008).

_The KP timing engine. Vimshottari is the 120-year planetary cycle that says
**when** a promised event fructifies. Given a birth moment, the engine produces
the nested period tree (Mahadasha → Antardasha → Pratyantardasha → Sookshma →
Prana) and, for any date, the stack of ruling period lords. SPEC-006 (daily
predictions) intersects these lords with the SPEC-003 significators; the LLM agent
reads the running stack to answer "what period am I in / when does X change"._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Balance of dasha at birth (Priority: P1)

Given a birth instant (UTC) and the sidereal longitude of the Moon, the engine
returns the **birth Mahadasha**: which graha's Mahadasha is running at birth (the
lord of the Moon's nakshatra), how much of it has already elapsed, how much
remains (the "balance"), and the instants at which that Mahadasha began (before
birth) and ends.

**Why this priority**: Every dasha calculation starts here. The first Mahadasha of
a life is a partial one — its balance is the anchor from which the entire forward
timeline is built. A wrong balance shifts every subsequent period.

**Independent Test**: For each golden chart, the birth Mahadasha lord, the elapsed
fraction, and the balance (in days) match a reference table.

**Acceptance Scenarios**:

1. **Given** a birth Moon at 10°00′ of a nakshatra 13°20′ wide whose lord is Venus
   (20-year Mahadasha), **When** the balance is computed, **Then** the elapsed
   fraction is 0.75, the elapsed period is 15 years, and the balance is 5 years.
2. **Given** the Moon exactly at the start of its nakshatra, **When** the balance
   is computed, **Then** the elapsed fraction is 0 and the balance is the full
   Mahadasha length of that nakshatra's lord.
3. **Given** the birth balance, **When** the Mahadasha's start and end instants
   are read, **Then** `end − start` equals the lord's full Mahadasha length and
   `start` is `birthInstant − elapsedPeriod`.

---

### User Story 2 - Running period stack for a date (Priority: P1)

Given the birth data and a query instant, the engine returns the **stack** of
period lords active at that instant — Mahadasha, Antardasha (Bhukti),
Pratyantardasha (Antara), Sookshma, Prana — to a requested depth (1–5), each level
naming its lord and its start and end instants.

**Why this priority**: This is the `getDashaPeriods` tool. It is what the agent
and SPEC-006 read for "which period am I in", "when does the next Bhukti start",
and to combine dasha lords with house significators.

**Independent Test**: For a golden chart plus a fixed query date, the five period
lords and their boundaries match a reference (KP software / textbook).

**Acceptance Scenarios**:

1. **Given** a birth and a query instant one day after birth, **When** the stack
   is requested to depth 5, **Then** the Mahadasha lord is the birth-balance lord
   and every deeper level is populated with a lord and a start/end that contains
   the query instant.
2. **Given** a query instant exactly at a period's start, **When** the stack is
   requested, **Then** that period is returned (not the one that just ended) at
   every level — half-open `[start, end)`.
3. **Given** a query depth of 3, **When** the stack is requested, **Then** exactly
   Mahadasha, Antardasha and Pratyantardasha are returned.
4. **Given** a query instant 130 years after birth, **When** the stack is
   requested, **Then** it is resolved in the second Vimshottari cycle (the cycle
   repeats from the same lord) without error.

---

### User Story 3 - Enumerate periods over a window (Priority: P2)

Given a birth, a level, and a half-open time window `[from, to)`, the engine
returns every period at that level that overlaps the window, in chronological
order, each carrying its lord, its start/end instants, and the chain of parent
lords it sits under.

**Why this priority**: Persistence (SPEC-008 `dasha_period` rows), "list my
upcoming Bhuktis", and any UI/agent view of a span of time need the list form, not
just the point lookup of User Story 2.

**Independent Test**: For a golden chart, the Antardashas overlapping a fixed
one-year window match a reference list (lords + boundaries).

**Acceptance Scenarios**:

1. **Given** a window that starts mid-period, **When** Antardashas overlapping it
   are listed, **Then** the first entry is the Antardasha in progress at `from`
   (its start is before `from`).
2. **Given** consecutive periods at one level, **When** they are listed, **Then**
   each period's `end` equals the next period's `start` (no gap, no overlap).
3. **Given** any listed sub-period, **When** its parent chain is read, **Then**
   the sub-period's `[start, end)` lies within its immediate parent's `[start,
   end)`.

---

### User Story 4 - Reproducible correctness (Priority: P2)

The golden charts are extended with the birth balance (lord + elapsed + balance)
and the running five-lord stack at a fixed query date. A property check confirms
that at every level the nine children exactly partition their parent. The snapshot
suite reproduces every value on every run and platform.

**Independent Test**: The extended golden suite in CI; a determinism test; the
exact-partition property test.

**Acceptance Scenarios**:

1. **Given** the extended golden set, **When** the suite runs, **Then** zero
   values differ from the recorded snapshots.
2. **Given** any period at any level, **When** its nine children are summed,
   **Then** the total duration equals the parent's duration exactly and the
   children's boundaries have no gap or overlap.
3. **Given** a change to a dasha rule (year length, level count, ordering),
   **Then** the engine version changes and the suite flags every affected value.

---

### Edge Cases

- Moon exactly at a nakshatra boundary → belongs to the **next** nakshatra
  (half-open, consistent with SPEC-001/002/003); the birth Mahadasha lord is the
  next lord with elapsed fraction 0.
- Query instant exactly at the birth instant → returns the birth-balance
  Mahadasha and the Antardasha/… running at the elapsed offset into it.
- Query instant before the birth instant → rejected (the timeline is defined from
  birth forward; periods before birth exist only as the birth Mahadasha's start).
- Query instant many cycles ahead (e.g. 250 years) → resolved by treating the
  Vimshottari sequence as periodic with a 120-year period.
- Very short deep periods → a Prana can be a few minutes; boundaries are computed
  from exact rational period lengths and represented with sub-second precision.
- Requested depth outside 1–5 → rejected.
- Birth instant outside the supported ephemeris range → the Moon is computed with
  `Accuracy.REDUCED`; the timeline is still produced and carries the flag
  (consistent with SPEC-001).
- Leap seconds → period arithmetic uses SI-second durations (as SPEC-001
  `TimeScales`), so a boundary is an exact offset from the birth instant on the
  UTC timeline.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Given a birth instant and the Moon's sidereal longitude, the engine
  MUST determine the **birth Mahadasha lord** as the lord of the Moon's nakshatra
  (SPEC-001 `KpLordage` / `Nakshatra`).
- **FR-002**: The **elapsed fraction** of the birth Mahadasha MUST be the fraction
  of the nakshatra the Moon has traversed — `(moonLongitude − nakshatraStart) /
  nakshatraWidth` — computed exactly; the **elapsed period** is that fraction ×
  the lord's Mahadasha length, and the **balance** is the remainder.
- **FR-003**: Mahadasha lengths MUST be the Vimshottari year weights (Ketu 7,
  Venus 20, Sun 6, Moon 10, Mars 7, Rahu 18, Jupiter 16, Saturn 19, Mercury 17;
  total 120), and the Mahadasha sequence MUST follow the fixed Vimshottari order
  (Ketu → Venus → Sun → Moon → Mars → Rahu → Jupiter → Saturn → Mercury → Ketu …).
- **FR-004**: Each period MUST subdivide into **nine** child periods in
  Vimshottari order **starting from that period's own lord**; a child's duration
  MUST be `parentDuration × childWeight / 120`.
- **FR-005**: The engine MUST support exactly five levels: **Mahadasha**,
  **Antardasha** (Bhukti), **Pratyantardasha** (Antara), **Sookshma**, **Prana**,
  ranked 1–5.
- **FR-006**: Period boundaries MUST be anchored to the **birth instant** on the
  UTC timeline: the birth Mahadasha starts at `birthInstant − elapsedPeriod`, and
  every subsequent boundary is the birth instant plus a cumulative offset.
- **FR-007**: A period length expressed in years MUST be converted to a duration
  using **365.25 days per year** (see Assumptions); conversions MUST preserve
  sub-second precision.
- **FR-008**: Given a birth and a query instant at or after the birth instant, the
  engine MUST return the active period at each level from Mahadasha down to a
  caller-specified depth `d` (1 ≤ d ≤ 5), each with its lord and its start/end
  instants.
- **FR-009**: Period containment MUST be half-open `[start, end)` — a query
  instant exactly on a boundary belongs to the later period, at every level.
- **FR-010**: A query instant before the birth instant MUST be rejected; a
  requested depth outside 1–5 MUST be rejected.
- **FR-011**: The Vimshottari sequence MUST be treated as **periodic** with a
  120-year period, so queries and enumerations arbitrarily far after birth resolve
  into later cycles without error.
- **FR-012**: Given a birth, a level, and a half-open window `[from, to)`, the
  engine MUST return every period at that level overlapping the window, in
  chronological order, each carrying its lord, its `[start, end)`, and its chain
  of parent lords. A period starting exactly at `to` is not included.
- **FR-013**: At every level the nine child periods MUST **exactly partition** the
  parent — contiguous, no gap, no overlap, summing to the parent's exact duration.
- **FR-014**: For identical inputs the engine MUST produce identical output across
  runs and platforms.
- **FR-015**: The engine version MUST be carried on the outputs and MUST change
  when any dasha rule changes (the year-length convention, the level count, the
  ordering, or an upstream SPEC-001 rule).
- **FR-016**: A birth instant outside the supported ephemeris range MUST still
  yield a timeline, with `Accuracy.REDUCED`, consistent with SPEC-001.
- **FR-017**: The implementing code MUST stay in the pure modules (`core`, and
  `ephemeris` only for the Moon position); no wall-clock reads; the Swiss
  Ephemeris type MUST NOT appear in any public API.
- **FR-018**: Every KP rule added MUST cite a source in `core/REFERENCES.md`.

### Key Entities *(include if feature involves data)*

- **Dasha level**: one of Mahadasha, Antardasha, Pratyantardasha, Sookshma, Prana,
  with a rank 1–5.
- **Dasha period**: a level, a lord (graha), a start instant, an end instant, and
  the chain of parent lords above it.
- **Dasha balance**: for one birth — the birth Mahadasha lord, the elapsed
  fraction, the elapsed duration, the balance duration, and the Mahadasha's start
  (before birth) and end instants.
- **Running dasha**: for one query instant — the ordered stack of active periods
  from Mahadasha down to the requested depth.
- **Dasha timeline**: the computed structure for one birth — the source of the
  balance, the running stack for any instant, and the period list for any level
  and window; carries the accuracy flag and the engine version.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every golden chart, the birth Mahadasha lord matches the
  reference exactly and the balance matches within **1 day**.
- **SC-002**: For every golden chart plus a fixed query date, the five running
  period lords (Mahadasha → Prana) match the reference **exactly**, and the
  Mahadasha and Antardasha boundaries match within **1 day**.
- **SC-003**: At every level, the nine child periods exactly partition the parent
  — the sum of child durations equals the parent duration as an exact rational,
  with contiguous boundaries (property check).
- **SC-004**: Half-open boundary — a query at a period's exact start instant
  returns that period, not its predecessor, at every level.
- **SC-005**: Computing the depth-5 running stack for a date is deterministic and
  completes in under **5 ms** on a developer machine; the extended golden snapshot
  suite passes unchanged in CI on every supported platform.
- **SC-006**: For at least one golden chart, the birth Mahadasha lord and balance
  are additionally checked against a published KP source (textbook worked example,
  or two agreeing mainstream KP tools on KP-New ayanamsa) and recorded as
  human-verified.

## Assumptions

- **Year length = 365.25 days** (the Julian year), per K. S. Krishnamurti's *KP
  Readers* dasha tables. This is the single biggest source of disagreement between
  KP tools (others use a 360-day savana year or the sidereal solar year); it is
  pinned here and would change only by an explicit later decision, with an engine
  version bump.
- The dasha is anchored to the birth **instant in UTC**. SPEC-007 will derive that
  UTC instant from the local birth time and place; until then the caller supplies
  UTC (as in SPEC-001/002/003).
- The Moon is the **same** sidereal Moon (KP-New ayanamsa, mean node frame) used
  everywhere else in the engine; its nakshatra and the traversed fraction come
  from SPEC-001 `Nakshatra` / `KpLordage`.
- Only the **Moon longitude and the birth instant** are needed — no full
  `NatalChart` dependency (PROJECT_PLAN: SPEC-004 depends on 001 only). A
  convenience path may accept a `NatalChart` later.
- The exact-rational partition machinery from SPEC-001 (`Span`,
  `VimshottariPartition`) is reused for the **time** partition (the same
  weight-proportional nine-way split), applied to durations instead of arc.
- **Dasha significators** — the intersection of the running dasha lords with the
  SPEC-003 house significators — are **SPEC-006**, not this feature. This feature
  produces only the period tree and the running lords.
- Other dasha systems (Char, Yogini, Ashtottari, Kalachakra) are **out of scope** —
  Vimshottari only.
- Terminology: Antardasha = Bhukti; Pratyantardasha = Antara (per PROJECT_PLAN).

## Dependencies

- **SPEC-001** — `Graha` (Vimshottari year weights and order), `Nakshatra` /
  `KpLordage` (the Moon's nakshatra lord and traversed fraction), `Span` /
  `VimshottariPartition` (exact `BigFraction` weight-proportional partition),
  `PositionProvider` (the Moon at the birth instant), `BirthData`,
  `EngineVersion`, `Accuracy`, `TimeScales`.
- **Golden-chart reference** extended with the birth balance and a running-stack
  example.
- **Consumed by** SPEC-006 (daily prediction ruleset) and SPEC-008 (persistence:
  the `dasha_period` table and engine versioning).
