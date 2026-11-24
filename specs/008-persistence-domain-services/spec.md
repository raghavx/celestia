# Feature Specification: Persistence & Domain Services (SPEC-008)

**Feature Branch**: `008-persistence-domain-services`

**Created**: 2026-09-11

**Status**: Draft

**Input**: PROJECT_PLAN.md — "SPEC-008 Persistence + domain services + engine
versioning. Depends on 002–007." Phase 3 exit: "JPA entities + Flyway migrations;
domain services; engine versioning. Given name/DOB/TOB/place text, the system
stores `birth_data` with the correct `birth_utc`; a stored chart is recomputed
when `engine_version` changes." §4 data model (MySQL 8.x via Flyway); ADR-0008
(test database, *proposed*), ADR-0015 (async — `inbound_event` + outbox schema
"are part of SPEC-008"), ADR-0016 (consent + "forget me" hard-delete).

_Everything above this spec is pure and stateless — the KP engine (SPEC-001–006)
and the geo resolver (SPEC-007) take inputs and return values. Everything below it
— the WhatsApp channel, onboarding, plans, payments, the Q&A agent — needs a
place to keep a person's identity, their birth data, their computed charts, and
the in-flight messages, and needs those reads to be **fast** and **never stale**.
This feature is the database: the MySQL schema, the Spring Data JPA layer, the
handful of domain services the rest of the system calls, and the rule that a
cached chart computed under an old engine version is silently recomputed._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Persist and reload a person's birth data (Priority: P1)

Given a resolved birth (the SPEC-007 `ResolvedBirth`) and a user identity, the
system stores it as the one `birth_data` row for that user and returns it on
demand — the local date/time, `zone_id`, `birth_utc`, latitude, longitude, place
label, country, `geocode_source`, and the resolution flags — byte-for-byte as
stored. Re-resolving and re-saving replaces the row.

**Why this priority**: This is the Phase 3 exit criterion and the thing SPEC-010
(onboarding) writes and SPEC-015 (Q&A) reads on every turn.

**Independent Test**: Save a `ResolvedBirth`, reload it, assert every field
matches; the `(instant, lat, lon)` reloaded equals what a chart cast for that
birth would use.

**Acceptance Scenarios**:

1. **Given** a `ResolvedBirth` for a user, **When** it is saved and reloaded,
   **Then** `birth_utc`, `lat`, `lon`, `zone_id`, `birth_time_known`, and the
   flags are exactly the stored values (no precision loss, no timezone drift).
2. **Given** a user who already has birth data, **When** a new `ResolvedBirth` is
   saved for them, **Then** the single row is replaced, not duplicated
   (`user_id` is unique).
3. **Given** a stored birth, **When** the chart pipeline reads it, **Then** it
   receives a `BirthData` value identical to the one produced at resolution time.

---

### User Story 2 - Engine-versioned chart & prediction cache (Priority: P1)

Given a user's birth data, the system returns their natal chart: from the `chart`
cache when the stored row's `engine_version` matches the **current** engine,
otherwise it recomputes the chart with the KP engine (SPEC-002–006), overwrites
the row, and returns the fresh result. The same rule governs horary charts,
dasha timelines, and daily predictions. A chart computed under a superseded
engine version is **never** served.

**Why this priority**: Constitution III — "cached charts computed under an older
version are recomputed." Without this, a rule fix silently leaves wrong answers
in the cache.

**Independent Test**: Store a chart row with a stale `engine_version`; request
the chart; assert it was recomputed, the row overwritten, and the served result
carries the current version.

**Acceptance Scenarios**:

1. **Given** no cached chart for a user, **When** the natal chart is requested,
   **Then** it is computed once, stored with the current `engine_version`, and
   returned.
2. **Given** a cached chart whose `engine_version` equals the current one,
   **When** the chart is requested again, **Then** it is served from the cache
   with no recomputation.
3. **Given** a cached chart whose `engine_version` differs from the current one,
   **When** the chart is requested, **Then** it is recomputed, the row is
   overwritten, and the stale JSON is gone.
4. **Given** a daily prediction cached for `(user, date, engine_version)`,
   **When** the engine version changes, **Then** the next request for that date
   recomputes.

