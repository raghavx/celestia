# Phase 0 Research: WhatsApp Channel (SPEC-009)

## 1. Module split & the dependency-direction constraint

`agent/pom.xml` already lists `channel-whatsapp` as a dependency; the reverse
is impossible without a cycle. So:

- **`channel-whatsapp`** gets everything that needs no database: the
  `MessagingChannel` port, the Graph API HTTP client, signature verification,
  the GET handshake, payload parsing, the window **policy** (a pure function
  over two instants — not the storage of the window itself), and the
  template catalog. It may use Spring (it already depends on
  `spring-boot-starter-webmvc` for `RestClient`) and network — it is an edge
  adapter (Constitution IX), not one of the five pure modules.
- **`agent`** gets the `@RestController`, the two `@Scheduled` loops, and
  anything that touches SPEC-008's stores (`InboxStore`, `OutboxStore`,
  `UserAccountService`) or a new store (`WhatsAppWindowStore`). This mirrors
  `EngineConfig` living in `agent` in SPEC-008 for the identical reason —
  `agent` is where the leaf modules get wired to persistence.

## 2. Raw-body capture

Spring MVC's `@RequestBody byte[]` receives the exact request bytes via
`ByteArrayHttpMessageConverter` — no JSON parsing happens first, so the HMAC is
computed over precisely what Meta sent, with no re-serialisation drift (a
common bug: verifying against a re-serialised copy of the parsed JSON, which
can differ in whitespace/key order and silently break the check). The
controller reads `byte[] rawBody`, verifies, then parses the same bytes.

```java
@PostMapping(path = "/webhooks/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
                              @RequestHeader("X-Hub-Signature-256") String signature) { ... }
```

## 3. Signature verification & the GET handshake

- **`WebhookSignatureVerifier.valid(rawBody, signatureHeader, appSecret)`**:
  strip the `sha256=` prefix, compute `HMAC-SHA256(appSecret, rawBody)` as hex,
  compare to the header with **`MessageDigest.isEqual`** (constant-time — a
  naive `String.equals` leaks timing information about how many leading bytes
  matched, the textbook webhook-spoofing mitigation). A missing/malformed
  header, or an exception during computation, is a **fail-closed** `false`.
- **`WebhookVerificationHandshake.challenge(mode, token, challenge, expected)`**:
  `mode.equals("subscribe") && MessageDigest.isEqual(token.getBytes(UTF_8),
  expected.getBytes(UTF_8)) ? Optional.of(challenge) : Optional.empty()`. Same
  constant-time discipline even though the verify token is lower-stakes than
  the app secret (defence in depth, and it costs nothing).

## 4. Inbound payload shape

Meta's webhook body (Cloud API, current version):

```json
{
  "entry": [{
    "id": "<whatsapp-business-account-id>",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "metadata": {"phone_number_id": "..."},
        "messages": [{
          "from": "91XXXXXXXXXX",
          "id": "wamid.HBg...",
          "timestamp": "1699999999",
          "type": "text",
          "text": {"body": "hello"}
        }]
      },
      "field": "messages"
    }]
  }]
}
```

A delivery can contain **multiple `entry`/`changes`/`messages`**, and can also
be a **status callback** (`statuses` instead of `messages` — delivery/read
receipts, out of scope for v1) or something else Meta sends for setup
verification. Decision:

- `InboundPayloadParser.parseAll(rawBody) -> List<InboundMessage>` walks every
  `messages[]` array it finds; anything without a `messages` array (a status
  callback, an unrecognised shape) contributes **zero** entries — accepted,
  not an error (FR-015).
- `InboundMessage(providerId, fromE164, timestamp, content)` where `content`
  is `TextContent(body)` for `type: "text"` and `UnsupportedContent(type)` for
  anything else (image, audio, location, interactive-reply, …) — the worker
  still produces a graceful reply for `UnsupportedContent` (FR-006).
- **`rawRef` is the single message's JSON object** (`messages[i]`), not the
  whole delivery — `recordInbound(providerId, rawRef)` is called once per
  message, and the worker re-parses just that one object
  (`InboundPayloadParser.parseOne(rawRef)`), never re-walking a batch.

## 5. The 24-hour window

- `WindowPolicy.windowExpiresAt(lastInboundAt) = lastInboundAt.plus(24, HOURS)`.
- `WindowPolicy.isWithinWindow(now, expiresAt) = now.isBefore(expiresAt)` —
  half-open: the instant of expiry itself is **outside** (FR-008's boundary,
  spec Edge Cases).
- **Storage**: `app_user.window_expires_at` (additive `V7` column), updated by
  the inbound worker on every processed message via a new, narrow
  `WhatsAppWindowStore` — kept separate from `UserAccountService` (identity /
  consent / forgetting) because the window is a channel concern, not an
  identity one. `forget(userId)` does not need to touch it (the column is
  meaningless once `phone_e164` is null; no PII of its own).

## 6. Outbound send shapes & retry/backoff

