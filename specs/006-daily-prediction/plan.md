# Implementation Plan: Daily Prediction Ruleset (SPEC-006)

**Branch**: `006-daily-prediction` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-daily-prediction/spec.md`

## Summary

A documented v1 KP daily reading, pure `core`, composed entirely from
SPEC-002/003/004:

- **House-group taxonomy** — a checked-in enum: each `Matter` → a favourable and
  an obstructive house set, each cited. `explainHouseGrouping` reads it.
- **Dasha significators** — for a `NatalChart` and a date: the running Vimshottari
  lords for the reference instant (a `DashaTimeline` built from the chart's Moon,
  SPEC-004), intersected with the natal `SignificatorTable` (SPEC-003) →
  per-lord significations + the union "activated house set" with per-house
  strength (how many of the five running levels signify it).
- **Transit contribution** — the transiting Moon and Sun at the reference instant
  (the one `PositionProvider` call), their KP lord chains, and — by the v1
  sub-lord rule — the natal significator houses each supports.
- **The daily reading** — a total-function verdict per matter
  (`FAVOURABLE` / `MIXED` / `UNFAVOURABLE` / `QUIET`) from the matter's house sets,
  the activated set, and the transit-supported sets, carrying the exact
  houses / lords / transit bodies behind it.

No new ephemeris capability; no new module. The only I/O is fetching the
transiting Moon / Sun.

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: unchanged. Test: JUnit 5 / AssertJ / jqwik /
jackson-databind.

**Storage**: N/A — immutable value objects. (The `prediction` cache table is
SPEC-008.)

**Testing**: `./mvnw verify`. Reuses the golden-chart harness, extended with one
daily-prediction case (chart + fixed date + place → the dasha significators, the
transit contribution, the per-matter reading), produced by
`tools/ephe-crosscheck` (a Python replica of the v1 rules). Because the tool and
the engine share the v1 rules, **one reading is additionally checked by a human
KP walk-through and the taxonomy against KSK** (SC-006).

**Target Platform**: JVM library. Bit-reproducible across Linux/macOS, x86-64/arm64.

**Project Type**: Library — one new package `com.celestia.core.prediction`; no
new module, no `ephemeris` change.

**Performance Goals**: SC-005 — a full reading (given a cast `NatalChart`) in
< 50 ms warm. Cost is three `PositionProvider.positions(...)` calls (Moon at the
reference instant ± 12 h) plus pure `SignificatorTable.of` / `DashaTimeline` work.

**Constraints**:
- Constitution II — pure, deterministic; no wall clock; no Swiss Ephemeris type
  in any public signature (FR-015).
- Constitution IV — structured output only; no prose.
- SC-004 / FR-017 — every verdict reconstructable by hand from `core/REFERENCES.md`.
- SC-001 — the taxonomy cited and human-checked; SC-006 — one reading walked by hand.

**Scale/Scope**: ~8–10 matters, 12 houses, 5 dasha levels, 2 transit bodies; one
chart / one date per reading.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code; the spec + `research.md` **are** the "design doc" PROJECT_PLAN asks for |
| II Deterministic pure core | **yes, central** | PASS — new `core.prediction` package; existing `DeterminismArchitectureTest` covers it; the reference instant is derived, not read from a clock |
| III Calculation Correctness Is Gated | **yes, central** | PASS — extends the golden suite (one reading); the taxonomy, transit rule, reference-instant rule and verdict rule are all cited or marked v1 in `core/REFERENCES.md`; SC-006 adds a human walk-through |
| IV LLM interprets, never calculates | **yes, central** | PASS — the output is a structured verdict + the houses / lords behind it; the sentence is the LLM's job (FR-008) |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — a chart, a date, a longitude |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — the transit positions come from the existing pure `PositionProvider`; no SE type in the public API |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB |
| ADR gate (D1–D23) | yes | PASS — no new ADR. The v1 ruleset (house groups, transit = sub-lord only, reference instant = local noon, the verdict thresholds) is **KP-rule design**, not a vendor / library / schema decision and not in D1–D23; it lives in the spec + `research.md` + `REFERENCES.md`, versioned via `EngineVersion`, exactly as SPEC-004's year-length and SPEC-005's Ascendant-midpoint. |

**Result: PASS.** No new dependency, no new module, no new ADR.

## Project Structure

### Documentation (this feature)

```text
specs/006-daily-prediction/
├── plan.md
├── research.md          # the house-group table + sources; the v1 transit rule; the reference instant; the v1 verdict rule (thresholds pinned here)
├── data-model.md         # Matter, Verdict, DashaSignificators, TransitContribution, MatterVerdict, DailyPrediction, invariants
├── contracts/
│   └── daily-prediction-api.md   # core: DailyPredictionFactory / HouseGroups
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
core/src/main/java/com/celestia/core/prediction/
├── Matter.java                  # enum: MARRIAGE, CAREER, … each with favourable + obstructive house sets + source
├── HouseGroups.java             # static of(Matter) / fromKey(String) / all(); explainHouseGrouping
├── Verdict.java                 # enum: FAVOURABLE, MIXED, UNFAVOURABLE, QUIET
├── ActivatedHouse.java          # record: int house, int strength, Set<Graha> lords
├── DashaSignificators.java      # record: RunningDasha running, Map<Graha,GrahaSignificators> perLord, List<ActivatedHouse> activated, boolean lordChangesWithinDay
├── TransitBody.java             # enum: MOON, SUN
├── TransitContribution.java     # record: LordChain moon, LordChain sun, Set<Integer> moonSupports, Set<Integer> sunSupports, boolean moonSubLordChangesWithinDay
├── MatterVerdict.java           # record: Matter, Verdict, favourableHit, obstructiveHit, Set<Graha> lords, Set<TransitBody> transits
├── DailyPrediction.java         # record: Instant referenceInstant, DashaSignificators, TransitContribution, List<MatterVerdict>, Accuracy, EngineVersion
└── DailyPredictionFactory.java  # predict(NatalChart, LocalDate date, double longitude); pure compute(...)
core/.../REFERENCES.md            # + the house groups, the v1 transit rule, the reference-instant rule, the v1 verdict rule
```

**Structure Decision**: no new module. Everything is a new
`com.celestia.core.prediction` package composed from the existing pure engine
types. `DailyPredictionFactory` takes only a `PositionProvider` (for the transit
Moon / Sun); the natal side — `SignificatorTable.of(chart)` and
`DashaTimeline.from(chart …)` — is pure and needs no providers. The reading
reuses `NatalChart` as its input; there is no new "person" or "profile" type.

## Phase 0 — Research

See [research.md](./research.md). Items:

1. The house-group taxonomy — the matters, the favourable / obstructive house
   sets, a source for each (KSK *KP Readers*), and the "closed & versioned"
   decision.
2. Dasha significators — running lords ∩ `grahaSignificators`; the activated house
   set and the strength definition (count of the 5 running levels).
3. The v1 transit rule — "a transiting body supports house H iff its sub lord is a
   natal significator of H"; Moon (day) + Sun (fortnight) only; what is v2.
4. The reference instant — local noon of the date (`date 12:00 − longitude/15 h`);
   the ±12 h window and the change flags.
5. The v1 verdict rule — the exact total function over (favourable/obstructive
   sets, activated set + strengths, Moon/Sun supported sets) → the four outcomes;
   the thresholds, pinned.
6. Traceability — what each `MatterVerdict` carries so a human can reconstruct it.
7. Golden reference — replicate the v1 rules in `ephe-crosscheck`; one case;
   SC-006 human walk-through + taxonomy check.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — the value objects and invariants.
- [contracts/daily-prediction-api.md](./contracts/daily-prediction-api.md).
- [quickstart.md](./quickstart.md).

### Post-design Constitution re-check

Unchanged — no framework, storage, or network introduced; no SE type crosses a
public boundary; the determinism / layering ArchUnit rules already cover
`com.celestia.core..`. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty.
