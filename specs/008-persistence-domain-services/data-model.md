# Phase 1 Data Model: Persistence & Domain Services (SPEC-008)

MySQL 8.4, `utf8mb4_0900_ai_ci`. All DDL via Flyway `V1..V6`. Entities are
`com.celestia.agent.persistence.entity`, internal. Bookkeeping columns
(`created_at` / `updated_at` / `consent_at` / …) are `TIMESTAMP` with
`DEFAULT CURRENT_TIMESTAMP`. Columns that hold a **historical** instant
(`birth_utc`, `dasha_period.*_utc`, `judged_utc`) are **`DATETIME(0)`** —
`TIMESTAMP` only spans 1970–2038 and births predate 1970.

## Tables

### `app_user` (V1)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` PK auto | |
| `phone_e164` | `VARCHAR(20)` null | **UNIQUE**; E.164; `NULL` after "forget me" (MySQL allows many NULLs under a UNIQUE key, so re-contact makes a fresh user) |
| `phone_hash` | `CHAR(64)` null | SHA-256 hex of the E.164, retained after "forget me" (ADR-0016; a pepper is SPEC-016) |
| `wa_id` | `VARCHAR(32)` null | WhatsApp id |
| `display_name` | `VARCHAR(120)` null | PII |
| `locale` | `VARCHAR(16)` null | |
| `consent_at` | `TIMESTAMP` null | write-once |
| `onboarding_state` | `VARCHAR(32)` | default `NEW`; FSM values are SPEC-010's |
| `guardrail_strikes` | `INT` | default 0 (SPEC-014 uses it) |
| `cooldown_until` | `TIMESTAMP` null | |
| `created_at` / `deleted_at` | `TIMESTAMP` / null | |

### `birth_data` (V2)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` PK | |
| `user_id` | `BIGINT` | **UNIQUE**, FK → `app_user` `ON DELETE CASCADE` |
| `name` | `VARCHAR(120)` null | PII; caller-supplied |
| `place_query` | `VARCHAR(255)` | caller-supplied raw text |
| `birth_date` | `DATE` | local civil date |
| `birth_time` | `TIME` null | local civil time; null ⇔ `birth_time_known = 0` |
| `birth_time_known` | `TINYINT(1)` | |
| `place_name` | `VARCHAR(255)` | `ResolvedBirth.placeLabel` |
| `country` | `VARCHAR(120)` | |
| `lat` / `lon` | `DECIMAL(9,6)` / `DECIMAL(9,6)` | exact as resolved |
| `zone_id` | `VARCHAR(64)` | IANA id |
| `birth_utc` | `DATETIME(0)` | the `Instant`, second precision |
| `offset_applied` | `VARCHAR(16)` | `ZoneOffset.getId()` |
| `geocode_source` | `VARCHAR(32)` | |
| `stated_zone_id` | `VARCHAR(64)` null | set ⇔ `ZONE_CONFLICT` |
| `flags` | `VARCHAR(255)` | comma-joined `BirthMomentFlag` names, e.g. `"DST_GAP,POLAR_LATITUDE"`; empty string = no flags |
| `created_at` / `updated_at` | `TIMESTAMP` | |

### `chart` (V3)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` PK | |
| `user_id` | `BIGINT` null | FK → `app_user` `ON DELETE CASCADE`; null for a standalone horary |
| `kind` | `VARCHAR(16)` | `NATAL` / `HORARY` |
| `ayanamsa` | `VARCHAR(32)` | e.g. `KRISHNAMURTI` |
| `house_system` | `VARCHAR(16)` | `PLACIDUS` |
| `engine_version` | `VARCHAR(128)` | `EngineVersion.id()` at compute time |
| `computed_json` | `JSON` | the serialised aggregate (`schema` tag inside) |
| `created_at` / `updated_at` | `TIMESTAMP` | |
| | | **UNIQUE** `(user_id, kind)` for `NATAL`; horary keyed via `horary_query` |

### `dasha_period` (V3)

`id`, `chart_id` (FK → `chart` `ON DELETE CASCADE`), `level` (`VARCHAR(16)` —
`MAHADASHA`…`PRANA`), `lord` (`VARCHAR(12)`), `start_utc` / `end_utc`
(`DATETIME(0)`), `parent_id` (`BIGINT` null, self-FK). Index `(chart_id, level)`.

**SPEC-008 creates the table but does NOT populate it.** `ChartCacheService`
returns the computed `DashaTimeline` object from `computed_json`; a flattened
row-per-period projection is only useful for the range queries SPEC-013 /
SPEC-015 will run, so **SPEC-013 owns writing these rows** (and the "forget me"
cascade already covers them via the `chart` FK). Landing the empty table here
keeps the FK stable and avoids a later `chart`-touching migration; populating it
now would be the "dead schema" the billing tables were deferred to avoid.

### `horary_query` (V3)

`id`, `chart_id` (FK → `chart` `ON DELETE CASCADE`), `number_1_249` (`SMALLINT`,
1..249), `question` (`VARCHAR(1000)` null), `judged_utc` (`DATETIME(0)`).

### `prediction` (V4)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` PK | |
| `user_id` | `BIGINT` | FK → `app_user` `ON DELETE CASCADE` |
| `for_date` | `DATE` | the reading's local date |
| `kind` | `VARCHAR(24)` | `DAILY` |
| `engine_version` | `VARCHAR(128)` | |
| `payload_json` | `JSON` | serialised `DailyPrediction` |
| `model` | `VARCHAR(64)` null | reserved (the LLM phrasing model, SPEC-017) |
| `created_at` | `TIMESTAMP` | |
| | | **UNIQUE** `(user_id, for_date, kind)` |

