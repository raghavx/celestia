# Phase 0 Research: Vimshottari Dasha (SPEC-004)

## 1. Balance of dasha at birth (formalised)

Inputs: `birthInstant` (UTC), `moonLongitude` λ (sidereal, KP-New, degrees).

1. `nakshatra` = `Nakshatra.at(λ)`; `mahaLord` = `nakshatra.lord()`.
2. `nakStart` = `nakshatra.ordinal() × 40/3` (exact `BigFraction`);
   `nakWidth` = `40/3`.
3. **Traversed fraction** `f = (λ − nakStart) / nakWidth`, in `[0, 1)`. λ is a
   measured `double`, so `f` carries the ephemeris precision (~1e-13 of a
   nakshatra). Compute `f` as a `BigFraction` from the `double` difference so the
   later multiplication does not add rounding.
4. `mahaYears` = `mahaLord.years()` (Ketu 7 … Mercury 17).
   **elapsed** = `f × mahaYears` years; **balance** = `(1 − f) × mahaYears` years.
5. `mahaStart` = `birthInstant − duration(elapsed)`;
   `mahaEnd` = `mahaStart + duration(mahaYears)` = `birthInstant + duration(balance)`.

**Edge**: λ exactly on a nakshatra boundary → `Nakshatra.at` returns the **next**
nakshatra (half-open, SPEC-001), so `f = 0` and the balance is the full Mahadasha
of the next lord. No special-casing.

**Precision note**: the balance is only as accurate as λ; a `double` λ good to
1e-10° gives a balance good to ~`1e-10/(40/3) × 20 yr ≈ 5 ms`. SC-001's "within 1
day" is dominated by *reference-tool* differences (ayanamsa rounding, year length),
not by our arithmetic.

## 2. The nested nine-way partition

A period `[t0, t1)` ruled by `lord L` splits into **nine** child periods, in
Vimshottari order **starting from L** (`L, L.next(), …` ×9), child *i* spanning
`parentDuration × weight(childᵢ) / 120`.

This is structurally identical to the SPEC-001 nakshatra sub-lord split
(`VimshottariPartition.partition(start, width, lord)`), which already does it
exactly on **arc**. Decision:

- Extract the weight sequence into a shared pure helper
  **`VimshottariSplit.of(BigFraction total, Graha fromLord)`** →
  `List<Portion(Graha lord, BigFraction span)>` (9 entries, spans sum to `total`
  exactly).
- `VimshottariPartition` is refactored to call it (pure change, no behaviour
  delta — the SPEC-001/002 golden suites still pass; no `EngineVersion` bump for a
  pure refactor). *Done in implementation (T032).*
- The dasha timeline applies `VimshottariSplit` to **durations** (exact rational
  seconds), recursing 4 times below the Mahadasha.

**Alternatives considered**: reusing `Span` (degrees-typed, validates `start <
end`) for time — rejected, the semantics ("arc of the zodiac") do not fit and
`Span` would need a second interpretation. A `double`-seconds partition — rejected,
SC-003 requires exact partition equality.

## 3. Years → `Instant`

**1 Vimshottari year = 365.25 days = 31 557 600 seconds, exactly** (see §7). A
duration of `y` years (`BigFraction`) is `y × 31 557 600` seconds, an exact
rational.

- Convert to `java.time.Duration` at the boundary: `secs = floor(rationalSeconds)`,
  `nanos = round((rationalSeconds − secs) × 1e9)`. Nanosecond resolution; the
  shortest possible Prana — Sun's Prana in a Sun / Sun / Sun / Sun chain — is
  `6 × (6/120)⁴` years ≈ 20 minutes, which dwarfs a nanosecond.
- **Cumulative offsets stay exact**: a boundary is
  `birthInstant + toDuration(Σ exact child seconds)`, rounded **once**. Never sum
  rounded `Duration`s — that would drift over hundreds of levels of a long
  enumeration.

Consistent with SPEC-001 `TimeScales`: SI seconds on the UTC timeline, no leap-
second modelling inside a span.

## 4. The point query — running stack

`running(query q, depth d)` with `q ≥ birthInstant`, `1 ≤ d ≤ 5`:

1. **Mahadasha**: the full sequence from `mahaStart` (the true start of the birth
   Maha, §1) with `mahaLord` for its *full* length, then `mahaLord.next()` full,
   … is exactly **periodic with period 120 years** from `mahaStart`. So:
   `Δ = q − mahaStart` (exact seconds); `cycles = floor(Δ / 120yr)`;
   `offset = Δ − cycles × 120yr`; walk the ≤ 9 mahas of one canonical cycle to
   find the one containing `offset`; its absolute start =
   `mahaStart + cycles × 120yr + (offset − offsetIntoThatMaha)`.
