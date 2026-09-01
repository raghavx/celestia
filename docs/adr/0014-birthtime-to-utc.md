# ADR 0014: Birth-time to UTC conversion

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D14 (PROJECT_PLAN.md §2)

## Context

A natal chart is computed from an instant in UTC. Users give a local civil time
plus a place. Converting requires the IANA zone for that lat/lon and the
**historical** offset rules for that date — pre-1970 offsets, DST transitions,
one-off war-time changes. `java.time` carries these rules; a naive fixed offset
shifts every cusp and the dasha balance.

## Options considered

For lat/lon → zone: `timeshape` (embedded polygon data, offline), the GeoNames
timezone API (online), `us.dustinj.timezonemap`.

## Decision

Use **`timeshape`** for lat/lon → `ZoneId` (offline, deterministic), then
`LocalDateTime.atZone(zoneId).toInstant()` for the historical conversion. Persist
`birth_local`, `zone_id`, and `birth_utc` on `birth_data`. Ambiguous or
non-existent local times (DST fold/gap) prompt the user to confirm. The computed
local time is echoed back in the onboarding confirmation step.

## Consequences

- The `timeshape` polygon dataset (~50 MB) is a `geo` dependency.
- The test corpus must include pre-1970 and DST-boundary births.
- If the user's stated zone contradicts the geocoded one, ask rather than guess.
