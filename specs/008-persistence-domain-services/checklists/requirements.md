# Specification Quality Checklist: Persistence & Domain Services (SPEC-008)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-11
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

- **First stateful spec.** Everything through SPEC-007 is pure; this is the
  database. "Users" are the downstream specs (009–016) and the operator, not an
  end user. Spring Data JPA / Flyway / MySQL / Testcontainers are named only in
  the Assumptions as `/speckit-plan` inputs — the FRs speak of "a store", "the
  schema", "migrations".
- **Scope deliberately narrow.** SPEC-008 = identity (`app_user`) + `birth_data`
  + the engine-versioned chart / prediction cache + the persistent geocode cache
  + the async inbox/outbox tables + the migration & Testcontainers harness. The
  billing/payment/guardrail tables and `conversation`/`message` are explicitly
  deferred to the specs that own their semantics (011/012/014, 009). This keeps
  the spec to the "1–3 days" size the constitution wants and avoids landing dead
  schema.
- **The engine-versioning rule (US2, FR-005/006) is the heart** — Constitution
  III made concrete: a stored chart whose `engine_version` ≠ the current one is
  recomputed on read and overwritten. Lazy, not a background sweep (v1).
- **Purity is preserved by ports** (FR-014/FR-015): the pure modules gain no
  JPA/JDBC dependency; `geo`'s `GeocodeCache` is the model — the port is in
  `geo`, the JPA adapter is in `agent`. `LayeringArchitectureTest` /
  `DeterminismArchitectureTest` already forbid Spring in the pure packages; a
  task adds an assertion that they also forbid `jakarta.persistence` /
  `org.springframework.data`.
- **PII**: the hard-delete ("forget me") operation is in scope now (ADR-0016);
  column encryption at rest and the *scheduled* deletion job are SPEC-016 /
  SPEC-012 respectively — the spec says so and structures the data for a later
  encryption wrap.
- **ADR-0008** (Testcontainers MySQL) is *proposed* → to be **accepted** in
  `/speckit-plan`, like SPEC-001 accepted ADR-0001. ADR-0015 and ADR-0016 are
  already accepted and drive US5 / US3.
- **SC-006** is the migration/harness gate rather than a human-judgement check;
  there is no astrological rule here to walk by hand, so no SC-006-style human
  verification task is needed (unlike SPEC-001–007).
- **MVP = Setup + Foundational + US1 + US2 + US3** — persist birth data, the
  versioned chart cache, and the identity store. US4/US5/US6 are P2.
- Ready for `/speckit-clarify` (optional) / `/speckit-plan` / `/speckit-tasks` /
  `/speckit-implement`.
