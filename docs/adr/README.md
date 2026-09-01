# Architecture Decision Records

Each ADR captures one decision from `PROJECT_PLAN.md` §2 (D1–D23). Format:
`docs/adr/0000-adr-template.md`.

**Status** is one of: `proposed` (needs an owner decision or external input),
`accepted` (settled — and, where noted, implemented), `superseded`.

Per the Constitution, an ADR must be `accepted` before the phase that depends on
it starts. Amending an accepted ADR requires a new ADR that supersedes it.

| ADR | Decision | Status | Blocks |
|-----|----------|--------|--------|
| [0001](0001-swiss-ephemeris-licensing.md) | Swiss Ephemeris licensing | proposed | Phase 1 |
| [0002](0002-ephemeris-data-files.md) | Ephemeris data source (`.se1` + Moshier fallback, 1800–2100) | accepted | Phase 1 |
| [0003](0003-ayanamsa.md) | Ayanamsa (KP-New default, enum config) | accepted | Phase 1 |
| [0004](0004-house-system.md) | House system (Placidus sidereal, fixed) | accepted | Phase 1 |
| [0005](0005-lunar-node.md) | Lunar node (mean default, true optional) | accepted | Phase 1 |
| [0006](0006-build-tooling.md) | Build tooling (Maven multi-module + wrapper) | accepted (done) | — |
| [0007](0007-jdk-version.md) | JDK version (Java 25 LTS) | accepted (done) | — |
| [0008](0008-test-database.md) | Test database (Testcontainers + Colima) | proposed | Phase 3 |
| [0009](0009-llm-runtime-and-model.md) | LLM runtime & model (Ollama + Llama 4 Scout + fallback) | proposed | Phase 6 |
| [0010](0010-ollama-hosting.md) | Ollama hosting & deployment | proposed | Phase 6 |
| [0011](0011-whatsapp-provider.md) | WhatsApp provider (Meta Cloud API) | proposed | Phase 4 |
| [0012](0012-whatsapp-number-and-templates.md) | WhatsApp number & message templates | proposed | Phase 4 |
| [0013](0013-geocoding.md) | Geocoding of birth place (OpenCage v1, cached) | proposed | Phase 3 |
| [0014](0014-birthtime-to-utc.md) | Birth-time → UTC (`timeshape` + `java.time`) | accepted | Phase 3 |
| [0015](0015-async-message-processing.md) | Async processing (in-DB inbox/outbox + worker) | accepted | Phase 4 |
| [0016](0016-consent-and-data-deletion.md) | Consent & data deletion | accepted | Phase 5 |
| [0017](0017-billing-model.md) | Billing model (Razorpay Payment Links v1) | proposed | Phase 6 |
| [0018](0018-plan-catalog.md) | Plan catalog configuration | accepted / catalog open | Phase 6 |
| [0019](0019-currency-and-tax.md) | Currency (INR/paise) & GST | accepted / GST open | Phase 6 / launch |
| [0020](0020-grace-and-renewal-reminders.md) | Grace period & renewal reminders | proposed | Phase 6 |
| [0021](0021-razorpay-credentials.md) | Razorpay credential management | accepted | Phase 6 |
| [0022](0022-refund-and-cancellation-policy.md) | Refund & cancellation policy | proposed | launch |
| [0023](0023-topic-gate-mechanism.md) | Topic-gate mechanism | proposed | Phase 7 |

## Still needing an owner decision

`proposed` ADRs that need input before their phase: **0001** (licensing — legal /
commercial), **0009 / 0010** (LLM hardware — procurement), **0011 / 0012**
(WhatsApp — Meta Business account), **0013** (geocoder API key), **0017 / 0020**
(billing model & grace values — product), **0022** (policy pages — legal),
**0023** (guardrail approach — confirm the recommendation).
