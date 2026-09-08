# Implementation Plan: Natal Chart & Placidus Cusps (SPEC-002)

**Branch**: `002-natal-chart-cusps` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-natal-chart-cusps/spec.md`

## Summary

Add house cusps to the KP engine, on top of SPEC-001:

- **`ephemeris`** — a `HouseProvider` interface with a Swiss Ephemeris
  implementation (`swe_houses`, `hsys='P'`, sidereal), returning the twelve raw
  cusp longitudes plus the Ascendant and Midheaven. New input: `BirthData`
  (instant + latitude + longitude). The `de.thmac.swisseph` types stay behind the
  interface, as `PositionProvider` does.
- **`core`** — assemble a `NatalChart`: attach the SPEC-001 lord chain to every
  cusp and angle (exposing the **cuspal sub lord**), place each graha in a
  **bhava** (cusp-to-cusp, half-open, wrap-aware) and a **rasi house** (whole
  signs from the Ascendant), and bundle it all with the ayanamsa and engine
  version into one immutable value.

High-latitude births (|lat| ≥ 66°, configurable) are rejected up front with a
named exception; the Ascendant/MC stay available. Correctness is the extended
golden-chart suite (cusp longitudes + cuspal sub lords + bhava + rasi), verified
against an independent pyswisseph `swe_houses` computation.

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: unchanged — Swiss Ephemeris Java port (already a
dependency, now also using `swe_houses`), `commons-numbers-fraction` (transitively,
via `core`), JUnit 5 / AssertJ / jqwik / jackson-databind (test).

**Storage**: N/A — `NatalChart` is an immutable value object.

**Testing**: `./mvnw verify`. Reuses the golden-chart harness from SPEC-001,
extended with cusp / bhava / rasi reference values produced by
`tools/ephe-crosscheck` (pyswisseph `swe_houses`).

**Target Platform**: JVM library. Bit-reproducible across Linux/macOS, x86-64/arm64.

**Project Type**: Library — same two modules (`ephemeris`, `core`), no new module.

**Performance Goals**: SC-006 — a full natal chart (positions + cusps + placements
+ lord chains) in < 75 ms on a developer machine (SPEC-001's positions+chains
already measure ~3 ms; houses add one `swe_houses` call).

**Constraints**:
- Constitution II — pure, deterministic; `HouseProvider` behind an interface;
  no SE type in any public signature (FR-014).
- SC-001 — cusp longitude within 1′ of the reference; cuspal sub lords exact.
- SC-004 — bhava assignment internally consistent (property test).
- SC-007 — |lat| ≥ 66° rejected; 65° ok; 91° is `IllegalArgumentException`.

**Scale/Scope**: 12 cusps + 2 angles, 9 grahas × (bhava + rasi house), one chart
per call.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist green, this plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — `ephemeris` + `core` only; the existing `DeterminismArchitectureTest` (no wall clock, no SE type leak) already covers the new packages |
| III Correctness is gated | **yes, central** | PASS — extends the golden-chart suite; new rules cited in `core/REFERENCES.md` |
| IV LLM interprets, never calculates | no | N/A |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — `BirthData` is an instant + coordinates, no personal identifier here |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — `HouseProvider` mirrors `PositionProvider`; the SE library type never appears in a public member (FR-014, enforced by `DeterminismArchitectureTest`) |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB |
| ADR gate | yes | PASS — ADR-0003 (ayanamsa), ADR-0004 (Placidus, sidereal) accepted; SPEC-001 merged to `master` |

**Result: PASS.** No new dependency, no new module, no framework. The change is
additive to the pure modules.

## Project Structure

### Documentation (this feature)

```text
specs/002-natal-chart-cusps/
├── plan.md              # this file
├── research.md          # Phase 0 — swe_houses behaviour, bhava rule, polar handling
├── data-model.md        # Phase 1 — value objects and invariants
├── contracts/
│   ├── house-provider-api.md   # ephemeris: HouseProvider
│   └── natal-chart-api.md      # core: NatalChart + assembly
├── quickstart.md        # Phase 1 — verify end to end
└── tasks.md             # Phase 2 — created by /speckit-tasks
```

### Source code (repository root)

```text
ephemeris/src/main/java/com/celestia/ephemeris/
├── BirthData.java                     # record: instant, latitude, longitude (+ validation)
├── Angle.java                         # enum: ASCENDANT, MIDHEAVEN
├── HouseSystem.java                   # enum: PLACIDUS (only)
├── HouseResult.java                   # record: double[12] cuspLongitudes, EnumMap<Angle,Double>, Accuracy, EngineVersion
├── HouseProvider.java                 # interface: houses(BirthData) -> HouseResult; anglesOnly(BirthData)
├── PlacidusUndefinedException.java    # extends EphemerisException
└── swisseph/
    ├── SwissEphemerisHouseProvider.java   # swe_houses, hsys='P', SEFLG_SIDEREAL; polar pre-check
    └── (SwissEphemerisConfig reused for the polar-limit default)

