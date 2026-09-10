# Implementation Plan: Persistence & Domain Services (SPEC-008)

**Branch**: `008-persistence-domain-services` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/008-persistence-domain-services/spec.md`

## Summary

The database and the thin service layer over it, all in the `agent` module. No
pure module changes; the domain stays clock-free and framework-free.

- **Schema** — Flyway migrations (`agent/src/main/resources/db/migration`) for
  `app_user`, `birth_data`, `chart`, `dasha_period`, `horary_query`,
  `prediction`, `geocode_cache`, `inbound_event`, `outbox_message`.
- **JPA layer** — `com.celestia.agent.persistence` — entities (internal),
  Spring Data repositories, and a `store` package of domain services that the
  rest of the system calls: `UserAccountService`, `BirthDataStore`,
  `ChartCacheService`, `PredictionCacheService`, `JpaGeocodeCache` (implements
  `com.celestia.geo.place.GeocodeCache`), `InboxStore`, `OutboxStore`.
- **Engine wiring** — an `EngineConfig` `@Configuration` that provides the
  Swiss-Ephemeris providers and the `core` factories as beans (the first time
  the engine meets Spring), plus `CurrentEngineVersion` — the single source of
  the live `EngineVersion` string.
- **Engine-versioned cache** — `ChartCacheService` / `PredictionCacheService`
  serve a stored row only when its `engine_version` equals what
  `CurrentEngineVersion` would stamp on a fresh result; otherwise recompute via
  `core`, overwrite, return fresh. Lazy — no background sweep.
- **Test harness** — a Testcontainers `mysql:8.4` base class; every persistence
  test runs against real MySQL (ADR-0008, to be accepted here).

## Technical Context

**Language/Version**: Java 25 LTS. Spring Boot 4.1.1 (Spring Framework 7,
Hibernate ORM 7 / Jakarta Persistence 3.2).

**Primary Dependencies** (added to `agent` only — the pom already reserves the
slot): `spring-boot-starter-data-jpa`, `mysql-connector-j`, `flyway-core`,
`flyway-mysql`; test — `org.testcontainers:mysql` + `:junit-jupiter`
(versions from the Spring Boot BOM). `jackson-databind` (already transitive via
web) for the JSON columns.

**Storage**: MySQL 8.4 LTS, `utf8mb4_0900_ai_ci`, HikariCP, Flyway. JSON columns
for `chart.computed_json`, `prediction.payload_json`, `geocode_cache.results_json`.

**Testing**: `./mvnw verify`. Persistence tests use Testcontainers `mysql:8.4`
(`@ServiceConnection`, one container per suite, reuse enabled). Unit-level tests
(mappers, `CurrentEngineVersion`, JSON round-trips) need no container. `.se1`
data is **not** required — charts compute as `REDUCED` and the cache logic is
accuracy-agnostic.

**Target Platform**: JVM service. The dev machine has **Podman** (no Docker);
Testcontainers drives Podman via `DOCKER_HOST`/socket config, else the ADR-0008
local-MySQL fallback. CI (`ubuntu-latest`) has Docker.

**Project Type**: Spring Boot application module — new package
`com.celestia.agent.persistence` (+ `db/migration` resources). No new Maven
module.

**Performance Goals**: a warm chart-cache hit is one indexed `SELECT` + one JSON
deserialise (sub-millisecond, no engine call — SC-002). A cold miss is one
`core` computation (ephemeris-bound, tens of ms) + one `INSERT`.

**Constraints**:
- Constitution II — the pure modules (`ephemeris`, `core`, `geo`, `billing`,
  `guardrail`) gain **no** dependency on JPA / Spring Data / a JDBC driver /
  Flyway. A new ArchUnit rule enforces it. Domain storage needs use a port
  (`geo.GeocodeCache`); the JPA class is the adapter (FR-014/FR-015).
- Constitution III — the versioned cache (FR-005/006, SC-002) is this principle
  made concrete: a stale `engine_version` is never served.
- Constitution VII — `birth_data` is PII; `consent_at` recorded once; "forget
  me" hard-deletes PII rows now (FR-010). Column encryption at rest is SPEC-016.
- FR-001/018 — all DDL via immutable Flyway migrations; `ddl-auto: validate`;
  `flyway validate` in CI.
- FR-016 — multi-table writes (chart + `horary_query`; "forget me" across five
  tables) are transactional.

**Scale/Scope**: 9 tables; ~7 services; one `birth_data` + one `chart` +
one `prediction`-per-date per user; the geocode cache grows with distinct
queries; the inbox/outbox are transient.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code |
| II Deterministic pure core | **yes, central** | PASS — JPA lands only in `com.celestia.agent.persistence`; a new ArchUnit rule bans `jakarta.persistence` / `org.springframework.data` / `org.flywaydb` / JDBC from the pure packages; `geo.GeocodeCache` port unchanged, adapter in `agent` |
| III Calculation Correctness Is Gated | **yes, central** | PASS — `ChartCacheService` / `PredictionCacheService` recompute on any `engine_version` mismatch and overwrite; SC-002 verifies with a counting engine; `ChartCacheGoldenIT` ties a cold cache-compute to a golden-chart fixture so the cache path shares the golden gate (`EngineConfig` wires the golden suite's `SwissEphemerisConfig` defaults); the golden suite itself is untouched |
| IV LLM interprets, never calculates | no | N/A — no LLM in this spec |
| V Astrology-only guarded path | no | N/A — no chat path yet |
| VI Deterministic conversation flows | partial | PASS — `app_user.onboarding_state` is a column; the FSM is SPEC-010 |
| VII Identity / consent / PII | **yes, central** | PASS — `phone_e164` identity, `consent_at` write-once, "forget me" hard-delete of `birth_data`/`chart`/`dasha_period`/`horary_query`/`prediction` + `app_user` anonymised to a phone hash (ADR-0016). Encryption-at-rest is SPEC-016, noted |
| VIII Payments from the gateway | no | N/A — billing tables deferred to SPEC-011/012 |
| IX Adapters at the edge | partial | PASS — `JpaGeocodeCache` is the adapter for `geo`'s port; entities never cross a pure-module boundary (FR-015) |
| Tech constraints (Java 25, Maven, MySQL, Flyway, minor units) | **yes** | PASS — MySQL 8.4, Flyway, `ddl-auto: validate`; FR-019 records the minor-units rule for the future billing migration; Spring only in `agent` |
| ADR gate (D1–D23) | **yes** | PASS — **this plan moves ADR-0008 (D8) `proposed → accepted`** (Testcontainers `mysql:8.4`, dated 2026-09-11). ADR-0015 (D15) and ADR-0016 (D16) already accepted and drive US5 / US3. No new ADR — the persistence architecture is constitution-determined; the lazy-cache strategy is a design choice in `research.md` |

**Result: PASS.** New deps confined to `agent`; one new ArchUnit rule; one ADR
promoted; no new module, no new ADR.

## Project Structure

### Documentation (this feature)

```text
specs/008-persistence-domain-services/
├── plan.md
├── research.md          # module & layering; the versioned-cache strategy; chart-JSON contract; identity idempotency; async claim; Flyway + Testcontainers/Podman
├── data-model.md         # the 9 tables (columns/keys/FKs), entity <-> domain mappings, service contracts, invariants
├── contracts/
│   └── persistence-api.md   # the store package — what SPEC-009..015 call
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
agent/pom.xml                          # + data-jpa, mysql-connector-j, flyway-core, flyway-mysql; test: testcontainers mysql + junit-jupiter
agent/src/main/resources/application.yml  # datasource (env), jpa.hibernate.ddl-auto=validate, flyway on
agent/src/main/resources/db/migration/
├── V1__identity.sql                   # app_user
├── V2__birth_data.sql
├── V3__chart_cache.sql                # chart, horary_query, dasha_period (table only — SPEC-013 populates)
├── V4__prediction_cache.sql           # prediction
├── V5__geocode_cache.sql
└── V6__async_io.sql                   # inbound_event, outbox_message

