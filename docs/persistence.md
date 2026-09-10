# Persistence layer (SPEC-008)

MySQL 8.4 + Spring Data JPA + Flyway, all inside the `agent` module
(`com.celestia.agent.persistence`). The pure domain modules gain **no** JPA/JDBC
dependency (`PersistenceArchitectureTest` enforces it); where a domain module
needs storage it exposes a port and `agent` supplies the adapter
(`geo.GeocodeCache` → `JpaGeocodeCache`).

## The `store` package — the layer's only public API

| Service | Purpose |
|---|---|
| `UserAccountService` | `findOrCreate(phoneE164)` (race-safe), `recordConsent` (write-once), `forget(userId)` (hard-delete PII + SHA-256 phone hash — ADR-0016) |
| `BirthDataStore` | `save(userId, name, placeQuery, ResolvedBirth)` / `load(userId)` — lossless round-trip of the SPEC-007 resolved birth |
| `ChartCacheService` | `natalChart(userId)` → `NatalChartBundle` — served from `chart` only when `engine_version` matches `CurrentEngineVersion`, else recomputed and overwritten (Constitution III) |
| `PredictionCacheService` | `dailyPrediction(userId, date)` — same rule, keyed `(user, for_date, DAILY)` |
| `JpaGeocodeCache` | implements `com.celestia.geo.place.GeocodeCache` over `geocode_cache` |
| `InboxStore` / `OutboxStore` | idempotent inbound-event record + `FOR UPDATE SKIP LOCKED` claim; outbox with attempt/error tracking (worker is SPEC-009) |

`entity` and `repository` are internal — never imported outside the package.

## Engine versioning

`CurrentEngineVersion.id(BirthData)` = `EngineVersion.RULES` + the ΔT model +
the SE port version + `swieph`/`moseph` (predicted from the birth year vs the
`.se1` range). A change to `EngineVersion.RULES` (a KP rule change) makes every
stored `engine_version` mismatch, so the cache self-heals on the next read per
user — **lazy**, no background sweep, no migration.

`ChartCacheGoldenIT` pins the cache path to the Constitution III golden gate:
`EngineConfig` wires the same `SwissEphemerisConfig.resolve()` the golden suite
uses, so a cold cache-compute equals a direct `NatalChartFactory.cast`.

## Migrations

`agent/src/main/resources/db/migration/V1..V6` — see the
[README there](../agent/src/main/resources/db/migration/README.md). `ddl-auto:
validate`; `flyway validate` runs on startup and in `FlywayMigrationIT`. **Never
edit an applied migration** — add a new `V*`.

## Running the tests

Unit tests (`*Test` — codecs, `CurrentEngineVersion`, ArchUnit) need nothing:

```bash
./mvnw -pl agent -am test
```

Integration tests (`*IT`) use Testcontainers `mysql:8.4` and run in the `verify`
phase. They need a container runtime:

- **Colima**: `brew install colima && colima start`
- **Podman** (already on this box): `podman machine start`; it forwards to
  `/var/run/docker.sock`, so Testcontainers finds it. Set
  `TESTCONTAINERS_RYUK_DISABLED=true` for the rootless socket.
- **Local MySQL 8.4**: point `DB_URL` / `DB_USER` / `DB_PASSWORD` at it and run
  the ITs against that instead (the ADR-0008 fallback).

```bash
./mvnw -pl agent -am verify            # unit + IT
./mvnw -pl agent -am verify -DskipITs  # unit only
```

CI runs the full `verify` on `ubuntu-latest` (Docker present) and skips `*IT` on
the macOS runner (no Docker; the persistence layer is not OS-sensitive).

## Not in this spec

`plan` / `subscription` / `payment*` / `usage_counter` (SPEC-011/012),
`guardrail_event` (SPEC-014), `conversation` / `message` (SPEC-009),
`dasha_period` population (SPEC-013 — the table exists here, empty), column
encryption at rest (SPEC-016), the async worker loop (SPEC-009).
