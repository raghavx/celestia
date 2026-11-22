"""Sheet classification and layouts for the KP toolkit import (SPEC-010 T004).

One entry per workbook sheet: its stable resource ``id``, topic group, kind,
default sensitivity class, whether it may be used as answer content, and — for
the sheets that hold house-formula rules or typed look-ups — the exact columns
those live in.

Why the ids are hand-written
----------------------------
``id`` is assigned here by hand, never computed from the title by a slug
algorithm. Real titles include ``1500+Rules``, ``249Mindsets``, ``RP-Sign-Star-Sub``
and ``Zodiacs-Characteristics``: an automatic slug would need special-casing and
could collide. Hand-assignment also keeps ids stable across toolkit releases by
construction, which ``data-model.md`` requires (renaming one is a breaking change
needing an ADR).

Why the layouts are hand-written
--------------------------------
The sheets do not share a shape. ``1500+Rules`` has a row-number column before
the judged house; ``ReadyReckoner`` puts the matter first; ``Stock Market
Astrology`` puts the houses first; ``Finance Astrology`` is a single column of
``4 = 2, 11 ...`` formula lines. Guessing columns by heuristic mis-read the
row-number column as a house during development, so each layout is declared
explicitly and reviewably instead.

Verified against *Prediction ToolKit for Advanced* 2026-Sep-12 (75 sheets). The
drift guard (T041) fails the import if the workbook's sheet names ever stop
matching ``TITLES``.
"""

from __future__ import annotations

from dataclasses import dataclass

# Mirror the Java enums in com.celestia.core.toolkit (data-model.md "Sheet").
# These strings are written into manifest.tsv and parsed with Enum.valueOf.
GROUPS = {
    "KP_FOUNDATIONS", "HORARY", "PREDICTION_METHOD", "LIFE_MATTERS",
    "MARRIAGE_RELATIONSHIPS", "CAREER_EDUCATION", "FINANCE_SPECULATION", "PROPERTY",
    "HEALTH_MEDICAL", "LONGEVITY", "TRAVEL", "CHILDBIRTH", "REMEDIES",
    "VASTU_FENG_SHUI", "NUMEROLOGY", "REFERENCE", "HOUSEKEEPING",
}
KINDS = {"RULES", "TABLE", "PROCEDURE", "TEMPLATE", "REMEDY", "HOUSEKEEPING"}
SENSITIVITIES = {"NONE", "REMEDY", "FINANCE", "HEALTH", "LONGEVITY"}


@dataclass(frozen=True)
class RuleLayout:
    """Where a rule sheet keeps each part of a rule.

    ``formula`` means the cells are self-contained ``H = h1, h2 matter`` lines
    and the column indices are ignored. Otherwise the indices are 0-based
    columns; ``None`` means the sheet does not carry that part.

    ``matter_col`` with ``formula_col`` set means the matter is in its own column
    and the judged house + houses come from a formula cell beside it.
    """

    judged_col: int | None = None
    houses_col: int | None = None
    matter_col: int | None = None
    category_col: int | None = None
    formula_col: int | None = None
    formula: bool = False
    skip_rows: int = 0


@dataclass(frozen=True)
class SheetSpec:
    """How one workbook sheet is classified."""

    title: str
    id: str
    group: str
    kind: str
    sensitivity: str = "NONE"
    answerable: bool = True
    rules: RuleLayout | None = None


# ---------------------------------------------------------------------------
# The 75 sheets, in workbook order.
# ---------------------------------------------------------------------------

