---
description: "Task list for SPEC-006 — Daily Prediction Ruleset"
---

# Tasks: Daily Prediction Ruleset (SPEC-006)

**Input**: Design documents in `specs/006-daily-prediction/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 5 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US3 + US4
(the whole reading — US4 composes US1–US3).

Builds on SPEC-002/003/004 (merged to `master`): `NatalChart`, `SignificatorTable`
/ `GrahaSignificators`, `DashaTimeline` / `RunningDasha` / `DashaLevel`,
`KpLordage` / `LordChain`, `Graha`, `PositionProvider`, `EphemerisResult`,
`EngineVersion`, `Accuracy`, `TimeScales`, the golden-chart harness. No new
module, no new dependency, no new ADR.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [x] T001 Extend `tools/ephe-crosscheck/compute_golden.py` with the
  daily-prediction case (research.md §1–§5): `date = birthDate + 40 Julian years`
  at `longitude = the chart's birth longitude` (research.md §4) →
  `expected.daily` — the reference instant, `dasha` (per-lord significations, the
  activated house set with strengths + lords, `lord_changes_within_day`),
  `transit` (`moon_sub_lord`, `sun_sub_lord`, `moon_supports`, `sun_supports`,
  `moon_sub_lord_changes_within_day`), and the per-matter verdicts with
  `favourable_hit` / `obstructive_hit` / `lords` / `transits`. Replicate the
  house-group table, the running-lord ∩ significators step, the sub-lord transit
  rule, and the 4-row verdict function. Emit into one golden file (`obama-1961`).
- [x] T002 Regenerate the golden files (`--write`); verify determinism (re-run,
  diff `expected`). Update `core/src/test/resources/golden/README.md` with the
  `expected.daily` shape, the `birth + 40 years` date + birth-longitude
  convention, and the `daily_human_check` protocol.
- [ ] T003 [P] SC-006: walk **one** reading by hand (running lords → natal
  significations → transit sub lord → verdict per matter) and check the §1
  house-group table against K. S. Krishnamurti's *KP Readers* house-signification
  tables; record in `verification.daily_human_check` and note any single-source
  house set.
- [x] T004 [P] Extend `core/src/test/java/com/celestia/core/golden/GoldenChart.java`
  to parse `expected.daily` (reference instant, activated houses, transit
  supports, per-matter verdicts).

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [x] T005 [P] `Matter` enum (research.md §1: `MARRIAGE`, `CAREER`, `WEALTH`,
  `EDUCATION`, `CHILDREN`, `PROPERTY`, `TRAVEL`, `LITIGATION`, `HEALTH_RECOVERY`;
  each `Set<Integer> favourable()`, `Set<Integer> obstructive()`, `String
  source()`, `String key()`; static init validates favourable non-empty,
  favourable ∩ obstructive = ∅, houses 1..12) in
  `core/src/main/java/com/celestia/core/prediction/Matter.java`
- [x] T006 [P] `Verdict` enum (`FAVOURABLE, MIXED, UNFAVOURABLE, QUIET`) and
  `TransitBody` enum (`MOON, SUN`) in `core/src/main/java/com/celestia/core/prediction/`
- [x] T007 [P] `ActivatedHouse` record (`int house, int strength, Set<Graha>
  lords`; compact ctor: `house` 1..12, `strength == lords.size()` in 1..5,
  `Set.copyOf`) in `core/src/main/java/com/celestia/core/prediction/ActivatedHouse.java`
- [x] T008 [P] `FoundationalTypesTest` in
  `core/src/test/java/com/celestia/core/prediction/FoundationalTypesTest.java` —
  every `Matter`: favourable non-empty, favourable ∩ obstructive = ∅, all houses
  1..12, `source()` non-blank; `ActivatedHouse` rejects `strength != lords.size()`
  and out-of-range `house`

**Checkpoint**: `./mvnw -pl core -am test` green.

---

## Phase 3: User Story 1 — House-group taxonomy (P1) 🎯 MVP

**Goal**: matter → its favourable / obstructive house sets + source.

