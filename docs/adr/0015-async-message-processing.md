# ADR 0015: Asynchronous message processing

- **Status:** accepted
- **Date:** 2026-09-02
- **Decision ref:** D15 (PROJECT_PLAN.md §2)

## Context

Meta requires the webhook to return `200` within ~5 seconds or it retries and
eventually disables the endpoint. An LLM turn plus tool calls takes seconds to
tens of seconds. We also need idempotency (Meta retries deliveries) and resilience
across application restarts.

## Options considered

1. In-DB inbox/outbox tables plus a polling worker (`@Scheduled`, with `ShedLock`
   or single-writer for multi-instance).
2. An external broker (RabbitMQ / Redis Streams / SQS).
3. In-JVM executor only — loses messages on crash. Rejected.

## Decision

Option 1 for v1. The webhook verifies the signature, does an **idempotent** insert
into `inbound_event` (unique on `wa_message_id`), and returns `200`. A worker
claims rows, runs the guarded pipeline, writes replies to an outbox, and a sender
drains the outbox with retry/backoff. Move to Option 2 only if throughput demands
it.

## Consequences

- `inbound_event` and outbox schema are part of SPEC-008.
- Scaling out needs `ShedLock` or a single-writer guarantee.
- Delivery is at-least-once, so every pipeline step must be idempotent.