agent/src/main/java/com/celestia/agent/persistence/
├── entity/            AppUserEntity, BirthDataEntity, ChartEntity, DashaPeriodEntity,
│                      HoraryQueryEntity, PredictionEntity, GeocodeCacheEntity,
│                      InboundEventEntity, OutboxMessageEntity        (package-private-ish; never exposed)
├── repository/        AppUserRepository, BirthDataRepository, ChartRepository, DashaPeriodRepository,
│                      HoraryQueryRepository, PredictionRepository, GeocodeCacheRepository,
│                      InboundEventRepository, OutboxMessageRepository (Spring Data JPA)
├── store/
│   ├── UserAccountService.java        # findOrCreate(phoneE164), recordConsent, forget(userId)
│   ├── BirthDataStore.java            # save(userId, name, placeQuery, ResolvedBirth), load(userId)
│   ├── ChartCacheService.java         # natalChart(userId), horaryChart(userId, number) — versioned
│   ├── PredictionCacheService.java    # dailyPrediction(userId, LocalDate) — versioned
│   ├── JpaGeocodeCache.java           # implements com.celestia.geo.place.GeocodeCache
│   ├── InboxStore.java                # recordInbound(waMessageId, rawRef) -> Recorded|Duplicate; claimNext()
│   └── OutboxStore.java               # enqueue(...), markSent/markFailed(id, error)
├── json/
│   ├── PersistenceJson.java           # ObjectMapper: JavaTime + BigFraction-as-string + records; a `schema` tag
│   ├── ChartCodec.java                # NatalChart <-> computed_json  (deserialise failure -> Optional.empty -> miss)
│   └── PredictionCodec.java / GeocodeResultCodec.java
├── EngineConfig.java                  # @Configuration: SwissEphemeris{Position,House,Horary,Sunrise}Provider,
│                                      #   NatalChartFactory, HoraryChartFactory, DashaTimelineFactory,
│                                      #   DailyPredictionFactory, RulingPlanetsFactory; from SwissEphemerisConfig
└── CurrentEngineVersion.java          # the live EngineVersion.id() for a given BirthData (accuracy-aware)

