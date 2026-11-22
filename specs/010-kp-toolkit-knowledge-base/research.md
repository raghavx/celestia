# Research: KP Toolkit Knowledge Base (SPEC-010)

Source analysed: *Prediction ToolKit for Advanced*, v2026-09-12, 75 sheets (the
workbook's INDEX lists 71 course topics; the rest are the index, the version log and
helper tables). A first prototype was built and run on 2026-11-22, then discarded.
Its findings are used below.

## §1 Resource format

- **Decision**: UTF-8 TSV with a `#` comment line and a header line. Cells escape
  `\\`, `\t`, `\n`. Plus one `toolkit.properties`.
- **Rationale**: `core` has no runtime JSON library (Constitution II keeps it lean).
  TSV is about 60 lines of reader, and its diffs show one rule per line, which a
  reviewer can audit when a toolkit release changes.
- **Alternatives**: JSON (needs Jackson at runtime in `core`); reading the `.xlsx` at
  runtime (needs POI, a licensed file at runtime, slower startup); a DB table
  (breaks II, and the data is static).

## §2 Rule-cell grammar

- **Decision**: parse a leading house list only. Everything after it is the rule's
  **condition**, kept word for word (FR-003).

| Cell form | Example | Result |
|-----------|---------|--------|
| single | `7` | [7] |
| list | `1, 6, 8, 12` | [1, 6, 8, 12] |
| list + condition | `6, 8, 12 + Mars / Sun` | [6, 8, 12], `+ Mars / Sun` |
| list + prose | `1, 2, 3, 6, 10, 11 and not related with Maraka and Badhaka` | list, prose as condition |
| formula line | `4 = 2, 4, 11 Construct a house +12 housing loan` | judged 4, [2, 4, 11], matter text |
| formula + leading condition | `4 = 4, 9, 11, 12 with influence of Jupiter, Construction of …` | condition `with influence of Jupiter` |
| no houses | `Saturn as 7th sub lord` | [], whole text as condition, never auto-matched |

- **Sheets parsed**: 1500+Rules, ReadyReckoner, JobRules (numbered rows only; the
  worked examples stay verbatim), Difficulty, Luck, Romance, Stock Market, Medical,
  Finance, Inheritance, Property. The prototype yielded **2,008 rules**.
- **Alternatives**: NLP extraction of planets or signs into structured conditions. This
  was rejected for v1 because it would invent structure the author didn't write
  (Constitution X).

## §3 "Connecting houses through the star lord of the house sub lord"

- **Decision**: the CSL is connected to house h iff, in SPEC-003's four-step table,
  the CSL signifies h at step `STAR_OF_OCCUPANT` (its star lord occupies h) or
  `STAR_OF_OWNER` (its star lord owns h). This is the toolkit's ABCD "A + C" for the
  CSL.
- **Rationale**: it is the literal column header of 1500+Rules, and in KP the star
  lord delivers the result. Rahu/Ketu agency is already folded into
  `SignificatorTable`.
- **Alternatives**: all four steps (credits the CSL's own occupancy and ownership,
  contrary to the header); occupancy only (drops ownership, which ABCD counts).
- **Prototype check**: a synthetic chart (1st CSL Venus in Pushya, star lord Saturn
  in house 7 and owning 10 and 11) gave exactly {7, 10, 11}.

## §4 All vs partial

- **Decision**: **promised** means all houses are connected and there is no condition.
  **Condition to check** means all houses are connected and a condition exists.
  **Partly indicated** means some houses are connected; it is returned only on request.
- **Rationale**: the toolkit writes a combination ("1, 6, 8, 12 → always in poor
  health") as one condition. Partial matches are still useful context but must not be
  presented as promises.

## §5 Sensitivity classes (FR-010a, FR-011)

- **Decision**: five classes ordered NONE < REMEDY < FINANCE < HEALTH < LONGEVITY.
  - **Sheet level:** set by hand in the import manifest.
  - **Rule level:** the sheet's default, raised by keywords in the rule's own wording.
    - LONGEVITY: death, danger to life, longevity, long/short life, fatal, maraka.
    - HEALTH: disease, health, hospital, surgery, sick, immunity, illness, mental,
      insanity, blind, deaf, depression, addiction, cure, treatment, injury, accident,
      pregnancy, operation, disorder, infection, chronic, disability, suicide.
    - FINANCE: share market, stock, speculation, lottery, gambling, investment, futures
      and options.
  - Suicide-related rules fall in HEALTH, like any other rule (Q2: B). There is no
    separate class.
- **Known issue from the prototype**: the classification fires on *favourable* health
  rules too ("good immunity"), so houses with only good health rules carried a HEALTH
  sentence. Decision: keep it for v1, because the advisory sentence is harmless on
  good news, but record the per-rule class so SPEC-011 can refine it to adverse rules
  only.

## §6 Star-name normalisation

- **Decision**: a fixed alias table maps every spelling used in the two star sheets to
  a `Nakshatra` constant. The table covers Sanskrit, Tamil and transliteration
  variants (Aswini / Ashvini, Karthikai / Krittika, Makam / Magha, Poorvapathrapada /
  Purva Bhadrapada, …). The quarter is parsed from the forms "1Q", "2-3-4Q",
  "2, 3 and 4th Quarters", "First/Second Half", "I-Quarter" and "IV quarter". An
  unknown label fails the import.
- **Result** (prototype): 36 rows in each sheet; 108/108 nakshatra-quarters covered.

## §7 Engine version (Constitution III, FR-009)

- **Decision**: `EngineVersion.RULES` stays the hand-bumped KP rules id in `ephemeris`.
  `ephemeris` cannot depend on `core`, so `agent`'s `CurrentEngineVersion` composes
  `rules = EngineVersion.RULES + "+" + KpToolkit.bundled().version().id()`, for
  example `kp-1+toolkit-2026-09-12`. Any toolkit release therefore invalidates cached
  charts and readings, which the cache already handles.
- **Alternatives**: a separate `toolkit_version` column (a migration for no gain); hard-
  coding the toolkit id into `RULES` (easy to forget on re-import).

## §8 The same matter in several sheets

- **Decision**: keep every version (for example "foreign travel for job" appears in
  1500+Rules `12 = 3,9,12 + 6`, ReadyReckoner `12 = 3,9,11,12 + 6` and JobRules).
  Each `PromiseHit` carries a `matterKey` (lower-case, punctuation-stripped matter).
  The agent contract groups hits by `matterKey` and cites all their sources once, so
  the reply never counts them as independent confirmations.

## §9 Non-answerable sheets (FR-014)

- **Decision**: the manifest flags `answerable=false` for INDEX, Version Control,
  Software List KP Astrology, and PlanetsStockSectors (its author marks it "Under
  research – Ignore"). They are stored, browsable by id, excluded from search results
  and from any tool answer.

## §10 Source fix-ups

- **Decision**: one, explicit and tested: 249Mindsets row "L07" is horary 107. Its
  sign, star and sub lords match `Horary249.arc(107)`. All other text is verbatim,
  spelling included.

## §11 Why the import tool is Python

- **Decision**: Python 3 + `openpyxl`, dev-only, not run by `./mvnw verify`, just like
  `tools/ephe-crosscheck`.
- **Rationale**: the workbook has merged cells, mixed types and dates. `openpyxl`
  reads them reliably, and keeping Apache POI out of the Maven build avoids a large
  dependency for a one-off step.
- **Determinism**: the same workbook gives byte-identical output (verified in the
  prototype by diffing two runs). The tool records the workbook SHA-256 and refuses a
  workbook whose sheet names differ from its manifest.
