# Implementation Plan: Vimshottari Dasha (SPEC-004)

**Branch**: `004-vimshottari-dasha` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-vimshottari-dasha/spec.md`

## Summary

One KP timing primitive, pure `core` logic: from a birth instant and the Moon's
sidereal longitude, build the Vimshottari period tree — five nested levels
(Mahadasha → Antardasha → Pratyantardasha → Sookshma → Prana) — and answer two
questions: the **balance of dasha at birth**, and the **stack of active period
lords** at any instant. Plus a windowed enumeration for persistence and display.

No new ephemeris capability: the only external value is the Moon at the birth
instant, from the existing `PositionProvider`. The weight-proportional nine-way
split is the same one SPEC-001 already implements exactly (`BigFraction`) for the
nakshatra sub-lords — applied here to **durations** instead of arc. Time is exact
rational seconds internally (1 year = 365.25 days = 31 557 600 s), rounded to a
nanosecond `Instant` only at each boundary.

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: unchanged. `org.apache.commons.numbers.fraction.BigFraction`
(already used by `core.dasha`). Test: JUnit 5 / AssertJ / jqwik / jackson-databind.

**Storage**: N/A — immutable value objects. (The `dasha_period` table is SPEC-008.)

**Testing**: `./mvnw verify`. Reuses the golden-chart harness, extended with the
birth balance (lord + elapsed + balance) and the running five-lord stack at a
fixed query date, produced by `tools/ephe-crosscheck` (Python replica of the
balance + partition rules). Because the tool and the engine share the formula,
**one chart's birth Mahadasha lord + balance is additionally checked against a
published KP source** (SC-006).

**Target Platform**: JVM library. Bit-reproducible across Linux/macOS, x86-64/arm64.

**Project Type**: Library — one new package `com.celestia.core.dasha` extends the
existing `Span` / `VimshottariPartition` file set; no new module, no `ephemeris`
change beyond reading the Moon.

**Performance Goals**: SC-005 — the depth-5 running stack for a date in < 5 ms
warm. Each level is ≤ 9 `BigFraction` steps; the point query is O(45) plus one
`PositionProvider` call for the Moon.

**Constraints**:
- Constitution II — pure, deterministic; no wall clock; no Swiss Ephemeris type in
  any public signature (FR-017).
- SC-003 — the nine children of every period partition the parent **exactly**
  (rational equality, not epsilon).
- SC-001 / SC-002 — balance and running lords vs the reference; lords exact,
  boundaries within 1 day.
- SC-006 — one chart's birth balance human-verified against a KP text.

**Scale/Scope**: 9 grahas, 5 levels, 9-way split per level; one birth / one query
instant (or one bounded window) per call.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — new `core.dasha` types; existing `DeterminismArchitectureTest` (no wall clock, no SE leak) covers the package; boundaries are exact `BigFraction` seconds |
| III Calculation Correctness Is Gated | **yes, central** | PASS — Principle III names "dasha balance" explicitly; the golden suite is extended; the year-length rule is cited in `core/REFERENCES.md`; SC-006 adds a human cross-check |
| IV LLM interprets, never calculates | no | N/A — this feature *is* the deterministic timing calculation the LLM will read via `getDashaPeriods` |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — a birth instant and one longitude, no personal identifier |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — no new adapter; the Moon comes from the existing pure `PositionProvider`; no SE type in the public API |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB (persistence is SPEC-008) |
| ADR gate (D1–D23) | yes | PASS — no new ADR. The **year-length convention** (365.25 d) is not one of D1–D23 and is not a vendor/library choice; it is a KP rule, pinned in the spec and cited in `REFERENCES.md`, exactly as SPEC-001 handled the sub-lord division. A future *switch* would be a decision; v1 has none. |

**Result: PASS.** No new dependency, no new module, no new ADR. Additive to the
pure `core` module.

## Project Structure

### Documentation (this feature)

```text
specs/004-vimshottari-dasha/
├── plan.md
├── research.md          # balance formula, the nested partition, years→Instant, the point query, enumeration
├── data-model.md         # value objects and invariants
├── contracts/
│   └── dasha-api.md       # core: DashaTimelineFactory / DashaTimeline
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
core/src/main/java/com/celestia/core/dasha/
├── DashaLevel.java              # enum: MAHADASHA(1), ANTARDASHA(2), PRATYANTARDASHA(3), SOOKSHMA(4), PRANA(5); int rank()
├── DashaPeriod.java             # record: DashaLevel level, Graha lord, Instant start, Instant end, List<Graha> parentLords
├── DashaBalance.java            # record: Graha mahaLord, double elapsedFraction, Duration elapsed, Duration balance, Instant mahaStart, Instant mahaEnd
├── RunningDasha.java            # record: Instant instant, List<DashaPeriod> stack (Maha..depth); period(DashaLevel), lord(DashaLevel)
├── VimshottariSplit.java        # pure: split(BigFraction total, Graha fromLord) -> List<Portion(Graha, BigFraction span)>; the shared nine-way weight split
├── DashaTimeline.java           # from(Instant birth, double moonLongitude, Accuracy, EngineVersion); balanceAtBirth(); running(Instant, int depth); periods(DashaLevel, Instant from, Instant to)
└── DashaTimelineFactory.java    # at(BirthData) wired with PositionProvider (Moon); pure static compute path
core/.../dasha/VimshottariPartition.java   # (optional) refactor to consume VimshottariSplit — pure, golden-guarded
core/.../REFERENCES.md            # + Vimshottari lengths, the nested split, the 365.25-day year, balance-of-dasha
```

**Structure Decision**: no new module and no `ephemeris` interface (unlike
SPEC-003's `SunriseProvider`). All logic is a new `com.celestia.core.dasha`
package sitting beside the SPEC-001 `Span` / `VimshottariPartition`. The birth
moment reuses `BirthData` (instant + lat + lon) even though only the instant and
the Moon matter — consistent with the other engine entry points and ready for the
`PositionProvider` call. A `NatalChart` overload is deliberately **not** added
(PROJECT_PLAN: SPEC-004 depends on 001 only; the chart-aware "dasha significators"
join is SPEC-006).

## Phase 0 — Research

See [research.md](./research.md). Items:

1. The balance-of-dasha formula, formalised — Moon nakshatra → birth Maha lord;
   traversed fraction → elapsed/balance; the Maha's start before birth.
2. The nested nine-way partition — reuse of the SPEC-001 exact weight split for
   durations; the shared `VimshottariSplit` helper; the periodic 120-year cycle.
3. Years → `Instant` — 365.25-day year as exact rational seconds; cumulative
   offsets kept exact, rounded once per boundary; nanosecond precision at Prana.
4. The point query (running stack) — fast-forward whole cycles, then ≤ 9 steps per
   level to depth d; half-open boundary at every level.
5. Windowed enumeration — walking siblings at a level; the safety cap for
   ill-proportioned window/level requests.
6. Input shape — `BirthData` + `PositionProvider`, mirroring SPEC-003; no
   `NatalChart` dependency.
7. Year-length survey — 365.25 (KSK / *KP Readers*) vs 360 savana vs sidereal
   solar; why 365.25 with no v1 switch.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — `DashaLevel`, `DashaPeriod`, `DashaBalance`,
  `RunningDasha`, `DashaTimeline`, `VimshottariSplit`, invariants.
- [contracts/dasha-api.md](./contracts/dasha-api.md) — `DashaTimelineFactory` /
  `DashaTimeline`.
- [quickstart.md](./quickstart.md).

### Post-design Constitution re-check

Unchanged — no framework, storage, or network introduced; no SE type crosses a
public boundary; the determinism / layering ArchUnit rules already cover
`com.celestia.core..`. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty.
