# Specification Quality Checklist: Significators & Ruling Planets (SPEC-003)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-08
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

- Same shape as SPEC-001/002: an engine primitive whose "users" are the downstream
  KP judgement specs and the LLM agent.
- **Contested KP rules pinned as assumptions with config flags**, not left vague:
  (a) node agency = conjunction + sign lord + star lord, aspects **off** by
  default (`includeNodeAspects`); (b) RP sub-lord inclusion **on** by default
  (`includeSubLords`), off = classic KSK. These are the two places practitioners
  differ; both are made explicit and switchable.
- **New capability**: `SunriseProvider` (`swe_rise_trans`) for the KP day-lord
  boundary — the one genuinely new ephemeris feature.
- Ruling planets are for the **judgment** moment, not birth — a different input
  shape (`JudgmentContext` = instant + place) from the natal chart.
- Prerequisite before `/speckit-implement`: extend the golden reference with
  significator tables + one RP example, and a textbook cross-check of one chart
  (SC-006).
- `/speckit-analyze` (2026-09-09): 3 MEDIUM remediated — FR-007 node-aspect flag
  scoped out of v1 significators (C1); FR-011 reworded to same-sign/nakshatra, no
  orb conjunction (I1); `KpWeekday.resolve` returns a `Resolution` record and
  T033 compares accessor outputs (A1, D1); FR-017 out-of-range RP test added to
  T026 (C2).
- Ready for `/speckit-plan` / `/speckit-tasks` / `/speckit-implement`.
- `/speckit-analyze` (2026-09-09, post-merge): F1 remediated — FR-007/FR-008,
  US3 (narrative + Independent Test + all 3 acceptance scenarios), the node Edge
  Case, and the "Node agency" Key Entity reworded to the delivered
  effective-occupant fold (`research.md §2`): a node's agents are folded into its
  bhava's effective occupants; `NodeAgency` carries no separate signification set.
  F3 remediated — T022 wording corrected to the FR-008 invariant. F2 (SC-006 /
  T004 independent textbook cross-check) still open.
