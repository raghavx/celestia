#!/usr/bin/env python3
"""
Independent reference computation for the SPEC-001 / SPEC-002 golden charts.

Computes, for each golden JSON's `birth.utc_instant` (+ `birth.latitude` /
`birth.longitude` for houses):
  - SPEC-001: the sidereal longitude, speed and retrograde flag of the nine KP
    grahas, plus their KP lord chain (sign / sign lord / nakshatra / pada /
    star / sub / sub-sub), using Swiss Ephemeris via **pyswisseph** (an
    independent binding from the Java port the engine will use);
  - SPEC-002: the twelve Placidus house cusps (sidereal, KP), the Ascendant and
    Midheaven, each with its lord chain; and per graha its bhava (cusp-to-cusp,
    half-open) and rasi house (whole sign from the Ascendant).

This is the "authoritative reference" of SC-002 and the seed of SC-001.

Usage:
    pip install -r requirements.txt
    python compute_golden.py PATH...            # verify only (non-zero exit on drift)
    python compute_golden.py PATH... --write     # fill/refresh the `expected` block
    CELESTIA_EPHE_PATH=/path/to/ephe python compute_golden.py ... # use .se1 data

Without .se1 data the script falls back to the Moshier model and records that in
`generated_by`; SC-002's 2-arcsecond claim then does not hold and the golden
files should be regenerated with real data before promoting their status.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import sys
from fractions import Fraction
from pathlib import Path

try:
    import swisseph as swe
except ImportError:
    sys.exit("pyswisseph not installed - run: pip install -r requirements.txt")

_EPHE_PATH = os.environ.get("CELESTIA_EPHE_PATH")
if _EPHE_PATH:
    swe.set_ephe_path(_EPHE_PATH)

# --- KP tables (cite: see core/src/main/java/com/celestia/core/REFERENCES.md) ---

# Vimshottari order starting from Ketu, with dasha years (total 120).
VIMS = [
    ("KETU", 7), ("VENUS", 20), ("SUN", 6), ("MOON", 10), ("MARS", 7),
    ("RAHU", 18), ("JUPITER", 16), ("SATURN", 19), ("MERCURY", 17),
]
VIMS_NAMES = [n for n, _ in VIMS]
VIMS_YEARS = dict(VIMS)
assert sum(VIMS_YEARS.values()) == 120

SIGN_NAMES = [
    "ARIES", "TAURUS", "GEMINI", "CANCER", "LEO", "VIRGO",
    "LIBRA", "SCORPIO", "SAGITTARIUS", "CAPRICORN", "AQUARIUS", "PISCES",
]
SIGN_LORDS = [
    "MARS", "VENUS", "MERCURY", "MOON", "SUN", "MERCURY",
    "VENUS", "MARS", "JUPITER", "SATURN", "SATURN", "JUPITER",
]

NAKSHATRA_NAMES = [
    "ASHWINI", "BHARANI", "KRITTIKA", "ROHINI", "MRIGASHIRA", "ARDRA",
    "PUNARVASU", "PUSHYA", "ASHLESHA", "MAGHA", "PURVA_PHALGUNI", "UTTARA_PHALGUNI",
    "HASTA", "CHITRA", "SWATI", "VISHAKHA", "ANURADHA", "JYESHTHA",
    "MULA", "PURVA_ASHADHA", "UTTARA_ASHADHA", "SHRAVANA", "DHANISHTA", "SHATABHISHA",
    "PURVA_BHADRAPADA", "UTTARA_BHADRAPADA", "REVATI",
]

NAK_DEG = Fraction(40, 3)          # 13 deg 20 min
PADA_DEG = Fraction(10, 3)         # 3 deg 20 min

# Swiss Ephemeris planet ids
GRAHA_SWE = {
    "SUN": swe.SUN, "MOON": swe.MOON, "MARS": swe.MARS, "MERCURY": swe.MERCURY,
    "JUPITER": swe.JUPITER, "VENUS": swe.VENUS, "SATURN": swe.SATURN,
    "RAHU": swe.MEAN_NODE,          # ADR-0005: mean node
}


def _partition(offset: float, width: Fraction, start_lord: str) -> tuple[str, Fraction, Fraction]:
    """Return (lord, sub_start, sub_width) for `offset` in [0, width), half-open,
    lords in Vimshottari order from `start_lord`, widths proportional to years."""
    idx = VIMS_NAMES.index(start_lord)
    acc = Fraction(0)
    for k in range(9):
        lord = VIMS_NAMES[(idx + k) % 9]
        w = Fraction(VIMS_YEARS[lord], 120) * width
        if float(acc) <= offset < float(acc + w):
            return lord, acc, w
        acc += w
    # offset should always land inside; guard for float noise at the top boundary
    lord = VIMS_NAMES[(idx + 8) % 9]
    w = Fraction(VIMS_YEARS[lord], 120) * width
    return lord, width - w, w


def lord_chain(longitude: float) -> dict:
    lon = longitude % 360.0
    sign = int(lon // 30) % 12
    nak = int(lon // float(NAK_DEG)) % 27
    within_nak = lon - nak * float(NAK_DEG)
    pada = min(4, int(within_nak // float(PADA_DEG)) + 1)
    star_lord = VIMS_NAMES[nak % 9]

    sub_lord, sub_start, sub_width = _partition(within_nak, NAK_DEG, star_lord)
    within_sub = within_nak - float(sub_start)
    sub_sub_lord, _, _ = _partition(within_sub, sub_width, sub_lord)

    return {
        "sign": SIGN_NAMES[sign],
        "sign_lord": SIGN_LORDS[sign],
        "nakshatra": NAKSHATRA_NAMES[nak],
        "pada": pada,
        "star_lord": star_lord,
        "sub_lord": sub_lord,
        "sub_sub_lord": sub_sub_lord,
    }


def _jd_ut(iso_utc: str) -> float:
    t = dt.datetime.fromisoformat(iso_utc.replace("Z", "+00:00")).astimezone(dt.timezone.utc)
    _, jd_ut = swe.utc_to_jd(t.year, t.month, t.day, t.hour, t.minute,
                             t.second + t.microsecond / 1e6, swe.GREG_CAL)
    return jd_ut


def _chain_point(lon: float) -> dict:
    """{longitude + full lord chain} for a cusp or an angle."""
    lon %= 360.0
    return {"longitude": round(lon, 6), **lord_chain(lon)}


def _bhava_of(lon: float, cusps: list[float]) -> int:
    """1..12 — the house whose forward arc [cusp n, cusp n+1) contains `lon`."""
    lon %= 360.0
    for n in range(12):
        arc_to_next = (cusps[(n + 1) % 12] - cusps[n]) % 360.0
        arc_to_lon = (lon - cusps[n]) % 360.0
        if arc_to_lon < arc_to_next:
            return n + 1
    return 12  # unreachable for a valid ring


def _rasi_house(graha_lon: float, asc_lon: float) -> int:
    return 1 + ((int(graha_lon // 30) - int(asc_lon // 30)) % 12)


# --- SPEC-003: significators + ruling planets (see research.md) ---

GRAHA_NAMES = VIMS_NAMES  # the nine, in Graha-enum (Vimshottari) order for tie-breaks
NODES = {"RAHU", "KETU"}
# Python date.weekday(): Mon=0 .. Sun=6
_WEEKDAY_NAME = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
_WEEKDAY_LORD = ["MOON", "MARS", "MERCURY", "JUPITER", "VENUS", "SATURN", "SUN"]
_NAK_DEG = 40.0 / 3.0


def compute_significators(out: dict, houses: dict) -> tuple[dict, dict]:
    """Four-step significators per house + the per-graha transpose + node agency."""
    star_lord = {g: out[g]["star_lord"] for g in GRAHA_NAMES}
    sign_lord = {g: out[g]["sign_lord"] for g in GRAHA_NAMES}
    bhava = {g: out[g]["bhava"] for g in GRAHA_NAMES}

    node_agency = {}
    for node in ("RAHU", "KETU"):
        conj = sorted(
            (g for g in GRAHA_NAMES if g not in NODES and bhava[g] == bhava[node]),
            key=GRAHA_NAMES.index,
        )
        agents = sorted(set(conj) | {sign_lord[node], star_lord[node]}, key=GRAHA_NAMES.index)
        node_agency[node] = {
            "conjunct_grahas": conj,
            "sign_lord": sign_lord[node],
            "star_lord": star_lord[node],
            "agents": agents,
        }

    by_house = []
    for house in range(1, 13):
        owner = houses["cusps"][house - 1]["sign_lord"]
        occupants = [g for g in GRAHA_NAMES if bhava[g] == house]

        effective = set(occupants)
        for node in occupants:
            if node in NODES:
                effective |= set(node_agency[node]["agents"])

        step1 = {g for g in GRAHA_NAMES if star_lord[g] in effective}
        step2 = set(effective)
        step3 = {g for g in GRAHA_NAMES if star_lord[g] == owner}
        step4 = {owner}

        steps_by_graha: dict[str, set] = {}
        for step_no, members in ((1, step1), (2, step2), (3, step3), (4, step4)):
            for g in members:
                steps_by_graha.setdefault(g, set()).add(step_no)

        ordered = sorted(
            steps_by_graha.items(),
            key=lambda kv: (min(kv[1]), GRAHA_NAMES.index(kv[0])),
        )
        by_house.append([{"graha": g, "steps": sorted(st)} for g, st in ordered])

    by_graha = {g: {} for g in GRAHA_NAMES}
    for h_index, sigs in enumerate(by_house, start=1):
        for s in sigs:
            by_graha[s["graha"]][str(h_index)] = s["steps"]

    return {"by_house": by_house, "by_graha": by_graha}, node_agency


def _jd_to_iso(jd_ut: float) -> str:
    y, m, d, hour = swe.revjul(jd_ut, swe.GREG_CAL)
    base = dt.datetime(y, m, d, tzinfo=dt.timezone.utc) + dt.timedelta(hours=hour)
    return base.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _sunrise_before(jd_ut: float, lat: float, lon: float):
    """JD(UT) of the latest sunrise <= jd_ut, or None if the Sun does not rise."""
    geopos = (lon, lat, 0.0)
    res, tret = swe.rise_trans(jd_ut - 1.05, swe.SUN, swe.CALC_RISE, geopos)
    if res < 0:
        return None
    r = tret[0]
    for _ in range(3):
        res, tret = swe.rise_trans(r + 1e-6, swe.SUN, swe.CALC_RISE, geopos)
        if res < 0 or tret[0] > jd_ut:
            break
        r = tret[0]
    return r if r <= jd_ut else None


def compute_ruling_planets(judgment: dict, asc_override: float | None = None) -> dict:
    jd = _jd_ut(judgment["utc_instant"])
    lat, lon = judgment["latitude"], judgment["longitude"]
    flags = swe.FLG_SIDEREAL | swe.FLG_SPEED | swe.FLG_SWIEPH

    _cusps, ascmc = swe.houses_ex(jd, lat, lon, b"P", swe.FLG_SIDEREAL)
    asc = ascmc[0] % 360.0 if asc_override is None else asc_override % 360.0
    moon = swe.calc_ut(jd, swe.MOON, flags)[0][0] % 360.0
    rahu = swe.calc_ut(jd, swe.MEAN_NODE, flags)[0][0] % 360.0
    ketu = (rahu + 180.0) % 360.0

    ac, mc = lord_chain(asc), lord_chain(moon)

    sunrise_jd = _sunrise_before(jd, lat, lon)
    if sunrise_jd is None:
        wd = (dt.datetime.fromisoformat(judgment["utc_instant"].replace("Z", "+00:00"))
              + dt.timedelta(hours=lon / 15.0)).weekday()
        sunrise_iso, fallback = None, True
    else:
        y, m, d, _h = swe.revjul(sunrise_jd + (lon / 15.0) / 24.0, swe.GREG_CAL)
        wd = dt.date(y, m, d).weekday()
        sunrise_iso, fallback = _jd_to_iso(sunrise_jd), False
    day_lord = _WEEKDAY_LORD[wd]

    sources: dict[str, set] = {}

    def add(graha: str, src: str) -> None:
        sources.setdefault(graha, set()).add(src)

    add(ac["sign_lord"], "LAGNA_SIGN")
    add(ac["star_lord"], "LAGNA_STAR")
    add(ac["sub_lord"], "LAGNA_SUB")
    add(mc["sign_lord"], "MOON_SIGN")
    add(mc["star_lord"], "MOON_STAR")
    add(mc["sub_lord"], "MOON_SUB")
    add(day_lord, "DAY_LORD")

    asc_sign, asc_nak = int(asc // 30), int(asc // _NAK_DEG)
    moon_sign, moon_nak = int(moon // 30), int(moon // _NAK_DEG)
    for node, nlon in (("RAHU", rahu), ("KETU", ketu)):
        nc = lord_chain(nlon)
        ns, nn = int(nlon // 30), int(nlon // _NAK_DEG)
        if (nc["sign_lord"] in sources or nc["star_lord"] in sources
                or ns in (asc_sign, moon_sign) or nn in (asc_nak, moon_nak)):
            add(node, "NODE")

    return {
        "judgment": dict(judgment),
        "ascendant": {"longitude": round(asc, 6), **ac},
        "moon": {"longitude": round(moon, 6), **mc},
        "sunrise_utc": sunrise_iso,
        "weekday": _WEEKDAY_NAME[wd],
        "day_lord": day_lord,
        "day_lord_fallback": fallback,
        "include_sub_lords": True,
        "planets": [
            {"graha": g, "sources": sorted(sources[g])}
            for g in sorted(sources, key=GRAHA_NAMES.index)
        ],
    }


YEAR_SECONDS = 365 * 86400 + 21600          # 365.25 days, exact (KSK / KP Readers)
CYCLE_SECONDS = 120 * YEAR_SECONDS
_DASHA_LEVELS = ["MAHADASHA", "ANTARDASHA", "PRATYANTARDASHA", "SOOKSHMA", "PRANA"]
RUNNING_QUERY_OFFSET_YEARS = 40             # research.md §4


def _split(start: Fraction, total: Fraction, from_lord: str):
    """Nine (lord, sub_start, sub_end) portions of [start, start+total), in
    Vimshottari order from `from_lord`, spans proportional to the dasha years."""
    idx = VIMS_NAMES.index(from_lord)
    out = []
    cursor = start
    for k in range(9):
        lord = VIMS_NAMES[(idx + k) % 9]
        end = cursor + total * Fraction(VIMS_YEARS[lord], 120)
        out.append((lord, cursor, end))
        cursor = end
    return out


def _iso_from_birth(birth: dt.datetime, seconds: Fraction) -> str:
    us = int(round(seconds * 1_000_000))
    t = birth + dt.timedelta(microseconds=us)
    return t.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def compute_dasha(chart: dict, out: dict) -> dict:
    """SPEC-004: balance of dasha at birth + the running five-lord stack at
    birth + 40 Julian years. 1 year = 365.25 days."""
    birth = dt.datetime.fromisoformat(chart["birth"]["utc_instant"].replace("Z", "+00:00"))
    moon = out["MOON"]["longitude"] % 360.0

    nak = int(moon // float(NAK_DEG)) % 27
    maha_lord = VIMS_NAMES[nak % 9]
    nak_start = nak * NAK_DEG
    frac = (Fraction(moon).limit_denominator(10**12) - nak_start) / NAK_DEG
    maha_years = VIMS_YEARS[maha_lord]
    elapsed_s = frac * maha_years * YEAR_SECONDS
    balance_s = (1 - frac) * maha_years * YEAR_SECONDS

    balance = {
        "maha_lord": maha_lord,
        "elapsed_fraction": round(float(frac), 9),
        "elapsed_days": round(float(frac * maha_years * Fraction(3652500, 10000)), 3),
        "balance_days": round(float((1 - frac) * maha_years * Fraction(3652500, 10000)), 3),
        "maha_start": _iso_from_birth(birth, -elapsed_s),
        "maha_end": _iso_from_birth(birth, balance_s),
    }

    # running stack: q measured in seconds from the (pre-birth) start of the birth Maha
    q = elapsed_s + RUNNING_QUERY_OFFSET_YEARS * YEAR_SECONDS
    periods = []
    # level 1 — periodic 120-year cycle from the birth-Maha start
    cyc = q // CYCLE_SECONDS
    off = q - cyc * CYCLE_SECONDS
    canonical = _split(Fraction(0), Fraction(CYCLE_SECONDS), maha_lord)
    lord, seg_start, seg_end = next(c for c in canonical if c[1] <= off < c[2])
    periods.append(("MAHADASHA", lord,
                    cyc * CYCLE_SECONDS + seg_start, cyc * CYCLE_SECONDS + seg_end))
    # levels 2..5 — recursive nine-way split of the parent
    for level in _DASHA_LEVELS[1:]:
        parent_lord, ps, pe = periods[-1][1], periods[-1][2], periods[-1][3]
        for child in _split(ps, pe - ps, parent_lord):
            if child[1] <= q < child[2]:
                periods.append((level, child[0], child[1], child[2]))
                break

    running = {
        "query_utc": _iso_from_birth(birth, RUNNING_QUERY_OFFSET_YEARS * YEAR_SECONDS),
        "query_offset_years": RUNNING_QUERY_OFFSET_YEARS,
        "lords": [p[1] for p in periods],
        "periods": [
            {
                "level": lvl, "lord": ld,
                # start/end are seconds from the birth-Maha start; render vs birth
                "start": _iso_from_birth(birth, st - elapsed_s),
                "end": _iso_from_birth(birth, en - elapsed_s),
            }
            for (lvl, ld, st, en) in periods
        ],
    }
    return {"year_days": 365.25, "balance": balance, "running": running}


# The one worked ruling-planet example — independent of any birth data.
RP_JUDGMENT = {
    "utc_instant": "2026-01-01T12:00:00Z",
    "latitude": 28.6139,
    "longitude": 77.209,
    "place": "New Delhi, India",
}

# --- SPEC-005: KP horary 1-249 ---

# The one worked horary example — a number + a fixed judgment moment.
HORARY_QUERY = {
    "number": 100,
    "utc_instant": "2026-01-01T12:00:00Z",
    "latitude": 28.6139,
    "longitude": 77.209,
    "place": "New Delhi, India",
}


def _nakshatra_subs(nak_index: int):
    """The 9 (sub_lord, start, end) sub-spans of nakshatra `nak_index`, absolute
    degrees, exact Fraction (== VimshottariPartition.subs)."""
    star_lord = VIMS_NAMES[nak_index % 9]
    base = nak_index * NAK_DEG
    idx = VIMS_NAMES.index(star_lord)
    out, cursor = [], base
    for k in range(9):
        lord = VIMS_NAMES[(idx + k) % 9]
        end = cursor + Fraction(VIMS_YEARS[lord], 120) * NAK_DEG
        out.append((lord, cursor, end))
        cursor = end
    return out


def horary_249_arcs():
    """The 249 arcs: the 243 sub-spans split wherever they strictly cross a 30 deg
    sign cusp, in longitude order. Each: (sub_lord, start, end) exact Fraction."""
    spans = []
    for n in range(27):
        spans.extend(_nakshatra_subs(n))
    sign_cusps = [Fraction(30 * k) for k in range(1, 12)]
    arcs = []
    for lord, s, e in spans:
        pts = [s] + sorted(c for c in sign_cusps if s < c < e) + [e]
        for i in range(len(pts) - 1):
            arcs.append((lord, pts[i], pts[i + 1]))
    arcs.sort(key=lambda a: a[1])
    return arcs


def _armc_from_ascendant(trop_asc_deg: float, lat_deg: float, eps_deg: float) -> float:
    """The RAMC that yields tropical Ascendant `trop_asc_deg` at latitude `lat_deg`
    for obliquity `eps_deg`. Closed-form inversion of the Ascendant equation
    (Meeus, Astronomical Algorithms 2nd ed. ch. 13)."""
    lam, phi, e = math.radians(trop_asc_deg), math.radians(lat_deg), math.radians(eps_deg)
    k = math.sin(e) * math.tan(phi)
    a = math.cos(lam) ** 2 + math.cos(e) ** 2 * math.sin(lam) ** 2
    b = 2 * k * math.cos(lam)
    c = k * k - math.cos(e) ** 2
    rho = (-b + math.sqrt(b * b - 4 * a * c)) / (2 * a)   # positive root -> Ascendant
    cos_t = rho * math.sin(lam)
    sin_t = (-rho * math.cos(lam) - k) / math.cos(e)
    return math.degrees(math.atan2(sin_t, cos_t)) % 360.0


def compute_horary(query: dict) -> dict:
    """SPEC-005: number -> Ascendant, Placidus cusps (RAMC-seeded), planets for the
    judgment instant, per-house significators, ruling planets."""
    arcs = horary_249_arcs()
    assert len(arcs) == 249, f"expected 249 arcs, got {len(arcs)}"
    sub_lord, s, e = arcs[query["number"] - 1]
    asc = float((s + e) / 2) % 360.0

    jd = _jd_ut(query["utc_instant"])
    lat = query["latitude"]
    flags = swe.FLG_SIDEREAL | swe.FLG_SPEED | swe.FLG_SWIEPH

    ph: dict[str, dict] = {}
    for name, pid in GRAHA_SWE.items():
        xx, _ = swe.calc_ut(jd, pid, flags)
        ph[name] = _entry(xx[0], xx[3])
    ph["KETU"] = _entry((ph["RAHU"]["longitude"] + 180.0) % 360.0, ph["RAHU"]["_speed"])
    for v in ph.values():
        v.pop("_speed", None)

    ayan = swe.get_ayanamsa_ut(jd)
    eps = swe.calc_ut(jd, swe.ECL_NUT, swe.FLG_SWIEPH)[0][0]
    armc = _armc_from_ascendant((asc + ayan) % 360.0, lat, eps)
    ct, at = swe.houses_armc(armc, lat, eps, b"P")
    cusp_lons = [(c - ayan) % 360.0 for c in ct[:12]]
    cusp_lons[0] = asc
    mc = (at[1] - ayan) % 360.0

    for v in ph.values():
        v["bhava"] = _bhava_of(v["longitude"], cusp_lons)
        v["rasi_house"] = _rasi_house(v["longitude"], asc)

    houses_h = {"cusps": [dict(house=i + 1, **_chain_point(cusp_lons[i])) for i in range(12)]}
    significators, _node = compute_significators(ph, houses_h)

    return {
        "query": dict(query),
        "ascendant": _chain_point(asc),
        "sub_lord": sub_lord,
        "midheaven": _chain_point(mc),
        "cusps": houses_h["cusps"],
        "placements": {
            g: {"longitude": ph[g]["longitude"], "bhava": ph[g]["bhava"],
                "rasi_house": ph[g]["rasi_house"]}
            for g in GRAHA_NAMES
        },
        "significators": significators,
        "ruling_planets": compute_ruling_planets(query, asc_override=asc),
    }


def compute_houses(chart: dict) -> dict:
    """SPEC-002: 12 Placidus cusps + Ascendant + Midheaven, sidereal KP."""
    jd = _jd_ut(chart["birth"]["utc_instant"])
    lat = chart["birth"]["latitude"]
    lon = chart["birth"]["longitude"]
    cusps, ascmc = swe.houses_ex(jd, lat, lon, b"P", swe.FLG_SIDEREAL)
    cusp_list = list(cusps[:12])
    cusp_list[0] = ascmc[0]  # cusp 1 := Ascendant, bit-identical (SPEC-002 FR-003)
    return {
        "cusps": [dict(house=i + 1, **_chain_point(cusp_list[i])) for i in range(12)],
        "angles": {
            "ascendant": _chain_point(ascmc[0]),
            "midheaven": _chain_point(ascmc[1]),
        },
        "_cusp_longitudes": [c % 360.0 for c in cusp_list],
        "_asc": ascmc[0] % 360.0,
    }


# --- SPEC-006: daily prediction ruleset (v1 - see specs/006-daily-prediction/research.md) ---

# research.md §1: matter -> (favourable houses, obstructive houses)
HOUSE_GROUPS = {
    "MARRIAGE": ({2, 7, 11}, {1, 6, 10}),
    "CAREER": ({2, 6, 10, 11}, {1, 5, 9, 12}),
    "WEALTH": ({2, 6, 10, 11}, {1, 5, 9, 12}),
    "EDUCATION": ({4, 9, 11}, {3, 8, 10}),
    "CHILDREN": ({2, 5, 11}, {1, 4, 10}),
    "PROPERTY": ({4, 11, 12}, {3, 8, 10}),
    "TRAVEL": ({3, 9, 12}, {4, 8}),
    "LITIGATION": ({6, 11}, {5, 8, 12}),
    "HEALTH_RECOVERY": ({5, 11}, {1, 6, 8, 12}),
}


def _running_lords_at(birth: dt.datetime, moon_lon: float, when: dt.datetime) -> list[str]:
    """The five running Vimshottari lords (Maha..Prana) at `when`. Mirrors
    compute_dasha's periodic-cycle walk (SPEC-004)."""
    nak = int(moon_lon // float(NAK_DEG)) % 27
    maha_lord = VIMS_NAMES[nak % 9]
    frac = (Fraction(moon_lon).limit_denominator(10**12) - nak * NAK_DEG) / NAK_DEG
    elapsed_s = frac * VIMS_YEARS[maha_lord] * YEAR_SECONDS
    q = elapsed_s + Fraction(round((when - birth).total_seconds()))
    cyc = q // CYCLE_SECONDS
    off = q - cyc * CYCLE_SECONDS
    canonical = _split(Fraction(0), Fraction(CYCLE_SECONDS), maha_lord)
    lord, seg_start, seg_end = next(c for c in canonical if c[1] <= off < c[2])
    periods = [(lord, cyc * CYCLE_SECONDS + seg_start, cyc * CYCLE_SECONDS + seg_end)]
    for _ in _DASHA_LEVELS[1:]:
        plord, ps, pe = periods[-1]
        for child in _split(ps, pe - ps, plord):
            if child[1] <= q < child[2]:
                periods.append((child[0], child[1], child[2]))
                break
    return [p[0] for p in periods]


def _transit_lon(when: dt.datetime, planet: int) -> float:
    _, jd = swe.utc_to_jd(when.year, when.month, when.day, when.hour, when.minute,
                          when.second + when.microsecond / 1e6, swe.GREG_CAL)
    xx, _ = swe.calc_ut(jd, planet, swe.FLG_SIDEREAL | swe.FLG_SWIEPH)
    return xx[0] % 360.0


def compute_daily(chart: dict, out: dict, significators: dict) -> dict:
    """SPEC-006: the v1 daily reading at birth + 40 Julian years, local noon."""
    birth = dt.datetime.fromisoformat(chart["birth"]["utc_instant"].replace("Z", "+00:00"))
    lon = chart["birth"]["longitude"]
    by_graha = significators["by_graha"]

    ref_approx = birth + dt.timedelta(seconds=40 * YEAR_SECONDS)
    date = (ref_approx + dt.timedelta(hours=lon / 15.0)).date()
    lmt_offset_s = round(lon / 15.0 * 3600.0)
    ref = (dt.datetime(date.year, date.month, date.day, 12, tzinfo=dt.timezone.utc)
           - dt.timedelta(seconds=lmt_offset_s))
    before, after = ref - dt.timedelta(hours=12), ref + dt.timedelta(hours=12)

    moon_lon = out["MOON"]["longitude"]
    running = _running_lords_at(birth, moon_lon, ref)
    running_before = _running_lords_at(birth, moon_lon, before)
    running_after = _running_lords_at(birth, moon_lon, after)
    lord_changes = running != running_before or running != running_after

    # activated house set: distinct running lords that signify each house
    activated: dict[int, set] = {}
    for lord in running:
        for h in by_graha.get(lord, {}):
            activated.setdefault(int(h), set()).add(lord)

    # transit: sub lord of the transiting Moon / Sun -> its natal significations
    moon_chain = lord_chain(_transit_lon(ref, swe.MOON))
    sun_chain = lord_chain(_transit_lon(ref, swe.SUN))
    moon_before = lord_chain(_transit_lon(before, swe.MOON))
    moon_after = lord_chain(_transit_lon(after, swe.MOON))
    moon_supports = {int(h) for h in by_graha.get(moon_chain["sub_lord"], {})}
    sun_supports = {int(h) for h in by_graha.get(sun_chain["sub_lord"], {})}
    transit_supported = moon_supports | sun_supports
    body_supports = {"MOON": moon_supports, "SUN": sun_supports}

    verdicts = []
    for matter, (fav, obs) in HOUSE_GROUPS.items():
        fav_active = {h for h in fav if h in activated}
        obs_active = {h for h in obs if h in activated}
        fav_strength = sum(len(activated[h]) for h in fav_active)
        obs_strength = sum(len(activated[h]) for h in obs_active)
        fav_triggered = fav_active & transit_supported
        if not fav_active and not obs_active:
            v = "QUIET"
        elif obs_strength > fav_strength:
            v = "UNFAVOURABLE"
        elif fav_active and fav_triggered:
            v = "FAVOURABLE"
        else:
            v = "MIXED"
        hit = fav_active | obs_active
        lords = sorted({lord for h in hit for lord in activated.get(h, ())}, key=GRAHA_NAMES.index)
        transits = sorted(b for b, s in body_supports.items() if fav_triggered & s)
        verdicts.append({
            "matter": matter, "verdict": v,
            "favourable_hit": sorted(fav_active), "obstructive_hit": sorted(obs_active),
            "lords": lords, "transits": transits,
        })

    return {
        "date": date.isoformat(),
        "longitude": lon,
        "reference_instant": ref.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "dasha": {
            "running_lords": running,
            "significations_by_lord": {lord: by_graha.get(lord, {}) for lord in dict.fromkeys(running)},
            "activated": [
                {"house": h, "strength": len(activated[h]),
                 "lords": sorted(activated[h], key=GRAHA_NAMES.index)}
                for h in sorted(activated)
            ],
            "lord_changes_within_day": lord_changes,
        },
        "transit": {
            "moon_sub_lord": moon_chain["sub_lord"],
            "sun_sub_lord": sun_chain["sub_lord"],
            "moon_supports": sorted(moon_supports),
            "sun_supports": sorted(sun_supports),
            "moon_sub_lord_changes_within_day":
                moon_before["sub_lord"] != moon_chain["sub_lord"]
                or moon_after["sub_lord"] != moon_chain["sub_lord"],
        },
        "verdicts": verdicts,
    }


def compute(chart: dict) -> tuple[dict, dict, dict, dict, dict, dict, str]:
    swe.set_sid_mode(swe.SIDM_KRISHNAMURTI, 0, 0)   # ADR-0003: constant value 5
    jd = _jd_ut(chart["birth"]["utc_instant"])

    flags = swe.FLG_SIDEREAL | swe.FLG_SPEED | swe.FLG_SWIEPH

    out: dict[str, dict] = {}
    model = "SWIEPH"
    for name, pid in GRAHA_SWE.items():
        xx, retflag = swe.calc_ut(jd, pid, flags)
        # Without .se1 data, SWIEPH silently falls back to Moshier and sets the
        # MOSEPH bit in the return flag. Record honestly.
        if retflag & swe.FLG_MOSEPH:
            model = "MOSEPH (SWIEPH requested; no .se1 data - set CELESTIA_EPHE_PATH)"
        lon, lon_speed = xx[0], xx[3]
        out[name] = _entry(lon, lon_speed)

    rahu_lon = out["RAHU"]["longitude"]
    rahu_speed = out["RAHU"]["_speed"]
    out["KETU"] = _entry((rahu_lon + 180.0) % 360.0, rahu_speed)

    for e in out.values():
        e.pop("_speed", None)

    # SPEC-002: houses, then bhava + rasi house per graha
    houses = compute_houses(chart)
    cusp_lons = houses.pop("_cusp_longitudes")
    asc = houses.pop("_asc")
    for name, e in out.items():
        e["bhava"] = _bhava_of(e["longitude"], cusp_lons)
        e["rasi_house"] = _rasi_house(e["longitude"], asc)

    # SPEC-003: significators + node agency
    significators, node_agency = compute_significators(out, houses)

    # SPEC-004: Vimshottari dasha
    dasha = compute_dasha(chart, out)

    # SPEC-006: daily prediction
    daily = compute_daily(chart, out, significators)

    swe_ver = getattr(swe, "version", "unknown")
    return (out, houses, significators, node_agency, dasha, daily,
            f"pyswisseph {swe_ver} / {model} / kp-crosscheck 0.6")


def _entry(lon: float, speed: float) -> dict:
    lon %= 360.0
    c = lord_chain(lon)
    return {
        "longitude": round(lon, 6),
        "sign": c["sign"], "sign_lord": c["sign_lord"],
        "nakshatra": c["nakshatra"], "pada": c["pada"],
        "star_lord": c["star_lord"], "sub_lord": c["sub_lord"],
        "sub_sub_lord": c["sub_sub_lord"],
        "retrograde": speed < 0, "_speed": speed,
    }


TOL_DEG = 2.0 / 3600.0  # 2 arc-seconds


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paths", nargs="+", type=Path)
    ap.add_argument("--write", action="store_true", help="fill the expected block")
    args = ap.parse_args()

    exit_code = 0
    for p in args.paths:
        chart = json.loads(p.read_text())
        computed, houses, significators, node_agency, dasha, daily, generated_by = compute(chart)
        prior = chart.get("expected", {}).get("grahas", {})
        prior_cusps = chart.get("expected", {}).get("cusps", [])
        prior_sig = chart.get("expected", {}).get("significators", {}).get("by_house", [])

        drift = []
        for name, e in computed.items():
            old = prior.get(name, {})
            if old.get("longitude") is not None:
                if abs(old["longitude"] - e["longitude"]) > TOL_DEG:
                    drift.append(f"{name} longitude {old['longitude']} -> {e['longitude']}")
                for k in ("sign_lord", "star_lord", "sub_lord", "sub_sub_lord",
                          "nakshatra", "pada", "bhava", "rasi_house"):
                    if old.get(k) is not None and old[k] != e[k]:
                        drift.append(f"{name} {k} {old[k]} -> {e[k]}")
        for i, c in enumerate(houses["cusps"]):
            old = prior_cusps[i] if i < len(prior_cusps) else {}
            if old.get("longitude") is not None:
                if abs(old["longitude"] - c["longitude"]) > 60 * TOL_DEG:  # 1 arc-minute
                    drift.append(f"cusp {i+1} longitude {old['longitude']} -> {c['longitude']}")
                if old.get("sub_lord") not in (None, c["sub_lord"]):
                    drift.append(f"cusp {i+1} sub_lord {old['sub_lord']} -> {c['sub_lord']}")
        for i, sigs in enumerate(significators["by_house"], start=1):
            old = prior_sig[i - 1] if i - 1 < len(prior_sig) else None
            if old is not None and old != sigs:
                drift.append(f"house {i} significators {old} -> {sigs}")
        prior_dasha = chart.get("expected", {}).get("dasha", {})
        if prior_dasha.get("balance", {}).get("maha_lord") is not None:
            ob, nb = prior_dasha["balance"], dasha["balance"]
            if ob["maha_lord"] != nb["maha_lord"]:
                drift.append(f"dasha maha_lord {ob['maha_lord']} -> {nb['maha_lord']}")
            if abs(ob["balance_days"] - nb["balance_days"]) > 1.0:
                drift.append(f"dasha balance_days {ob['balance_days']} -> {nb['balance_days']}")
            if prior_dasha.get("running", {}).get("lords") not in (None, dasha["running"]["lords"]):
                drift.append(f"dasha running lords {prior_dasha['running']['lords']} -> {dasha['running']['lords']}")

        if args.write:
            expected = {
                "generated_by": generated_by,
                "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
                "grahas": computed,
                "cusps": houses["cusps"],
                "angles": houses["angles"],
                "significators": significators,
                "node_agency": node_agency,
                "dasha": dasha,
            }
            if chart.get("expected", {}).get("ruling_planets") is not None or chart["id"] == "obama-1961":
                expected["ruling_planets"] = compute_ruling_planets(RP_JUDGMENT)
            if chart.get("expected", {}).get("horary") is not None or chart["id"] == "obama-1961":
                expected["horary"] = compute_horary(HORARY_QUERY)
            if chart.get("expected", {}).get("daily") is not None or chart["id"] == "obama-1961":
                expected["daily"] = daily
            chart["expected"] = expected
            v = chart.setdefault("verification", {})
            v["positions_reference"] = generated_by
            v["houses_reference"] = generated_by
            v["significators_reference"] = generated_by
            v["dasha_reference"] = generated_by
            v.setdefault("significators_human_check", "pending - see golden/README.md (SC-006)")
            v.setdefault("dasha_human_check", "pending - see golden/README.md (SC-006)")
            if "horary" in expected:
                v["horary_reference"] = generated_by
                v.setdefault("horary_human_check", "pending - see golden/README.md (SC-006)")
            if "daily" in expected:
                v["daily_reference"] = generated_by
                v.setdefault("daily_human_check", "pending - see golden/README.md (SC-006)")
            if v.get("status") == "birth_data_sourced":
                v["status"] = "expected_generated"
            p.write_text(json.dumps(chart, indent=2) + "\n")
            print(f"{p.name}: written ({generated_by})")
        else:
            if any(vv.get("longitude") is not None for vv in prior.values()):
                if drift:
                    exit_code = 1
                    print(f"{p.name}: DRIFT\n  " + "\n  ".join(drift))
                else:
                    print(f"{p.name}: ok ({generated_by})")
            else:
                print(f"{p.name}: expected block is empty - run with --write")

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
