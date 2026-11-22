# KP rule sources (`core` + `ephemeris`)

Constitution III: every KP rule in code names an authoritative source. ADR
references are acceptable where an ADR settled the choice.

| Rule | Where | Source |
|------|-------|--------|
| Ayanamsa = Swiss Ephemeris `SE_SIDM_KRISHNAMURTI` (5), not VP291 | `ephemeris.Ayanamsa`, `SwissEphemerisPositionProvider` | ADR-0003 |
| Rahu/Ketu from the **mean** node; Ketu = Rahu + 180&deg; | `SwissEphemerisPositionProvider` | ADR-0005; Krishnamurti, *KP Readers* (classic KP uses the mean node) |
| &Delta;T model | `TimeScales` | Swiss Ephemeris built-in (Espenak-Meeus); tagged `se-builtin` in `EngineVersion` |
| Supported full-accuracy range 1800-2100 | `SwissEphemerisConfig` | ADR-0002 |
| Vimshottari order (Ketu, Venus, Sun, Moon, Mars, Rahu, Jupiter, Saturn, Mercury) and year weights (7, 20, 6, 10, 7, 18, 16, 19, 17; total 120) | `ephemeris.Graha` | Standard Vimshottari dasha table; e.g. *Brihat Parasara Hora Sastra* ch. on Vimshottari dasa; used unchanged by KP |
| 12 signs, 30&deg; each, classical seven-planet rulership (Ari/Sco&rarr;Mars, Tau/Lib&rarr;Venus, Gem/Vir&rarr;Mercury, Can&rarr;Moon, Leo&rarr;Sun, Sag/Pis&rarr;Jupiter, Cap/Aqu&rarr;Saturn) | `core.lordage.Sign` | Standard Jyotisha sign lordships; KP uses no outer-planet co-rulers |
| 27 nakshatras, 13&deg;20' each from 0&deg; Aries; star lord = Vimshottari lord for position in the 9-cycle (Ashwini&rarr;Ketu, &hellip; repeating 3&times;) | `core.lordage.Nakshatra` | Standard nakshatra-lord assignment (Vimshottari) |
| Pada = quarter of a nakshatra, 3&deg;20' each | `core.lordage.Nakshatra` | Standard |
| Sub lord: nakshatra divided into 9 parts in Vimshottari order **from the star lord**, widths proportional to the dasha years | `core.dasha.VimshottariPartition` | K. S. Krishnamurti, *Krishnamurti Padhdhati / KP Readers* (the sub-division that defines KP) |
| Sub-sub lord: each sub divided the same way, in Vimshottari order from the sub lord | `core.dasha.VimshottariPartition` | KP practice (4th level of the significator hierarchy) |
| Half-open `[start, end)` boundary convention, boundary to the higher division | `core.lordage.Longitudes`, `Span` | Design decision (research.md §5); matches common KP software |
| 243 sub divisions tile the zodiac; the KP **249** horary table adds the splits at the 12 sign boundaries | `VimshottariPartition.subDivisions()` | KP horary (249) system; the split itself is SPEC-005 |
| **Placidus** house cusps, sidereal, KP-New ayanamsa; polar limit 66° (config) | `SwissEphemerisHouseProvider`, `SwissEphemerisConfig.polarLimit` | ADR-0004; Placidus is undefined above the Arctic/Antarctic circle (~66°34′) |
| Cusp 1 = the Ascendant (bit-identical) | `SwissEphemerisHouseProvider` | SPEC-002 FR-003 |
| **Bhava** (cusp-to-cusp): a graha is in bhava _n_ iff its longitude lies in the forward arc `[cusp n, cusp n+1)` — half-open, wrap-aware. Not the Sripati midpoint method. | `core.chart.Bhavas.bhavaOf` | K. S. Krishnamurti, *KP Readers* (the cuspal system) |
| **Rasi house** (display): whole signs from the Ascendant's sign, which is house 1 | `core.chart.Bhavas.rasiHouseOf` | standard whole-sign Rasi layout |
| **Four-step significators** of a house: (1) grahas in the star of an effective occupant, (2) effective occupants, (3) grahas in the star of the house owner, (4) the house owner; strength decreases 1&rarr;4; a graha keeps every step that qualified it | `core.judgement.SignificatorTable`, `Step` | K. S. Krishnamurti, *KP Readers* VI (four-fold significator hierarchy) |
| **Effective occupants**: the occupants of a bhava plus, for a node in that bhava, its **agents** = non-node grahas conjoined in the same bhava &cup; the lord of the node's sign &cup; the lord of the node's star | `core.judgement.SignificatorTable`, `NodeAgency` | K. S. Krishnamurti, *KP Readers* (Rahu/Ketu act as agents of the planets they represent) |
| **Per-graha significators** = the strict transpose of the twelve per-house lists (no independent rule) | `core.judgement.SignificatorTable.grahaSignificators` | KP practice (the significator table read either way) |
| **Ruling planets** for a moment: lagna sign/star/(sub) lords, Moon sign/star/(sub) lords, the day lord; Rahu/Ketu added when the node's sign or star lord is already ruling, or the node shares the sign or nakshatra of the Moon or the Ascendant. Sub-lord inclusion is a switch (default on = modern KP) | `core.judgement.RulingPlanetsFactory`, `RpSource` | K. S. Krishnamurti, *KP Readers* VI &amp; horary method; sub-lord inclusion per modern KP |
| **Day lord** = lord of the KP weekday, which runs from local **sunrise** to the next local sunrise (not civil midnight); Sun&rarr;Sunday &hellip; Saturn&rarr;Saturday. No sunrise that day &rarr; civil (LMT) weekday + fallback flag | `core.judgement.KpWeekday`, `ephemeris.SunriseProvider` | K. S. Krishnamurti, *KP Readers* (Hindu day = sunrise to sunrise) |
| **Vimshottari Mahadasha lengths** (Ketu 7, Venus 20, Sun 6, Moon 10, Mars 7, Rahu 18, Jupiter 16, Saturn 19, Mercury 17; total 120 years) and the fixed cyclic order | `core.dasha.DashaTimeline`, `ephemeris.Graha` | Standard Vimshottari table (as SPEC-001); K. S. Krishnamurti, *KP Readers* II |
| **Balance of dasha at birth**: the birth Mahadasha lord is the lord of the Moon's nakshatra; the fraction of the nakshatra the Moon has traversed is the fraction of that Mahadasha already elapsed | `core.dasha.DashaTimeline.balanceAtBirth` | K. S. Krishnamurti, *KP Readers* II (balance of dasa) |
| **Nested subdivision**: each period splits into nine, in Vimshottari order **from that period's own lord**, each child = parent &times; childYears / 120; five levels Mahadasha &rarr; Antardasha (Bhukti) &rarr; Pratyantardasha (Antara) &rarr; Sookshma &rarr; Prana | `core.dasha.VimshottariSplit`, `DashaLevel` | K. S. Krishnamurti, *KP Readers* II; standard Vimshottari sub-period rule |
| **1 Vimshottari year = 365.25 days** (Julian year) for converting dasha lengths to dates | `core.dasha.DashaTimeline` | K. S. Krishnamurti, *KP Readers* dasa tables; the mainstream KP-software convention (`specs/004-vimshottari-dasha/research.md` §7) |
| **KP horary 1–249 table**: the 243 nakshatra sub-lord divisions, each split wherever it crosses a 30&deg; sign boundary, numbered in longitude order — exactly 249 arcs | `core.horary.Horary249` | K. S. Krishnamurti, *Krishnamurti Padhdhati* (horary / *Prasna* volume); the 249 table |
| **Horary Ascendant = the midpoint of the number's arc** — the sub lord (the judged quantity) is constant over the arc, so the choice is judgement-neutral | `core.horary.HoraryChartFactory.ascendant` | KP horary practice (the number fixes the Ascendant sub) |
| **Horary house cusps**: cusp 1 = the number's Ascendant; cusps 2–12 = Placidus from the RAMC that yields that Ascendant at the judgment latitude — closed-form inversion of the Ascendant equation, sidereal&harr;tropical via the instant's ayanamsa | `ephemeris.swisseph.SwissEphemerisHoraryHouseProvider` | J. Meeus, *Astronomical Algorithms* (2nd ed.) ch. 13 & the house-cusp formulae |
| **Daily-prediction house groups** (`v1`): per matter a favourable house set and an obstructive set (the 12th-from-each-favourable "negation" houses) — marriage 2/7/11 vs 1/6/10, career 2/6/10/11 vs 1/5/9/12, &hellip; | `core.prediction.Matter` | K. S. Krishnamurti, *KP Readers* III–IV (house significations); the 12th-from negation is standard KP. **v1** — see `specs/006-daily-prediction/research.md` §1 |
| **Dasha significators** for a date: the five running Vimshottari lords (SPEC-004) &cap; each lord's natal `grahaSignificators` (SPEC-003); a house's activation strength = the count of distinct running lords that signify it | `core.prediction.DashaSignificators` | KP timing practice (the dasha lord must be a significator of the matter) |
| **v1 transit rule**: a transiting body **supports** house H iff its **sub lord** is a natal significator of H; only the Moon (day) and the Sun (fortnight) in v1 | `core.prediction.TransitContribution` | K. S. Krishnamurti (transit through the sub of a significator); **v1** — star-lord agreement / slow planets / aspects are v2 |
| **Reference instant** for a daily reading = **local noon** of the date (`date 12:00 − longitude/15 h`); the reading flags any within-day change of a running lord or the Moon's sub lord | `core.prediction.DailyPredictionFactory` | **v1 design choice** — the true civil timezone is SPEC-007 |
| **v1 verdict rule**: `QUIET` if neither the matter's favourable nor obstructive houses are dasha-activated; `UNFAVOURABLE` if `Σ obstructive-strength > Σ favourable-strength` (strict); `FAVOURABLE` if a favourable house is activated **and** transit-triggered and not outweighed; `MIXED` otherwise | `core.prediction.VerdictRule` | **v1 design choice** — see `specs/006-daily-prediction/research.md` §5 |

