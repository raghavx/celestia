# Specification Quality Checklist: Vimshottari Dasha (SPEC-004)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-09
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

- Same shape as SPEC-001/002/003: an engine primitive whose "users" are the
  downstream KP specs (SPEC-006 daily predictions, SPEC-008 persistence) and the
  LLM agent. Prior-spec domain type names (`KpLordage`, `Nakshatra`, `Span`,
  `BirthData`, `EngineVersion`, `PositionProvider`, `TimeScales`) are referenced
  by name deliberately, as in SPEC-001/002/003 — they name the contract, not a
  tech stack.
- **One contested rule pinned as an assumption, not left vague**: the year-length
  convention (365.25 days, KSK). This is the primary reason two KP tools disagree
  on dasha dates; it is stated explicitly and tied to the engine version. No
  config flag is offered in v1 (unlike SPEC-003's node/sub-lord switches) because
  there is a single dominant KP convention; a switch would be a later decision.
- **No new ephemeris capability** — needs only the Moon (existing
  `PositionProvider`). Pure `core` plus the exact-rational partition machinery
  already built in SPEC-001 (`Span` / `VimshottariPartition`), reused for the time
  partition.
- SC-005 references "a developer machine" and "CI" for the performance/determinism
  outcome — consistent with SPEC-002/003 (`/speckit-analyze` treats this as an
  accepted LOW).
- **SC-006** requires a human/textbook cross-check of one chart's birth balance —
  because the golden reference tool and the engine will share the same formula.
- Ready for `/speckit-clarify` (optional) / `/speckit-plan` / `/speckit-tasks` /
  `/speckit-implement`.
- `/speckit-analyze` (2026-09-09, post-tasks): A1 remediated — T012 gains the
  FR-016 assertion (year-1600 birth → `Accuracy.REDUCED`, no exception). A2
  remediated — the golden running-stack query instant is pinned to
  `birthInstant + 40 Julian years` (research.md §4; T001/T002/T019/quickstart
  updated). A4 remediated — research.md §3 shortest-Prana expression corrected.
  A3 (SC-005 5 ms vs the 50 ms guard) and A5 (no automated engine-version check)
  left as accepted LOW, consistent with SPEC-002/003.
- `/speckit-analyze` (2026-09-09, post-implementation): A1/A2/A4 confirmed
  resolved. B2 remediated — spec.md US3 / FR-012 now say the window is half-open
  `[from, to)`. B3 — research.md §2 marks the `VimshottariPartition` refactor as
  done (T032). B4 — quickstart scenario map lists `RunningDashaGoldenTest` /
  `DashaWindowGoldenTest`. B1 (SC-006 / T003 human dasha-balance cross-check)
  still open; B5/B6 accepted LOW.
