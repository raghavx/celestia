# ADR 0017: Billing model — Payment Links vs Subscriptions

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D17 (PROJECT_PLAN.md §2)

## Context

Users subscribe to a plan and pay via Razorpay. Razorpay offers **Payment Links**
(a one-off hosted checkout URL) and **Subscriptions** (recurring, backed by a UPI
AutoPay or card mandate). Mandate setup adds friction over WhatsApp, and many
Indian users are wary of autopay.

## Options considered

1. **Payment Links**, one per billing period; the app tracks `current_period_end`
   and sends a renewal link before expiry. Manual renewal.
2. **Razorpay Subscriptions** — true auto-renew; mandate friction; a richer
   webhook set (`subscription.charged`, `.halted`, …).
3. Both, user's choice.

## Decision

_Proposed:_ Option 1 for v1. The `subscription` and `payment` entities already
carry `razorpay_subscription_id` (nullable) and `auto_renew`, so Subscriptions can
be added later without a schema rewrite. Renewal reminders are covered by ADR-0020.

## Consequences

- No dunning automation from Razorpay — the app owns renewal reminders and the lock.
- Simpler webhook handling: `payment_link.paid`, `payment.captured`,
  `payment.failed`, `refund.processed`.
- Revisit once retention data justifies the mandate friction.
