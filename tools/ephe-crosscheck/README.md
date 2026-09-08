# ephe-crosscheck

An **independent** reference computation for the SPEC-001 golden charts. Not built
by `./mvnw verify` and not shipped — a developer tool.

It uses **pyswisseph** (a Python binding of Swiss Ephemeris, a different
implementation lineage from the Java port the engine will use) to compute the nine
grahas' sidereal longitudes, and derives the KP lord chain from them with exact
rational arithmetic. Agreement between this tool and the Java engine is the
"authoritative reference" of spec.md **SC-002**; disagreement on a lord chain
(where both encode the same KP rule) is caught instead by the human check
described in `core/src/test/resources/golden/README.md`.

## Settings (must match ADR-0003 / ADR-0005 and the engine)

| | |
|---|---|
| Ayanamsa | `swe.SIDM_KRISHNAMURTI` (constant **5**) |
| Node | `swe.MEAN_NODE`; Ketu = Rahu + 180° |
| Flags | `FLG_SIDEREAL | FLG_SPEED` + `SWIEPH` (`MOSEPH` fallback) |

## Use

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt

# with real ephemeris data (recommended - required for the SC-002 2" claim):
export CELESTIA_EPHE_PATH=/path/to/swisseph/ephe    # dir with sepl_*.se1, semo_*.se1

# fill the expected blocks:
python compute_golden.py ../../core/src/test/resources/golden/*.json --write

# verify only (what a maintenance check / future CI job would do):
python compute_golden.py ../../core/src/test/resources/golden/*.json
```

`--write` sets each file's `verification.status` to `expected_generated`. Promote
to `human_verified` by hand once a lord chain has been checked against a published
KP source (see the golden README).

## Caveats

- Without `.se1` data the tool uses the Moshier model and records `MOSEPH` in
  `generated_by`; regenerate with real data before trusting SC-002.
- This tool and the Java engine share the KP *division rule*, so it cannot catch a
  wrong understanding of that rule — only a numerical/ayanamsa/node error. That is
  why the golden set also needs one textbook chart with printed lords.
