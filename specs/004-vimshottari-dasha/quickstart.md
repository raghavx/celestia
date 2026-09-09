# Quickstart: verifying SPEC-004

Builds on SPEC-001 (merged to `master`). Branch `004-vimshottari-dasha`.

## Prerequisites

- Everything from SPEC-001 (JDK 25, `./mvnw`, `.se1` data for `FULL` accuracy).
- Golden files extended with the birth balance + a running-stack example:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
    python compute_golden.py ../../core/src/test/resources/golden/*.json --write
  ```

  (`--write` now also emits `expected.dasha.balance` — birth Maha lord, elapsed,
  balance — and `expected.dasha.running` — the five period lords + boundaries at
  `birthInstant + 40 Julian years`.)

## Build & test

```bash
./mvnw -q -pl ephemeris,core -am verify
```

New coverage:

- `core.dasha` — `VimshottariSplit`: nine weight-proportional portions summing to
  the input exactly.
- `core.dasha` — `DashaTimeline`: `balanceAtBirth()`, `running(instant, depth)`
  (all five levels, half-open boundary), `periods(level, from, to)` (contiguous,
  parent chains, safety cap).
- extended golden suite — the birth balance (lord exact, days within tolerance)
  and the running five-lord stack at `birth + 40 years`.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 balance of dasha at birth | `core.dasha.DashaBalanceTest`, golden `DashaGoldenTest` |
| US2 running stack for a date, depth, 2nd cycle | `core.dasha.RunningDashaTest`, golden `RunningDashaGoldenTest` |
| US2 half-open boundary (SC-004) | `core.dasha.DashaBoundaryTest` |
| US3 windowed enumeration + parent chains | `core.dasha.DashaWindowTest`, golden `DashaWindowGoldenTest` |
| US3 / SC-003 children partition the parent exactly | `core.dasha.VimshottariSplitTest`, `DashaPartitionPropertyTest` (jqwik) |
| US4 determinism | `core.dasha.DashaDeterminismTest` |
| SC-005 performance (< 5 ms) | `core.dasha.DashaPerformanceTest` — `@Tag("perf")` |
| SC-006 birth balance vs a KP text | `verification.dasha_human_check` in one golden file |

## Determinism / architecture gates

Unchanged — `DeterminismArchitectureTest` already covers `com.celestia.core..`
(no wall clock, no `de.thmac.swisseph` in the public API). `DashaTimeline` is a
class without `equals`; the determinism test compares accessor outputs.

## Interpreting a failure

- **birth Maha lord** wrong → the Moon's nakshatra (ayanamsa / longitude), not the
  dasha logic.
- **balance off by more than the tolerance** → the traversed-fraction arithmetic
  or the 365.25-day year constant.
- **running lord wrong deep in the stack** → the nine-way `VimshottariSplit` order
  (must start from the *parent's* lord) or a boundary comparison.
- **partition property fails** → rounding crept in; boundaries must be derived
  from exact `BigFraction` cumulative seconds, rounded once.
