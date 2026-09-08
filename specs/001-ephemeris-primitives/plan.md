# Implementation Plan: Ephemeris & Longitude Primitives (SPEC-001)

**Branch**: `001-ephemeris-primitives` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-ephemeris-primitives/spec.md`

## Summary

Deliver the deterministic foundation of the KP engine in two pure-Java modules:

- **`ephemeris`** — time handling (UTC instant → Julian Day in UT and TT, ΔT), and
  geocentric **sidereal** positions of the nine KP grahas (KP-New ayanamsa, mean
  node) behind a `PositionProvider` interface with a Swiss Ephemeris implementation.
- **`core`** — decomposition of any ecliptic longitude into the full KP lord chain
  (sign / sign lord / nakshatra / pada / star lord / sub lord / sub-sub lord), and
  the Vimshottari proportional partition (sub within nakshatra, sub-sub within sub)
  computed with **exact rational arithmetic** so boundaries tile perfectly.

Correctness is proven by a golden-chart snapshot suite (≥ 3 published KP charts,
every value pinned) plus property tests that sweep the whole zodiac. Everything is
deterministic and offline; an `EngineVersion` string ties every output to the
exact rules and data that produced it.

## Technical Context

**Language/Version**: Java 25 LTS (`maven.compiler.release=25`)

**Primary Dependencies**:
- Swiss Ephemeris Java port (Thomas Mack lineage, `de.thmac.swisseph` package) —
  **not on Maven Central**; consumed via JitPack build of a maintained fork, with
  source-vendoring as the fallback. Wrapped behind `PositionProvider`; AGPL, covered
  by ADR-0001 (commercial licence at go-live).
- Apache Commons Numbers `commons-numbers-fraction` — exact rational arithmetic for
  the Vimshottari partition. Pure, tiny, no transitive weight.
- Test: JUnit 5, AssertJ, jqwik (property-based tests for the circle sweep).

**Storage**: N/A — outputs are immutable value objects. Persisting charts is the
`agent` module's concern (SPEC-008), not this feature.

**Testing**: `./mvnw verify`. Unit tests, jqwik property tests, and a golden-chart
snapshot suite (plain expected-value tables checked into
`core/src/test/resources/golden/`). A cross-check harness against an independent
ephemeris implementation runs outside CI to establish the arc-second reference.

**Target Platform**: JVM library (JARs) consumed by the `agent` service and by
tests. Must be bit-reproducible across Linux/macOS, x86-64 and arm64.

**Project Type**: Library (two Maven modules: `ephemeris`, `core`).

**Performance Goals**: SC-006 — nine positions + their lord chains for one instant
in **< 50 ms** on a developer machine. Lord-chain-only decomposition is expected to
be sub-millisecond.

**Constraints**:
- Constitution II — pure and deterministic: no Spring, no DB, no network, no
  ambient clock. (Local **read-only** access to bundled `.se1` data files is
  permitted and necessary; it is not "network" and not non-determinism.)
- SC-002 — sidereal longitudes within 1 arc-minute of published values and 2
  arc-seconds of an authoritative reference.
- SC-003 — partition boundaries tile exactly (exact-rational check, no float
  residue).
- SC-007 — full accuracy over 1800–2100; a result plus a `REDUCED` accuracy flag
  outside that range (Moshier fallback).

**Scale/Scope**: 9 grahas, 12 signs, 27 nakshatras, 9 subs/nakshatra, 9
sub-subs/sub. Single-instant computation — no batch or throughput dimension in
this feature.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist green, this plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — `ephemeris` + `core` stay Spring/DB/network/clock-free; enforced by the existing `LayeringArchitectureTest` plus a **new forbidden-API rule** banning `Instant.now()`, `System.currentTimeMillis()`, `Clock.systemUTC()`, `LocalDate.now()` etc. in both modules. `.se1` file reads are local, read-only, deterministic. |
| III Correctness is gated | **yes, central** | PASS — this feature *delivers* the golden-chart suite; every KP rule cites a source in a `core/…/REFERENCES.md`. |
| IV LLM interprets, never calculates | no | N/A — no LLM in this feature |
| V Astrology-only guarded path | no | N/A |
| VI Deterministic conversation flows | no | N/A |
| VII Identity / consent / PII | no | N/A — no personal data handled here (an instant + a longitude) |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | partial | PASS — the Swiss Ephemeris binding sits behind `PositionProvider` in `ephemeris`; the SE library type is never exposed in a public signature (FR-018), **enforced** by `DeterminismArchitectureTest` rule (b) (task T017) |
| Tech constraints (Java 25, Maven, amounts, Flyway) | partial | PASS — Java 25; no money, no DB in scope |
| ADR gate | yes | PASS — ADR-0001 accepted (go-live action noted), ADR-0002/0003/0005 accepted |

**Result: PASS.** One tracked follow-up, not a violation: `LICENSE-NOTICES.md`
must be kept current for the Swiss Ephemeris dependency.

## Project Structure

### Documentation (this feature)

```text
specs/001-ephemeris-primitives/
├── plan.md              # this file
├── research.md          # Phase 0 — decisions on the SE port, exact arithmetic, golden data
├── data-model.md        # Phase 1 — value objects and their invariants
├── contracts/           # Phase 1 — the public API contract of each module
│   ├── ephemeris-api.md
│   └── core-lordage-api.md
├── quickstart.md        # Phase 1 — how to verify the feature end to end
└── tasks.md             # Phase 2 — created by /speckit-tasks, not here
```

### Source code (repository root)

```text
ephemeris/
├── src/main/java/com/celestia/ephemeris/
│   ├── Graha.java                     # enum: 9 bodies + Vimshottari dasha-year weight
│   ├── Ayanamsa.java                  # enum; KP_NEW is the only supported value
│   ├── Accuracy.java                  # enum: FULL | REDUCED
│   ├── JulianDay.java                 # record: jdUt, jdTt, deltaTSeconds
│   ├── TimeScales.java                # UTC Instant -> JulianDay (ΔT model)
│   ├── GrahaPosition.java             # record: longitude, latitude, speed, retrograde, accuracy
│   ├── EphemerisResult.java           # record: Map<Graha,GrahaPosition> + Ayanamsa + EngineVersion
│   ├── EngineVersion.java             # value: rules id + SE version + data version
│   ├── PositionProvider.java          # interface: positions(Instant) -> EphemerisResult
│   ├── EphemerisException.java
│   └── swisseph/
│       ├── SwissEphemerisPositionProvider.java
│       └── SwissEphemerisConfig.java  # data path, supported-range bounds
├── src/main/resources/ephe/           # .se1 files — gitignored, provisioned (ADR-0002)
├── src/test/java/...                  # provider tests, ΔT tests, range/fallback tests
└── src/test/resources/ephe/           # a minimal .se1 subset for CI, or Moshier-only tests