---

### User Story 3 - Identity store (Priority: P1)

The system finds or creates an `app_user` keyed on the verified `phone_e164`,
records `consent_at` when the user agrees to the first-contact notice, and — on a
"forget me" request — removes the user's `birth_data`, `chart`, `dasha_period`,
`horary_query`, and `prediction` rows and anonymises `app_user` (keeping only a
phone hash). Concurrent first-contact for the same number yields exactly one user.

**Why this priority**: The phone number is the identity (Constitution VII);
every other table hangs off `app_user`.

**Independent Test**: Look up an unknown number twice concurrently → one row;
capture consent → `consent_at` set; "forget me" → all PII rows gone, `app_user`
anonymised, phone hash retained.

**Acceptance Scenarios**:

1. **Given** an unknown `phone_e164`, **When** it is looked up, **Then** a new
   `app_user` is created and returned; a second lookup returns the same row.
2. **Given** two simultaneous first-contacts for the same number, **When** both
   resolve, **Then** exactly one `app_user` exists (unique constraint upheld).
3. **Given** a user who has agreed to the notice, **When** consent is recorded,
   **Then** `consent_at` is a timestamp and is not overwritten on later contact.
4. **Given** a user with birth data and cached charts, **When** "forget me" runs,
   **Then** `birth_data` / `chart` / `dasha_period` / `horary_query` /
   `prediction` rows for that user are deleted, `display_name` / `wa_id` are
   cleared, and a non-reversible phone hash remains.

---

### User Story 4 - Persistent geocode cache (Priority: P2)

A JPA implementation of SPEC-007's `GeocodeCache` port, backed by `geocode_cache`
(unique on `query_norm`). A cache hit returns the identical candidate list with
no geocoder call, and survives application restarts and multiple instances.

**Why this priority**: ADR-0013 — "the cache layer is required, not optional."
It makes geocoding deterministic and free after the first lookup.

**Independent Test**: Put a `GeocodeResult`, get it back in a new persistence
context, assert the candidate list is equal; a second put for the same
`query_norm` updates in place.

**Acceptance Scenarios**:

1. **Given** a normalised query not in the cache, **When** `get` is called,
   **Then** it returns empty.
2. **Given** a `GeocodeResult` stored under a `query_norm`, **When** `get` is
   called with that key in a fresh context, **Then** the returned result's
   candidate list equals the stored one.
3. **Given** a `query_norm` already cached, **When** a new result is put for it,
   **Then** the row is updated, not duplicated.

---

### User Story 5 - Idempotent async inbox & outbox (Priority: P2)

An idempotent insert into `inbound_event` keyed on `wa_message_id` (a duplicate
delivery is a no-op), a "claim the next unprocessed event" primitive, and an
outbox for outbound messages with attempt/error tracking. The webhook and the
worker loop are SPEC-009; this feature provides the tables and the store
operations.

**Why this priority**: ADR-0015 — "`inbound_event` and outbox schema are part of
SPEC-008"; at-least-once delivery means every step must be idempotent.

**Independent Test**: Insert the same `wa_message_id` twice → one row, second
returns "already seen"; claim → marks in-progress; an outbox row records
attempts and the last error.

**Acceptance Scenarios**:

1. **Given** an inbound event with a `wa_message_id`, **When** it is recorded
   twice, **Then** the second call is a no-op and reports the duplicate.
2. **Given** unprocessed inbound events, **When** a worker claims one, **Then**
   it is marked so a second claimer skips it, and `processed_at` is set on
   completion.
3. **Given** an outbound message, **When** a send attempt fails, **Then**
   `attempts` increments and `error` records the reason; a later retry can
   resume.

---

### User Story 6 - Schema migrations & a real-MySQL test harness (Priority: P2)

The whole in-scope schema is a set of ordered Flyway migrations that apply
cleanly to an empty MySQL 8.4, and a Testcontainers-backed base class runs every
persistence test against real MySQL (ADR-0008 — not H2).

**Independent Test**: `flyway migrate` on an empty database succeeds and is
re-runnable (no pending changes on a second run); the migration checksum set is
validated in CI.

