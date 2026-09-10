-- SPEC-008 — the daily-prediction cache. Keyed on (user, date, kind);
-- recomputed on an engine_version change (FR-007).

CREATE TABLE prediction (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    for_date       DATE         NOT NULL,
    kind           VARCHAR(24)  NOT NULL,           -- DAILY
    engine_version VARCHAR(128) NOT NULL,
    payload_json   JSON         NOT NULL,
    model          VARCHAR(64)      NULL,           -- reserved: the LLM phrasing model (SPEC-017)
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_prediction_user_date_kind UNIQUE (user_id, for_date, kind),
    CONSTRAINT fk_prediction_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