core/
├── src/main/java/com/celestia/core/
│   ├── lordage/
│   │   ├── Sign.java                  # enum: 12 signs + lord
│   │   ├── Nakshatra.java             # enum: 27 + lord + start longitude
│   │   ├── Longitudes.java            # normalise to [0,360); boundary convention
│   │   ├── LordChain.java             # record: sign, signLord, nakshatra, pada, starLord, subLord, subSubLord
│   │   └── KpLordage.java             # longitude -> LordChain
│   ├── dasha/
│   │   ├── Vimshottari.java           # refactor: sequence/weights delegate to Graha
│   │   ├── Span.java                  # record: lord, start (Fraction deg), end (Fraction deg)
│   │   └── VimshottariPartition.java  # nakshatra -> 9 subs; (nakshatra,sub) -> 9 sub-subs
│   └── REFERENCES.md                  # source citation per KP rule (FR-019)
├── src/test/java/...                  # jqwik circle sweep, partition exactness, boundary tests
└── src/test/resources/golden/         # golden-chart expected values (positions + lord chains)

agent/src/test/java/com/celestia/agent/architecture/
└── DeterminismArchitectureTest.java   # new: forbid ambient-clock APIs in ephemeris + core
```

**Structure Decision**: two existing pure modules, no new module. `Graha` and
`Ayanamsa` live in `ephemeris` (the lowest layer) so both the position code and
`core`'s partition logic can share them without a dependency cycle. The scaffold's
`core.dasha.Vimshottari` enum is refactored to source its ordering and year
weights from `ephemeris.Graha`.

## Phase 0 — Research

See [research.md](./research.md). Open items to resolve there:

1. Swiss Ephemeris Java port — distribution (JitPack fork vs vendored source),
   API surface, thread-safety, sidereal/Moshier/speed flags, `SE_SIDM_KRISHNAMURTI`.
   The fork choice + JitPack-on-JDK-25 verification is task **T002(a)**, a spike
   gating the SE-dependent US1 work.
2. `.se1` data set for 1800–2100 — which files, provisioning script, checksums,
   Docker layering, and the minimal subset (or Moshier) usable in CI.
3. Exact-rational arithmetic — Commons Numbers `Fraction`/`BigFraction` vs a
   hand-rolled `BigInteger` fraction vs scaled `long` thirds-of-arc.
4. Golden-chart sources — published KP charts with full lord chains; the
   independent tool used to establish the 2-arc-second reference.
5. Boundary & display convention — how published KP tables treat exact sub
   boundaries and rounding.
6. Cross-platform float determinism of the SE port — verify in a CI matrix.
7. ΔT model provenance and its tie into `EngineVersion`.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — every value object, its fields, invariants,
  and the exact-arithmetic representation of partition boundaries.
- [contracts/ephemeris-api.md](./contracts/ephemeris-api.md) — the `PositionProvider`
  contract: inputs, outputs, error modes, determinism guarantee, thread-safety.
- [contracts/core-lordage-api.md](./contracts/core-lordage-api.md) — `KpLordage` and
  `VimshottariPartition` contracts: boundary rule, normalisation, exactness guarantee.
- [quickstart.md](./quickstart.md) — provision data, run `./mvnw -pl ephemeris,core verify`,
  run the golden-chart suite, interpret a failure.

### Post-design Constitution re-check

No change from the pre-design check. The design introduces no framework, storage,
or network dependency; the one new test (`DeterminismArchitectureTest`) *tightens*
Principle II. `contracts/` keep the SE library type out of every public signature
(FR-018 / Principle IX). **PASS.**

## Complexity Tracking

No constitution violations — this section is intentionally empty.
