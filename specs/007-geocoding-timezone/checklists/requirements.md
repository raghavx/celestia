# Specification Quality Checklist: Geocoding & Timezone (SPEC-007)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **First non-KP-math spec.** The "users" are still the LLM agent
  (`geocodePlace`) and the onboarding flow (SPEC-010), not an end user typing at
  a keyboard. `Geocoder`, `GeocodeCache`, `BirthData` name the contract, not a
  tech stack; the concrete geocoder (OpenCage per ADR-0013) and the offline
  tz-boundary library (`timeshape` per ADR-0014) are named only in the
  Assumptions as `/speckit-plan` inputs, not in the requirements.
- **Two decisions carried in from PROJECT_PLAN**: D14 (ADR-0014) is *accepted* —
  offline lat/lon→zone + `java.time` historical conversion. D13 (ADR-0013) is
  still *proposed*; FR/Assumptions/Dependencies all flag that `/speckit-plan`
  must move it to *accepted* (the same ADR-acceptance step SPEC-001 did for
  ADR-0001). Not a `[NEEDS CLARIFICATION]` — it is a scheduled design action.
- **Purity boundary is the crux** (FR-011, FR-017): the deterministic parts —
  lat/lon→zone, local→UTC, the candidate ranking / normalisation, the resolver —
  live in the pure `geo` module and are fully golden-testable offline. The one
  impure piece, the geocoder HTTP call, is an adapter *outside* `geo`; `geo`
  ships a fixture `Geocoder` so every test and the golden corpus run with no
  network. Constitution II / IX.
- **Scope deliberately narrow for v1**: forward geocoding only; in-memory cache
  (the DB-backed `geocode_cache` is SPEC-008); DST gap/fold and unknown-time get
  a *documented resolution + a flag*, never a silent guess; the disambiguation
  *interaction* is SPEC-010. Reverse geocoding, address parsing, and map tiles
  are out.
- **Historical correctness is the risk** — pre-1970 offsets, one-off war-time
  changes, and countries that shifted their standard offset (India pre-1955,
  Portugal, Alaska) all come from `java.time`'s bundled `tzdb`; its version is
  recorded on every result so a JRE upgrade that moves a rule is caught by the
  corpus (SC-001, FR-016).
- **SC-006** is the human check: for ≥ 3 births, verify the computed local→UTC
  instant against an independent source (an online tz converter or a published
  birth record) — the golden tool and the engine share `java.time`, so an
  outside reference is the only independent verification. Left open like
  SPEC-003…006's SC-006 tasks.
- **MVP = Setup + Foundational + US1 + US2 + US3.** US1 (local→UTC) is the piece
  every chart already needs and is entirely offline. US4 (guardrails) and US5
  (the corpus / CI gate) are P2.
- Ready for `/speckit-clarify` (optional) / `/speckit-plan` / `/speckit-tasks` /
  `/speckit-implement`.
- `/speckit-analyze` (2026-09-10, post-tasks): no CRITICAL/HIGH. Applied A1
  (FR-012 scoped to place-and-time-derived fields; `place_query` / `name` are the
  caller's), A2 (SC-001 "hand-verified" → "independent reference: `zoneinfo` +
  `timezonefinder`"), C1 (cache policy — no-TTL, permanent — documented in
  research.md §1 and T039), F1 (FR-014 + US4 sc.2: polar = **flag only**, the
  chart pipeline rejects at cast time), F2 (T003 / research.md §8 add a one-off
  war-time corpus row), I1 (SC-003 "byte-identical" → "equal, value equality;
  `results_json` byte-stability is SPEC-008). D1 (perf guard 60 ms vs 20 ms
  target), E1 (US4 = tests + docs over US3 code), E2 (timeshape-offline
  assertion) accepted LOW. SC-006 / T005 open.
