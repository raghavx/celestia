# Implementation Plan: Significators & Ruling Planets (SPEC-003)

**Branch**: `003-significators-ruling-planets` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-significators-ruling-planets/spec.md`

## Summary

Two KP judgement primitives, both pure `core` logic:

- **Significators** — from a `NatalChart` (SPEC-002), the four-step significators
  of every house, the inverse per-graha table, and Rahu/Ketu agency. No new
  ephemeris call; it reads bhava occupants, cuspal owners, and per-graha lord
  chains derived from the stored positions via `KpLordage`.
- **Ruling planets** — for a judgment instant + place, the lagna and Moon
  sign/star/sub lords plus the day lord. The day lord needs the KP weekday
  (sunrise-to-sunrise), so `ephemeris` gains a `SunriseProvider`
  (`swe_rise_trans`), the one genuinely new capability.

Two contested KP rules are switches, not opinions: node aspects
(`includeNodeAspects`, default off) and RP sub lords (`includeSubLords`, default
on).

## Technical Context

**Language/Version**: Java 25 LTS (unchanged).

**Primary Dependencies**: unchanged. `ephemeris` uses `swe_rise_trans` from the
already-present Swiss Ephemeris port. Test: JUnit 5 / AssertJ / jqwik /
jackson-databind.

**Storage**: N/A — immutable value objects.

**Testing**: `./mvnw verify`. Reuses the golden-chart harness, extended with the
per-house significator lists, the per-graha table, node agencies, and one worked
ruling-planet example, produced by `tools/ephe-crosscheck` (Python replica of the
four-step + RP rules). Because the tool and the engine then share those rules,
**one chart's significator table is additionally checked against a published KP
source** (SC-006).

**Target Platform**: JVM library. Bit-reproducible across Linux/macOS, x86-64/arm64.

**Project Type**: Library — same two modules (`ephemeris`, `core`), no new module;
one new package `com.celestia.core.judgement`.

**Performance Goals**: SC-005 — all 12 houses' significators + the per-graha table
in < 20 ms warm. Ruling planets add one `swe_houses` + one `swe_calc_ut` (Moon) +
one or two `swe_rise_trans` calls.

**Constraints**:
- Constitution II — pure, deterministic; `SunriseProvider` behind an interface;
  no SE type in any public signature (FR-018).
- SC-001 — four-step significator membership + step tags exact vs the reference.
- SC-004 — day lord respects the sunrise boundary to the minute.
- SC-006 — one chart's significators human-verified against a KP text.

**Scale/Scope**: 9 grahas, 12 houses, 4 steps; one chart / one judgment moment
per call.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist green, plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — `core.judgement` + one `ephemeris` provider; existing `DeterminismArchitectureTest` (no wall clock, no SE leak) covers the new packages |
| III Correctness is gated | **yes, central** | PASS — extends the golden suite; new rules cited in `core/REFERENCES.md`; SC-006 adds a human cross-check |
| IV LLM interprets, never calculates | no | N/A — this feature *is* the deterministic calculation the LLM will read |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — an instant + coordinates, no personal identifier |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — `SunriseProvider` mirrors `PositionProvider` / `HouseProvider`; SE type never in a public member (FR-018) |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB |
| ADR gate | yes | PASS — no new ADR; ADR-0003/0004/0005 already accepted; SPEC-002 on `master` |

**Result: PASS.** No new dependency, no new module. Additive to the pure modules.

## Project Structure

### Documentation (this feature)

```text
specs/003-significators-ruling-planets/
├── plan.md
├── research.md          # the four-step + node-agency algorithm, RP rules, sunrise
├── data-model.md        # value objects and invariants
├── contracts/
│   ├── sunrise-provider-api.md   # ephemeris: SunriseProvider
│   ├── significators-api.md      # core: SignificatorTable
│   └── ruling-planets-api.md     # core: RulingPlanets
├── quickstart.md
└── tasks.md             # created by /speckit-tasks
```

### Source code (repository root)

```text
ephemeris/src/main/java/com/celestia/ephemeris/
├── SunriseProvider.java          # interface: sunriseBefore(Instant, lat, lon) -> Optional<Instant>
└── swisseph/
    └── SwissEphemerisSunriseProvider.java   # swe_rise_trans, SE_CALC_RISE, upper limb + refraction

