-- SPEC-008 — the persistent implementation of SPEC-007's geo.GeocodeCache port.
-- Keyed on the normalised query; a hit costs nothing and stays deterministic
-- (ADR-0013).

CREATE TABLE geocode_cache (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    query_norm   VARCHAR(255) NOT NULL,
    results_json JSON         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_geocode_cache_query_norm UNIQUE (query_norm)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
