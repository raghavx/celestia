# Implementation Plan: KP Toolkit Knowledge Base

**Branch**: `010-kp-toolkit-knowledge-base` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-kp-toolkit-knowledge-base/spec.md`

## Summary

Make the licensed *Prediction ToolKit for Advanced* the product's single source of KP
judgement (Constitution X).

- **Import tool.** A dev-only Python tool reads the workbook, which is never committed,
  and writes deterministic, reviewable UTF-8 TSV resources into `core`. It produces
  every sheet verbatim, about 2,000 structured promise rules, and 8 typed look-up
  tables. It writes a manifest that classifies each sheet by topic group, kind,
  sensitivity class and answerability.
- **Knowledge base.** A new pure package, `com.celestia.core.toolkit`, loads the
  resources once into an immutable `KpToolkit`.
- **Matching.** A `PromiseEvaluator` matches the rule book to any `NatalChart` through
  the SPEC-003 significator table. For each house it takes the CSL and its star lord,
  finds the houses connected through that star lord, and labels each rule promised,
  condition to check, or partly indicated.
- **Versioning.** The toolkit version joins the engine version, so cached readings are
  recomputed when the toolkit changes.

## Technical Context

**Language/Version**: Java 25 (ADR-0007) for runtime code. Python 3.11+ for the
offline import tool, following the `tools/ephe-crosscheck` precedent.

**Primary Dependencies**: none new at runtime. The import tool uses `openpyxl`
(dev-only, pinned in `tools/toolkit-import/requirements.txt`).

**Storage**: classpath resources under `core/src/main/resources/com/celestia/core/toolkit/`
(TSV + properties, about 1.1 MB, committed as generated data). No database change; the
existing caches pick up the new engine version (research §7).

**Testing**: JUnit 5 + AssertJ + jqwik (existing in `core`). Cross-checks against
`Horary249`. Synthetic-chart evaluator tests. A golden 12-house reading per golden chart.

**Target Platform**: JVM server (the `agent` Spring Boot app); `core` stays framework-free.

**Project Type**: library code inside the existing `core` module plus a developer tool.

**Performance Goals**:
- Toolkit load ≤ 1 s cold, once per JVM.
- 12-house promise reading ≤ 50 ms per chart, well inside SC-007's "well under one
  second of the reply time".

**Constraints**:
- Constitution II: classpath reads only, immutable, no clock, no network.
- Constitution X: text kept word for word, with fix-ups documented.
- Constitution III: import checks are a CI gate.
- No JSON library added to `core`'s runtime (Jackson is test-scope there).

**Scale/Scope**: 75 sheets, about 2,000 rules, 8 typed tables, 5 sensitivity classes,
17 topic groups. One toolkit release at a time.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1 (v2.0.0).*

| Principle | How this plan complies | Status |
|-----------|------------------------|--------|
| I Spec-driven | spec → this plan → tasks; clarifications recorded in spec; ADR-0024 | ✅ |
| II Deterministic core | `core.toolkit` reads bundled resources only; immutable; no clock/network; same input → same output (FR-009) | ✅ |
| III Correctness gated | CI tests: 75/75 sheets, 249 cross-check vs `Horary249`, 108/108 nakshatra-quarters, re-import zero-diff, golden 12-house readings; toolkit version in `engine_version` (research §7) | ✅ |
| IV LLM never matches | `PromiseEvaluator` does all matching; the agent receives results, not raw rules to judge | ✅ |
| V Toolkit-scoped single path | Supplies the topic-group taxonomy and fixed advisory sentences the guardrail (SPEC-011) uses; no route to the model is added here | ✅ |
| VI Deterministic flows | Not touched | n/a |
| VII Data protection | Toolkit data holds no personal data; a second person's chart is evaluated in memory only (the evaluator takes a `NatalChart`, stores nothing) | ✅ |
| VIII Payments | Not touched | n/a |
| IX Adapters at the edge | No provider types; pure `core` | ✅ |
| X Toolkit is the judgement | Every rule and look-up carries its source sheet + row; text verbatim; one documented fix-up; housekeeping / "ignore" sheets flagged not answerable (FR-014); methods kept in row order (FR-007) | ✅ |

No violations, so no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/010-kp-toolkit-knowledge-base/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── core-api.md      # Java API of com.celestia.core.toolkit
│   ├── agent-tools.md   # the five toolkit tools SPEC-012 exposes to the LLM
│   └── import-tool.md   # CLI + resource-format contract of the import tool
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
tools/toolkit-import/
├── import_toolkit.py          # workbook → resources; manifest of all sheets; drift guard
├── requirements.txt           # openpyxl (pinned)
└── README.md

core/src/main/resources/com/celestia/core/toolkit/      # generated, committed
├── toolkit.properties         # title, version, counts, workbook SHA-256
├── manifest.tsv               # one row per sheet
├── sheets/<sheet-id>.tsv      # 75 verbatim sheets
├── rules/promise-rules.tsv
└── tables/{horary-249, star-professions, star-diseases, planet-house-signification,
            higher-education-streams, planet-friendship, house-meanings,
            relationship-rotation}.tsv

core/src/main/java/com/celestia/core/toolkit/
├── KpToolkit.java             # loader + queries (sheets, search, rules, look-ups)
├── PromiseEvaluator.java      # chart → 12 HousePromise
├── HousePromise.java  PromiseHit.java  PromiseRule.java
├── ToolkitSheet.java  ToolkitVersion.java  HoraryMindset.java  StarEntry.java  Friendship.java
├── Sensitivity.java           # NONE < REMEDY < FINANCE < HEALTH < LONGEVITY; fixed advisory sentence
├── TopicGroup.java  SheetKind.java
└── Tsv.java                   # package-private reader

core/src/test/java/com/celestia/core/toolkit/
├── ToolkitCompletenessTest.java     # SC-001, SC-002, FR-001/002/014
├── ToolkitHoraryCrossCheckTest.java # SC-003
├── ToolkitLookupTest.java           # SC-004, FR-006
├── PromiseEvaluatorTest.java        # FR-004/005/009, synthetic chart
├── PromiseGoldenTest.java           # 12-house reading for each golden chart (snapshot)
├── SensitivityTest.java             # FR-010a
└── SyntheticChart.java              # test helper (copy of judgement's)

agent/src/main/java/com/celestia/agent/persistence/CurrentEngineVersion.java  # + toolkit id (research §7)
core/src/main/java/com/celestia/core/REFERENCES.md   # toolkit rows + bump procedure
```

