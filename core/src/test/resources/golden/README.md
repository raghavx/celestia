# Golden charts — KP engine correctness oracle (SPEC-001)

These files are the reference data for the golden-chart snapshot suite
(spec.md SC-001, SC-002). Each pins, for one birth instant, every value the
engine must reproduce: the sidereal longitude and the full KP lord chain of all
nine grahas.

**Nothing in `expected` is filled from memory or by hand.** Values are produced by
an independent computation (`tools/ephe-crosscheck/`) and, for at least one chart,
a lord chain is additionally checked against a published KP source. See
_Verification protocol_ below.

## Engine settings (identical for every chart)

| Setting | Value | Source |
|---------|-------|--------|
| Ayanamsa | Swiss Ephemeris `SE_SIDM_KRISHNAMURTI` = **5** | ADR-0003 |
| Node | **mean** node (`SE_MEAN_NODE`); Ketu = Rahu + 180° | ADR-0005 |
| Zodiac | sidereal, geocentric, apparent positions | SPEC-001 FR-002 |
| Positions flags | `SEFLG_SIDEREAL | SEFLG_SPEED` (+ `SWIEPH`, or `MOSEPH` fallback) | research.md §1 |
| Nakshatra span | 13°20′ from 0° Aries; pada 3°20′ | FR-007 |
| Sub / sub-sub | Vimshottari proportion, order from the parent lord, half-open `[start,end)` | FR-009–FR-011 |

House cusps / Ascendant are **not** in these files — SPEC-001 is positions +
lordage only. (They arrive with SPEC-002 and its own golden data.)

## File format

One JSON object per chart, `<id>.json`:

```jsonc
{
  "id": "einstein-1879",
  "subject": "Albert Einstein",
  "birth": {
    "local_datetime": "1879-03-14T11:30:00",
    "local_time_basis": "…how the local clock time is defined…",
    "place": "…",
    "latitude": 48.39841,          // degrees, + = N   (informational; positions are geocentric)
    "longitude": 9.99155,          // degrees, + = E
    "utc_instant": "1879-03-14T10:50:02Z",   // THE input to the engine
    "utc_derivation": "…shows how local → UTC was done…"
  },
  "sources": [ { "type": "birth_record", "rodden_rating": "AA", "citation": "…", "url": "…" } ],
  "engine_settings": { "ayanamsa": "SE_SIDM_KRISHNAMURTI (5)", "node": "mean", "zodiac": "sidereal apparent geocentric" },
  "expected": {
    "generated_by": null,          // e.g. "pyswisseph 2.10.03.2 / ephe DE441 / kp-crosscheck 0.1"
    "generated_at": null,
    "grahas": {
      "SUN":  { "longitude": null, "sign": null, "sign_lord": null, "nakshatra": null, "pada": null,
                "star_lord": null, "sub_lord": null, "sub_sub_lord": null, "retrograde": null },
      "MOON": { … }, "MARS": { … }, "MERCURY": { … }, "JUPITER": { … }, "VENUS": { … },
      "SATURN": { … }, "RAHU": { … }, "KETU": { … }
    }
  },
  "verification": {
    "positions_reference": "…",
    "lordchain_human_check": "…which grahas checked against which published KP source…",
    "status": "birth_data_sourced | expected_generated | human_verified"
  }
}
```

`longitude` is stored to 1e-6° (≈ 3.6 mas) — finer than any tolerance — so the
snapshot is exact for `DeterminismTest` and the numeric tolerance (SC-002) is
applied by the test, not the file.

`expected.generated_at` is provenance only — it changes on every regeneration and
is **not** read by any test. `expected.grahas` is deterministic across
regenerations with the same pyswisseph + data version (verified).

## Verification protocol

1. **Birth data** — only charts with a birth-record ("AA") or otherwise
   well-attested time. `utc_instant` is the single input; `utc_derivation` shows
   the local→UTC step (this is SPEC-007's job in production — the golden files
   pre-compute it and document it).
2. **Positions** — `tools/ephe-crosscheck/compute_golden.py` computes the nine
   sidereal longitudes with **pyswisseph** (an independent binding of Swiss
   Ephemeris) using the settings above, and fills `expected`. This is the
   "authoritative reference" of SC-002.
3. **Lord chain** — the same script derives the chain from the longitudes. Because
   our Java implementation and the script will encode the *same* KP division rule,
   this alone does not catch a misunderstanding of the rule. So for **≥ 1 chart**,
   the Sun and Moon lord chains are also read off a published KP source (a KP
   textbook worked example, or two independent mainstream KP websites that agree)
   and recorded in `verification.lordchain_human_check`.
4. **Two-source rule** — any single-sourced value is flagged in
   `verification`; the snapshot test may apply a looser tolerance or skip it with
   a documented reason.

## Regenerating

```bash
cd tools/ephe-crosscheck
python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
python compute_golden.py ../../core/src/test/resources/golden/*.json --write    # fill expected
python compute_golden.py ../../core/src/test/resources/golden/*.json            # verify only (CI-style)
```

A change in `expected` after regeneration means the ephemeris data version, the
pyswisseph version, or the ayanamsa setting changed — investigate before
committing (it is a reference change, not a code change).

## Current charts

| id | subject | instant (UTC) | rating | status |
|----|---------|---------------|--------|--------|
| `einstein-1879` | Albert Einstein | 1879-03-14T10:50:02Z | AA (birth certificate) | expected_generated |
| `jobs-1955` | Steve Jobs | 1955-02-25T03:15:00Z | AA (birth certificate) | expected_generated |
| `obama-1961` | Barack Obama | 1961-08-05T05:24:00Z | AA (birth certificate) | expected_generated |

`expected` blocks were generated by `tools/ephe-crosscheck/compute_golden.py`
with **pyswisseph 2.10.03** and real Swiss Ephemeris data (`sepl_18.se1`,
`semo_18.se1`, provisioned by `scripts/fetch-ephe.sh`). Sanity-checked against
public Vedic-chart knowledge: Einstein Sun Pisces / Moon Scorpio, Jobs Sun
Aquarius / Mercury retrograde, Obama Sun Cancer — all consistent.

### Still to do on these charts

1. **`human_verified` status** — read the Sun and Moon lord chains for at least
   one chart off a published KP source (KP textbook worked example, or two
   agreeing mainstream KP websites set to KP ayanamsa + mean node) and record it
   in `verification.lordchain_human_check`. This is the only check that can catch a
   wrong *understanding* of the sub-lord division (the script and the engine both
   encode the same rule).
2. **4th chart (wanted):** a KP-textbook worked example where the lords are
   printed — add as `<book-slug>.json` with the citation. Gives a fully
   independent human reference for the lord-chain rule.

### Charts considered and dropped

- `clinton-1946` — dropped in favour of `jobs-1955`: Clinton's Rodden rating and
  exact minute are less certain (needs an astro.com confirmation), and his Moon
  sits on the Aries/Taurus cusp where a small time error flips the sign.
