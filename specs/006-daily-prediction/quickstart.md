# Quickstart: verifying SPEC-006

Builds on SPEC-002, SPEC-003, SPEC-004 (merged to `master`). Branch
`006-daily-prediction`.

## Prerequisites

- Everything from SPEC-001…004 (JDK 25, `./mvnw`, `.se1` data for `FULL` accuracy).
- Golden files extended with one daily-prediction case:

  ```bash
  cd tools/ephe-crosscheck && pip install -r requirements.txt
  CELESTIA_EPHE_PATH=../../ephemeris/src/main/resources/ephe \
    python compute_golden.py ../../core/src/test/resources/golden/*.json --write
  ```

  (`--write` now also emits `expected.daily` for a fixed date + longitude — the
  reference instant, the activated house set with strengths, the Moon / Sun
  supported sets, and the per-matter verdicts.)

## Build & test

```bash
./mvnw -q -pl ephemeris,core -am verify
```

New coverage:

- `core.prediction` — `Matter` / `HouseGroups`: the closed taxonomy, disjoint
  favourable / obstructive sets, every key round-trips.
- `core.prediction` — `DailyPredictionFactory`: the dasha significators (running
  lords ∩ natal significations), the v1 sub-lord transit rule, the 4-row verdict
  rule; date-before-birth rejected.
- extended golden suite — one reading: activated set, transit support, verdicts.
- a property test — the verdict function is total and matches `research.md` §5.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 house-group taxonomy (SC-001) | `core.prediction.HouseGroupsTest` |
| US2 dasha significators == SPEC-003/004 (SC-002) | `core.prediction.DashaSignificatorsTest`, golden `DailyPredictionGoldenTest` |
| US3 transit sub-lord rule (SC-003) | `core.prediction.TransitContributionTest`, golden |
| US4 the four verdicts (SC-004) | `core.prediction.VerdictRuleTest`, `VerdictRulePropertyTest` (jqwik) |
| US4 traceability (FR-017) | `core.prediction.VerdictRuleTest` — every verdict rebuilt from its `MatterVerdict` fields |
| US5 determinism | `core.prediction.DailyPredictionDeterminismTest` |
| SC-005 performance (< 50 ms) | `core.prediction.DailyPredictionPerformanceTest` — `@Tag("perf")` |
| SC-006 one reading + taxonomy vs KSK | `verification.daily_human_check` in one golden file |

## Determinism / architecture gates

Unchanged — `DeterminismArchitectureTest` already covers `com.celestia.core..`
(no wall clock, no `de.thmac.swisseph` in the public API). `DailyPredictionFactory`
uses `LocalDate` / `Instant` arithmetic only — no `now()`.

## Interpreting a failure

- **verdict wrong** → the 4-row table (`research.md` §5) or the favActive /
  obsActive / favTriggered computation; the `MatterVerdict` fields show the
  inputs.
- **activated house / strength wrong** → the running lords (SPEC-004) or the
  `grahaSignificators` transpose (SPEC-003) — not a prediction bug.
- **transit support wrong** → the Moon / Sun sidereal longitude at the reference
  instant, or the reference-instant LMT offset.
- **house group mismatch vs KSK** → `Matter`'s data + citation; a taxonomy change
  is an `EngineVersion` bump.
