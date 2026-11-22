# Contract: `com.celestia.core.toolkit` public API (SPEC-010)

Pure Java, no Spring. All types are immutable records or enums. All methods are
deterministic.

## `KpToolkit`

| Method | Returns | Errors / notes |
|--------|---------|----------------|
| `static KpToolkit bundled()` | the shared instance, loaded once | `IllegalStateException` if resources are missing or inconsistent (data-model "Validation at load") |
| `ToolkitVersion version()` | release identity | |
| `List<ToolkitSheet> sheets()` | all sheets in workbook order | includes non-answerable sheets |
| `Optional<ToolkitSheet> sheet(String id)` | one sheet | |
| `List<ToolkitSheet> sheets(TopicGroup g)` | sheets of a group | |
| `List<SheetHit> search(String query, int limit)` | answerable sheets only, ranked: title-term matches ×100 + matching rows; ties by workbook order | terms < 3 letters ignored; empty query → empty list |
| `List<PromiseRule> promiseRules()` | all rules | |
| `List<PromiseRule> rulesFor(int judgedHouse)` | rules judged from that house's CSL | `IllegalArgumentException` outside 1..12 |
| `List<PromiseRule> findRules(String query)` | rules whose matter + category contain every term | |
| `HoraryMindset horary(int n)` | toolkit row for 1..249 | `IllegalArgumentException` outside 1..249 |
| `Optional<StarEntry> starProfession(Nakshatra, int quarter)` / `starDisease(…)` | look-up | total over 27 × 4 |
| `Optional<String> planetInHouse(Graha, int house)` | look-up | |
| `Optional<String> higherEducation(Graha ninthCslStarLord, int house)` | look-up | |
| `Friendship friendship(Graha of, Graha towards)` | relation | `IllegalArgumentException` if equal |
| `String houseMeaning(int house)` / `relationship(int cusp)` | text | 1..12 |
| `String planetCharacteristics(Graha)` / `planetDirection(Graha)` | text | total over the 9 grahas *(2026-11-23 clarification, FR-006)* |
| `String zodiacCharacteristics(Sign)` | text | total over the 12 signs *(2026-11-23 clarification, FR-006)* |

`SheetHit(ToolkitSheet sheet, int score, List<List<String>> matchingRows)`.

## `PromiseEvaluator`

| Method | Returns |
|--------|---------|
| `new PromiseEvaluator(KpToolkit)` / `static bundled()` | evaluator |
| `List<HousePromise> evaluate(NatalChart chart)` | 12 house promises; no PARTLY_INDICATED |
| `List<HousePromise> evaluate(NatalChart, SignificatorTable, boolean includePartial)` | 12 house promises |
| `HousePromise evaluateHouse(NatalChart, SignificatorTable, int house, boolean includePartial)` | one house |

Works for any `NatalChart`: the user's natal chart, a horary chart (SPEC-005), or a
second person's chart supplied in conversation (FR-012). Nothing is stored.

## `Sensitivity`

`note()` returns the fixed advisory sentence (empty for NONE). `max(other)` returns the
more severe class. These constants are the only source of advisory copy (FR-010a).

## Stability

Adding a toolkit release changes data, not this API. Renaming a sheet id is a breaking
change and needs an ADR. Adding a `TopicGroup` constant is not breaking.
