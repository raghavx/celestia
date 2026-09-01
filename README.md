# Celestia

A WhatsApp-based Krishnamurti Paddhati (KP) astrology agent. A self-hosted LLM
(Llama 4 via Ollama) answers questions about a user's natal chart by orchestrating
a deterministic KP calculation engine, gated by a paid subscription.

- **Plan:** [`PROJECT_PLAN.md`](PROJECT_PLAN.md)
- **Constitution:** [`.specify/memory/constitution.md`](.specify/memory/constitution.md)
- **Specs:** `specs/` (spec-driven development via GitHub Spec Kit)

## Build

```bash
./mvnw verify
```

Requires JDK 25. The Maven Wrapper downloads Maven 3.9.11 on first run.

## Modules

| Module | Purpose | Spring? |
|--------|---------|---------|
| `ephemeris` | Swiss Ephemeris wrapper: positions, cusps | no |
| `core` | KP domain: lords, significators, dasha, horary, predictions | no |
| `geo` | geocoding + timezone → UTC | no |
| `billing` | plan catalog, subscriptions, entitlements; `PaymentGateway` port | no |
| `guardrail` | injection detector + astrology-only topic gate + output filter | no |
| `payments-razorpay` | Razorpay adapter | thin |
| `channel-whatsapp` | WhatsApp Cloud API adapter | thin |
| `agent` | Spring Boot app: orchestrator, FSMs, guarded LLM pipeline, persistence | yes |

## Spec-driven workflow

`/speckit-constitution` → `/speckit-specify` → `/speckit-clarify` → `/speckit-plan`
→ `/speckit-tasks` → human review → `/speckit-implement`.

Next: resolve decisions **D1–D23** (see `PROJECT_PLAN.md` §2) as ADRs under
`docs/adr/`, then `/speckit-specify` for SPEC-001.
