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

## Engine version bump procedure

`EngineVersion.rules` (currently `"kp-1"`, in `ephemeris`) is bumped **by hand**
whenever any rule above changes -- including rules that live in `core`
(`Sign`, `Nakshatra`, `VimshottariPartition`, `KpLordage`, `Longitudes`,
`judgement.SignificatorTable`, `judgement.RulingPlanetsFactory`,
`judgement.KpWeekday`). `core`
cannot reference `EngineVersion` (no dependency back to nothing -- `ephemeris` is
the lower layer), so the coupling is a documented manual step. A change without a
bump is caught by the golden-chart snapshot suite drifting with no version change.
