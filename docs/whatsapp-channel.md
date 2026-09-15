# WhatsApp channel (SPEC-009)

The WhatsApp Cloud API adapter, split across two modules by the direction of
the existing dependency graph: `agent` depends on `channel-whatsapp`, never
the reverse.

## Module split

| Module | Owns | Depends on Spring/network? |
|---|---|---|
| `channel-whatsapp` | Protocol + pure computation: `OutboundMessage`/`InboundMessage`/`SendResult` (sealed value types), `WebhookVerificationHandshake`, `WebhookSignatureVerifier`, `InboundPayloadParser`, `WindowPolicy`, `WhatsAppTemplateCatalog`, the `MessagingChannel` port, and its one implementation `WhatsAppMessagingChannel` + the internal `graphapi.GraphApiClient` HTTP client | Yes — it's an edge adapter (Constitution II explicitly exempts it, alongside `payments-razorpay`) |
| `agent.channel` | The HTTP endpoint (`WhatsAppWebhookController`), the two `@Scheduled` loops (`WhatsAppInboundWorker`, `WhatsAppOutboundSender`), and persistence glue (`WhatsAppWindowStore`, `InboxStore`/`OutboxStore` extensions) | Yes — this is the wiring layer |

`ChannelArchitectureTest` enforces the one rule that keeps this split honest:
nothing outside `com.celestia.channel.whatsapp..` may depend on
`com.celestia.channel.whatsapp.graphapi..` — Graph API request/response DTOs
never leak past `WhatsAppMessagingChannel`. `agent` code holds only
`MessagingChannel`, `OutboundMessage`, `SendResult`, and the other public
`channel-whatsapp` types.

## Message flow

```
POST /webhooks/whatsapp  →  WhatsAppWebhookController
                              (signature check, then InboxStore.recordInbound
                               once per message — no reply-generation here)
                                        ↓
                          WhatsAppInboundWorker.tick()  (@Scheduled)
                              claim → InboundPayloadParser.parseOne(rawRef)
                              → UserAccountService.findOrCreate
                              → WhatsAppWindowStore.recordInbound (resets the 24h window)
                              → ReplyHandler.reply(...)  (EchoReplyHandler in v1)
                              → OutboxStore.enqueue(OutboundMessageCodec.write(reply))
                                        ↓
                          WhatsAppOutboundSender.tick()  (@Scheduled)
                              claim → inside the 24h window? send as-is
                                    : outside it? downgrade to the
                                      generic-reply template
                              → MessagingChannel.send(...)
                              → Sent: markSent(id, providerMessageId)
                                Failed(retryable): markRetry (capped-exponential backoff)
                                Failed(permanent): markFailed
```

Both loops call `reclaimStale(...)` first: a `CLAIMED` row older than
`whatsapp.channel.reclaim-after` is reset to `NEW`/`PENDING` (under the
attempt cap) or `FAILED` (over it) — a crashed worker/sender never leaves a
message stuck forever (US6).

`OutboundMessageCodec` (in `agent.channel`) is the outbox's own JSON storage
format for `outbox_message.payload_json` — deliberately *not* the Graph API
request shape, which stays internal to `channel-whatsapp.graphapi`.

## Inbound payload shape (Meta Cloud API)

```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "waba-1",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "metadata": {"display_phone_number": "...", "phone_number_id": "..."},
        "contacts": [{"profile": {"name": "..."}, "wa_id": "919876543210"}],
        "messages": [{
          "from": "919876543210", "id": "wamid...", "timestamp": "...",
          "type": "text", "text": {"body": "..."}
        }]
      },
      "field": "messages"
    }]
  }]
}
```

A delivery batch can carry several `messages[]` — `InboxStore.recordInbound`
is called once per message, each with its own JSON as `raw_ref` (a `TEXT`
column, sized for the ~4096-char WhatsApp text limit). A `statuses[]` payload
(delivery/read receipts) is accepted and produces zero `recordInbound` calls
(FR-015). Recording is idempotent on the message's `wamid`
(`inbound_event.wa_message_id UNIQUE`) — a replayed delivery is a no-op
(SC-004/FR-017, exercised end to end in `WhatsAppChannelEndToEndIT`).

