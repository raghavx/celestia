# Quickstart: verifying SPEC-009

Builds on SPEC-008 (merged). Branch `009-whatsapp-channel`. No live WhatsApp
number or Meta account is needed anywhere in this suite.

## Prerequisites

- JDK 25, `./mvnw`.
- A container runtime for the `*IT` tests (Colima / Podman / local MySQL —
  see `docs/persistence.md`).
- No `.se1` data needed (this spec never touches the ephemeris).

## Build & test

```bash
./mvnw -q -pl channel-whatsapp,agent -am verify
```

New coverage:

- `channel.whatsapp` — `WebhookSignatureVerifierTest` (tampered/missing
  header, fail-closed), `WebhookVerificationHandshakeTest`,
  `InboundPayloadParserTest` (fixture: single message, a 3-message batch, a
  status callback, an unsupported type), `WindowPolicyTest` (the 24h
  boundary), `WhatsAppTemplateCatalogTest`.
- `channel.whatsapp.WhatsAppMessagingChannelTest` — WireMock-backed: each
  `OutboundMessage` shape produces the documented Graph API JSON; 4xx →
  `Failed(retryable=false)`; 5xx → `Failed(retryable=true)`.
- `agent.channel` — `WhatsAppWebhookControllerGetTest` (MockMvc: GET
  handshake) and `WhatsAppWebhookControllerPostTest` (POST signature
  reject/accept, batch recording, duplicate no-op, 10-message timing, a
  malformed message in a batch skipped without sinking its well-formed
  siblings), `WhatsAppInboundWorkerIT`, `WhatsAppOutboundSenderIT` (Testcontainers
  `mysql:8.4` + WireMock), `ReclaimSweepIT` (both stores' `reclaimStale`
  directly, both branches), `RestartSurvivalIT` (a crashed-then-restarted
  worker/sender via `.tick()`), `WhatsAppChannelEndToEndIT` (webhook →
  worker → outbox → WireMock Graph API, exactly one send, plus a replay
  case).
- `ChannelArchitectureTest` — no class outside
  `com.celestia.channel.whatsapp` depends on `...graphapi`.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 GET handshake (SC-001) | `WebhookVerificationHandshakeTest`, `WhatsAppWebhookControllerGetTest` |
| US2 signature reject (SC-002) | `WebhookSignatureVerifierTest`, `WhatsAppWebhookControllerPostTest` |
| US2 idempotent record + batch, incl. a long body (SC-002, H1) | `InboundPayloadParserTest`, `WhatsAppWebhookControllerPostTest` |
| US2 full-stack replay — one record, zero extra replies (SC-004, FR-017) | `WhatsAppChannelEndToEndIT` |
| US2 fast response on a 10-message batch (SC-003) | `WhatsAppWebhookControllerPostTest` (timing assertion) |
| US2 a malformed message in a batch doesn't sink its well-formed siblings (Edge Cases) | `WhatsAppWebhookControllerPostTest` |
| US3 worker claim + echo + unsupported-type fallback | `WhatsAppInboundWorkerIT` |
| US3 no double-claim under concurrency | `WhatsAppInboundWorkerIT` (two claimers) |
| US4 window decision (SC-006) | `WindowPolicyTest`, `WhatsAppOutboundSenderIT` |
| US4 retry/backoff, permanent-failure no-retry (SC-007) | `WhatsAppOutboundSenderIT` |
| US4 round trip (SC-005, Phase 4 exit) | `WhatsAppChannelEndToEndIT` |
| US5 interactive list shape | `WhatsAppMessagingChannelTest` |
| US6 reclaim sweep, both branches (under/over attempt cap) | `ReclaimSweepIT` |
| US6 restart survival (crashed worker/sender, next tick recovers) | `RestartSurvivalIT` |
| purity (FR-011) | `ChannelArchitectureTest` |

## Architecture gates

- `ChannelArchitectureTest` (new) — the `graphapi` package stays internal.
- `LayeringArchitectureTest` (SPEC-001, unchanged) already forbids
  `core`/`ephemeris`/`geo` from depending on `com.celestia.channel..`.
- `DeterminismArchitectureTest` — unaffected; `channel-whatsapp` was never in
  the pure-module list.

## Interpreting a failure

- **signature check passes for a tampered body** → the controller likely
  parsed-then-reserialised before verifying; it must verify the exact
  `byte[]` first.
- **duplicate delivery produces two replies** → check `InboxStore.recordInbound`
  is called with the **provider** message id, not a locally generated one.
- **window decision wrong at the boundary** → `WindowPolicy.isWithinWindow`
  must be strict (`<`), not `<=`.
- **retry never stops** → `markRetry` vs `markFailed` branch on `attempts >=
  maxAttempts`, not on `retryable` alone.
- **`ChannelArchitectureTest` fails** → a class outside `channel.whatsapp`
  imported a `graphapi` DTO directly instead of going through
  `WhatsAppMessagingChannel` / `MessagingChannel`.