2. **Levels 2…d**: `VimshottariSplit` the found parent `[ps, pe)` (exact seconds)
   by its lord; find the child containing `q`; recurse.
3. Each level ≤ 9 iterations; total ≤ 45 `BigFraction` comparisons. O(1) in `q`.

**Half-open** `[start, end)` at every level: `q == start` → that period; `q ==
end` → the next one. A `q` exactly on a Mahadasha boundary is the start of the new
Maha and the start of *its* first child at every deeper level.

**Golden running-stack query instant**: for the extended golden files the query
instant is `birthInstant + 40 Julian years` (`40 × 31 557 600` s). Forty years
puts every golden chart (1879, 1955, 1961) into a non-first Mahadasha and
exercises all five levels; it is independent of the wall clock.

## 5. Windowed enumeration

`periods(level, from, to)`:

- Resolve the level-`level` period in progress at `from` (via §4 to that depth).
- Emit it, then repeatedly compute the **next period at that level** and emit
  while `start < to`. "Next at level L" = next child of the current parent; when
  the parent is exhausted, ascend to the parent's next sibling and descend to its
  first level-L child — all exact-rational arithmetic, no re-resolution from
  `mahaStart`.
- Each emitted `DashaPeriod` carries its `parentLords` chain.

**Safety cap**: the count of level-L periods in `[from, to]` is
`≈ (to − from) / averageLevelLDuration`. For sane requests (Antardashas over
years, Pranas over hours) this is small; a mis-scaled request (Pranas over
decades) is millions. Implement a hard cap (**10 000** periods) → throw
`IllegalArgumentException` naming the level and window. Document the intended
scale per level in the contract.

## 6. Input shape

Mirror SPEC-003's `RulingPlanetsFactory`:

- `DashaTimelineFactory(PositionProvider positions)` — instance `at(BirthData
  birth)` reads the Moon from `positions.positions(birth.instant())`, takes its
  longitude and `Accuracy`, and calls the pure path.
- `DashaTimeline.from(Instant birthInstant, double moonLongitude, Accuracy
  accuracy, EngineVersion engineVersion)` — pure, no I/O.

Reuse `BirthData` for the birth moment (instant + lat + lon); lat/lon are unused
by the dasha but keep the entry point uniform and ready for SPEC-006/008. **No
`NatalChart` parameter** — PROJECT_PLAN scopes SPEC-004 to a SPEC-001 dependency;
the chart-aware dasha-significator join is SPEC-006.

`accuracy` mirrors the Moon position: a birth outside the supported ephemeris
range yields `Accuracy.REDUCED` and the timeline still computes (FR-016).

## 7. Year-length convention

| Convention | Days/yr | Used by |
|---|---|---|
| **Julian year** | **365.25** | **K. S. Krishnamurti, *KP Readers*; KP-Astro, most KP software** |
| Savana (civil) year | 360 | some Parashari dasha tables |
| Tropical (Gregorian mean) | 365.2425 | rare |
| Sidereal solar year | 365.25636 | "true" solar-return dasha, uncommon |

**Decision: 365.25 days**, cited to *KP Readers* — it is the dominant KP
convention and what the mainstream KP tools used for the SC-006 cross-check
produce. Unlike SPEC-003's genuinely contested rules (node aspects, RP sub lords)
there is a single KP answer here, so **no config switch in v1**. A switch, if ever
wanted, is a later decision with an `EngineVersion` bump; the constant lives in
one place (`core/REFERENCES.md` cites it, one `BigFraction` names it).

## 8. Level naming

`enum DashaLevel { MAHADASHA, ANTARDASHA, PRATYANTARDASHA, SOOKSHMA, PRANA }`,
`int rank()` = `ordinal() + 1`. Javadoc records the common synonyms: Antardasha =
**Bhukti**, Pratyantardasha = **Antara** (PROJECT_PLAN uses the short forms). The
DB `dasha_period.level` (SPEC-008) stores `rank()` or `name()`.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | Balance | Moon nakshatra lord = birth Maha lord; elapsed = traversed-fraction × Maha years; Maha starts before birth |
| 2 | Nested split | shared `VimshottariSplit` (exact `BigFraction`); reused for arc (SPEC-001) and duration |
| 3 | Time unit | 365.25-day year as exact rational seconds; cumulative offsets exact, rounded once per boundary |
| 4 | Point query | fast-forward whole 120-yr cycles, then ≤ 9 steps/level to depth d; half-open at every level |
| 5 | Enumeration | walk siblings at the level; 10 000-period safety cap |
| 6 | Input | `BirthData` + `PositionProvider`; pure `from(...)`; no `NatalChart` |
| 7 | Year length | 365.25 days (KSK); no v1 switch |
| 8 | Levels | `DashaLevel` enum, rank 1–5; Bhukti/Antara synonyms in javadoc |
