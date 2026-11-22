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
- [ ] T002 Generic sheet + properties writer: for every workbook sheet (all 75),
      normalise (NFC, CRLF→LF, trim trailing spaces per line) and write
      `sheets/<id>.tsv` verbatim — one file per sheet, every non-empty row,
      `#` comment + header + records (research.md §1, FR-001), where `<id>` is
      looked up by title in T004's manifest (never computed as an auto-slug — see
      T004). Compute and write `toolkit.properties`: title, version (the latest
      date in the Version Control sheet), sheet count, workbook SHA-256 (the rule
      count field is filled in later by T015). This is the foundational output
      every other import-tool and `core` task assumes already exists — found
      missing by `/speckit-analyze` on 2026-11-23 — in
      `tools/toolkit-import/import_toolkit.py` (depends on T001 and T004's id
      mapping; the two can be authored in either order within Setup since T004
      is static data, but T002's logic needs it wired in before it can run)
- [ ] T003 [P] Create the resource directories: `core/src/main/resources/com/celestia/core/toolkit/{sheets,rules,tables}/`
      (the production root `KpToolkit.bundled()` reads — generated, committed by
      T047) and, under `core/src/test/resources/com/celestia/core/toolkit/`, the
      sibling fixture roots `fixture/`, `promise-fixture/`, `horary-fixture/` and
      `star-fixture/` (each a distinct `KpToolkit.load(...)` root, so none shadows
      the production path or each other — see T011)
- [ ] T004 [P] Author the initial sheet-classification manifest (title → stable
      `id` slug, `TopicGroup`, `SheetKind`, sensitivity default, `answerable`)
      for every sheet named in spec.md's "What the toolkit contains" table, in
      `tools/toolkit-import/manifest.py` (research.md §5, §9). The `id` is a
      hand-assigned slug (e.g. "Medical Astrology" → `medical-astrology`,
      "1500+Rules" → `1500-rules`), not computed by an automatic title→slug
      algorithm — this sidesteps special-character/collision edge cases (digits,
      `+`, mixed scripts) entirely, and keeps ids stable across releases by
      construction, matching data-model.md's own requirement — refined when the
      real workbook's exact sheet names are first seen (T041's drift guard)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the `core.toolkit` package skeleton every user story depends on: the TSV
format, the shared enums and records, and a loader that can be exercised against a
small fixture without the real workbook.

**⚠️ CRITICAL**: no user story task may start until this phase is complete.

- [ ] T005 [P] `Tsv` reader/writer (escapes `\\`, `\t`, `\n`; `#` comment line + header
      line + records, per data-model.md) in `core/src/main/java/com/celestia/core/toolkit/Tsv.java`
- [ ] T006 [P] `TopicGroup` enum (`KP_FOUNDATIONS` … `HOUSEKEEPING`, data-model.md Sheet
      table) in `core/src/main/java/com/celestia/core/toolkit/TopicGroup.java`
- [ ] T007 [P] `SheetKind` enum (`RULES, TABLE, PROCEDURE, TEMPLATE, REMEDY, HOUSEKEEPING`)
      in `core/src/main/java/com/celestia/core/toolkit/SheetKind.java`
- [ ] T008 [P] `Sensitivity` enum (`NONE < REMEDY < FINANCE < HEALTH < LONGEVITY`,
      `note()` returning the fixed FR-010a sentence, `max(other)`) in
      `core/src/main/java/com/celestia/core/toolkit/Sensitivity.java`
- [ ] T009 [P] `ToolkitVersion` record (`title, version, sheets, rules, sourceSha256,
      id()`) in `core/src/main/java/com/celestia/core/toolkit/ToolkitVersion.java`
- [ ] T010 [P] `ToolkitSheet` record (`order, id, title, group, kind, sensitivity,
      answerable, rows`) in `core/src/main/java/com/celestia/core/toolkit/ToolkitSheet.java`
- [ ] T011 `KpToolkit` loader — `bundled()` (cached singleton, reads the fixed
      production classpath root `com/celestia/core/toolkit/`) delegates to a
      package-private `static KpToolkit load(String classpathRoot)`; `version()`,
      `sheets()`, `sheet(String id)`, `sheets(TopicGroup)`; fail-fast validation that
      manifest row count equals `toolkit.properties`' sheet count (data-model.md
      "Validation at load"). The package-private `load(root)` exists so same-package
      tests can point at a fixture root *other than* the production one — `src/test/
      resources` shadows `src/main/resources` at the same path on the test
      classpath, so any fixture placed at `com/celestia/core/toolkit/` would make
      `bundled()` never see the real data, even after T047. Every fixture-based test
      below therefore uses `load(testRoot)`, never `bundled()`; `bundled()` is
      reserved for the T047-gated real-data cases, which is exactly what makes those
      cases meaningful in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on
      T005–T010)
