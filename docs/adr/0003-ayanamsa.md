# ADR 0003: Ayanamsa

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D3 (PROJECT_PLAN.md §2)

## Context

Sidereal longitude = tropical longitude − ayanamsa. KP practice is not uniform:
"KP" / "KP-New" (Krishnamurti, `SE_SIDM_KRISHNAMURTI`), "KP-Senthil",
"KP straight-line", and some practitioners use Lahiri. Every derived value — sign,
star lord, sub lord, cuspal sub-lord — depends on the ayanamsa to the arc-minute.

## Options considered

1. Fix to KP-New only.
2. Default KP-New; expose ayanamsa as a small config enum (KP-New, KP-Senthil, Lahiri).
3. Allow an arbitrary custom ayanamsa value.

## Decision

Option 2. Default to Swiss Ephemeris **`SE_SIDM_KRISHNAMURTI`** (constant value
**5**, name "Krishnamurti"). `Ayanamsa` is a config enum, recorded on every
`chart` row and included in `engine_version` scope. All golden charts use this
constant. Non-default values are available but **unsupported** (no golden
coverage) until a dedicated spec adds them.

### Clarification (added while sourcing golden charts, 2026-09-08)

The KP community names its ayanamsas inconsistently ("KP Old", "KP New", "True
KP"). What we commit to is the **Swiss Ephemeris constant**, not a label:

- **`SE_SIDM_KRISHNAMURTI` (5)** — what we use. This is the ayanamsa the great
  majority of KP software and practitioners use and call "KP" (or "KP New").
- **`SE_SIDM_KRISHNAMURTI_VP291` (42)** — the Senthilathiban precession-corrected
  variant ("True KP" / VP291). We do **not** use this.

The two differ by roughly a quarter of an arc-minute near 2000 — occasionally
enough to move a cuspal sub-lord. Because the label is ambiguous, golden charts
and any independent cross-check tool MUST be configured with the numeric constant
`5`, and the reference tool's ayanamsa setting is recorded in each golden file.

## Consequences

- Chart storage carries the ayanamsa used.
- Changing the default is an `engine_version` bump plus a recompute of cached charts.
- If multiple ayanamsas are ever offered per user, the guardrail and system prompt
  must keep an answer within the user's configured one.