SHEETS: tuple[SheetSpec, ...] = (
    # 1-2 housekeeping (FR-014: never answer content)
    SheetSpec("INDEX", "index", "HOUSEKEEPING", "HOUSEKEEPING", answerable=False),
    SheetSpec("Version Control", "version-control", "HOUSEKEEPING", "HOUSEKEEPING", answerable=False),
    # 3 its own first row reads "Unverified, Still under research" - FR-014
    SheetSpec("Zodiac-Places", "zodiac-places", "REFERENCE", "TABLE", answerable=False),
    SheetSpec("RP-Sign-Star-Sub", "rp-sign-star-sub", "PREDICTION_METHOD", "TABLE"),
    SheetSpec("ChildBirthTime", "child-birth-time", "CHILDBIRTH", "PROCEDURE"),
    SheetSpec("Ruling Planets Logic", "ruling-planets-logic", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("AnnualPrediction", "annual-prediction", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Twin Birth Astrology", "twin-birth", "CHILDBIRTH", "PROCEDURE"),
    SheetSpec("249Mindsets", "horary-249-mindsets", "HORARY", "TABLE"),
    SheetSpec("249SubDivisionsHoraryNumbers", "horary-249-subdivisions", "HORARY", "TABLE"),
    SheetSpec("ABCD Significance", "abcd-significance", "PREDICTION_METHOD", "PROCEDURE"),
    # 12-16 the five yantras
    SheetSpec("Mental Illness Cure Yantra", "yantra-mental-illness", "REMEDIES", "REMEDY", "HEALTH"),
    SheetSpec("Removal of Suffering Yantra", "yantra-removal-of-suffering", "REMEDIES", "REMEDY", "REMEDY"),
    SheetSpec("Child Birth Pregnancy Yantra", "yantra-child-birth", "CHILDBIRTH", "REMEDY", "HEALTH"),
    SheetSpec("Prosperity Yantra", "yantra-prosperity", "REMEDIES", "REMEDY", "REMEDY"),
    SheetSpec("Health Yantra", "yantra-health", "REMEDIES", "REMEDY", "HEALTH"),
    SheetSpec("TravelRelatedSignification", "travel-signification", "TRAVEL", "TABLE"),
    SheetSpec("HealthChart", "health-chart", "HEALTH_MEDICAL", "TEMPLATE", "HEALTH"),
    # 19-23 Astro-Vastu and Feng Shui
    SheetSpec("Astro Planet Vastu", "astro-planet-vastu", "VASTU_FENG_SHUI", "TABLE", "REMEDY"),
    SheetSpec("Astro Vastu Remedies", "astro-vastu-remedies", "VASTU_FENG_SHUI", "REMEDY", "REMEDY"),
    SheetSpec("Ideal Room Vastu Zones", "ideal-room-vastu-zones", "VASTU_FENG_SHUI", "TABLE", "REMEDY"),
    SheetSpec("Feng Shui Five Elements", "feng-shui-five-elements", "VASTU_FENG_SHUI", "TABLE", "REMEDY"),
    SheetSpec("RelationshipZones", "relationship-zones", "VASTU_FENG_SHUI", "TABLE", "REMEDY"),
    SheetSpec("Numerology", "numerology", "NUMEROLOGY", "PROCEDURE"),
    # 25 rule sheet: judged, houses, matter (row 1 is a title)
    SheetSpec(
        "Stock Market Astrology", "stock-market", "FINANCE_SPECULATION", "RULES", "FINANCE",
        rules=RuleLayout(judged_col=0, houses_col=1, matter_col=2, skip_rows=1),
    ),
    SheetSpec("Quantum Astrology", "quantum-astrology", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Untenented Planets", "untenanted-planets", "KP_FOUNDATIONS", "PROCEDURE"),
    SheetSpec("BTR", "btr", "PREDICTION_METHOD", "PROCEDURE"),
    # 29 rule sheet: matter, then a formula cell beside it
    SheetSpec(
        "Medical Astrology", "medical-astrology", "HEALTH_MEDICAL", "RULES", "HEALTH",
        rules=RuleLayout(matter_col=0, formula_col=1),
    ),
    SheetSpec("Prediction Algorithm", "prediction-algorithm", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("PlanetsCharacteristics", "planet-characteristics", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("Health Impact by Planets", "health-impact-by-planets", "HEALTH_MEDICAL", "TABLE", "HEALTH"),
    SheetSpec("Strongest Planet Calculation", "strongest-planet", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Aspects", "aspects", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("Aspects Template", "aspects-template", "KP_FOUNDATIONS", "TEMPLATE"),
    SheetSpec("AstroDiary", "astro-diary", "PREDICTION_METHOD", "TEMPLATE"),
    SheetSpec("ChildBirthGender", "child-gender", "CHILDBIRTH", "PROCEDURE"),
    # 38-39 prose rules about death: stored verbatim, not machine-parsed (FR-003)
    SheetSpec("Death", "death", "LONGEVITY", "RULES", "LONGEVITY"),
    SheetSpec("DeathModeRules", "death-mode-rules", "LONGEVITY", "RULES", "LONGEVITY"),
    # 40 rule sheet: category, matter, judged, houses (no header row)
    SheetSpec(
        "Difficulty", "difficulty", "LIFE_MATTERS", "RULES", "HEALTH",
        rules=RuleLayout(category_col=0, matter_col=1, judged_col=2, houses_col=3),
    ),
    # 41 single column of formula lines
    SheetSpec(
        "Finance Astrology", "finance", "FINANCE_SPECULATION", "RULES", "FINANCE",
        rules=RuleLayout(formula=True),
    ),
    SheetSpec("FortunaMethod", "fortuna-method", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Fortuna", "fortuna", "PREDICTION_METHOD", "TABLE"),
    SheetSpec("Full Life Events Prediction", "full-life-events", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Gems Remedies", "gems-remedies", "REMEDIES", "REMEDY", "REMEDY"),
    SheetSpec("HigherEducationStreams", "higher-education-streams", "CAREER_EDUCATION", "TABLE"),
    SheetSpec("Houses Meaning", "houses-meaning", "KP_FOUNDATIONS", "TABLE"),
    # 48 formula lines
    SheetSpec(
        "Inheritance Astrology", "inheritance", "PROPERTY", "RULES",
        rules=RuleLayout(formula=True),
    ),
    # 49 rule sheet: #, matter, judged, houses
    SheetSpec(
        "JobRules", "job-rules", "CAREER_EDUCATION", "RULES",
        rules=RuleLayout(matter_col=1, judged_col=2, houses_col=3),
    ),
    # 50 rule sheet: matter, judged, houses (row 1 is a title)
    SheetSpec(
        "Luck Astrology", "luck", "LIFE_MATTERS", "RULES",
        rules=RuleLayout(matter_col=0, judged_col=1, houses_col=2, skip_rows=1),
    ),
    SheetSpec("MaleficBenefic", "malefic-benefic", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("MarriageMatching", "marriage-matching", "MARRIAGE_RELATIONSHIPS", "PROCEDURE"),
    SheetSpec("Marriage Partner Looks", "partner-looks", "MARRIAGE_RELATIONSHIPS", "TABLE"),
    SheetSpec("MarriageRules", "marriage-rules", "MARRIAGE_RELATIONSHIPS", "RULES"),
    SheetSpec("MissingPerson", "missing-person", "HORARY", "PROCEDURE"),
    SheetSpec("PlanetDirection", "planet-direction", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("PlanetPositionSignification", "planet-position-signification", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("PlanetFriendship", "planet-friendship", "KP_FOUNDATIONS", "TABLE"),
    # 59 its author marks this "Under research - Ignore" (FR-014)
    SheetSpec(
        "PlanetsStockSectors", "planets-stock-sectors", "FINANCE_SPECULATION", "TABLE", "FINANCE",
        answerable=False,
    ),
    SheetSpec("Prediction Template", "prediction-template", "PREDICTION_METHOD", "TEMPLATE"),
    SheetSpec("ProfessionAstrology", "profession", "CAREER_EDUCATION", "PROCEDURE"),
    # 62 formula lines, in two columns
    SheetSpec(
        "Property Astrology", "property", "PROPERTY", "RULES",
        rules=RuleLayout(formula=True),
    ),
    SheetSpec("Punarpoo", "punarpoo", "MARRIAGE_RELATIONSHIPS", "PROCEDURE"),
    SheetSpec("Relationship", "relationship", "MARRIAGE_RELATIONSHIPS", "TABLE"),
    # 65 rule sheet: matter, judged, houses
    SheetSpec(
        "Romance Astrology Rules", "romance", "MARRIAGE_RELATIONSHIPS", "RULES",
        rules=RuleLayout(matter_col=0, judged_col=1, houses_col=2),
    ),
    # 66 the main rule book: #, judged, houses, matter
    SheetSpec(
        "1500+Rules", "1500-rules", "LIFE_MATTERS", "RULES",
        rules=RuleLayout(judged_col=1, houses_col=2, matter_col=3),
    ),
    # 67 category, matter, judged (primary house), houses (supporting)
    SheetSpec(
        "ReadyReckoner", "ready-reckoner", "LIFE_MATTERS", "RULES",
        rules=RuleLayout(category_col=0, matter_col=1, judged_col=2, houses_col=3),
    ),
    SheetSpec("ShubHorai", "shubha-horai", "REMEDIES", "TABLE"),
    SheetSpec("Software List KP Astrology", "software-list", "HOUSEKEEPING", "HOUSEKEEPING", answerable=False),
    SheetSpec("StarwiseDiseases", "star-wise-diseases", "HEALTH_MEDICAL", "TABLE", "HEALTH"),
    SheetSpec("Star-wise Professions", "star-wise-professions", "CAREER_EDUCATION", "TABLE"),
    SheetSpec("TimeOfEvent", "time-of-event", "PREDICTION_METHOD", "PROCEDURE"),
    SheetSpec("Vimshottari", "vimshottari", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("ZodiacSigns", "zodiac-signs", "KP_FOUNDATIONS", "TABLE"),
    SheetSpec("Zodiacs-Characteristics", "zodiac-signs-characteristics", "KP_FOUNDATIONS", "TABLE"),
)

#: Workbook sheet title -> its classification.
BY_TITLE: dict[str, SheetSpec] = {s.title: s for s in SHEETS}

#: Titles the import expects to find, for the T041 drift guard.
TITLES: frozenset[str] = frozenset(BY_TITLE)

#: Sheets whose rules are machine-parsed into structured PromiseRules.
#: `kind == "RULES"` without a layout (Death, DeathModeRules, MarriageRules) means
#: prose rules: stored verbatim and retrievable, never auto-matched (FR-003).
RULE_SHEETS: tuple[SheetSpec, ...] = tuple(s for s in SHEETS if s.rules is not None)

#: The sheet whose latest date gives the toolkit release version.
VERSION_SHEET_ID = "version-control"

# --- look-up table sources (one id per typed table the loader expects) ------
HORARY_SUBDIVISIONS_ID = "horary-249-subdivisions"
HORARY_MINDSETS_ID = "horary-249-mindsets"
STAR_PROFESSIONS_ID = "star-wise-professions"
STAR_DISEASES_ID = "star-wise-diseases"
PLANET_SIGNIFICATION_ID = "planet-position-signification"
EDUCATION_STREAMS_ID = "higher-education-streams"
FRIENDSHIP_ID = "planet-friendship"
HOUSE_MEANINGS_ID = "houses-meaning"
RELATIONSHIP_ID = "relationship"
PLANET_CHARACTERISTICS_ID = "planet-characteristics"
PLANET_DIRECTION_ID = "planet-direction"
ZODIAC_CHARACTERISTICS_ID = "zodiac-signs-characteristics"


def title_of(sheet_id: str) -> str:
    """The workbook title for a sheet id."""
    for spec in SHEETS:
        if spec.id == sheet_id:
            return spec.title
    raise KeyError(f"unknown sheet id: {sheet_id!r}")


def _self_check() -> None:
    """Fail fast on a manifest that cannot produce valid resources."""
    ids = [s.id for s in SHEETS]
    if len(set(ids)) != len(ids):
        raise ValueError(f"duplicate sheet ids: {sorted({i for i in ids if ids.count(i) > 1})}")
    titles = [s.title for s in SHEETS]
    if len(set(titles)) != len(titles):
        raise ValueError(f"duplicate titles: {sorted({t for t in titles if titles.count(t) > 1})}")
    for s in SHEETS:
        if s.group not in GROUPS:
            raise ValueError(f"{s.id}: unknown group {s.group!r}")
        if s.kind not in KINDS:
            raise ValueError(f"{s.id}: unknown kind {s.kind!r}")
        if s.sensitivity not in SENSITIVITIES:
            raise ValueError(f"{s.id}: unknown sensitivity {s.sensitivity!r}")
        if s.rules and not s.rules.formula:
            if s.rules.judged_col is None and s.rules.formula_col is None:
                raise ValueError(f"{s.id}: a column layout needs judged_col or formula_col")
    known = set(ids)
    for name, value in list(globals().items()):
        if name.endswith("_ID") and isinstance(value, str) and value not in known:
            raise ValueError(f"{name} names an unknown sheet id: {value!r}")


_self_check()
