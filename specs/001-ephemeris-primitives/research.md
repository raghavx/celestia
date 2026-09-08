# Phase 0 Research: Ephemeris & Longitude Primitives (SPEC-001)

## 1. Swiss Ephemeris Java port — distribution and API

**Decision**: Consume a maintained fork of the Thomas Mack Java port (package
`de.thmac.swisseph`) via **JitPack** during Phase 0/1; keep **source vendoring**
as a ready fallback. Wrap it entirely behind `PositionProvider`.

**Rationale**:
- There is **no `swisseph` artifact on Maven Central** (verified — the search API
  returns zero results). The realistic options are JitPack, vendoring the source,
  or building and hosting a jar in an internal repo.
- JitPack builds a tagged commit of a GitHub repo on demand — zero code in our
  tree, trivial to pin. A fork with a standard `src/main/java` Maven layout builds
  cleanly.
- Vendoring the source (into `ephemeris/src/main/java/de/thmac/swisseph/…`) is the
  fallback if JitPack is unreliable or if we want zero external build
  repositories. AGPL source in-tree is acceptable — we hold (will hold) the
  commercial licence per ADR-0001, and AGPL itself expects source availability.

**API surface we depend on** (all via the wrapper):
- `SwissEph(ephePath)` / `swe_set_ephe_path(...)` — point at the `.se1` directory.
- `swe_set_sid_mode(SE_SIDM_KRISHNAMURTI, 0, 0)` — `SE_SIDM_KRISHNAMURTI` has
  constant value **5**. This is the "KP-New" / Krishnamurti ayanamsa (ADR-0003).
- `swe_calc_ut(jdUt, planet, flags, xx[6])` — returns `[lon, lat, dist, lonSpeed,
  latSpeed, distSpeed]`. Flags: `SEFLG_SIDEREAL | SEFLG_SPEED | SEFLG_SWIEPH`
  (or `| SEFLG_MOSEPH` for the fallback).
- Planet ids: `SE_SUN … SE_SATURN`, `SE_MEAN_NODE` (Rahu; **mean** per ADR-0005).
  Ketu = Rahu + 180° computed by us, not by the library.
- `swe_deltat(jd)` / `swe_deltat_ex(...)` — ΔT in days; used to derive TT from UT.
- `swe_close()` — release native/file handles.

**Thread-safety**: `SwissEph` instances are **not** thread-safe. The provider will
either (a) hold a small pool of `SwissEph` instances guarded by a semaphore, or
(b) create-use-close per call (simpler, measured against SC-006 first). Decision
deferred to implementation; the contract already declares the provider
thread-safe at the interface level regardless.

**Alternatives considered**:
- Pure-Java VSOP87/ELP2000 reimplementation — rejected: large verification burden,
  weaker Moon accuracy, direct risk to Principle III. (This was ADR-0001 Option 3.)
- `aloistr/swisseph` C via JNI/Panama — rejected for v1: native build/packaging
  complexity across the CI matrix; the pure-Java port is sufficient for KP.

**Open verification** (do during implementation, not blocking the plan):
- Confirm the chosen fork builds on JitPack with a JDK 25 toolchain.
- Confirm `swe_calc_ut` output is bit-identical across the CI OS/arch matrix for a
  fixed jd + flags (float determinism).

## 2. `.se1` ephemeris data for 1800–2100

**Decision**: Bundle the planetary and lunar `.se1` files covering **1800–2100**
(`sepl_18.se1`, `semo_18.se1` — each file covers 600 years from 1800). Provision
them with `scripts/fetch-ephe.sh` (download from `https://www.astro.com/ftp/swisseph/ephe/`,
verify SHA-256 against a checked-in manifest). Files are git-ignored (already in
`.gitignore`) and added as a Docker image layer.

**Rationale**: Two files (~3–4 MB total) cover the entire supported range for
planets and Moon. Asteroid files (`seas_*`) are not needed — KP uses only the nine
grahas. The checksum manifest makes the data version explicit and feeds
`EngineVersion`.

**CI**: two options, decided in implementation —
1. Provision the real `.se1` files in CI (cache them between runs); golden-chart
   tests use the true ephemeris.
2. Run CI golden tests against **Moshier** (`SEFLG_MOSEPH`, no files) and accept a
   slightly looser tolerance, keeping a nightly job on the real files.
Leaning option 1 (fidelity), with a tiny committed `.se1` subset only if caching
proves flaky.

**Alternatives considered**: JPL DE files — far larger, no KP benefit. Shipping no
files and always using Moshier — rejected: Moon accuracy is marginal exactly where
KP is most sensitive (sub-lord boundaries).

## 3. Exact-rational arithmetic for the Vimshottari partition

**Decision**: Represent every partition boundary as an **exact rational**
(`org.apache.commons.numbers.fraction.BigFraction`). Convert to `double` degrees
only at the public API surface, and additionally expose the exact boundary for
tests and for the horary table (SPEC-005).

