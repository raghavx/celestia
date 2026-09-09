---
description: "Task list for SPEC-004 — Vimshottari Dasha"
---

# Tasks: Vimshottari Dasha (SPEC-004)

**Input**: Design documents in `specs/004-vimshottari-dasha/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 4 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 (the birth
balance + the running lord stack — everything SPEC-006 needs).

Builds on SPEC-001 (merged to `master`): `Graha` (Vimshottari weights + order +
`next()`), `Nakshatra` / `KpLordage`, `Span` / `VimshottariPartition`,
`PositionProvider`, `BirthData`, `EngineVersion`, `Accuracy`, `TimeScales`, the
golden-chart harness. No new module, no new dependency, no new ADR.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [x] T001 Extend `tools/ephe-crosscheck/compute_golden.py` with the balance +
  running-stack rules (research.md §1, §4): emit `expected.dasha.balance`
  (`maha_lord`, `elapsed_fraction`, `elapsed_days`, `balance_days`) and
  `expected.dasha.running` (query instant = `birthInstant + 40 Julian years`,
  research.md §4 → the five period lords with start/end). Reuse the existing Moon
  computation and the `lord_chain` / nakshatra helpers; 1 year = 365.25 days.
- [x] T002 Regenerate the golden files (`--write`); verify determinism (re-run,
  diff `expected`). Update `core/src/test/resources/golden/README.md` with the new
  fields, the `birth + 40 years` query convention, and the year-length note.
- [ ] T003 [P] SC-006: transcribe **one** golden chart's birth Mahadasha lord and
  balance from a KP textbook worked example (or two agreeing mainstream KP tools
  on KP-New ayanamsa); record it in `verification.dasha_human_check` and note any
  single-source value.
- [x] T004 [P] Extend `core/src/test/java/com/celestia/core/golden/GoldenChart.java`
  to parse `expected.dasha.balance` and `expected.dasha.running`.

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [x] T005 [P] `DashaLevel` enum (`MAHADASHA` 1 … `PRANA` 5; `int rank()`;
  `static ofRank(int)`; `Optional<DashaLevel> child()` / `parent()`; Bhukti /
  Antara synonyms in javadoc) in `core/src/main/java/com/celestia/core/dasha/DashaLevel.java`
- [x] T006 [P] `Portion` record (`Graha lord, BigFraction span`) and
  `VimshottariSplit.of(BigFraction total, Graha fromLord)` in
  `core/src/main/java/com/celestia/core/dasha/VimshottariSplit.java` — nine
  portions in Vimshottari order from `fromLord`, span `i` = `total × grahaᵢ.years()
  / 120`, spans summing to `total` exactly; `total > 0` else `IllegalArgumentException`
- [x] T007 [P] `DashaPeriod` record (`DashaLevel level, Graha lord, Instant start,
  Instant end, List<Graha> parentLords`; `contains(Instant)` half-open;
  `Duration duration()`; compact ctor validates `start` before `end`,
  `parentLords` size == `level.rank() − 1`, `List.copyOf`) in
  `core/src/main/java/com/celestia/core/dasha/DashaPeriod.java`
- [x] T008 [P] `DashaBalance` record (`Graha mahaLord, double elapsedFraction,
  Duration elapsed, Duration balance, Instant mahaStart, Instant mahaEnd`; compact
  ctor validates `0.0 ≤ elapsedFraction < 1.0`, `mahaStart` before `mahaEnd`) in
  `core/src/main/java/com/celestia/core/dasha/DashaBalance.java`
- [x] T009 [P] `RunningDasha` record (`Instant instant, List<DashaPeriod> stack`;
  `period(DashaLevel)`, `lord(DashaLevel)`, `int depth()`; compact ctor validates
  non-empty stack ≤ 5, `stack.get(i).level().rank() == i + 1`, every period
  `contains(instant)`, each nested in the previous, `List.copyOf`) in
  `core/src/main/java/com/celestia/core/dasha/RunningDasha.java`
- [x] T010 [P] `FoundationalTypesTest` in
  `core/src/test/java/com/celestia/core/dasha/FoundationalTypesTest.java` —
  `DashaLevel.rank()` 1..5 and `ofRank` round-trip / rejects out of range;
  `DashaPeriod` rejects wrong `parentLords` size and `start ≥ end`;
  `RunningDasha` rejects a non-nested stack; `VimshottariSplit.of` returns nine
  portions in Vimshottari order from the lord

**Checkpoint**: `./mvnw -pl core -am test` green.

---

## Phase 3: User Story 1 — Balance of dasha at birth (P1) 🎯 MVP

**Goal**: birth instant + Moon longitude → the birth Mahadasha (lord, elapsed,
balance, start before birth, end).

**Independent Test**: golden charts — the birth Maha lord (exact) and balance
(within 1 day).

### Tests (write first)

- [ ] T011 [P] [US1] `DashaBalanceTest` in
  `core/src/test/java/com/celestia/core/dasha/DashaBalanceTest.java` — hand-set
  Moon longitudes: a Moon 3/4 through a Venus nakshatra → `elapsedFraction` 0.75,
  `elapsed` 15 years, `balance` 5 years; a Moon exactly at a nakshatra start →
  fraction 0 and `balance` == the lord's full Mahadasha; `mahaEnd − mahaStart` ==
  `mahaLord.years()` (365.25-day years, ± the ns rounding); `mahaStart` ==
  `birthInstant − elapsed`
- [ ] T012 [P] [US1] `DashaGoldenTest` — for each golden chart,
  `balanceAtBirth().mahaLord()` == `expected.dasha.balance.maha_lord` and the
  balance matches within **1 day**; plus a year-1600 birth via
  `DashaTimelineFactory.at(...)` → `accuracy() == Accuracy.REDUCED`,
  `balanceAtBirth()` non-null, no exception (FR-016); `@EnabledIf` ephemeris data

### Implementation

- [ ] T013 [US1] Years → `Duration` conversion in
  `core/src/main/java/com/celestia/core/dasha/DashaTimeline.java` — 1 year =
  365.25 d = 31 557 600 s exact; `BigFraction` seconds → `Duration`
  (`floor` seconds + rounded nanos); helper stays private
- [ ] T014 [US1] `DashaBalance` computation in
  `DashaTimeline.from(Instant birthInstant, double moonLongitude, Accuracy
  accuracy, EngineVersion engineVersion)` — `Nakshatra.at(moonLongitude)` → maha
  lord; exact traversed fraction `(λ − nakStart) / (40/3)`; `elapsed` / `balance`
  = fraction / (1 − fraction) × `mahaLord.years()`; `mahaStart` =
  `birthInstant − elapsed`, `mahaEnd` = `birthInstant + balance`
- [ ] T015 [US1] `DashaTimeline.balanceAtBirth()`, `birthInstant()`, `accuracy()`,
  `engineVersion()` accessors; store the exact birth-Maha start (rational seconds)
  for the running query (Phase 4)
- [ ] T016 [US1] `DashaTimelineFactory` in
  `core/src/main/java/com/celestia/core/dasha/DashaTimelineFactory.java` —
  `DashaTimelineFactory(PositionProvider positions)`; `at(BirthData birth)` reads
  the Moon from `positions.positions(birth.instant())`, takes its longitude and
  `Accuracy`, and delegates to `DashaTimeline.from(...)` with the position
  result's `EngineVersion`

**Checkpoint**: the birth balance is computable and golden-verified.

---

## Phase 4: User Story 2 — Running period stack for a date (P1)

**Goal**: birth + query instant → the Maha/Antar/Pratyantar/Sookshma/Prana lords
active at that instant, to a requested depth, with boundaries.

**Independent Test**: golden charts + the `birth + 40 years` query instant — the
five lords (exact) and the Maha/Antar boundaries (within 1 day).

### Tests (write first)

- [ ] T017 [P] [US2] `RunningDashaTest` in
  `core/src/test/java/com/celestia/core/dasha/RunningDashaTest.java` — depth 5:
  every level populated, each period `contains` the query, each nested in the
  previous; depth 3 returns exactly Maha / Antar / Pratyantar; a query 130 years
  after birth resolves in the second Vimshottari cycle; query `< birthInstant` →
  `IllegalArgumentException`; depth 0 or 6 → `IllegalArgumentException`
- [ ] T018 [P] [US2] `DashaBoundaryTest` (SC-004) — a query exactly at a period's
  `start` returns that period (not its predecessor) at **every** level; a query
  exactly at `mahaEnd` returns the next Mahadasha and its first child at each
  deeper level
- [ ] T019 [P] [US2] `RunningDashaGoldenTest` — for each golden chart plus the
  `birthInstant + 40 Julian years` query instant, the five running lords ==
  `expected.dasha.running.lords` **exactly**, and the Mahadasha and Antardasha
  boundaries match within **1 day**; `@EnabledIf` ephemeris data
- [ ] T020 [US2] Mahadasha resolution in `DashaTimeline` — the Vimshottari
  sequence from the birth-Maha start is periodic with a 120-year period; find the
  Maha containing the query by fast-forwarding whole cycles then a ≤ 9-step walk;
  absolute `start` / `end` as exact cumulative seconds from the birth instant
- [ ] T021 [US2] Recursive level split in `DashaTimeline` — `VimshottariSplit` the
  parent `[start, end)` (exact seconds) by its lord, find the child containing the
  query, recurse to depth; build each `DashaPeriod` with its `parentLords` chain
- [ ] T022 [US2] `DashaTimeline.running(Instant query, int depth)` → `RunningDasha`
  — validates `query ≥ birthInstant` and `1 ≤ depth ≤ 5`; half-open `[start, end)`
  at every level

**Checkpoint**: the running stack is computable and golden-verified.

---

## Phase 5: User Story 3 — Enumerate periods over a window (P2)

**Goal**: birth + level + `[from, to]` → every period at that level overlapping
the window, contiguous, with parent-lord chains.

### Tests (write first)

- [ ] T023 [P] [US3] `DashaWindowTest` in
  `core/src/test/java/com/celestia/core/dasha/DashaWindowTest.java` — Antardashas
  over a one-year window: the first entry is the one in progress at `from` (its
  `start` is before `from`); `result[i].end == result[i+1].start`; each entry's
  `[start, end)` lies within its `parentLords` Mahadasha; `from` after `to` or
  `from < birthInstant` → `IllegalArgumentException`; a Pranas-over-decades
  request → `IllegalArgumentException` (the 10 000-period safety cap)
- [ ] T024 [P] [US3] `DashaWindowGoldenTest` — the Antardashas overlapping a fixed
  window for one golden chart == a reference list (lords + boundaries);
  `@EnabledIf` ephemeris data
- [ ] T025 [US3] "Next period at level L" walk in `DashaTimeline` — the next child
  of the current parent; ascend to the parent's next sibling and descend to its
  first level-L child at a boundary; all exact-rational arithmetic (no
  re-resolution from the birth-Maha start)
- [ ] T026 [US3] `DashaTimeline.periods(DashaLevel level, Instant from, Instant
  to)` → `List<DashaPeriod>` — resolve the level-L period at `from`, walk while
  `start < to`, attach `parentLords`; validates the window; throws if more than
  10 000 periods would be returned (naming the level and window)

**Checkpoint**: windowed enumeration is computable and golden-verified.

---

## Phase 6: User Story 4 — Reproducible correctness (P2)

- [ ] T027 [P] [US4] `VimshottariSplitTest` in
  `core/src/test/java/com/celestia/core/dasha/VimshottariSplitTest.java` —
  `of(total, lord)` for several totals and lords: nine portions, Vimshottari order
  from `lord`, `Σ span == total` as an **exact** `BigFraction` equality; portion
  `i` span == `total × grahaᵢ.years() / 120`
- [ ] T028 [P] [US4] `DashaPartitionPropertyTest` (jqwik) in
  `core/src/test/java/com/celestia/core/dasha/DashaPartitionPropertyTest.java` —
  for a generated birth instant + Moon longitude + level + depth: the nine
  children of any resolved period are contiguous (`child[i].end == child[i+1]
  .start`), `child[0].start == parent.start`, `child[8].end == parent.end`
- [ ] T029 [P] [US4] `DashaDeterminismTest` — `DashaTimeline.from` twice on a
  golden chart; compare the **accessor outputs** (`balanceAtBirth()`,
  `running(q, 5)`, `periods(ANTARDASHA, from, to)` — records with value equality),
  since `DashaTimeline` is a class without `equals`
- [ ] T030 [US4] `.github/workflows/ci.yml` — add `DashaGoldenTest`,
  `RunningDashaGoldenTest`, `DashaWindowGoldenTest`, `DashaDeterminismTest` to the
  OS-matrix determinism step

**Checkpoint**: dasha correctness is a CI gate.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T031 [P] `core/src/main/java/com/celestia/core/REFERENCES.md` — add the
  Vimshottari Mahadasha lengths and order, the nested nine-way split (in
  Vimshottari order **from the parent's lord**, proportional to the weights), the
  **365.25-day year** (cite K. S. Krishnamurti, *KP Readers*), and the
  balance-of-dasha rule; note that a `core.dasha` rule change also bumps
  `EngineVersion.rules`
- [ ] T032 [P] (optional) Refactor `core/src/main/java/com/celestia/core/dasha/VimshottariPartition.java`
  to consume `VimshottariSplit` — pure, no behaviour change (SPEC-001/002 golden
  suites guard it, no version bump); skip if it looks risky during implementation
- [ ] T033 [P] `DashaPerformanceTest` `@Tag("perf")` in
  `core/src/test/java/com/celestia/core/dasha/DashaPerformanceTest.java` — the
  depth-5 `running(...)` for a date under a 50 ms warm guard (SC-005 target 5 ms);
  excluded from the default run
- [ ] T034 [P] Update `core/README.md` (+ `DashaTimeline`, `DashaTimelineFactory`,
  `VimshottariSplit`, `DashaLevel`)
- [ ] T035 Run `specs/004-vimshottari-dasha/quickstart.md` end to end; fix drift
- [ ] T036 `./mvnw -q verify` — full reactor green incl.
  `DeterminismArchitectureTest` / `LayeringArchitectureTest`

---

## Dependencies & Execution Order

1. **Setup (P1)** — T001–T002 (golden data) block the golden tests; T004 blocks
   the loader-based tests; T003 is SC-006 (parallel, human).
2. **Foundational (P2)** — value types + `VimshottariSplit`; blocks every user
   story.
3. **US1 (P3)** — the balance + `DashaTimeline.from` scaffolding + the factory;
   depends only on Foundational.
4. **US2 (P4)** — the running stack; depends on US1 (`from`, the stored birth-Maha
   start, the years→Duration helper).
5. **US3 (P5)** — windowed enumeration; depends on US2 (the level split + sibling
   arithmetic).
6. **US4 (P6)** — depends on US1 + US2 + US3 (it snapshots and property-checks all
   three).
7. **Polish (P7)** — after the stories you intend to ship.

### Parallel opportunities

- Setup: T003 (human) and T004 in parallel; T001 → T002 serial.
- Foundational: T005–T009 in parallel; T010 after.
- US1: T011/T012 (tests) in parallel; T013 → T014 → T015 serial; T016 after T015.
- US2: T017/T018/T019 (tests) in parallel; T020 → T021 → T022 serial.
- US3: T023/T024 (tests) in parallel; T025 → T026 serial.
- US4: T027/T028/T029 in parallel; T030 after.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2. Delivers the birth balance and the
running lord stack — everything SPEC-006 (daily predictions) and the
`getDashaPeriods` agent tool consume. Stop, run `quickstart.md`, open the PR.

**Blocked-work note**: the golden tests (T012, T019, T024) need the extended
golden data (T002). Everything else is unblocked — SPEC-001 is on `master`, no new
dependency.

---

## Notes

- No new module, no new dependency, no new ADR — additive to `core`.
- `VimshottariSplit` is the shared exact weight split; `VimshottariPartition`
  (SPEC-001, arc) may be refactored onto it (T032) but need not be.
- Time is exact `BigFraction` seconds internally; `Instant`s are produced by
  rounding a cumulative offset **once** per boundary (never by summing `Duration`s).
- Every rule added gets a citation in `core/REFERENCES.md` (Constitution III).
- Commit per task or tight group; keep `master` releasable (work on `004-...`).
