# Quickstart: verifying SPEC-008

The first stateful spec. Builds on SPEC-001–007 (all merged). Branch
`008-persistence-domain-services`.

## Prerequisites

- JDK 25, `./mvnw`.
- A container runtime for the persistence tests:
  - **Colima** (`brew install colima && colima start`) — the ADR-0008 default, or
  - **Podman** (already on this box at `/opt/podman/bin/podman`):
    `podman machine init && podman machine start`, then
    `export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"`
    and `export TESTCONTAINERS_RYUK_DISABLED=true`, or
  - a local **MySQL 8.4** (`spring.datasource.url` pointing at it) — the
    ADR-0008 fallback.
- No `.se1` ephemeris data needed — charts compute as `REDUCED`; the cache logic
  is accuracy-agnostic.

## Build & test

```bash
./mvnw -q -pl agent -am verify
```

New coverage:

- `agent.persistence.json` — `ChartCodec` / `PredictionCodec` /
  `GeocodeResultCodec` round-trip a domain aggregate through JSON; a corrupted
  payload deserialises to `Optional.empty()`. **No container.**
- `agent.persistence.CurrentEngineVersion` — `swieph` vs `moseph` by birth year;
  a `RULES` change flips the id. **No container.**
- `agent.persistence.store` (Testcontainers `mysql:8.4`):
  - `BirthDataStore` — save/reload a SPEC-007-corpus birth; `birthData()` identical.
  - `ChartCacheService` — cold miss computes once (counting engine); warm hit
    computes zero; stale `engine_version` recomputes + overwrites.
  - `PredictionCacheService` — same, keyed on `(user, date)`.
  - `UserAccountService` — 20 concurrent `findOrCreate` → one row; consent
    write-once; `forget` clears every PII row + leaves a phone hash.
  - `JpaGeocodeCache` — put/get across a fresh context; upsert on `query_norm`.
  - `InboxStore` / `OutboxStore` — duplicate `wa_message_id` is a no-op;
    `claimNext` is not double-claimed.
- `agent.persistence` Flyway — `V1..V6` apply to an empty schema, re-runnable,
  checksums validate.
- `PersistenceArchitectureTest` — no `jakarta.persistence` /
  `org.springframework.data` / `org.flywaydb` / `java.sql` in the pure packages.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 birth data round-trips losslessly (SC-001) | `BirthDataStoreIT` |
| US1 one row per user (upsert) | `BirthDataStoreIT` |
| US2 cold miss computes once (SC-002) | `ChartCacheServiceIT` (counting engine) |
| US2 warm hit computes zero (SC-002) | `ChartCacheServiceIT` |
| US2 stale `engine_version` recomputes + overwrites (SC-002) | `ChartCacheServiceIT`, `PredictionCacheServiceIT` |
| US2 the current version string | `CurrentEngineVersionTest` |
| US3 concurrent first-contact → one row (SC-003) | `UserAccountServiceIT` |
| US3 consent write-once | `UserAccountServiceIT` |
| US3 "forget me" removes PII (SC-004) | `UserAccountServiceIT` |
| US4 geocode cache hit equal, no call (SC-005) | `JpaGeocodeCacheIT` |
| US5 duplicate `wa_message_id` no-op (SC-007) | `InboxStoreIT` |
| US5 claim not double-claimed | `InboxStoreIT`, `OutboxStoreIT` |
| US6 migrations apply + re-runnable + validate (SC-006) | `FlywayMigrationIT` |
| US6 tests run against `mysql:8.4` not H2 | the `PersistenceIT` base (Testcontainers) |
| purity (FR-014/15) | `PersistenceArchitectureTest` |
| JSON codecs + failure-as-miss | `agent.persistence.json.*Test` |

## Architecture gates

- **New**: `PersistenceArchitectureTest` — the pure modules gain no
  JPA/JDBC/Flyway/Spring-Data dependency.
- `LayeringArchitectureTest` / `DeterminismArchitectureTest` unchanged and still
  green — `com.celestia.geo..` etc. stay pure; `agent` is where Spring lives.
- `./mvnw -q verify` — full reactor incl. the golden-chart suite (untouched).

## Interpreting a failure

- **`birth_utc` off by a fraction of a second** → `TIMESTAMP(0)` vs the `Instant`;
  the column must truncate to whole seconds and the codec must not re-add nanos.
- **cache hit still computes** → `CurrentEngineVersion.id(birth)` ≠ the stored
  `engine_version`; log both. Likely the `swieph`/`moseph` prediction or the
  `sePort` string.
- **`findOrCreate` throws under load** → the `DataIntegrityViolationException`
  catch-and-refetch is missing or the unique key is not on `phone_e164`.
- **Flyway checksum failure in CI** → someone edited an applied `V*.sql`; revert
  it and add a new `V*` instead (FR-018).
- **Testcontainers can't find Docker** → set `DOCKER_HOST` for Podman (see
  Prerequisites) or use the local-MySQL fallback; in CI this should not happen
  (`ubuntu-latest` has Docker).