## The 24h customer-service window

`app_user.window_expires_at` (nullable `DATETIME(0)`) is refreshed to
`lastInboundAt + 24h` on every inbound message
(`WindowPolicy.windowExpiresAt`). `WhatsAppOutboundSender` checks
`WindowPolicy.isWithinWindow(now, expiresAt)` (a strict, half-open
comparison) before sending: inside the window any `OutboundMessage` goes out
as-is; outside it, anything other than a `TemplateMessage` is downgraded to
the single stub template shipped in v1, `generic-reply` (ADR-0012).

## Template catalog

`channel-whatsapp/src/main/resources/whatsapp-templates/*.properties` — one
file per logical template key, loaded from the classpath at startup
(`WhatsAppTemplateCatalog.loadFromClasspath`, via
`PathMatchingResourcePatternResolver`). Plain `java.util.Properties`, not
YAML — `channel-whatsapp` has no YAML parser on its classpath and adding one
purely for this wasn't worth it. Shape:

```properties
key=generic-reply
metaTemplateName=celestia_generic_reply
languageCode=en
```

Callers never hard-code Meta's approved template name — they resolve a
logical `templateKey` through the catalog. A later spec adding a new
approved template drops in one more `.properties` file; no code change.

## Runtime configuration

All under `whatsapp.*` in `application.yml`, sourced from env vars — nothing
committed:

| Property | Env var | Notes |
|---|---|---|
| `whatsapp.phone-number-id` | `WHATSAPP_PHONE_NUMBER_ID` | Graph API path segment |
| `whatsapp.graph-api-base-url` | `WHATSAPP_GRAPH_API_BASE_URL` | defaults to `https://graph.facebook.com` |
| `whatsapp.graph-api-version` | `WHATSAPP_GRAPH_API_VERSION` | defaults to `v21.0` |
| `whatsapp.access-token` | `WHATSAPP_ACCESS_TOKEN` | Bearer token for Graph API calls |
| `whatsapp.app-secret` | `WHATSAPP_APP_SECRET` | HMAC-SHA256 key for `X-Hub-Signature-256` |
| `whatsapp.verify-token` | `WHATSAPP_VERIFY_TOKEN` | Meta's webhook setup handshake |
| `whatsapp.channel.poll-interval` | `WHATSAPP_POLL_INTERVAL` | worker/sender `@Scheduled` cadence, default `PT2S` |
| `whatsapp.channel.max-attempts` | `WHATSAPP_MAX_ATTEMPTS` | reclaim-sweep / retry cap, default `5` |
| `whatsapp.channel.reclaim-after` | `WHATSAPP_RECLAIM_AFTER` | staleness threshold for a stuck `CLAIMED` row, default `PT2M` |

## Running the test suite

- `channel-whatsapp` unit tests need nothing external — `WhatsAppMessagingChannelTest`
  stubs the Graph API with `org.wiremock:wiremock-standalone` (the plain
  `wiremock` artifact's transitive Jetty 11/12 version skew makes it
  unusable as a direct dependency; the shaded standalone jar avoids it).
- `agent`'s `*IT` classes need MySQL via Testcontainers — same Podman setup
  as [`persistence.md`](persistence.md). Every IT drives the worker/sender
  explicitly through `.tick()`; the `test` Spring profile
  (`agent/src/test/resources/application-test.yml`) stretches
  `whatsapp.channel.poll-interval` out to an hour so the real `@Scheduled`
  loop never fires mid-suite and races those explicit calls.
- `WhatsAppChannelEndToEndIT` is the Phase 4 exit criterion: a real signed
  fixture POSTed to `/webhooks/whatsapp`, through both `tick()`s, to exactly
  one WireMock-stubbed Graph API send — plus the replay case (delivered
  twice, and re-delivered after processing, still exactly one send).
- Fixtures live in `channel-whatsapp/src/test/resources/whatsapp/payloads/`
  and are duplicated into `agent/src/test/resources/whatsapp/payloads/`
  (Maven modules don't share test resources).

```bash
./mvnw -pl channel-whatsapp -am test
./mvnw -pl agent -am verify   # needs Podman/Docker for the *IT classes
```
