# Phase 0 Research: Persistence & Domain Services (SPEC-008)

Design decisions for the first stateful layer. The pure engine and `geo` are
untouched; everything here is in `agent`.

## 1. Module & layering

**Decision**: JPA lives only in `com.celestia.agent.persistence`. Three layers:

- `entity/` — `@Entity` classes, one per table. Not exported: other packages
  never import them.
- `repository/` — Spring Data JPA interfaces.
- `store/` — the **only** public surface: plain `@Service` classes that take and
  return domain values (`ResolvedBirth`, `BirthData`, `NatalChart`,
  `DailyPrediction`, `GeocodeResult`) and hide the entities.

Domain modules that need storage expose a **port**; `agent` supplies the adapter.
Today the only port is `com.celestia.geo.place.GeocodeCache` → `JpaGeocodeCache`.
`core` needs no port — `ChartCacheService` simply *calls* the pure `core`
factories (via beans from `EngineConfig`).

**ArchUnit** (`PersistenceArchitectureTest`, new): `noClasses().that()
.resideInAnyPackage("com.celestia.ephemeris..", "com.celestia.core..",
"com.celestia.geo..", "com.celestia.billing..", "com.celestia.guardrail..")
.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..",
"org.springframework.data..", "org.hibernate..", "org.flywaydb..", "java.sql..",
"javax.sql..", "com.zaxxer.hikari..")`. Mirrors the existing no-Spring rule.

## 2. The engine-versioned cache

**The rule (Constitution III)**: a stored `chart` / `prediction` row is served
**only** when its `engine_version` string equals the version a fresh computation
would carry; otherwise recompute, overwrite, return fresh.

- **`CurrentEngineVersion`** — a `@Component` that returns the expected
  `EngineVersion` for a given `BirthData`:
  - `rules` = `EngineVersion.RULES` (constant `"kp-1"`)
  - `deltaTModel` = `TimeScales.DELTA_T_MODEL` (constant)
  - `sePort` = from `SwissEphemerisConfig` (a bean; a constant string for the
    pinned JitPack build)
  - `ephemerisData` = accuracy-dependent: `swieph:<id>` when the birth year is
    inside the configured `.se1` range, else `moseph`. Predicted from the year,
    not from a computation.
  - `.id()` joins them with `/`.
- **Serve-or-recompute**: `ChartCacheService.natalChart(userId)`:
  1. load `birth_data` → `BirthData`.
  2. `expected = currentEngineVersion.forBirth(birthData).id()`.
  3. load the `chart` row (`user_id`, `kind = NATAL`). If present **and**
     `row.engine_version.equals(expected)` **and** the JSON deserialises →
     return it.
  4. else compute via `NatalChartFactory` (+ `SignificatorTable.of`,
     `DashaTimeline.from`, `RulingPlanetsFactory`), serialise, **upsert** the
     `chart` row and replace its `dasha_period` children, return fresh.
- **When in doubt, recompute.** A JSON deserialise failure, a missing row, an
  unrecognised `engine_version` — all fall through to recompute. Recompute is
  always correct; a bad cache hit is not (principle III).
- **Lazy** — recomputation happens on the next read. No background sweep; that is
  SPEC-016 ops. A deploy that changes `RULES` needs no migration — the next read
  per user self-heals.
- **Cold-cache race**: two requests for the same cold chart may both compute.
  Resolution: the `chart` row has a unique key on `(user_id, kind)`; the loser's
  `INSERT` hits the constraint → catch and re-read (the value is identical
  anyway, so last-write-wins is also safe). No lock needed.
- **`@Transactional`**: the chart write + its `dasha_period` delete/insert are
  one transaction (FR-016). `forget(userId)` is one transaction across five
  tables.

## 3. Chart / prediction JSON

**Decision**: `computed_json` / `payload_json` hold a Jackson serialisation of
the domain aggregate, produced by a dedicated codec.