**Acceptance Scenarios**:

1. **Given** an empty MySQL 8.4 schema, **When** the migrations run, **Then**
   every in-scope table exists with the documented columns, keys, and
   `utf8mb4` collation.
2. **Given** the migrations have run, **When** they run again, **Then** Flyway
   reports no pending migrations and validates all checksums.
3. **Given** the persistence test suite, **When** it runs in CI, **Then** it
   executes against a `mysql:8.4` container, not an embedded database.

---

### Edge Cases

- A `ResolvedBirth` with a `null` `statedZoneId` vs. one carrying a
  `ZONE_CONFLICT` — the flag set and the stated zone must round-trip together.
- A chart JSON that is large (full natal + 5-level dasha) — the `computed_json`
  column must hold it (MySQL `JSON` / `LONGTEXT`).
- Two requests for the same user's chart racing when the cache is cold — only one
  computation should win; the other reads the stored row (or recomputes
  harmlessly and overwrites with an identical value).
- The current engine version string changes mid-process (a redeploy) — the next
  read sees the mismatch and recomputes; no restart required.
- "Forget me" for a user who has a `payment` row (future) — payment/tax records
  are **exempt** from deletion (ADR-0016); only PII rows are removed.
- A Flyway migration that has already been applied is edited — CI must fail on
  the checksum mismatch, not silently diverge.
- Clock: `created_at` / `updated_at` are database-generated (`CURRENT_TIMESTAMP`);
  the pure engine still receives no clock.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All schema changes MUST be Flyway migrations under a versioned
  location; no runtime DDL, no Hibernate `ddl-auto` beyond `validate`.
- **FR-002**: The in-scope schema MUST include `app_user`, `birth_data`,
  `chart`, `dasha_period`, `horary_query`, `prediction`, `geocode_cache`,
  `inbound_event`, and `outbox_message`, with the key columns from PROJECT_PLAN
  §4, `utf8mb4` collation, and the documented unique constraints
  (`app_user.phone_e164`, `birth_data.user_id`, `geocode_cache.query_norm`,
  `inbound_event.wa_message_id`).
- **FR-003**: `birth_data` MUST persist and return, without loss, every
  place-and-time-derived field of a `ResolvedBirth` (SPEC-007) plus the raw
  `place_query` and the person's `name` supplied by the caller; `birth_utc` is
  stored as a UTC timestamp and reloads to the identical `Instant`.
- **FR-004**: A `BirthDataStore` MUST expose save (upsert by `user_id`) and load;
  the loaded value MUST yield a `BirthData` identical to resolution time.
- **FR-005**: A chart-cache service MUST, for a natal-chart request, return the
  stored `chart` row when its `engine_version` equals the current engine
  version, and otherwise recompute via the KP engine, overwrite the `chart` row
  transactionally, and return the fresh result. (Populating the dependent
  `dasha_period` rows is SPEC-013's; horary charts — which need a judgment
  moment / place — are SPEC-015's. The `chart.HORARY` kind and `horary_query`
  table are created here, unused.)
- **FR-006**: The **current engine version** MUST be a single well-defined string
  assembled from `EngineVersion` (rules + SE port + ΔT model + ephemeris data
  identity) and available as an injectable value.
- **FR-007**: A daily-prediction cache MUST be keyed on
  `(user, for_date, engine_version)` and recompute on an engine-version change or
  a cache miss.
- **FR-008**: A `UserAccountService` MUST find-or-create an `app_user` by
  `phone_e164` idempotently under concurrency (a race yields one row, not a
  constraint-violation error surfaced to the caller).
- **FR-009**: The service MUST record `consent_at` once and MUST NOT overwrite an
  existing `consent_at`.
