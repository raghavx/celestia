# Contract: toolkit tools for the LLM agent (consumed by SPEC-012)

This spec delivers the core behaviour. SPEC-012 exposes it as `@Tool` methods.
Shared rules for all five tools:

- **Read-only.** `userId`, coordinates and clock are injected server-side (Constitution V).
- **Every result item carries `source`** (`{sheetId, sheetTitle, row}`) and
  `sensitivity`, so every interpretation the answer uses is traceable (Constitution
  X, FR-008). **This is internal only** — the toolkit is a licensed asset used to
  build Celestia, not something the user has a relationship to. `source` is for
  the eval harness, logging and human review; the reply to the user MUST NEVER
  name the toolkit, a sheet's id/title, or a row number.
- **Advisory sentence.** The output stage (SPEC-011) appends `sensitivity.note()` for
  the most severe class used, once per reply. The model never writes it.
- **Bounded size.** Results are capped (≤ 40 hits, ≤ 4,000 characters of sheet text)
  to fit the WhatsApp reply budget. A `truncated` flag says when more exists.

| Tool | Input | Output |
|------|-------|--------|
| `getChartPromises` | `house?` (1..12, default all), `includePartial?` (default false), `chart` = the user's natal chart, or a chart cast from birth data the user supplied this turn | per house: `cuspSubLord`, `starLord`, retrograde flags, `connectedHouses`, hits grouped by `matterKey` → `{matter, status, formula, condition, sources[]}`, `sensitivity` |
| `findToolkitRules` | `matter` text | rules `{judgedHouse, houses, condition, matter, source}`. The agent then calls `getChartPromises(house)` to judge them |
| `lookupToolkit` | `topic` text | up to 5 answerable sheets `{id, title, group, kind, matchingRows}`; methods return all rows in order |
| `getToolkitLookup` | `point` (planet name, `cusp:N`, or a zodiac sign name), `table` ∈ {profession, disease, signification, education, planet-characteristics, direction, zodiac-characteristics} | **chart-grounded** (`point` resolves through the user's own chart first): the row keyed by that point's nakshatra-quarter (profession, disease) or bhava (signification), or by the 9th CSL's star lord and its house (education). **Direct reference, no chart involved** (`point` is used as a literal key): the row for that planet alone (`planet-characteristics`, `direction`) or that sign alone (`zodiac-characteristics`) — e.g. Mars's characteristics are the same answer for every user *(2026-11-23 clarification, FR-006)* |
| `getHoraryMindset` | `number` 1..249 | `{sign, star, signLord, starLord, subLord, mindset, source}` |

## Answer obligations (checked by the SPEC-012 eval harness)

1. A rule with status CONDITION_TO_CHECK is never stated as a firm promise.
2. PARTLY_INDICATED is said as "partly indicated" or not mentioned.
3. Hits sharing a `matterKey` are reported once, with all their sources.
4. A retrograde CSL or star lord is mentioned as weakening or delaying the result.
5. If no toolkit rule or look-up covers the question, the answer says it doesn't
   have a KP basis for that (without naming "the toolkit"). It never improvises
   KP judgement.
6. The reply never mentions the toolkit, a sheet id or title, or a row number —
   only the astrological basis (house, sub lord, star lord, matter).
