# Quickstart: verifying SPEC-002

Builds on SPEC-001 (merged to `master`). Assumes branch `002-natal-chart-cusps`.

## Prerequisites

- Everything from SPEC-001's quickstart (JDK 25, `./mvnw`, `.se1` data via
  `scripts/fetch-ephe.sh`).
- Golden files extended with cusp / bhava / rasi reference values:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
    python compute_golden.py ../../core/src/test/resources/golden/*.json --write
  ```

  (the `--write` pass now also emits `expected.cusps`, `expected.angles`, and each
  graha's `bhava` / `rasi_house`; birth `latitude` / `longitude` are already in
  the files).

## Build & test

```bash
./mvnw -q -pl ephemeris,core -am verify
```

Expected `BUILD SUCCESS`. New coverage:

- `ephemeris` — `HouseProvider`: 12 cusps in `[0,360)`, cusp 1 == Ascendant
  (bit-identical), monotone ring; polar rejection at 66°; `anglesOnly` at any
  latitude; out-of-range instant → `REDUCED` not error.
- `core` — `Bhavas`: half-open, wrap-aware assignment; `NatalChartFactory`:
  aggregate invariants, `cuspSubLord(h)` consistency; jqwik bhava-consistency
  property.
- extended golden-chart suite — cusp longitudes (≤ 1′), **cuspal sub lords**
  (exact), each graha's bhava + rasi house.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 #1 cusps + sub lords match golden | `core …chart.NatalChartGoldenTest` |
| US1 #2 cusp 1 == Ascendant, ring ordered | `ephemeris.HouseProviderContractTest` |
| US2 bhava assignment + consistency | `core …chart.BhavasTest`, `BhavaConsistencyPropertyTest` |
| US3 rasi house from Ascendant sign | `core …chart.RasiHouseTest` |
| US4 NatalChart aggregate + determinism | `core …chart.NatalChartFactoryTest` |
| US5 polar rejection 66/65/91 | `ephemeris.PlacidusPolarTest` |
| US6 golden determinism | `core …chart.NatalChartDeterminismTest` |
| SC-006 performance (< 75 ms) | `core …chart.NatalChartPerformanceTest` — `@Tag("perf")` |

## Determinism / architecture gates

Unchanged from SPEC-001 — `DeterminismArchitectureTest` already covers the new
`com.celestia.ephemeris..` and `com.celestia.core..` packages (no wall clock, no
`de.thmac.swisseph` type in the public API).

## Interpreting a failure

- **cuspal sub lord** mismatch with a correct cusp longitude → the lord-chain /
  partition reuse, or the cusp-1-equals-Ascendant assignment.
- **cusp longitude** mismatch → the sidereal flag, `hsys`, ayanamsa, or SE port
  version — check `engineVersion.id()` first.
- **bhava** mismatch → the wrap handling in `Bhavas` or a graha sitting within an
  arc-second of a cusp (compare against the reference's own bhava).
