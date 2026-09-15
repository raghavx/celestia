# Implementation Plan: WhatsApp Channel (SPEC-009)

**Branch**: `009-whatsapp-channel` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-whatsapp-channel/spec.md`

## Summary

The channel splits across two modules along the same line SPEC-008 drew for
persistence — protocol/computation in the adapter, wiring + storage in `agent`
— because `agent` already depends on `channel-whatsapp` and not the reverse:

- **`channel-whatsapp`** (edge adapter, Constitution IX — not one of the pure
  modules, so network is expected here): the `MessagingChannel` port and its
  value types, the WhatsApp Graph API HTTP client, webhook signature
  verification, the GET-handshake logic, inbound-payload parsing, the 24h
  window decision, and the template catalog. All pure/computable pieces get
  unit tests with no network; the Graph API client is tested against WireMock.
- **`agent`** (`com.celestia.agent.channel`): the actual `@RestController` for
  `/webhooks/whatsapp` (raw-body capture, delegates to `channel-whatsapp`'s
  verifier/parser, then to SPEC-008's `InboxStore`), a `@Scheduled` inbound
  worker (claim → `ReplyHandler` → enqueue via `OutboxStore` — v1's
  `EchoReplyHandler` is the only implementation; SPEC-010 adds another), and a
  `@Scheduled` outbound sender (claim → window decision → `MessagingChannel`
  → mark sent/retry/failed). A small `WhatsAppWindowStore` (new, in
  `agent.persistence.store`) tracks each user's 24h window.
- **Additive migration `V7`**: `app_user.window_expires_at`,
  `outbox_message.provider_message_id` + `next_attempt_at` (the retry-backoff
  gate). SPEC-008's tables are otherwise untouched.

No LLM, no guardrail, no entitlement check — the v1 reply is a canned echo; the
worker's `ReplyHandler` seam is what SPEC-010/014/015 replace.

## Technical Context

**Language/Version**: Java 25 LTS. Spring Boot 4.1.1 — `spring-boot-starter-webmvc`
(already present in both modules) supplies `RestClient` and `@Scheduled`
(`@EnableScheduling`, no new dependency).

**Primary Dependencies**: no new **main**-scope dependency in either module.
**Test**: `org.wiremock:wiremock` `3.13.1` (current stable; Boot's BOM does not
manage it) in `channel-whatsapp` (Graph API client) and `agent` (the
webhook→worker→sender E2E test).

**Storage**: MySQL via SPEC-008. `V7__whatsapp_channel.sql` adds three nullable
columns to existing tables — no new table (the spec's Assumption that
`conversation`/FSM schema is out of scope holds).

**Testing**: `./mvnw verify`. Pure logic (signature check, handshake,
payload parsing, window policy, template lookup) — plain unit tests, no
container, no network. The Graph API client — WireMock. The webhook
controller, worker, and sender — Testcontainers `mysql:8.4` (SPEC-008's
`PersistenceIT` base), with the Graph API stubbed by WireMock for the E2E test
(SC-005).

**Target Platform**: JVM service; single instance for v1. SPEC-008's
`FOR UPDATE SKIP LOCKED` claim is already multi-instance-safe, so no
ShedLock / distributed-lock dependency is needed even though ADR-0015 flags it
as a future concern for a different kind of work (the guarded LLM pipeline).

**Project Type**: existing modules, no new module. New packages
`com.celestia.channel.whatsapp` (+ internal `com.celestia.channel.whatsapp.graphapi`)
and `com.celestia.agent.channel`.

**Performance Goals**: SC-003 — webhook response under 1 s for a 10-message
batch, with recording being the only synchronous work (an indexed unique-key
insert per message). Worker/sender poll interval is configurable (default a
few seconds) — that cadence, not a queue push, is the "async" mechanism (ADR-0015
Option 1).

**Constraints**:
- Constitution IX — `MessagingChannel` is the only type `agent` orchestration
  code may hold; Graph API request/response DTOs stay in
  `channel.whatsapp.graphapi`, enforced by a new ArchUnit rule (mirrors
  SPEC-008's "entities stay internal" rule).
- Constitution II — `channel-whatsapp` is **not** one of the five pure modules
  (`ephemeris`, `core`, `geo`, `billing`, `guardrail`); it is expected to make
  network calls. `LayeringArchitectureTest.domainCoreDoesNotDependOnEdgeAdapters`
  already forbids those five from depending on `com.celestia.channel..` —
  unaffected by this spec.
- FR-002 — the signature check runs on the **raw byte body**
  (`@RequestBody byte[]`, no JSON parsing beforehand) with a constant-time
  comparison (`MessageDigest.isEqual`).
- FR-012 — verify token, app secret, access token: env-only, never logged.
- ADR-0015's "every step is idempotent" — the inbound worker's own crash
  recovery (a claimed-then-abandoned row) is addressed by a bounded reclaim
  sweep (US6, P2); it is **not** required for the MVP's literal acceptance
  scenarios, which SPEC-008's primitives already satisfy for the
  not-yet-claimed case.
- Constitution VII — `inbound_event.raw_ref` necessarily holds the message text
  (the worker cannot reply to text it cannot read); this is consistent with
  SPEC-008's schema intent, "logged as metadata not message bodies **by
  default**" is the *log-line* policy, not the processing-queue policy;
  encryption at rest is SPEC-016.

**Scale/Scope**: a handful of new classes per module; 3 new nullable columns;
one new ArchUnit rule; v1 message volume is low (single WhatsApp test number).

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| Principle | Applies? | Status |
|-----------|----------|--------|
| I Spec-Driven Delivery | yes | PASS — spec approved, checklist 16/16, plan precedes code |
| II Deterministic pure core | partial | PASS — the five pure modules are untouched; `channel-whatsapp` is explicitly an edge adapter (not in that list) and is expected to hold network/Spring, same as `payments-razorpay` will be |
| III Calculation Correctness Is Gated | no | N/A — no calculation |
| IV LLM interprets, never calculates | no | N/A — no LLM in this spec; the echo reply is a fixed string |
| V Astrology-only guarded path | no | N/A — no chat model reachable yet; the guardrail gate is SPEC-014's, wired in front of the *real* pipeline later, not the echo path |
| VI Deterministic conversation flows | partial | PASS — the echo `ReplyHandler` is trivially deterministic; the real FSM is SPEC-010's |
| VII Identity / consent / PII | partial | PASS with a noted caveat — identity via SPEC-008's `findOrCreate`; `inbound_event.raw_ref` holds the message text of functional necessity (the worker must read it to reply); redaction/encryption at rest is SPEC-016 |
| VIII Payments from the gateway | no | N/A |
| IX Adapters at the edge | **yes, central** | PASS — `MessagingChannel` port + value types are the only WhatsApp-shaped things `agent` touches; Graph API DTOs confined to `channel.whatsapp.graphapi`, a new ArchUnit rule proves it |
| Tech constraints (Java 25, Maven, MySQL, Flyway, secrets) | yes | PASS — additive Flyway `V7`; secrets env-only (FR-012); no new main dependency |
| ADR gate (D1–D23) | **yes** | PASS — **this plan moves ADR-0011 (D11) and ADR-0012 (D12) `proposed → accepted`** (dated 2026-09-15); ADR-0015 (D15, accepted) is implemented here (the worker + outbox sender it deferred to "SPEC-009"). No new ADR. |

**Result: PASS.** No new module, no new main-scope dependency, one new
ArchUnit rule, two ADRs promoted.

## Project Structure

### Documentation (this feature)

```text
specs/009-whatsapp-channel/
├── plan.md
├── research.md          # module split rationale; raw-body capture; signature/HMAC; payload parsing shape; window + retry-backoff design; reclaim sweep; WireMock setup
├── data-model.md         # OutboundMessage/InboundMessage/SendResult value types; the V7 columns; store contracts
├── contracts/
│   └── whatsapp-channel-api.md   # MessagingChannel port; the webhook HTTP contract; ReplyHandler seam
├── quickstart.md
└── tasks.md               # created by /speckit-tasks
```

### Source code (repository root)

```text
channel-whatsapp/pom.xml                     # + test: org.wiremock:wiremock 3.13.1
channel-whatsapp/src/main/java/com/celestia/channel/whatsapp/
├── MessagingChannel.java              # port: SendResult send(OutboundMessage)
├── OutboundMessage.java               # sealed: TextMessage, InteractiveListMessage, TemplateMessage
├── SendResult.java                    # sealed: Sent(providerMessageId) | Failed(errorCode, message, retryable)
├── InboundMessage.java                # record + MessageContent sealed: TextContent, UnsupportedContent(type)
├── WebhookVerificationHandshake.java  # pure: challenge(mode, token, challenge, expected) -> Optional<String>
├── WebhookSignatureVerifier.java      # pure: valid(rawBody, signatureHeader, appSecret); HMAC-SHA256 + MessageDigest.isEqual
├── InboundPayloadParser.java          # Meta webhook JSON -> List<InboundMessage>; a single message JSON -> InboundMessage
├── WindowPolicy.java                  # pure: windowExpiresAt(lastInboundAt); isWithinWindow(now, expiresAt)
├── WhatsAppConfig.java                # @ConfigurationProperties("whatsapp"): phoneNumberId, graphApiBaseUrl/Version, accessToken, appSecret, verifyToken
├── WhatsAppTemplateCatalog.java       # logical key -> {template name, language, param slots}
├── WhatsAppMessagingChannel.java      # implements MessagingChannel via GraphApiClient
└── graphapi/                          # INTERNAL — never referenced outside this package
    ├── GraphApiClient.java            # RestClient wrapper; POST /{phoneNumberId}/messages
    └── … request/response DTOs
