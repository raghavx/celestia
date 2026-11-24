<!--
Sync Impact Report — 2026-11-24 (fourth amendment this date)
Version change: 2.3.0 → 3.0.0 (MAJOR: an existing prohibition is narrowed for a
class of deployment that previously had it. This is a weakening, not a
clarification, and it is versioned as one so that nobody can mistake it for
housekeeping. v2.2.0 defined a term; v2.3.0 bounded development and added
conditions; this removes a protection, under conditions.)
Modified sections:
  - Technology Constraints, **LLM — in deployment**: adds ONE bounded exception.
    A deployment whose entitlement allow-list holds exactly one number — the
    developer's own — may use a third-party inference API, under ADR-0026's six
    conditions. Enforced at startup, not trusted: on a third-party API the
    application refuses to run with any other allow-list. Lapses automatically
    when a second number is added or public sign-up opens.
  - Preamble: "self-hosted LLM" now carries the qualifier that this is the rule
    for anything deployed, with the single exception named. v2.2.0's scope note
    had left the preamble as written; a MAJOR change to what deployment permits
    makes the document's first paragraph misleading if it is left alone.
Added sections: none
Removed sections: none
Rationale: v2.3.0 defined the owner preview as a deployment, which is correct —
  it is a live webhook and entitlement limits who it answers, not what it is. The
  consequence was that shipping a preview for ONE person required renting a GPU
  VM at a standing monthly cost before a single answer had been judged
  acceptable. ADR-0026 argues the alternative on two grounds: the only personal
  data at stake is the owner's own and he consents for himself, and Principle III
  ties a correctness verdict to the model that produced it — so running the
  preview and development on one model means the practitioner's verdict is earned
  once rather than twice.
What stops this becoming the public launch: the first condition is a startup
  invariant rather than a sentence. Admitting a second person requires either
  moving to a self-hosted model or a further amendment — the pressure that would
  otherwise erode this is answered in code (OwnerOnlyAllowListGuard, asserted in
  LlmConfigContextTest and EntitlementServiceTest).
What this does NOT grant: public traffic, a second allow-listed person, or any
  other living person's data. ADR-0010's rented GPU VM remains the decided answer
  for public launch and is deferred, not superseded. SC-001's verdict will have to
  be re-earned on the deployed model before launch, which Principle III already
  requires.
Templates: no principle-specific content in plan/spec/tasks templates ✅.
Propagated: PROJECT_PLAN.md (overview, D9, D10) the same day.
Decision ref: ADR-0026 (docs/adr/0026-owner-preview-on-a-third-party-api.md),
  accepted 2026-11-24
-->

<!--
Sync Impact Report — 2026-11-24 (third amendment this date)
Version change: 2.2.0 → 2.3.0 (MINOR: previously-unstated guidance is added and a
term is defined; no existing protection is removed or weakened)
Modified sections:
  - Technology Constraints, **LLM**: split into three bullets — deployment,
    development and evaluation, integration. The deployment prohibition is
    unchanged in substance and strengthened in wording ("no data of any user"),
    and **"deployment" is now defined**: any run reachable by an inbound message
    from outside the developer's machine, whatever the allow-list currently
    permits. The owner preview is named as a deployment so nobody can read it as
    a development exemption — entitlement limits who it answers, not what it is.
  - Technology Constraints, **LLM**: a development-and-evaluation permission is
    added for a third-party inference API, under five conditions (developer's own
    data only; no deployment; no prompt retention or training; named in an ADR;
    provider stays configuration).
  - Technology Constraints, **LLM**: the integration line said "Spring AI 2.0.x
    `OllamaChatModel`", which stopped describing the code when a second provider
    was added. It now names the `ChatModel` abstraction, with both provider types
    as instances of it.
  - III. Calculation and Toolkit Correctness Are Gated: adds that a correctness
    verdict is tied to the model that produced it and does not transfer to
    another — so developing against one model and deploying another means judging
    again on the deployed one.
Added sections: none
Removed sections: none
Rationale: ADR-0009's development story — a local Ollama with the smallest
  tool-capable model — was measured on 2026-11-24 and withdrawn: ~24 minutes a
  question and three faults in four, teaching nothing about the prompt or the
  tools. Development moved to the Claude API. The constraint as written was
  scoped to deployments and so did not forbid that, but it did not say what
  development *may* do either, and "deployment" was undefined — which is exactly
  the ambiguity v2.2.0 was written to stop repeating. This amendment settles both
  in the direction that names a risk (serving other people's data to a third
  party) rather than a location.
Why MINOR and not MAJOR: a reader could see the development permission as a
  loosening. It is not. The prohibition always said "deployment", the owner
  preview is now explicitly inside it, and the development case is bounded by
  five conditions that did not exist before. Nothing a user's data was protected
  from before is permitted now.
