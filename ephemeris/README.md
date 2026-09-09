# ephemeris

Pure-Java wrapper over the Swiss Ephemeris Java port. Time handling and geocentric
**sidereal** positions of the nine KP grahas. No Spring, no DB, no network; the
`de.thmac.swisseph` types never appear in this module's public API (enforced by
`DeterminismArchitectureTest`).

## Public API

| Type | Purpose |
|------|---------|
| `PositionProvider` | `positions(Instant) -> EphemerisResult` — the nine grahas |
| `HouseProvider` | `houses(BirthData) -> HouseResult` — 12 Placidus cusps + angles; `anglesOnly(BirthData)` at any latitude |
| `SunriseProvider` | `sunriseBefore(Instant, lat, lon) -> Optional<Instant>` — the local sunrise that began the KP day; `Optional.empty()` on a polar day/night |
| `SwissEphemerisPositionProvider` / `SwissEphemerisHouseProvider` / `SwissEphemerisSunriseProvider` | the implementations (KP-New ayanamsa, mean node, Placidus) |
| `SwissEphemerisConfig` | resolves the `.se1` directory, the supported range, and the polar limit |
| `TimeScales.of(Instant)` / `.instantFromJulianDayUt(double)` | UTC instant &harr; `JulianDay` (UT + TT + &Delta;T) |
| `Graha` | the nine grahas + Vimshottari year weights |
| `BirthData` | instant + latitude + longitude (the house input) |
| `EphemerisResult` / `HouseResult` / `GrahaPosition` / `EngineVersion` / `Ayanamsa` / `Accuracy` / `Angle` / `HouseSystem` | value objects |

`houses(...)` throws `PlacidusUndefinedException` for births at `|lat| >= 66°`
(configurable); `anglesOnly(...)` still returns the Ascendant/MC there.

## Ephemeris data

`positions()` needs the `.se1` files for full accuracy. Provision them:

```bash
scripts/fetch-ephe.sh                       # -> ephemeris/src/main/resources/ephe/
# or point elsewhere:
export CELESTIA_EPHE_PATH=/path/to/ephe
```

Without the data, every result is flagged `Accuracy.REDUCED` (Moshier model).

## Decisions

ADR-0001 (licensing), ADR-0002 (data / range), ADR-0003 (ayanamsa), ADR-0005
(mean node). Rule sources: `../core/src/main/java/com/celestia/core/REFERENCES.md`.
