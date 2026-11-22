---

description: "Task list for SPEC-010: KP Toolkit Knowledge Base"
---

# Tasks: KP Toolkit Knowledge Base

**Input**: Design documents from `/specs/010-kp-toolkit-knowledge-base/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/core-api.md](contracts/core-api.md),
[contracts/agent-tools.md](contracts/agent-tools.md),
[contracts/import-tool.md](contracts/import-tool.md), [quickstart.md](quickstart.md)

**Tests**: included — `plan.md`'s Project Structure names the test classes explicitly
(`ToolkitCompletenessTest`, `ToolkitHoraryCrossCheckTest`, `ToolkitLookupTest`,
`PromiseEvaluatorTest`, `PromiseGoldenTest`, `SensitivityTest`) as Constitution III's
CI gate, so they are tasks here, not optional.

**Organization**: tasks are grouped by user story (spec.md priorities P1/P1/P2/P2/P2/P3)
so each story is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no unmet dependency)
- **[Story]**: US1 … US6, matching spec.md's user stories
- Every task names an exact file path

## A note on the workbook

The licensed workbook (`Prediction ToolKit for Advanced`, v2026-09-12) is never
committed to this repository (plan.md, Technical Context). Every task below either
builds the import tool and core library against documented formats and small test
fixtures, or is explicitly marked **MANUAL — owner only** for the one step that needs
the real file.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: scaffold the import tool and the resource layout before any sheet group
is parsed.

- [ ] T001 Create the import tool scaffold: `tools/toolkit-import/requirements.txt`
      (pin `openpyxl`), `tools/toolkit-import/import_toolkit.py` with its CLI contract
      (`<workbook.xlsx> [out_dir]`; exit 0 on success printing
      `toolkit <version>: <n> sheets, <m> rules -> <out_dir>`; exit 2 with usage when
      the workbook argument is missing) per [contracts/import-tool.md](contracts/import-tool.md)
- [ ] T002 [P] Create the resource directories: `core/src/main/resources/com/celestia/core/toolkit/{sheets,rules,tables}/`
      (generated, committed later) and `core/src/test/resources/com/celestia/core/toolkit/`
      (the fixture toolkit used by unit tests)
- [ ] T003 [P] Author the initial sheet-classification manifest (title → `TopicGroup`,
      `SheetKind`, sensitivity default, `answerable`) for every sheet named in
      spec.md's "What the toolkit contains" table, in
      `tools/toolkit-import/manifest.py` (research.md §5, §9) — refined when the
      real workbook's exact sheet names are first seen (T040's drift guard)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the `core.toolkit` package skeleton every user story depends on: the TSV
format, the shared enums and records, and a loader that can be exercised against a
small fixture without the real workbook.

**⚠️ CRITICAL**: no user story task may start until this phase is complete.

- [ ] T004 [P] `Tsv` reader/writer (escapes `\\`, `\t`, `\n`; `#` comment line + header
      line + records, per data-model.md) in `core/src/main/java/com/celestia/core/toolkit/Tsv.java`
- [ ] T005 [P] `TopicGroup` enum (`KP_FOUNDATIONS` … `HOUSEKEEPING`, data-model.md Sheet
      table) in `core/src/main/java/com/celestia/core/toolkit/TopicGroup.java`
- [ ] T006 [P] `SheetKind` enum (`RULES, TABLE, PROCEDURE, TEMPLATE, REMEDY, HOUSEKEEPING`)
      in `core/src/main/java/com/celestia/core/toolkit/SheetKind.java`
- [ ] T007 [P] `Sensitivity` enum (`NONE < REMEDY < FINANCE < HEALTH < LONGEVITY`,
      `note()` returning the fixed FR-010a sentence, `max(other)`) in
      `core/src/main/java/com/celestia/core/toolkit/Sensitivity.java`
- [ ] T008 [P] `ToolkitVersion` record (`title, version, sheets, rules, sourceSha256,
      id()`) in `core/src/main/java/com/celestia/core/toolkit/ToolkitVersion.java`