- `OutboundMessage` sealed family: `TextMessage(to, body)`,
  `InteractiveListMessage(to, header, bodyText, List<ListSection>)` (a
  section has a title and rows of `{id, title, description}` — the WhatsApp
  interactive-list JSON shape), `TemplateMessage(to, templateKey, List<TemplateParam>)`
  — `templateKey` is resolved through `WhatsAppTemplateCatalog` to the actual
  Meta template name + language, so callers never hard-code Meta's naming.
- `SendResult`: `Sent(providerMessageId)` or `Failed(errorCode, message,
  retryable)`. The Graph API returns 4xx for permanent problems (invalid
  recipient, template not found, re-engagement window closed) and 5xx/network
  errors for transient ones; `WhatsAppMessagingChannel` maps HTTP status to
  `retryable` (5xx/network → true, 4xx → false).
- **Backoff**: `outbox_message` gains `next_attempt_at` (additive). `markRetry
  (id, error, nextAttemptAt)` sets `status = PENDING`, records the error, and
  sets `next_attempt_at = now + backoff(attempts)` — `backoff(n) =
  min(2^n seconds, 5 minutes)`, a simple capped exponential curve, no new
  infrastructure. `claimNextPending()` (SPEC-008, extended) filters
  `next_attempt_at IS NULL OR next_attempt_at <= NOW()`. After a configured
  max attempts (e.g. 5), `markFailed` instead of `markRetry` — no infinite
  retry (SC-007).
- `provider_message_id` (additive column on `outbox_message`) is set on a
  successful send (FR-013).

## 7. Reliability (US6) & the reclaim sweep

SPEC-008's primitives already satisfy the spec's literal US6 acceptance
scenarios: a `NEW` inbound row or a `PENDING` outbox row that was never
claimed is trivially still claimable after a restart — nothing has to change.

The gap they leave — a row `CLAIMED` by a worker that then crashes before
`markProcessed`/`markFailed` (or `markSent`/`markRetry`/`markFailed`) — is a
known limitation of a bare claim-and-mark pattern, not something the spec's
acceptance scenarios exercise, but it is the honest reading of ADR-0015's "every
pipeline step must be idempotent." **Decision**: add a small, additive, P2
improvement rather than leave it silently unaddressed:

- `InboxStore.reclaimStale(Duration olderThan)` / `OutboxStore.reclaimStale(...)`
  — `UPDATE … SET status = 'NEW' WHERE status = 'CLAIMED' AND received_at <
  (NOW() - olderThan) AND attempts < :maxAttempts`; over the attempt cap,
  `markFailed` instead. Uses the existing `received_at` / `created_at` as the
  staleness clock (an approximation — good enough for a worker doing a cheap
  echo reply; SPEC-015's guarded LLM pipeline may want a real `claimed_at`
  column when that work lands, noted for later, not built now).
- Called once per worker/sender tick, before claiming new work.

## 8. Test strategy

- **Pure logic** — `WebhookSignatureVerifierTest`, `WebhookVerificationHandshakeTest`,
  `InboundPayloadParserTest` (fixture JSON files: one message, a batch, a
  status callback, an unsupported type), `WindowPolicyTest`,
  `WhatsAppTemplateCatalogTest`. No network, no container.
- **`WhatsAppMessagingChannel`** — `org.wiremock:wiremock` `3.13.1` (pinned;
  Boot's BOM does not manage it) stubs the Graph API `/messages` endpoint for
  each `OutboundMessage` shape and for 4xx/5xx responses.
- **Webhook controller** — `MockMvc`, asserting the signature/handshake rules
  end-to-end at the HTTP layer (no container needed if the store is mocked;
  an `*IT` variant runs the real thing against Testcontainers `mysql:8.4`,
  SPEC-008's `PersistenceIT`).
- **Worker / sender** — `*IT` against `PersistenceIT` + WireMock; the full
  round trip (SC-005) drives a fixture inbound payload through the real
  controller, worker, and sender to a WireMock-stubbed Graph API and asserts
  exactly one send.
- No live WhatsApp number or Meta account anywhere in the test suite or CI
  (FR-014).

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | Module split | protocol/computation in `channel-whatsapp`; HTTP endpoint + scheduled loops + storage glue in `agent` |
| 2 | Raw body | `@RequestBody byte[]`, verify before any JSON parsing |
| 3 | Signature / handshake | HMAC-SHA256 + `MessageDigest.isEqual`; fail-closed on any error |
| 4 | Payload shape | walk every `messages[]`; non-message payloads → zero entries, not an error; `rawRef` = one message's JSON |
| 5 | Window | `lastInboundAt + 24h`, half-open; stored on `app_user` via a new `WhatsAppWindowStore` |
| 6 | Outbound + backoff | 3 message shapes; `next_attempt_at` capped-exponential backoff; `provider_message_id` recorded on success |
| 7 | Reliability | literal US6 scenarios already satisfied by SPEC-008; a bounded reclaim sweep added as a deliberate P2 improvement |
| 8 | Tests | pure unit tests + WireMock + `PersistenceIT`; zero dependency on a live WhatsApp number |
