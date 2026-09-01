# ADR 0020: Grace period and renewal reminders

- **Status:** proposed (values)
- **Date:** 2026-09-02
- **Decision ref:** D20 (PROJECT_PLAN.md §2)

## Context

With manual renewal (ADR-0017), users will lapse. A grace window and reminders
reduce involuntary churn without giving the service away.

## Decision

_Proposed:_ after `current_period_end`, a configurable **grace period** (default
**3 days**) during which the agent still answers but prepends a renewal nudge;
then the subscription moves to `EXPIRED` and Q&A is locked (subscribe flow only).
Renewal reminders are sent via approved templates at **T-3 days**, **T-0**, and
**T+1 day** (into grace). All windows and offsets are config
(`celestia.billing.grace-days`, reminder-offset list).

## Consequences

- A daily scheduled job scans subscriptions.
- Needs the WhatsApp templates from ADR-0012.
- `subscription.status` gains a `GRACE` value; the entitlement check treats
  `ACTIVE` and `GRACE` as "may answer".
