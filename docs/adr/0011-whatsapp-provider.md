# ADR 0011: WhatsApp provider

- **Status:** proposed
- **Date:** 2026-09-02
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

_Proposed:_ Option 1, behind the `MessagingChannel` port so that switching to a
BSP later is a new adapter module, not a rewrite. Reconsider if Meta Business
verification or number provisioning proves painful in practice.

## Consequences

- `channel-whatsapp` implements Cloud API specifics: versioned Graph API URLs,
  `X-Hub-Signature-256` verification, template-message format.
- A Meta Business Manager account and a verified business are required before
  go-live — external, long lead time, so start the verification early.
