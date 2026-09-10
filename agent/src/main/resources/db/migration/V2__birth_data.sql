-- SPEC-008 — the SPEC-007 ResolvedBirth persisted. One row per user.
-- PII: name / place. Removed by "forget me" (FR-010, ADR-0016).

CREATE TABLE birth_data (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    name             VARCHAR(120)     NULL,
    place_query      VARCHAR(255) NOT NULL,
    birth_date       DATE         NOT NULL,
    birth_time       TIME             NULL,
    birth_time_known TINYINT(1)   NOT NULL,
    place_name       VARCHAR(255) NOT NULL,
    country          VARCHAR(120) NOT NULL DEFAULT '',
    lat              DECIMAL(9,6) NOT NULL,
    lon              DECIMAL(9,6) NOT NULL,
    zone_id          VARCHAR(64)  NOT NULL,
    -- DATETIME, not TIMESTAMP: TIMESTAMP is 1970-2038; births predate 1970.
    -- Hibernate maps Instant to this via jdbc.time_zone=UTC.
    birth_utc        DATETIME(0)  NOT NULL,
    offset_applied   VARCHAR(16)  NOT NULL,
    geocode_source   VARCHAR(32)  NOT NULL,
    stated_zone_id   VARCHAR(64)      NULL,
    flags            VARCHAR(255) NOT NULL DEFAULT '',
    tzdb_version     VARCHAR(32)  NOT NULL,
    tz_boundary_version VARCHAR(32) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_birth_data_user UNIQUE (user_id),
    CONSTRAINT fk_birth_data_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