core/src/main/java/com/celestia/core/judgement/
├── Step.java                     # enum: STAR_OF_OCCUPANT(1), OCCUPANT(2), STAR_OF_OWNER(3), OWNER(4)
├── Significator.java             # record: Graha, int house, Set<Step>
├── HouseSignificators.java       # record: int house, List<Significator> (ordered)
├── GrahaSignificators.java       # record: Graha, Map<Integer, Set<Step>>
├── NodeAgency.java               # Rahu/Ketu agent resolution
├── SignificatorTable.java        # SignificatorTable.of(NatalChart) -> per-house + per-graha
├── RpSource.java                 # enum: LAGNA_SIGN, LAGNA_STAR, LAGNA_SUB, MOON_SIGN, MOON_STAR, MOON_SUB, DAY_LORD, NODE
├── RulingPlanet.java             # record: Graha, Set<RpSource>
├── RulingPlanets.java            # record: Set<RulingPlanet>, KpWeekday dayLord source, Accuracy, dayLordFallback flag, EngineVersion
├── KpWeekday.java                # weekday <-> day lord; sunrise-boundary resolution
└── RulingPlanetsFactory.java     # at(BirthData judgment) wired with Position/House/Sunrise providers; pure compute(...) core
core/.../REFERENCES.md            # + four-step, node agency, RP, day-lord-at-sunrise
```

**Structure Decision**: no new module. `SunriseProvider` lives in `ephemeris`
alongside the other providers. All KP judgement logic is in a new
`com.celestia.core.judgement` package. The judgment moment reuses the `BirthData`
record (instant + latitude + longitude) — a structurally identical value; a
separate `JudgmentContext` type would be noise (documented in research.md §5).

## Phase 0 — Research

See [research.md](./research.md). Items:

1. The four-step significator algorithm, formalised — including "effective
   occupants" (a node in a house pulls in its agents) and the ordering/tie-break.
2. Per-graha table as the strict inverse; the per-node view.
3. Ruling planets — the seven/eight sources, the node-addition rule, the
   `includeSubLords` switch.
4. Day lord: `swe_rise_trans` usage; "the sunrise that most recently preceded the
   judgment instant"; deriving the weekday (LMT-date approximation, since the true
   civil timezone is SPEC-007); the no-sunrise polar fallback.
5. Judgment context — reuse `BirthData` vs a new type. Decision: reuse.
6. Golden reference — extend `ephe-crosscheck` with the four-step + RP rules;
   pick one chart + judgment instant + place for the RP example; textbook
   cross-check target for SC-006.
7. Node agency when the `includeNodeAspects` flag is on — the aspect scheme is
   out of scope for v1 but the flag and the extension point are defined.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — `Step`, `Significator`, `HouseSignificators`,
  `GrahaSignificators`, `SignificatorTable`, `RpSource`, `RulingPlanet`,
  `RulingPlanets`, `KpWeekday`, invariants.
- [contracts/sunrise-provider-api.md](./contracts/sunrise-provider-api.md)
- [contracts/significators-api.md](./contracts/significators-api.md)
- [contracts/ruling-planets-api.md](./contracts/ruling-planets-api.md)
- [quickstart.md](./quickstart.md)

### Post-design Constitution re-check

Unchanged — no framework, storage, or network introduced; SE stays behind
`SunriseProvider`; the determinism / encapsulation ArchUnit rules already cover
`com.celestia.ephemeris..` and `com.celestia.core..`. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty.
