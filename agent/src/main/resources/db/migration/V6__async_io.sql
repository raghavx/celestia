-- SPEC-008 — the async inbox/outbox tables (ADR-0015). At-least-once delivery:
-- every operation is idempotent. The webhook and the worker loop are SPEC-009.

CREATE TABLE inbound_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    wa_message_id VARCHAR(128) NOT NULL,
    raw_ref       VARCHAR(255) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'NEW',   -- NEW | CLAIMED | DONE | FAILED
    attempts      INT          NOT NULL DEFAULT 0,
    error         VARCHAR(500)     NULL,
    received_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at  TIMESTAMP        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_inbound_event_wa_message_id UNIQUE (wa_message_id),
    INDEX ix_inbound_event_status_received (status, received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT           NULL,
    target       VARCHAR(64)  NOT NULL,
    payload_json JSON         NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    attempts     INT          NOT NULL DEFAULT 0,
    error        VARCHAR(500)     NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_outbox_message_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    INDEX ix_outbox_message_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
