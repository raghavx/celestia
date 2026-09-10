---
description: "Task list for SPEC-008 — Persistence & Domain Services"
---

# Tasks: Persistence & Domain Services (SPEC-008)

**Input**: Design documents in `specs/008-persistence-domain-services/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — every store service has an integration test against real
MySQL (SC-001…SC-007); the codecs and `CurrentEngineVersion` have unit tests.

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US3.

Everything lands in the `agent` module (`com.celestia.agent.persistence` +
`db/migration`). No pure-module change. New deps: `spring-boot-starter-data-jpa`,
`mysql-connector-j`, `flyway-core`, `flyway-mysql`, test Testcontainers. ADR-0008
accepted by the plan.

**Container runtime note**: persistence ITs need Docker/Podman/local-MySQL
(quickstart.md). Codec / `CurrentEngineVersion` / ArchUnit tests run everywhere.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [ ] T001 `agent/pom.xml` — add `org.springframework.boot:spring-boot-starter-data-jpa`,
  `com.mysql:mysql-connector-j`, `org.flywaydb:flyway-core`,
  `org.flywaydb:flyway-mysql`; test scope `org.springframework.boot:spring-boot-testcontainers`,
  `org.testcontainers:mysql`, `org.testcontainers:junit-jupiter` (versions from
  the Spring Boot BOM); drop the "Phase 3" reservation comment.
- [ ] T002 `agent/src/main/resources/application.yml` — `spring.datasource`
  (url/username/password from env: `DB_URL` / `DB_USER` / `DB_PASSWORD`, sane
  local defaults), `spring.jpa.hibernate.ddl-auto: validate`,
  `spring.jpa.open-in-view: false`, `spring.flyway.enabled: true`. Secrets never
  literal.
- [ ] T003 `agent/src/main/resources/db/migration/V1__identity.sql` — `app_user`
  (data-model.md): `phone_e164` UNIQUE, `onboarding_state` default `NEW`,
  `guardrail_strikes` default 0, timestamps; `utf8mb4_0900_ai_ci`.
- [ ] T004 `db/migration/V2__birth_data.sql` — `birth_data`: `user_id` UNIQUE +
  FK → `app_user` `ON DELETE CASCADE`; all place/time columns; `birth_utc
  TIMESTAMP(0)`; `lat`/`lon` `DECIMAL(9,6)`.
- [ ] T005 `db/migration/V3__chart_cache.sql` — `chart` (UNIQUE `(user_id, kind)`,
  `computed_json JSON`, `engine_version VARCHAR(128)`), `dasha_period` (FK →
  `chart` cascade, self-FK `parent_id`), `horary_query` (FK → `chart` cascade,
  `number_1_249` 1..249).
- [ ] T006 `db/migration/V4__prediction_cache.sql` — `prediction`: UNIQUE
  `(user_id, for_date, kind)`, `payload_json JSON`, `engine_version`, FK →
  `app_user` cascade.
- [ ] T007 `db/migration/V5__geocode_cache.sql` — `geocode_cache`: `query_norm`
  UNIQUE, `results_json JSON`, timestamps.
- [ ] T008 `db/migration/V6__async_io.sql` — `inbound_event` (`wa_message_id`
  UNIQUE, `status` default `NEW`, `attempts`, index `(status, received_at)`),
  `outbox_message` (`status` default `PENDING`, `attempts`, `error`, index
  `(status, created_at)`).
- [ ] T009 [P] `agent/src/test/java/com/celestia/agent/persistence/PersistenceIT.java`
  — abstract base: `@SpringBootTest` (or `@DataJpaTest` + `@AutoConfigure…`),
  `@Testcontainers`, `static @ServiceConnection MySQLContainer<>("mysql:8.4")`
  with `withReuse(true)`, one container per hierarchy.
- [ ] T010 [P] `agent/src/main/java/com/celestia/agent/persistence/json/PersistenceJson.java`
  — a shared `ObjectMapper`: `JavaTimeModule`, dates as ISO strings, a
  `BigFraction` serialiser/deserialiser (`"num/den"`), fail on unknown enum;
  records native.

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [ ] T011 [P] `persistence/entity/AppUserEntity.java` +
  `BirthDataEntity.java` — `@Entity`, column mapping per data-model.md;
  package-private setters where possible; no domain logic.
- [ ] T012 [P] `persistence/entity/` — `ChartEntity`, `DashaPeriodEntity`,
  `HoraryQueryEntity`, `PredictionEntity` (`@Entity`, FKs, `@Column
  columnDefinition = "json"` for the JSON columns via a `String` field or an
  `AttributeConverter`).
- [ ] T013 [P] `persistence/entity/` — `GeocodeCacheEntity`,
  `InboundEventEntity`, `OutboxMessageEntity`.
- [ ] T014 [P] `persistence/repository/` — Spring Data JPA interfaces:
  `AppUserRepository` (`findByPhoneE164`), `BirthDataRepository`
  (`findByUserId`), `ChartRepository` (`findByUserIdAndKind`),
  `DashaPeriodRepository`, `HoraryQueryRepository`, `PredictionRepository`
  (`findByUserIdAndForDateAndKind`), `GeocodeCacheRepository`
  (`findByQueryNorm`), `InboundEventRepository`, `OutboxMessageRepository`.
- [ ] T015 [P] `persistence/EngineConfig.java` — `@Configuration` providing
  `SwissEphemerisConfig` (from `application.yml` props / defaults),
  `PositionProvider`, `HouseProvider`, `HoraryHouseProvider`, `SunriseProvider`,
  and the `core` factories (`NatalChartFactory`, `HoraryChartFactory`,
  `DashaTimelineFactory`, `DailyPredictionFactory`, `RulingPlanetsFactory`) as
  beans.
- [ ] T016 [P] `persistence/CurrentEngineVersion.java` + `CurrentEngineVersionTest.java`
  (unit, **no container**) — `forBirth(BirthData)` / `id(BirthData)`:
  `EngineVersion.RULES` + `TimeScales.DELTA_T_MODEL` + config `sePort` +
  `swieph:<id>` / `moseph` predicted from the birth year vs the `.se1` range;
  test both accuracy branches and a `RULES`-bump flip.
- [ ] T017 [P] `persistence/json/` — `ChartCodec`, `PredictionCodec`,
  `GeocodeResultCodec` (each `write(domain) -> String`, `read(String) ->
  Optional<domain>`; a `"schema"` int; **any exception on read → `Optional.empty()`**)
  + `…CodecTest` (unit, no container) — round-trip a fixture aggregate; a
  corrupted / unknown-schema payload → empty.
- [ ] T018 [P] `agent/src/test/java/com/celestia/agent/architecture/PersistenceArchitectureTest.java`
  — `noClasses().that().resideInAnyPackage("com.celestia.ephemeris..",
  "com.celestia.core..", "com.celestia.geo..", "com.celestia.billing..",
  "com.celestia.guardrail..").should().dependOnClassesThat().resideInAnyPackage(
  "jakarta.persistence..", "org.springframework.data..", "org.hibernate..",
  "org.flywaydb..", "java.sql..", "javax.sql..", "com.zaxxer.hikari..")`.
- [ ] T019 `agent/src/test/java/com/celestia/agent/persistence/FlywayMigrationIT.java`
  (extends `PersistenceIT`) — the context starts (migrations applied); assert
  every in-scope table exists with its unique keys; a second `flyway.migrate()`
  reports no pending; `flyway.validate()` passes.

**Checkpoint**: `./mvnw -pl agent -am test` — unit tests green; ITs green where a
runtime is available.

---

## Phase 3: User Story 1 — Persist & reload birth data (P1) 🎯 MVP

**Goal**: a `ResolvedBirth` + name + place query → the one `birth_data` row for a
user, reloaded losslessly.

### Tests (write first)

- [ ] T020 [P] [US1] `BirthDataStoreIT` (extends `PersistenceIT`) — for a set of
  SPEC-007-corpus births: `save` then `load`; assert `birth_utc` reloads to the
  exact `Instant`, `lat`/`lon`/`zone_id`/`birth_time_known`/flags identical, and
  `load(...).birthData()` equals the original `resolved.birthData()`; a second
  `save` for the same user replaces the row (still one row).

### Implementation

- [ ] T021 [US1] `persistence/store/BirthDataStore.java` + `StoredBirth` (a
  carrier exposing the `ResolvedBirth`-equivalent fields + `birthData()`) —
  `save(long userId, String name, String placeQuery, ResolvedBirth)` upserts by
  `user_id`; `load(long userId)` maps the entity back. Flag list ↔
  `EnumSet<BirthMomentFlag>`.

**Checkpoint**: onboarding (SPEC-010) can persist a birth; Q&A (SPEC-015) can read it.

---

## Phase 4: User Story 2 — Engine-versioned chart & prediction cache (P1)

**Goal**: serve a stored chart/prediction only on an `engine_version` match; else
recompute + overwrite. Constitution III.

### Tests (write first)

- [ ] T022 [P] [US2] `agent/src/test/java/com/celestia/agent/persistence/CountingEngine.java`
  — decorators over `NatalChartFactory` / `HoraryChartFactory` /
  `DailyPredictionFactory` that count real computations; a `@TestConfiguration`
  swaps them in.
- [ ] T023 [P] [US2] `ChartCacheServiceIT` (extends `PersistenceIT`) — cold miss
  → exactly one computation, row stored with the current `engine_version`; warm
  hit → **zero** computations; a row pre-seeded with a stale `engine_version` →
  one computation, row overwritten, old JSON gone; a corrupt `computed_json` →
  recompute.
- [ ] T024 [P] [US2] `PredictionCacheServiceIT` — same, keyed on
  `(user, for_date)`; changing the date recomputes; a stale `engine_version`
  recomputes.

### Implementation

- [ ] T025 [US2] `persistence/store/ChartCacheService.java` + `NatalChartBundle`
  / `HoraryChartBundle` — `natalChart(long userId)`: load `birth_data`,
  `expected = currentEngineVersion.id(birthData)`, serve the `chart` row on
  match + deserialisable, else compute via `EngineConfig` beans
  (`NatalChartFactory` + `SignificatorTable.of` + `DashaTimeline.from` +
  `RulingPlanetsFactory`), `@Transactional` upsert of `chart` + replace
  `dasha_period` children, return fresh. `horaryChart(userId, number)` for
  `kind = HORARY` + a `horary_query` row. Unique-key race → catch + re-read.
- [ ] T026 [US2] `persistence/store/PredictionCacheService.java` —
  `dailyPrediction(long userId, LocalDate date)`: keyed `(user_id, for_date,
  DAILY)`; serve-or-recompute via `DailyPredictionFactory`; `@Transactional`
  upsert.

**Checkpoint**: SC-002 green; a `RULES` bump self-heals the cache on read.

---

## Phase 5: User Story 3 — Identity store (P1)

**Goal**: find-or-create by phone (race-safe), consent write-once, "forget me".

### Tests (write first)

- [ ] T027 [P] [US3] `UserAccountServiceIT` (extends `PersistenceIT`) — 20
  concurrent `findOrCreate` for one `phone_e164` → one row, no caller sees an
  exception (SC-003); `recordConsent` sets `consent_at` once and does not
  overwrite; after `save`-ing birth data + a chart + a prediction, `forget` →
  zero `birth_data`/`chart`/`dasha_period`/`horary_query`/`prediction` rows,
  `phone_e164` = `deleted:<hash>`, `deleted_at` set (SC-004).

### Implementation

- [ ] T028 [US3] `persistence/store/UserAccountService.java` + `AppUserRef` —
  `findOrCreate` (findByPhone → save → catch `DataIntegrityViolationException` →
  re-read); `recordConsent` (`UPDATE … WHERE consent_at IS NULL`); `forget`
  (`@Transactional`: delete PII rows via the repositories, then anonymise
  `app_user` with `SHA2(phone_e164, 256)`).

**Checkpoint**: SPEC-009/010 have an identity anchor; ADR-0016 delete op exists.

---

## Phase 6: User Story 4 — Persistent geocode cache (P2)

### Tests (write first)

- [ ] T029 [P] [US4] `JpaGeocodeCacheIT` (extends `PersistenceIT`) — `put` a
  `GeocodeResult`, `get` it in a fresh persistence context → candidate list
  equal (SC-005); `get` on an absent key → empty; a second `put` for the same
  `query_norm` updates in place (one row); a corrupt `results_json` → empty.

### Implementation

- [ ] T030 [US4] `persistence/store/JpaGeocodeCache.java` **implements
  `com.celestia.geo.place.GeocodeCache`** — `get`/`put` against
  `geocode_cache` via `GeocodeResultCodec`; `put` upserts on `query_norm`.

**Checkpoint**: SPEC-007's `CachingGeocoder` can be given a persistent cache.

---

## Phase 7: User Story 5 — Idempotent async inbox & outbox (P2)

### Tests (write first)

- [ ] T031 [P] [US5] `InboxStoreIT` + `OutboxStoreIT` (extend `PersistenceIT`) —
  `recordInbound` twice with one `wa_message_id` → one row, second is
  `Duplicate` (SC-007); `claimNext` marks the row so a second concurrent
  `claimNext` returns a different row or empty; `markProcessed` sets
  `processed_at`; outbox `enqueue` → `claimNextPending` → `markFailed` records
  `error` and `attempts`.

### Implementation

- [ ] T032 [US5] `persistence/store/InboxStore.java` (+ `RecordResult` sealed
  interface, `ClaimedEvent`) and `OutboxStore.java` (+ `ClaimedMessage`) —
  idempotent insert on the unique key; `claimNext` via a native
  `SELECT … FOR UPDATE SKIP LOCKED` + status update; `attempts` incremented at
  claim.

**Checkpoint**: SPEC-009's webhook + worker have their store primitives.

---

## Phase 8: User Story 6 — Migrations & real-MySQL harness (P2)

- [ ] T033 [US6] Confirm `FlywayMigrationIT` (T019) asserts: all tables + unique
  keys present, `utf8mb4` collation, second migrate is a no-op, `validate`
  passes. Add `spring.flyway.validate-on-migrate: true`.
- [ ] T034 [US6] `.github/workflows/ci.yml` — confirm the persistence suite runs
  in `verify` on `ubuntu-latest` (Docker present); add a short note; if helpful,
  a dedicated `flyway validate` step is optional (the IT already covers it).

**Checkpoint**: the schema is a CI gate against real MySQL.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T035 [P] `docs/persistence.md` (or `agent/README.md`) — the `store` API
  surface, running the ITs (Colima / Podman `DOCKER_HOST` / local MySQL 8.4),
  the "immutable migrations" rule, the versioned-cache behaviour.
- [ ] T036 [P] `agent/src/main/resources/db/migration/README.md` — the V1..V6
  layout + FR-018 (never edit an applied migration).
- [ ] T037 Run `specs/008-persistence-domain-services/quickstart.md` end to end
  (with a runtime if available); fix drift.
- [ ] T038 `./mvnw -q verify` — full reactor green incl. `PersistenceArchitectureTest`,
  `LayeringArchitectureTest`, `DeterminismArchitectureTest`, the golden-chart
  suite, Spotless. Note which ITs were container-verified vs CI-only.

---

## Dependencies & Execution Order

1. **Setup (Phase 1)** — T001 (deps) → T002 (config); T003→T004→T005→T006→T007→T008
   (migrations, ordered); T009 / T010 parallel with the migrations.
2. **Foundational (Phase 2)** — T011/T012/T013 (entities) after the matching
   migrations; T014 (repos) after entities; T015 / T016 / T017 / T018 parallel;
   T019 after all migrations + T009.
3. **US1 (Phase 3)** — T020 → T021; needs entities + repos + `PersistenceIT`.
4. **US2 (Phase 4)** — T022 (counting engine) + T023/T024 (tests) → T025 / T026;
   needs US1 (`BirthDataStore`) + `EngineConfig` + `CurrentEngineVersion` + codecs.
5. **US3 (Phase 5)** — T027 → T028; needs entities + repos; the `forget` test
   uses US1/US2 to seed rows.
6. **US4 (Phase 6)** — T029 → T030; needs `GeocodeResultCodec` + the `geo` port.
7. **US5 (Phase 7)** — T031 → T032; needs entities + repos.
8. **US6 (Phase 8)** — T033/T034 finalise the harness T019 started.
9. **Polish (Phase 9)** — after the stories you ship.

### Parallel opportunities

- Migrations T003–T008 are quick and sequential; T009 / T010 alongside.
- Foundational: T011 / T012 / T013 parallel; T015 / T016 / T017 / T018 parallel.
- **US4 and US5 whole phases run in parallel** with each other and can start
  once Foundational is done (they don't need US1–US3).
- US2 tests T023 / T024 parallel; impl T025 / T026 parallel.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US3. Delivers: persist a birth,
the engine-versioned chart/prediction cache, and the identity store — the three
things SPEC-009 (channel) and SPEC-010 (onboarding) need first. Stop, run
`quickstart.md`, open the PR.

**Container-runtime note**: if no Docker/Podman/MySQL is available in the dev
environment, land the ITs (they are the spec's proof) and verify them in CI;
locally verify T010/T016/T017/T018 (codecs, version, ArchUnit) + `./mvnw verify`
minus the ITs. State this plainly in the completion report.

---

## Notes

- No pure-module change. New deps and all code are in `agent`.
- The `store` package is the layer's only public API (contracts/persistence-api.md);
  entities / repositories are internal (FR-014/15) — `PersistenceArchitectureTest`
  proves the pure modules stay clean.
- The versioned cache **recomputes when in doubt** (missing row, version
  mismatch, JSON deserialise failure) — a bad hit is worse than a wasted
  computation (Constitution III).
- Billing / payment / guardrail tables and `conversation` / `message` are **not**
  in this spec — SPEC-009 / 011 / 012 / 014 own them.
- Commit per task or tight group; keep `master` releasable (work on `008-…`).
