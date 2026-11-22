# Feature Specification: KP Toolkit Knowledge Base

**Feature Branch**: `010-kp-toolkit-knowledge-base`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "KP Toolkit knowledge base: bring the licensed "Prediction ToolKit for Advanced" workbook (v2026-09-12, 75 sheets — 1500+ house-sublord promise rules, ReadyReckoner, JobRules, 249 horary mindsets, star-wise professions/diseases, marriage, finance, health, longevity, Vastu, numerology, remedies, procedures like BTR/annual prediction/missing person) into the product as the base knowledge the agent answers from."

_The engine (SPEC-001 to 006) already computes every KP number for a user's chart:
cusps, star and sub lords, significators, ruling planets, dasha, horary. What the
product lacks is the **astrologer's judgement**: the rules that turn "the 7th
cuspal sub lord is Rahu, in Jupiter's star, connecting houses 1, 3, 4, 7, 9" into
"an affectionate spouse; gains through business". The toolkit is that judgement,
written down. This feature makes it the product's single source of KP
interpretation, applied to the user's chart by deterministic logic, traceable
internally to the sheet it came from, and explained the way an astrologer would —
never by naming the toolkit to the user (FR-008, ADR-0025)._

### What the toolkit contains (analysis of the 75 sheets)

| Group | Sheets | Shape |
|-------|--------|-------|
| **Promise rule book** | 1500+Rules (≈1,600 rules), ReadyReckoner (≈170), JobRules (≈70), Difficulty (≈50), Finance, Property, Inheritance, Luck, Romance, Stock Market, Medical | "Sub lord of house H, through its star lord, connects to houses X, Y ⇒ event"; some rules carry an extra condition (a planet, sign type, Maraka/Badhaka) |
| **Horary** | 249 sub-divisions, 249 mindsets, Missing Person | number → sign / star / sub lord and a "mindset" reading; step-by-step method |
| **Star / planet / house look-ups** | Star-wise Professions, Star-wise Diseases, Planet Position Signification, Higher Education Streams, Houses Meaning, Planet Characteristics, Planet Friendship, Planet Direction, Zodiac Signs and Characteristics, Relationship (chart rotation) | keyed by nakshatra + quarter, planet × house, sign, or house |
| **Methods and templates** | Ruling Planets Logic, ABCD Significance, Prediction Algorithm (25 scenarios), Prediction Template, Time of Event, Strongest Planet, Malefic/Benefic, Untenented Planets, Aspects, Full Life Events, Astro Diary, Annual Prediction, BTR, Twin Birth, Quantum Astrology, Profession method, Marriage Rules / Matching / Partner Looks, Punarpoo, Child Birth Time / Gender, Fortuna | procedures an astrologer follows step by step |
| **Sensitive topics** | Medical Astrology, Health Chart, Health Impact by Planets, Star-wise Diseases, Death, Death Mode Rules, Difficulty (incl. suicide, insanity, crime), Stock Market, Planets–Stock Sectors (marked "under research – ignore" by its author) | rules about disease, lifespan, manner of death, speculation |
| **Beyond natal KP** | Astro-Vastu (4 sheets), Feng Shui Five Elements, Numerology, five Yantras, Gems Remedies, Shubha Horai, Zodiac Places | remedies, home zones, number systems |
| **Housekeeping** | Index, Version Control, Software List | not answer content |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chart-grounded promise reading (Priority: P1)

