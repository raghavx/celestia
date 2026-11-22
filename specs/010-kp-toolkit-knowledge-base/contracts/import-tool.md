# Contract: toolkit import tool (SPEC-010 FR-013)

## Command

```bash
pip install -r tools/toolkit-import/requirements.txt
python3 -I tools/toolkit-import/import_toolkit.py <workbook.xlsx> [out_dir]
```

`out_dir` defaults to `core/src/main/resources/com/celestia/core/toolkit`.

| Exit | Meaning |
|------|---------|
| 0 | resources written; prints `toolkit <version>: <n> sheets, <m> rules -> <out_dir>` |
| 2 | no workbook argument (prints usage) |
| non-zero + `manifest out of date: unmapped sheets […], absent sheets […]` | the workbook's sheet names differ from the tool's manifest. Classify the new sheet (group, kind, sensitivity, answerable) in the manifest, then re-run |
| non-zero + `unknown nakshatra label: '…'` | a star-sheet label has no alias. Add it to the alias table |
| non-zero + `249 … incomplete` | the horary sheets don't number 1..249 exactly once |

## Guarantees

- **Deterministic**: the same workbook gives byte-identical output (SC-006).
- **Complete**: one `sheets/<id>.tsv` per workbook sheet, every non-empty row.
- **Verbatim**: cell text is normalised only by NFC, CRLF→LF and trimming each line's
  trailing spaces. The single documented fix-up is research §10.
- **Recorded**: `toolkit.properties` holds title, version, sheet count, rule count and
  the workbook SHA-256.

## Resource formats

See [../data-model.md](../data-model.md). Every TSV has line 1 = a `#` comment
naming the tool, line 2 = the header, then one record per line. Cells escape `\\`,
`\t`, `\n`.

## Operational rule

The workbook is licensed content and is never committed. After a re-import that
changes anything, review the diff rule by rule, then commit. The engine version
changes automatically (research §7).