- [ ] T009 [P] `ToolkitSheet` record (`order, id, title, group, kind, sensitivity,
      answerable, rows`) in `core/src/main/java/com/celestia/core/toolkit/ToolkitSheet.java`
- [ ] T010 `KpToolkit` loader skeleton — `bundled()` (cached singleton),
      `version()`, `sheets()`, `sheet(String id)`, `sheets(TopicGroup)`; fail-fast
      validation that manifest row count equals `toolkit.properties`' sheet count
      (data-model.md "Validation at load") in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on
      T004–T009)
- [ ] T011 [P] Minimal fixture toolkit — `toolkit.properties`, `manifest.tsv`, and 3–4
      `sheets/<id>.tsv` spanning the RULES/TABLE/PROCEDURE kinds — in
      `core/src/test/resources/com/celestia/core/toolkit/`
- [ ] T012 `KpToolkitLoadTest`: the fixture loads correctly; a manifest/properties
      mismatch fails fast in
      `core/src/test/java/com/celestia/core/toolkit/KpToolkitLoadTest.java` (depends
      on T010, T011)

**Checkpoint**: `KpToolkit` loads a toolkit (fixture today, the real one after T046)
and can list/find sheets. User story work can now begin.

---

## Phase 3: User Story 1 - Chart-grounded promise reading (Priority: P1) 🎯 MVP

**Goal**: for any chart and any house, report which toolkit promise rules are
promised / condition-to-check / partly indicated, each naming its CSL, star lord and
source rule.

**Independent Test**: for a documented test chart, ask for the promise of each of the
12 houses; every rule reported promised has all its connecting houses actually
connected, each names its source rule (spec.md Independent Test, US1).

### Implementation for User Story 1

- [ ] T013 [P] [US1] Rule-cell grammar parser (the 7 cell forms of research.md §2:
      single, list, list+condition, list+prose, formula, formula+leading-condition,
      no-houses) in `tools/toolkit-import/import_toolkit.py`
- [ ] T014 [US1] Rule-sheet import: apply the grammar parser to 1500+Rules,
      ReadyReckoner, JobRules, Difficulty, Luck, Romance, Stock Market, Medical,
      Finance, Inheritance and Property, writing `rules/promise-rules.tsv` with id
      `<sheet-id>:<row-ref>`, `category`, `matter`, `matterKey` (research.md §8,
      lower-case + punctuation-stripped), `judgedHouse`, `houses`, `condition` in
      `tools/toolkit-import/import_toolkit.py` (depends on T013)
- [ ] T015 [US1] Rule-level sensitivity classifier: raise each rule above its sheet's
      default sensitivity by the LONGEVITY / HEALTH / FINANCE keyword lists
      (research.md §5) in `tools/toolkit-import/import_toolkit.py` (depends on T014)
- [ ] T016 [P] [US1] `PromiseRule` record + reader for `rules/promise-rules.tsv` in
      `core/src/main/java/com/celestia/core/toolkit/PromiseRule.java`
- [ ] T017 [US1] `KpToolkit.promiseRules()`, `rulesFor(int judgedHouse)` (throws
      `IllegalArgumentException` outside 1..12), `findRules(String query)` in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T010,
      T016)
- [ ] T018 [P] [US1] `HousePromise` and `PromiseHit` records (status
      `PROMISED` / `CONDITION_TO_CHECK` / `PARTLY_INDICATED`, data-model.md) in
      `core/src/main/java/com/celestia/core/toolkit/HousePromise.java` and
      `core/src/main/java/com/celestia/core/toolkit/PromiseHit.java`
- [ ] T019 [US1] `PromiseEvaluator`: connected houses = the CSL's star lord's
      `GrahaSignificators` houses at step `STAR_OF_OCCUPANT` ∪ `STAR_OF_OWNER`
      (research.md §3); classify promised / condition-to-check / partly-indicated
      (research.md §4); retrograde flags on the CSL and its star lord (FR-004);
      `evaluate(NatalChart)`, `evaluate(NatalChart, SignificatorTable, boolean
      includePartial)`, `evaluateHouse(...)` (contracts/core-api.md) in
      `core/src/main/java/com/celestia/core/toolkit/PromiseEvaluator.java` (depends
      on T017, T018)
