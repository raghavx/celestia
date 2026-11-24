-- SPEC-012 — the owner-preview agent's turn record. One row per turn, where a
-- turn is a message that acquired the turn permit (FR-010c). Messages stopped
-- before that point write nothing here; they are counted instead.
--
-- PII: `question` is a message body and `delivered_reply` is derived from one
-- (Constitution VII). Both are removed by "forget me" (FR-020a) and purged past
-- the retention age (FR-020b). Both are sized VARCHAR rather than TEXT so the
-- deferred column-encryption pass can reach them without a schema change
-- (FR-020c) — the position SPEC-008 took for birth_data.name / place_query.
--
-- NOT stored: a second person's birth data. `tool_calls_json` redacts the
-- arguments of castChartFor, because FR-011 forbids persisting that data at all.

CREATE TABLE owner_turn_record (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    -- DATETIME, not TIMESTAMP: consistent with birth_data.birth_utc, and the
    -- purge compares against an injected Clock rather than the server's.
    received_at           DATETIME(0)   NOT NULL,
    question              VARCHAR(4096) NOT NULL,
    delivered_reply       VARCHAR(4096)     NULL,
    entitlement           VARCHAR(16)   NOT NULL,
    gate_outcome          VARCHAR(32)       NULL,
    gate_reason           VARCHAR(32)       NULL,
    outcome               VARCHAR(32)   NOT NULL,
    sensitivity_used      VARCHAR(16)   NOT NULL,
    delivered_length      INT           NOT NULL DEFAULT 0,
    fault_kind            VARCHAR(32)       NULL,
    -- Which model answered. A practitioner's verdict on an answer is meaningless
    -- without it, and early on the fallback IS the normal path (ADR-0009).
    model_used            VARCHAR(64)       NULL,
    fallback_switched     TINYINT(1)    NOT NULL DEFAULT 0,
    withheld_family       VARCHAR(32)       NULL,
    reply_trimmed         TINYINT(1)    NOT NULL DEFAULT 0,
    tool_calls_json       LONGTEXT      NOT NULL,
    resolved_sources_json LONGTEXT      NOT NULL,
    PRIMARY KEY (id),
    KEY ix_owner_turn_record_user (user_id),
    KEY ix_owner_turn_record_received (received_at),
    CONSTRAINT fk_owner_turn_record_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
