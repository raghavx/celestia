# Flyway migrations (SPEC-008)

| File | Tables |
|------|--------|
| `V1__identity.sql` | `app_user` |
| `V2__birth_data.sql` | `birth_data` |
| `V3__chart_cache.sql` | `chart`, `horary_query`, `dasha_period` (table only — SPEC-013 populates) |
| `V4__prediction_cache.sql` | `prediction` |
| `V5__geocode_cache.sql` | `geocode_cache` |
| `V6__async_io.sql` | `inbound_event`, `outbox_message` |

## Rules

- **A merged migration is immutable.** Never edit a `V*.sql` that has been
  applied anywhere — CI runs `flyway validate` and a checksum mismatch fails the
  build (SPEC-008 FR-018). A schema change is a **new** `V*` file.
- MySQL 8.4, `ENGINE=InnoDB`, `utf8mb4_0900_ai_ci`.
- `spring.jpa.hibernate.ddl-auto: validate` — Hibernate never writes DDL.
- Money columns (SPEC-011/012) will use integer minor units (paise).
- The billing / payment / guardrail tables and `conversation` / `message` are
  owned by the specs that add them (009 / 011 / 012 / 014).
