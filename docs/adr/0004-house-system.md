# ADR 0004: House system

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D4 (PROJECT_PLAN.md §2)

## Context

KP is cuspal astrology — the sub-lord of a house cusp is the primary determinant
of a matter. KP uses **Placidus** cusps on the **sidereal** zodiac. Any other
house system (Equal, Koch, Sripati/Porphyry) changes every cusp and invalidates
KP significator and ruling-planet logic.

## Options considered

1. Placidus, sidereal — standard KP; non-configurable.
2. Make the house system configurable.

## Decision

Option 1. Placidus only, sidereal, computed via `swe_houses_ex` with the sidereal
flag and the KP ayanamsa (ADR-0003). Placidus is mathematically undefined above
roughly 66° latitude; the onboarding / geocoding flow warns or rejects extreme
latitudes, and `core` has a defined behaviour for the polar edge case (specified
in SPEC-002).

## Consequences

- `geo` must surface latitude to the chart pipeline.
- The polar edge case is an explicit spec item, not an open question.
- No configurability debt to carry.
