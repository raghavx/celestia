<!--
Sync Impact Report — 2026-11-22
Version change: 1.0.0 → 2.0.0 (MAJOR: Principle V, a NON-NEGOTIABLE principle, is redefined;
the answer scope widens from "this user's own chart" to the whole licensed toolkit)
Modified principles:
  - Preamble: Celestia answers from the licensed KP toolkit, computed by the engine
  - III. Calculation Correctness Is Gated → III. Calculation and Toolkit Correctness Are Gated
    (adds toolkit import cross-checks and toolkit version in engine_version)
  - IV. The LLM Interprets — It Never Calculates or Transacts (adds: never matches toolkit rules itself)
  - V. Astrology-Only, Guarded Single Path → V. Toolkit-Scoped, Guarded Single Path
    (scope = every toolkit topic except housekeeping; second-person charts supplied in
    conversation allowed; fixed advisory sentences; self-harm support line)
Added sections:
  - Principle X. The Toolkit Is the Source of KP Judgement (NON-NEGOTIABLE)
  - Technology Constraints: toolkit data bullet
Removed sections: none
Numbering: I–IX kept unchanged on purpose (≈80 references in specs and code); the new
principle is X.
Templates: .specify/templates/plan-template.md — its "Constitution Check" reads this file at
  run time; no edit needed ✅. spec-template.md / tasks-template.md — no principle-specific
  content ✅.
Follow-up TODOs (governance, outside this command):
  - TODO(ADR): dated ADR recording this amendment (Governance requires it)
  - TODO(PROJECT_PLAN): principles 10 and 14, system-prompt paragraph, spec list re-ordered
    around the toolkit
-->

# Celestia Constitution

Celestia is a WhatsApp-based Krishnamurti Paddhati (KP) astrology agent built around the
licensed *Prediction ToolKit for Advanced*. A deterministic KP engine computes the chart.
The toolkit's rules and methods, applied to that chart by code, supply the judgement. A
self-hosted LLM (Llama 4 via Ollama) explains the result and cites it. Answering is gated
by a paid subscription. This constitution governs how the system is built. It supersedes
convenience, habit, and individual preference.

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
The imported toolkit is part of the domain core and obeys the same rules: it is read
from bundled data only, never from the network or a live file.

### III. Calculation and Toolkit Correctness Are Gated

A suite of golden-chart snapshot tests (3+ fully documented KP charts, every
derived value pinned: planet longitudes, star/sub/sub-sub lords, cusps, dasha
balance) runs in CI and MUST pass for any merge. Each KP rule in code names the
authoritative source it implements. Toolkit imports are gated too. Every sheet of
the workbook MUST be present. The toolkit's 249 horary table MUST agree with the
engine's lord for lord. Re-importing an unchanged workbook MUST produce no
difference. `engine_version` includes the toolkit version and is bumped whenever a
rule or a toolkit release changes. Cached charts and readings computed under an
older version are recomputed.

### IV. The LLM Interprets — It Never Calculates, Matches, or Transacts

Every number the agent states (positions, lords, cusps, dasha dates, horary subs)
comes from a tool call into `core`; the model never does astronomical or calendar
arithmetic. Deciding which toolkit rules a chart satisfies is also code, never the
model. Plan display, plan selection, payment-link creation, payment handling,
entitlement checks, and the decision to unlock are deterministic code and webhooks
— the model is never in that path.

### V. Toolkit-Scoped, Guarded Single Path (NON-NEGOTIABLE)

There is exactly one route from a user message to an answer:
`entitlement check → input guardrail → hardened prompt + fixed tool set → output
guardrail → reply`. The `guardrail` module runs a deterministic injection/jailbreak
check and a topic gate *before* the model is invoked.

- **In scope** is KP astrology and every topic of the licensed toolkit except its
  housekeeping sheets. This includes health and disease, longevity and death,
  finance and speculation, remedies (gems, yantras), Astro-Vastu, Feng Shui,
  numerology and Shubha Horai.
- **Charts** are the user's own, or another person's when the user supplies that
  person's birth data or a horary number in the conversation. Supplied data is used
  for that reply only.
- **Out of scope:** anything unrelated to astrology gets a fixed refusal, and the
  model is not called.
- **Advisory sentences:** a reply drawing on health, longevity, finance or remedy
  content ends with exactly one fixed advisory sentence for the most severe class
  used. The wording is set by the product, never generated.
- **Self-harm:** when a user's own message expresses intent to harm themselves, the
  reply starts with a short support line and a crisis helpline.
- No code path reaches the chat model except through that guardrail (ArchUnit-enforced).
- The system prompt, tool definitions, internal reasoning, and any other *Celestia
  user's* data are never disclosed.
- Every tool call's `userId`, coordinates, and clock are injected server-side from the
  authenticated identity — never read from message text.

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
"forget me" request triggers a hard delete. A second person's birth data supplied
in a conversation is personal data too. It is not stored beyond the reply unless the
user explicitly saves it.

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

### X. The Toolkit Is the Source of KP Judgement (NON-NEGOTIABLE)

- Every KP interpretation in an answer — what a house promises, what a star means for
  profession, how to time an event, which remedy suits — MUST trace to a sheet of the
  licensed toolkit. Each such statement cites that sheet.
- The model MUST NOT state KP interpretation that no toolkit sheet supports. Where the
  toolkit is silent, the answer says so.
- The toolkit's methods (time of event, BTR, annual prediction, marriage matching,
  missing person, gem selection, …) are followed in their written order. Each numeric
  step is filled by the engine.
- Toolkit content is kept word for word. Rules are never paraphrased into new
  conditions, and known source errors are corrected only by a documented fix-up.
- New product capabilities are specified as toolkit topics. A capability the toolkit
  does not cover needs an ADR naming its source.

## Technology Constraints

- **Language / build**: Java 25 LTS; Maven multi-module with the Maven Wrapper.
- **Framework**: Spring Boot 4.1.x (Spring Framework 7), Spring MVC, Spring Data
  JPA, Spring Security — only in the `agent` module and the adapter modules.
- **LLM**: Ollama, self-hosted; primary model Llama 4 (`llama4:scout`), with a
  configured fallback tool-calling model and a small guardrail classifier model.
  Integrated via Spring AI 2.0.x `OllamaChatModel`.
- **Ephemeris**: Swiss Ephemeris Java port. Its licence (AGPL vs. commercial) is
  an architecture decision recorded as an ADR before Phase 1.
- **Toolkit data**: the licensed workbook is not committed. Its imported content is
  committed as versioned, generated, reviewable data, produced by one repeatable
  import step that records the workbook's version and fingerprint.
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
  and single-path rules, the golden-chart snapshot suite, the toolkit import checks,
  the guardrail adversarial-corpus suite, code formatting (Spotless), and a
  code-coverage threshold.
- The guardrail adversarial corpus (jailbreaks, topic drift, prompt-extraction,
  cross-user probes, encoded payloads) is a living checked-in file; every case
  asserts the message never reaches a mock chat model. An in-scope corpus holds at
  least one question per toolkit topic group, and every such question MUST pass the
  gate.
- Commits follow Conventional Commits. Work happens on branches; the default
  branch is always releasable.

## Governance

This constitution supersedes other practices. Amendments require: a dated ADR
stating the change and rationale, a version bump below, and a matching update to
`PROJECT_PLAN.md` where affected. Every pull request and review verifies
compliance with these principles; deviations must be justified in writing in the
PR description and approved by a maintainer, or the PR is not merged. Added
complexity carries the burden of proof.

**Version**: 2.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-11-22
