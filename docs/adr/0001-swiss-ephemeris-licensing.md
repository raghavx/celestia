# ADR 0001: Swiss Ephemeris licensing

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D1 (PROJECT_PLAN.md §2)

## Context

The `ephemeris` module wraps the Swiss Ephemeris Java port for planetary positions
and Placidus cusps. The Java port is licensed **AGPL-3.0**. Running Celestia as a
network service triggers the AGPL's network-use clause: users of the service would
be entitled to the complete corresponding source. Moshier mode does not exempt the
code from the AGPL.

Astrodienst sells a **Swiss Ephemeris Professional License** (one-time fee) that
removes the AGPL obligations for closed-source and commercial use.

## Options considered

1. **Comply with AGPL-3.0** — publish Celestia's source and offer it to every
   service user. Zero licensing cost; commits the whole product to open source.
2. **Buy the Swiss Ephemeris Professional License** — keep Celestia closed-source;
   one-time cost; standard commercial terms.
3. **Replace with a permissively-licensed ephemeris** (VSOP87/ELP2000
   implementations) — no cost, no copyleft, but lower accuracy and significant
   implementation/verification effort; risk to Principle III (correctness).

## Decision

_Pending._ Blocks Phase 1.

## Consequences

- Option 1 shapes the repository (public) and contribution model.
- Option 2 adds a procurement step and a cost line; no code impact.
- Option 3 adds an ephemeris-accuracy spec and extends Phase 1.
