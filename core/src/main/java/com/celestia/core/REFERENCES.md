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

## Engine version bump procedure

`EngineVersion.rules` (currently `"kp-1"`, in `ephemeris`) is bumped **by hand**
whenever any rule above changes -- including rules that live in `core`
(`Sign`, `Nakshatra`, `VimshottariPartition`, `KpLordage`, `Longitudes`). `core`
cannot reference `EngineVersion` (no dependency back to nothing -- `ephemeris` is
the lower layer), so the coupling is a documented manual step. A change without a
bump is caught by the golden-chart snapshot suite drifting with no version change.