| **Toolkit promise rule**: the cuspal sub lord of house H, *through its star lord*, connects to houses X, Y &rArr; the promised matter | `core.toolkit.PromiseRule` | Licensed *Prediction ToolKit for Advanced* (1500+Rules and the rest of its rule book) — the column header is read literally; see `specs/010-kp-toolkit-knowledge-base/research.md` §2 |
| **"Connected through the star lord"**: the CSL is connected to house *h* iff the CSL signifies *h* at step `STAR_OF_OCCUPANT` (its star lord occupies *h*) or `STAR_OF_OWNER` (its star lord owns *h*) — the toolkit's ABCD "A + C" for the CSL. Not all four steps: crediting the CSL's own occupancy/ownership contradicts the rule book's own header | `core.toolkit.PromiseEvaluator` | Licensed toolkit (1500+Rules column header; ABCD Significance sheet); `specs/010-kp-toolkit-knowledge-base/research.md` §3 |
| **All-or-nothing matching**: a multi-house rule needs **every** house connected — *promised* with no extra condition, *condition to check* when the author added one, *partly indicated* when only some connect (returned on request only, never as a promise) | `core.toolkit.PromiseEvaluator`, `PromiseHit.Status` | The toolkit writes a combination as one condition; `specs/010-kp-toolkit-knowledge-base/research.md` §4 |
| **Sensitivity classes** `NONE < REMEDY < FINANCE < HEALTH < LONGEVITY`, each with one fixed advisory sentence; a sheet's default is raised (never lowered) by keywords in a rule's own wording. Self-harm rules fall in HEALTH like any other — no separate class | `core.toolkit.Sensitivity` | Product policy, not KP: Constitution V and X, ADR-0024; `specs/010-kp-toolkit-knowledge-base/research.md` §5, FR-010a / FR-011 |
| **Toolkit horary 249 table** is cross-checked against the engine's `Horary249` lord for lord (249/249); the one documented source fix-up is the 249Mindsets row mis-numbered "L07", which is horary 107 | `core.toolkit.HoraryMindset`, `ToolkitHoraryCrossCheckTest` | Licensed toolkit (249SubDivisionsHoraryNumbers, 249Mindsets); `specs/010-kp-toolkit-knowledge-base/research.md` §10 |
| **Toolkit look-up keys**: nakshatra + quarter (professions, diseases), planet &times; house (signification, education stream), ordered planet pair (friendship), house (meaning, relationship), and planet or sign alone (characteristics, direction) | `core.toolkit.KpToolkit` | Licensed toolkit's look-up sheets; `specs/010-kp-toolkit-knowledge-base/research.md` §6 and §12 |
| **Toolkit version joins the engine version**: `rules = EngineVersion.RULES + "+" + toolkit-<release>`, composed in `agent` because `ephemeris` cannot depend on `core`. A toolkit release therefore invalidates every cached chart and reading | `agent.persistence.CurrentEngineVersion`, `core.toolkit.ToolkitVersion` | `specs/010-kp-toolkit-knowledge-base/research.md` §7; Constitution III |

