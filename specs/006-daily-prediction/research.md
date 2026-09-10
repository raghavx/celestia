# Phase 0 Research: Daily Prediction Ruleset (SPEC-006)

**This document is the "design doc" PROJECT_PLAN asks for.** KP daily prediction
is under-specified in the literature; every rule below is either cited or marked a
**v1 design choice**, and the whole set is versioned (`EngineVersion`).

## 1. House-group taxonomy

A closed enum `Matter`, each with a **favourable** house set (must be well
signified for the matter to prosper) and an **obstructive** set (the "negation"
houses — typically the 12th-from each favourable house). Source: K. S.
Krishnamurti, *KP Readers* vols. III–IV (house significations); the 12th-house
negation is standard KP.

| Matter | Favourable | Obstructive | Note |
|--------|-----------|-------------|------|
| `MARRIAGE` | 2, 7, 11 | 1, 6, 10 | 12th from 2 / 7 / 11 |
| `CAREER` | 2, 6, 10, 11 | 1, 5, 9, 12 | profession, income, rise |
| `WEALTH` | 2, 6, 10, 11 | 1, 5, 9, 12 | accumulation (same houses; different reading downstream) |
| `EDUCATION` | 4, 9, 11 | 3, 8, 10 | 4 schooling, 9 higher, 11 success |
| `CHILDREN` | 2, 5, 11 | 1, 4, 10 | 5 progeny, 11 fulfilment |
| `PROPERTY` | 4, 11, 12 | 3, 8, 10 | 4 fixed assets, 12 for land purchase |
| `TRAVEL` | 3, 9, 12 | 4, 8 | 3 short, 9/12 long / foreign |
| `LITIGATION` | 6, 11 | 5, 8, 12 | 6 victory over opponents, 11 gains |
| `HEALTH_RECOVERY` | 5, 11 | 1, 6, 8, 12 | 5 = 12th-from-6 (end of disease), 11 = 12th-from-12 |

**Decisions**:

- **Closed & versioned**, not runtime config in v1 — the guardrail keeps its own
  in-scope category list (D23); a configurable prediction taxonomy is a later
  concern.
- `WEALTH` and `CAREER` share houses; they are separate matters so a v2 can
  diverge and so the LLM can phrase them differently.
- The exact lists are the contested part of KP; they are **v1**, cited to KSK,
  and SC-006 checks a sample against the printed *Readers* tables.

`explainHouseGrouping(matter)` → `HouseGroups.of(Matter)` → the two sets + the
source string. `Matter.fromKey("marriage")` for the tool; unknown key rejected.

## 2. Dasha significators for a date

Given a `NatalChart` and the reference instant `t` (§4):

- `table = SignificatorTable.of(chart)` (SPEC-003).
- `dasha = DashaTimeline.from(chart.birthData().instant(),
  chart.position(MOON).longitude(), chart.accuracy(), chart.engineVersion())`
  (SPEC-004 — pure, no I/O).
- `running = dasha.running(t, 5)` — the five lords.
- For each running lord `L`: `table.grahaSignificators(L).houses()` — the natal
  houses `L` signifies, with steps.
- **Activated house set**: `{ h : some running lord signifies h }`. Each `h`
  carries its **strength** = the number of the five running lords that signify it
  (1–5), and the set of those lords.

**`lordChangesWithinDay`**: `dasha.running(t − 12h, 5)` and `dasha.running(t + 12h,
5)` — flag true if any level's lord differs from `running`.

## 3. The v1 transit rule

For the reference instant `t`, from `PositionProvider.positions(t)`:

- `moonChain = KpLordage.chainFor(moonLongitude)`, `sunChain =
  KpLordage.chainFor(sunLongitude)` (sidereal, KP-New).
- **v1 rule**: a transiting body **supports** house `H` iff its **sub lord** is a
  natal significator of `H` — i.e. `H ∈ table.grahaSignificators(chain.subLord())
  .houses().keySet()`.
- `moonSupports` / `sunSupports` are those house sets.

**Rationale / citation**: the KP transit principle is "a matter fructifies when
the transiting agent moves through a sign / star / sub of a significator of that
matter" (KSK). v1 takes only the **sub-lord** leg — the finest and most decisive —
and only the Moon (fastest, the "trigger of the day") and the Sun (the
"fortnightly" mover). **v2**: star-lord agreement (sub *and* star both
significators), the transiting Jupiter / Saturn slow cycle, retrogression,
Moon-phase, aspects. All explicitly out of scope.

