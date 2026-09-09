# Quickstart: verifying SPEC-003

Builds on SPEC-002 (merged to `master`). Branch `003-significators-ruling-planets`.

## Prerequisites

- Everything from SPEC-001/002 (JDK 25, `./mvnw`, `.se1` data).
- Golden files extended with significators + one ruling-planet example:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
    python compute_golden.py ../../core/src/test/resources/golden/*.json --write
  ```

  (`--write` now also emits `expected.significators.by_house` /
  `.by_graha`, `expected.node_agency`, and — in one file —
  `expected.ruling_planets`.)

## Build & test

```bash
./mvnw -q -pl ephemeris,core -am verify
```

New coverage:

- `ephemeris` — `SunriseProvider`: sunrise before a given instant; polar
  `Optional.empty()`.
- `core.judgement` — `SignificatorTable`: four-step lists per house, per-graha
  transpose, node agency; `RulingPlanetsFactory`: sign/star/sub + day lord + node
  additions, the `includeSubLords` switch.
- extended golden suite — 12 per-house significator lists (membership + step
  tags), the per-graha table as the transpose, the node agencies, and the RP
  example.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 four-step significators, empty house, multi-step graha | `core.judgement.SignificatorTableTest`, golden `SignificatorGoldenTest` |
| US2 per-graha table is the transpose (SC-002) | `core.judgement.SignificatorTransposePropertyTest` |
| US3 Rahu/Ketu agency | `core.judgement.NodeAgencyTest`, golden |
| US4 ruling planets, sources, sub-lord flag | `core.judgement.RulingPlanetsTest`, golden `RulingPlanetsGoldenTest` |
| US4 day lord sunrise boundary (SC-004) | `core.judgement.KpWeekdayTest` |
| US5 determinism | `SignificatorTableTest`, `RulingPlanetsTest` (compute twice) |
| SC-005 performance (< 20 ms) | `core.judgement.SignificatorPerformanceTest` — `@Tag("perf")` |

## Determinism / architecture gates

Unchanged — `DeterminismArchitectureTest` already covers `com.celestia.ephemeris..`
and `com.celestia.core..` (no wall clock, no `de.thmac.swisseph` in the public API).

## Interpreting a failure

- **significator membership** mismatch with correct positions → the four-step
  logic or the effective-occupant (node agency) fold.
- **step tag** mismatch → a graha qualifying via a step the reference didn't
  record (or vice versa) — check the owner/occupant definitions.
- **day lord** off by one → the `sunriseBefore` search or the LMT-date weekday
  approximation; check the resolved sunrise instant in the failure output.
