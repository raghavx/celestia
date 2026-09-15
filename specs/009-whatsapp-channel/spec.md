# Feature Specification: WhatsApp Channel (SPEC-009)

**Feature Branch**: `009-whatsapp-channel`

**Created**: 2026-09-15

**Status**: Draft

**Input**: PROJECT_PLAN.md — "SPEC-009 WhatsApp channel: webhook GET
verification, POST signature verification, payload parsing, idempotency, async
enqueue, Graph API sender, 24h-window + template handling. Depends on: 008."
Phase 4 exit: "a message sent from a real WhatsApp number round-trips to a
canned echo reply; spoofed/invalid-signature payloads are rejected." ADR-0011
(WhatsApp provider, *proposed*), ADR-0012 (number + templates, *proposed*),
ADR-0015 (async processing, *accepted* — "the webhook … does an idempotent
insert … and returns 200. A worker claims rows, runs the guarded pipeline,
writes replies to an outbox, and a sender drains the outbox").

_Every conversation with the agent starts and ends on WhatsApp. This feature is
the front door: verifying Meta's webhook handshake, authenticating every inbound
delivery, recording it exactly once even under retries, handing it to a worker
off the request thread, and sending the reply back through the Graph API —
choosing a free-form message inside the 24-hour customer-service window and a
pre-approved template outside it. What the reply **says** is not this spec's
concern — v1 sends a canned acknowledgement to prove the round trip; SPEC-010's
onboarding FSM and SPEC-014/015's guarded LLM pipeline plug into the same worker
slot later without touching the channel plumbing._

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Webhook verification handshake (Priority: P1)

Meta calls `GET /webhooks/whatsapp` with `hub.mode=subscribe`,
`hub.verify_token`, and `hub.challenge` when the webhook is registered. The
endpoint echoes `hub.challenge` back only when the token matches the configured
secret.

**Why this priority**: Nothing else works until Meta can register the webhook.

**Independent Test**: A valid handshake returns the challenge; a wrong token or
a missing parameter returns a rejection with no challenge leaked.

**Acceptance Scenarios**:

1. **Given** `hub.mode=subscribe` and the correct `hub.verify_token`, **When**
   Meta calls the endpoint, **Then** the response is `200` with the body set to
   exactly `hub.challenge`.
2. **Given** an incorrect `hub.verify_token`, **When** the endpoint is called,
   **Then** the response is a rejection and the challenge value is not echoed.
3. **Given** `hub.mode` is anything other than `subscribe`, **When** the
   endpoint is called, **Then** the response is a rejection.

---

### User Story 2 - Secure, idempotent inbound intake (Priority: P1)

`POST /webhooks/whatsapp` verifies the `X-Hub-Signature-256` of the **raw**
request body against the app secret before parsing anything. A validly-signed
delivery is parsed — a single delivery may batch several messages — and each
message is recorded exactly once, keyed on its WhatsApp message id, even if
Meta redelivers the same payload. The response is fast: no reply-generation
work runs on the request thread.

**Why this priority**: This is the security perimeter (webhook spoofing) and
the reliability perimeter (Meta disables webhooks that are slow or error) in
one place — the Phase 4 exit criterion names both.

**Independent Test**: Tampered or missing signatures never reach the parser or
the database; a replayed delivery yields the same one recorded row; the
response returns well under Meta's timeout with the worker fully decoupled.

**Acceptance Scenarios**:

1. **Given** a payload with a valid signature, **When** it is posted, **Then**
   each message inside it is recorded and the response is `200` within budget.
2. **Given** a payload with a tampered or missing signature, **When** it is
   posted, **Then** the request is rejected and **nothing** is recorded.
3. **Given** a delivery already recorded (Meta retried it), **When** it is
   posted again, **Then** the response is still `200` and no second record is
   created.
4. **Given** a delivery batching three messages, **When** it is posted,
   **Then** all three are recorded independently.

---

### User Story 3 - Async processing off the webhook thread (Priority: P1)

A worker claims recorded-but-unprocessed inbound messages, one at a time per
message, and hands each to a reply handler. v1's handler resolves the sender's
identity and produces a **canned acknowledgement** — proving the pipeline
end-to-end without yet having an onboarding FSM or an LLM. An unrecognised
message type (image, location, …) gets a graceful fallback reply, not a crash.

**Why this priority**: ADR-0015's worker half; decouples "recorded" from
"replied to" so a slow or failing reply path never blocks the webhook.

**Independent Test**: A recorded message is claimed and processed exactly once;
a message of an unsupported type still produces a reply, not an error.

**Acceptance Scenarios**:

1. **Given** a recorded text message, **When** the worker runs, **Then** the
   sender is resolved (or created) and a canned reply is queued for delivery.
2. **Given** a recorded message of an unsupported type, **When** the worker
   runs, **Then** a graceful fallback reply is queued — the worker does not
   error or skip it silently.
3. **Given** two workers running concurrently, **When** both look for work,
   **Then** no message is claimed by both.

---

### User Story 4 - Outbound send via the Graph API, respecting the 24h window (Priority: P1)

A sender drains queued replies and delivers them through the WhatsApp Graph
API. For each recipient it knows whether the 24-hour customer-service window
(opened by their most recent inbound message) is still open: inside the window
it sends free-form; outside it, only a pre-approved template. Transient
failures are retried with backoff; permanent failures are not retried.

**Why this priority**: Completes the round trip named in the Phase 4 exit
criterion, and is the one Meta-imposed rule (ADR-0012) that silently breaks
re-engagement messaging if ignored.

**Independent Test**: For a fixture set of window states, the sender picks
free-form or template exactly as documented; a simulated failing send retries
bounded, then gives up cleanly.

**Acceptance Scenarios**:

1. **Given** a queued reply for a user who messaged within the last 24 hours,
   **When** the sender runs, **Then** it sends a free-form message.
2. **Given** a queued reply for a user whose window has expired, **When** the
   sender runs, **Then** it sends a template message, not free-form.
3. **Given** a send that fails with a transient error, **When** the sender
   retries, **Then** it backs off and eventually succeeds or is marked failed
   after a bounded number of attempts — never retried forever.
4. **Given** a send that fails with a permanent error (e.g. invalid
   recipient), **When** the sender handles it, **Then** it is marked failed
   immediately, no retry.

---

### User Story 5 - Interactive messages (Priority: P2)

The sender can deliver an interactive list or button message (options + a
title), not just plain text — the shape later specs need for a place-candidate
picker (SPEC-010) or a plan picker (SPEC-013).

**Why this priority**: Nothing in SPEC-009 itself needs to *show* a list, but
building and testing the capability now means those specs add no channel code.

**Independent Test**: A fixture interactive message round-trips through the
sender to a WireMock Graph API with the documented JSON shape.

**Acceptance Scenarios**:

1. **Given** a list of options with a title, **When** the sender is asked to
   deliver it as an interactive message, **Then** the Graph API request has the
   documented interactive-list shape.

---

### User Story 6 - Reliability under retries (Priority: P2)

The system behaves correctly under WhatsApp's at-least-once delivery model: a
crash between recording and processing never loses a message; a crash between
producing and sending a reply never silently drops it (a duplicate reply is an
accepted, documented risk — never a lost one).

**Independent Test**: Killing the worker mid-run and restarting it still
processes the message; the same is true for the sender.

**Acceptance Scenarios**:

1. **Given** a recorded but unclaimed message, **When** the worker restarts,
   **Then** the message is still claimable and gets processed.
2. **Given** a queued-but-unsent reply, **When** the sender restarts, **Then**
   the reply is still sent.

---

### Edge Cases

- Meta re-sends the GET verification request (setup retry) → idempotent, same
  answer every time.
- A webhook payload that is not a recognised message shape (a status callback,
  a test ping Meta sends) → accepted (`200`) and safely ignored, not an error.
- A batched delivery where one message is malformed → the well-formed messages
  in the batch are still recorded.
- The customer-service window boundary at exactly 24h00m → a documented
  half-open convention (inside vs. outside is unambiguous).
- A worker or sender process restarts mid-batch → SPEC-008's claim primitive
  makes an in-flight item reclaimable, not stuck forever.
- The Graph API access token expires or is revoked → sends fail permanently
  (4xx/401) and are marked failed with a clear error, not retried forever;
  this is an operational alert, out of scope for this spec's behaviour.
- A message from a phone number already "forgotten" (SPEC-008 `forget me`) →
  treated as a new user (the number no longer matches any account).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `GET /webhooks/whatsapp` MUST verify `hub.mode == "subscribe"`
  and `hub.verify_token` against the configured secret; on a match it MUST
  return `200` with the body set to exactly `hub.challenge`; otherwise it MUST
  reject the request without echoing the challenge.
- **FR-002**: `POST /webhooks/whatsapp` MUST verify the `X-Hub-Signature-256`
  HMAC-SHA256 of the **raw, unparsed** request body against the configured app
  secret, using a **constant-time comparison**, before any parsing or storage.
  A mismatch MUST reject the request and record nothing.
- **FR-003**: A validly-signed delivery MUST be parsed and every message it
  contains MUST be recorded **exactly once**, keyed on the provider's message
  id, even across repeated deliveries of the same payload or across a batch
  containing several messages.
- **FR-004**: The webhook response MUST NOT wait on reply generation, delivery
  sending, or any other work beyond the idempotent record; it returns promptly
  once the record succeeds.
- **FR-005**: An async worker MUST claim a recorded-but-unprocessed message
  such that no two concurrent workers claim the same one.
- **FR-006**: The v1 reply handler MUST resolve the sender's identity
  (find-or-create by phone) and produce a reply for every claimed message,
  including messages of a type it does not specifically understand (a graceful
  fallback reply, never a silent drop or a crash).
- **FR-007**: A produced reply MUST be queued for delivery through the same
  outbound path regardless of which handler produced it (v1's canned echo,
  or a future FSM/LLM).
- **FR-008**: The outbound sender MUST determine, per recipient, whether the
  24-hour customer-service window (from their most recent inbound message) is
  open, and MUST send free-form inside the window and a template outside it.
- **FR-009**: The outbound sender MUST support at least three message shapes:
  free-form text, an interactive list/button message, and a named template
  with parameters.
- **FR-010**: A transient send failure MUST be retried with backoff up to a
  bounded attempt count and then marked failed; a permanent failure MUST be
  marked failed immediately with no retry.
- **FR-011**: The messaging capability MUST be exposed behind a
  provider-agnostic port; no pure domain module (`ephemeris`, `core`, `geo`,
  `billing`, `guardrail`) and no orchestration code outside the channel adapter
  MUST reference a WhatsApp/Graph-API type directly (Constitution IX).
- **FR-012**: Webhook verify token, app secret, and Graph API access token
  MUST come from the environment/secrets manager, MUST NOT be logged, and
  MUST NOT appear in a committed file.
- **FR-013**: Every inbound message MUST be traceable by its provider message
  id; every successfully sent outbound message MUST record the provider's
  assigned message id.
- **FR-014**: The signature check, the 24h-window decision, and the
  idempotent-record rule MUST each be independently testable without a live
  WhatsApp number or a real Meta account (fixtures / a stubbed Graph API).
- **FR-015**: A webhook payload that is not a recognised WhatsApp Cloud API
  message shape MUST be accepted (`200`) and safely ignored.
- **FR-016**: The full round trip — an inbound text message results in exactly
  one outbound reply — MUST be demonstrable end-to-end using only this spec's
  code (Phase 4 exit criterion).
- **FR-017**: A duplicate delivery of an already-recorded message id MUST NOT
  produce a second reply.

### Key Entities *(include if data involved)*

- **Inbound message**: a provider message id, the sender's phone, the message
  type and content, the recorded/claimed/processed state — persisted via
  SPEC-008's idempotent inbound store.
- **Outbound message**: a target, a message shape (text / interactive /
  template) and payload, the send state and attempt/error tracking —
  persisted via SPEC-008's outbox store.
- **Customer-service window**: per user, the instant the current 24-hour
  free-form window expires, derived from their most recent inbound message.
- **`MessagingChannel`**: the provider-agnostic port — send a message shape to
  a recipient; the WhatsApp Graph API adapter is the v1 (and, for now, only)
  implementation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A set of GET verification requests (valid token, wrong token,
  missing/wrong `hub.mode`) each get the documented response, 100%.
- **SC-002**: A set of POST payloads with tampered or missing signatures are
  rejected before any row is written, 100%; validly-signed payloads are
  recorded, 100%.
- **SC-003**: The webhook responds in well under Meta's timeout (target: under
  1 second) for a batch of up to 10 messages in one delivery, with the worker
  fully decoupled from the request.
