# Feature Specification: Ephemeris & Longitude Primitives (SPEC-001)

**Feature Branch**: `001-ephemeris-primitives`

**Created**: 2026-09-08

**Status**: Draft

**Input**: PROJECT_PLAN.md §5 — "SPEC-001 Ephemeris & longitude primitives: JD/ΔT,
sidereal positions, nakshatra/pada, star/sub/sub-sub lord decomposition,
Vimshottari partition. Depends on: nothing."

_This feature is the deterministic foundation of the KP engine. Its "users" are the
downstream calculation specs (chart assembly, significators, ruling planets, dasha,
horary) and the KP correctness guarantee itself — ultimately every answer a
subscriber receives rests on these primitives being exactly right._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sidereal graha positions for an instant (Priority: P1)

Given a precise moment in time (in UTC), the engine returns the sidereal ecliptic
longitude, ecliptic latitude, daily speed, and retrograde state of each of the
nine KP grahas — Sun, Moon, Mars, Mercury, Jupiter, Venus, Saturn, Rahu, Ketu —
using the KP-New ayanamsa and the mean lunar node.

**Why this priority**: Nothing else in KP can be computed without planetary
positions. This is the entry point to the whole engine.

**Independent Test**: Feed the birth instants of the checked-in golden charts and
compare the nine sidereal longitudes to the published values.

**Acceptance Scenarios**:

1. **Given** a golden chart's birth instant, **When** positions are requested,
   **Then** each graha's sidereal longitude matches the published value (lord chain
   exactly; longitude within the stated tolerance in SC-002).
2. **Given** any instant, **When** positions are requested, **Then** Ketu's
   longitude equals Rahu's + 180° (mod 360°) and both are flagged retrograde.
3. **Given** an instant during a Mercury retrograde period, **When** positions are
   requested, **Then** Mercury's retrograde flag is set and its reported speed is
   negative.

---

### User Story 2 - KP lord chain for any longitude (Priority: P1)

Given any ecliptic longitude in the range 0°–360°, the engine returns: sign (1–12)
and sign lord; nakshatra (1–27) and pada (1–4); nakshatra (star) lord; sub lord;
sub-sub lord — with a single documented rule for a longitude that lands exactly on
a division boundary.

**Why this priority**: This is the single most reused primitive. Significators,
ruling planets, cuspal analysis, and horary all resolve to a lord chain.

**Independent Test**: A table of known longitude → lord-chain rows from published
KP material, plus property tests that sweep the entire circle.

**Acceptance Scenarios**:

1. **Given** a longitude from the published golden set, **When** the lord chain is
   requested, **Then** the star lord, sub lord, and sub-sub lord all match the
   published values.
2. **Given** a longitude exactly on a sub boundary, **When** the lord chain is
   requested, **Then** the boundary belongs to the division of higher longitude
   (half-open interval `[start, end)`).
3. **Given** longitude inputs of 0°, 360°, −5°, and 365°, **When** decomposed,
   **Then** they are treated as 0°, 0°, 355°, and 5° respectively.
4. **Given** the 27 nakshatra start longitudes, **When** decomposed, **Then** each
   begins pada 1 and each pada spans exactly 3°20′.

---

### User Story 3 - Vimshottari proportional partition (Priority: P2)

The engine exposes the Vimshottari partition rule as data: for a nakshatra it
returns the nine ordered sub-spans (lord + start/end longitude); for a
(nakshatra, sub) pair it returns the nine ordered sub-sub-spans. Widths are
proportional to the Vimshottari dasha years (Ketu 7 … Mercury 17, total 120) and
the lords run in Vimshottari order starting from the parent division's lord.

**Why this priority**: It underpins the lord-chain tables in US2, the KP 249
horary table (SPEC-005), and the dasha timeline (SPEC-004).

**Independent Test**: Assert that each level's spans sum exactly to the parent
width and that lords follow Vimshottari order from the parent lord.

**Acceptance Scenarios**:

1. **Given** a nakshatra, **When** its sub partition is requested, **Then** the
   nine sub widths equal `(dashaYears / 120) × 13°20′` and sum to exactly 13°20′.
2. **Given** a nakshatra ruled by Ketu, **When** its sub partition is requested,
   **Then** the first sub is Ketu with width 0°46′40″ and the second is Venus with
   width 2°13′20″.
3. **Given** any (nakshatra, sub) pair, **When** its sub-sub partition is
   requested, **Then** the nine sub-sub widths sum to exactly the sub's width and
   the first sub-sub lord is the sub's lord.
4. **Given** the full zodiac, **When** all sub spans are combined, **Then** they
   cover `[0°, 360°)` with no gap and no overlap.

---

### User Story 4 - Reproducible correctness contract (Priority: P2)

For every checked-in golden chart the engine reproduces every published primitive
value (positions and full lord chains) on every run and on every machine, and any
change to a calculation rule is surfaced through the engine version.

**Why this priority**: Constitution III — correctness is the product. Silent drift
must fail the build.

**Independent Test**: The golden-chart snapshot suite in CI, plus a determinism
test that computes the same input twice and diffs the output.