agent/src/test/java/com/celestia/agent/persistence/
├── PersistenceTest.java               # @DataJpaТest-style base + Testcontainers mysql:8.4 (@ServiceConnection)
├── ...IT.java                         # per-service integration tests
└── json/...Test.java                  # codec round-trips (no container)
agent/src/test/java/com/celestia/agent/architecture/PersistenceArchitectureTest.java  # the no-JPA-in-pure-modules rule
.github/workflows/ci.yml               # persistence suite already covered by `verify`; note Docker present on ubuntu-latest
docs/adr/0008-test-database.md         # proposed -> accepted
```

**Structure Decision**: no new module. The constitution's pure-module list has no
`persistence` module and Spring already lives in `agent`, so JPA goes in
`com.celestia.agent.persistence`. The `store` package is the layer's only public
surface; entities and repositories are implementation detail. Domain modules that
need storage keep a **port** (only `geo.GeocodeCache` today); `agent` supplies the
adapter. `EngineConfig` is where the pure engine is first wired into Spring —
legitimately a "domain service" concern since `ChartCacheService` must call the
`core` factories.

## Phase 0 — Research

See [research.md](./research.md). Items:

1. Module & layering — JPA in `agent.persistence`; the `store` façade; entities
   never exposed; the new ArchUnit rule (no `jakarta.persistence` /
   `org.springframework.data` / `org.flywaydb` / `java.sql` in the pure packages).
2. The engine-versioned cache — `CurrentEngineVersion` (RULES + ΔT model as
   constants; SE port from config; `swieph`/`moseph` predicted from the birth
   year vs the configured range); serve only on an exact match, **recompute when
   in doubt**; lazy (no sweep); the cold-cache race (unique key on the cache row
   + insert-or-read, last-write-wins with an identical value); `@Transactional`
   boundaries.
3. Chart / prediction JSON — the `computed_json` contract: a `schema` tag,
   `BigFraction` as a canonical string, `java.time` via the JSR-310 module;
   deserialise failure ⇒ treat as a cache miss and recompute (a structural
   change that didn't bump `engine_version` is thus self-healing).
4. Identity idempotency — `phone_e164` unique; `findOrCreate` catches
   `DataIntegrityViolationException` and re-reads; the anonymisation phone hash
   (SHA-256 of the E.164; an app pepper is a SPEC-016 hardening).
5. `geocode_cache` — `results_json` = the `GeocodeResult` candidate list;
   `JpaGeocodeCache.get/put` against `query_norm`; `put` upserts.
6. Async inbox/outbox — `inbound_event` unique `wa_message_id`, `recordInbound`
   returns `Recorded` or `Duplicate`; `claimNext` via
   `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL 8) or a status CAS; `outbox_message`
   `attempts` / `error`. The worker loop is SPEC-009.
7. Flyway + Testcontainers — grouped `V1..V6`; `utf8mb4_0900_ai_ci`;
   `ddl-auto: validate`; `flyway validate` fails a build on an edited applied
   migration; `mysql:8.4` container, `@ServiceConnection`, reuse; Podman-as-Docker
   config for this dev box; the local-MySQL fallback (ADR-0008).
8. Test fixtures — a `birth_data` fixture set from the SPEC-007 corpus; a
   counting/spy engine (a `NatalChartFactory` decorator) for SC-002.

**Output**: research.md; ADR-0008 promoted to accepted.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — the 9 tables and the entity↔domain mappings.
- [contracts/persistence-api.md](./contracts/persistence-api.md) — the `store` package.
- [quickstart.md](./quickstart.md).

### Post-design Constitution re-check

Unchanged — JPA is confined to `agent.persistence`; the pure modules are
untouched and the new ArchUnit rule proves it; the versioned cache upholds
principle III; PII handling (consent, hard-delete) upholds principle VII for
what is in scope. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty. (The engine is wired into
Spring for the first time here; this is necessary — `ChartCacheService` cannot
compute a chart without the `core` factories — and is contained in one
`@Configuration`, not a violation.)