### Tests (write first)

- [ ] T009 [P] [US1] `HouseGroupsTest` in
  `core/src/test/java/com/celestia/core/prediction/HouseGroupsTest.java` —
  `HouseGroups.fromKey("marriage")` == `Matter.MARRIAGE`; an unknown key →
  `IllegalArgumentException`; every `Matter.key()` round-trips through
  `fromKey`; `MARRIAGE.favourable()` == {2, 7, 11}, `CAREER.favourable()` ==
  {2, 6, 10, 11}; `HouseGroups.all()` == every `Matter`

### Implementation

- [ ] T010 [US1] `HouseGroups.fromKey(String)` (unknown → `IllegalArgumentException`)
  and `all()` in `core/src/main/java/com/celestia/core/prediction/HouseGroups.java`

**Checkpoint**: `explainHouseGrouping` answerable.

---

## Phase 4: User Story 2 — Dasha significators for a date (P1)

**Goal**: chart + reference instant → the running lords, their natal
significations, and the activated house set with strengths.

### Tests (write first)

- [ ] T011 [P] [US2] `DashaSignificatorsTest` in
  `core/src/test/java/com/celestia/core/prediction/DashaSignificatorsTest.java` —
  first add `core/src/test/java/com/celestia/core/prediction/SyntheticChart.java`
  (mirror SPEC-004's `com.celestia.core.dasha.SyntheticChart` — SPEC-003/004's are
  package-private and unreachable here). With a synthetic `NatalChart`: for every
  running lord `L`, `significationsByLord.get(L)` == `SignificatorTable.of(chart)
  .grahaSignificators(L)`; `activated` is exactly the union; `strengthOf(h)` ==
  the count of running lords signifying `h`; a house no running lord signifies is
  absent; `lordChangesWithinDay` true when a level's lord differs at `t ± 12 h`
- [ ] T012 [P] [US2] `DailyPredictionGoldenTest` (dasha section) — for the golden
  case, `dasha.significationsByLord` and `dasha.activated` (+ strengths) match
  `expected.daily`; `@EnabledIf` ephemeris data

### Implementation

- [ ] T013 [US2] `DashaSignificators` record + `DashaSignificators.compute(
  SignificatorTable table, DashaTimeline timeline, Instant referenceInstant)` in
  `core/src/main/java/com/celestia/core/prediction/DashaSignificators.java` —
  `running(t, 5)`; per-lord `grahaSignificators`; activated set (union) with
  `ActivatedHouse` strength = count of running levels; `lordChangesWithinDay` from
  `running(t ± 12 h, 5)`

**Checkpoint**: the dasha side is computable and golden-verified.

---

## Phase 5: User Story 3 — Transit contribution for a date (P1)

**Goal**: chart + reference instant + transit positions → the Moon / Sun lord
chains and the houses each supports.

### Tests (write first)

- [ ] T014 [P] [US3] `TransitContributionTest` in
  `core/src/test/java/com/celestia/core/prediction/TransitContributionTest.java` —
  hand-set Moon / Sun longitudes over a synthetic `NatalChart` (the T011 helper):
  `moonChain` == `KpLordage.chainFor(moonLon)`;
  `moonSupports` == `SignificatorTable.of(chart).grahaSignificators(moonChain
  .subLord()).houses().keySet()`; likewise the Sun; `supported()` == the union;
  `moonSubLordChangesWithinDay` true when the Moon's sub lord differs at `t ± 12 h`

### Implementation

- [ ] T015 [US3] `TransitContribution` record + `TransitContribution.compute(
  SignificatorTable table, double moonLon, double sunLon, double moonLonBefore,
  double moonLonAfter)` in
  `core/src/main/java/com/celestia/core/prediction/TransitContribution.java` —
  the v1 sub-lord rule (research.md §3)

**Checkpoint**: the transit side is computable and golden-verified.

---

## Phase 6: User Story 4 — The daily reading (P1)

**Goal**: `getDailyPrediction` → a structured per-matter verdict with its
evidence.

