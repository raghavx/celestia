---
description: "Task list for SPEC-001 — Ephemeris & Longitude Primitives"
---

# Tasks: Ephemeris & Longitude Primitives (SPEC-001)

**Input**: Design documents in `specs/001-ephemeris-primitives/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED. Correctness is this feature's whole purpose — SC-001…SC-005
are test-defined and User Story 4 *is* the correctness suite. Write tests first
and see them fail before implementing.

**Organization**: by user story (spec.md priorities). MVP = Setup + Foundational
+ US1 + US2 (positions *and* lord chains — the minimum SPEC-002 can build on).

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: parallelizable (different file, no dependency on an incomplete task)
- **[Story]**: US1–US5 for user-story phases; none for Setup / Foundational / Polish

---

## Phase 1: Setup

**Purpose**: dependencies and data provisioning

- [x] T001 `commons-numbers-fraction` 1.2 + `jqwik` 1.9.2 pinned as version properties and managed in the parent `pom.xml`; fraction is a compile dep of `core`, jqwik a test dep of `core` and `ephemeris`. Resolved OK against the Spring Boot 4.1.1 JUnit 5.
- [x] T002 Swiss Ephemeris Java port added.
  - (a) **Spike DONE**: `krishnact/swisseph` via JitPack, pinned `com.github.krishnact:swisseph:master-6000e46cf8-1` (SE 2.01.00). Compiles + runs on JDK 25; agrees with pyswisseph to 0.83″. Recorded in `research.md` §1, `LICENSE-NOTICES.md`.
  - (b) `jitpack.io` repo added to `pom.xml`; dep on `ephemeris`; resolves.
  - (c) Vendoring fallback documented (T059) — not needed.
- [x] T003 [P] `scripts/fetch-ephe.sh` + `scripts/ephe.sha256` — download `sepl_18.se1` + `semo_18.se1` (cover 1800–2399) from the aloistr/swisseph mirror into `ephemeris/src/main/resources/ephe/` and verify SHA-256. Verified working; `.se1` are git-ignored
- [x] T004 [P] Moshier-mode fallback documented for tests so CI can run without the full data set: `ephemeris/src/test/resources/ephemeris-test.properties` + a short note in `ephemeris/src/test/resources/README.md`
- [x] T005 [P] `.github/workflows/ci.yml`: caches + provisions `.se1`; matrix job still to add in T050. Original: cache `ephemeris/src/main/resources/ephe/` between runs (matrix determinism job is added in T050)

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

**Purpose**: the shared kernel used by every story

- [x] T006 Create `Graha` enum in `ephemeris/src/main/java/com/celestia/ephemeris/Graha.java` — 9 constants in Vimshottari order, `years()`, `next()` (wraps), `vimshottariOrder()`; unit test asserts `Σ years == 120`
- [x] T007 Refactor `core/src/main/java/com/celestia/core/dasha/Vimshottari.java` to source its sequence and year weights from `ephemeris.Graha`; update `core/src/test/java/com/celestia/core/dasha/VimshottariTest.java`
- [x] T008 [P] Create `Ayanamsa` enum (`KP_NEW` only) in `ephemeris/src/main/java/com/celestia/ephemeris/Ayanamsa.java`
- [x] T009 [P] Create `Accuracy` enum (`FULL`, `REDUCED`) in `ephemeris/src/main/java/com/celestia/ephemeris/Accuracy.java`
- [x] T010 [P] Create `Sign` enum (12 signs, `lord()` returning `Graha`, 30° each) in `core/src/main/java/com/celestia/core/lordage/Sign.java`
- [x] T011 [P] Create `Nakshatra` enum (27, `lord()`, `startLongitude()` = ordinal × 13°20′) in `core/src/main/java/com/celestia/core/lordage/Nakshatra.java`
- [x] T012 Create `Longitudes` utility (`normalize(double)` → `[0,360)`, boundary-convention constant + Javadoc: half-open `[start,end)`, boundary to higher division) in `core/src/main/java/com/celestia/core/lordage/Longitudes.java`; `LongitudesTest` covers 0, 360, −5, 365, NaN, ±Infinity
- [x] T013 Create `Span` record (`Graha lord, BigFraction start, BigFraction end`, `startDeg()`, `endDeg()`, `contains(double)` half-open) in `core/src/main/java/com/celestia/core/dasha/Span.java`; `SpanTest`
- [x] T014 Implement `VimshottariPartition.subs(Nakshatra)` in `core/src/main/java/com/celestia/core/dasha/VimshottariPartition.java` — 9 exact `BigFraction` spans, order from `nakshatra.lord()`, widths `(years/120) × 13°20′`
- [x] T015 Implement `VimshottariPartition.subSubs(Nakshatra, Graha subLord)` in the same file — 9 exact spans within the sub, order from `subLord`
- [x] T016 [P] `VimshottariPartitionTest` — sub widths equal `(years/120) × 800′` and sum to `BigFraction(40,3)` exactly; Ketu-lorded nakshatra → first sub Ketu 0°46′40″, second Venus 2°13′20″; sub-subs sum exactly to the sub width
- [x] T017 Create `DeterminismArchitectureTest` in `agent/src/test/java/com/celestia/agent/architecture/DeterminismArchitectureTest.java` with two rules (`allowEmptyShould(true)` so they pass before the code they guard exists):
  - (a) fail if `com.celestia.ephemeris..` or `com.celestia.core..` reference `Instant.now`, `System.currentTimeMillis`, `System.nanoTime`, `Clock.systemUTC`, `Clock.systemDefaultZone`, `LocalDate.now`, `LocalDateTime.now`, `LocalTime.now`, `ZonedDateTime.now`, `OffsetDateTime.now`, or `new java.util.Date()` (Constitution II / FR-014).
  - (b) **encapsulation** (FR-018): fail if any `de.thmac.swisseph..` (or whichever package the chosen SE port uses) type appears in a **public** member — parameter, return, field, or thrown type — of any class in `com.celestia.ephemeris`. The SE library must stay behind `PositionProvider`. This rule only has teeth once T028 lands.

**Checkpoint**: `./mvnw -pl ephemeris,core,agent -am verify` green; shared kernel ready.

---

## Phase 3: User Story 1 — Sidereal graha positions for an instant (P1) 🎯 MVP

**Goal**: given a UTC instant, return sidereal longitude, latitude, speed,
retrograde and accuracy for all nine grahas (KP-New ayanamsa, mean node).

**Independent Test**: feed the golden charts' birth instants (once available) and,
before that, assert the structural contract — nine grahas, longitudes in `[0,360)`,
Ketu opposite Rahu, `retrograde == speed < 0`.

### Tests (write first, expect failure)

- [x] T018 [P] [US1] `PositionProviderContractTest` in `ephemeris/src/test/java/com/celestia/ephemeris/PositionProviderContractTest.java` — encodes `contracts/ephemeris-api.md`: nine grahas present, longitude range, `ayanamsa == KP_NEW`, Ketu identity, retrograde rule, null instant → `EphemerisException`
- [x] T019 [P] [US1] `TimeScalesTest` in `ephemeris/src/test/java/com/celestia/ephemeris/TimeScalesTest.java` — `Instant` → `JulianDay`, `jdTt ≥ jdUt` for modern dates, `deltaTSeconds` sign and magnitude sane

### Implementation

- [x] T020 [P] [US1] `JulianDay` record (`double jdUt, double jdTt, double deltaTSeconds`) in `ephemeris/src/main/java/com/celestia/ephemeris/JulianDay.java`
- [x] T021 [P] [US1] `EngineVersion` record (`rules, sePort, deltaTModel, ephemerisData`, `id()`) in `ephemeris/src/main/java/com/celestia/ephemeris/EngineVersion.java`
- [x] T022 [P] [US1] `GrahaPosition` record in `ephemeris/src/main/java/com/celestia/ephemeris/GrahaPosition.java` — compact-constructor invariants: `longitude ∈ [0,360)`, `retrograde == (speedPerDay < 0)`
- [x] T023 [US1] `EphemerisResult` record in `ephemeris/src/main/java/com/celestia/ephemeris/EphemerisResult.java` — invariant: `positions.keySet()` == all nine `Graha`
- [x] T024 [US1] `EphemerisException` (unchecked) in `ephemeris/src/main/java/com/celestia/ephemeris/EphemerisException.java`
- [x] T025 [US1] `TimeScales.of(Instant)` in `ephemeris/src/main/java/com/celestia/ephemeris/TimeScales.java` — UTC → JD(UT); ΔT via the SE port's `swe_deltat`; JD(TT)
- [x] T026 [US1] `PositionProvider` interface in `ephemeris/src/main/java/com/celestia/ephemeris/PositionProvider.java` (Javadoc = the contract)
- [x] T027 [US1] `SwissEphemerisConfig` in `ephemeris/src/main/java/com/celestia/ephemeris/swisseph/SwissEphemerisConfig.java` — the ephemeris-data path is a **filesystem directory** resolved from an explicit config value / env var (`swe_set_ephe_path` cannot read files packaged inside a jar): default to the repo path in dev and test, and to a mounted/extracted directory in the `agent` container; supported range (1800–2100); Moshier toggle for when the directory is absent. Document the resolution order in `quickstart.md`.
- [x] T028 [US1] `SwissEphemerisPositionProvider` in `ephemeris/src/main/java/com/celestia/ephemeris/swisseph/SwissEphemerisPositionProvider.java` — `swe_set_sid_mode(SE_SIDM_KRISHNAMURTI)`, `swe_calc_ut` with `SEFLG_SIDEREAL | SEFLG_SPEED`, `SE_MEAN_NODE` for Rahu, Ketu = Rahu + 180° (mod 360), retrograde from speed sign, thread-safe access to the non-thread-safe SE handle, `Accuracy` from the range check; no SE type in any public signature
- [x] T029 [US1] Out-of-range handling in the provider: dates outside 1800–2100 compute via `SEFLG_MOSEPH` and return a result with `Accuracy.REDUCED` on every position — never an exception (FR-016)
- [x] T030 [P] [US1] `AccuracyRangeTest` in `ephemeris/src/test/java/com/celestia/ephemeris/AccuracyRangeTest.java` — in-range → `FULL`; year 1600 → `REDUCED`, no exception
- [x] T031 [P] [US1] `PositionProviderTest` in `ephemeris/src/test/java/com/celestia/ephemeris/PositionProviderTest.java` — Ketu exactly opposite Rahu and both retrograde; a known Mercury-retrograde instant → retrograde flag set, `speedPerDay < 0`
- [x] T032 [US1] Populate `EngineVersion` (SE port version, ΔT tag, `.se1` manifest hash / `"moseph"`); `EngineVersionTest` asserts stability for a fixed build+data

**Checkpoint**: planetary positions are computable and structurally verified.

---

## Phase 4: User Story 2 — KP lord chain for any longitude (P1)

**Goal**: any longitude → `{sign, sign lord, nakshatra, pada, star lord, sub lord,
sub-sub lord}` with a single documented boundary rule.

**Independent Test**: a table of published longitude → lord-chain rows, plus
`chainFor(0.0)` → Aries / Ashwini / pada 1, plus a boundary-exactness check.

### Tests (write first, expect failure)

- [ ] T033 [P] [US2] `CoreLordageContractTest` in `core/src/test/java/com/celestia/core/lordage/CoreLordageContractTest.java` — encodes `contracts/core-lordage-api.md`: total function after normalisation, `NaN`/`Infinity` → `IllegalArgumentException`, boundary → higher division, `chainFor(0.0)` result
- [ ] T034 [P] [US2] `KpLordageTableTest` in `core/src/test/java/com/celestia/core/lordage/KpLordageTableTest.java` — a hand-built table of longitude → full lord chain rows taken from KP reference material

### Implementation

- [ ] T035 [P] [US2] `LordChain` record in `core/src/main/java/com/celestia/core/lordage/LordChain.java`
- [ ] T036 [US2] `KpLordage.chainFor(double)` in `core/src/main/java/com/celestia/core/lordage/KpLordage.java` — normalise; sign + sign lord; nakshatra + pada + star lord; sub lord and sub-sub lord by locating the containing `Span` from `VimshottariPartition` (half-open)
- [ ] T037 [P] [US2] `KpLordageBoundaryTest` in `core/src/test/java/com/celestia/core/lordage/KpLordageBoundaryTest.java` — longitudes exactly on sign / nakshatra / pada / sub / sub-sub boundaries resolve to the higher division
- [ ] T038 [P] [US2] `PadaTest` in `core/src/test/java/com/celestia/core/lordage/PadaTest.java` — each of the 27 nakshatra start longitudes → pada 1; each pada spans exactly 3°20′
- [ ] T039 [US2] `LordChainConsistencyPropertyTest` (jqwik) in `core/src/test/java/com/celestia/core/lordage/LordChainConsistencyPropertyTest.java` — for sampled λ, `chainFor(λ).subLord` equals the `lord` of the containing span in `subs(chainFor(λ).nakshatra)`; same for sub-sub

**Checkpoint**: any longitude yields a verified KP lord chain.

---

## Phase 5: User Story 3 — Vimshottari proportional partition (P2)

**Goal**: expose the partition as public data (subs of a nakshatra, sub-subs of a
sub) with exact-tiling guarantees.

**Independent Test**: partition sums are exact `BigFraction` equalities; the 243
sub spans tile `[0°,360°)` with no gap or overlap.

### Tests (write first, expect failure)

- [ ] T040 [P] [US3] `ZodiacTilingPropertyTest` (jqwik) in `core/src/test/java/com/celestia/core/dasha/ZodiacTilingPropertyTest.java` — for λ swept across `[0,360)`, exactly one sub and one sub-sub contain it; concatenating `subs(n)` for all 27 nakshatras yields 243 contiguous spans covering `[0,360)` exactly (SC-004)
- [ ] T041 [P] [US3] `PartitionExactnessTest` in `core/src/test/java/com/celestia/core/dasha/PartitionExactnessTest.java` — for all 27 nakshatras `Σ sub widths == BigFraction(40,3)`; for all 27×9 subs `Σ sub-sub widths == sub width` (SC-003)

### Implementation

- [ ] T042 [US3] Finalise the `VimshottariPartition` public API per `contracts/core-lordage-api.md` — `subs`, `subSubs`, exact-boundary accessors for the SPEC-005 horary table; Javadoc including the 243 → 249 sign-split note
- [ ] T043 [P] [US3] Extend `SpanTest` — `contains` half-open at both ends; `startDeg`/`endDeg` doubles; `start < end` always
- [ ] T044 [US3] `core/src/main/java/com/celestia/core/REFERENCES.md` — cite an authoritative source for **every** KP rule/table in `core` and `ephemeris` (Constitution III / FR-019): the sub & sub-sub partition rule, the Vimshottari year table, the 12 sign-lordship assignments, the 27 nakshatra-lord assignments, the nakshatra/pada spans, and the half-open boundary convention. ADR references are acceptable for the ayanamsa choice (ADR-0003) and the mean-node choice (ADR-0005).

**Checkpoint**: partition is exact, exposed, and property-proven.

---

## Phase 6: User Story 4 — Reproducible correctness contract (P2)

**Goal**: every published golden value is reproduced on every run and platform;
rule changes surface through `EngineVersion`.

**Independent Test**: the golden-chart snapshot suite passes in CI; a
compute-twice determinism test passes.

> ✅ **UNBLOCKED** — the golden-chart dataset is sourced (3 AA-rated charts,
> `expected` blocks generated with real Swiss Ephemeris data). Remaining polish:
> one human lord-chain check (T046b) and an optional 4th textbook chart.

### Tasks

- [x] T045 [US4] Golden-chart file format defined and documented in `core/src/test/resources/golden/README.md` (birth instant UTC, source citation + Rodden rating, per-graha longitude + full lord chain, `verification` block with status)
- [x] T046 [US4] Three golden-chart files added — `einstein-1879.json`, `jobs-1955.json`, `obama-1961.json` — all Rodden AA (birth certificate); `expected` generated by `tools/ephe-crosscheck` with pyswisseph 2.10.03 + real `.se1` data; regeneration is deterministic
- [ ] T046b [US4] Promote ≥ 1 chart to `verification.status: human_verified` — read its Sun & Moon lord chains off a published KP source (textbook worked example, or two agreeing mainstream KP sites on KP ayanamsa + mean node); optionally add a 4th chart from a KP textbook where the lords are printed
- [ ] T046c [US4] Confirm Steve Jobs' Rodden rating / exact minute on astro.com (currently cited from secondary sources)
- [ ] T047 [US4] `GoldenChartLoader` test utility in `core/src/test/java/com/celestia/core/golden/GoldenChartLoader.java`
- [ ] T048 [US4] `GoldenChartTest` in `core/src/test/java/com/celestia/core/golden/GoldenChartTest.java` — per chart, per graha: sidereal longitude within 1′ of published and 2″ of the reference (SC-002); full lord chain exact (SC-001); failure message prints `chart/graha/field/expected/actual` and `EngineVersion.id()`
- [ ] T049 [P] [US4] `DeterminismTest` in `core/src/test/java/com/celestia/core/golden/DeterminismTest.java` — compute one chart's primitives twice, assert `equals` (SC-005)
- [ ] T050 [US4] `.github/workflows/ci.yml` — determinism job on `{ubuntu-latest, macos-latest}` running the golden + determinism suites and comparing an output digest across the matrix
- [x] T051 [P] [US4] `tools/ephe-crosscheck/` — standalone harness (not built by `verify`): `compute_golden.py` computes the golden charts with pyswisseph (independent SE binding), fills/verifies `expected`, reports drift > 2″; `requirements.txt`, `README.md`. `scripts/fetch-ephe.sh` + `scripts/ephe.sha256` provision the `.se1` data
- [ ] T052 [US4] `EngineVersionChangeTest` — altering any id component changes `id()`; document the bump procedure in `core/src/main/java/com/celestia/core/REFERENCES.md`, including that a change to a **`core`** algorithm (lord chain, partition) also requires bumping `EngineVersion.rules` even though the type lives in `ephemeris` (the coupling is manual by design — `ephemeris` cannot depend on `core`)

**Checkpoint**: correctness is a CI gate.

---

## Phase 7: User Story 5 — Historical & out-of-range dates (P3)

**Goal**: correct results across 1800–2100; a flagged result outside it.

**Independent Test**: 1850/1950/2050 → `FULL`; 1600/2200 → `REDUCED`, no exception.

### Tasks

- [ ] T053 [P] [US5] `HistoricalDateTest` in `ephemeris/src/test/java/com/celestia/ephemeris/HistoricalDateTest.java` — instants in 1850, 1950, 2050 → `Accuracy.FULL`, longitudes within plausible bounds
- [ ] T054 [P] [US5] `OutOfRangeTest` in `ephemeris/src/test/java/com/celestia/ephemeris/OutOfRangeTest.java` — instants in 1600 and 2200 → result returned, `REDUCED` on every position, no exception
- [ ] T055 [US5] `DeltaTConsistencyTest` in `ephemeris/src/test/java/com/celestia/ephemeris/DeltaTConsistencyTest.java` — same instant → identical ΔT; `jdTt − jdUt` matches the tagged ΔT model

**Checkpoint**: robustness verified.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T056 [P] `PerformanceSmokeTest` in `core/src/test/java/com/celestia/core/PerformanceSmokeTest.java` — nine positions + nine lord chains for one instant, warm (SC-006). `@Tag("perf")`, **excluded from the default CI run** (opt-in via a Maven profile) to avoid machine-dependent flakiness; assert a generous ceiling (e.g. < 250 ms) as a regression guard rather than the exact 50 ms target
- [ ] T057 [P] Update `LICENSE-NOTICES.md` with the resolved Swiss Ephemeris port coordinates / pinned commit and the JitPack note
- [ ] T058 [P] `ephemeris/README.md` and `core/README.md` — module purpose, entry points (`PositionProvider`, `KpLordage`, `VimshottariPartition`), "how to add a golden chart"
- [ ] T059 [P] Document the Swiss Ephemeris source-vendoring fallback procedure in `docs/ephemeris-vendoring.md`
- [ ] T060 Run `specs/001-ephemeris-primitives/quickstart.md` end to end; fix drift; tick its checklist
- [ ] T061 `./mvnw -q verify` — full reactor green including `LayeringArchitectureTest` and `DeterminismArchitectureTest`

---

## Dependencies & Execution Order

### Phase order

1. **Setup (P1)** — no dependencies, except **T002(a)** (SE-port spike) gates
   T002(b) and the SE-dependent US1 tasks T025 and T028.
2. **Foundational (P2)** — depends on Setup. **Blocks every user story.**
3. **US1 (P3)** and **US2 (P4)** — both depend only on Foundational. US2 uses
   `VimshottariPartition` (built in Foundational), not US1. They are independent
   of each other and can run in parallel. Within US1, T025/T028 additionally
   depend on T002(a).
4. **US3 (P5)** — depends on Foundational; independent of US1/US2 (it hardens and
   exposes the partition those already use).
5. **US4 (P6)** — depends on US1 + US2 (it verifies their output). T046 also
   depends on the external golden-chart dataset.
6. **US5 (P7)** — depends on US1 (accuracy/range live in the provider).
7. **Polish (P8)** — after the user stories you intend to ship.

### Story dependency notes

- US1 ⟂ US2 ⟂ US3 (independent; parallelizable across people).
- US4 needs US1 + US2 green first.
- US5 needs US1 green first.

### Parallel opportunities

- Setup: T003, T004, T005 in parallel.
- Foundational: T008–T011 in parallel; T013 → then T014/T015; T016 after T015;
  T017 any time.
- US1: T018/T019 (tests) in parallel; T020/T021/T022 in parallel; then T023→T028
  serial (same area); T030/T031 in parallel after T028.
- US2: T033/T034 in parallel; T035 in parallel; T037/T038 in parallel after T036.
- US3: T040/T041 in parallel; T043 in parallel.
- US4: T049/T051 in parallel with T048.
- US5: T053/T054 in parallel.

---

## Parallel Example: Foundational enums

```bash
# T008–T011 touch four different new files, no interdependency:
Task: "Create Ayanamsa enum in ephemeris/.../Ayanamsa.java"
Task: "Create Accuracy enum in ephemeris/.../Accuracy.java"
Task: "Create Sign enum in core/.../lordage/Sign.java"
Task: "Create Nakshatra enum in core/.../lordage/Nakshatra.java"
```

---

## Implementation Strategy

### MVP (recommended stopping point for the first PR)

Setup → Foundational → **US1** → **US2**. This delivers positions *and* lord
chains — everything SPEC-002 (chart assembly) needs. Stop, run
`quickstart.md`, open the PR.

### Incremental delivery

1. Setup + Foundational → kernel ready.
2. + US1 → positions (demo: print the nine grahas for a date).
3. + US2 → lord chains (demo: longitude → full chain). **MVP.**
4. + US3 → partition exactness proven (SC-003, SC-004).
5. + US4 → golden-chart CI gate (needs the dataset). **Feature complete per SC-001/002/005.**
6. + US5 → historical/range robustness.
7. Polish.

### Blocked-work note

- T002(a) is a **spike** that must complete before T002(b) and anything in US1
  that touches the SE port (T025, T028). It resolves the last open unknown from
  research.md (which SE port fork, JitPack-on-JDK-25).
- Do not run `/speckit-implement` past T045 until the golden-chart dataset exists.
- Everything through US3 (T001–T044) is otherwise unblocked today.

---

## Notes

- `[P]` = different file, no incomplete dependency.
- Tests are first-class here — write them, watch them fail, then implement.
- Commit after each task or tight logical group; keep `main` releasable (work on
  `001-ephemeris-primitives`).
- Every KP rule added MUST get a citation in `core/.../REFERENCES.md` (Constitution III).