Templates: no principle-specific content in plan/spec/tasks templates ✅.
Propagated: PROJECT_PLAN.md (overview, D9, D10, the technology summary) the same
  day; agent/README.md already carried the distinction.
Decision ref: ADR-0009 (docs/adr/0009-llm-runtime-and-model.md), amended
  2026-11-24 — "development runs against the Claude API", which states the
  data-scope reasoning and the obligations in full.
-->

<!--
Sync Impact Report — 2026-11-24
Version change: 2.1.0 → 2.2.0 (MINOR: an ambiguous term is defined and new
obligations are added; nothing is removed, and what the term protected is
preserved and now stated outright)
Modified sections:
  - Technology Constraints, **LLM**: "self-hosted" is defined as *we run the
    weights*, not *the machine is ours*. The substance it was protecting — no
    prompt or user data to a third-party inference API, model ids ours and
    configurable — is unchanged and now explicit. Rented infrastructure we
    control is permitted, and carries three new obligations: encrypted transport,
    a region chosen for the data, and no prompt logging on the host.
Added sections: none
Removed sections: none
Rationale: ADR-0010 accepted option 3 (a rented cloud GPU VM) on 2026-11-24
  because it needs no procurement and so does not gate Phase 6. That strained the
  letter of this line while satisfying its purpose, and the ADR recorded the
  amendment as outstanding rather than reinterpreting the constitution as a side
  effect of a hosting choice. This is that amendment. The project had in fact
  carried both readings — ADR-0009's context said "no external API" (the API
  reading) while PROJECT_PLAN D10 said "over the LAN" (the premises reading) —
  so the ambiguity was real and is now settled in favour of the one that names a
  risk rather than a location.
Scope note: the term is defined here and that definition governs its use
  elsewhere, including the preamble, which is left as written.
Templates: no principle-specific content in plan/spec/tasks templates ✅.
Propagated: PROJECT_PLAN.md (overview, D9, the technology summary) and README.md
  updated the same day.
Decision ref: ADR-0010 (docs/adr/0010-ollama-hosting.md), accepted 2026-11-24
-->

<!--
Sync Impact Report — 2026-11-22 (second amendment this date)
Version change: 2.0.0 → 2.1.0 (MINOR: new, previously-unstated guidance added to a
NON-NEGOTIABLE principle and the preamble; no existing requirement removed or weakened)
Modified principles:
  - Preamble: reworded so the agent is described as the KP astrologer the user talks to
    (explains judgement in an astrologer's own language), with toolkit traceability named
    as internal, not user-facing
  - X. The Toolkit Is the Source of KP Judgement (NON-NEGOTIABLE): added that the toolkit is
    an internal asset (calculation + knowledge base) that must never be named, disclosed, or
    cited to the user (no toolkit name, sheet id/title, or row number in any reply);
    reworded the "cites that sheet" / "toolkit is silent" bullets so traceability is
    explicitly internal (logging, eval, human review) while the substantive requirement —
    every interpretation traces to a toolkit sheet, nothing fabricated — is unchanged
Added sections: none
Removed sections: none
Rationale: discovered and resolved first at the SPEC-010 level (specs/010-kp-toolkit-
  knowledge-base/spec.md FR-008, contracts/agent-tools.md) while planning the toolkit
  knowledge base — the original wording was ambiguous about whether "cites that sheet"
  meant the user-facing reply must name the sheet. This amendment settles that reading at
  the constitution level so no future spec repeats the ambiguity.
Templates: .specify/templates/plan-template.md — its "Constitution Check" reads this file at
  run time; no edit needed ✅. spec-template.md / tasks-template.md — no principle-specific
  content ✅.
Decision ref: ADR-0025 (docs/adr/0025-toolkit-is-internal-only.md)
-->

# Celestia Constitution

Celestia is a WhatsApp-based Krishnamurti Paddhati (KP) astrology agent built around the
licensed *Prediction ToolKit for Advanced*. A deterministic KP engine computes the chart.
The toolkit's rules and methods, applied to that chart by code, supply the judgement — but
the toolkit itself is an internal asset, used to build the agent's calculations and
knowledge, never disclosed to the user. To the user, Celestia **is** the KP astrologer: a
self-hosted LLM (Llama 4 via Ollama) explains that judgement the way an astrologer would —
self-hosted being the rule for anything deployed, with the single bounded exception the
Technology Constraints set out for a one-person allow-list (ADR-0026) —
in houses, lords, stars, and timing — never as a system quoting a document. Every
explanation stays internally traceable to its toolkit source, but that tracing is for the
system, not the user. Answering is gated by a paid subscription. This constitution governs
how the system is built. It supersedes convenience, habit, and individual preference.

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

**A correctness verdict is tied to the model that produced it.** Where a success
criterion is a human judgement of answer quality, the verdict holds for the model
that was judged and **does not transfer to another** — not to a different model,
and not to the same model after it is retired and replaced. Developing against
one model and deploying another therefore means the judgement must be made again
on the deployed one, before launch rather than after.

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
  licensed toolkit. Each such statement is internally traceable to that sheet.
- The toolkit is an internal asset: it powers the agent's calculations and is its
  knowledge base, built into the product. It is never disclosed to the user. No reply
  may name the toolkit, a sheet's id or title, or a row number. The agent **is** the
  KP astrologer from the user's point of view; it explains judgement in ordinary KP
  language — house, sub lord, star lord, timing — never as a system quoting a
  document. Traceability (the `source` every tool result carries) is for the system —
  logging, evaluation, human review — not for the user.
- The model MUST NOT state KP interpretation that no toolkit sheet supports. Where the
  toolkit has no basis for a question, the agent says so in its own voice, without
  naming the toolkit.
- The toolkit's methods (time of event, BTR, annual prediction, marriage matching,
  missing person, gem selection, …) are followed in their written order. Each numeric
  step is filled by the engine.
