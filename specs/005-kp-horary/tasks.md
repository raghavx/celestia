---
description: "Task list for SPEC-005 — KP Horary 1–249"
---

# Tasks: KP Horary 1–249 (SPEC-005)

**Input**: Design documents in `specs/005-kp-horary/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 5 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US3
(the 249 map, the horary Ascendant, and the cast chart — everything
`castHoraryChart` needs).

Builds on SPEC-001/002/003 (merged to `master`): `VimshottariPartition`,
`KpLordage` / `LordChain`, `Sign`, `Span`, `NatalChart`, `NatalChartFactory`,
`Cusp`, `AnglePoint`, `HouseResult`, `Angle`, `PlacidusUndefinedException`,
`PositionProvider`, `SunriseProvider`, `SignificatorTable`, `RulingPlanetsFactory`,
`BirthData`, `EngineVersion`, `Accuracy`, the golden-chart harness. No new module,
no new dependency, no new ADR.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [x] T001 Extend `tools/ephe-crosscheck/compute_golden.py` with a horary case
  (research.md §3–§6): a fixed number 1–249 + a fixed judgment instant + place →
  `expected.horary` — the Ascendant (longitude + lord chain), the twelve cusps
  (longitude + lord chain), the nine planetary placements (bhava, rasi), the
  **per-house significators** (`by_house`, from `compute_significators` fed the
  horary houses — backs SC-003), and the horary ruling planets. Reuse `lord_chain`
  / cusp / `compute_significators` helpers; add the RAMC inversion +
  `swe_houses_armc` (tropical) + `swe_get_ayanamsa_ut` round-trip.
- [x] T002 Regenerate the golden files (`--write`); verify determinism (re-run,
  diff `expected`). Update `core/src/test/resources/golden/README.md` with the
  `expected.horary` shape, the fixed number / instant / place, and the
  `horary_human_check` protocol.
- [ ] T003 [P] SC-006: transcribe a sample of K. S. Krishnamurti's published 249
  table (number → sub lord + degree range, ~10 rows spanning the zodiac) and one
  cast chart's cusp 1 + one other cuspal sub lord from a mainstream KP horary tool
  (KP-New ayanamsa + Placidus); record in `verification.horary_human_check` and
  note any single-source value.
- [x] T004 [P] Extend `core/src/test/java/com/celestia/core/golden/GoldenChart.java`
  to parse `expected.horary` (ascendant, cusps, placements, ruling planets).

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [ ] T005 [P] `HoraryArc` record (`int number, BigFraction start, BigFraction
  end, Sign sign, LordChain lordChain`; `double startDeg()/endDeg()/midpointDeg()`;
  `Graha subLord()`; `boolean contains(double)` half-open; compact ctor validates
  `1 ≤ number ≤ 249`, `start < end`, `Sign.at(midpointDeg()) == sign`) in
  `core/src/main/java/com/celestia/core/horary/HoraryArc.java`
- [ ] T006 [P] `HoraryHouseProvider` interface
  (`HouseResult housesFor(BirthData judgment, double ascendantLongitude)`;
  Javadoc = the contract) in
  `ephemeris/src/main/java/com/celestia/ephemeris/HoraryHouseProvider.java`
- [ ] T007 [P] `FoundationalTypesTest` in
  `core/src/test/java/com/celestia/core/horary/FoundationalTypesTest.java` —
  `HoraryArc` rejects `number` 0 / 250 and `start ≥ end`; `contains` is half-open;
  `subLord()` == `lordChain().subLord()`

**Checkpoint**: `./mvnw -pl ephemeris,core -am test` green.

---

## Phase 3: User Story 1 — The 1–249 map (P1) 🎯 MVP

**Goal**: number 1–249 → the exact arc (bounds, sign, lord chain).

**Independent Test**: the 249 arcs tile `[0°, 360°)` exactly; the count is 249; a
sample matches KSK's table.

### Tests (write first)

- [ ] T008 [P] [US1] `Horary249TilingPropertyTest` in
  `core/src/test/java/com/celestia/core/horary/Horary249TilingPropertyTest.java` —
  `Horary249.arcs().size() == 249`; `arcs().get(0).start()` == `0` and
  `arcs().get(248).end()` == `360` as exact `BigFraction`;
  `arc[n].end() == arc[n+1].start()` for all n; every arc lies in one `Sign`;
  every arc's `subLord()` == the sub lord of the `VimshottariPartition
  .subDivisions()` span covering its midpoint
- [ ] T009 [P] [US1] `Horary249SnapshotTest` — the full 249-row list (number,
  start°, end°, sign, sub lord) matches
  `core/src/test/resources/horary/horary-249.json`. The test **writes the file
  when it is missing** (as the golden harness does); it is then committed and any
  later diff is a table change to review. Include the KSK-sample rows from T003 as
  an inline assertion once transcribed.
- [ ] T010 [P] [US1] `Horary249Test` — `arc(0)` and `arc(250)` throw
  `IllegalArgumentException`; `arc(1).startDeg()` == 0; `arc(249).endDeg()` == 360;
  a hand-checked number's sub lord and sign

### Implementation

- [ ] T011 [US1] Sign-crossing split in
  `core/src/main/java/com/celestia/core/horary/Horary249.java` — for each
  `VimshottariPartition.subDivisions()` span, split it at every `30k°`
  (`k = 1..11`) it **strictly** crosses (`s < c < e`); collect the pieces
- [ ] T012 [US1] `Horary249.arcs()` (built once, cached), `arc(int number)`
  (1..249), `count()` — sort the pieces by `start`, number from 1, build each
  `HoraryArc` with `Sign.at(midpoint)` and `KpLordage.chainFor(midpoint)`

**Checkpoint**: the 249 map is computable and tiling-verified.

---

## Phase 4: User Story 2 — The horary Ascendant (P1)

**Goal**: number → a fixed Ascendant angle (arc midpoint + lord chain).

### Tests (write first)

- [ ] T013 [P] [US2] `HoraryAscendantTest` in
  `core/src/test/java/com/celestia/core/horary/HoraryAscendantTest.java` —
  `HoraryChartFactory.ascendant(n).longitude()` == `Horary249.arc(n).midpointDeg()`
  for every `n`; `.lordChain().subLord()` == `arc(n).subLord()`; two numbers that
  share a sub lord (an arc split at a sign cusp) give the same sub lord in
  different signs; `ascendant(0)` / `ascendant(250)` throw

### Implementation

- [ ] T014 [US2] `HoraryChartFactory.ascendant(int number)` in
  `core/src/main/java/com/celestia/core/horary/HoraryChartFactory.java` — returns
  `new AnglePoint(Angle.ASCENDANT, Horary249.arc(number).midpointDeg(),
  Horary249.arc(number).lordChain())`

**Checkpoint**: the horary Ascendant is available without a chart.

---

## Phase 5: User Story 3 — The full horary chart (P1)

**Goal**: number + judgment instant + place → a `NatalChart` with cusp 1 fixed by
the number.

**Independent Test**: golden horary case — cusp 1 == the number's Ascendant, the
twelve cuspal sub lords and nine placements match, and the SPEC-003 significators
reproduce.

### Tests (write first)

- [ ] T015 [P] [US3] `HoraryHouseProviderContractTest` in
  `ephemeris/src/test/java/com/celestia/ephemeris/HoraryHouseProviderContractTest.java`
  — `housesFor(judgment, asc)`: `cuspLongitudes().get(0)` bit-identical to `asc`;
  **round-trip** — take a normal `SwissEphemerisHouseProvider.houses(bd)` cast,
  feed its Ascendant back through `housesFor(bd, asc)`, the twelve cusps match to
  ≤ 1′; a polar latitude → `PlacidusUndefinedException`; a year-1600 instant →
  `Accuracy.REDUCED`, no exception; deterministic
- [ ] T016 [P] [US3] `HoraryChartFactoryTest` — `cast(n, j).cusp(1)` ==
  `ascendant(n).longitude()`; same number + place, two instants **5 minutes
  apart** → identical cusps and Moon longitudes differing by **2′–3′** (SC-004);
  same instant + place, two numbers → identical planets, different cusps; polar
  judgment → `PlacidusUndefinedException`; `cast(0/250, ...)` throw;
  `SignificatorTable.of(cast(...))` and `RulingPlanetsFactory` accept the chart
- [ ] T017 [P] [US3] `HoraryGoldenTest` — the golden horary case: `cast(...)
  .cusp(1)` == `expected.horary.ascendant`; the twelve cuspal sub lords and the
  nine placements (bhava, rasi) match; `SignificatorTable.of(chart)`
  `houseSignificators(h)` for all 12 houses == `expected.horary.significators
  .by_house[h]` (membership + step tags — SC-003); `@EnabledIf` ephemeris data

### Implementation

- [ ] T018 [US3] RAMC-from-Ascendant in
  `ephemeris/src/main/java/com/celestia/ephemeris/swisseph/SwissEphemerisHoraryHouseProvider.java`
  — obliquity via `swe_calc_ut(jdUt, SE_ECL_NUT, iflag, xx, serr)` (`xx[0]`),
  ayanamsa via `swe_get_ayanamsa_ut(jdUt)`; closed form
  `armc = atan2(−cos λ, sin λ·cos ε + tan φ·sin ε)` normalised `[0, 360)`
  (Meeus, *Astronomical Algorithms* 2nd ed. ch. 13 / house formulae — **confirm
  the sign convention against the T015 round-trip before trusting it**);
  a 60-iteration bisection fallback behind the same method (research.md §3)
- [ ] T019 [US3] `SwissEphemerisHoraryHouseProvider.housesFor(BirthData, double)`
  — polar check (`|lat| ≥ config.polarLimit()` → `PlacidusUndefinedException`);
  `tropicalAsc = normalise(asc + ayanamsa)`; RAMC (T018);
  `swe_houses_armc(armc, lat, eps, (int)'P', cusp, ascmc)`; sidereal cusp =
  `normalise(cusp − ayanamsa)`; **force `cusp[0]` = the sidereal Ascendant**;
  build `HouseResult` (synchronized handle, no SE type in the public API)
- [ ] T020 [US3] `HoraryChartFactory(PositionProvider positions,
  HoraryHouseProvider houses)` + `cast(int number, BirthData judgment)` +
  `static assemble(int number, BirthData judgment, EphemerisResult positions,
  HouseResult horaryHouses)` — validate `number` 1..249 and
  `horaryHouses.cusp(1)` == `arc(number).midpointDeg()`; delegate to
  `NatalChartFactory.assemble`

**Checkpoint**: `castHoraryChart` works and is golden-verified.

---

## Phase 6: User Story 4 — Horary ruling planets (P2)

**Goal**: number + judgment moment → SPEC-003 ruling planets with the number's
Ascendant.

### Tests (write first)

- [ ] T021 [P] [US4] `HoraryRulingPlanetsTest` in
  `core/src/test/java/com/celestia/core/horary/HoraryRulingPlanetsTest.java` —
  the lagna sign / star / sub lords equal those of `HoraryChartFactory.ascendant(n)`;
  the Moon lords and day lord match the judgment moment; equals
  `RulingPlanetsFactory.compute(...)` with the number's Ascendant; deterministic
- [ ] T022 [P] [US4] `HoraryRulingPlanetsGoldenTest` — the golden horary case's
  ruling planets == `expected.horary.ruling_planets` (planet set + sources + day
  lord + weekday); `@EnabledIf` ephemeris data

### Implementation

- [ ] T023 [US4] `HoraryRulingPlanets.at(int number, BirthData judgment,
  PositionProvider positions, SunriseProvider sunrise)` (+ an `Options` overload)
  in `core/src/main/java/com/celestia/core/horary/HoraryRulingPlanets.java` —
  `KpWeekday.resolve(...)` for the day lord; `RulingPlanetsFactory.compute(
  judgment, Horary249.arc(number).midpointDeg(), moonLongitude, rahuLongitude,
  weekday, fallback, moonAccuracy, engineVersion, options)`

**Checkpoint**: horary ruling planets computable and golden-verified.

---

## Phase 7: User Story 5 — Reproducible correctness (P2)

- [ ] T024 [P] [US5] `HoraryDeterminismTest` — `Horary249.arcs()` stable across
  two calls; `HoraryChartFactory.cast` twice on the golden case → equal
  `NatalChart` (value equality); `HoraryRulingPlanets.at` twice → equal
- [ ] T025 [US5] `.github/workflows/ci.yml` — add `Horary249SnapshotTest`,
  `HoraryGoldenTest`, `HoraryRulingPlanetsGoldenTest`, `HoraryDeterminismTest` to
  the OS-matrix determinism step

**Checkpoint**: horary correctness is a CI gate.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T026 [P] `core/src/main/java/com/celestia/core/REFERENCES.md` — add the 249
  table derivation (243 sub-spans split at the twelve sign cusps; cite KSK's
  *Krishnamurti Padhdhati* horary volume), the Ascendant-midpoint rule, and the
  RAMC-from-Ascendant inversion (cite Meeus, *Astronomical Algorithms* 2nd ed.
  ch. 13 / house formulae); add
  `horary.Horary249` / `horary.HoraryChartFactory` and
  `swisseph.SwissEphemerisHoraryHouseProvider` to the `EngineVersion` bump list
- [ ] T027 [P] `HoraryPerformanceTest` `@Tag("perf")` in
  `core/src/test/java/com/celestia/core/horary/HoraryPerformanceTest.java` — a
  cast chart under a 200 ms warm guard (SC-005 target 75 ms) and
  `Horary249.arcs()` first build under 20 ms; excluded from the default run
- [ ] T028 [P] Update `ephemeris/README.md` (+ `HoraryHouseProvider`) and
  `core/README.md` (+ `Horary249`, `HoraryChartFactory`, `HoraryRulingPlanets`)
- [ ] T029 Run `specs/005-kp-horary/quickstart.md` end to end; fix drift
- [ ] T030 `./mvnw -q verify` — full reactor green incl.
  `DeterminismArchitectureTest` / `LayeringArchitectureTest`

---

## Dependencies & Execution Order

1. **Setup (P1)** — T001–T002 (golden data) block T017 / T022; T004 blocks the
   loader-based tests; T003 is SC-006 (parallel, human).
2. **Foundational (P2)** — `HoraryArc` + the `HoraryHouseProvider` interface;
   blocks every user story.
3. **US1 (P3)** — `Horary249`; depends only on Foundational + SPEC-001.
4. **US2 (P4)** — the Ascendant accessor; depends on US1.
5. **US3 (P5)** — the house provider impl + the cast; depends on US2 + the
   Foundational interface. T018 → T019; T020 after T019 + US2.
6. **US4 (P6)** — horary RP; depends on US1 + SPEC-003 (`RulingPlanetsFactory`).
   Independent of US3, can run in parallel.
7. **US5 (P7)** — depends on US1 + US3 + US4.
8. **Polish (P8)** — after the stories you intend to ship.

### Parallel opportunities

- Setup: T003 (human) and T004 in parallel; T001 → T002 serial.
- Foundational: T005 / T006 in parallel; T007 after T005.
- US1: T008/T009/T010 (tests) in parallel; T011 → T012 serial.
- US3: T015/T016/T017 (tests) in parallel; T018 → T019 → T020 serial.
- US4 (whole phase) parallel with US3.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US3. Delivers the 249 map, the horary
Ascendant, and `castHoraryChart` — everything SPEC-006 and the agent tool consume.
Stop, run `quickstart.md`, open the PR.

**Blocked-work note**: the golden tests (T017, T022) need the extended golden data
(T002). Everything else is unblocked — SPEC-001/002/003 are on `master`, no new
dependency.

---

## Notes

- No new module, no new dependency, no new ADR — additive to `ephemeris` + `core`.
- A horary chart **is** a `NatalChart` — `NatalChartFactory.assemble` is reused
  unchanged; only the `HouseResult` construction is new.
- The 249 table is **derived** from `VimshottariPartition.subDivisions()`, not
  transcribed; the snapshot + SC-006 guard it.
- Every rule added gets a citation in `core/REFERENCES.md` (Constitution III).
- Commit per task or tight group; keep `master` releasable (work on `005-...`).