- A shared `ObjectMapper` (`PersistenceJson`): `JavaTimeModule`,
  `WRITE_DATES_AS_TIMESTAMPS=false`, a `BigFraction` serialiser/deserialiser
  (canonical `"num/den"` string — `BigFraction` is in `core`'s public API),
  records supported natively (Jackson 2.15+/Boot 4.1).
- Every payload carries a top-level `"schema"` integer. A codec bumps it when the
  serialised shape changes.
- **Deserialise failure ⇒ cache miss.** `ChartCodec.read(json)` returns
  `Optional<NatalChart>`; any exception (shape drift, unknown `schema`, unknown
  enum) → `Optional.empty()` → recompute + overwrite. This makes a structural
  change that didn't bump `engine_version` self-healing rather than a bug.
- The JSON is an internal cache representation, **not** an API contract — no
  external consumer reads it directly. SPEC-015 reads `NatalChart` via
  `ChartCacheService`, not the column.

## 4. Identity idempotency

- `app_user.phone_e164` — `VARCHAR(20)`, unique, the E.164 string
  (`+<country><number>`), validated by the caller (SPEC-009 verifies the WhatsApp
  sender).
- `UserAccountService.findOrCreate(phoneE164)`:
  - `repo.findByPhoneE164(p)` → return if present.
  - else `repo.save(new AppUserEntity(p))` inside a `try`; on
    `DataIntegrityViolationException` (the unique key — a concurrent create won)
    → `repo.findByPhoneE164(p).orElseThrow()`.
  - SC-003: 20 concurrent calls → one row, no caller sees the exception.
- `recordConsent(userId)` — sets `consent_at = now()` **only if null**
  (`UPDATE ... SET consent_at = ? WHERE id = ? AND consent_at IS NULL`).
- `forget(userId)` — `@Transactional`: delete `prediction`, `horary_query` (via
  `chart`), `dasha_period` (via `chart`), `chart`, `birth_data` for the user;
  then `UPDATE app_user SET display_name = NULL, wa_id = NULL,
  phone_e164 = CONCAT('deleted:', SHA2(phone_e164, 256)), deleted_at = now()`.
  Payment/tax rows are out of scope here and exempt (ADR-0016). A **pepper** on
  the hash is a SPEC-016 hardening — v1 uses a plain SHA-256 so a "forget me"
  cannot be reversed but a re-contact from the same number still creates a fresh
  user (the hashed value never matches a raw E.164 lookup).

## 5. `geocode_cache`

- Columns: `id`, `query_norm VARCHAR(255)` unique, `results_json JSON`,
  `created_at`, `updated_at`.
- `JpaGeocodeCache implements com.celestia.geo.place.GeocodeCache`:
  - `get(queryNorm)` → `repo.findByQueryNorm` → `GeocodeResultCodec.read` →
    `Optional<GeocodeResult>`; a deserialise failure → `Optional.empty()`
    (re-geocode).
  - `put(queryNorm, result)` → upsert (`INSERT ... ON DUPLICATE KEY UPDATE`
    or find-then-save).
- `results_json` = `{ "schema": 1, "query": {...}, "candidates": [ ... ] }` —
  the `GeocodeResult` shape. Value equality on read (SC-005).
- The SPEC-007 `PlaceQuery` in the result is reconstructed from the stored
  `query` object; the port only promises the candidate list is equal.

## 6. Async inbox / outbox

- **`inbound_event`**: `id`, `wa_message_id VARCHAR(128)` unique, `raw_ref`
  (a pointer to the stored raw payload — object store / blob, out of scope here,
  a string), `status` (`NEW`/`CLAIMED`/`DONE`/`FAILED`), `received_at`,
  `processed_at`, `attempts`.
  - `recordInbound(waMessageId, rawRef)` → `INSERT`; on the unique-key violation
    return `Duplicate(existingId)`, else `Recorded(id)`. Idempotent (SC-007).
  - `claimNext()` → `SELECT id FROM inbound_event WHERE status = 'NEW'
    ORDER BY received_at LIMIT 1 FOR UPDATE SKIP LOCKED`, then
    `UPDATE ... SET status='CLAIMED', attempts = attempts + 1`. Correct for one
    instance; `SKIP LOCKED` already makes it multi-instance safe. Returns
    `Optional<ClaimedEvent>`.
  - `markProcessed(id)` / `markFailed(id, error)`.
- **`outbox_message`**: `id`, `user_id`, `target` (phone / conversation ref),
  `payload_json`, `status` (`PENDING`/`SENT`/`FAILED`), `attempts`, `error`,
  `created_at`, `updated_at`.
  - `enqueue(...)`, `claimNextPending()` (same `SKIP LOCKED` pattern),
    `markSent(id)`, `markFailed(id, error)`.
- The webhook, the worker `@Scheduled` loop, and `ShedLock` are **SPEC-009**;
  this spec ships the tables and the store operations only (ADR-0015).

## 7. Flyway + Testcontainers

- **Migrations**: `agent/src/main/resources/db/migration/V1..V6` (grouped by
  concern — §"Project Structure"). Engine: `spring.jpa.hibernate.ddl-auto:
  validate`, `spring.flyway.enabled: true`, `baseline-on-migrate: false`.
  `utf8mb4_0900_ai_ci`. FK constraints `ON DELETE CASCADE` where the child is
  meaningless without the parent (`dasha_period` → `chart`).
- **Immutability** (FR-018): once a `V*.sql` is merged it is never edited. CI
  runs the app context / `flyway:validate` so an edited applied migration fails
  the build (checksum mismatch). A schema change is a new `V*` file.
- **Testcontainers**: `org.testcontainers:mysql` `MySQLContainer<>("mysql:8.4")`,
  Spring Boot `@ServiceConnection` (no manual datasource props), one static
  container per test class hierarchy (`PersistenceIT` base), `withReuse(true)`
  for local speed.
- **This dev box has Podman, not Docker.** Testcontainers supports Podman via
  `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock` (+
  `TESTCONTAINERS_RYUK_DISABLED=true` if the socket is rootless).
  `/speckit-implement` will try this; if the runtime cannot start locally the
  integration tests are written and verified in CI (Docker on `ubuntu-latest`),
  with the unit-level tests (codecs, `CurrentEngineVersion`, mappers, ArchUnit)
  runnable everywhere. ADR-0008's local-MySQL 8.4 path is the documented fallback.
- **ADR-0008** moves `proposed → accepted` (dated 2026-09-11): Testcontainers
  `mysql:8.4`, `@ServiceConnection`, reuse; devs install Colima **or** use
  Podman **or** a local MySQL 8.4.

## 8. Test fixtures

- A `birth_data` fixture set derived from the SPEC-007 corpus (`geo`'s
  `birthmoments/corpus.json` shapes) — same places / instants, now persisted and
  reloaded (SC-001).
- **Counting engine** for SC-002: a `NatalChartFactory` (and prediction factory)
  wrapper that increments a counter on each real computation. A cache hit ⇒
  counter unchanged; a stale-version request ⇒ counter +1.
- `CurrentEngineVersion` unit tests: the same `BirthData` in / out of the `.se1`
  range → `swieph` vs `moseph` tag; a `RULES` bump flips every stored row to
  "stale".

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | Layering | JPA in `agent.persistence`; `store` package the only public surface; ports for domain needs; new ArchUnit rule |
| 2 | Versioned cache | serve only on exact `engine_version` match; recompute when in doubt; lazy, no sweep; unique-key race handling |
| 3 | Chart JSON | Jackson + `schema` tag + `BigFraction`-as-string; deserialise failure ⇒ miss ⇒ recompute |
| 4 | Identity | `findOrCreate` catches the unique-key race and re-reads; `consent_at` write-once; `forget` = tx delete PII + SHA-256 phone hash |
| 5 | Geocode cache | `JpaGeocodeCache` implements the `geo` port; `results_json`; upsert on `query_norm` |
| 6 | Async I/O | `inbound_event` unique `wa_message_id` → `Recorded`/`Duplicate`; `claimNext` via `FOR UPDATE SKIP LOCKED`; outbox attempts/error; worker is SPEC-009 |
| 7 | Migrations / tests | grouped `V1..V6`, immutable, `validate` in CI; Testcontainers `mysql:8.4` (`@ServiceConnection`, reuse); Podman/local-MySQL fallback; ADR-0008 accepted |
| 8 | Fixtures | SPEC-007 corpus → `birth_data` fixtures; a counting engine for SC-002 |
