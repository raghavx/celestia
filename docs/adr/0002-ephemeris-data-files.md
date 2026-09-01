# ADR 0002: Ephemeris data source

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D2 (PROJECT_PLAN.md §2)

## Context

The `ephemeris` module needs planetary and lunar positions. Swiss Ephemeris can
draw from: (a) compressed `.se1` data files from Astrodienst (JPL-derived,
arc-second accuracy), (b) the built-in Moshier analytical theory (no files,
offline, ~arc-second for planets but weaker for the Moon over long spans), or
(c) raw JPL DE files (very large). KP is sensitive to the Moon: nakshatra and
sub-lord boundaries move roughly 0.5°/hour, so Moon accuracy directly affects the
star lord and sub lord.

## Options considered

1. Bundle `.se1` files (`sepl_*`, `semo_*`) for a bounded year range in
   `ephemeris/src/main/resources/ephe/`; a few MB per century.
2. Moshier only — zero files, but Moon accuracy risk on boundary cases.
3. `.se1` primary with automatic Moshier fallback outside the bundled range.

## Decision

Option 3. Bundle `.se1` files for **1800–2100** (covers plausible births, horary,
and near-term transits); Moshier is the automatic fallback for dates outside the
range. The range is a config property. Files are provisioned by a documented
script / Make target, kept out of git (binary; see `.gitignore`), and verified by
checksum in CI. The data files' licence is governed by ADR-0001.

## Consequences

- Onboarding a dev machine and building the Docker image both need the provisioning step.
- Golden-chart tests pin the ephemeris data version.
- Extending the date range is a config change plus a file drop — no code change.
