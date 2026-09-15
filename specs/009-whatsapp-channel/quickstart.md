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
- `agent.channel` — `WhatsAppWebhookControllerTest` (MockMvc: GET handshake,
  POST signature reject/accept, batch recording, duplicate no-op),
  `WhatsAppInboundWorkerIT`, `WhatsAppOutboundSenderIT` (Testcontainers
  `mysql:8.4` + WireMock), `WhatsAppChannelEndToEndIT` (webhook → worker →
  outbox → WireMock Graph API, exactly one send).
- `ChannelArchitectureTest` — no class outside
  `com.celestia.channel.whatsapp` depends on `...graphapi`.

## Scenario → test map

| Spec scenario | Test |
|---------------|------|
| US1 GET handshake (SC-001) | `WebhookVerificationHandshakeTest`, `WhatsAppWebhookControllerTest` |
| US2 signature reject (SC-002) | `WebhookSignatureVerifierTest`, `WhatsAppWebhookControllerTest` |
| US2 idempotent record + batch (SC-002, SC-004) | `InboundPayloadParserTest`, `WhatsAppWebhookControllerTest`, `WhatsAppChannelEndToEndIT` |
| US2 fast response (SC-003) | `WhatsAppWebhookControllerTest` (timing assertion) |
| US3 worker claim + echo + unsupported-type fallback | `WhatsAppInboundWorkerIT` |
| US3 no double-claim under concurrency | `WhatsAppInboundWorkerIT` (two claimers) |
| US4 window decision (SC-006) | `WindowPolicyTest`, `WhatsAppOutboundSenderIT` |
| US4 retry/backoff, permanent-failure no-retry (SC-007) | `WhatsAppOutboundSenderIT` |
| US4 round trip (SC-005, Phase 4 exit) | `WhatsAppChannelEndToEndIT` |
| US5 interactive list shape | `WhatsAppMessagingChannelTest` |
| US6 restart reliability + reclaim sweep | `WhatsAppInboundWorkerIT`, `WhatsAppOutboundSenderIT` |
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
