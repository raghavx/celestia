# ADR 0011: WhatsApp provider

- **Status:** accepted
- **Date:** 2026-09-02
- **Accepted:** 2026-09-15 (SPEC-009 `/speckit-plan`)
- **Decision ref:** D11 (PROJECT_PLAN.md §2)

## Context

The agent needs inbound webhooks and outbound sends over WhatsApp. This is served
either by Meta's first-party WhatsApp Business Cloud API or by a Business Solution
Provider (BSP) such as Twilio, 360dialog, or Gupshup.

## Options considered

1. **Meta WhatsApp Business Cloud API directly** — first-party, free inbound,
   pay-per-conversation, you manage number registration, templates, and Meta
   Business verification.
2. **BSP** — they own onboarding and compliance and often ship friendlier SDKs;
   per-message markup; an extra vendor in the path.

## Decision

Option 1, behind the `MessagingChannel` port so that switching to a BSP later
is a new adapter module, not a rewrite. Reconsider if Meta Business
verification or number provisioning proves painful in practice.

## Consequences

- `channel-whatsapp` implements Cloud API specifics: versioned Graph API URLs,
  `X-Hub-Signature-256` verification, template-message format.
- A Meta Business Manager account and a verified business are required before
  go-live — external, long lead time, so start the verification early.
- **Port placement (SPEC-009 `/speckit-plan`, 2026-09-15):** `MessagingChannel`
  and its `WhatsAppMessagingChannel` implementation both live in
  `channel-whatsapp` — `agent` depends on `channel-whatsapp`, not the reverse,
  so the port cannot live in `agent` without a dependency cycle. Promoting the
  interface + shared value types to a dedicated module is a mechanical,
  behaviour-free move if a second channel (SMS, a BSP) is ever added.
