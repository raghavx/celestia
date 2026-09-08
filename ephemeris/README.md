# ephemeris

Pure-Java wrapper over the Swiss Ephemeris Java port. Time handling and geocentric
**sidereal** positions of the nine KP grahas. No Spring, no DB, no network; the
`de.thmac.swisseph` types never appear in this module's public API (enforced by
`DeterminismArchitectureTest`).

## Public API

| Type | Purpose |
|------|---------|
| `PositionProvider` | `positions(Instant) -> EphemerisResult` — the entry point |
| `SwissEphemerisPositionProvider` | the implementation (KP-New ayanamsa, mean node) |
| `SwissEphemerisConfig` | resolves the `.se1` directory and the supported range |
| `TimeScales.of(Instant)` | UTC instant -> `JulianDay` (UT + TT + &Delta;T) |
| `Graha` | the nine grahas + Vimshottari year weights |
| `EphemerisResult` / `GrahaPosition` / `EngineVersion` / `Ayanamsa` / `Accuracy` | value objects |

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