- [ ] T020 [P] [US1] `SyntheticChart` test helper (a copy of `judgement`'s) in
      `core/src/test/java/com/celestia/core/toolkit/SyntheticChart.java`
- [ ] T021 [US1] `PromiseEvaluatorTest`: the research.md §3 prototype check (1st CSL
      Venus in Pushya, star lord Saturn occupying 7 and owning 10/11 ⇒ exactly
      `{7, 10, 11}`); promised / condition-to-check / partly-indicated classification
      (FR-005); retrograde flagging (FR-004); same chart + toolkit version ⇒ same
      rules twice (FR-009, acceptance scenario 3) in
      `core/src/test/java/com/celestia/core/toolkit/PromiseEvaluatorTest.java`
      (depends on T019, T020)
- [ ] T022 [P] [US1] `SensitivityTest`: the four fixed advisory sentences (FR-010a)
      and `max()` ordering; rule-level keyword escalation on the fixture toolkit in
      `core/src/test/java/com/celestia/core/toolkit/SensitivityTest.java`
- [ ] T023 [US1] `PromiseGoldenTest`: a 12-house promise-reading snapshot for each
      existing golden chart (SPEC-001/002) in
      `core/src/test/java/com/celestia/core/toolkit/PromiseGoldenTest.java` (depends
      on T019)
- [ ] T024 [US1] Compose the toolkit version into the engine version so a toolkit
      release invalidates cached readings: `rules = EngineVersion.RULES + "+" +
      KpToolkit.bundled().version().id()` (research.md §7, FR-009) in
      `agent/src/main/java/com/celestia/agent/persistence/CurrentEngineVersion.java`;
      extend
      `agent/src/test/java/com/celestia/agent/persistence/CurrentEngineVersionTest.java`
      to assert the `rules` tag ends with `+toolkit-<version>` (depends on T010)

**Checkpoint**: a 12-house promise reading is available for any chart, cited and
version-stamped. User Story 1 is independently functional.

---

## Phase 4: User Story 2 - Horary answer with the mindset (Priority: P1)

**Goal**: for a horary number 1–249, return the toolkit's sign/star/sub lord and its
mindset reading, agreeing with the engine's own table.

**Independent Test**: for all 249 numbers, the toolkit's sign, star and sub lord equal
`Horary249`'s, and every number has a mindset (spec.md Independent Test, US2).

### Implementation for User Story 2

- [ ] T025 [P] [US2] Horary importer: merge the 249SubDivisionsHoraryNumbers and
      249Mindsets sheets into `tables/horary-249.tsv`, applying the one documented
      fix-up (row "L07" → horary number 107, research.md §10) in
      `tools/toolkit-import/import_toolkit.py`
- [ ] T026 [P] [US2] `HoraryMindset` record + reader for `tables/horary-249.tsv` in
      `core/src/main/java/com/celestia/core/toolkit/HoraryMindset.java`
- [ ] T027 [US2] `KpToolkit.horary(int n)` (1..249; `IllegalArgumentException`
      outside range) in `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java`
      (depends on T010, T026)
- [ ] T028 [US2] `ToolkitHoraryCrossCheckTest`: 249/249 rows agree with
      `Horary249.arc(n)` on sign/star/sub lord (SC-003); the row-107 fix-up regression
      (acceptance scenario 1); rejection outside 1..249 (acceptance scenario 2) in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitHoraryCrossCheckTest.java`
      (depends on T027)

**Checkpoint**: User Stories 1 and 2 both work independently — **MVP complete**.

---

## Phase 5: User Story 3 - Star, planet and house look-ups (Priority: P2)

**Goal**: resolve profession, disease, education, planet-in-house, friendship, house
meaning and relationship look-ups by their precise keys (nakshatra + quarter,
planet + house, house).

**Independent Test**: all 108 nakshatra-quarters resolve to exactly one profession row
and one body-part row; every planet × house combination the sheets cover resolves to
its text (spec.md Independent Test, US3).

### Implementation for User Story 3

- [ ] T029 [P] [US3] Star-name normalisation alias table (Aswini/Ashvini,
      Karthikai/Krittika, …) and the quarter-format parser ("1Q", "2-3-4Q",
      "First/Second Half", "I-Quarter", …; research.md §6) in
      `tools/toolkit-import/import_toolkit.py`
- [ ] T030 [US3] Star-wise Professions / Star-wise Diseases importers →
      `tables/star-professions.tsv`, `tables/star-diseases.tsv`, keyed by nakshatra +
      quarter set, in `tools/toolkit-import/import_toolkit.py` (depends on T029)
- [ ] T031 [US3] Planet Position Signification, Higher Education Streams, Planet
      Friendship (all 72 ordered pairs), Houses Meaning and Relationship importers →
      `tables/planet-house-signification.tsv`, `tables/higher-education-streams.tsv`,
      `tables/planet-friendship.tsv`, `tables/house-meanings.tsv`,
      `tables/relationship-rotation.tsv` in `tools/toolkit-import/import_toolkit.py`
- [ ] T032 [P] [US3] `StarEntry` and `Friendship` records + readers in
      `core/src/main/java/com/celestia/core/toolkit/StarEntry.java` and
      `core/src/main/java/com/celestia/core/toolkit/Friendship.java`
- [ ] T033 [US3] `KpToolkit` look-up methods — `starProfession`, `starDisease`,
      `planetInHouse`, `higherEducation`, `friendship` (`IllegalArgumentException` if
      the two grahas are equal), `houseMeaning`, `relationship` — in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T010,
      T032)
- [ ] T034 [US3] `ToolkitLookupTest`: 108/108 nakshatra-quarters in both star tables
      (SC-004); all 72 friendship pairs present; "Krittika 2-3-4Q" professions for a
      10th CSL in Krittika quarter 3 (acceptance scenario 1); the education stream
      keyed by a 9th CSL's star lord and the house it occupies (acceptance scenario 2)
      in `core/src/test/java/com/celestia/core/toolkit/ToolkitLookupTest.java`
      (depends on T033)

**Checkpoint**: User Stories 1–3 all work independently.

---

## Phase 6: User Story 4 - Methods the agent follows (Priority: P2)

**Goal**: any procedure or template sheet's steps can be retrieved verbatim and in
order, and found by topic.

**Independent Test**: for each procedure sheet, its steps are retrievable verbatim and
in order (spec.md Independent Test, US4).

### Implementation for User Story 4

- [ ] T035 [US4] `KpToolkit.search(String query, int limit)`: answerable sheets only,
      ranked by title-term matches ×100 + matching rows, ties broken by workbook
      order; terms under 3 letters ignored; empty query → empty list
      (contracts/core-api.md) in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T010)
- [ ] T036 [US4] `ToolkitProcedureTest`: PROCEDURE/TEMPLATE sheets (Time of Event, BTR,
      Annual Prediction, Marriage Matching, Missing Person, Twin Birth, Ruling Planets
      Logic, Prediction Algorithm, Prediction Template) are retrievable via
      `sheet(id)` with rows in exact workbook order (FR-007); `search("time of
      event")` returns that sheet first (acceptance scenario 1) in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitProcedureTest.java`
      (depends on T035)

**Checkpoint**: User Stories 1–4 all work independently.

---

## Phase 7: User Story 5 - Every sheet retrievable, nothing lost (Priority: P2)

**Goal**: every one of the 75 sheets is stored complete and findable by topic, and
housekeeping / author-"ignore" sheets never surface as answers.

**Independent Test**: the stored toolkit has as many sheets as the workbook; each
sheet's rows match the workbook's non-empty rows; a search by a sheet's title returns
that sheet first (spec.md Independent Test, US5).

### Implementation for User Story 5

- [ ] T037 [US5] Apply the manifest (T003) when writing `manifest.tsv`: every sheet
      gets its `TopicGroup`/`SheetKind`/sensitivity default/`answerable` flag; FR-014
      sets `answerable=false` for INDEX, Version Control, Software List KP Astrology
      and PlanetsStockSectors (marked "Under research – Ignore" by its author) in
      `tools/toolkit-import/import_toolkit.py` (depends on T003)
- [ ] T038 [US5] Exclude non-answerable sheets from `KpToolkit.search()` results —
      still browsable by `sheet(id)` — in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T035,
      T037)
- [ ] T039 [US5] `ToolkitCompletenessTest`: manifest row count == `toolkit.properties`
      sheet count == `sheets().size()`; every `PromiseRule.source` resolves to a
      manifest sheet id; at least 2,000 promise rules present (SC-002); non-answerable
      sheets absent from `search()` results in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitCompletenessTest.java`
      (depends on T038, T017)

**Checkpoint**: User Stories 1–5 all work independently.

---

## Phase 8: User Story 6 - Toolkit versioning (Priority: P3)

**Goal**: a new toolkit release replaces the old one in one repeatable, auditable
step; re-importing an unchanged workbook changes nothing.

**Independent Test**: re-importing the same workbook produces no change; importing a
workbook with a new or renamed sheet stops and asks for that sheet to be classified
(spec.md Independent Test, US6).

### Implementation for User Story 6

- [ ] T040 [US6] Drift guard: compare the workbook's sheet names against the manifest
      at import time; exit non-zero with `manifest out of date: unmapped sheets […],
      absent sheets […]` on any mismatch (FR-013) in
      `tools/toolkit-import/import_toolkit.py` (depends on T037)
- [ ] T041 [US6] Determinism: the same workbook gives byte-identical output across two
      runs (SC-006); record the workbook SHA-256 in `toolkit.properties` in
      `tools/toolkit-import/import_toolkit.py` (depends on T040)
- [ ] T042 [P] [US6] `tools/toolkit-import/README.md`: the command, the exit codes,
      the resource formats, and the operational rule (the workbook is never
      committed; after a re-import that changes anything, review the diff rule by
      rule, then commit) per [contracts/import-tool.md](contracts/import-tool.md)

**Checkpoint**: all six user stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: documentation and final verification across all stories.

- [ ] T043 [P] Add rows to `core/src/main/java/com/celestia/core/REFERENCES.md` for:
      the connecting-houses definition (research.md §3), sensitivity ordering and the
      four fixed advisory sentences (research.md §5), the horary fix-up
      (research.md §10) and the engine-version composition (research.md §7); extend
      the "Engine version bump procedure" paragraph to name `core.toolkit` and
      `KpToolkit`
- [ ] T044 [P] Javadoc the API-stability note on `com.celestia.core.toolkit`: a
      toolkit release changes data, not the API; renaming a sheet id is a breaking
      change and needs an ADR (contracts/core-api.md "Stability") in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` and
      `core/src/main/java/com/celestia/core/toolkit/ToolkitSheet.java`
- [ ] T045 Run quickstart.md steps 3, 4 and 6 — `./mvnw -pl core -am test
      -Dtest='com.celestia.core.toolkit.*Test'`, `./mvnw -pl agent -am test
      -Dtest=CurrentEngineVersionTest`, `./mvnw verify` — and confirm all green,
      including the ArchUnit determinism/layering tests and Spotless
- [ ] T046 **MANUAL — owner only**: run `python3 tools/toolkit-import/import_toolkit.py
      <licensed-workbook.xlsx>` against the real *Prediction ToolKit for Advanced*
      workbook (quickstart.md step 1), review the generated diff rule by rule, and
      commit the real resources under
      `core/src/main/resources/com/celestia/core/toolkit/`. SC-001, SC-002, SC-003,
      SC-004 and SC-005 can only be certified once this step is done — the workbook
      is licensed content that stays outside the repository and outside any coding
      agent's reach

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — BLOCKS every user story
- **User Stories (Phases 3–8)**: all depend on Foundational
  - US1 and US2 (both P1) have no dependency on each other and can proceed in
    parallel, or sequentially in spec order
  - US3 and US4 depend only on Foundational (`KpToolkit.sheets()`/`sheet()`), not on
    US1/US2 — they can start as soon as Foundational is done
  - US5 (T037) depends on the Setup manifest (T003) but not on US1–US4's rule/look-up
    content
  - US6 (T040) depends on US5's manifest-application (T037)
- **Polish (Phase 9)**: depends on all desired user stories being complete; T046 is
  independent of the others and can happen at any time once the owner has the
  workbook

### Within Each User Story

- Import-tool tasks before the core-API tasks that read their output
- Records/readers before the `KpToolkit` methods that expose them
- `KpToolkit` methods before the tests that exercise them

### Parallel Opportunities

- All Setup tasks marked [P] (T002, T003) can run in parallel with T001
- All Foundational tasks marked [P] (T004–T009, T011) can run in parallel
- Once Foundational is done, US1 and US2 (both P1) can be staffed in parallel; so can
  US3 and US4 once US1/US2 are underway, since neither depends on promise rules or
  horary content
- Within a story, the Python (import-tool) and Java (core) tracks are independent
  files and can run in parallel (e.g. T013 ∥ T016; T025 ∥ T026)

---

## Parallel Example: User Story 1

```bash
# Python (import tool) and Java (core) tracks start together:
Task: "Rule-cell grammar parser in tools/toolkit-import/import_toolkit.py"       # T013
Task: "PromiseRule record + reader in core/.../toolkit/PromiseRule.java"         # T016
Task: "HousePromise + PromiseHit records in core/.../toolkit/{HousePromise,PromiseHit}.java"  # T018
Task: "SyntheticChart test helper in core/src/test/.../toolkit/SyntheticChart.java"  # T020
```

## Parallel Example: User Story 2

```bash
Task: "Horary importer (merge + L07 fix-up) in tools/toolkit-import/import_toolkit.py"  # T025
Task: "HoraryMindset record + reader in core/.../toolkit/HoraryMindset.java"            # T026
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 — both P1)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — CRITICAL, blocks everything.
2. Complete Phase 3 (US1: promise readings) and Phase 4 (US2: horary + mindset).
3. **STOP and VALIDATE**: run `PromiseEvaluatorTest`, `PromiseGoldenTest` and
   `ToolkitHoraryCrossCheckTest` against the fixture toolkit.
4. The real correctness gates (SC-001–SC-005) wait on T046 (owner runs the real
   import); everything else is verifiable today against the fixture.

### Incremental Delivery

1. Setup + Foundational → the loader works.
2. US1 + US2 → the MVP: chart-grounded promises and horary mindsets, both cited.
3. US3 → star/planet/house look-ups.
4. US4 → procedure/template retrieval for the agent to follow.
5. US5 → completeness guarantee (nothing silently dropped).
6. US6 → repeatable, auditable re-import.
7. Polish → documentation, full build, then the owner's one manual import step.

### Parallel Team Strategy

With multiple developers, after Foundational:
- Developer A: US1 (promise rules + evaluator) then US5 (completeness)
- Developer B: US2 (horary) then US6 (versioning)
- Developer C: US3 (look-ups) then US4 (search/procedures)

---

## Notes

- [P] tasks touch different files with no unmet dependency
- [Story] labels trace every task back to its spec.md user story
- Tests are included per Constitution III and plan.md's explicit test-class list —
  they are not optional for this feature
- Commit after each task or logical group; stop at any checkpoint to validate a story
  independently
- T046 is the one task genuinely blocked on the owner: everything else is buildable
  and testable today against fixtures and golden charts
