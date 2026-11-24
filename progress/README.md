# Celestia progress tracker

A dated series of one-page project maps. Each map follows one WhatsApp message
through the nine stops of the system and shows each stop as **built**,
**in progress** (built but not wired, or spec written but not built), or
**not started**.

Open `index.html` in a browser to see every snapshot, newest first.

## Snapshots

| Date | Built | In progress | Not started | Summary |
|------|------:|------------:|------------:|---------|
| [2026-11-25](2026-11-25.html) | 8 | 0 | 1 | **Stage A is over: 10 of 10 accepted as astrology.** The owner read ten answers drawn from his own chart on `claude-sonnet-5` and rejected none; SC-001's bar was nine. SC-008 measured the same day — **10 of 10 inside 30 seconds, median 14.3s, slowest 15.3s** — so the criterion needed no amendment. SPEC-012 closes at **55/56 tasks, 986 tests green**; T047 stays open *by decision*, after launch. Three defects came from running it, not reviewing it: **Spring AI does not run the tool loop and the stub had emulated one**, leaving 116 tests passing over code that could not have worked; thinking hides the reply entirely on this provider (1,877 output tokens, zero readable characters); and a decline could be assumed rather than earned, so two answerable questions were refused after zero tool calls. The advisory tail was traced to house 1 carrying 12 longevity rules in a system where the Lagna is read every time — **accepted as a measured cost**. Both verdicts bind to the model that earned them. |
| [2026-11-24e](2026-11-24e.html) | 8 | 0 | 1 | **The turn budget is real.** A 120-second deadline used to take 24 minutes; the cause was a default nobody chose — Spring AI retries **ten times**, backing off from 2s with a multiplier of five and **no ceiling**. Now one attempt, and each call is given the turn's *remaining* budget rather than a fresh one, so rounds cannot sum past the deadline — a bound by arithmetic rather than by vigilance. 50/54 tasks, **981 tests green**. Three of ADR-0026's six conditions are mechanical (startup invariant, tool-surface gate, the provider seam); **condition 3 is the one code cannot enforce** and is now **T039a**, blocking T044/T040/T041 because each sends real birth details. Once sent, sent. |
| [2026-11-24d](2026-11-24d.html) | 8 | 0 | 1 | **The hardware stopped being the blocker.** Constitution v2.3.0 separated deployment from development and defined the term by reachability; v3.0.0 then permitted a one-person deployment on a third-party API under six conditions — the first enforced at startup, so admitting a second person needs an amendment rather than a property edit. A provider seam makes the model configuration. SPEC-012 holds at 47/51 with **974 tests green across the reactor** and SC-013 verified against a real MySQL. Two things now gate the practitioner's verdict: **T045** (the turn deadline is a checkpoint, not a bound — a 120-second budget ran 24 minutes) and **ADR-0026 condition 3** (no training on prompts, minimum retention, a DPA — unchecked). Also resolved: the supplied-person-chart question, where wiring a geocoder would have *ended* the permission rather than completing the requirement. |
| [2026-11-24c](2026-11-24c.html) | 8 | 0 | 1 | SPEC-012 built: 47/51 tasks, 3,028 new lines in `agent`, 108 unit + 52 integration tests green, the echo deleted. Entitlement, the turn permit, 11 tools, the reference swap (73 sources behind opaque references, asserted on what the model received), the turn record with its "forget me" delete, and the reply clamp. **No real answer yet**: the local 0.5B model took ~24 minutes a question and faulted on three of four, so T040/T041/T044 wait on a rented GPU. Four bugs only running the code could find — tool arguments silently null without `-parameters`, an unregistered properties record, a `@Lob` column mapping to 255 bytes, and a turn deadline that does not bound a turn (T045). |
| [2026-11-24b](2026-11-24b.html) | 6 | 0 | 3 | SPEC-011 built: 45/46 tasks, 2,166 lines of main code, 125 tests, zero answerer invocations across the adversarial corpus, 281µs gate decisions. Three spec claims falsified by running the code — sheet ids that are ordinary English (withholding every refusal), a stopword list 50× too small, and a crisis rule firing on "when will I die?". T043, the owner's corpus read, is the one task left. |
| [2026-11-24](2026-11-24.html) | 5 | 1 | 3 | SPEC-011 planned to the task: research, data model, 2 contracts, quickstart, 46 tasks. Five analyze passes, 22 findings → clean; two were real (a refused crisis message got no support line; FR-017's "another user's data" limb had no mechanism). Multilingual revised to English-only, Hindi/Hinglish parked. No code since 23b. |
| [2026-11-23c](2026-11-23c.html) | 5 | 1 | 3 | SPEC-011 (guardrail core) specified: 6 stories, 20 requirements, 9 criteria. Two owner decisions — multilingual gate (English/Hindi/Hinglish) and fail-closed on unmatched messages — which compound, so the in-scope corpus is required in all three registers and unmatched refusals counted separately. No code since 23b. |
| [2026-11-23b](2026-11-23b.html) | 5 | 0 | 4 | The toolkit is built: import tool, `core.toolkit`, 72 tests, and the licensed workbook imported (75 sheets, 2,001 rules, 11 look-ups). SC-001–SC-004, SC-006, SC-007 verified against the real file; SC-005 awaits the practitioner review. Replies still echo. |
| [2026-11-23](2026-11-23.html) | 4 | 1 | 4 | SPEC-010 fully planned: research, data model, contracts, 48-task breakdown. Four analyze passes and two clarification rounds closed every gap. Constitution v2.0.0 → v2.1.0 (ADR-0025): toolkit is internal-only, agent is the astrologer. Still no code. |
| [2026-11-22b](2026-11-22b.html) | 4 | 1 | 4 | Evening, after the re-order: constitution v2.0.0 (toolkit is the source of KP judgement), ADR-0024, SPEC-010 spec written, roadmap in stages A–C with specs 010–023. The earlier toolkit code was discarded; SPEC-010 is spec-only. |
| [2026-11-22](2026-11-22.html) | 4 | 1 | 4 | Engine (SPEC-001–006), geo (007), persistence (008) and WhatsApp in/out (009) built. Toolkit (018) added but not wired. Agent (015), onboarding (010), payments (011–013) and topic gate (014) not started. Replies are still an echo. |

## Adding a snapshot

1. Copy the latest map to a new file named with today's date: `cp 2026-11-22b.html YYYY-MM-DD.html`.
   A second snapshot on the same day gets a letter suffix (`YYYY-MM-DDb.html`).
2. In the new file, update the snapshot date line, the headline and the three arrays
   in the script at the bottom: `st` (the nine stops: class `done`, `wired`, `todo`,
   plus `gap` on the next thing to build), `mods` (modules and line counts) and
   `adrs` (open decisions).
3. Add a row to `SNAPSHOTS` in `index.html` and to the table above.
4. Never edit an old snapshot. The series is only useful if each map shows what
   was true on its date.

Line counts per module:

```bash
for m in ephemeris core geo agent channel-whatsapp guardrail billing payments-razorpay; do
  printf "%s main=%s test=%s\n" $m \
    $(find $m/src/main -name '*.java' | xargs cat 2>/dev/null | wc -l) \
    $(find $m/src/test -name '*.java' 2>/dev/null | xargs cat 2>/dev/null | wc -l)
done
```
