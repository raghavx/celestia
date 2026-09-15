# Contract: the WhatsApp channel

Consumers: SPEC-010 (onboarding — provides the next `ReplyHandler`, reads
`InboundMessage`, sends `InteractiveListMessage` for POB disambiguation),
SPEC-013 (subscribe-and-pay — plan-picker `InteractiveListMessage`, renewal
`TemplateMessage`), SPEC-014/015 (guardrail + LLM pipeline — a further
`ReplyHandler`), SPEC-017 (daily push via `TemplateMessage`).

No `de.thmac`-style leakage here, but the same discipline: Graph API
request/response DTOs never cross out of `com.celestia.channel.whatsapp.graphapi`
(ArchUnit-enforced); `agent` orchestration code holds only `MessagingChannel`,
`OutboundMessage`, `InboundMessage`, and the small pure helpers.

## `MessagingChannel` (`channel-whatsapp`, public)

```java
public interface MessagingChannel {
    SendResult send(OutboundMessage message);
}
```

| Rule | Behaviour |
|------|-----------|
| `TextMessage` | free-form send; only valid inside the recipient's 24h window (the **caller** — `WhatsAppOutboundSender` — decides this, not the port) |
| `InteractiveListMessage` | a list/button message; the documented WhatsApp interactive-list JSON shape |
| `TemplateMessage` | resolved via `WhatsAppTemplateCatalog`; sendable regardless of window state |
| success | `Sent(providerMessageId)` |
| 4xx / permanent provider error | `Failed(errorCode, message, retryable=false)` |
| 5xx / network error | `Failed(errorCode, message, retryable=true)` |
| never throws for a provider-side failure | exceptions are reserved for programmer errors (e.g. an unresolvable `templateKey`) |

## Webhook HTTP contract (`agent`)

| | |
|---|---|
| `GET /webhooks/whatsapp` | query params `hub.mode`, `hub.verify_token`, `hub.challenge`; `200` + body = `hub.challenge` on a match (FR-001); otherwise a rejection with no challenge in the body |
| `POST /webhooks/whatsapp` | header `X-Hub-Signature-256`; body = Meta's raw JSON. Invalid/missing signature → rejected, nothing recorded (FR-002). Valid → every message in the payload recorded via `InboxStore.recordInbound`, response `200` **before** any reply is generated (FR-003/004). A payload with no `messages[]` (status callback, ping) → `200`, nothing recorded (FR-015). |

Both endpoints are unauthenticated at the HTTP-framework level — their entire
security model is the signature (POST) and the verify-token match (GET); no
additional filter chain is required for these two routes.

## The `ReplyHandler` seam (`agent`)

```java
public interface ReplyHandler {
    OutboundMessage reply(AppUserRef user, InboundMessage inbound);
}
```

- v1: `EchoReplyHandler` — a fixed acknowledgement string, regardless of
  `inbound.content()`, wrapped as a `TextMessage`.
- SPEC-010 registers the onboarding-FSM handler; a later spec (014/015) adds
  the guarded LLM handler. Which one runs for a given user/turn is a
  **routing decision those specs make**, not SPEC-009's — v1 has exactly one
  handler, so no routing exists yet.
- A `ReplyHandler` MUST NOT throw for an `UnsupportedContent` message — it
  must still return some `OutboundMessage` (FR-006).

## `WhatsAppTemplateCatalog` (`channel-whatsapp`)

`resources/whatsapp-templates/*.yml`, one file per logical template:

```yaml
key: generic-reply
metaTemplateName: celestia_generic_reply   # placeholder — the real, approved name (ADR-0012) is an ops task
languageCode: en
params: []
```

v1 ships exactly one entry (`generic-reply`), used only when the outbound
sender must reply outside the 24h window — which the echo path's own tests
never actually trigger (the test recipient just messaged). Real templates
(renewal reminder, payment-link resend, onboarding nudge, daily prediction)
are added by the specs that need them, once Meta has approved the wording.

## Versioning

No `EngineVersion` here — this is protocol plumbing, not a KP calculation.
`WhatsAppConfig.graphApiVersion` pins the Graph API version string
(`v{N}.0`) used in every outbound URL; bumping it is a config change, not a
code change.