**`moonSubLordChangesWithinDay`**: compare `KpLordage.chainFor` of the Moon at
`t − 12h` and `t + 12h` — the Moon moves ~13°/day and a sub can be < 1°, so this
is common; the flag tells the LLM the reading is a snapshot.

## 4. The reference instant

**Decision: local noon of the date.** `referenceInstant = date.atTime(12:00)
.toInstant(UTC) − round(longitude / 15 · 3600) s` — the same LMT approximation
SPEC-003's `KpWeekday` uses (the true civil timezone is SPEC-007). Noon is chosen
because it is the middle of the day's activity and keeps the ±12 h window inside
the calendar day at all longitudes.

The reading carries `referenceInstant` and the two within-day change flags (§2,
§3). It does **not** integrate over the day in v1.

**Date before the birth date** → `IllegalArgumentException` (FR-011). A date
outside the ephemeris range → the transit positions come back `REDUCED`; the
reading is still produced (FR-012).

## 5. The v1 verdict rule

For matter `M` with favourable set `F`, obstructive set `O`, the activated house
set `A` (with `strength(h)` for `h ∈ A`, else 0), and the transit-supported set
`T = moonSupports ∪ sunSupports`:

```
favActive   = { h ∈ F : h ∈ A }          favStrength = Σ strength(h) for h ∈ favActive
obsActive   = { h ∈ O : h ∈ A }          obsStrength = Σ strength(h) for h ∈ obsActive
favTriggered = favActive ∩ T
```

| # | Condition (first match wins) | Verdict |
|---|------------------------------|---------|
| 1 | `favActive` and `obsActive` both empty | `QUIET` |
| 2 | `obsStrength > favStrength` | `UNFAVOURABLE` |
| 3 | `favActive` non-empty **and** `favTriggered` non-empty | `FAVOURABLE` |
| 4 | otherwise | `MIXED` |

- **Total**: every input hits exactly one row; the four outcomes partition the
  space (FR-010).
- **Ties** (`obsStrength == favStrength`) do **not** go to `UNFAVOURABLE` — row 2
  is strict — so an evenly-activated matter is `MIXED` (or `FAVOURABLE` if
  triggered).
- **`FAVOURABLE` requires a transit trigger** — dasha ripeness without a transit
  today is `MIXED` ("ripe, not fired"; spec US4 scenario 2).
- **v1 design choice**: strength-sum weighting (not just count), strict `>` for
  the obstructive threshold, transit-trigger requirement for `FAVOURABLE`. Stated
  here and in `core/REFERENCES.md`; a v2 tuning is an `EngineVersion` bump.

## 6. Traceability

Each `MatterVerdict` carries: the verdict, `favourableHit` (= `favActive`),
`obstructiveHit` (= `obsActive`), the `Set<Graha>` of running lords that activated
those houses, and the `Set<TransitBody>` that triggered them. From those plus the
table in §5, a reviewer reconstructs the verdict with no hidden state (FR-017,
SC-004).

## 7. Golden reference

Extend `tools/ephe-crosscheck/compute_golden.py`:

- Replicate §1 (the table), §2 (running lords ∩ significators), §3 (the sub-lord
  transit rule), §4 (the reference instant), §5 (the verdict table).
- Add to one golden file: `expected.daily` for a fixed date + longitude — the
  reference instant, the activated house set with strengths, the Moon / Sun
  supported sets, and the per-matter verdicts with their hits / lords / transits.
- **SC-006**: a human walks one reading by hand (running lords → significations →
  transit → verdict per matter) and checks the §1 table against KSK's *KP
  Readers* house-signification tables.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | House groups | closed enum, favourable + obstructive (12th-from) sets, cited to KSK; v1 |
| 2 | Dasha significators | running lords ∩ `grahaSignificators`; activated set with strength = count of the 5 levels |
| 3 | Transit rule | v1: sub lord of the transiting Moon / Sun is a natal significator; star-lord / slow planets / aspects are v2 |
| 4 | Reference instant | local noon (`date 12:00 − longitude/15 h`); ±12 h change flags; no integration |
| 5 | Verdict rule | 4-row total function over favourable/obstructive activation + transit trigger; strict `>` for UNFAVOURABLE; FAVOURABLE needs a trigger |
| 6 | Traceability | each `MatterVerdict` carries the houses / lords / transits behind it |
| 7 | Golden | replicate the v1 rules; one case; human walk-through + taxonomy check (SC-006) |
