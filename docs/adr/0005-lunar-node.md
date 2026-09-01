# ADR 0005: Lunar node (Rahu / Ketu)

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D5 (PROJECT_PLAN.md §2)

## Context

Rahu and Ketu can be computed as the **mean** node (smoothed) or the **true /
osculating** node (actual, oscillates by up to ~1.5°). The two can differ by more
than a degree, which is enough to change a planet's star lord or sub lord when the
node sits near a boundary. Krishnamurti's own work uses the mean node, as does
most classic KP literature.

## Options considered

1. Mean node only.
2. Mean node default, true node available as config.
3. True node only (some modern KP software defaults to this).

## Decision

Option 2. **Mean node** is the default — it matches classic KP and the golden
charts. `true` is available as config. Ketu is always exactly 180° from Rahu. The
choice is within `engine_version` scope.

## Consequences

- Golden charts use the mean node.
- Switching to the true node is an `engine_version` bump plus a recompute.
- Document the choice in user-facing help — practitioners will ask.
