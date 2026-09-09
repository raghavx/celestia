# Implementation Plan: KP Horary 1–249 (SPEC-005)

**Branch**: `005-kp-horary` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-kp-horary/spec.md`

## Summary

Three parts, mostly reuse:

- **The 1–249 map** — pure `core`. `VimshottariPartition.subDivisions()` gives 243
  exact sub-spans; split each wherever it crosses one of the twelve 30° sign
  cusps, number the result 1–249. Each arc carries its exact `BigFraction`
  bounds, its sign, and the KP lord chain of its interior.
- **The horary Ascendant** — the midpoint longitude of arc *N* plus its lord
  chain, as an `AnglePoint`. Clock-independent.
- **The horary chart** — `NatalChartFactory.assemble(judgment, positionsForTheJudgmentInstant,
  horaryHouses)` where `horaryHouses` is a `HouseResult` whose **cusp 1 is the
  number's Ascendant** and whose other eleven cusps are Placidus, seeded from the
  right ascension of the MC that *produces* that Ascendant at the judgment
  latitude. That RAMC comes from a **direct closed-form inversion** of the
  Ascendant formula (verified by round-tripping `swe_houses`); the one new
  `ephemeris` capability is "houses from a given Ascendant longitude", not from an
  instant.

A horary chart is a `NatalChart`, so the SPEC-003 `SignificatorTable` /
`RulingPlanetsFactory` and the LLM's judgement need no changes.

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: unchanged. `ephemeris` uses `swe_houses`,
`swe_houses_armc`, `swe_calc_ut(SE_ECL_NUT)` and `swe_get_ayanamsa_ut` from the
already-present Swiss Ephemeris port. `BigFraction` for the 249 boundaries. Test:
JUnit 5 / AssertJ / jqwik / jackson-databind.

**Storage**: N/A — immutable value objects. (`chart` kind `HORARY` and
`horary_query` are SPEC-008.)

**Testing**: `./mvnw verify`. Reuses the golden-chart harness, extended with
horary cases (number + instant + place → Ascendant, twelve cusps, placements,
significators, ruling planets) produced by `tools/ephe-crosscheck` (pyswisseph
replica). Because the 249 table is *derived* from the same rule the engine uses,
**a sample of the table and one cast chart's cusps are cross-checked against KSK's
published table / a mainstream KP horary tool** (SC-006).

**Target Platform**: JVM library. Bit-reproducible across Linux/macOS, x86-64/arm64.

**Project Type**: Library — one new package `com.celestia.core.horary`, one new
`ephemeris` interface + impl; no new module.

**Performance Goals**: SC-005 — a cast horary chart in < 75 ms warm. The 249 table
is built once and cached. A cast is one position call + one obliquity call + one
ayanamsa lookup + one `swe_houses_armc` — comparable to a natal chart (SPEC-002).

**Constraints**:
- Constitution II — pure, deterministic; the RAMC inversion behind an interface;
  no Swiss Ephemeris type in any public signature (FR-016).
- SC-001 — the 249 arcs tile `[0°, 360°)` exactly, and there are exactly 249.
- SC-002 / SC-003 — a horary golden chart's cusps + placements + significators
  match the reference; the horary chart is a valid `NatalChart`.
- SC-006 — a sample of the 249 table + one cast chart human-verified.

**Scale/Scope**: 249 arcs (fixed), one number + one judgment moment per cast.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — new `core.horary` package + one `ephemeris` provider; existing `DeterminismArchitectureTest` (no wall clock, no SE leak) covers the new packages; the 249 boundaries are exact `BigFraction` |
| III Calculation Correctness Is Gated | **yes, central** | PASS — extends the golden suite (horary cases); the 249 rule and the RAMC inversion are cited in `core/REFERENCES.md`; SC-006 adds a human cross-check of the table + a cast chart |
| IV LLM interprets, never calculates | no | N/A — this feature *is* the deterministic horary chart the LLM reads |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — a number, an instant and coordinates; no personal identifier |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — the horary house provider mirrors `HouseProvider` / `SunriseProvider`; the SE type never crosses a public boundary (FR-016) |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB |
| ADR gate (D1–D23) | yes | PASS — no new ADR. The **Ascendant = arc midpoint** choice and the **RAMC-inversion cusp method** are computational / KP-rule decisions (not vendor or library choices, not in D1–D23); both are pinned in the spec and cited in `REFERENCES.md`, exactly as SPEC-004 handled the year-length. |

**Result: PASS.** No new dependency, no new module, no new ADR.

## Project Structure

### Documentation (this feature)

```text
specs/005-kp-horary/
├── plan.md
├── research.md          # the 249 split, the Ascendant-midpoint choice, the RAMC inversion, sidereal<->tropical
├── data-model.md         # HoraryArc, Horary249, HoraryChartFactory, HoraryHouseProvider, invariants
├── contracts/
│   ├── horary-houses-api.md   # ephemeris: HoraryHouseProvider
│   └── horary-api.md           # core: Horary249 / HoraryChartFactory
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
ephemeris/src/main/java/com/celestia/ephemeris/
├── HoraryHouseProvider.java                 # interface: housesFor(BirthData judgment, double ascendantLongitude) -> HouseResult
└── swisseph/
    └── SwissEphemerisHoraryHouseProvider.java   # obliquity + ayanamsa for the instant; sidereal Asc -> tropical; RAMC inversion; swe_houses_armc; tropical -> sidereal