## Engine version bump procedure

`EngineVersion.rules` (currently `"kp-1"`, in `ephemeris`) is bumped **by hand**
whenever any rule above changes -- including rules that live in `core`
(`Sign`, `Nakshatra`, `VimshottariPartition`, `KpLordage`, `Longitudes`,
`judgement.SignificatorTable`, `judgement.RulingPlanetsFactory`,
`judgement.KpWeekday`, `dasha.VimshottariSplit`, `dasha.DashaTimeline` — including
the 365.25-day year — `horary.Horary249` / `horary.HoraryChartFactory`, plus
`ephemeris.swisseph.SwissEphemerisHoraryHouseProvider`'s RAMC inversion, and the
whole `prediction.*` v1 ruleset — `Matter`, `VerdictRule`,
`DailyPredictionFactory`).

The `core.toolkit` package is the exception that needs **no** hand bump: a
toolkit release changes `KpToolkit.bundled().version()`, which `agent`'s
`CurrentEngineVersion` already appends to `rules` (`kp-1+toolkit-2026-09-12`).
A change to the toolkit *matching rules* in code — `PromiseEvaluator`'s
connected-house definition, `PromiseHit.Status`, `Sensitivity` — is a rule
change like any other and does need the hand bump. `core`
cannot reference `EngineVersion` (no dependency back to nothing -- `ephemeris` is
the lower layer), so the coupling is a documented manual step. A change without a
bump is caught by the golden-chart snapshot suite drifting with no version change.
