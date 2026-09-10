-- SPEC-008 — the engine-versioned chart cache (Constitution III). A stored row
-- whose engine_version != the current one is recomputed on read (FR-005).

CREATE TABLE chart (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT           NULL,
    kind           VARCHAR(16)  NOT NULL,           -- NATAL | HORARY
    ayanamsa       VARCHAR(32)  NOT NULL,
    house_system   VARCHAR(16)  NOT NULL,
    engine_version VARCHAR(128) NOT NULL,
    computed_json  JSON         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_chart_user_kind UNIQUE (user_id, kind),
    CONSTRAINT fk_chart_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Horary question metadata; one row per horary chart.
CREATE TABLE horary_query (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    chart_id     BIGINT       NOT NULL,
    number_1_249 SMALLINT     NOT NULL,
    question     VARCHAR(1000)    NULL,
    judged_utc   DATETIME(0)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_horary_query_chart UNIQUE (chart_id),
    CONSTRAINT fk_horary_query_chart FOREIGN KEY (chart_id) REFERENCES chart (id) ON DELETE CASCADE,
    CONSTRAINT ck_horary_number CHECK (number_1_249 BETWEEN 1 AND 249)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Flattened dasha projection for the range queries SPEC-013 / SPEC-015 will run.
-- SPEC-008 CREATES the table (stable FK, no later chart-touching migration) but
-- does NOT write it — ChartCacheService returns the DashaTimeline from
-- chart.computed_json. SPEC-013 owns population. "Forget me" reaches these rows
-- via the chart FK cascade.
CREATE TABLE dasha_period (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    chart_id  BIGINT       NOT NULL,
    level     VARCHAR(16)  NOT NULL,               -- MAHADASHA .. PRANA
    lord      VARCHAR(12)  NOT NULL,
    start_utc DATETIME(0)  NOT NULL,
    end_utc   DATETIME(0)  NOT NULL,
    parent_id BIGINT           NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dasha_period_chart FOREIGN KEY (chart_id) REFERENCES chart (id) ON DELETE CASCADE,
    CONSTRAINT fk_dasha_period_parent FOREIGN KEY (parent_id) REFERENCES dasha_period (id) ON DELETE CASCADE,
    INDEX ix_dasha_period_chart_level (chart_id, level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