- **FR-010**: A "forget me" operation MUST delete the user's `birth_data`,
  `chart`, `dasha_period`, `horary_query`, and `prediction` rows and anonymise
  `app_user` (clear `display_name` / `wa_id`, retain a non-reversible phone
  hash, set `deleted_at`). Payment/tax records are out of scope here and exempt
  (ADR-0016). The *scheduling* of the job is SPEC-012 **in the 2026-09 numbering
  this spec was written against — not the SPEC-012 that exists today.** See
  PROJECT_PLAN.md "Renumbering from the 2026-09 plan"; every SPEC-0NN reference
  in this document uses the old scheme (onboarding is "SPEC-010" here and 017
  now, Q&A is "SPEC-015" here and 012 now). Deleting the user's
  `inbound_event` / `outbox_message` payload rows (which can contain a phone
  number or message text — "message bodies" in ADR-0016) is **SPEC-009's**
  "forget me" extension: SPEC-009 owns those tables' payload semantics and the
  raw-payload store they point at, so it removes them when it wires the worker.
- **FR-011**: A `JpaGeocodeCache` MUST implement `com.celestia.geo.place.GeocodeCache`
  against `geocode_cache`, keyed on `query_norm`; `get` on a hit returns a
  `GeocodeResult` whose candidate list **equals** the stored one; `put` upserts.
- **FR-012**: An `InboxStore` MUST record an inbound event idempotently on
  `wa_message_id` (duplicate → no-op + a "seen" signal) and expose a
  claim-next-unprocessed operation that is safe against a second concurrent
  claimer.
- **FR-013**: An `OutboxStore` MUST persist an outbound message with `attempts`
  and `error`, and support marking sent / failed.