A subscribed user asks about a life matter ("Will I get a government job?", "How
will my married life be?"). The system finds the toolkit rules for that matter,
checks them against the user's own chart, and the reply states what is promised,
why (which house, sub lord, star lord, connected houses) and which toolkit rule
it rests on.

**Why this priority**: This is the product's core promise: a KP reading of *my*
chart that a KP astrologer would recognise as correct, not generic text.

**Independent Test**: For a documented test chart, ask for the promise of each of
the 12 houses. Every rule reported as promised must have all its required
connecting houses actually connected in that chart, and each must name its source
rule.

**Acceptance Scenarios**:

1. **Given** a chart whose 7th cuspal sub lord connects (through its star lord) to
   houses 2, 7 and 11, **When** the user asks about marriage, **Then** the reply
   includes the toolkit's marriage-promise rules for those houses and names the
   7th sub lord and its star lord.
2. **Given** a rule that requires an extra condition the system cannot decide by
   houses alone (for example "+ Saturn"), **When** its houses are connected,
   **Then** it is reported as "indicated, condition to check" and never as a
   firm promise.
3. **Given** the same chart and question asked twice, **When** answered, **Then**
   the same rules are found both times.

---

### User Story 2 - Horary answer with the mindset (Priority: P1)

A user gives a horary number (1–249) with a question. The reply uses the toolkit's
sign, star and sub lord for that number and its mindset reading, alongside the
horary chart judgement.

**Why this priority**: Horary is a headline KP service and the toolkit's 249 table
is the reference KP students check against.

**Independent Test**: For all 249 numbers, the toolkit's sign, star and sub lord
must equal the engine's own horary table, and every number must have a mindset.

**Acceptance Scenarios**:

1. **Given** horary number 107, **When** looked up, **Then** the toolkit row
   (Virgo, star lord Sun, sub lord Jupiter) and its mindset are returned, even
   though the workbook mis-numbers that row as "L07".
2. **Given** a number outside 1–249, **When** given, **Then** the user is asked
   for a valid number and no reading is produced.

---

### User Story 3 - Star, planet and house look-ups (Priority: P2)

The reply to profession, education, character or body-part questions draws on the
look-up sheets for the user's actual placements. Examples: the profession list for
the nakshatra and quarter of the 10th sub lord; the education stream for the 9th
sub lord's star lord by house; what a planet in a given house signifies.

**Why this priority**: These are the most-asked "what does my chart say about me"
questions, and they need precise keys (nakshatra + quarter, planet + house), not
text search.

**Independent Test**: Every nakshatra-quarter (27 × 4 = 108) resolves to exactly
one profession row and one body-part row; every planet × house combination the
sheets cover resolves to its text; every planet (9 of 9) resolves to exactly one
characteristics row and one direction row; every zodiac sign (12 of 12) resolves
to exactly one characteristics row. *(Clarified 2026-11-23.)*

**Acceptance Scenarios**:

1. **Given** a 10th sub lord in Krittika quarter 3, **When** profession is asked,
   **Then** the "Krittika 2-3-4Q" professions are used.
2. **Given** a 9th sub lord whose star lord sits in the 10th house, **When** higher
   studies are asked, **Then** that planet's stream for house 10 is used.

---

### User Story 4 - Methods the agent follows (Priority: P2)

For procedure questions (birth-time rectification, annual prediction, timing of
an event, missing person, marriage matching, gemstone selection), the agent follows
the toolkit's own steps in order, using engine values at each step, without naming
the procedure's source to the user.

**Why this priority**: Procedures hold the toolkit's "how a KP astrologer actually
works". Without them the agent improvises method, which the constitution forbids
for numbers and is risky for judgement.

**Independent Test**: For each procedure sheet, the agent can retrieve its steps
verbatim and in order (this feature's scope, FR-007). The full reply behaviour below
— every step that needs a number filled from the engine, never by the model — is
the agent-tool wiring of SPEC-012, which this feature's retrieval makes possible.

**Acceptance Scenarios**:

1. **Given** "when will my health improve?", **When** answered, **Then** the reply
   follows the Time of Event steps (dasha lord, bhukti lord, current ruling
   planets) with the engine's values. *(End-to-end behaviour delivered by SPEC-012;
   this feature supplies the ordered, verbatim steps it fills.)*

---

### User Story 5 - Every sheet retrievable, nothing lost (Priority: P2)

Every sheet of the workbook, including the ones not turned into structured rules,
is stored complete and can be found by topic, so the agent can draw on it when
answering.

**Why this priority**: "The toolkit as the base" means nothing is silently dropped.
The owner can trust that a question covered by the toolkit can be answered from it.

**Independent Test**: The stored toolkit has as many sheets as the workbook; each
sheet's rows match the workbook's non-empty rows; a search by a sheet's title
returns that sheet first.

---

### User Story 6 - Toolkit versioning (Priority: P3)

When the owner receives a new release of the toolkit, it replaces the old one in a
single, repeatable step. The change shows rule by rule, and every reading records
which toolkit version produced it.

**Why this priority**: The workbook's own version log shows releases every few
months. Readings must stay explainable after an update.

**Independent Test**: Re-importing the same workbook produces no change; importing
a workbook with a new or renamed sheet stops and asks for that sheet to be
classified.

### Edge Cases

- A rule with no house numbers at all ("Saturn as 7th sub lord") is kept and
  retrievable but never matched automatically.
- The same matter appears in several sheets with slightly different houses (e.g.
  "foreign travel for job" in 1500+Rules, ReadyReckoner and JobRules). All
  versions are kept, each with its source; the reply must not present them as
  independent confirmations.
- Star names spelled in different scripts or traditions (Aswini / Ashvini,
  Karthikai / Krittika, "First Half" vs "1-2Q") resolve to the same nakshatra and
  quarter; an unrecognised spelling stops the import.
- A sheet its author marks "under research – ignore" is stored but not used for
  answers.
- The cuspal sub lord or its star lord is retrograde. The toolkit's Prediction
  Algorithm treats this as weakening or delaying the result, so the reply must say so.
- A rule touches suicide or self-harm (Difficulty and 1500+Rules contain such
  rules). It is handled like any other health-class rule (FR-011).
- A second person's birth data supplied in the conversation is used for that
  reply only. It is never added to the requesting user's profile or treated as
  their own chart (FR-012).

## Clarifications

### Session 2026-11-22

- Q: Which toolkit sheet groups are in scope? → A: Everything except
  housekeeping, with a fixed advisory sentence on health, longevity, finance and
  remedy answers (FR-010, FR-010a).
- Q: How are suicide / self-harm rules handled? → A: Like any other rule, with the
  health advisory (FR-011).
- Q: Can a user supply another person's chart? → A: Yes, birth data or a horary
  number given in the conversation; never another user's stored data (FR-012).

### Session 2026-11-23

- Q: Should "Planet Characteristics," "Planet Direction," and "Zodiac Signs and
  Characteristics" be precise keyed look-ups or generic searchable sheets? → A:
  Precise keyed look-ups, matching the other star/planet tables — "Planet
  Characteristics" and "Planet Direction" keyed by planet alone (9 rows each),
  "Zodiac Signs and Characteristics" keyed by zodiac sign alone (12 rows) (FR-006).
- Q: Who carries out the SC-005 KP-practitioner review? → A: The owner themselves,
  as the licence holder; no external reviewer or confidentiality/NDA step is
  needed (SC-005).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST hold the complete toolkit: every one of its sheets,
  every non-empty row, with its title, a topic group and a kind (rules, look-up,
  method, template, remedy, housekeeping).
- **FR-002**: The system MUST turn every house-formula rule (FR-001's promise rule
  book group) into a structured rule: the house whose sub lord is judged, the
  connecting houses, any extra condition kept word for word, the promised matter
  and its source sheet and row.
- **FR-003**: The system MUST never invent houses or conditions. Text that is not a
  house number stays as the rule's extra condition.
- **FR-004**: For a given chart, the system MUST determine for each house: its
  cuspal sub lord, that sub lord's star lord, the houses the sub lord connects to
  through its star lord (those the star lord occupies or owns), and whether each
  of them is retrograde.
- **FR-005**: The system MUST classify each rule against a chart as **promised**
  (all connecting houses connected, no extra condition), **indicated — condition
  to check** (all connected, extra condition present) or **partly indicated**
  (some connected). Partly indicated rules are reported only when asked for.
- **FR-006**: The system MUST provide the horary 1–249 table with mindsets, verified
  against the engine's own table, and the star, planet and house look-ups in
  User Story 3, keyed precisely (nakshatra + quarter, planet + house, house, planet
  alone for Planet Characteristics and Planet Direction, zodiac sign alone for
  Zodiac Signs and Characteristics). *(Clarified 2026-11-23.)*
- **FR-007**: The agent MUST be able to retrieve any method or template sheet's
  steps in order, verbatim, as this feature's knowledge-base query. Filling each
  step's numeric values from the engine (dasha lords, ruling planets, …) is the
  agent-tool wiring delivered by SPEC-012, which consumes this retrieval (see
  Assumptions).
- **FR-008**: Every statement in a reply that rests on the toolkit MUST carry a
  traceable source sheet (the `source` field this feature returns on every result,
  per contracts/agent-tools.md). The agent MUST NOT state KP interpretation that is
  not traceable to the toolkit or to an already-cited KP source. **This tracing is
  internal**: the toolkit is a licensed asset used to build Celestia, not something
  the user has any relationship to. The reply to the user MUST NEVER name the
  toolkit itself, a sheet's id/title, or a row number — it explains the
  astrological basis in ordinary KP language (the house, the sub lord, the star
  lord, the matter) without naming its source artifact.
- **FR-009**: Matching MUST be deterministic: the same chart and toolkit version
  always yield the same rules, and every stored reading records the toolkit
  version.
- **FR-010**: Every toolkit sheet group except Housekeeping MUST be answerable,
  including the Sensitive group (medical, disease, longevity, death, speculation)
  and the Beyond-natal-KP group (Astro-Vastu, Feng Shui, numerology, yantras,
  gems, Shubha Horai). *(Clarified 2026-11-22, Q1: A.)*
- **FR-010a**: Every sheet and every rule MUST carry a sensitivity class (none,
  remedy, finance, health, longevity). Any reply that draws on a health,
  longevity, finance or remedy class MUST end with exactly one fixed advisory
  sentence for the most severe class used. The wording is set by the product, not
  generated per reply:
  - health: "Astrological guidance only — please consult a qualified doctor for diagnosis or treatment."
  - longevity: "Astrological indications only — not a prediction of any certain event; please consult a doctor about any health concern."
  - finance: "Astrological guidance only — not financial advice; consult a registered financial advisor before investing."
  - remedy: "Traditional remedy — complementary to, never a replacement for, professional advice."
- **FR-011**: Rules about suicide or self-harm MUST be treated like any other
  rule: matched against the chart, reported with their status and source, and
  carrying the health advisory sentence. *(Clarified 2026-11-22, Q2: B.)*
- **FR-011a** *(added by Claude; confirmed by owner 2026-11-22)*: When a user's own
  message expresses an intent or wish to harm themselves (as distinct from asking
  what the chart says), the reply MUST include a short support line with a crisis
  helpline (Tele-MANAS 14416, India) before any astrological content. This does
  not change FR-011.
- **FR-012**: For procedures that need a second person's chart (Marriage Matching,
  Missing Person, Twin Birth, Relationship chart rotation), the user MAY supply
  that person's birth data or a horary number in the conversation, and the system
  MUST use it for that reply. The system MUST NEVER read another Celestia user's
  stored data, whatever the message says. *(Clarified 2026-11-22, Q3: A.)*