core/src/main/java/com/celestia/core/horary/
├── HoraryArc.java                # record: int number, BigFraction start, BigFraction end, Sign sign, LordChain lordChain; startDeg()/endDeg()/midpointDeg()
├── Horary249.java                # static arcs() (the 249, cached) / arc(int) / count()
├── HoraryChartFactory.java       # cast(int number, BirthData judgment) -> NatalChart; ascendant(int number) -> AnglePoint; pure assemble(...)
└── HoraryRulingPlanets.java      # at(int number, BirthData judgment, SunriseProvider) -> RulingPlanets (SPEC-003 compute with the number's Ascendant)
core/.../REFERENCES.md             # + the 249 table derivation, the Ascendant-midpoint rule, the RAMC inversion
```

**Structure Decision**: no new module. The 249 map and the horary factory are a
new `com.celestia.core.horary` package. `HoraryHouseProvider` is a **separate**
`ephemeris` interface (like `SunriseProvider`) rather than a method on
`HouseProvider`, because "houses from an Ascendant" is a different concern from
"houses from an instant". The horary chart reuses `NatalChart` and
`NatalChartFactory.assemble` unchanged — the only new assembly step is building
the number-seeded `HouseResult`.

## Phase 0 — Research

See [research.md](./research.md). Items:

1. The 243 → 249 split — which sub-spans cross a sign cusp, the exact split, the
   count is 249, longitude-order numbering.
2. The horary Ascendant — why the arc midpoint; what the sub lord / sub-sub lord
   are; the sign of a split arc.
3. The RAMC from a given Ascendant — the closed-form inversion of the Ascendant
   formula; verification by round-trip against `swe_houses`; the bisection
   fallback.
4. Sidereal ↔ tropical — `swe_houses_armc` is tropical; convert the horary
   Ascendant to tropical with the instant's ayanamsa, compute, convert the cusps
   back; obliquity of date from `swe_calc_ut(SE_ECL_NUT)`.
5. Assembling the chart — reuse `NatalChartFactory.assemble`; the horary
   `HouseResult`'s `birthData` is the judgment moment even though its cusps are
   number-seeded (documented).
6. Horary ruling planets — `RulingPlanetsFactory.compute` with the number's
   Ascendant longitude; the Moon / day lord from the judgment moment.
7. Polar / out-of-range / short-arc edge handling.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — `HoraryArc`, `Horary249`,
  `HoraryChartFactory`, `HoraryRulingPlanets`, `HoraryHouseProvider`, invariants.
- [contracts/horary-houses-api.md](./contracts/horary-houses-api.md)
- [contracts/horary-api.md](./contracts/horary-api.md)
- [quickstart.md](./quickstart.md)

### Post-design Constitution re-check

Unchanged — no framework, storage, or network introduced; the SE type stays
behind `HoraryHouseProvider`; the determinism / layering ArchUnit rules already
cover `com.celestia.ephemeris..` and `com.celestia.core..`. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty.