- [ ] T012 [P] Minimal fixture toolkit at the non-shadowing test root
      `com/celestia/core/toolkit/fixture/` (loaded via `KpToolkit.load(
      "com/celestia/core/toolkit/fixture")`, never `bundled()`) — `toolkit.properties`,
      `manifest.tsv`, and one `sheets/<id>.tsv` per: RULES, TABLE, PROCEDURE/TEMPLATE,
      a HOUSEKEEPING sheet (`answerable=false`, for T040's exclusion check), and one
      each in `HEALTH_MEDICAL`, `LONGEVITY`, `FINANCE_SPECULATION`,
      `VASTU_FENG_SHUI` and `NUMEROLOGY` (`answerable=true`, for T040's FR-010 group
      check) — in `core/src/test/resources/com/celestia/core/toolkit/fixture/`
- [ ] T013 `KpToolkitLoadTest`: `KpToolkit.load("com/celestia/core/toolkit/fixture")`
      loads correctly; a second, deliberately broken fixture root (manifest/
      properties mismatch) fails fast per "Validation at load" in
      `core/src/test/java/com/celestia/core/toolkit/KpToolkitLoadTest.java`
      (depends on T011, T012)

**Checkpoint**: `KpToolkit` loads a toolkit (fixture today, the real one after T047)
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

- [ ] T014 [P] [US1] Rule-cell grammar parser (the 7 cell forms of research.md §2:
      single, list, list+condition, list+prose, formula, formula+leading-condition,
      no-houses) in `tools/toolkit-import/import_toolkit.py`
- [ ] T015 [US1] Rule-sheet import: apply the grammar parser to 1500+Rules,
      ReadyReckoner, JobRules, Difficulty, Luck, Romance, Stock Market, Medical,
      Finance, Inheritance and Property, writing `rules/promise-rules.tsv` with id
      `<sheet-id>:<row-ref>`, `category`, `matter`, `matterKey` (research.md §8,
      lower-case + punctuation-stripped), `judgedHouse`, `houses`, `condition` in
      `tools/toolkit-import/import_toolkit.py` (depends on T014)
- [ ] T016 [US1] Rule-level sensitivity classifier: raise each rule above its sheet's
      default sensitivity by the LONGEVITY / HEALTH / FINANCE keyword lists
      (research.md §5) in `tools/toolkit-import/import_toolkit.py` (depends on T015)
- [ ] T017 [P] [US1] `PromiseRule` record + reader for `rules/promise-rules.tsv` in
      `core/src/main/java/com/celestia/core/toolkit/PromiseRule.java`
- [ ] T018 [US1] `KpToolkit.promiseRules()`, `rulesFor(int judgedHouse)` (throws
      `IllegalArgumentException` outside 1..12), `findRules(String query)` in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T011,
      T017)
- [ ] T019 [P] [US1] `HousePromise` and `PromiseHit` records (status
      `PROMISED` / `CONDITION_TO_CHECK` / `PARTLY_INDICATED`, data-model.md) in
      `core/src/main/java/com/celestia/core/toolkit/HousePromise.java` and
      `core/src/main/java/com/celestia/core/toolkit/PromiseHit.java`
- [ ] T020 [US1] `PromiseEvaluator`: connected houses = the CSL's star lord's
      `GrahaSignificators` houses at step `STAR_OF_OCCUPANT` ∪ `STAR_OF_OWNER`
      (research.md §3); classify promised / condition-to-check / partly-indicated
      (research.md §4); retrograde flags on the CSL and its star lord (FR-004);
      `evaluate(NatalChart)`, `evaluate(NatalChart, SignificatorTable, boolean
      includePartial)`, `evaluateHouse(...)` (contracts/core-api.md) in
      `core/src/main/java/com/celestia/core/toolkit/PromiseEvaluator.java` (depends
      on T018, T019)
