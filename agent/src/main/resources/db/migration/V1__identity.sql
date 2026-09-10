-- SPEC-008 — identity. The verified WhatsApp phone number is the identity
-- (Constitution VII). Every other table hangs off app_user.

CREATE TABLE app_user (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    -- NULL after "forget me" (the number is gone); MySQL allows many NULLs under
    -- a UNIQUE key, so a re-contact from the same number creates a fresh user.
    phone_e164        VARCHAR(20)      NULL,
    -- SHA-256 hex of the E.164, kept after "forget me" (ADR-0016); a pepper is a
    -- SPEC-016 hardening.
    phone_hash        CHAR(64)         NULL,
    wa_id             VARCHAR(32)      NULL,
    display_name      VARCHAR(120)     NULL,
    locale            VARCHAR(16)      NULL,
    consent_at        TIMESTAMP        NULL,
    onboarding_state  VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    guardrail_strikes INT          NOT NULL DEFAULT 0,
    cooldown_until    TIMESTAMP        NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_app_user_phone_e164 UNIQUE (phone_e164)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
