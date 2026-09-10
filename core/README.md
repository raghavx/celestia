# core

Pure-Java KP domain. No Spring, no DB, no network, no wall clock (enforced by
`DeterminismArchitectureTest`). Delivered: longitude decomposition and the
Vimshottari partition (SPEC-001), the natal chart with cusps and bhavas
(SPEC-002), the four-step significators and ruling planets (SPEC-003), the
Vimshottari dasha timeline (SPEC-004), the KP horary 1–249 chart (SPEC-005), the
daily-prediction ruleset (SPEC-006).

## Public API

| Type | Purpose |
|------|---------|
| `KpLordage.chainFor(double)` | any longitude -> `LordChain` (sign / sign lord / nakshatra / pada / star / sub / sub-sub lord) |
| `VimshottariPartition.subs(Nakshatra)` / `.subSubs(Nakshatra, Graha)` / `.subDivisions()` | exact `BigFraction` sub / sub-sub spans |
| `Sign` / `Nakshatra` / `Longitudes` | 12 signs, 27 nakshatras, normalisation + the half-open boundary rule |
| `Span` | one ruled arc with exact rational endpoints |
| `chart.NatalChartFactory` | `assemble(BirthData, EphemerisResult, HouseResult)` / `cast(BirthData)` -> `NatalChart` |
| `chart.NatalChart` | positions + 12 `Cusp`s + `AnglePoint`s + `HousePlacement`s (bhava + rasi house); `cuspSubLord(h)` |
| `chart.Bhavas` | `bhavaOf(longitude, cusps)` (cusp-to-cusp), `rasiHouseOf(grahaSign, ascSign)` |
| `judgement.SignificatorTable.of(NatalChart)` | the four-step significators: `houseSignificators(h)` &times;12, `grahaSignificators(g)` (transpose), `nodeAgency(RAHU/KETU)` |
| `judgement.RulingPlanetsFactory` | `at(BirthData)` / pure `compute(...)` -> `RulingPlanets` (lagna + Moon lords, day lord, node agency) |
| `judgement.KpWeekday.resolve(...)` | the sunrise-to-sunrise KP weekday + day lord, with a civil-day fallback |
| `judgement.Step` / `Significator` / `HouseSignificators` / `GrahaSignificators` / `NodeAgency` / `RpSource` / `RulingPlanet` | value objects |
| `dasha.DashaTimelineFactory.at(BirthData)` / `dasha.DashaTimeline.from(...)` | the Vimshottari timeline: `balanceAtBirth()`, `running(instant, depth)`, `periods(level, from, to)` |
| `dasha.VimshottariSplit.of(BigFraction, Graha)` | the exact nine-way weight split (shared by the nakshatra sub-lords and the dasha subdivision) |
| `dasha.DashaLevel` / `DashaPeriod` / `DashaBalance` / `RunningDasha` | value objects (5 levels: Mahadasha &rarr; Antardasha &rarr; Pratyantardasha &rarr; Sookshma &rarr; Prana) |
| `horary.Horary249.arc(int)` / `.arcs()` | the KP horary 1–249 map — number &rarr; `HoraryArc` (bounds, sign, lord chain; sub lord = the answer's determinant) |
| `horary.HoraryChartFactory` | `ascendant(number)` (clock-independent) / `cast(number, BirthData)` &rarr; `NatalChart` (cusp 1 = the number's Ascendant) |
| `horary.HoraryRulingPlanets.at(...)` | SPEC-003 ruling planets with the number's Ascendant for the lagna lords |
| `prediction.HouseGroups.fromKey(String)` / `prediction.Matter` | the KP house-group taxonomy — `explainHouseGrouping` (matter &rarr; favourable / obstructive houses) |
| `prediction.DailyPredictionFactory.predict(NatalChart, LocalDate, longitude)` | the v1 daily reading — running lords &cap; significators, the sub-lord transit rule, a per-matter `Verdict` + its evidence |
| `prediction.DailyPrediction` / `MatterVerdict` / `DashaSignificators` / `TransitContribution` | structured value objects (no prose — Constitution IV) |

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
