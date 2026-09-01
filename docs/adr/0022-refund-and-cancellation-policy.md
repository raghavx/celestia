# ADR 0022: Refund and cancellation policy

- **Status:** proposed (policy content needs owner / legal sign-off)
- **Date:** 2026-09-02
- **Decision ref:** D22 (PROJECT_PLAN.md §2)

## Context

Razorpay account activation requires public **Terms**, **Privacy**,
**Refund / Cancellation**, and **Contact** pages. The product also needs defined
behaviour for cancellations and refunds.

## Decision

_Proposed:_ v1 policy —

- **Cancellation:** no auto-renew in v1, so "cancel" means "do not renew"; access
  continues until `current_period_end`.
- **Refunds:** handled manually via the Razorpay dashboard, case by case
  (duplicate charge, immediate post-purchase regret within a stated window). The
  `refund.processed` / `payment.refunded` webhook is consumed regardless so
  `subscription` and `payment` state stays correct.
- Publish the four policy pages on a simple static site before live activation.

## Consequences

- The four pages are a live-launch blocker (they also carry ADR-0016's privacy notice).
- `payments-razorpay` must handle refund webhooks even though refunds are initiated
  manually.
- The subscription FSM needs a `cancel` keyword.
