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


def compute(chart: dict) -> tuple[dict, dict, str]:
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

    swe_ver = getattr(swe, "version", "unknown")
    return out, houses, f"pyswisseph {swe_ver} / {model} / kp-crosscheck 0.2"


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
        computed, houses, generated_by = compute(chart)
        prior = chart.get("expected", {}).get("grahas", {})
        prior_cusps = chart.get("expected", {}).get("cusps", [])

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

        if args.write:
            chart["expected"] = {
                "generated_by": generated_by,
                "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
                "grahas": computed,
                "cusps": houses["cusps"],
                "angles": houses["angles"],
            }
            v = chart.setdefault("verification", {})
            v["positions_reference"] = generated_by
            v["houses_reference"] = generated_by
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