**Structure Decision**: the knowledge base lives in the existing pure `core` module as
package `com.celestia.core.toolkit`. It depends on `chart`, `judgement` and `lordage`.
`agent` changes only for the engine version. The import tool sits beside the existing
`tools/ephe-crosscheck`. Wiring the agent tools is SPEC-012; this spec delivers their
core API and contract.

## Phase 0 — Research

All unknowns are resolved in [research.md](research.md): resource format (§1), the
rule-cell grammar (§2), the "connected through the star lord" definition (§3), the
all-vs-partial rule (§4), sensitivity classification (§5), star-name normalisation
(§6), engine-version composition (§7), duplicate matters across sheets (§8),
non-answerable sheets (§9), source fix-ups (§10), and why Python for import (§11).

## Phase 1 — Design & Contracts

[data-model.md](data-model.md), [contracts/core-api.md](contracts/core-api.md),
[contracts/agent-tools.md](contracts/agent-tools.md),
[contracts/import-tool.md](contracts/import-tool.md), [quickstart.md](quickstart.md).

### Post-design Constitution re-check

Re-checked against v2.0.0. All gates still pass:

- **III:** `CurrentEngineVersion` composes `kp-N+toolkit-<version>`, so a toolkit
  release invalidates cached charts and readings.
- **X:** `PromiseHit` and every look-up return `source` (sheet id + row). The agent
  contract makes the citation mandatory in the answer.
- **V:** sensitivity sentences are constants in `core`, not prompts.

## Complexity Tracking

None.
