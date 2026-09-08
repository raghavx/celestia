# ADR 0001: Swiss Ephemeris licensing

- **Status:** accepted
- **Date:** 2026-09-02 (accepted 2026-09-08)
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

**Option 2 — the Swiss Ephemeris Professional License.** Celestia stays
closed-source. The licence will be **purchased before the production service is
exposed to any external user**; development, testing, demos, and internal use up
to that point proceed under AGPL-3.0, whose obligations are not triggered while
the software is not distributed and not offered as a network service to third
parties.

Buying the licence at go-live (rather than now) is a deliberate cash-flow choice.
The engineering approach does not depend on the timing: the `ephemeris` module
already wraps Swiss Ephemeris behind an internal provider interface (see
SPEC-001), so nothing about the codebase changes when the licence is acquired —
only the legal basis does.

## Consequences

- **Go-live gate:** purchasing the Swiss Ephemeris Professional License from
  Astrodienst is a hard prerequisite for the first production deployment that
  serves external users. It joins the other launch blockers (Razorpay live
  activation, policy pages) on the pre-launch checklist.
- **Cost line:** a one-time licence fee, due at go-live; budget for it now.
- **No code impact:** the provider-interface design means SPEC-001 implementation
  can start immediately.
- **Interim posture:** while under AGPL, do not distribute builds to third parties
  and do not open a public network endpoint to outside users before the licence is
  in hand. Internal/private staging is fine.
- **Data files:** the `.se1` ephemeris data (ADR-0002) is covered by the same
  licence; the professional licence also covers the data.
- A `LICENSE-NOTICES.md` (or `THIRD-PARTY-NOTICES`) entry records the Swiss
  Ephemeris licence status and is updated when the professional licence is bought.
