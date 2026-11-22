# Contract: toolkit tools for the LLM agent (consumed by SPEC-012)

This spec delivers the core behaviour. SPEC-012 exposes it as `@Tool` methods.
Shared rules for all five tools:

- **Read-only.** `userId`, coordinates and clock are injected server-side (Constitution V).
- **Every result item carries `source`** (`{sheetId, sheetTitle, row}`) and
  `sensitivity`. The answer MUST cite the source of every interpretation it uses
  (Constitution X, FR-008).
- **Advisory sentence.** The output stage (SPEC-011) appends `sensitivity.note()` for
  the most severe class used, once per reply. The model never writes it.
- **Bounded size.** Results are capped (≤ 40 hits, ≤ 4,000 characters of sheet text)
  to fit the WhatsApp reply budget. A `truncated` flag says when more exists.

| Tool | Input | Output |
|------|-------|--------|
| `getChartPromises` | `house?` (1..12, default all), `includePartial?` (default false), `chart` = the user's natal chart, or a chart cast from birth data the user supplied this turn | per house: `cuspSubLord`, `starLord`, retrograde flags, `connectedHouses`, hits grouped by `matterKey` → `{matter, status, formula, condition, sources[]}`, `sensitivity` |
| `findToolkitRules` | `matter` text | rules `{judgedHouse, houses, condition, matter, source}`. The agent then calls `getChartPromises(house)` to judge them |
| `lookupToolkit` | `topic` text | up to 5 answerable sheets `{id, title, group, kind, matchingRows}`; methods return all rows in order |
| `getToolkitLookup` | `point` (planet name or `cusp:N`), `table` ∈ {profession, disease, signification, education} | the row keyed by that point's nakshatra-quarter (profession, disease) or bhava (signification), or by the 9th CSL's star lord and its house (education) |
| `getHoraryMindset` | `number` 1..249 | `{sign, star, signLord, starLord, subLord, mindset, source}` |

## Answer obligations (checked by the SPEC-012 eval harness)

1. A rule with status CONDITION_TO_CHECK is never stated as a firm promise.
2. PARTLY_INDICATED is said as "partly indicated" or not mentioned.
3. Hits sharing a `matterKey` are reported once, with all their sources.
4. A retrograde CSL or star lord is mentioned as weakening or delaying the result.
5. If no toolkit rule or look-up covers the question, the answer says the toolkit does
   not cover it. It never improvises KP judgement.