**Rationale**:
- A nakshatra is 13°20′ = 800′. Sub width = `800′ × years / 120`. Sub-sub width =
  `subWidth × years / 120`. The denominator is `120^k`; numerators stay integer
  only if the unit is fine enough — `800′ = 2 880 000` thirds, and
  `2 880 000 / 120^2 = 200` is integer but `2 880 000 × 7 × 7 / 120^2 = 9800`
  while `2 880 000 × 7 / 120 × 16 / 120` etc. do **not** all land on integer
  thirds. Scaled-integer math therefore accumulates rounding; exact rationals do
  not.
- `BigFraction` makes SC-003 ("sum of spans == parent width, exactly") a literal
  equality assertion, not an epsilon comparison.

**Alternatives considered**:
- Scaled `long` in arc-thirds or arc-quarter-seconds — fails the exactness
  guarantee at the sub-sub level; would need documented rounding rules and a
  tolerance in SC-003. Rejected.
- Hand-rolled `BigInteger` numerator/denominator — works, but re-implements
  Commons Numbers for no benefit. Rejected.
- `BigDecimal` with a fixed scale — same rounding problem as scaled longs.

**Note**: `commons-numbers-fraction` is a single small pure-Java jar (no
transitive deps). It does not compromise Principle II.

## 4. Golden-chart reference data

**Decision**: Assemble **3–5 golden charts**, each stored as a checked-in
`core/src/test/resources/golden/<name>.json` containing: birth instant (UTC),
source citation, and the published values for all nine grahas — sidereal
longitude, sign, nakshatra, pada, star lord, sub lord, sub-sub lord.

**Sources** (to be finalised during the data-gathering task — this is the one
prerequisite still outstanding before `/speckit-implement`):
- Worked example charts from standard KP texts (Krishnamurti's *Readers*, or a
  widely used KP textbook) — these give the lord chains directly.
- One or two charts cross-generated with an independent, well-regarded KP tool
  (e.g. an established open-source KP library) to establish the **2-arc-second**
  numerical reference in SC-002, since book values are usually only to the
  arc-minute.

**Rationale**: Two independent kinds of reference — human-published lord chains
(catch logic errors) and an independent computation (catch numerical/ayanamsa
errors). Both are needed; neither alone is sufficient.

**Risk**: published charts occasionally contain typos or use a slightly different
ayanamsa. Mitigation: require agreement between at least two sources per value, or
flag the value as "single-source" in the golden file.

## 5. Boundary and display convention

**Decision**: Half-open intervals `[start, end)` at every level (sign, nakshatra,
pada, sub, sub-sub). A longitude exactly on a boundary belongs to the division of
**higher** longitude. Normalisation to `[0°, 360°)` happens before any
decomposition.

**Rationale**: Matches the prevailing behaviour of KP software and makes the
"tiles the circle with no gap/overlap" property (SC-004) clean to state and test.
Exact-boundary inputs are astronomically near-impossible for real positions but
must be defined for the property tests and for the 249 horary table.

**Display**: internal math is exact; any human-facing rounding (e.g. to
arc-seconds) is a presentation concern for later specs, not part of SPEC-001's
outputs.

## 6. Cross-platform determinism

**Decision**: Treat determinism as a tested property. `EngineVersion` embeds: a
`rules` id (bumped by us on any algorithm change), the SE port version, the ΔT
model tag, and the `.se1` data manifest hash. A CI job runs the golden suite on
`{ubuntu, macos} × {x86-64, arm64}` and asserts byte-identical output.

**Rationale**: FR-014 / SC-005. IEEE-754 double arithmetic is reproducible, and
the SE port is straight-line C-to-Java with no concurrency in the math, so
bit-stability is expected — but it must be verified, not assumed, given the
"correctness is the product" principle.

## 7. ΔT model

**Decision**: Use the SE port's built-in ΔT (`swe_deltat`), which implements the
Espenak–Meeus polynomial set with the standard long-term extrapolation. The ΔT
model tag is part of `EngineVersion`, so an upstream ΔT table update is a
versioned, visible change.

**Rationale**: ΔT differences at KP-relevant dates (1900–2100) are sub-second of
time and do not move the Moon enough to change a sub lord except in already
knife-edge cases where the birth time itself is the dominant uncertainty. Rolling
our own ΔT adds risk for no accuracy gain.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | SE port | `de.thmac.swisseph` fork via JitPack; vendored-source fallback; wrapped by `PositionProvider` |
| 2 | Data | `.se1` planets+Moon for 1800–2100, provisioned by script + SHA-256 manifest |
| 3 | Partition math | exact `BigFraction`; `double` only at the API edge |
| 4 | Golden data | 3–5 charts, book lord-chains + independent numeric cross-check (outstanding prerequisite) |
| 5 | Boundaries | half-open `[start, end)`, boundary to higher division; normalise to `[0,360)` first |
| 6 | Determinism | `EngineVersion` (rules + SE ver + ΔT tag + data hash); CI OS/arch matrix asserts bit-identical |
| 7 | ΔT | SE built-in (Espenak–Meeus), tagged in `EngineVersion` |