core/src/main/java/com/celestia/core/
├── chart/
│   ├── Cusp.java                      # record: int house(1..12), double longitude, LordChain lordChain
│   ├── HousePlacement.java            # record: Graha, int bhava(1..12), int rasiHouse(1..12)
│   ├── NatalChart.java                # immutable aggregate
│   ├── Bhavas.java                    # cusp-ring bhava assignment (half-open, wrap-aware)
│   └── NatalChartFactory.java         # from(BirthData, EphemerisResult, HouseResult); or wires the two providers
└── REFERENCES.md                     # + bhava rule, rasi-house rule, Placidus/polar

core/src/test/java/com/celestia/core/chart/    # unit + property + extended golden tests
core/src/test/resources/golden/*.json           # + expected.cusps[], expected.grahas[].bhava / .rasi_house
tools/ephe-crosscheck/compute_golden.py          # + swe_houses pass
```

**Structure Decision**: no new module. `BirthData` and the house types live in
`ephemeris` (the layer that talks to Swiss Ephemeris). `NatalChart` and the
bhava/rasi logic live in `core`. `NatalChartFactory` in `core` orchestrates —
`core` already depends on `ephemeris`, so it can call both providers, or accept
their already-computed results for maximum testability.

## Phase 0 — Research

See [research.md](./research.md). Items:

1. `swe_houses` in the port — exact signature, sidereal flag, cusp array layout,
   whether Ascendant == cusp 1, monotonic ordering / single wrap.
2. High-latitude behaviour — the backend errors rather than silently falling back;
   decision is to **pre-check latitude** and throw, not to interpret the backend.
3. Bhava assignment — the KP cusp-to-cusp definition, half-open, wrap handling;
   intercepted-sign edge case.
4. Rasi-house convention — whole-sign count from the Ascendant's sign; source.
5. Do cusps need the `.se1` data? (No — sidereal time + obliquity + analytic
   ayanamsa/nutation.) How that interacts with `Accuracy`.
6. Golden reference — extend `tools/ephe-crosscheck` with a `swe_houses` pass;
   regenerate the three golden files (the one prerequisite before implement).

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — `BirthData`, `HouseResult`, `Cusp`,
  `HousePlacement`, `NatalChart`, invariants.
- [contracts/house-provider-api.md](./contracts/house-provider-api.md) — the
  `HouseProvider` contract: inputs, outputs, polar rejection, determinism, no leak.
- [contracts/natal-chart-api.md](./contracts/natal-chart-api.md) — `NatalChart`
  assembly and accessors; the bhava and rasi-house rules.
- [quickstart.md](./quickstart.md) — regenerate golden data, `./mvnw verify`,
  scenario → test map.

### Post-design Constitution re-check

Unchanged. No framework, storage, or network introduced; the SE binding stays
behind `HouseProvider`; the determinism/encapsulation ArchUnit rules already cover
`com.celestia.ephemeris..` and `com.celestia.core..`. **PASS.**

## Complexity Tracking

No constitution violations — this section is intentionally empty.