**Independent Test**: golden case — every matter's verdict + hits + lords +
transits match; each verdict reproducible by hand.

### Tests (write first)

- [ ] T016 [P] [US4] `VerdictRulePropertyTest` (jqwik) in
  `core/src/test/java/com/celestia/core/prediction/VerdictRulePropertyTest.java` —
  over random (favourable set, obstructive set, activated house→strength map,
  transit-supported set): exactly one of the 4 rows matches; `QUIET` iff
  `favActive` and `obsActive` both empty; `UNFAVOURABLE` iff `obsStrength >
  favStrength` (strict); `FAVOURABLE` iff `favActive` non-empty, `favTriggered`
  non-empty, and not row 2; `MIXED` otherwise (research.md §5)
- [ ] T017 [P] [US4] `VerdictRuleTest` — the four spec acceptance scenarios
  (FAVOURABLE, MIXED = ripe-not-triggered, UNFAVOURABLE, QUIET) as concrete
  cases; **traceability**: rebuild each `verdict` from the `MatterVerdict` fields
  (`favourableHit` / `obstructiveHit` / `lords` / `transits`) and the §5 table
- [ ] T018 [P] [US4] `DailyPredictionGoldenTest` (full) — golden case:
  `referenceInstant`; `transit.moonChain().subLord()` / `sunChain().subLord()`,
  `transit.moonSupports()` / `sunSupports()`, `dasha.lordChangesWithinDay` and
  `transit.moonSubLordChangesWithinDay` == `expected.daily` (SC-003); and for
  every `Matter` the `verdict` + `favourableHit` + `obstructiveHit` + `lords` +
  `transits` == `expected.daily`; `@EnabledIf` ephemeris data
- [ ] T019 [P] [US4] `DailyPredictionFactoryTest` (`@EnabledIf`) — `predict(chart,
  date, longitude)`: a date before the birth date → `IllegalArgumentException`;
  `referenceInstant` == `date` 12:00 UTC − `round(longitude/15·3600) s`; a
  year-1600 birth or date → `Accuracy.REDUCED`, no exception; deterministic

### Implementation

- [ ] T020 [US4] `VerdictRule.evaluate(Matter matter, DashaSignificators dasha,
  Set<Integer> transitSupported)` → `MatterVerdict` in
  `core/src/main/java/com/celestia/core/prediction/VerdictRule.java` — the 4-row
  total function (research.md §5); populate `favourableHit` / `obstructiveHit` /
  `lords` (running lords that activated the hit houses) / `transits`
- [ ] T021 [US4] `MatterVerdict` record (`Matter, Verdict, Set<Integer>
  favourableHit, Set<Integer> obstructiveHit, Set<Graha> lords, Set<TransitBody>
  transits`) and `DailyPrediction` record (`Instant referenceInstant,
  DashaSignificators dasha, TransitContribution transit, List<MatterVerdict>
  verdicts, Accuracy accuracy, EngineVersion engineVersion`; `verdictFor(Matter)`)
  in `core/src/main/java/com/celestia/core/prediction/`
- [ ] T022 [US4] `DailyPredictionFactory` in
  `core/src/main/java/com/celestia/core/prediction/DailyPredictionFactory.java` —
  `DailyPredictionFactory(PositionProvider positions)`; `predict(NatalChart,
  LocalDate date, double longitude)` computes `referenceInstant`, rejects a date
  before birth, fetches the transit `EphemerisResult` at the instant and `± 12 h`;
  pure `compute(NatalChart, Instant referenceInstant, EphemerisResult atReference,
  EphemerisResult before, EphemerisResult after)` — builds `SignificatorTable.of`
  + `DashaTimeline.from`, then `DashaSignificators` + `TransitContribution` +
  `VerdictRule` per `Matter`; `accuracy` from the reference Moon

**Checkpoint**: `getDailyPrediction` works and is golden-verified.

---

## Phase 7: User Story 5 — Reproducible correctness (P2)

