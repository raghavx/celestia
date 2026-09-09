# Quickstart: verifying SPEC-005

Builds on SPEC-002 and SPEC-003 (merged to `master`). Branch `005-kp-horary`.

## Prerequisites

- Everything from SPEC-001/002/003 (JDK 25, `./mvnw`, `.se1` data for `FULL`
  accuracy).
- Golden files extended with horary cases:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
    python compute_golden.py ../../core/src/test/resources/golden/*.json --write
  ```

  (`--write` now also emits `expected.horary` for a fixed number + judgment
  instant + place — the Ascendant, the twelve cusps with lord chains, the nine
  planetary placements, and the horary ruling planets. The 249 table is checked
  separately by a Java snapshot.)

## Build & test

```bash
./mvnw -q -pl ephemeris,core -am verify
```

New coverage:

- `core.horary` — `Horary249`: 249 arcs, exact tiling, one sign each, sub lord
  matching `VimshottariPartition`.
- `core.horary` — `HoraryChartFactory`: cusp 1 == the number's Ascendant; the
  cast chart is a valid `NatalChart` (significators + ruling planets run on it).
- `ephemeris` — `HoraryHouseProvider`: houses from a given Ascendant; round-trips
  `swe_houses` to ≤ 1′; polar → `PlacidusUndefinedException`.
- extended golden suite — one horary case: Ascendant, twelve cuspal sub lords,
  placements, per-house significators, ruling planets.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 the 249 map (SC-001) | `core.horary.Horary249Test`, `Horary249SnapshotTest` |
| US1 tiling is exact + count 249 | `core.horary.Horary249TilingPropertyTest` (jqwik) |
| US2 horary Ascendant = arc midpoint, sub lord | `core.horary.HoraryAscendantTest` |
| US3 cast chart, cusp 1 fixed, polar rejection | `core.horary.HoraryChartFactoryTest`, golden `HoraryGoldenTest` |
| US3 round-trip vs `swe_houses` | `ephemeris.HoraryHouseProviderContractTest` |
| US3 horary chart feeds SPEC-003 (SC-003) | `core.horary.HoraryChartFactoryTest` |
| US4 horary ruling planets | `core.horary.HoraryRulingPlanetsTest`, golden |
| US5 determinism | `core.horary.HoraryDeterminismTest` |
| SC-005 performance (< 75 ms) | `core.horary.HoraryPerformanceTest` — `@Tag("perf")` |
| SC-006 table + one cast chart vs a KP source | `verification.horary_human_check` in one golden file |

## Determinism / architecture gates

Unchanged — `DeterminismArchitectureTest` already covers `com.celestia.ephemeris..`
and `com.celestia.core..` (no wall clock, no `de.thmac.swisseph` in the public
API). `Horary249` is a class without `equals`; its snapshot test compares the arc
list (records with value equality).

## Interpreting a failure

- **249 count wrong** → the sign-crossing predicate (must be strict `s < c < e`)
  or a `subDivisions()` change upstream.
- **cusp 1 ≠ Ascendant** → the `HouseResult` cusp-1 override, or the
  `assemble(...)` assertion.
- **cusps 2–12 off by degrees** → the RAMC inversion sign convention, or the
  sidereal↔tropical ayanamsa direction; check the `swe_houses` round-trip test.
- **significators differ on a horary chart** → not a horary bug — the chart is a
  plain `NatalChart`; check SPEC-003.
