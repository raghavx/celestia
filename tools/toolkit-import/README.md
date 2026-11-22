# toolkit-import

Turns the licensed *Prediction ToolKit for Advanced* workbook into the committed
TSV resources that `com.celestia.core.toolkit` reads (SPEC-010).

A **developer tool**. Not built by `./mvnw verify`, not shipped, and run once per
toolkit release — the same place `tools/ephe-crosscheck` sits.

## The workbook is never committed

The workbook is licensed content. It lives outside the repository; only its
*imported content* is committed, as reviewable generated data. That is also why
most of SPEC-010's tests run against synthetic fixtures: everything that does
not need the licensed text is testable without it, so `./mvnw verify` is green
on a fresh clone.

## Use

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r tools/toolkit-import/requirements.txt

python3 -I tools/toolkit-import/import_toolkit.py ~/Documents/Prediction_ToolKit_for_Advanced.xlsx
```

`out_dir` defaults to `core/src/main/resources/com/celestia/core/toolkit`. On
success it prints, for example:

```text
toolkit 2026-09-12: 75 sheets, 2008 rules -> core/src/main/resources/com/celestia/core/toolkit
```

## Exit codes

| Exit | Meaning |
|------|---------|
| 0 | resources written |
| 2 | no workbook argument (prints usage) |
| 1 | a fatal, actionable problem — see below |

The exit-1 messages each name their own fix:

| Message | What to do |
|---------|------------|
| `manifest out of date: unmapped sheets [...], absent sheets [...]` | The workbook's sheet names differ from `manifest.py`. Classify the new sheet there (id, group, kind, sensitivity, answerable), or correct a renamed title, then re-run. |
| `unknown nakshatra label: '...'` | A star sheet spells a nakshatra a way the alias table does not cover. Add it to `NAKSHATRA_ALIASES`. |
| `unknown planet label: '...'` / `unknown sign label: '...'` | Same, for `GRAHA_ALIASES` / `SIGN_ALIASES`. |
| `249 ... incomplete` | The horary sheets do not number 1..249 exactly once. |
| `... expected N keyed rows, found M` | A single-key look-up sheet does not cover every planet or sign exactly once. |

The guards are the point: a silently-missing sheet or an unrecognised spelling
would leave the product answering from part of the toolkit while believing it
held all of it. **Fix the manifest or the alias table — never the guard.**

## What it writes

```text
core/src/main/resources/com/celestia/core/toolkit/
├── toolkit.properties     # title, version, sheet + rule counts, workbook SHA-256
├── manifest.tsv           # one row per sheet: id, title, group, kind, sensitivity, answerable
├── sheets/<id>.tsv        # every sheet, every non-empty row, verbatim
├── rules/promise-rules.tsv
└── tables/                # 11 typed look-ups (horary 249, star, planet, sign, house)
```

Every TSV is line 1 a `#` comment, line 2 the header, then one record per line.
Cells escape `\\`, `\t` and `\n`. See `specs/010-kp-toolkit-knowledge-base/data-model.md`.

## Guarantees

- **Deterministic** — the same workbook gives byte-identical output, so a
  re-import of an unchanged file produces an empty `git diff` (SC-006).
- **Complete** — one file per sheet, every non-empty row (FR-001).
- **Verbatim** — cell text is normalised only by NFC, CRLF&rarr;LF and trimming
  each line's trailing spaces. Rules are never paraphrased into new conditions
  (Constitution X). The single documented fix-up is research.md §10: the
  249Mindsets row mis-numbered "L07" is horary 107.
- **Recorded** — `toolkit.properties` carries the workbook's SHA-256, so a stored
  reading can be traced to the exact file it came from even though that file is
  not in the repository.

## After a re-import

1. `git diff core/src/main/resources/com/celestia/core/toolkit` — review it
   **rule by rule**. This is the point at which a toolkit change gets human
   review; the diff is one rule per line so that it can.
2. `./mvnw -pl core -am test -Dtest='com.celestia.core.toolkit.*Test'`
3. Commit.

The engine version changes automatically: `agent`'s `CurrentEngineVersion`
composes `kp-N+toolkit-<release>`, so every cached chart and reading computed
under the old toolkit is recomputed (research.md §7, FR-009).

## Note on `manifest.py`

The sheet titles there were taken from the spec's analysis of the workbook, not
read off the real file — the file is not committed, so they could not be. The
drift guard exists precisely to catch that: the owner's first real import will
report any title that differs, and the fix is to correct `manifest.py`.