**Acceptance Scenarios**:

1. **Given** the golden-chart suite, **When** it runs in CI, **Then** zero values
   differ from the recorded snapshots.
2. **Given** identical input, **When** primitives are computed twice, **Then** the
   two outputs are identical.
3. **Given** a change to any calculation rule, ayanamsa default, node choice, or
   ephemeris data version, **When** the suite runs, **Then** the engine version has
   changed and the suite flags every affected value.

---

### User Story 5 - Historical and out-of-range dates (Priority: P3)

The engine returns correct primitives for any instant within the supported
ephemeris date range and still returns a result outside it, with an explicit
indicator that accuracy is reduced.

**Why this priority**: The vast majority of birth instants fall well within range;
the fallback is a robustness guarantee, not a common path.

**Independent Test**: Compute for dates in 1850, 1950, and 2050; compute for 1600
and assert the reduced-accuracy indicator is set.

**Acceptance Scenarios**:

1. **Given** an instant within the supported range, **When** primitives are
   computed, **Then** no accuracy warning is present.
2. **Given** an instant outside the supported range, **When** primitives are
   computed, **Then** a result is still returned and the reduced-accuracy indicator
   is set.
3. **Given** the same instant, **When** primitives are computed, **Then** delta-T
   is applied consistently (universal time vs. terrestrial time are distinguished).

---

### Edge Cases

- A longitude exactly on a sign, nakshatra, pada, sub, or sub-sub boundary →
  resolved by the half-open convention `[start, end)`; the boundary belongs to the
  division of higher longitude.
- Longitude input of exactly 360.0, a negative value, or a value greater than
  360° → normalised into `[0°, 360°)` before decomposition.
- The Moon moves roughly 0.5°/hour; near a sub boundary a one-second error in the
  birth instant can change the sub lord. Positions must carry sufficient numeric
  precision; the precision of the birth instant itself is captured upstream
  (onboarding records whether the time is exact or approximate).
- Rahu and Ketu are always retrograde under the mean-node model; the reported
  speed sign must reflect this.
- Sub and sub-sub partition arithmetic must not accumulate rounding error across
  the 9 × 9 divisions — boundaries must tile exactly.
- A birth instant outside the bundled ephemeris data range → analytical fallback
  plus the reduced-accuracy indicator; this is not an error.
- A request for any ayanamsa other than KP-New, for grahas beyond the nine, or for
  a lordage level beyond sub-sub → rejected as out of scope; no silent partial
  answer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The engine MUST accept a time instant expressed in UTC and derive
  the Julian Day in both universal time and terrestrial (dynamical) time, applying
  a delta-T model.
- **FR-002**: The engine MUST compute, for a given instant, the geocentric
  sidereal ecliptic longitude of the nine KP grahas: Sun, Moon, Mars, Mercury,
  Jupiter, Venus, Saturn, Rahu, Ketu.
- **FR-003**: The engine MUST use the KP-New (Krishnamurti) ayanamsa for the
  sidereal conversion (ADR-0003).
- **FR-004**: The engine MUST derive Rahu and Ketu from the **mean** lunar node
  (ADR-0005), with Ketu exactly 180° from Rahu.
- **FR-005**: The engine MUST report, per graha, the ecliptic latitude, the daily
  motion (speed), and a retrograde indicator derived from the sign of the speed.
- **FR-006**: The engine MUST decompose any ecliptic longitude into: sign (1–12)
  and sign lord; nakshatra (1–27); pada (1–4); nakshatra (star) lord; sub lord;
  sub-sub lord.
- **FR-007**: Each of the 27 nakshatras MUST span exactly 13°20′ beginning at 0°
  Aries, and each pada MUST span exactly 3°20′.
- **FR-008**: Sign lords, nakshatra lords, and the Vimshottari ordering MUST follow
  the standard KP tables (Ketu 7, Venus 20, Sun 6, Moon 10, Mars 7, Rahu 18,
  Jupiter 16, Saturn 19, Mercury 17; total 120 years).
- **FR-009**: The engine MUST partition each nakshatra into nine sub-spans whose
  widths are proportional to the Vimshottari years and whose lords run in
  Vimshottari order starting from the nakshatra lord.
- **FR-010**: The engine MUST partition each sub into nine sub-sub-spans by the
  same proportional rule, starting from the sub lord.
- **FR-011**: Boundary values MUST be resolved by a single documented convention:
  half-open intervals `[start, end)`; a longitude exactly on a boundary belongs to
  the division of higher longitude.
- **FR-012**: All longitude inputs MUST be normalised into `[0°, 360°)` before
  decomposition (handling 360.0, negative values, and values greater than 360°).
- **FR-013**: The complete set of sub-span boundaries MUST tile `[0°, 360°)` with
  no gap or overlap, and sub-sub boundaries MUST tile each sub exactly.
- **FR-014**: For identical inputs the engine MUST produce identical outputs across
  repeated runs and across machines — no dependence on wall clock, locale, time
  zone, or environment.
