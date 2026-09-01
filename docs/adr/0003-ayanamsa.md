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

Option 2. Default **KP-New** (`SE_SIDM_KRISHNAMURTI`). `Ayanamsa` is a config enum,
recorded on every `chart` row and included in `engine_version` scope. All golden
charts use KP-New. Non-default values are available but **unsupported** (no golden
coverage) until a dedicated spec adds them.

## Consequences

- Chart storage carries the ayanamsa used.
- Changing the default is an `engine_version` bump plus a recompute of cached charts.
- If multiple ayanamsas are ever offered per user, the guardrail and system prompt
  must keep an answer within the user's configured one.