- **SC-004**: Replaying the same delivery N times produces exactly one
  recorded message and, once processed, zero additional replies.
- **SC-005**: An end-to-end test (webhook POST → worker → outbox → a stubbed
  Graph API) delivers exactly one reply for one inbound message.
- **SC-006**: For a fixture set of window states (just-messaged, 23h59m,
  24h00m, 30h since last inbound), the sender picks free-form/template exactly
  as documented, 100%.
- **SC-007**: A simulated Graph API that fails transiently a bounded number of
  times then succeeds results in exactly one successful send; a persistently
  failing one stops retrying after the bounded attempt count and is marked
  failed, never retried forever.

## Assumptions

- **v1's reply is a canned acknowledgement**, not the real product. It proves
  the channel plumbing end-to-end; SPEC-010 replaces the handler with the
  onboarding FSM, and SPEC-014/015 route it through the guardrail + LLM
  pipeline instead. No entitlement check or guardrail runs on the echo path —
  those gate the *real* pipeline, which does not exist yet.
- **The `MessagingChannel` port and its WhatsApp adapter both live in
  `channel-whatsapp`** for v1. `agent` depends on `channel-whatsapp`, not the
  reverse, so the interface cannot live in `agent` without a dependency cycle.
  If a second channel is ever added, promoting the interface + shared value
  types to their own module is a mechanical, behaviour-free move.