- Toolkit content is kept word for word internally. Rules are never paraphrased into
  new conditions, and known source errors are corrected only by a documented fix-up.
- New product capabilities are specified as toolkit topics. A capability the toolkit
  does not cover needs an ADR naming its source.

## Technology Constraints

- **Language / build**: Java 25 LTS; Maven multi-module with the Maven Wrapper.
- **Framework**: Spring Boot 4.1.x (Spring Framework 7), Spring MVC, Spring Data
  JPA, Spring Security — only in the `agent` module and the adapter modules.
- **LLM — in deployment**: Ollama, **self-hosted — which means we run the
  weights, not that the machine stands on our premises.** What that protects, and
  what **no deployment** may give up: **no prompt and no data of any user reaches
  a third-party inference API**, and the model is ours to name, pin and swap in
  configuration. The inference host may therefore be our own hardware or
  infrastructure we rent and control (ADR-0010).
  **Where it is rented it is a processor, not part of the trust boundary**, and
  three obligations follow: transport to it is encrypted; its region is chosen
  for the personal data it will see; and it does not log prompts. Primary model
  Llama 4 (`llama4:scout`), with a configured fallback tool-calling model and a
  small guardrail classifier model.
  **A deployment is any run reachable by an inbound message from outside the
  developer's machine** — a webhook, a queue, a live channel — whatever the
  allow-list currently permits and however few people it admits. **The owner
  preview is a deployment**: entitlement limits who it answers, not what it is,
  and an allow-list is one property edit away from admitting someone else.
  **One bounded exception, and it is the only one** (ADR-0026). A deployment whose
  entitlement allow-list contains **exactly one number — the developer's own** —
  may use a third-party inference API, subject to that ADR's six conditions: no
  other living person's data, no training or retention of prompts by the provider,
  a processing agreement in place, the provider named in an ADR, and the provider
  kept in configuration rather than code. **The exception is enforced at startup
  rather than trusted**: on a third-party API the application refuses to run with
  any other allow-list. It **lapses** when a second number is added or public
  sign-up opens, whichever comes first, and lapsing restores this rule with no
  further decision — a lapsed permission with a running service is a defect to
  fix, not a grace period.
- **LLM — in development and evaluation**: a third-party inference API **is
  permitted**, because a model small enough to run on a laptop cannot drive this
  product's tool surface at all and pretending otherwise produced a development
  story the project had to withdraw (ADR-0009, amended 2026-11-24). Five
  conditions, and they are the whole of the permission:
  1. **Only the developer's own personal data.** Never another living person's
     birth details, name, number or question — not a friend's, not a tester's,
     not a sample from a real user. Synthetic data is always allowed.
  2. **No deployment, by the definition above.** Nothing inbound from outside the
     developer's machine: no webhook, no queue, no production database, no path
     by which another person's message could arrive.
  3. **The provider account must not train on or retain the prompts**, or the
     data must be synthetic. Checked before real birth details are sent, not
     after.
  4. **Named in an ADR** — the provider, the model and the date — so "what did we
     develop against?" is answerable a year later.
  5. **The provider stays configuration, never code.** The answering path must
     remain swappable between providers without an architectural change, so that
     moving to a self-hosted deployment is a property edit.
  This permission covers development, local testing and the owner's evaluation
  runs over his own chart. **It grants nothing about serving anyone else**, which
  the deployment rule above governs and this does not relax.
- **LLM — integration**: Spring AI 2.0.x, through its `ChatModel` abstraction
  rather than a provider type, so condition 5 holds structurally.
  `OllamaChatModel` serves a self-hosted deployment and `AnthropicChatModel`
  serves development; an ArchUnit rule names the only classes that may invoke
  either (Principle V).
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

**Version**: 3.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-11-24
