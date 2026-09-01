# ADR 0021: Razorpay credential management

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D21 (PROJECT_PLAN.md §2)

## Context

Razorpay integration needs a `key_id`, a `key_secret` (server-side only), and a
`webhook_secret` for signature verification. Test and live use different keys.

## Decision

All three come from environment variables / the platform secret store —
`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` — never
committed, never logged, and not present in `application.yml`. Keys are
per-environment (`test` locally and in CI, `live` in production). `key_secret` and
`webhook_secret` are referenced only inside `payments-razorpay`. `.env` is
git-ignored; `.env.example` lists the variable names with blank values.

## Consequences

- Local development needs a Razorpay **test** account (already a listed next action).
- CI uses dummy values; Razorpay calls are stubbed with WireMock in unit tests.
- Production needs an env-injection or secrets-store mechanism.
