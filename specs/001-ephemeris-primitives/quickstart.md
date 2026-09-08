# Quickstart: verifying SPEC-001

How to prove the ephemeris & longitude primitives work end to end. Assumes the
feature is implemented on branch `001-ephemeris-primitives`.

## Prerequisites

- JDK 25, `./mvnw` (wrapper fetches Maven 3.9.11).
- Ephemeris data: `scripts/fetch-ephe.sh` downloads the `.se1` files for
  1800–2100 into `ephemeris/src/main/resources/ephe/` and verifies them against
  `scripts/ephe.sha256`. (Skip if running the golden suite in Moshier mode.)
  The engine reads this as a **filesystem directory**, resolved in this order:
  1. explicit config value / `CELESTIA_EPHE_PATH` env var;
  2. `ephemeris/src/main/resources/ephe/` when running from the source tree (dev, test);
  3. a directory mounted into the `agent` container in production;
  4. if none exists → Moshier mode (`Accuracy.REDUCED` results only).
  Files packaged inside a jar are **not** usable — the Swiss Ephemeris port needs
  a real directory path.
- Golden-chart files present under `core/src/test/resources/golden/` (3 charts,
  `expected` blocks generated).

## Build & test

```bash
./scripts/fetch-ephe.sh                 # one-time, ~4 MB
./mvnw -q -pl ephemeris,core -am verify  # unit + property + golden-chart suites
```

Expected: `BUILD SUCCESS`. The run includes:

- `ephemeris` — provider tests (nine positions, mean-node/Ketu identity,
  retrograde sign), ΔT tests, in-range vs out-of-range `Accuracy`.
- `core` — `KpLordage` tables, boundary-convention tests, jqwik circle sweep
  (SC-004), exact-partition tests (SC-003).
- golden-chart snapshot suite (SC-001, SC-002) — every published value for every
  golden chart.

## Scenario checks (map to spec acceptance scenarios)

| Spec scenario | Test |
|---------------|------|
| US1 #1 positions match golden chart | `core …golden.GoldenChartTest` — per-chart, per-graha longitude + lord chain |
| US1 #2 Ketu = Rahu + 180°, both retrograde | `ephemeris.PositionProviderContractTest.ketuIsExactlyOppositeRahuAndBothRetrograde` |
| US1 #3 Mercury retrograde ⇒ negative speed | `ephemeris.PositionProviderTest` |
| US2 #1 chain matches reference / #2 boundary → higher division | `core.lordage.KpLordageTableTest`, `KpLordageBoundaryAndPadaTest` |
| US2 #3 normalisation of 0/360/−5/365 | `core.lordage.LongitudesTest`, `CoreLordageContractTest` |
| US3 #1–#3 exact partition sums | `core.dasha.PartitionExactnessTest`, `VimshottariPartitionTest` (`BigFraction` equality) |
| US3 #4 zodiac tiling, no gap/overlap | `core.dasha.ZodiacTilingPropertyTest` (jqwik, 20k) |
| US4 #2 determinism | `core …golden.DeterminismTest` |
| US4 #3 engine-version surfacing | `core …golden.EngineVersionChangeTest` |
| US5 in-range FULL / out-of-range REDUCED | `ephemeris.HistoricalAndRangeTest`, `AccuracyRangeTest` |
| SC-006 performance (< 50 ms) | `core.PerformanceSmokeTest` — `@Tag("perf")`, run with `-Pperf` |

## Determinism / architecture gates

```bash
./mvnw -q -pl agent -am test -Dtest=DeterminismArchitectureTest,LayeringArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Asserts no `Instant.now()` / `System.currentTimeMillis()` / `Clock.systemUTC()` /
`LocalDate.now()` in `ephemeris` or `core`, and that neither module depends on
Spring or the edge adapters.

## Interpreting a golden-chart failure

The suite prints `chart / graha / field / expected / actual`. A **lord-chain**
mismatch with a correct longitude points at the partition or boundary logic. A
**longitude** mismatch points at the ayanamsa, ΔT, node, or data version — check
`EngineVersion.id()` in the failure output first; a changed id means the reference
snapshot needs review, not the code.

## Cross-check harness (outside CI)

`tools/ephe-crosscheck/` (not built by `verify`) computes the same charts with
pyswisseph and reports any longitude difference over 2 arc-seconds — this is how
the SC-002 numeric reference is maintained.

```bash
cd tools/ephe-crosscheck && pip install -r requirements.txt
CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
  python compute_golden.py ../../core/src/test/resources/golden/*.json
```

## Verified

`./mvnw verify` from the repo root: **BUILD SUCCESS**, 90 tests, 0 skipped
(with `.se1` data provisioned). `GoldenChartTest` = 27 checks, all green — the
engine reproduces every golden value within 3″ of the pyswisseph reference and
the full lord chain exactly.
