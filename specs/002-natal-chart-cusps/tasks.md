---
description: "Task list for SPEC-002 — Natal Chart & Placidus Cusps"
---

# Tasks: Natal Chart & Placidus Cusps (SPEC-002)

**Input**: Design documents in `specs/002-natal-chart-cusps/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — correctness is the point (SC-001…SC-005; User Story 6 *is*
the correctness suite). Write tests first and watch them fail.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 (cusps +
cuspal sub lords + bhava — everything SPEC-003 needs).

Builds on SPEC-001 (merged to `master`): `PositionProvider`, `KpLordage`,
`Graha`, `Sign`, `Nakshatra`, `Longitudes`, `EngineVersion`, `Accuracy`,
`EphemerisResult`, the golden-chart harness.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [x] T001 Extend `tools/ephe-crosscheck/compute_golden.py` with a `swe.houses_ex(jd, lat, lon, b'P', FLG_SIDEREAL)` pass: emit `expected.cusps` (12 × {longitude + lord chain via the existing `lord_chain`}), `expected.angles` ({ascendant, midheaven} each with lord chain), and `expected.grahas[g].bhava` + `.rasi_house` (bhava = cusp-to-cusp half-open wrap-aware; rasi = whole sign from the Ascendant). Birth `latitude`/`longitude` already in each file.
- [x] T002 Regenerate the three golden files with `--write`; verify the run is deterministic (re-run, diff `expected`); bump `verification.status` note. Update `core/src/test/resources/golden/README.md` with the new fields.
- [x] T003 [P] Extend `core/src/test/java/com/celestia/core/golden/GoldenChart.java` to parse `expected.cusps`, `expected.angles`, and `bhava` / `rasi_house`.

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [x] T004 [P] `BirthData` record in `ephemeris/src/main/java/com/celestia/ephemeris/BirthData.java` — `(Instant instant, double latitude, double longitude)`; compact constructor rejects null instant, `|lat| > 90`, `|lon| > 180` with `IllegalArgumentException`
- [x] T005 [P] `Angle` enum (`ASCENDANT`, `MIDHEAVEN`) and `HouseSystem` enum (`PLACIDUS`) in `ephemeris/src/main/java/com/celestia/ephemeris/`
- [x] T006 [P] `PlacidusUndefinedException extends EphemerisException` in `ephemeris/.../PlacidusUndefinedException.java` — message names the latitude and the limit
- [x] T007 [P] `Cusp` (`int house, double longitude, LordChain lordChain`, `subLord()`), `AnglePoint` (`Angle, double longitude, LordChain`), `HousePlacement` (`Graha, int bhava, int rasiHouse`) records in `core/src/main/java/com/celestia/core/chart/`
- [x] T008 `Bhavas` utility in `core/src/main/java/com/celestia/core/chart/Bhavas.java` — `bhavaOf(double longitude, List<Double> cuspLongitudes)` (forward-arc, half-open `[cusp n, cusp n+1)`, `cusp[12]→cusp[0]` wrap) and `rasiHouseOf(Sign grahaSign, Sign ascendantSign)` (`1 + Math.floorMod(ordinal diff, 12)`)
- [x] T009 [P] `BhavasTest` — half-open at an exact cusp; wrap across 0°/360°; two close cusps (intercepted sign); every longitude → exactly one bhava in 1..12; `rasiHouseOf` for Asc-sign → 1, next sign → 2

**Checkpoint**: `./mvnw -pl ephemeris,core -am test` green.

---

## Phase 3: User Story 1 — The twelve cusps and the Ascendant (P1) 🎯 MVP

**Goal**: birth data → 12 Placidus cusps + Ascendant/MC, sidereal KP-New, each
with a full lord chain and an exposed cuspal sub lord.

**Independent Test**: golden charts' birth data → cusp longitudes within 1′ of the
reference, cuspal sub lords exact; cusp 1 == Ascendant.

### Tests (write first)

- [ ] T010 [P] [US1] `HouseProviderContractTest` in `ephemeris/src/test/java/com/celestia/ephemeris/HouseProviderContractTest.java` — 12 finite cusps in `[0,360)`; `cuspLongitudes[0]` bit-identical to `angles.get(ASCENDANT)`; consecutive forward arcs all > 0; `houseSystem == PLACIDUS`; deterministic; `EngineVersion` populated
- [ ] T011 [P] [US1] `HouseAccuracyTest` — in-range instant → `Accuracy.FULL`; year 1600 → `REDUCED`, no exception

### Implementation

- [ ] T012 [P] [US1] `HouseResult` record in `ephemeris/.../HouseResult.java` — `birthData`, `List<Double> cuspLongitudes` (size 12, `List.copyOf` in the compact constructor so the record has value equality — **not** `double[]`), `Map<Angle,Double> angles` (immutable copy), `HouseSystem`, `Accuracy`, `EngineVersion`; compact-constructor invariants (12 in `[0,360)`, monotone ring, `cuspLongitudes.get(0) == angles.get(ASCENDANT)`)
- [ ] T013 [US1] `HouseProvider` interface in `ephemeris/.../HouseProvider.java` — `houses(BirthData)`, `anglesOnly(BirthData)` (Javadoc = the contract). `anglesOnly` computes Asc/MC from the ARMC (sidereal time), not via Placidus — defined at any latitude below ±90° (research.md §2)
- [ ] T014 [US1] `SwissEphemerisHouseProvider` in `ephemeris/.../swisseph/SwissEphemerisHouseProvider.java` — `swe_set_sid_mode(KRISHNAMURTI)`, `swe_houses(jdUt, SEFLG_SIDEREAL, lat, lon, (int)'P', cusp[13], ascmc[10])`; set cusp 1 = `ascmc[0]`; expose the 12 cusps as `List<Double>`; **pre-check** `|lat| >= polarLimit` (from `SwissEphemerisConfig`, default 66.0) → `PlacidusUndefinedException` before any backend call; `anglesOnly` via `swe_houses_armc` Equal system / direct ARMC formula (never runs Placidus); thread-safe (synchronized handle); `Accuracy` mirrors the positions' range check; no SE type in any public member (FR-014)
- [ ] T015 [US1] Add `polarLimit` (default 66.0) to `SwissEphemerisConfig` and its `resolve()`
- [ ] T016 [P] [US1] `Cusps` helper in `core/src/main/java/com/celestia/core/chart/Cusps.java` — `fromHouseResult(HouseResult)` → `List<Cusp>` (12, lord chain via `KpLordage.chainFor`) and `AnglePoint` for each `Angle`
- [ ] T017 [P] [US1] `CuspsTest` — 12 `Cusp`s house 1..12; `cusp.subLord() == cusp.lordChain().subLord()`; `AnglePoint` for ASC has `longitude == cuspLongitudes[0]`

**Checkpoint**: cusps + angles + lord chains are computable and structurally verified.

---

## Phase 4: User Story 2 — Bhava placement (P1)

**Goal**: each graha assigned to exactly one bhava (cusp-to-cusp).

**Independent Test**: golden charts — each graha's bhava matches the reference and
is consistent with `[cusp n, cusp n+1)`.

### Tests (write first)

- [ ] T018 [P] [US2] `BhavaConsistencyPropertyTest` (jqwik) in `core/src/test/java/com/celestia/core/chart/BhavaConsistencyPropertyTest.java` — generate a **valid** cusp ring (12 random positive gaps normalised to sum 360°, cumulative from a random start), pick a random longitude, and assert `Bhavas.bhavaOf` returns the unique `n` whose forward arc `[cusp n, cusp n+1)` contains it; also assert exact-cusp longitudes land in the bhava that cusp *starts*
- [ ] T019 [P] [US2] `BhavaGoldenTest` — for each golden chart, each graha's computed bhava == `expected.grahas[g].bhava`

### Implementation

- [ ] T020 [US2] Wire bhava into placement inside `NatalChartFactory` (no separate helper — it already has the positions and the cusps): `HousePlacement.bhava` from `position(g).longitude()` and `HouseResult.cuspLongitudes` via `Bhavas.bhavaOf`

**Checkpoint**: every graha has a verified bhava.

---

## Phase 5: User Story 3 — Rasi (sign-based) house (P2)

**Goal**: each graha gets a whole-sign house counted from the Ascendant's sign.

### Tests (write first)

- [ ] T021 [P] [US3] `RasiHouseTest` in `core/src/test/java/com/celestia/core/chart/RasiHouseTest.java` — Asc sign → 1; graha one/two signs ahead → 2/3; wrap (graha behind the Asc sign) → 11/12; matches `expected.grahas[g].rasi_house` for the golden charts

### Implementation

- [ ] T022 [US3] Wire `HousePlacement.rasiHouse` from `position(g)`'s sign and the Ascendant's sign via `Bhavas.rasiHouseOf`

**Checkpoint**: rasi houses verified.

---

## Phase 6: User Story 4 — The natal chart as one object (P2)

**Goal**: one immutable `NatalChart` bundling everything, for SPEC-003/004/005.

### Tests (write first)

- [ ] T023 [P] [US4] `NatalChartApiTest` in `core/src/test/java/com/celestia/core/chart/NatalChartApiTest.java` — encodes `contracts/natal-chart-api.md`: 12 cusps in order; `cusp(1).longitude() == ascendant().longitude()`; `cuspSubLord(h) == cusp(h).lordChain().subLord()` for all h; `placements().keySet()` == nine grahas; every `bhava`/`rasiHouse` in 1..12
- [ ] T024 [P] [US4] `NatalChartFactoryTest` — `assemble(birthData, positions, houses)` is deterministic (equal inputs → equal `NatalChart`); `cast(birthData)` wires the providers and propagates `PlacidusUndefinedException`

### Implementation

- [ ] T025 [US4] `NatalChart` record/aggregate in `core/.../chart/NatalChart.java` — fields per data-model.md; accessors `position`, `cusp`, `cuspSubLord`, `ascendant`, `midheaven`, `placement`, `cusps`, `placements`, `ayanamsa`, `accuracy`, `engineVersion`; compact-constructor invariants
- [ ] T026 [US4] `NatalChartFactory` in `core/.../chart/NatalChartFactory.java` — `static assemble(BirthData, EphemerisResult, HouseResult)` (pure); instance `NatalChartFactory(PositionProvider, HouseProvider)` + `cast(BirthData)`; `accuracy` from the positions

**Checkpoint**: `NatalChart` assembled and invariant-checked.

---

## Phase 7: User Story 5 — High-latitude births (P3)

**Goal**: |lat| ≥ 66° refused with a named exception; Ascendant/MC still available.

### Tests

- [ ] T027 [P] [US5] `PlacidusPolarTest` in `ephemeris/src/test/java/com/celestia/ephemeris/PlacidusPolarTest.java` — `houses()` at 70°N → `PlacidusUndefinedException` naming the latitude; at 65°N → a normal `HouseResult`; at 91°N → `IllegalArgumentException` (from `BirthData`)
- [ ] T028 [P] [US5] `anglesOnly()` at 78°N returns `ASCENDANT` + `MIDHEAVEN` without throwing (ARMC path, not Placidus); the Ascendant is a finite longitude in `[0,360)`
- [ ] T029 [US5] `NatalChartFactory.cast` at 70°N propagates `PlacidusUndefinedException` (no partial chart)

**Checkpoint**: polar behaviour defined and tested.

---

## Phase 8: User Story 6 — Reproducible correctness (P2)

**Goal**: the extended golden suite reproduces every cusp / sub lord / bhava /
rasi value on every run.

### Tasks

- [ ] T030 [US6] `NatalChartGoldenTest` in `core/src/test/java/com/celestia/core/chart/NatalChartGoldenTest.java` — for each golden chart, cast via `SwissEphemerisPositionProvider` + `SwissEphemerisHouseProvider`; per cusp: longitude within 1′ of the reference (SC-001) and full lord chain exact; per graha: `bhava` and `rasiHouse` exact (SC-002); `@EnabledIf` ephemeris data present
- [ ] T031 [P] [US6] `NatalChartDeterminismTest` — cast a golden chart twice, assert equal `NatalChart`; `cusp(1)` bit-identical to the Ascendant across runs (SC-003)
- [ ] T032 [US6] `.github/workflows/ci.yml` — run `NatalChartGoldenTest` + `NatalChartDeterminismTest` in the OS-matrix step. (Note: the SPEC-001 "fingerprint" hashes the *static* golden JSONs, which is largely redundant — the real cross-platform check is these tests asserting `computed == stored` on each OS. Optionally have the test emit computed values to a scratch file and fingerprint that instead.)

**Checkpoint**: natal-chart correctness is a CI gate.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T033 [P] `core/src/main/java/com/celestia/core/REFERENCES.md` — add the bhava (cusp-to-cusp, KP Readers), rasi-house (whole sign from Asc), and Placidus/polar-limit rules
- [ ] T034 [P] `NatalChartPerformanceTest` `@Tag("perf")` in `core/src/test/java/com/celestia/core/chart/` — full chart (positions + cusps + placements + lord chains) < 250 ms warm guard (SC-006 target 75 ms); excluded from the default run
- [ ] T035 [P] Update `ephemeris/README.md` (+ `HouseProvider`, `BirthData`) and `core/README.md` (+ `NatalChart`, `NatalChartFactory`)
- [ ] T036 [P] `LICENSE-NOTICES.md` unchanged check (no new deps) — note SPEC-002 uses `swe_houses` from the same port
- [ ] T037 Run `specs/002-natal-chart-cusps/quickstart.md` end to end; fix drift
- [ ] T038 `./mvnw -q verify` — full reactor green incl. `DeterminismArchitectureTest` / `LayeringArchitectureTest`

---

## Dependencies & Execution Order

1. **Setup (P1)** — T001/T002 (golden data) block US6; T003 blocks the golden tests.
2. **Foundational (P2)** — blocks every user story. `Bhavas` (T008) is used by US2/US3.
3. **US1 (P3)** — `HouseProvider` + `Cusps`; depends only on Foundational. The
   polar pre-check lives here; US5 tests depend on US1.
4. **US2 (P4)** and **US3 (P5)** — depend on Foundational (`Bhavas`) + US1
   (`HouseResult`); independent of each other.
5. **US4 (P6)** — depends on US1 + US2 + US3 (it bundles their outputs).
6. **US5 (P7)** — depends on US1.
7. **US6 (P8)** — depends on US4 + the extended golden data (T002).
8. **Polish (P9)** — after the stories you intend to ship.

### Parallel opportunities

- Foundational: T004/T005/T006/T007 in parallel; then T008 → T009.
- US1: T010/T011 in parallel; T012 → T013 → T014 (same area); T016/T017 in parallel after T012.
- US2/US3 can be built in parallel by different people once US1 lands.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2. Delivers cusps + cuspal sub lords +
bhava — everything SPEC-003 (significators) needs. Stop, run `quickstart.md`, open
the PR.

**Blocked-work note**: T030 (`NatalChartGoldenTest`) needs the extended golden
data (T002). Everything else is unblocked once SPEC-001 is on `master` (it is).

---

## Notes

- No new module, no new dependency — additive to `ephemeris` + `core`.
- `KpLordage.chainFor` is reused unchanged for cusp lord chains.
- Every rule added gets a citation in `core/REFERENCES.md` (Constitution III).
- Commit per task or tight group; keep `master` releasable (work on `002-natal-chart-cusps`).