- **FR-013**: Re-importing a toolkit release MUST be one repeatable step that
  produces identical results for an identical workbook, shows changes rule by
  rule, and refuses a workbook whose sheets are not all classified.
- **FR-014**: Housekeeping sheets and sheets marked "ignore" by their author MUST
  NOT be used as answer content.

### Key Entities

- **Toolkit release**: title, version, number of sheets and rules, fingerprint of
  the source workbook.
- **Sheet**: title, topic group, kind, sensitivity class, its rows exactly as written.
- **Promise rule**: judged house, connecting houses, extra condition, promised
  matter, category, source sheet and row, sensitivity class.
- **House promise** (per chart, per house): cuspal sub lord, its star lord,
  retrograde flags, connected houses, matched rules with their status.
- **Look-up entry**: horary number row + mindset; nakshatra-quarter row
  (professions / body parts and diseases); planet × house row (signification /
  education stream); house meaning; relationship by cusp; planet friendship;
  planet row (characteristics; direction); zodiac sign row (characteristics).
  *(Clarified 2026-11-23.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % of the workbook's sheets (75 of 75) are stored; every row
  check against the workbook passes.
- **SC-002**: At least 2,000 house-formula rules are available as structured rules,
  each traceable to its sheet and row.
- **SC-003**: 249 of 249 horary rows agree with the engine on sign, star and sub
  lord.
- **SC-004**: 108 of 108 nakshatra-quarters resolve in both star look-ups; 9 of 9
  planets resolve in both the Planet Characteristics and Planet Direction
  look-ups; 12 of 12 zodiac signs resolve in the Zodiac Signs and Characteristics
  look-up. *(Clarified 2026-11-23.)*
- **SC-005**: On 3 documented test charts, the owner — as the reviewing KP
  practitioner — reviewing 10 promise readings per chart agrees that each cited
  rule is correctly applied in at least 9 of 10 cases. No external reviewer or
  confidentiality step is needed. *(Clarified 2026-11-23.)*
- **SC-006**: Re-importing an unchanged workbook produces zero differences.
- **SC-007**: A full 12-house promise reading for one chart is available to the
  agent fast enough that the user's reply is not noticeably delayed (well under one
  second of the reply time).

