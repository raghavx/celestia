# Celestia Constitution

Celestia is a WhatsApp-based Krishnamurti Paddhati (KP) astrology agent: a
self-hosted LLM (Llama 4 via Ollama) answers questions about a user's natal chart
by orchestrating a deterministic KP calculation engine, gated by a paid subscription.
This constitution governs how the system is built. It supersedes convenience,
habit, and individual preference.

## Core Principles

### I. Spec-Driven Delivery (NON-NEGOTIABLE)

No implementation work begins without an approved `spec → plan → tasks` trail in
`specs/`. Specs are small (1–3 days of work), cite their sources for any
astrological rule, and are reviewed by a human before `/speckit-implement`. Code
that has drifted from its spec is a defect: update the spec or revert the code.

### II. Deterministic Domain Core

The calculation and domain modules (`ephemeris`, `core`, `geo`, `billing`,
`guardrail`) are pure: no Spring, no database, no network, no `now()` or ambient
clock. Every input is explicit; the same input always produces the same output.
Dependencies point inward only — `ephemeris → core → agent → adapters`, never the
reverse. This layering is enforced by an ArchUnit test, not by good intentions.

### III. Calculation Correctness Is Gated

A suite of golden-chart snapshot tests (3+ fully documented KP charts, every
derived value pinned: planet longitudes, star/sub/sub-sub lords, cusps, dasha
balance) runs in CI and must pass for any merge. Each KP rule in code names the
authoritative source it implements. `engine_version` is bumped whenever a rule
changes, and cached charts computed under an older version are recomputed.

### IV. The LLM Interprets — It Never Calculates or Transacts

Every number the agent states (positions, lords, cusps, dasha dates, horary subs)
comes from a tool call into `core`; the model never does astronomical or calendar
arithmetic. Plan display, plan selection, payment-link creation, payment handling,
entitlement checks, and the decision to unlock are deterministic code and webhooks
— the model is never in that path.

### V. Astrology-Only, Guarded Single Path (NON-NEGOTIABLE)

There is exactly one route from a user message to an answer:
`entitlement check → input guardrail → hardened prompt + fixed tool set → output
guardrail → reply`. The `guardrail` module runs a deterministic injection/jailbreak
check and a topic gate *before* the model is invoked; anything outside KP astrology
about *this user's own* chart (houses, planets, nakshatras, sub-lords, dasha
timing, ruling planets, KP horary, and the life events those indicate) receives a
fixed refusal and the model is not called. No code path reaches the chat model
except through that guardrail (ArchUnit-enforced). The system prompt, tool
definitions, internal reasoning, and any other user's data are never disclosed.
Every tool call's `userId`, coordinates, and clock are injected server-side from
the authenticated identity — never read from message text.

### VI. Deterministic Conversation Flows

Onboarding (name / DOB / time / place) and subscription (show plans → select →
pay → activate → renew) are coded finite-state machines with explicit validation
and confirmation steps. The state of what has been collected or paid lives in the
database, never in the model's memory. The LLM is used only to parse a user's
free-text answer into typed fields via structured output.

### VII. Identity, Consent, and Data Protection

The signature-verified WhatsApp sender phone number is the authenticated identity;
an unknown number creates a new user and enters onboarding. Name, date/time/place
of birth, and phone are personal data: encrypted at rest where feasible, logged as
metadata not message bodies by default. First contact captures consent; a
"forget me" request triggers a hard delete.

### VIII. Payments Are Reconciled From the Gateway

A subscription becomes active only on a signature-verified Razorpay webhook or a
verified server-side fetch — never because a user says they paid. All payment
events are idempotent (keyed on the provider event id) and reconciled by a
scheduled job. Every Q&A, horary, and prediction turn first checks an active,
non-expired subscription with remaining quota.

### IX. Adapters at the Edge

WhatsApp and Razorpay sit behind `MessagingChannel` and `PaymentGateway` ports.
The core and domain modules never import provider SDKs or types. Test and live
credentials are separated per environment and never committed.

## Technology Constraints

- **Language / build**: Java 25 LTS; Maven multi-module with the Maven Wrapper.
- **Framework**: Spring Boot 4.1.x (Spring Framework 7), Spring MVC, Spring Data
  JPA, Spring Security — only in the `agent` module and the adapter modules.
- **LLM**: Ollama, self-hosted; primary model Llama 4 (`llama4:scout`), with a
  configured fallback tool-calling model and a small guardrail classifier model.
  Integrated via Spring AI 2.0.x `OllamaChatModel`.
- **Ephemeris**: Swiss Ephemeris Java port. Its licence (AGPL vs. commercial) is
  an architecture decision recorded as an ADR before Phase 1.
- **Database**: MySQL 8.x; all schema changes via Flyway migrations.
- **Persistence rule**: monetary amounts stored as integer minor units; a `plan`
  price change never mutates an existing `subscription`.
- **Decisions D1–D23** from `PROJECT_PLAN.md` are each resolved by a dated ADR in
  `docs/adr/` before the phase that depends on them.

## Development Workflow and Quality Gates

- The Spec Kit flow is: `/speckit-constitution` → `/speckit-specify` →
  `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → human review →
  `/speckit-implement`.
- CI must pass before merge and enforces: compile, unit tests, ArchUnit layering
  and single-path rules, the golden-chart snapshot suite, the guardrail
  adversarial-corpus suite, code formatting (Spotless), and a code-coverage
  threshold.
- The guardrail adversarial corpus (jailbreaks, topic drift, prompt-extraction,
  cross-user probes, encoded payloads) is a living checked-in file; every case
  asserts the message never reaches a mock chat model.
- Commits follow Conventional Commits. Work happens on branches; the default
  branch is always releasable.

## Governance

This constitution supersedes other practices. Amendments require: a dated ADR
stating the change and rationale, a version bump below, and a matching update to
`PROJECT_PLAN.md` where affected. Every pull request and review verifies
compliance with these principles; deviations must be justified in writing in the
PR description and approved by a maintainer, or the PR is not merged. Added
complexity carries the burden of proof.

**Version**: 1.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-09-02
