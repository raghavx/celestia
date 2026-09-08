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
- Golden-chart files present under `core/src/test/resources/golden/` (the
  outstanding data-gathering prerequisite).

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

| Spec scenario | How to see it |
|---------------|---------------|
| US1 #1 positions match golden chart | `GoldenChartTest` — per-chart, per-graha longitude + lord chain |
| US1 #2 Ketu = Rahu + 180°, both retrograde | `PositionProviderTest.ketuOppositeRahu` |
| US2 #2 boundary belongs to higher division | `KpLordageBoundaryTest` — λ exactly on a sub boundary |
| US2 #3 normalisation of 0/360/−5/365 | `LongitudesTest` |
| US3 #1–#3 exact partition sums | `VimshottariPartitionTest` — `BigFraction` equality |
| US3 #4 zodiac tiling, no gap/overlap | `ZodiacTilingPropertyTest` (jqwik) |
| US4 #2 determinism | `DeterminismTest` — compute twice, assert `equals` |
| US4 #3 engine-version surfacing | `EngineVersionTest` |
| US5 out-of-range → REDUCED | `AccuracyRangeTest` |

## Determinism / architecture gates

```bash
./mvnw -q -pl agent test -Dtest=DeterminismArchitectureTest,LayeringArchitectureTest
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

`tools/ephe-crosscheck/` (not built by `verify`) computes the same charts with an
independent KP implementation and reports any longitude difference over 2
arc-seconds — this is how the SC-002 numeric reference is maintained.