- **Where the 24h-window expiry and the outbound message-id are stored** (a
  new column, a new table) is a `/speckit-plan` decision, not fixed here;
  SPEC-008's `inbound_event` / `outbox_message` are the likely landing spot for
  most of it, since ADR-0015 already assigned the async schema to SPEC-008.
- **The `conversation` table (FSM `mode`/`state`/`context_json`) is out of
  scope** — it is dead schema without SPEC-010's onboarding FSM, which owns it.
- **Template content and Meta's approval process are external** (ADR-0012);
  this spec builds and tests the send capability and the window decision
  against a stub/fixture template, not a live approved one.
- **Delivery-status callbacks** (sent/delivered/read receipts) and rich
  message types beyond text/interactive/template (location, media, button
  *replies*) are **out of scope** for v1.
- **Outbound delivery is at-least-once.** A crash between "Graph API accepted
  the send" and "marked sent" can produce a rare duplicate reply — an accepted,
  documented v1 risk, not a data-loss risk.
- **Meta Business verification, number registration, and webhook app setup**
  are external, long-lead-time operational tasks (ADR-0011/0012) tracked
  outside this spec; the code here is verified against the documented Graph
  API contract with a stubbed provider (e.g. WireMock) in CI, not a live number.

## Dependencies

- **SPEC-008** — `InboxStore` (idempotent record + claim) and `OutboxStore`
  (queue + claim + mark sent/failed), `UserAccountService.findOrCreate`.
- **ADRs** — ADR-0011 (WhatsApp provider) and ADR-0012 (number + templates),
  both to be moved to **accepted** in planning; ADR-0015 (async, accepted —
  this spec is its worker/sender half).
- **Consumed by** SPEC-010 (onboarding FSM replaces the echo handler),
  SPEC-013 (subscribe-and-pay renders plans via the interactive-list capability
  and sends renewal reminders via template), SPEC-014/015 (guardrail + LLM
  pipeline plug into the same worker slot), SPEC-017 (daily prediction push via
  template).
