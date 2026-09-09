---
description: "Task list for SPEC-003 — Significators & Ruling Planets"
---

# Tasks: Significators & Ruling Planets (SPEC-003)

**Input**: Design documents in `specs/003-significators-ruling-planets/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 5 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US4
(both significator views + ruling planets — everything SPEC-005 needs).

Builds on SPEC-002 (merged to `master`): `NatalChart`, `Cusp`, `HousePlacement`,
`KpLordage`, `Graha`, `Sign`, `PositionProvider`, `HouseProvider`, `BirthData`,
`EngineVersion`, `Accuracy`, the golden-chart harness.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [x] T001 Extend `tools/ephe-crosscheck/compute_golden.py` with the four-step
  significator algorithm (research.md §1) and the ruling-planet rules (§3): emit
  `expected.significators.by_house` (12 lists of `{graha, steps}`),
  `expected.significators.by_graha` (9 entries), `expected.node_agency` (Rahu /
  Ketu agents + significations). Reuse the existing `lord_chain`, `bhava`, cusp
  helpers.
- [x] T002 Add **one** `expected.ruling_planets` block to one golden file: a fixed
  judgment instant + place (independent of the birth data) → RP set with sources,
  the resolved KP weekday, day lord, and sunrise instant.
- [x] T003 Regenerate the golden files (`--write`); verify determinism (re-run,
  diff `expected`). Update `core/src/test/resources/golden/README.md` with the new
  fields and settings.
- [ ] T004 [P] SC-006: transcribe the twelve per-house significator lists for
  **one** golden chart from a KP textbook worked example (or two agreeing
  mainstream KP tools on KP-New ayanamsa + mean node + Placidus); record it in
  `verification.significators_human_check` and note any single-source values.
- [x] T005 [P] Extend `core/src/test/java/com/celestia/core/golden/GoldenChart.java`
  to parse `expected.significators.*`, `expected.node_agency`, and
  `expected.ruling_planets`.

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [x] T006 [P] `Step` enum (`STAR_OF_OCCUPANT` 1, `OCCUPANT` 2, `STAR_OF_OWNER` 3,
  `OWNER` 4; `int rank()`) in `core/src/main/java/com/celestia/core/judgement/Step.java`
- [x] T007 [P] `Significator` (`Graha, int house, Set<Step> steps`; `strongestStep()`;
  non-empty immutable steps), `HouseSignificators` (`int house, List<Significator>`;
  `stepsFor`, `signifies`), `GrahaSignificators` (`Graha, Map<Integer,Set<Step>>`;
  `signifies`) records in `core/.../judgement/`
- [x] T008 [P] `NodeAgency` record (`Graha node, Set<Graha> conjunctGrahas, Graha
  signLord, Graha starLord, Set<Graha> agents`) in `core/.../judgement/NodeAgency.java`
- [x] T009 [P] `RpSource` enum (`LAGNA_SIGN, LAGNA_STAR, LAGNA_SUB, MOON_SIGN,
  MOON_STAR, MOON_SUB, DAY_LORD, NODE`) and `RulingPlanet` record (`Graha, Set<RpSource>`)
  in `core/.../judgement/`
- [x] T010 [P] `KpWeekday` enum (`SUNDAY..SATURDAY`, each `Graha lord()`;
  `static KpWeekday of(DayOfWeek)`) in `core/.../judgement/KpWeekday.java`
- [x] T011 [P] `FoundationalTypesTest` — `Step.rank()` 1..4; `Significator` rejects
  empty steps; `KpWeekday` maps Sun→Sunday … Saturn→Saturday and `of(DayOfWeek)` round-trips

**Checkpoint**: `./mvnw -pl core -am test` green.

---

## Phase 3: User Story 1 — Four-step significators of a house (P1) 🎯 MVP

**Goal**: `NatalChart` + house → the ranked four-step significators.

**Independent Test**: golden charts — the 12 per-house lists (membership + step tags).

### Tests (write first)

- [x] T012 [P] [US1] `SignificatorTableTest` in `core/src/test/java/com/celestia/core/judgement/SignificatorTableTest.java` — a hand-built small chart (or a golden chart): a house with occupants populates all 4 steps; an empty house has empty 1–2, non-empty 3–4; a graha qualifying via 2 steps appears once tagged with both, ordered by the stronger; owner==occupant tagged `{OCCUPANT,OWNER}`
- [x] T013 [P] [US1] `SignificatorGoldenTest` — for each golden chart, `houseSignificators(h)` for all 12 houses == `expected.significators.by_house[h]` (graha membership **and** step-tag sets), ordering deterministic; `@EnabledIf` ephemeris data

### Implementation

- [x] T014 [US1] Per-graha lord-chain helper (star lord, sign it occupies) in `SignificatorTable` — `KpLordage.chainFor(chart.position(g).longitude())`
- [x] T015 [US1] Effective-occupants computation (research.md §1 step 2): occupants of a house ∪ (for each node occupant) its agents = conjunct non-node grahas in the same bhava ∪ occupied-sign lord ∪ occupied-star lord
- [x] T016 [US1] `SignificatorTable.of(NatalChart)` in `core/.../judgement/SignificatorTable.java` — steps 1–4 per house (star of effective occupants / effective occupants / star of owner / owner); merge, de-dupe, order by `strongestStep().rank()` then `Graha` ordinal; build `HouseSignificators` ×12; carry `chart.engineVersion()`
- [x] T017 [P] [US1] `SignificatorOrderingTest` — the per-house list has no duplicate graha; order is by strongest step then ordinal; deterministic across two `of(chart)` calls

**Checkpoint**: four-step significators computable and golden-verified.

---

## Phase 4: User Story 2 — Per-graha significator table (P1)

**Goal**: the inverse view; `grahaSignificators(g)`.

### Tests (write first)

- [x] T018 [P] [US2] `SignificatorTransposePropertyTest` (jqwik over the golden charts, or a generated chart) — for every `Graha g` and house `h`, `grahaSignificators(g).signifies(h)` ⇔ `houseSignificators(h).signifies(g)`, with identical step sets (SC-002)
- [x] T019 [P] [US2] `GrahaSignificatorGoldenTest` — for each golden chart, `grahaSignificators(g)` == `expected.significators.by_graha[g]`

### Implementation

- [x] T020 [US2] `SignificatorTable.grahaSignificators(Graha)` — transpose the twelve `HouseSignificators` (no independent rule); immutable `Map<Integer,Set<Step>>`

**Checkpoint**: per-graha table is the verified transpose.

---

## Phase 5: User Story 3 — Rahu / Ketu agency (P2)

**Goal**: expose and verify node agency explicitly.

### Tests (write first)

- [x] T021 [P] [US3] `NodeAgencyTest` — a node conjoined with a graha → agents include that graha; a node alone → agents = `{signLord, starLord}`; `nodeAgency(SUN)` throws `IllegalArgumentException`
- [x] T022 [P] [US3] `NodeAgencyGoldenTest` — for each golden chart, `nodeAgency(RAHU)` / `nodeAgency(KETU)` == `expected.node_agency`; and every agent is a step-2 (`OCCUPANT`) significator of the node's bhava, with the node itself present there too (FR-008)

### Implementation

- [x] T023 [US3] `SignificatorTable.nodeAgency(Graha node)` — surface the `NodeAgency` computed for the effective-occupants fold (T015); reject non-nodes

**Checkpoint**: node agency exposed and golden-verified.

---

## Phase 6: User Story 4 — Ruling planets for a moment (P1)

**Goal**: judgment instant + place → ruling planets with sources and the day lord.

**Independent Test**: the golden RP example; the sunrise boundary (SC-004).

### Tests (write first)

- [x] T024 [P] [US4] `SunriseProviderContractTest` in `ephemeris/src/test/java/com/celestia/ephemeris/SunriseProviderContractTest.java` — `sunriseBefore(t, lat, lon)` returns an instant ≤ t and > t − 26h; two nearby instants on the same KP day return the same sunrise; polar summer latitude → `Optional.empty()`; deterministic
- [x] T025 [P] [US4] `KpWeekdayTest` in `core/src/test/java/com/celestia/core/judgement/KpWeekdayTest.java` — `resolve(sunrise + 1 min)` and `resolve(sunrise − 1 min)` differ by one weekday (SC-004); no-sunrise input → civil weekday + `fallback == true`
- [x] T026 [P] [US4] `RulingPlanetsTest` — `compute(...)` with hand-set ascendant/moon longitudes: the 6 lagna/moon lords + day lord present with correct sources; `includeSubLords=false` drops `LAGNA_SUB` / `MOON_SUB`; node added when its occupied-sign/star lord is ruling or it shares the Moon/Asc sign or nakshatra (no orb conjunction); deterministic; a year-1600 judgment instant via `at(...)` → `RulingPlanets` with `Accuracy.REDUCED`, no exception (FR-017)
- [x] T027 [P] [US4] `RulingPlanetsGoldenTest` — the golden RP example: `at(judgment)` == `expected.ruling_planets` (planet set, sources, day lord, weekday, sunrise instant); `@EnabledIf` ephemeris data

### Implementation

- [x] T028 [P] [US4] `SunriseProvider` interface in `ephemeris/.../SunriseProvider.java` (Javadoc = the contract)
- [x] T029 [US4] `SwissEphemerisSunriseProvider` in `ephemeris/.../swisseph/SwissEphemerisSunriseProvider.java` — `swe_rise_trans(jd, SE_SUN, null, epheflag, SE_CALC_RISE, {lon,lat,0}, 0, 0, tret, serr)`; "sunrise before" = latest rise ≤ instant (start at t − 1.05d, advance ≤ 2×); rc `−2` → `Optional.empty()`; synchronized handle; no SE type in the public API (FR-018)
- [x] T030 [US4] `KpWeekday.resolve(Instant, double lat, double lon, SunriseProvider)` → `KpWeekday.Resolution(weekday, fallback)` — sunrise-boundary weekday: weekday of `(sunrise + longitude/15 h)`'s UTC `LocalDate`; on `Optional.empty()` use `(instant + longitude/15 h)`'s `LocalDate` and `fallback = true`. Code comment: the `longitude/15 h` LMT offset is a stand-in for the real civil timezone (SPEC-007); wrong only for a large tz-vs-LMT offset with sunrise near midnight
- [x] T031 [US4] `RulingPlanets` record + `RulingPlanets.Options` (`includeSubLords` default true, `includeNodeAspects` default false) in `core/.../judgement/RulingPlanets.java`
- [x] T032 [US4] `RulingPlanetsFactory` in `core/.../judgement/RulingPlanetsFactory.java` — instance `at(BirthData)` wires `PositionProvider` (Moon) + `HouseProvider.anglesOnly` (Ascendant) + `SunriseProvider`; `static compute(...)` pure: the 6 lagna/moon lords + `DAY_LORD` + node additions (research.md §3); `accuracy` from the Moon position

**Checkpoint**: ruling planets computable and golden-verified.

---

## Phase 7: User Story 5 — Reproducible correctness (P2)

- [x] T033 [P] [US5] `SignificatorDeterminismTest` — `SignificatorTable.of` twice on a golden chart; compare the **accessor outputs** (`houseSignificators(h)` for h=1..12 and `grahaSignificators(g)` for every g — these are records with value equality), since `SignificatorTable` itself is a class without `equals`
- [x] T034 [P] [US5] `RulingPlanetsDeterminismTest` — `compute(...)` and `at(...)` twice → equal `RulingPlanets`
- [x] T035 [US5] `.github/workflows/ci.yml` — add `SignificatorGoldenTest`,
  `GrahaSignificatorGoldenTest`, `NodeAgencyGoldenTest`, `RulingPlanetsGoldenTest`
  to the OS-matrix determinism step

**Checkpoint**: judgement correctness is a CI gate.

---

## Phase 8: Polish & Cross-Cutting

- [x] T036 [P] `core/src/main/java/com/celestia/core/REFERENCES.md` — add the
  four-step significator rule, the node-agency rule, the ruling-planet sources +
  node rule, and the day-lord-at-sunrise rule (cite KSK / *KP Readers*); note that
  a `core.judgement` rule change also bumps `EngineVersion.rules`
- [x] T037 [P] `SignificatorPerformanceTest` `@Tag("perf")` — all 12 houses +
  per-graha table for a chart < 200 ms warm guard (SC-005 target 20 ms); excluded
  from the default run
- [x] T038 [P] Update `ephemeris/README.md` (+ `SunriseProvider`) and
  `core/README.md` (+ `SignificatorTable`, `RulingPlanetsFactory`)
- [x] T039 Run `specs/003-significators-ruling-planets/quickstart.md` end to end; fix drift
- [x] T040 `./mvnw -q verify` — full reactor green incl. `DeterminismArchitectureTest` / `LayeringArchitectureTest`

---

## Dependencies & Execution Order

1. **Setup (P1)** — T001–T003 (golden data) block the golden tests; T005 blocks
   the loader-based tests; T004 is SC-006 (parallel, human).
2. **Foundational (P2)** — value types; blocks every user story.
3. **US1 (P3)** — `SignificatorTable.of` incl. the effective-occupant (node
   agency) fold; depends only on Foundational.
4. **US2 (P4)** — transpose; depends on US1.
5. **US3 (P5)** — exposes the node agency built in US1; depends on US1.
6. **US4 (P6)** — ruling planets + `SunriseProvider`; **independent of US1–US3**
   (different input), depends only on Foundational + SPEC-001/002. Can run in
   parallel with US1–US3.
7. **US5 (P7)** — depends on US1 + US4.
8. **Polish (P8)** — after the stories you intend to ship.

### Parallel opportunities

- Foundational: T006–T010 in parallel; T011 after.
- US1: T012/T013 (tests) in parallel; T014 → T015 → T016 serial; T017 after.
- US4 (whole phase) parallel with US1–US3, by a second person.
- US4: T024/T025/T026/T027 in parallel; T028 → T029; T030 after T029; T031 → T032.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US4. Delivers both significator views
and the ruling planets — everything SPEC-005 (horary) and SPEC-004 (dasha)
consume. Stop, run `quickstart.md`, open the PR.

**Blocked-work note**: the golden tests (T013, T019, T022, T027) need the extended
golden data (T003). Everything else is unblocked — SPEC-002 is on `master`, no new
dependency.

---

## Notes

- No new module, no new dependency — additive to `ephemeris` + `core`.
- `KpLordage.chainFor` is reused for graha, Ascendant and Moon lord chains.
- Every rule added gets a citation in `core/REFERENCES.md` (Constitution III).
- Commit per task or tight group; keep `master` releasable (work on `003-...`).
