---
description: "Task list for SPEC-009 — WhatsApp Channel"
---

# Tasks: WhatsApp Channel (SPEC-009)

**Input**: Design documents in `specs/009-whatsapp-channel/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the signature check, the idempotency rule, and the round
trip are the point (SC-001…SC-007).

**Organization**: by user story. MVP = Setup + Foundational + US1 + US2 + US3 +
US4 — the exact Phase 4 exit criterion.

Split across two modules (no new module): `channel-whatsapp` (protocol +
computation, no DB) and `agent.channel` (the HTTP endpoint, the two scheduled
loops, storage glue). Builds on SPEC-008 (`InboxStore`, `OutboxStore`,
`UserAccountService`, `PersistenceIT`). No new main-scope dependency; test-only
`org.wiremock:wiremock:3.13.1`. ADR-0011/0012 accepted by the plan.

## Format: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup

- [ ] T001 Parent `pom.xml` — add `wiremock.version=3.13.1` property +
  `dependencyManagement` entry for `org.wiremock:wiremock`. `channel-whatsapp/pom.xml`
  and `agent/pom.xml` — add it as a **test**-scope dependency.
- [ ] T002 `agent/src/main/resources/db/migration/V7__whatsapp_channel.sql` —
  additive: `app_user.window_expires_at DATETIME(0) NULL`,
  `outbox_message.provider_message_id VARCHAR(64) NULL`,
  `outbox_message.next_attempt_at DATETIME(0) NULL`; **and widen**
  `inbound_event.raw_ref` from `VARCHAR(255)` to `TEXT` (`ALTER TABLE
  inbound_event MODIFY raw_ref TEXT NOT NULL`) — SPEC-009 stores the
  message's JSON directly (T020), and a WhatsApp text body runs to 4096
  characters.
- [ ] T003 [P] `channel-whatsapp/src/test/resources/whatsapp/payloads/` —
  fixture JSON: `single-text.json` (one message), `batch-three.json` (three
  messages in one delivery), `batch-ten.json` (ten messages, for the SC-003
  latency check), `long-text.json` (a message body near WhatsApp's 4096-char
  limit — proves `raw_ref TEXT` round-trips, H1), `status-callback.json` (a
  `statuses[]` payload, no `messages[]`), `unsupported-type.json` (e.g.
  `type: "image"`), matching Meta's `entry[].changes[].value.messages[]`
  shape (research.md §4).
- [ ] T004 [P] `channel-whatsapp/src/main/resources/whatsapp-templates/generic-reply.yml`
  — the v1 stub/fixture template entry (contracts/whatsapp-channel-api.md).
- [ ] T005 `agent/src/main/resources/application.yml` — `whatsapp.*` properties
  (`phone-number-id`, `graph-api-base-url`, `graph-api-version`,
  `access-token`, `app-secret`, `verify-token`, all `${ENV_VAR}` — never
  literal) and channel scheduling properties (poll interval, max attempts,
  reclaim threshold).

---

## Phase 2: Foundational (blocking — no user story starts until this is done)

- [ ] T006 [P] `channel/whatsapp/OutboundMessage.java` (sealed:
  `TextMessage`, `InteractiveListMessage` + `ListSection`/`ListRow`,
  `TemplateMessage` + `TemplateParam`; compact-ctor validation) and
  `SendResult.java` (sealed: `Sent(providerMessageId)`,
  `Failed(errorCode, message, retryable)`) in `channel-whatsapp`.
- [ ] T007 [P] `channel/whatsapp/InboundMessage.java` + `MessageContent.java`
  (sealed: `TextContent`, `UnsupportedContent(type)`) in `channel-whatsapp`.
- [ ] T008 [P] `channel/whatsapp/MessagingChannel.java` (port interface) +
  `WhatsAppConfig.java` (`@ConfigurationProperties("whatsapp")`) in
  `channel-whatsapp`.
- [ ] T009 [P] `FoundationalTypesTest` in `channel-whatsapp` — every
  `OutboundMessage` variant rejects blank/empty required fields;
  `InboundMessage` / `MessageContent` construct cleanly.
- [ ] T010 [P] `agent/persistence/entity/AppUserEntity.java` — add
  `windowExpiresAt` (`Instant`, column `window_expires_at`) field + getter/setter.
- [ ] T011 [P] `agent/persistence/entity/OutboxMessageEntity.java` — add
  `providerMessageId` (`String`) and `nextAttemptAt` (`Instant`) fields +
  getters/setters.
- [ ] T012 [P] `agent/src/test/java/com/celestia/agent/architecture/ChannelArchitectureTest.java`
  — `noClasses().that().resideOutsideOfPackage("com.celestia.channel.whatsapp..")
  .should().dependOnClassesThat().resideInAPackage("com.celestia.channel.whatsapp.graphapi..")`.

**Checkpoint**: `./mvnw -pl channel-whatsapp,agent -am test` — value types +
entities compile and validate; ArchUnit rule in place (vacuously true until
`graphapi/` exists).

---

## Phase 3: User Story 1 — Webhook verification handshake (P1) 🎯 MVP

**Goal**: `GET /webhooks/whatsapp` answers Meta's setup challenge correctly.

### Tests (write first)

- [ ] T013 [P] [US1] `WebhookVerificationHandshakeTest` in `channel-whatsapp`
  — `mode="subscribe"` + matching token → `Optional.of(challenge)`; wrong
  token → empty; `mode` anything else → empty; constant-time comparison used
  (no early-exit on the token compare — assert via a fixed-length check, not a
  timing test).

### Implementation

- [ ] T014 [US1] `channel/whatsapp/WebhookVerificationHandshake.java` — pure,
  static `challenge(mode, token, challenge, expectedToken)`.
- [ ] T015 [US1] `agent/channel/WhatsAppWebhookController.java` (GET only) +
  `WhatsAppWebhookControllerGetTest` (MockMvc) in `agent` — `GET
  /webhooks/whatsapp` wired to `WebhookVerificationHandshake`; `200` + body =
  challenge on match; a rejection status with no challenge in the body
  otherwise.

**Checkpoint**: Meta can register the webhook (SC-001).

---

## Phase 4: User Story 2 — Secure, idempotent inbound intake (P1)

**Goal**: `POST /webhooks/whatsapp` verifies the raw-body signature, records
every message exactly once, and responds fast.

### Tests (write first)

- [ ] T016 [P] [US2] `WebhookSignatureVerifierTest` in `channel-whatsapp` —
  valid signature → `true`; tampered body, wrong secret, missing header,
  malformed header → `false`, never throws.
- [ ] T017 [P] [US2] `InboundPayloadParserTest` in `channel-whatsapp` — the
  T003 fixtures: `single-text.json` → 1 `InboundMessage`; `batch-three.json`
  → 3, each independently re-parseable via `parseOne` from its own JSON;
  `long-text.json` → the full ~4096-char body preserved exactly (no
  truncation — H1); `status-callback.json` → `[]`; `unsupported-type.json` →
  1 message with `UnsupportedContent`.
- [ ] T018 [P] [US2] `WhatsAppWebhookControllerPostTest` (MockMvc, `InboxStore`
  mocked) in `agent` — a tampered signature → rejected, `InboxStore` never
  called; a valid signature → `InboxStore.recordInbound` called once per
  message in the payload (incl. `status-callback.json` → zero calls, still
  `200` — FR-015), response `200`; posting `batch-ten.json` responds in
  under 1 second (SC-003), asserted with a wall-clock timing check around the
  `MockMvc` call.

### Implementation

- [ ] T019 [US2] `channel/whatsapp/WebhookSignatureVerifier.java` — pure,
  static `valid(byte[] rawBody, String signatureHeader, String appSecret)`;
  HMAC-SHA256 + `MessageDigest.isEqual`; fail-closed on any exception.
- [ ] T020 [US2] `channel/whatsapp/InboundPayloadParser.java` — static
  `parseAll(byte[] rawBody)` (walks every `messages[]`, `[]` for
  non-message payloads) and `parseOne(String messageJson)`.
- [ ] T021 [US2] `agent/channel/WhatsAppWebhookController.java` (POST) —
  `@RequestBody byte[] rawBody` + `X-Hub-Signature-256` header; verify →
  reject on mismatch (nothing recorded); else `parseAll`, then
  `inboxStore.recordInbound(providerId, messageJson)` per message; `200`
  returned once recording completes, no reply-generation on this thread.

**Checkpoint**: SC-002/SC-003/SC-004 — security + idempotency + speed.

---

## Phase 5: User Story 3 — Async processing off the webhook thread (P1)

**Goal**: a worker claims recorded messages and queues a reply, one at a time
per message, without erroring on unsupported types.

### Tests (write first)

- [ ] T022 [P] [US3] `EchoReplyHandlerTest` in `agent` — any `InboundMessage`
  (text or `UnsupportedContent`) → a non-blank `TextMessage` reply.
- [ ] T023 [P] [US3] `WhatsAppWindowStoreIT` (extends `PersistenceIT`) in
  `agent` — `recordInbound(userId, when)` sets `window_expires_at = when +
  24h`; `windowExpiresAt(userId)` reads it back; absent user → empty.

### Implementation

- [ ] T024 [US3] `agent/channel/ReplyHandler.java` (interface) +
  `EchoReplyHandler.java` (v1 `@Component`) in `agent`.
- [ ] T025 [US3] `agent/persistence/store/WhatsAppWindowStore.java` +
  `AppUserRepository` query addition (update `window_expires_at` by id).
- [ ] T026 [US3] `agent/channel/WhatsAppInboundWorker.java` (`@Scheduled`) —
  loop `inboxStore.claimNext()`; per claim: `parseOne(rawRef)` →
  `userAccountService.findOrCreate(inbound.fromE164())` →
  `windowStore.recordInbound(...)` → `replyHandler.reply(...)` →
  `outboxStore.enqueue(...)` → `inboxStore.markProcessed(id)`; any exception →
  `inboxStore.markFailed(id, error)`.
- [ ] T027 [US3] `WhatsAppInboundWorkerIT` (extends `PersistenceIT`) in `agent`
  — a recorded text message is claimed, resolved, replied to, and queued; a
  recorded `long-text.json` message round-trips through the real `TEXT`
  `raw_ref` column and is claimed/parsed intact (H1, against real MySQL); an
  `unsupported-type.json` message still produces a queued reply (no error, no
  silent skip); two concurrent `claimNext()` callers never claim the same row.

**Checkpoint**: the webhook thread is free; a reply is queued for every
inbound message.

---

## Phase 6: User Story 4 — Outbound send via the Graph API, respecting the 24h window (P1)

**Goal**: the sender drains the outbox, picks free-form vs. template per the
window, delivers via the Graph API, retries transient failures with backoff.

### Tests (write first)

- [ ] T028 [P] [US4] `WindowPolicyTest` in `channel-whatsapp` —
  `windowExpiresAt(lastInboundAt)` = `+24h`; `isWithinWindow` is strict
  (`now < expiresAt`), so `isWithinWindow(expiresAt, expiresAt)` is `false`.
- [ ] T029 [P] [US4] `WhatsAppTemplateCatalogTest` in `channel-whatsapp` —
  resolves `"generic-reply"` (T004) to its Meta template name + language;
  an unknown key → empty.
- [ ] T030 [P] [US4] `WhatsAppMessagingChannelTest` (WireMock) in
  `channel-whatsapp` — a `TextMessage` / `TemplateMessage` produces the
  documented Graph API JSON and returns `Sent(providerMessageId)` on `200`; a
  stubbed `4xx` → `Failed(retryable=false)`; a stubbed `5xx` → `Failed(retryable=true)`.

### Implementation

- [ ] T031 [US4] `channel/whatsapp/WindowPolicy.java` (pure).
- [ ] T032 [US4] `channel/whatsapp/WhatsAppTemplateCatalog.java` — loads
  `resources/whatsapp-templates/*.yml`.
- [ ] T033 [US4] `channel/whatsapp/graphapi/GraphApiClient.java` (internal —
  `RestClient`, POST `/{phoneNumberId}/messages`) + request/response DTOs, and
  `channel/whatsapp/WhatsAppMessagingChannel.java` (implements
  `MessagingChannel`, maps each `OutboundMessage` shape to a Graph API
  request and the response/HTTP status to a `SendResult`).
- [ ] T034 [US4] `OutboxStore` additive — a new **overload**
  `markSent(long id, String providerMessageId)` alongside SPEC-008's existing
  `markSent(long id)` (not a replacement — keeps the SPEC-008 contract
  unchanged), plus `markRetry(long id, String error, Instant nextAttemptAt)`
  (`backoff(attempts) = min(2^attempts s, 5 min)`); the `claimNextPending()`
  query gated on `next_attempt_at IS NULL OR <= NOW()` (a `NULL` value — every
  existing row — still claims immediately).
- [ ] T035 [US4] `agent/channel/WhatsAppOutboundSender.java` (`@Scheduled`) —
  loop `outboxStore.claimNextPending()`; per claim: deserialise the queued
  `OutboundMessage`; `windowStore.windowExpiresAt(userId)` +
  `WindowPolicy.isWithinWindow` → send as-is inside the window, else wrap as
  the catalog's `generic-reply` `TemplateMessage`; `messagingChannel.send(...)`;
  `Sent` → `markSent`; `Failed(retryable=true)` and `attempts < max` →
  `markRetry`; else → `markFailed`.
- [ ] T036 [US4] `WhatsAppOutboundSenderIT` (`PersistenceIT` + WireMock) in
  `agent` — a queued reply inside the window sends free-form; outside it,
  template; a Graph API stub that fails twice then succeeds → exactly one
  successful send; a persistently failing stub → `markFailed` after the
  configured max attempts, never retried again.

**Checkpoint**: SC-005/SC-006/SC-007 — the send half of the round trip.

---

## Phase 7: User Story 5 — Interactive messages (P2)

- [ ] T037 [P] [US5] Extend `WhatsAppMessagingChannelTest` — an
  `InteractiveListMessage` produces the documented WhatsApp interactive-list
  JSON (header, body, sections of rows) against WireMock.
- [ ] T038 [US5] Extend `GraphApiClient` / `WhatsAppMessagingChannel` to
  build and send the interactive-list request shape.

**Checkpoint**: SPEC-010/013 can send a list message with no channel code of
their own.

---

## Phase 8: User Story 6 — Reliability under retries (P2)

- [ ] T039 [P] [US6] `InboxStore.reclaimStale(Duration, int maxAttempts)` and
  `OutboxStore.reclaimStale(Duration, int maxAttempts)` — `CLAIMED` rows older
  than the threshold and under the attempt cap → back to `NEW`/`PENDING`;
  over the cap → `markFailed`. Unit/IT tests for both branches.
- [ ] T040 [US6] Wire a reclaim-sweep call at the top of each
  `WhatsAppInboundWorker` / `WhatsAppOutboundSender` tick; a restart-survival
  IT — a `NEW`/`PENDING` row untouched by a "crashed" run is still processed
  by the next tick; a `CLAIMED` row older than the threshold is reclaimed and
  processed instead of stuck forever.

**Checkpoint**: the channel survives a worker/sender restart cleanly.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T041 [P] `WhatsAppChannelEndToEndIT` (`PersistenceIT` + WireMock) in
  `agent` — `POST /webhooks/whatsapp` with a real signed fixture payload →
  the worker → the outbox → a WireMock-stubbed Graph API receives **exactly
  one** send. This is the Phase 4 exit criterion, end to end. **Plus a
  replay case (SC-004/FR-017)**: POST the identical signed payload a second
  time before the worker runs — still one recorded `inbound_event` row; run
  the worker to completion, then re-run the webhook POST and the worker
  again — the Graph API still receives **exactly one** send in total, never
  a second reply for the same message.
- [ ] T042 [P] `docs/whatsapp-channel.md` (or `channel-whatsapp/README.md`) —
  the module split, the message shapes, running the WireMock/Testcontainers
  suite, the template-catalog format, the secrets required at runtime.
- [ ] T043 [P] `.github/workflows/ci.yml` — confirm `channel-whatsapp` and the
  new `agent.channel` tests run in `verify` on `ubuntu-latest` (already true
  via the module build; note `*IT` still gated by `-DskipITs` on macOS per
  SPEC-008).
- [ ] T044 Run `specs/009-whatsapp-channel/quickstart.md` end to end; fix drift.
- [ ] T045 `./mvnw -q verify` — full reactor green incl.
  `ChannelArchitectureTest`, `LayeringArchitectureTest`, `DeterminismArchitectureTest`,
  the golden-chart suite, Spotless.

---

## Dependencies & Execution Order

1. **Setup (Phase 1)** — T001 (WireMock dep) independent; T002 (migration)
   blocks anything touching `window_expires_at` / `provider_message_id` /
   `next_attempt_at`; T003/T004 fixtures block US2/US4 tests; T005 config
   blocks the controller/worker/sender.
2. **Foundational (Phase 2)** — value types + entity fields + the ArchUnit
   rule; blocks every user story.
3. **US1 (Phase 3)** — depends on Foundational only.
4. **US2 (Phase 4)** — depends on Foundational + T003 fixtures. Independent
   of US1's handshake logic (shares the controller class, not the code path).
5. **US3 (Phase 5)** — depends on US2 (needs recorded messages to claim) +
   SPEC-008's `InboxStore`/`OutboxStore`/`UserAccountService`.
6. **US4 (Phase 6)** — depends on Foundational (`MessagingChannel`,
   `OutboundMessage`) + US3 (needs queued outbound messages to send) + T004.
7. **US5 (Phase 7)** — depends on US4's `GraphApiClient`/`WhatsAppMessagingChannel`.
8. **US6 (Phase 8)** — depends on US3 + US4 (extends both loops).
9. **Polish (Phase 9)** — T041 needs US1–US4 complete; after the stories you ship.

### Parallel opportunities

- Setup: T001/T003/T004 parallel; T002 → T005 (config references the schema
  conceptually, not literally — can also run parallel).
- Foundational: T006/T007/T008/T009/T010/T011/T012 all parallel (disjoint files).
- US1 and US2's **tests** can be written in parallel; their controller
  **implementations** touch the same class (T015 GET, T021 POST) — do T015
  first, T021 extends it.
- US4: T028/T029/T030 (tests) parallel; T031/T032 parallel; T033 after T006-8;
  T034 independent of T031-33; T035 after T031-34.
- US5 and US6 whole phases can run in parallel once US4 is done.

---

## Implementation Strategy

**MVP**: Setup → Foundational → US1 → US2 → US3 → US4. Delivers the Phase 4
exit criterion verbatim: a message round-trips to a canned echo reply;
spoofed/invalid-signature payloads are rejected. Stop, run `quickstart.md`,
open the PR.

**No live WhatsApp number anywhere** — every test uses fixtures + WireMock;
Meta Business verification / number registration (ADR-0011/0012) is a
separate, external, long-lead-time task, not a blocker for this spec's tests.

---

## Notes

- No new module, no new main-scope dependency. `channel-whatsapp` holds the
  protocol logic (no DB); `agent.channel` holds the HTTP endpoint, the two
  `@Scheduled` loops, and the storage glue — mirroring SPEC-008's
  `EngineConfig`/`store` split.
- `InboxStore` / `OutboxStore` gain **additive** methods only — SPEC-008's
  existing contract is unchanged.
- The v1 reply is a canned echo. `ReplyHandler` is the seam SPEC-010 (and
  later SPEC-014/015) replace — no channel code changes when they do.
- Commit per task or tight group; keep `master` releasable (work on `009-…`).
