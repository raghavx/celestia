# Golden charts — KP engine correctness oracle (SPEC-001 / 002 / 003)

These files are the reference data for the golden-chart snapshot suites. Each
pins, for one birth, every value the engine must reproduce:

- **SPEC-001** (SC-001/002): the sidereal longitude and full KP lord chain of the
  nine grahas.
- **SPEC-002** (SC-001/002): the twelve Placidus house cusps and the
  Ascendant/Midheaven with their lord chains, and each graha's `bhava`
  (cusp-to-cusp) and `rasi_house` (whole sign from the Ascendant).
- **SPEC-003** (SC-001…003): `expected.significators.by_house` (12 lists of
  `{graha, steps}`), `expected.significators.by_graha` (the transpose),
  `expected.node_agency` (Rahu / Ketu agents), and — in `obama-1961.json` only —
  `expected.ruling_planets` (a fixed judgment instant + place, independent of the
  birth data → the RP set with sources, the resolved KP weekday, day lord, and
  sunrise instant).

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
| House system | Placidus, sidereal (`swe_houses_ex`, `hsys='P'`, `SEFLG_SIDEREAL`) | ADR-0004 / SPEC-002 |
| Cusp 1 | = the Ascendant (`ascmc[0]`), bit-identical | SPEC-002 FR-003 |
| Bhava | forward arc `[cusp n, cusp n+1)`, half-open, wrap-aware | SPEC-002 research §3 |
| Rasi house | `1 + ((graha.sign − asc.sign) mod 12)` | SPEC-002 research §4 |
| Significators | 4 steps (star of effective occupants / effective occupants / star of owner / owner); a node occupant folds its agents (conjunct non-nodes + sign lord + star lord) into "effective occupants"; ordered by strongest step then Graha ordinal | SPEC-003 research §1 |
| Ruling planets | lagna & Moon sign/star/sub lords + day lord (KP weekday, sunrise-to-sunrise) + node by shared sign/nakshatra; `include_sub_lords` on | SPEC-003 research §3–4 |
| Sunrise | `swe_rise_trans`, `SE_CALC_RISE`, Sun's upper limb + refraction | SPEC-003 research §4 |

