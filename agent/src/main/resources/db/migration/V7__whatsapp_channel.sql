-- SPEC-009 — the WhatsApp channel. Additive: the 24h customer-service window,
-- the outbound provider-message-id and retry-backoff gate. Plus one widen.

ALTER TABLE app_user
    ADD COLUMN window_expires_at DATETIME(0) NULL AFTER cooldown_until;

ALTER TABLE outbox_message
    ADD COLUMN provider_message_id VARCHAR(64) NULL AFTER payload_json,
    ADD COLUMN next_attempt_at     DATETIME(0)  NULL AFTER attempts;

-- SPEC-008 sized raw_ref for "a pointer to a raw payload" (a few dozen chars).
-- SPEC-009 stores the inbound message's JSON directly (research.md §4) so the
-- worker can re-parse it without a separate blob store; a WhatsApp text body
-- runs to 4096 characters, well past the original VARCHAR(255).
ALTER TABLE inbound_event
    MODIFY COLUMN raw_ref TEXT NOT NULL;
