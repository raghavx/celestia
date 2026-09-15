# Phase 1 Data Model: WhatsApp Channel (SPEC-009)

## `com.celestia.channel.whatsapp` value types

### `OutboundMessage` (sealed)

```java
sealed interface OutboundMessage permits TextMessage, InteractiveListMessage, TemplateMessage {
    String to();   // E.164
}
record TextMessage(String to, String body) implements OutboundMessage {}
record InteractiveListMessage(String to, String header, String bodyText,
                               List<ListSection> sections) implements OutboundMessage {}
record ListSection(String title, List<ListRow> rows) {}
record ListRow(String id, String title, String description) {}
record TemplateMessage(String to, String templateKey,
                        List<TemplateParam> params) implements OutboundMessage {}
record TemplateParam(String name, String value) {}
```

Compact ctors validate: `to` non-blank; `body`/`bodyText` non-blank;
`sections`/`rows` non-empty; `templateKey` non-blank.

### `SendResult` (sealed)

```java
sealed interface SendResult permits Sent, Failed {}
record Sent(String providerMessageId) implements SendResult {}
record Failed(String errorCode, String message, boolean retryable) implements SendResult {}
```

### `InboundMessage`

```java
record InboundMessage(String providerId, String fromE164, Instant timestamp, MessageContent content) {}
sealed interface MessageContent permits TextContent, UnsupportedContent {}
record TextContent(String body) implements MessageContent {}
record UnsupportedContent(String type) implements MessageContent {}
```

### Ports / pure services

```java
interface MessagingChannel {
    SendResult send(OutboundMessage message);
}

final class WebhookVerificationHandshake {
    static Optional<String> challenge(String mode, String token, String challenge, String expectedToken);
}

final class WebhookSignatureVerifier {
    static boolean valid(byte[] rawBody, String signatureHeader, String appSecret);  // fail-closed
}

final class InboundPayloadParser {
    static List<InboundMessage> parseAll(byte[] rawBody);   // walks every messages[]; non-message -> []
    static InboundMessage parseOne(String messageJson);      // re-parse a single stored message
}

final class WindowPolicy {
    static Instant windowExpiresAt(Instant lastInboundAt);   // + 24h
    static boolean isWithinWindow(Instant now, Instant expiresAt);  // half-open: now < expiresAt
}

final class WhatsAppTemplateCatalog {
    Optional<ResolvedTemplate> resolve(String templateKey);  // -> Meta template name + language
}
record ResolvedTemplate(String metaTemplateName, String languageCode) {}
```

`WhatsAppConfig` (`@ConfigurationProperties("whatsapp")`): `phoneNumberId`,
`graphApiBaseUrl`, `graphApiVersion`, `accessToken`, `appSecret`, `verifyToken`
— all from environment variables, never literal (FR-012).

## `agent` — channel orchestration

```java
interface ReplyHandler {
    OutboundMessage reply(AppUserRef user, InboundMessage inbound);
}

final class EchoReplyHandler implements ReplyHandler { /* v1: a canned acknowledgement */ }
```

`WhatsAppInboundWorker` (`@Scheduled`): reclaim stale → loop
`inboxStore.claimNext()` → `userAccountService.findOrCreate(inbound.fromE164())`
→ `windowStore.recordInbound(user.id(), inbound.timestamp())` →
`replyHandler.reply(user, inbound)` → `outboxStore.enqueue(user.id(),
inbound.fromE164(), serialise(outboundMessage))` → `inboxStore.markProcessed(id)`;
any exception → `inboxStore.markFailed(id, error)`.

`WhatsAppOutboundSender` (`@Scheduled`): reclaim stale → loop
`outboxStore.claimNextPending()` → deserialise the queued `OutboundMessage` →
`windowStore.windowExpiresAt(userId)` → `WindowPolicy.isWithinWindow` decides
free-form vs. wrapping as a `TemplateMessage` (the catalog's generic-reply
template) → `messagingChannel.send(...)` → `Sent` → `outboxStore.markSent(id,
providerMessageId)`; `Failed(retryable=true)` → `outboxStore.markRetry(id,
error, nextAttemptAt)`; `Failed(retryable=false)` or attempts exhausted →
`outboxStore.markFailed(id, error)`.

## `V7__whatsapp_channel.sql` (additive)

| Table | New column | Notes |
|---|---|---|
| `app_user` | `window_expires_at DATETIME(0) NULL` | the 24h customer-service window |
| `outbox_message` | `provider_message_id VARCHAR(64) NULL` | set on a successful send (FR-013) |
| `outbox_message` | `next_attempt_at DATETIME(0) NULL` | the retry-backoff gate |

No new table. `dasha_period`, `conversation` remain out of scope (SPEC-013 /
SPEC-010 respectively).

## Store contract additions (SPEC-008, extended)

```java
// InboxStore — additive
Optional<ClaimedEvent> claimNext();                                  // unchanged
int reclaimStale(Duration olderThan, int maxAttempts);                // new: NEW <- CLAIMED, else markFailed over cap

// OutboxStore — additive
Optional<ClaimedMessage> claimNextPending();                          // now also gates on next_attempt_at
void markSent(long id, String providerMessageId);                     // was markSent(id); now records the id
void markRetry(long id, String error, Instant nextAttemptAt);         // new: PENDING again, gated
int reclaimStale(Duration olderThan, int maxAttempts);                // new

// new
final class WhatsAppWindowStore {
    void recordInbound(long userId, Instant when);      // sets window_expires_at = WindowPolicy.windowExpiresAt(when)
    Optional<Instant> windowExpiresAt(long userId);
}
```

## Invariants (tested)

1. `WebhookSignatureVerifier.valid` is `false` for a tampered body, a wrong
   secret, a missing header, or a malformed header — never throws.
2. `WebhookVerificationHandshake.challenge` returns the challenge iff both
   `mode == "subscribe"` and the token matches; otherwise empty.
3. `InboundPayloadParser.parseAll` on a batch of N messages returns exactly N
   `InboundMessage`s, each independently re-parseable from its own `rawRef`
   via `parseOne`; on a status-callback payload it returns `[]`.
4. `WindowPolicy.isWithinWindow(expiresAt, expiresAt)` is `false` (half-open);
   `isWithinWindow(expiresAt.minusSeconds(1), expiresAt)` is `true`.
5. A `WhatsAppInboundWorker` run on N claimed messages (including unsupported
   types) queues exactly N outbound replies and marks all N processed — no
   silent drop, no double-processing.
6. A `WhatsAppOutboundSender` run against a Graph API that fails transiently
   `k < maxAttempts` times then succeeds performs exactly one successful send;
   against one that always fails, it stops at `maxAttempts` and marks failed.
7. No class outside `com.celestia.channel.whatsapp` depends on
   `com.celestia.channel.whatsapp.graphapi` (ArchUnit).