## Assumptions

- The owner holds a lifetime licence that permits using the toolkit's content
  inside the product (owner-confirmed, 2026-11-22). The workbook itself is not
  stored in the code repository. Only the imported content is.
- "Connecting houses through the star lord of the house sub lord" (the
  1500+Rules column header) is read literally: a multi-house rule needs all its
  houses. ReadyReckoner's "supporting houses" are read the same way.
- Extra conditions (planets, signs, Maraka/Badhaka, aspects, gender) are judged by
  the reader in v1, not automated.
- The one known source error (horary row "L07" for 107) is corrected on import.
  All other text is kept exactly as written, spelling included.
- FR-010, FR-010a, FR-011a and FR-012 are permitted by Constitution v2.0.0
  (Principles V and X, ADR-0024, 2026-11-22). The security protections
  (injection gate, identity isolation, PII, payment gate) are not changed.
- This feature delivers the knowledge base and its chart matching. Wiring it into
  the conversation is done by the topic-gate and agent features (PROJECT_PLAN
  SPEC-011 / SPEC-012, re-ordered 2026-11-22), which consume FR-007's engine-filling
  clause, FR-008, FR-010 and FR-011. FR-011a (the self-harm support line) is
  specifically SPEC-011's responsibility: Constitution V places self-harm detection
  in the input guardrail, before the model is invoked, so it is prepended by the
  topic-gate, not by anything this feature or SPEC-012 builds. FR-012 (a second
  person's birth data or horary number supplied in the conversation) is SPEC-012's
  responsibility: the agent tool that parses the user's message passes the
  resulting chart into this feature's `PromiseEvaluator`, which already accepts any
  `NatalChart` and stores nothing (contracts/core-api.md).
- Depends on the existing chart, significator and horary features (SPEC-002,
  003, 005).
