# ADR 0012: WhatsApp number and message templates

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D12 (PROJECT_PLAN.md §2)

## Context

Meta permits free-form replies only within 24 hours of the user's last inbound
message (the "customer-service window"). Anything outside — renewal reminders,
daily prediction push, re-engagement — must be a pre-approved **template
message**. Template approval takes hours to days; templates have a fixed structure
with named variables.

## Options considered

- Register a new dedicated number vs. port an existing one.
- Which templates to submit for approval up front.

## Decision

_Proposed:_ register a **dedicated** WhatsApp Business number (not a personal
one). Pre-submit templates for: subscription renewal reminder (T-3d / T-0 /
T+1d), payment-link resend, onboarding nudge, and daily prediction (if SPEC-017 is
built). The `channel-whatsapp` adapter tracks `window_expires_at` per user and
automatically chooses free-form vs. template.

## Consequences

- The template catalog lives in config (`resources/whatsapp-templates/`); any
  wording change needs re-approval.
- The 24-hour-window tracker is a required part of SPEC-009.