- **FR-015**: The engine MUST expose an engine-version identifier that changes
  whenever any calculation rule, the ayanamsa default, the node choice, or the
  ephemeris data version changes; stored chart snapshots reference it.
- **FR-016**: For instants within the supported ephemeris date range the engine
  MUST produce full-accuracy results; outside the range it MUST still produce a
  result and set a reduced-accuracy indicator.
- **FR-017**: A request for any ayanamsa other than KP-New, for grahas beyond the
  nine, or for a lordage level beyond sub-sub MUST be rejected as out of scope (no
  silent partial answer).
- **FR-018**: The engine's public outputs MUST be immutable value objects
  consumable by later specs without exposing the underlying ephemeris library's
  types.
- **FR-019**: Every KP rule implemented MUST cite its authoritative source in the
  code or an accompanying reference document (Constitution III).
- **FR-020**: The implementing module MUST contain no framework, database,
  network, or ambient-clock dependency (Constitution II).

### Key Entities *(include if feature involves data)*

- **Instant / Julian Day**: a precise moment; carries the universal-time and
  terrestrial-time Julian Day and the delta-T value applied.
- **Graha**: one of the nine KP planets/points; identity plus its Vimshottari
  dasha-year weight.
- **Graha Position**: for one graha at one instant — sidereal longitude, latitude,
  speed, retrograde flag, accuracy indicator.
- **Lord Chain (KP lordage)**: for one longitude — sign and sign lord, nakshatra
  and pada, star lord, sub lord, sub-sub lord.
- **Vimshottari Partition**: an ordered list of spans (lord, start longitude, end
  longitude) at one level — subs within a nakshatra, or sub-subs within a sub.
- **Ayanamsa**: the sidereal reference; fixed to KP-New for this feature and
  recorded on every output.
- **Engine Version**: an identifier tying a set of outputs to the exact
  calculation rules and data that produced them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every checked-in golden chart (at least 3), the KP lord chain
  (sign, sign lord, nakshatra, pada, star lord, sub lord, sub-sub lord) of all nine
  grahas matches the published values with **zero** discrepancies.
- **SC-002**: For every golden chart, each graha's sidereal longitude matches the
  published value to within **1 arc-minute**, and matches an independent
  authoritative ephemeris reference to within **2 arc-seconds**.
- **SC-003**: The nine sub widths of every nakshatra sum to exactly 13°20′00″, and
  the nine sub-sub widths of every sub sum to exactly that sub's width, with no
  rounding residue (verified as an exact-rational check).
- **SC-004**: The full set of sub-lord boundaries partitions `[0°, 360°)` with no
  gap and no overlap, verified by a property test that sweeps the whole circle.
- **SC-005**: Computing the same primitive set twice on the same input yields
  identical output, and the golden-chart snapshot suite passes unchanged in CI on
  every supported platform.
- **SC-006**: Computing the nine positions plus their lord chains for a single
  instant completes in under **50 ms** on a developer machine, so it is usable
  inside a request path.
- **SC-007**: Results are produced for any instant in 1800–2100 with no accuracy
  warning, and for instants outside that range with the reduced-accuracy indicator
  set.

## Assumptions

- The input instant is already in UTC. Converting a user's local civil birth time
  and place to UTC is SPEC-007, not this feature.
- Planetary positions are geocentric and independent of birth location. The
  Ascendant and house cusps (which do need location) are SPEC-002.
- KP-New ayanamsa (ADR-0003) and the mean lunar node (ADR-0005) are the fixed
  defaults; both are accepted decisions.
- The Swiss Ephemeris Java port is the intended position source (ADR-0002), used
  behind an internal provider interface so the binding is swappable.
  **ADR-0001 (Swiss Ephemeris licensing) must move to `accepted` before
  `/speckit-implement` for this feature.**
- The supported full-accuracy date range is 1800–2100 (ADR-0002); outside it, an
  analytical (Moshier) fallback applies with reduced accuracy.
- The boundary convention is half-open `[start, end)`, the boundary belonging to
  the higher division — an informed default consistent with common KP software.
- Golden-chart reference data — at least three fully documented KP charts with
  published planetary longitudes and complete lord chains — will be sourced and
  checked in before implementation. Candidate sources: standard KP textbooks and an
  established open KP tool used for cross-checking.
- Only the nine classical grahas are in scope; Uranus, Neptune, Pluto and
  additional lordage levels are explicitly excluded.
- The Vimshottari dasha **timeline** (calendar start/end dates derived from the
  Moon's birth longitude) is SPEC-004. This feature provides only the proportional
  partition arithmetic that SPEC-004 will build on.

## Dependencies

- **ADR-0001** — Swiss Ephemeris licensing — MUST be `accepted` before
  implementation of this feature begins.
- **ADR-0002** (ephemeris data source), **ADR-0003** (ayanamsa), **ADR-0005**
  (lunar node) — accepted; this feature assumes them.
- **Golden-chart reference dataset** — a prerequisite deliverable for the
  correctness suite (SC-001, SC-002).
- No dependency on any other SPEC. SPEC-002 through SPEC-005 depend on this one.
