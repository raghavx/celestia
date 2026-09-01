# ADR 0019: Currency and tax

- **Status:** accepted (currency) / proposed (GST)
- **Date:** 2026-09-02
- **Decision ref:** D19 (PROJECT_PLAN.md §2)

## Context

The audience is India; Razorpay settles in INR. Money must be stored without
floating-point error. GST applies to digital services sold in India; whether
invoicing obligations bite depends on turnover and registration status.

## Decision

Store all amounts as integer **paise** in `amount_minor`, with `currency = 'INR'`
explicit on every money-bearing row. Never use floating point for money — a
`Money` value type lives in `billing`.

**GST is open.** If the business is or will be GST-registered, capture minimal
billing details at checkout (name, optional GSTIN, state of supply) and use
Razorpay's invoice / GST features. If below the threshold, defer. The GST decision
does not block Phase 6 (test mode) but does block live launch.

## Consequences

- If GST is needed later, add fields to `payment` or a `billing_profile` table and
  enable Razorpay invoicing — an additive change.