### `geocode_cache` (V5)

`id`, `query_norm` (`VARCHAR(255)` **UNIQUE**), `results_json` (`JSON`),
`created_at`, `updated_at`.

### `inbound_event` (V6)

`id`, `wa_message_id` (`VARCHAR(128)` **UNIQUE**), `raw_ref` (`VARCHAR(255)`),
`status` (`VARCHAR(16)`, default `NEW`), `attempts` (`INT`, default 0),
`received_at`, `processed_at` (null). Index `(status, received_at)`.

### `outbox_message` (V6)

`id`, `user_id` (`BIGINT` null), `target` (`VARCHAR(64)`), `payload_json` (`JSON`),
`status` (`VARCHAR(16)`, default `PENDING`), `attempts` (`INT`, default 0),
`error` (`VARCHAR(500)` null), `created_at`, `updated_at`. Index
`(status, created_at)`.

## Entity ↔ domain mappings

| Domain value (module) | Persisted as | Codec |
|-----------------------|--------------|-------|
| `geo.ResolvedBirth` + `name` + `placeQuery` | `birth_data` columns | field-by-field in `BirthDataStore` |
| `ephemeris.BirthData` | derived from `birth_data` (`birth_utc`, `lat`, `lon`) | `BirthDataStore.load(...).birthData()` |
| `core.chart.NatalChart` (+ `SignificatorTable`, `DashaTimeline` re-derived on read) | `chart.computed_json` = raw `EphemerisResult` + `HouseResult` | `ChartCodec` (rebuilds via pure `NatalChartFactory.assemble`) |
| `core.prediction.DailyPrediction` | `prediction.payload_json` | `PredictionCodec` |
| `geo.place.GeocodeResult` | `geocode_cache.results_json` | `GeocodeResultCodec` |
| `ephemeris.EngineVersion` | `chart.engine_version` / `prediction.engine_version` (`id()` string) | `CurrentEngineVersion` |

## `store` service contracts

```java
@Service class UserAccountService {
    AppUserRef findOrCreate(String phoneE164);      // idempotent under concurrency
    void recordConsent(long userId);                // write-once — conditional UPDATE ... WHERE consent_at IS NULL
    void forget(long userId);                       // @Transactional; deletes PII rows, phone_e164 -> NULL + phone_hash
}

@Service class BirthDataStore {
    void save(long userId, String name, String placeQuery, ResolvedBirth resolved);  // upsert by user_id
    Optional<StoredBirth> load(long userId);        // StoredBirth exposes ResolvedBirth-equivalent + birthData()
}

@Service class ChartCacheService {
    NatalChartBundle natalChart(long userId);       // versioned: serve-or-recompute
    // horaryChart(...) is SPEC-015's — a horary chart needs a judgment moment / place.
}

@Service class PredictionCacheService {
    DailyPrediction dailyPrediction(long userId, LocalDate date);  // versioned
}

@Component class JpaGeocodeCache implements com.celestia.geo.place.GeocodeCache {
    Optional<GeocodeResult> get(String queryNorm);
    void put(String queryNorm, GeocodeResult result);
}

@Service class InboxStore {
    sealed interface RecordResult { record Recorded(long id) ...; record Duplicate(long existingId) ...; }
    RecordResult recordInbound(String waMessageId, String rawRef);
    Optional<ClaimedEvent> claimNext();
    void markProcessed(long id); void markFailed(long id, String error);
}

@Service class OutboxStore {
    long enqueue(Long userId, String target, String payloadJson);
    Optional<ClaimedMessage> claimNextPending();
    void markSent(long id); void markFailed(long id, String error);
}

@Component class CurrentEngineVersion {
    EngineVersion forBirth(BirthData birth);        // accuracy-aware
    String id(BirthData birth);
}
```

`NatalChartBundle` = `{ NatalChart chart, SignificatorTable significators,
DashaTimeline dasha, String engineVersion }`. (`RulingPlanets` is a
"now" / horary timing concept, not a natal-chart property, so it is not in the
bundle.)

## Invariants (tested)

1. `BirthDataStore.load(save(x)).birthData()` equals `x.birthData()` — no
   precision or timezone loss (SC-001).
2. `ChartCacheService.natalChart` with a fresh cache computes once; a second call
   with a matching `engine_version` computes **zero** times; a row with a
   different `engine_version` computes once and overwrites (SC-002).
3. `PredictionCacheService` recomputes when `engine_version` or `for_date`
   changes; the `(user, for_date, kind)` row is unique.
4. `UserAccountService.findOrCreate` under 20 concurrent calls for one phone →
   one row, no exception to any caller (SC-003).
5. `forget(userId)` → 0 rows in `birth_data` / `chart` / `dasha_period` /
   `horary_query` / `prediction` for the user; `app_user.phone_e164` is a
   `deleted:<hash>` value; `deleted_at` set (SC-004).
6. `JpaGeocodeCache.put` then `get` in a fresh context → candidate list equal;
   `get` on an absent key → empty (SC-005).
7. `InboxStore.recordInbound` twice with one `wa_message_id` → one row, second is
   `Duplicate`; a claimed event is not re-claimed (SC-007).
8. Flyway `V1..V6` apply to an empty `mysql:8.4`, are re-runnable, and pass
   checksum validation (SC-006).
9. No `com.celestia.{ephemeris,core,geo,billing,guardrail}` class depends on
   `jakarta.persistence` / `org.springframework.data` / `org.flywaydb` / `java.sql`.