channel-whatsapp/src/main/resources/whatsapp-templates/generic-reply.yml   # v1 stub/fixture template
channel-whatsapp/src/test/java/…                                          # unit tests + WireMock-backed client test

agent/src/main/resources/db/migration/V7__whatsapp_channel.sql
agent/src/main/java/com/celestia/agent/channel/
├── WhatsAppWebhookController.java     # GET/POST /webhooks/whatsapp
├── WhatsAppInboundWorker.java         # @Scheduled: claim -> ReplyHandler -> enqueue -> markProcessed(+ reclaim sweep, US6)
├── WhatsAppOutboundSender.java        # @Scheduled: claim -> window decision -> MessagingChannel.send -> markSent/markRetry/markFailed
├── ReplyHandler.java                  # interface: OutboundMessage reply(AppUserRef, InboundMessage) — SPEC-010 provides the next implementation
├── EchoReplyHandler.java              # v1 @Component
└── ChannelConfig.java                 # @Configuration: MessagingChannel bean, @EnableScheduling, poll-interval props
agent/src/main/java/com/celestia/agent/persistence/store/WhatsAppWindowStore.java  # recordInbound(userId, when) / windowExpiresAt(userId)
agent/src/main/java/com/celestia/agent/persistence/entity/AppUserEntity.java       # + windowExpiresAt field
agent/src/main/java/com/celestia/agent/persistence/entity/OutboxMessageEntity.java # + providerMessageId, nextAttemptAt
agent/src/main/java/com/celestia/agent/persistence/repository/                     # + reclaimStale / retry-gated claim queries
agent/src/main/java/com/celestia/agent/persistence/store/InboxStore.java           # + reclaimStale(...)   (additive)
agent/src/main/java/com/celestia/agent/persistence/store/OutboxStore.java          # + markRetry(id, error, nextAttemptAt)  (additive)
agent/src/test/java/com/celestia/agent/architecture/ChannelArchitectureTest.java   # graphapi package stays internal
agent/src/test/java/com/celestia/agent/channel/…                                   # webhook MockMvc tests, worker/sender ITs, E2E (WireMock + Testcontainers)
```

**Structure Decision**: no new module. `channel-whatsapp` holds every
WhatsApp-protocol concern that needs no database; `agent` holds the
HTTP endpoint, the scheduled loops, and the storage-touching glue — the same
split SPEC-008 used for `EngineConfig`/`store` vs. the pure engine modules.
`OutboxStore`/`InboxStore` gain **additive** methods (no breaking change to
SPEC-008's contract).

## Phase 0 — Research

See [research.md](./research.md). Items:

1. Module split & the dependency-direction constraint (`agent → channel-whatsapp`,
   never the reverse) — why the port, the HTTP endpoint, and the scheduled
   loops land where they do.
2. Raw-body capture — `@RequestBody byte[]`, no pre-parsing, HMAC over the
   exact bytes.
3. Signature verification — HMAC-SHA256, `MessageDigest.isEqual`
   (constant-time); the GET handshake (`hub.mode`/`hub.verify_token`/`hub.challenge`).
4. Inbound payload shape — Meta's `entry[].changes[].value.messages[]`; the
   provider-agnostic `InboundMessage` / `MessageContent`; per-message
   `rawRef` (not the whole delivery) so the worker can re-parse one message
   without re-walking the batch; unsupported types and non-message payloads.
5. The 24h window — `windowExpiresAt = lastInboundAt + 24h`; storage on
   `app_user` (a `WhatsAppWindowStore`, separate from identity concerns);
   half-open boundary.
6. Outbound send shapes + retry/backoff — `TextMessage` / `InteractiveListMessage`
   / `TemplateMessage`; `next_attempt_at` gate on `outbox_message` for a real
   (bounded, increasing) backoff without a new queue technology.
7. Reliability (US6) — SPEC-008's primitives already satisfy the literal
   "not-yet-claimed survives a restart" scenarios; a bounded reclaim sweep for
   claimed-then-abandoned rows is added as a deliberate, scoped improvement.
8. Test strategy — WireMock for the Graph API; fixtures for Meta payload
   shapes; no live WhatsApp number needed anywhere in CI.

**Output**: research.md; ADR-0011 and ADR-0012 promoted to accepted.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — value types, the `V7` columns, store contracts.
- [contracts/whatsapp-channel-api.md](./contracts/whatsapp-channel-api.md) — the
  `MessagingChannel` port, the webhook HTTP contract, the `ReplyHandler` seam.
- [quickstart.md](./quickstart.md).

### Post-design Constitution re-check

Unchanged — the pure modules are untouched; `MessagingChannel` is the sole
WhatsApp-shaped surface `agent` depends on, ArchUnit-enforced; secrets stay in
config/env. **PASS.**

## Complexity Tracking

No constitution violations — intentionally empty. (`channel-whatsapp` making a
network call is expected for an edge adapter, not a pure-module violation.)
