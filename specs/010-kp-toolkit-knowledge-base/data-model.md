# Data Model: KP Toolkit Knowledge Base (SPEC-010)

## Entities

### ToolkitRelease (`ToolkitVersion`)

| Field | Type | Rule |
|-------|------|------|
| title | text | "Prediction ToolKit for Advanced" |
| version | text | workbook release date, e.g. `2026-09-12` (latest date in its Version Control sheet) |
| sheets | int | must equal the manifest row count |
| rules | int | must equal the promise-rule count |
| sourceSha256 | text, 64 hex | fingerprint of the imported workbook |
| id() | derived | `toolkit-<version>`; part of the engine version (research §7) |

### Sheet (`ToolkitSheet`)

| Field | Type | Rule |
|-------|------|------|
| order | int ≥ 1 | position in the workbook |
| id | slug, unique | stable across releases (e.g. `medical-astrology`) |
| title | text | the workbook's sheet title |
| group | `TopicGroup` | KP_FOUNDATIONS, HORARY, PREDICTION_METHOD, LIFE_MATTERS, MARRIAGE_RELATIONSHIPS, CAREER_EDUCATION, FINANCE_SPECULATION, PROPERTY, HEALTH_MEDICAL, LONGEVITY, TRAVEL, CHILDBIRTH, REMEDIES, VASTU_FENG_SHUI, NUMEROLOGY, REFERENCE, HOUSEKEEPING |
| kind | `SheetKind` | RULES, TABLE, PROCEDURE, TEMPLATE, REMEDY, HOUSEKEEPING |
| sensitivity | `Sensitivity` | sheet default (research §5) |
| answerable | bool | false for housekeeping and author-"ignore" sheets (FR-014) |
| rows | list of list of text | every non-empty row in order, trailing empty cells trimmed |

### PromiseRule

| Field | Type | Rule |
|-------|------|------|
| id | text, unique | `<sheet-id>:<row ref>` |
| source | sheet id | must exist in the manifest |
| category | text | the sheet's own grouping ("Income", "Difficulty", "Rule book", …) |
| matter | text, non-blank | the promised event, verbatim |
| matterKey | text | lower-case, punctuation-stripped matter (research §8) |
| judgedHouse | 1..12 | the house whose CSL is judged |
| houses | ordered list of 1..12, no duplicates | may be empty (condition-only rule) |
| condition | text | verbatim; blank when none |
| sensitivity | `Sensitivity` | ≥ the source sheet's default |

### HousePromise (computed per chart, per house; not stored by `core`)

| Field | Type | Rule |
|-------|------|------|
| house | 1..12 | |
| cuspSubLord / cuspSubLordRetrograde | Graha / bool | from `NatalChart` |
| starLord / starLordRetrograde | Graha / bool | star lord of the CSL's longitude |
| connectedHouses | sorted set of 1..12 | CSL's `STAR_OF_OCCUPANT` ∪ `STAR_OF_OWNER` houses (research §3) |
| hits | list of `PromiseHit` | PROMISED and CONDITION_TO_CHECK in rule-book order, then PARTLY_INDICATED when requested |
| sensitivity | `Sensitivity` | max over hits |

### PromiseHit

| Field | Type | Rule |
|-------|------|------|
| rule | PromiseRule | `rule.judgedHouse == house` |
| connected / missing | lists of 1..12 | partition of `rule.houses` |
| status | PROMISED · CONDITION_TO_CHECK · PARTLY_INDICATED | PROMISED ⇒ missing empty and condition blank; CONDITION_TO_CHECK ⇒ missing empty and condition present; PARTLY_INDICATED ⇒ connected and missing both non-empty |

### Look-up entries

| Entity | Key | Value | Source sheet |
|--------|-----|-------|--------------|
| HoraryMindset | number 1..249 | sign, star, from, to, sign/star/sub lord, mindset | 249SubDivisionsHoraryNumbers + 249Mindsets |
| StarEntry (profession) | Nakshatra + quarter set | label, professions, classical professions | Star-wise Professions |
| StarEntry (disease) | Nakshatra + quarter set | label, body parts, diseases | StarwiseDiseases |
| Planet-in-house | Graha + house | signification text | PlanetPositionSignification |
| Education stream | Graha (9th CSL's star lord) + house | stream text | HigherEducationStreams |
| Friendship | Graha × Graha (distinct) | FRIENDLY / NEUTRAL / ENEMY | PlanetFriendship |
| House meaning | house 1..12 | text | Houses Meaning |
| Relationship | cusp 1..12 | relationship for chart rotation | Relationship |

### Sensitivity

| Class | Order | Fixed advisory sentence (FR-010a) |
|-------|------:|-----------------------------------|
| NONE | 0 | — |
| REMEDY | 1 | Traditional remedy — complementary to, never a replacement for, professional advice. |
| FINANCE | 2 | Astrological guidance only — not financial advice; consult a registered financial advisor before investing. |
| HEALTH | 3 | Astrological guidance only — please consult a qualified doctor for diagnosis or treatment. |
| LONGEVITY | 4 | Astrological indications only — not a prediction of any certain event; please consult a doctor about any health concern. |

## Validation at load (fail fast)

- manifest rows == `toolkit.properties` sheets; rule rows == `toolkit.properties` rules.
- every rule's `source` is a manifest id; every house in 1..12.
- 249 horary rows numbered 1..249 exactly once.
- star tables: every (nakshatra, quarter) covered exactly once.
- friendship: all 72 ordered pairs present.

## Relationships

`ToolkitRelease 1—* Sheet`; `Sheet 1—* PromiseRule` (rule sheets); `Sheet 1—* look-up
entries` (table sheets); `NatalChart + SignificatorTable → 12 HousePromise`;
`HousePromise 1—* PromiseHit *—1 PromiseRule`.
