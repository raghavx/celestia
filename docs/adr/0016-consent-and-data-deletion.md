# ADR 0016: Consent and data deletion

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D16 (PROJECT_PLAN.md §2)

## Context

Name, date/time/place of birth, and phone number are personal data. India's DPDP
Act (and GDPR-style norms generally) require a lawful basis, purpose limitation,
and a deletion right. WhatsApp is the only user interface.

## Options considered

- Implicit vs. explicit consent.
- Soft-delete vs. hard-delete on request.

## Decision

On first contact, **before** collecting any birth data, send a short notice
(purpose, what is stored, how to delete, link to the privacy page) and record
`consent_at` once the user chooses to proceed. A `DELETE` / "forget me" keyword
(documented in help) triggers immediate deactivation plus a scheduled hard-delete
job that removes `birth_data`, `chart`, `prediction`, and message bodies, and
anonymises `app_user` — retaining only a phone hash and the payment/tax records
that financial-retention law requires.

## Consequences

- The privacy page is a Phase 0 / Phase 8 deliverable (also required by Razorpay,
  ADR-0022).
- The deletion job and retention rules are part of SPEC-012.
- Payment and tax records are exempt from deletion; the first-contact notice must
  say so.
