# Quickstart: validating SPEC-010

## Prerequisites

- JDK 25, the Maven Wrapper, `scripts/fetch-ephe.sh` run once (for the golden charts).
- Python 3.11+ with `tools/toolkit-import/requirements.txt` installed.
- The licensed workbook somewhere **outside** the repo,
  e.g. `~/Documents/Prediction_ToolKit_for_Advanced_2026-Sep-12.xlsx`.

## 1. Import

```bash
python3 -I tools/toolkit-import/import_toolkit.py ~/Documents/Prediction_ToolKit_for_Advanced_2026-Sep-12.xlsx
```

Expected: `toolkit 2026-09-12: 75 sheets, ~2000 rules -> core/src/main/resources/com/celestia/core/toolkit`.

## 2. Determinism (SC-006)

Run step 1 again, then `git status core/src/main/resources/com/celestia/core/toolkit`.
Expected: nothing changed.

## 3. Correctness gates (SC-001 … SC-004, FR-001 … FR-014)

```bash
./mvnw -pl core -am test -Dtest='com.celestia.core.toolkit.*Test'
```

Expected: all pass. That covers 75/75 sheets, ≥ 2,000 rules, 249/249 horary rows equal
to the engine, 108/108 nakshatra-quarters, synthetic-chart evaluator invariants, golden
12-house snapshots, and the sensitivity sentences.

## 4. Engine version (FR-009)

```bash
./mvnw -pl agent -am test -Dtest=CurrentEngineVersionTest
```

Expected: the rules id ends with `+toolkit-2026-09-12`.

## 5. A real reading (SC-005 preparation)

Run `PromiseGoldenTest` and open its snapshot for one golden chart. For each house you
should see the CSL, its star lord, the connected houses and the matched rules with
their sources. Give 10 of these readings per golden chart to a KP practitioner. The
target is at least 9 of 10 judged correctly applied (SC-005).

## 6. Full build

```bash
./mvnw verify
```

Expected: green, including the ArchUnit determinism and layering tests and Spotless.
