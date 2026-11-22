# ADR 0024: Toolkit-centred constitution (v2.0.0)

- **Status:** accepted
- **Date:** 2026-11-22
- **Decision ref:** Constitution v1.0.0 → v2.0.0; amends ADR-0023's in-scope taxonomy;
  PROJECT_PLAN.md principles 10 and 14; SPEC-010 (`specs/010-kp-toolkit-knowledge-base`)
  clarifications of 2026-11-22

## Context

The owner holds a lifetime licence to the *Prediction ToolKit for Advanced* (KP course
workbook, v2026-09-12, 75 sheets): about 2,000 house-sub-lord promise rules, the 249
horary table with mindsets, star, planet and house look-ups, and the KP methods (time of
event, BTR, annual prediction, marriage matching, missing person, gem selection). The
owner wants the product built around it. Constitution v1.0.0 had no notion of a source
of KP judgement. Its Principle V limited answers to "this user's own chart" and, with
plan principle 10, refused medical, financial and death questions. Most of the toolkit
would have been unusable.

## Options considered

1. Keep v1.0.0 and use only the natal-chart subset of the toolkit.
2. Widen scope to the whole toolkit, but leave judgement unconstrained (model may
   interpret freely).
3. **Make the toolkit the single source of KP judgement, widen the scope to all of it,
   and keep every security guardrail.**

## Decision

Option 3, as recorded in Constitution v2.0.0:

- **New Principle X** (NON-NEGOTIABLE): every KP interpretation traces to a toolkit
  sheet and cites it. The model does not invent interpretation. Methods are followed
  in their written order, with numbers from the engine.
- **Principle IV**: matching toolkit rules to a chart is code, not the model.
- **Principle III**: toolkit import checks are a CI gate, and the toolkit version is
  part of `engine_version`.
- **Principle V** (redefined), per the SPEC-010 clarifications:
  - Scope is every toolkit topic except housekeeping (Q1: A).
  - Suicide and self-harm rules are treated like other health rules (Q2: B).
  - A second person's chart can be supplied in the conversation (Q3: A).
  - Fixed advisory sentences apply to health, longevity, finance and remedy answers.
  - A support line and helpline are added when a user's own message expresses intent
    to self-harm (SPEC-010 FR-011a, confirmed by the owner).
- **Unchanged:** the injection gate, server-side identity injection, no access to
  other users' data, PII handling, the payment gate.

## Consequences

- MAJOR version bump (Principle V redefined). Principle numbers I–IX are unchanged.
- SPEC-011's in-scope taxonomy comes from the toolkit's topic groups (SPEC-010), not
  the seven-item list in ADR-0023.
- Health, longevity, death and finance answers carry reputational and legal exposure.
  Mitigations: the fixed advisory sentences, the Terms page (ADR-0022), and a legal
  review before launch.
- Telling a user their chart "indicates" suicide is a deliberate owner decision
  (Q2: B). Revisit it before public launch.
- The roadmap is re-ordered around the toolkit (PROJECT_PLAN §5).