- [ ] T023 [P] [US5] `DailyPredictionDeterminismTest` — `compute(...)` twice on
  the golden case → equal `DailyPrediction`; `predict(...)` twice → equal
- [ ] T024 [US5] `.github/workflows/ci.yml` — add `DailyPredictionGoldenTest` and
  `DailyPredictionDeterminismTest` to the OS-matrix determinism step

**Checkpoint**: the daily reading is a CI gate.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T025 [P] `core/src/main/java/com/celestia/core/REFERENCES.md` — add the
  house-group table (cite KSK's *KP Readers* house significations; the 12th-from
  negation), the **v1** sub-lord transit rule, the local-noon reference-instant
  rule, and the **v1** 4-row verdict function (with the exact thresholds); add
  `prediction.Matter`, `prediction.VerdictRule`, `prediction.DailyPredictionFactory`
  to the `EngineVersion` bump list; mark the whole ruleset "v1 — see
  `specs/006-daily-prediction/research.md`"
- [ ] T026 [P] `DailyPredictionPerformanceTest` `@Tag("perf")` in
  `core/src/test/java/com/celestia/core/prediction/DailyPredictionPerformanceTest.java`
  — a full reading (given a cast `NatalChart`) under a 150 ms warm guard (SC-005
  target 50 ms); excluded from the default run
- [ ] T027 [P] Update `core/README.md` (+ `HouseGroups` / `explainHouseGrouping`,
  `DailyPredictionFactory`)
- [ ] T028 Run `specs/006-daily-prediction/quickstart.md` end to end; fix drift
- [ ] T029 `./mvnw -q verify` — full reactor green incl.
  `DeterminismArchitectureTest` / `LayeringArchitectureTest`

---

## Dependencies & Execution Order

1. **Setup (P1)** — T001–T002 (golden data) block T012 / T018; T004 blocks the
   loader-based tests; T003 is SC-006 (parallel, human).
2. **Foundational (P2)** — `Matter`, `Verdict`, `TransitBody`, `ActivatedHouse`;
   blocks every user story.
3. **US1 (P3)** — `HouseGroups`; depends only on Foundational.
4. **US2 (P4)** — `DashaSignificators.compute`; depends on Foundational +
   SPEC-003/004. Independent of US1 / US3.
5. **US3 (P5)** — `TransitContribution.compute`; depends on Foundational +
   SPEC-003. Independent of US1 / US2.
6. **US4 (P6)** — `VerdictRule` + `DailyPredictionFactory`; depends on US1 + US2 +
   US3. T020 (rule) and T021 (records) → T022 (factory).
7. **US5 (P7)** — depends on US4.
8. **Polish (P8)** — after the stories you intend to ship.

### Parallel opportunities

- Setup: T003 (human) and T004 in parallel; T001 → T002 serial.
- Foundational: T005 / T006 / T007 in parallel; T008 after.
- US2 / US3 whole phases in parallel with each other and with US1.
- US4: T016 / T017 (tests) in parallel; T020 → T021 → T022 serial; T018 / T019
  after T022.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US3 → US4. Delivers
`getDailyPrediction` and `explainHouseGrouping` — the two tools the agent needs.
Stop, run `quickstart.md`, open the PR.

**Blocked-work note**: the golden tests (T012, T018) need the extended golden data
(T002). Everything else is unblocked — SPEC-002/003/004 are on `master`, no new
dependency.

---

## Notes

- No new module, no new dependency, no new ADR — additive to `core`.
- The natal side (`SignificatorTable.of`, `DashaTimeline.from`) is pure; the
  factory's only I/O is three `PositionProvider.positions(...)` calls for the
  transit Moon / Sun.
- Output is **structured only** (Constitution IV) — verdicts + evidence; the LLM
  writes the sentence.
- The whole ruleset is **v1**, documented in `research.md`, versioned via
  `EngineVersion`. Every rule added gets a citation or a "v1 design choice" note
  in `core/REFERENCES.md` (Constitution III).
- Commit per task or tight group; keep `master` releasable (work on `006-...`).
