# Contract: `agent.persistence.store` — the persistence layer API

Consumers: SPEC-009 (channel — `InboxStore` / `OutboxStore`), SPEC-010
(onboarding — `UserAccountService` / `BirthDataStore`), SPEC-011/012 (billing
builds on this layer), SPEC-014 (`guardrail_event` will be added), SPEC-015
(Q&A — `BirthDataStore` + `ChartCacheService` + `PredictionCacheService`).

**Rules**

- The `store` package is the layer's **only** public surface. `entity` and
  `repository` are internal.
- Every method takes and returns domain values — `ResolvedBirth`, `BirthData`,
  `NatalChart`, `DailyPrediction`, `GeocodeResult`, primitives. **No** JPA /
  Hibernate / entity type crosses this boundary (FR-015).
- The pure modules (`ephemeris`, `core`, `geo`, `billing`, `guardrail`) do not
  depend on this package or on any persistence library (ArchUnit-enforced).
- Multi-table operations are `@Transactional` (FR-016).

## `UserAccountService`

| Method | Contract |
|--------|----------|
| `AppUserRef findOrCreate(String phoneE164)` | Returns the existing user for `phoneE164` or creates one. **Idempotent under concurrency** — a race resolves to one row and no caller sees a `DataIntegrityViolationException` (SC-003). |
| `void recordConsent(long userId)` | Sets `consent_at` to now **iff** it is null; a later call is a no-op. |
| `void forget(long userId)` | `@Transactional`. Deletes the user's `birth_data`, `chart` (+ `dasha_period`, `horary_query`), and `prediction` rows; sets `app_user.display_name`/`wa_id` null, `phone_e164` = `deleted:<sha256(e164)>`, `deleted_at` = now. Payment/tax rows exempt (ADR-0016; not in this schema yet). |

## `BirthDataStore`

| Method | Contract |
|--------|----------|
| `void save(long userId, String name, String placeQuery, ResolvedBirth resolved)` | Upsert by `user_id` (one row per user). Persists every place-and-time-derived field plus `name` + `placeQuery`. |
| `Optional<StoredBirth> load(long userId)` | `StoredBirth.birthData()` returns a `BirthData` **identical** to `resolved.birthData()` at save time — `birth_utc` reloads to the exact `Instant`, `lat`/`lon` unchanged (SC-001). `StoredBirth` also exposes the local date/time, zone, flags, label. |

## `ChartCacheService`

| Method | Contract |
|--------|----------|
| `NatalChartBundle natalChart(long userId)` | Loads `birth_data`; computes `expected = CurrentEngineVersion.id(birthData)`. If the `chart` row (`user_id`, `NATAL`) exists, `engine_version == expected`, and `computed_json` deserialises → return it with **no engine call**. Otherwise compute via `core`, upsert the row + replace `dasha_period` children (`@Transactional`), return fresh. |
| `HoraryChartBundle horaryChart(long userId, int number)` | As above for `kind = HORARY` + a `horary_query` row; `number` in 1..249. |

`NatalChartBundle` = `{ NatalChart chart, SignificatorTable significators,
DashaTimeline dasha, RulingPlanets rulingPlanets, String engineVersion }`.

## `PredictionCacheService`

| Method | Contract |
|--------|----------|
| `DailyPrediction dailyPrediction(long userId, LocalDate date)` | Keyed `(user_id, for_date, DAILY)`. Serve on `engine_version == expected`, else recompute via `DailyPredictionFactory` and upsert. |

## `JpaGeocodeCache implements com.celestia.geo.place.GeocodeCache`

| Method | Contract |
|--------|----------|
| `Optional<GeocodeResult> get(String queryNorm)` | Hit → a `GeocodeResult` whose `candidates()` **equals** the stored list (SC-005). Deserialise failure → `Optional.empty()`. Absent → `Optional.empty()`. |
| `void put(String queryNorm, GeocodeResult result)` | Upsert on `query_norm`. |

Wired into SPEC-007's `CachingGeocoder` in place of `InMemoryGeocodeCache` once
the real `Geocoder` adapter exists (SPEC-009/010).

## `InboxStore` / `OutboxStore`

| Method | Contract |
|--------|----------|
| `RecordResult recordInbound(String waMessageId, String rawRef)` | `INSERT`; unique-key hit → `Duplicate(existingId)`, else `Recorded(id)`. Idempotent (SC-007). |
| `Optional<ClaimedEvent> claimNext()` | Claims one `NEW` event with `FOR UPDATE SKIP LOCKED`; a concurrent claimer gets a different row or none. |
| `void markProcessed(long id)` / `void markFailed(long id, String error)` | Sets `status` + `processed_at` / `error`, `attempts` already incremented at claim. |
| `long enqueue(Long userId, String target, String payloadJson)` | Appends a `PENDING` outbox row. |
| `Optional<ClaimedMessage> claimNextPending()` / `markSent` / `markFailed` | As the inbox, for outbound. |

The webhook, the `@Scheduled` worker, and `ShedLock` are **SPEC-009**.

## `CurrentEngineVersion`

| Method | Contract |
|--------|----------|
| `EngineVersion forBirth(BirthData birth)` | `rules` = `EngineVersion.RULES`; `deltaTModel` = `TimeScales.DELTA_T_MODEL`; `sePort` from config; `ephemerisData` = `swieph:<id>` if the birth year is inside the configured `.se1` range, else `moseph`. |
| `String id(BirthData birth)` | `forBirth(birth).id()` — the string stored in / compared against `chart.engine_version`. |

A change to `EngineVersion.RULES` (a KP rule change, per `core/REFERENCES.md`)
makes every stored `engine_version` mismatch → the whole cache self-heals on
read. No migration, no sweep.