Birth `latitude` / `longitude` (decimal degrees, + = N / E) are the house inputs.

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
    "generated_by": "pyswisseph 2.10.03 / SWIEPH / kp-crosscheck 0.2",
    "generated_at": "…",
    "grahas": {
      "SUN":  { "longitude": …, "sign": …, "sign_lord": …, "nakshatra": …, "pada": …,
                "star_lord": …, "sub_lord": …, "sub_sub_lord": …, "retrograde": …,
                "bhava": …,        // SPEC-002: 1..12, cusp-to-cusp
                "rasi_house": … }, // SPEC-002: 1..12, whole sign from the Ascendant
      "MOON": { … }, "…": { … }, "KETU": { … }
    },
    "cusps": [
      { "house": 1, "longitude": …, "sign": …, "sign_lord": …, "nakshatra": …,
        "pada": …, "star_lord": …, "sub_lord": …, "sub_sub_lord": … },
      // … houses 2..12
    ],
    "angles": {
      "ascendant": { "longitude": …, /* + lord chain */ },
      "midheaven": { "longitude": …, /* + lord chain */ }
    },
    "significators": { "by_house": [ … ], "by_graha": { … } },   // SPEC-003
    "node_agency": { "RAHU": { … }, "KETU": { … } },             // SPEC-003
    "ruling_planets": { … },                                     // SPEC-003 (obama-1961 only)
    "horary": {                                                  // SPEC-005 (obama-1961 only)
      "query": { "number": 100, "utc_instant": …, "latitude": …, "longitude": … },
      "sub_lord": …, "ascendant": { … chain … }, "midheaven": { … chain … },
      "cusps": [ { "house": 1, … chain … }, … ],                 // Placidus, RAMC-seeded; cusp 1 = the number's Ascendant
      "placements": { "SUN": { "longitude": …, "bhava": …, "rasi_house": … }, … },
      "significators": { "by_house": [ … ], "by_graha": { … } }, // of the horary chart
      "ruling_planets": { … }                                    // with the number's Ascendant for the lagna lords
    },
    "daily": {                                                   // SPEC-006 (obama-1961 only)
      "date": "…", "longitude": …,                               // birth + 40 Julian years, birth longitude
      "reference_instant": "…",                                  // local noon of that date
      "dasha": { "running_lords": [ … 5 … ],
                 "significations_by_lord": { "<lord>": { "<house>": [steps] } },
                 "activated": [ { "house": …, "strength": 1..5, "lords": [ … ] } ],
                 "lord_changes_within_day": … },
      "transit": { "moon_sub_lord": …, "sun_sub_lord": …,
                   "moon_supports": [ … ], "sun_supports": [ … ],
                   "moon_sub_lord_changes_within_day": … },
      "verdicts": [ { "matter": "MARRIAGE", "verdict": "FAVOURABLE|MIXED|UNFAVOURABLE|QUIET",
                      "favourable_hit": [ … ], "obstructive_hit": [ … ],
                      "lords": [ … ], "transits": [ "MOON"|"SUN" ] } ]
    },
    "dasha": {                                                   // SPEC-004
      "year_days": 365.25,                                       // KSK / KP Readers
      "balance": { "maha_lord": …, "elapsed_fraction": …,
                   "elapsed_days": …, "balance_days": …,
                   "maha_start": …, "maha_end": … },
      "running": {                                               // stack at birth + 40 Julian years
        "query_utc": …, "query_offset_years": 40,
        "lords": [ "MAHADASHA lord", …, "PRANA lord" ],
        "periods": [ { "level": "MAHADASHA", "lord": …, "start": …, "end": … }, … ]
      }
    }
  },
  "verification": {
    "positions_reference": "…",
    "lordchain_human_check": "…which grahas checked against which published KP source…",
    "significators_human_check": "…SPEC-003 SC-006…",
    "dasha_human_check": "…SPEC-004 SC-006: birth Maha lord + balance vs a KP text…",
    "horary_human_check": "…SPEC-005 SC-006…",
    "daily_human_check": "…SPEC-006 SC-006: one reading walked by hand + the house-group table vs KSK…",
    "status": "birth_data_sourced | expected_generated | human_verified"
  }
}
```

`longitude` is stored to 1e-6° (≈ 3.6 mas) — finer than any tolerance — so the
snapshot is exact for `DeterminismTest` and the numeric tolerance (SC-002) is
applied by the test, not the file.

`expected.generated_at` is provenance only — it changes on every regeneration and
is **not** read by any test. `expected.grahas`, `expected.cusps` and
`expected.angles` are deterministic across regenerations with the same pyswisseph
+ data version (verified).

## Verification protocol

1. **Birth data** — only charts with a birth-record ("AA") or otherwise
   well-attested time. `utc_instant` is the single input; `utc_derivation` shows
   the local→UTC step (this is SPEC-007's job in production — the golden files
   pre-compute it and document it).
2. **Positions & cusps** — `tools/ephe-crosscheck/compute_golden.py` computes the
   nine sidereal longitudes and the twelve Placidus cusps + Asc/MC with
   **pyswisseph** (an independent binding of Swiss Ephemeris) using the settings
   above, and fills `expected`. This is the "authoritative reference" of SC-002.
3. **Lord chain** — the same script derives every chain (graha and cusp) from the
   longitudes, and the bhava / rasi house from the cusps. Because
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
(`kp-crosscheck 0.6`) with **pyswisseph 2.10.03** and real Swiss Ephemeris data
(`sepl_18.se1`, `semo_18.se1`, provisioned by `scripts/fetch-ephe.sh`), and
include the SPEC-002 cusps / angles / bhava / rasi, the SPEC-003 significators /
node agency / ruling planets, the SPEC-004 dasha balance / running stack, the
SPEC-005 horary case, and the SPEC-006 daily reading. Sanity-checked against public
Vedic-chart knowledge: Einstein Sun Pisces / Moon Scorpio / **Gemini Ascendant**
/ Sun in the 10th, Jobs Sun Aquarius / Mercury retrograde / **Leo Ascendant**,
Obama Sun Cancer / **Capricorn Ascendant** — all consistent.

### Still to do on these charts

1. **`lordchain_human_check`** — read the Sun and Moon lord chains and one cuspal
   sub lord for at least one chart off a published KP source and record it.
2. **`significators_human_check` (SPEC-003 SC-006)** — currently `pending`. For
   **one** chart (recommend `obama-1961`, an unambiguous AA chart), transcribe the
   **twelve per-house significator lists** from an independent source and record
   which houses / planets matched. An independent source must be set to
   **KP-New ayanamsa (SE constant 5) + mean node + Placidus** — e.g. a KP textbook
   worked example, or two agreeing mainstream KP tools (Jagannath Hora's KP
   significators view, or an established KP website). This is the only check that
   catches a wrong *understanding* of the four-step rule or the node-agency fold
   (`compute_golden.py` and the Java engine encode the same rules).
3. **`dasha_human_check` (SPEC-004 SC-006)** — currently `pending`. For **one**
   chart, transcribe the **birth Mahadasha lord and the balance** (years / months
   / days, or a start date) from a KP source set to **KP-New ayanamsa + mean
   node** — a KP textbook worked example, or two agreeing mainstream KP tools —
   and record it. `compute_golden.py` uses **1 Vimshottari year = 365.25 days**
   (KSK / *KP Readers*); a reference on a 360-day year will disagree by ~1.5 % and
   is not a valid cross-check.
4. **`horary_human_check` (SPEC-005 SC-006)** — currently `pending`. Cross-check
   (a) a sample of ~10 rows of the derived 249 arc table (number → sub lord +
   degree range) against K. S. Krishnamurti's published horary table, and (b) the
   cast chart's cusp 1 + one other cuspal sub lord for `expected.horary.query`
   against a mainstream KP horary tool set to KP-New ayanamsa + Placidus. The 249
   arcs and the RAMC-seeded cusps are *derived* by the same rule the engine uses,
   so this is the only independent check.
5. **`daily_human_check` (SPEC-006 SC-006)** — currently `pending`. (a) Walk the
   `expected.daily` reading by hand for **one** chart — running lords → each
   lord's natal significations → the transiting Moon / Sun sub lord → the verdict
   per matter — and confirm it reconstructs. (b) Check the `HOUSE_GROUPS` table
   (favourable / 12th-from-obstructive houses per matter) against K. S.
   Krishnamurti's *KP Readers* house-signification tables. The v1 ruleset is
   *derived* by the same rules the engine uses, so this is the only independent
   check. The whole ruleset is **v1** — see
   `specs/006-daily-prediction/research.md`.
6. **4th chart (wanted):** a KP-textbook worked example where the planet + cusp
   lords **and significators / dasha balance** are printed — add as
   `<book-slug>.json` with the citation.

`expected.daily` uses `date = birthDate + 40 Julian years` at the chart's birth
longitude, and the reference instant is local noon of that date.

The `expected.horary` block uses the fixed query `{number: 100, 2026-01-01T12:00Z,
New Delhi}`; its Ascendant is the **midpoint** of arc 100 and its other cusps come
from the RAMC that yields that Ascendant at the judgment latitude (Meeus's
closed-form inversion), converted sidereal↔tropical with the instant's ayanamsa.

The `expected.dasha.running` stack is taken at **`birth + 40 Julian years`**
(`40 × 31 557 600` s) — a fixed offset that lands every current chart in a
non-first Mahadasha and exercises all five levels.

### Charts considered and dropped

- `clinton-1946` — dropped in favour of `jobs-1955`: Clinton's Rodden rating and
  exact minute are less certain (needs an astro.com confirmation), and his Moon
  sits on the Aries/Taurus cusp where a small time error flips the sign.
