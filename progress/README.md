# Celestia progress tracker

A dated series of one-page project maps. Each map follows one WhatsApp message
through the nine stops of the system and shows each stop as **built**,
**in progress** (built but not wired, or spec written but not built), or
**not started**.

Open `index.html` in a browser to see every snapshot, newest first.

## Snapshots

| Date | Built | In progress | Not started | Summary |
|------|------:|------------:|------------:|---------|
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