- [ ] T021 [P] [US1] `SyntheticChart` test helper (a copy of `judgement`'s) in
      `core/src/test/java/com/celestia/core/toolkit/SyntheticChart.java`
- [ ] T022 [US1] `PromiseEvaluatorTest`, against a small hand-built promise-rule
      fixture loaded via its own `KpToolkit.load("com/celestia/core/toolkit/
      promise-fixture")` root (distinct from T012's, so neither shadows the other
      or production) — include a trivial `toolkit.properties` (`sheets=0`) and an
      empty `manifest.tsv` alongside `rules/promise-rules.tsv`, since `load()`
      checks those two files against each other on every call, not just the real
      one: the research.md §3 prototype check (1st CSL Venus in Pushya,
      star lord Saturn occupying 7 and owning 10/11 ⇒ exactly `{7, 10, 11}`);
      promised / condition-to-check / partly-indicated classification (FR-005);
      retrograde flagging (FR-004); same chart + toolkit version ⇒ same rules twice
      (FR-009, acceptance scenario 3). Add a T047-gated case: 1,000 calls to
      `evaluate(chart)` on a golden chart, against `KpToolkit.bundled()`'s real
      ~2,000 rules, average under 50 ms each (plan.md Performance Goals, SC-007) —
      tolerant of CI noise (assert the mean, not a single call); meaningless against
      the small fixture, so it must run against real data in
      `core/src/test/java/com/celestia/core/toolkit/PromiseEvaluatorTest.java`
      (depends on T020, T021; the timing case additionally depends on T047)
- [ ] T023 [P] [US1] `SensitivityTest`: the four fixed advisory sentences (FR-010a)
      and `max()` ordering; rule-level keyword escalation on the fixture toolkit in
      `core/src/test/java/com/celestia/core/toolkit/SensitivityTest.java`
- [ ] T024 [US1] `PromiseGoldenTest`: a 12-house promise-reading snapshot for each
      existing golden chart (SPEC-001/002) in
      `core/src/test/java/com/celestia/core/toolkit/PromiseGoldenTest.java` (depends
      on T020)
- [ ] T025 [US1] Compose the toolkit version into the engine version so a toolkit
      release invalidates cached readings: `rules = EngineVersion.RULES + "+" +
      KpToolkit.bundled().version().id()` (research.md §7, FR-009) in
      `agent/src/main/java/com/celestia/agent/persistence/CurrentEngineVersion.java`;
      extend
      `agent/src/test/java/com/celestia/agent/persistence/CurrentEngineVersionTest.java`
      to assert the `rules` tag ends with `+toolkit-<version>` (depends on T011)

**Checkpoint**: a 12-house promise reading is available for any chart, cited and
version-stamped. User Story 1 is independently functional.

---

## Phase 4: User Story 2 - Horary answer with the mindset (Priority: P1)

**Goal**: for a horary number 1–249, return the toolkit's sign/star/sub lord and its
mindset reading, agreeing with the engine's own table.

**Independent Test**: for all 249 numbers, the toolkit's sign, star and sub lord equal
`Horary249`'s, and every number has a mindset (spec.md Independent Test, US2).

### Implementation for User Story 2

- [ ] T026 [P] [US2] Horary importer: merge the 249SubDivisionsHoraryNumbers and
      249Mindsets sheets into `tables/horary-249.tsv`, applying the one documented
      fix-up (row "L07" → horary number 107, research.md §10) in
      `tools/toolkit-import/import_toolkit.py`
- [ ] T027 [P] [US2] `HoraryMindset` record + reader for `tables/horary-249.tsv` in
      `core/src/main/java/com/celestia/core/toolkit/HoraryMindset.java`
- [ ] T028 [US2] `KpToolkit.horary(int n)` (1..249; `IllegalArgumentException`
      outside range) in `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java`
      (depends on T011, T027)
- [ ] T029 [US2] A Java test-fixture generator (`HoraryFixtureSupport`, in
      `core/src/test/java/com/celestia/core/toolkit/HoraryFixtureSupport.java`) that
      calls `Horary249.arc(n)` for n in 1..249 to build a 249-row fixture under
      `com/celestia/core/toolkit/horary-fixture/tables/horary-249.tsv` with
      placeholder mindset text — no Python involved, since the sign/star/sub-lord
      columns are the engine's own calculation, not licensed content. Also write a
      trivial `toolkit.properties` (`sheets=0`) and empty `manifest.tsv` alongside
      it, since `load()` checks those two files against each other on every call,
      not just the real one. `ToolkitHoraryCrossCheckTest` loads it via `KpToolkit.load(
      "com/celestia/core/toolkit/horary-fixture")` and asserts 249/249 rows agree
      with `Horary249.arc(n)` (SC-003 structural check) and rejection outside 1..249
      (acceptance scenario 2). Add a second, T047-gated case asserting the row-107
      fix-up (acceptance scenario 1) against `KpToolkit.bundled()`'s real table in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitHoraryCrossCheckTest.java`
      (depends on T028; the fix-up case additionally depends on T047)

**Checkpoint**: User Stories 1 and 2 both work independently — **MVP complete**.

---

## Phase 5: User Story 3 - Star, planet and house look-ups (Priority: P2)

**Goal**: resolve profession, disease, education, planet-in-house, friendship, house
meaning, relationship, planet-characteristics, planet-direction and zodiac-sign
look-ups by their precise keys (nakshatra + quarter, planet + house, house, planet
alone, zodiac sign alone).

**Independent Test**: all 108 nakshatra-quarters resolve to exactly one profession row
and one body-part row; every planet × house combination the sheets cover resolves to
its text; all 9 planets resolve to exactly one characteristics row and one direction
row; all 12 zodiac signs resolve to exactly one characteristics row (spec.md
Independent Test, US3; extended 2026-11-23).

### Implementation for User Story 3

- [ ] T030 [P] [US3] Star-name normalisation alias table (Aswini/Ashvini,
      Karthikai/Krittika, …) and the quarter-format parser ("1Q", "2-3-4Q",
      "First/Second Half", "I-Quarter", …; research.md §6), plus a planet-name
      alias table (9 planets; English/Sanskrit — Shukra/Venus, Guru/Jupiter, …)
      and a sign-name alias table (12 signs; English/Sanskrit — Mesha/Aries,
      Vrishabha/Taurus, …) for the Planet Characteristics, Planet Direction and
      Zodiac Signs sheets (research.md §12, 2026-11-23 clarification) in
      `tools/toolkit-import/import_toolkit.py`
- [ ] T031 [US3] Star-wise Professions / Star-wise Diseases importers →
      `tables/star-professions.tsv`, `tables/star-diseases.tsv`, keyed by nakshatra +
      quarter set, in `tools/toolkit-import/import_toolkit.py` (depends on T030)
- [ ] T032 [US3] Planet Position Signification, Higher Education Streams, Planet
      Friendship (all 72 ordered pairs), Houses Meaning and Relationship importers →
      `tables/planet-house-signification.tsv`, `tables/higher-education-streams.tsv`,
      `tables/planet-friendship.tsv`, `tables/house-meanings.tsv`,
      `tables/relationship-rotation.tsv`, plus Planet Characteristics, Planet
      Direction and Zodiac Signs and Characteristics importers →
      `tables/planet-characteristics.tsv`, `tables/planet-direction.tsv`,
      `tables/zodiac-characteristics.tsv`, each keyed by planet or sign alone
      (2026-11-23 clarification) in `tools/toolkit-import/import_toolkit.py`
      (the three new tables depend on T030's planet/sign alias tables)
- [ ] T033 [P] [US3] `StarEntry` and `Friendship` records + readers in
      `core/src/main/java/com/celestia/core/toolkit/StarEntry.java` and
      `core/src/main/java/com/celestia/core/toolkit/Friendship.java`
- [ ] T034 [US3] `KpToolkit` look-up methods — `starProfession`, `starDisease`,
      `planetInHouse`, `higherEducation`, `friendship` (`IllegalArgumentException` if
      the two grahas are equal), `houseMeaning`, `relationship`, plus
      `planetCharacteristics(Graha)`, `planetDirection(Graha)` and
      `zodiacCharacteristics(Sign)` (bare `String` returns, same pattern as
      `houseMeaning`/`relationship` — no new record type needed, 2026-11-23
      clarification) — in `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java`
      (depends on T011, T033)
- [ ] T035 [US3] A Java test-fixture generator
      (`core/src/test/java/com/celestia/core/toolkit/StarLookupFixtureSupport.java`)
      producing a synthetic fixture under
      `com/celestia/core/toolkit/star-fixture/tables/` covering all 27×4
      nakshatra-quarters, all 72 friendship pairs, all 9 planets (characteristics
      and direction) and all 12 zodiac signs (characteristics) with placeholder
      text — built in Java by iterating `Nakshatra.values()` × 1..4,
      `Graha.values()` × `Graha.values()`, `Graha.values()` alone, and `Sign.values()`
      alone, not hand-authored (2026-11-23 clarification extends this fixture's
      scope). Also write a trivial `toolkit.properties` (`sheets=0`) and empty
      `manifest.tsv` alongside it, since `load()` checks those two files against
      each other on every call, not just the real one. `ToolkitLookupTest` loads
      it via `KpToolkit.load("com/celestia/core/toolkit/star-fixture")` and
      asserts 108/108 resolve in both star tables, 72/72 friendship pairs present,
      9/9 planets resolve in both the characteristics and direction look-ups, and
      12/12 signs resolve in the zodiac-characteristics look-up (SC-004 structural
      check, all four). Add a T047-gated case asserting the real "Krittika 2-3-4Q"
      professions (acceptance scenario 1), the real education stream for a 9th
      CSL's star lord by house (acceptance scenario 2), and non-empty real text
      for one sampled planet and one sampled sign, against `KpToolkit.bundled()`
      in `core/src/test/java/com/celestia/core/toolkit/ToolkitLookupTest.java`
      (depends on T034; the real-content cases additionally depend on T047)

**Checkpoint**: User Stories 1–3 all work independently.

---

## Phase 6: User Story 4 - Methods the agent follows (Priority: P2)

**Goal**: any procedure or template sheet's steps can be retrieved verbatim and in
order, and found by topic.

**Independent Test**: for each procedure sheet, its steps are retrievable verbatim and
in order (spec.md Independent Test, US4).

### Implementation for User Story 4

- [ ] T036 [US4] `KpToolkit.search(String query, int limit)`: answerable sheets only,
      ranked by title-term matches ×100 + matching rows, ties broken by workbook
      order; terms under 3 letters ignored; empty query → empty list
      (contracts/core-api.md) in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T011)
- [ ] T037 [US4] `ToolkitProcedureTest`: PROCEDURE/TEMPLATE sheets (Time of Event, BTR,
      Annual Prediction, Marriage Matching, Missing Person, Twin Birth, Ruling Planets
      Logic, Prediction Algorithm, Prediction Template) are retrievable via
      `sheet(id)` with rows in exact workbook order (FR-007); `search("time of
      event")` returns that sheet first (acceptance scenario 1) in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitProcedureTest.java`
      (depends on T002, T036; the real procedure-sheet names additionally depend
      on T047 — this test reads `sheet(id)` for the workbook's own sheet ids, not
      a fixture)

**Checkpoint**: User Stories 1–4 all work independently.

---

## Phase 7: User Story 5 - Every sheet retrievable, nothing lost (Priority: P2)

**Goal**: every one of the 75 sheets is stored complete and findable by topic, and
housekeeping / author-"ignore" sheets never surface as answers.

**Independent Test**: the stored toolkit has as many sheets as the workbook; each
sheet's rows match the workbook's non-empty rows; a search by a sheet's title returns
that sheet first (spec.md Independent Test, US5).

### Implementation for User Story 5

- [ ] T038 [US5] Apply the manifest (T004) when writing `manifest.tsv`: every sheet
      gets its `TopicGroup`/`SheetKind`/sensitivity default/`answerable` flag; FR-014
      sets `answerable=false` for INDEX, Version Control, Software List KP Astrology
      and PlanetsStockSectors (marked "Under research – Ignore" by its author) in
      `tools/toolkit-import/import_toolkit.py` (depends on T002, T004)
- [ ] T039 [US5] Exclude non-answerable sheets from `KpToolkit.search()` results —
      still browsable by `sheet(id)` — in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` (depends on T036,
      T038)
- [ ] T040 [US5] `ToolkitCompletenessTest`, against T012's fixture loaded via
      `KpToolkit.load("com/celestia/core/toolkit/fixture")`: manifest row count ==
      `toolkit.properties` sheet count == `sheets().size()`; every `PromiseRule.
      source` resolves to a manifest sheet id; non-answerable sheets absent from
      `search()` results; every sheet in `TopicGroup.HEALTH_MEDICAL`, `LONGEVITY`,
      `FINANCE_SPECULATION`, `VASTU_FENG_SHUI` and `NUMEROLOGY` is `answerable=true`
      and found by `sheets(group)` (FR-010's named Sensitive and Beyond-natal-KP
      groups, exercised by T012's representative sheets) — all runnable today,
      independent of T047. Add a separate T047-gated case asserting ≥ 2,000 promise
      rules and 75 sheets against `KpToolkit.bundled()` (SC-001, SC-002) in
      `core/src/test/java/com/celestia/core/toolkit/ToolkitCompletenessTest.java`
      (depends on T039, T018, T012; the SC-001/SC-002 case additionally depends on
      T047)

**Checkpoint**: User Stories 1–5 all work independently.

---

## Phase 8: User Story 6 - Toolkit versioning (Priority: P3)

**Goal**: a new toolkit release replaces the old one in one repeatable, auditable
step; re-importing an unchanged workbook changes nothing.

**Independent Test**: re-importing the same workbook produces no change; importing a
workbook with a new or renamed sheet stops and asks for that sheet to be classified
(spec.md Independent Test, US6).

### Implementation for User Story 6

- [ ] T041 [US6] Drift guard: compare the workbook's sheet names against the manifest
      at import time; exit non-zero with `manifest out of date: unmapped sheets […],
      absent sheets […]` on any mismatch (FR-013) in
      `tools/toolkit-import/import_toolkit.py` (depends on T038)
- [ ] T042 [US6] Determinism: the same workbook gives byte-identical output across two
      runs (SC-006); record the workbook SHA-256 in `toolkit.properties` in
      `tools/toolkit-import/import_toolkit.py` (depends on T041)
- [ ] T043 [P] [US6] `tools/toolkit-import/README.md`: the command, the exit codes,
      the resource formats, and the operational rule (the workbook is never
      committed; after a re-import that changes anything, review the diff rule by
      rule, then commit) per [contracts/import-tool.md](contracts/import-tool.md)

**Checkpoint**: all six user stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: documentation and final verification across all stories.

- [ ] T044 [P] Add rows to `core/src/main/java/com/celestia/core/REFERENCES.md` for:
      the connecting-houses definition (research.md §3), sensitivity ordering and the
      four fixed advisory sentences (research.md §5), the horary fix-up
      (research.md §10) and the engine-version composition (research.md §7); extend
      the "Engine version bump procedure" paragraph to name `core.toolkit` and
      `KpToolkit`
- [ ] T045 [P] Javadoc the API-stability note on `com.celestia.core.toolkit`: a
      toolkit release changes data, not the API; renaming a sheet id is a breaking
      change and needs an ADR (contracts/core-api.md "Stability") in
      `core/src/main/java/com/celestia/core/toolkit/KpToolkit.java` and
      `core/src/main/java/com/celestia/core/toolkit/ToolkitSheet.java`
- [ ] T046 Run quickstart.md steps 3, 4 and 6 — `./mvnw -pl core -am test
      -Dtest='com.celestia.core.toolkit.*Test'`, `./mvnw -pl agent -am test
      -Dtest=CurrentEngineVersionTest`, `./mvnw verify` — and confirm all green,
      including the ArchUnit determinism/layering tests and Spotless
- [ ] T047 **MANUAL — owner only**: run `python3 tools/toolkit-import/import_toolkit.py
      <licensed-workbook.xlsx>` against the real *Prediction ToolKit for Advanced*
      workbook (quickstart.md step 1), review the generated diff rule by rule, and
      commit the real resources under
      `core/src/main/resources/com/celestia/core/toolkit/`. The T047-gated cases in
      T029, T035 and T040 (SC-001–SC-004) can only be certified once this step is
      done — the workbook is licensed content that stays outside the repository and
      outside any coding agent's reach
- [ ] T048 **MANUAL — owner only**: for each of the 3 documented golden charts, run
      `PromiseGoldenTest` against the real resources (post-T047), pull 10 promise
      readings per chart (30 total) from its snapshot, and have a KP practitioner
      mark each "correctly applied" or not. Record the score. SC-005 passes at
      ≥9/10 per chart; a chart scoring below that is a defect in the rule-cell
      grammar (T014) or the connecting-houses logic (T020), not in the review
      itself — file it against the responsible task and re-run quickstart.md
      step 5 after the fix (depends on T047, T024)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — BLOCKS every user story
- **User Stories (Phases 3–8)**: all depend on Foundational
  - US1 and US2 (both P1) have no dependency on each other and can proceed in
    parallel, or sequentially in spec order
  - US4 depends only on Foundational (`KpToolkit.sheets()`/`sheet()`), not on
    US1/US2 — it can start as soon as Foundational is done. US3 is likewise
    independent of US1/US2/US4.
  - US5 is **not** independent of US1 and US4: T039 (exclude non-answerable sheets
    from search) depends on T036 (US4's `search()`), and T040 (completeness test)
    depends on T018 (US1's `promiseRules()`) as well as T039. US5's manifest work
    (T038) depends only on T004 and can start early, but its completeness *test*
    cannot land before US1 and US4 do.
  - US6 (T041) depends on US5's manifest-application (T038)
- **Polish (Phase 9)**: depends on all desired user stories being complete; T047 is
  independent of the others and can happen at any time once the owner has the
  workbook. T048 depends on T047 and T024 (US1's `PromiseGoldenTest`)

### Within Each User Story

- Import-tool tasks before the core-API tasks that read their output
- Records/readers before the `KpToolkit` methods that expose them
- `KpToolkit` methods before the tests that exercise them

### Parallel Opportunities

- T002 depends on T001 (same file) but can then run in parallel with T003 and T004
  (different files); T003 and T004 are marked [P] and can run in parallel with T001
- All Foundational tasks marked [P] (T005–T010, T012) can run in parallel
- Once Foundational is done, US1 and US2 (both P1) can be staffed in parallel; so can
  US3 and US4 once US1/US2 are underway, since neither depends on promise rules or
  horary content
- Within a story, the Python (import-tool) and Java (core) tracks are independent
  files and can run in parallel (e.g. T014 ∥ T017; T026 ∥ T027)

---

## Parallel Example: User Story 1

```bash
# Python (import tool) and Java (core) tracks start together:
Task: "Rule-cell grammar parser in tools/toolkit-import/import_toolkit.py"       # T014
Task: "PromiseRule record + reader in core/.../toolkit/PromiseRule.java"         # T017
Task: "HousePromise + PromiseHit records in core/.../toolkit/{HousePromise,PromiseHit}.java"  # T019
Task: "SyntheticChart test helper in core/src/test/.../toolkit/SyntheticChart.java"  # T021
```

## Parallel Example: User Story 2

```bash
Task: "Horary importer (merge + L07 fix-up) in tools/toolkit-import/import_toolkit.py"  # T026
Task: "HoraryMindset record + reader in core/.../toolkit/HoraryMindset.java"            # T027
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 — both P1)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — CRITICAL, blocks everything.
2. Complete Phase 3 (US1: promise readings) and Phase 4 (US2: horary + mindset).
3. **STOP and VALIDATE**: run `PromiseEvaluatorTest`, `PromiseGoldenTest` and
   `ToolkitHoraryCrossCheckTest` (the latter against its Java-generated 249-row
   fixture, `HoraryFixtureSupport`) — all pass without the real workbook.
4. The real-content cases in T029/T035/T040 and T048's practitioner review (SC-001,
   SC-002, SC-003's fix-up, SC-004's real rows, SC-005) wait on T047 (owner runs the
   real import); everything else is verifiable today against fixtures.

### Incremental Delivery

1. Setup + Foundational → the loader works.
2. US1 + US2 → the MVP: chart-grounded promises and horary mindsets, both cited.
3. US3 → star/planet/house look-ups.
4. US4 → procedure/template retrieval for the agent to follow.
5. US5 → completeness guarantee (nothing silently dropped).
6. US6 → repeatable, auditable re-import.
7. Polish → documentation, full build, the owner's manual import (T047), then the
   practitioner review (T048).

### Parallel Team Strategy

With multiple developers, after Foundational:
- Developer A: US1 (promise rules + evaluator)
- Developer B: US2 (horary) then US6 (versioning)
- Developer C: US3 (look-ups) then US4 (search/procedures)
- Whoever finishes US1 and US4 first picks up US5 (completeness) — it needs both

---

## Notes

- [P] tasks touch different files with no unmet dependency
- [Story] labels trace every task back to its spec.md user story
- Tests are included per Constitution III and plan.md's explicit test-class list —
  they are not optional for this feature
- Commit after each task or logical group; stop at any checkpoint to validate a story
  independently
- T047 and T048 are the two tasks genuinely blocked on the owner (the real workbook,
  then a human practitioner's review); every other task is buildable and testable
  today against Java-generated fixtures and golden charts — no Python-side
  computation and no model call stands in for a `core` calculation anywhere in this
  feature
