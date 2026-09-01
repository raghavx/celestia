# ADR 0018: Plan catalog configuration

- **Status:** accepted (structure) / proposed (initial catalog)
- **Date:** 2026-09-02
- **Decision ref:** D18 (PROJECT_PLAN.md §2)

## Context

"Configurable astrology plan" means operators add or adjust plans with no code
change and no deploy.

## Decision

Plans live in a `plan` table: `code` (stable id), `name`, `description`,
`amount_minor`, `currency`, `period` (`ONE_TIME` / `MONTHLY` / `QUARTERLY` /
`ANNUAL`), `entitlements_json` (`questions_per_day`, `horary_per_month`,
`daily_prediction`, …), `display_order`, `active`, timestamps. Seeded by a Flyway
migration; edited via a secured admin REST API (`/admin/plans`, role-gated).
WhatsApp renders only `active` plans in `display_order`. A price change never
mutates a live `subscription`; the amount actually charged is snapshotted on
`payment`. `entitlements_json` is validated against a schema on write.

**Open:** the launch catalog itself — tiers, prices, quota values — needs the
product owner's pricing input.

## Consequences

- Entitlement checks read `entitlements_json`, never hard-coded numbers.
- Adding a new entitlement key needs code (the check) plus config (the value).
- The admin API needs authentication and an audit trail (SPEC-012).
