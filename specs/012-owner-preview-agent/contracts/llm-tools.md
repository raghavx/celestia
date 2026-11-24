# Contract: the tool surface the model sees (SPEC-012)

Two adapter classes expose everything the model may call. If it is not here, the
model cannot reach it.

## Shared rules, enforced by the adapters rather than asked for in the prompt

1. **No `@Tool` method accepts `userId`, a coordinate or a time.** All three are
   injected from the authenticated sender (Constitution V, FR-006), through the
   per-turn `TurnScope` bound to the answering thread. A model that tries to pass
   someone else's identity has nowhere to put it.
1a. **Arguments bind by parameter name**, so `agent` is compiled with
   `-parameters` (`maven.compiler.parameters=true`). `@ToolParam` has no name
   attribute; without the flag every argument arrives null and a tool answers a
   question the model never asked.
2. **Every result is rewritten before serialisation.** Each `source`
   (`{sheetId, sheetTitle, row}`) becomes a per-turn reference (`s1`, `s2`), and the
   real values go to the turn record. The model never receives a toolkit string
   (FR-013a).
3. **Read-only.** No tool mutates anything.
4. **Bounded.** SPEC-010's caps apply (≤ 40 hits, ≤ 4,000 characters of sheet
   text) with a `truncated` flag, so a result cannot blow the reply budget or
   the context.

## `ToolkitTools` — the five of SPEC-010 `agent-tools.md`

| Tool | Input the model gives | What it gets back |
|------|----------------------|-------------------|
| `getChartPromises` | `house?`, `includePartial?` | per house: cusp sub lord, star lord, retrograde flags, connected houses, hits grouped by matter with `status`, `formula`, `condition`, `sensitivity`, and **references** in place of sources |
| `findToolkitRules` | `matter` | rules with judged house, houses, condition, matter, **reference** |
| `lookupToolkit` | `topic` | up to 5 answerable sheets — **titles are not returned to the model**; only the group, kind and matching row text, with references |
| `getToolkitLookup` | `point`, `table` | the keyed row, with its reference |
| `getHoraryMindset` | `number` 1..249 | sign, star, lords, mindset, reference |

**`lookupToolkit` needs the most care.** Its SPEC-010 shape returns sheet
`{id, title}`, which is exactly what must not reach the model. The adapter
returns the group, the kind and the matching rows, with the identity behind a
reference. A tool that hands over sheet titles would defeat the reference swap in one
call.

## `EngineTools` — the deterministic core

| Tool | Input | What it gets back |
|------|-------|-------------------|
| `getNatalChart` | — (the owner's own) | cusps, planets, star and sub lords |
| `getRunningDasha` | — | the current mahadasha/bhukti with dates |
| `getSignificators` | `house` | the significators for that house |
| `getRulingPlanets` | — | the ruling planets now, clock injected |
| `castHorary` | `number` 1..249 | the horary chart for that number |
| `castChartFor` | birth date, time, place supplied **in this message** | **currently returns `{available: false, note}`** — see below |

These carry no toolkit source, so no reference applies. They exist so the model never
computes: Constitution IV is enforced by there being a tool for every number and
no arithmetic the model could do instead.

**`castChartFor` is not available yet, and says so rather than guessing.** Casting
a chart from a place *name* needs a live geocoder and a zone resolution; `geo`
defines the port (SPEC-007) and nothing implements it, and the model must never be
handed coordinates to supply itself (FR-006, rule 1). So the tool returns
`available: false` with a note the model can read, and the agent declines in its
own voice. FR-011's plumbing is built and tested around it — a supplied chart is
what the tools judge for that turn, is never persisted, and this tool's arguments
are redacted from the turn record — but **US1 scenario 4 is not demonstrable until
a geocoder exists.**

Because these carry no source, an engine call alone never grounds an
interpretation. SC-002 counts `ToolkitTools` calls, and the eval harness asserts
on those.

## What the model may not call

Anything else. No HTTP, no file access, no database, no `castChartFor` on a
stored user other than the sender, no tool that returns a sheet id or title.