- **FR-014**: JPA entities and repository types MUST live in the `agent` module
  (`com.celestia.agent.persistence..`); the pure modules
  (`ephemeris`, `core`, `geo`, `billing`, `guardrail`) MUST NOT gain a
  dependency on Spring Data, JPA, or a JDBC driver (Constitution II). Domain
  code that needs storage uses a **port** (e.g. `geo`'s `GeocodeCache`); the
  JPA class is the adapter.
- **FR-015**: No JPA/Hibernate type may appear in a method signature that a pure
  module calls; the persistence layer converts at its own boundary (to
  `ResolvedBirth`, `NatalChart`, `GeocodeResult`, `BirthData`, …).
- **FR-016**: Reads and writes that span more than one table (chart + its
  `dasha_period` rows; "forget me") MUST be transactional; a failure leaves no
  partial state.
- **FR-017**: Every persistence test MUST run against a real `mysql:8.4`
  (Testcontainers, ADR-0008), sharing one container per suite; H2/embedded is
  not permitted for these tests.
- **FR-018**: Flyway migrations MUST be immutable once merged; CI MUST run
  `flyway validate` (or the Spring Boot equivalent) so an edited applied
  migration fails the build.
- **FR-019**: Monetary columns are **out of scope** for this spec; when the
  billing tables land (SPEC-011/012) amounts MUST be integer minor units
  (paise) — recorded here as a constraint for the migration author.
- **FR-020**: `created_at` / `updated_at` MUST be database-generated; no pure
  module reads a clock as a side effect of persistence.

### Key Entities *(include if data involved)*

- **`app_user`**: identity — `phone_e164` (unique), `wa_id`, `display_name`,
  `locale`, `consent_at`, `onboarding_state`, `guardrail_strikes`,
  `cooldown_until`, `created_at`, `deleted_at`.
- **`birth_data`**: one per user — `name`, `birth_date`, `birth_time`,
  `birth_time_known`, `place_query`, `place_name`, `country`, `lat`, `lon`,
  `zone_id`, `birth_utc`, `geocode_source`, resolution flags, timestamps.
- **`chart`**: the engine cache — `user_id?`, `kind` (NATAL/HORARY), `ayanamsa`,
  `house_system`, `engine_version`, `computed_json`, `created_at`.
- **`dasha_period`** / **`horary_query`**: rows dependent on a `chart`.
- **`prediction`**: daily-prediction cache — `user_id`, `for_date`, `kind`,
  `payload_json`, `engine_version`, `model`, `created_at`.
- **`geocode_cache`**: `query_norm` (unique), `results_json`, `created_at`.
- **`inbound_event`**: `wa_message_id` (unique), `raw_ref`, `received_at`,
  `processed_at`, `attempts`.
- **`outbox_message`**: outbound queue — target, payload, `status`, `attempts`,
  `error`, timestamps.
- **Domain services** (in `agent`): `UserAccountService`, `BirthDataStore`,
  `ChartCacheService`, `PredictionCacheService`, `JpaGeocodeCache`,
  `InboxStore`, `OutboxStore`, `CurrentEngineVersion`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A `ResolvedBirth` saved and reloaded is field-for-field identical;
  `birth_utc` reloads to the exact `Instant` and the reconstructed `BirthData`
  equals the original for 100% of a fixture set spanning the SPEC-007 corpus.
- **SC-002**: A chart request with a matching cached `engine_version` performs
  **zero** engine computations (verified with a counting/spy engine); a request
  with a stale version performs exactly one and overwrites the row.
- **SC-003**: Under 20 concurrent first-contacts for one `phone_e164`, exactly
  one `app_user` row exists and no caller receives a constraint-violation error.
- **SC-004**: "Forget me" leaves zero `birth_data` / `chart` / `dasha_period` /
  `horary_query` / `prediction` rows for the user and a non-reversible phone
  hash on `app_user`.
- **SC-005**: A geocode-cache hit returns a `GeocodeResult` whose candidate list
  **equals** the stored one across a fresh persistence context; a second `put`
  for the same `query_norm` updates in place (one row). (The "no geocoder call
  on a hit" guarantee is SPEC-007's `CachingGeocoder`, already tested — a
  `GeocodeCache` has no geocoder to call.)
- **SC-006**: The Flyway migrations apply to an empty `mysql:8.4` and, on a
  second run, report no pending migrations and pass checksum validation; CI runs
  the persistence suite against the container.
- **SC-007**: Recording the same `wa_message_id` twice yields one `inbound_event`
  row; a claimed event is not re-claimed by a concurrent worker.

## Assumptions

- **MySQL 8.4 via Testcontainers** for tests (ADR-0008, to be **accepted** in
  `/speckit-plan`); a developer runs Colima (or the documented local-MySQL
  fallback). CI uses the container on `ubuntu-latest`.
- **The billing / payment / guardrail tables are out of scope** — `plan`,
  `subscription`, `payment`, `payment_event`, `usage_counter`, `guardrail_event`
  land with SPEC-011 / 012 / 014. `conversation` and `message` land with
  SPEC-009 (the channel). SPEC-008 lays identity, birth data, the engine cache,
  the geocode cache, and the async inbox/outbox.
- **Encryption at rest** for PII columns is SPEC-016 ("PII encryption");
  SPEC-008 stores the data in a structure that a later column-encryption pass
  can wrap. The **hard-delete** operation is in scope now (ADR-0016).
- **The chart cache is lazy** — recompute happens on the next read after an
  engine-version change; there is no background re-computation sweep in v1 (that
  is SPEC-016 ops).
- **JPA lives in `agent`** — the constitution's pure-module list has no
  `persistence` module, and Spring already lives in `agent`. No constitution
  amendment; domain modules expose ports.
- **`EngineVersion`** already exists in `ephemeris`; SPEC-008 assembles the live
  instance and compares its `id()` string.
- **Multi-instance** concerns (ShedLock for the worker) are noted by ADR-0015 but
  the worker itself is SPEC-009; SPEC-008's claim primitive uses a
  `SELECT … FOR UPDATE SKIP LOCKED` or a status-CAS that is correct for one
  instance and extensible to many.

## Dependencies

- **SPEC-001–006** — the KP engine and `EngineVersion` (the values cached in
  `chart` / `prediction`).
- **SPEC-007** — `ResolvedBirth`, `BirthData`, and the `GeocodeCache` /
  `Geocoder` ports (`geo`).
- **ADRs** — ADR-0008 (accept in planning), ADR-0015 (async inbox/outbox),
  ADR-0016 (consent + "forget me"). Constitution II (pure modules), VII
  (PII/consent), and the Technology Constraints (MySQL 8.x, Flyway, minor units).
- **Consumed by** SPEC-009 (channel — inbox/outbox), SPEC-010 (onboarding —
  `app_user` + `birth_data`), SPEC-011/012 (billing tables build on this
  layer), SPEC-014 (`guardrail_event`), SPEC-015 (Q&A reads birth data + chart
  cache).
