# core

Pure-Java KP domain. No Spring, no DB, no network, no wall clock (enforced by
`DeterminismArchitectureTest`). SPEC-001 delivered: longitude decomposition and
the Vimshottari partition. Significators, ruling planets, the dasha timeline, and
horary come in later specs.

## Public API

| Type | Purpose |
|------|---------|
| `KpLordage.chainFor(double)` | any longitude -> `LordChain` (sign / sign lord / nakshatra / pada / star / sub / sub-sub lord) |
| `VimshottariPartition.subs(Nakshatra)` / `.subSubs(Nakshatra, Graha)` / `.subDivisions()` | exact `BigFraction` sub / sub-sub spans |
| `Sign` / `Nakshatra` / `Longitudes` | 12 signs, 27 nakshatras, normalisation + the half-open boundary rule |
| `Span` | one ruled arc with exact rational endpoints |

`org.apache.commons.numbers.fraction.BigFraction` is part of the public API
(`Span.start()/end()`) — see `specs/001-ephemeris-primitives/contracts/`.

## Correctness

- `core/REFERENCES.md` — a source for every KP rule + the `EngineVersion` bump procedure.
- Golden charts: `src/test/resources/golden/` (+ `README.md`). `GoldenChartTest`
  checks every graha of every chart against an independent pyswisseph computation.
- Regenerate reference values: `tools/ephe-crosscheck/`.

## Adding a golden chart

1. Create `src/test/resources/golden/<id>.json` with birth data + citation
   (see the golden `README.md` format).
2. `cd tools/ephe-crosscheck && CELESTIA_EPHE_PATH=... python compute_golden.py <path> --write`
3. Add `<id>` to `GoldenChart.loadAll()`.
