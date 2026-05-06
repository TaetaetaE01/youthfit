-- ingestion_run_log: receivePolicy 1회 = 1 row (Spec 5)
CREATE TABLE ingestion_run_log (
    id                          BIGSERIAL PRIMARY KEY,
    source                      VARCHAR(40) NOT NULL,
    received_count              INT NOT NULL DEFAULT 0,
    normalized_success_count    INT NOT NULL DEFAULT 0,
    normalized_failure_count    INT NOT NULL DEFAULT 0,
    duplicate_count             INT NOT NULL DEFAULT 0,
    received_at                 TIMESTAMP NOT NULL,
    processed_at                TIMESTAMP NOT NULL,
    duration_ms                 INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP NOT NULL
);
CREATE INDEX idx_ingestion_run_log_received_at ON ingestion_run_log (received_at DESC);
CREATE INDEX idx_ingestion_run_log_source_received_at ON ingestion_run_log (source, received_at DESC);

COMMENT ON TABLE ingestion_run_log IS 'IngestionService.receivePolicy 호출 단위 집계 (Spec 5)';

-- ingestion_item_failure: 정규화 실패 단건 (Spec 5)
CREATE TABLE ingestion_item_failure (
    id                  BIGSERIAL PRIMARY KEY,
    run_log_id          BIGINT,
    source              VARCHAR(40) NOT NULL,
    source_item_id      VARCHAR(120),
    raw_payload         JSONB,
    raw_payload_hash    VARCHAR(64),
    failure_reason      VARCHAR(30) NOT NULL,
    error_message       TEXT,
    retry_count         INT NOT NULL DEFAULT 0,
    last_retried_at     TIMESTAMP,
    created_at          TIMESTAMP NOT NULL
);
CREATE INDEX idx_ingestion_item_failure_created_at ON ingestion_item_failure (created_at DESC);
CREATE INDEX idx_ingestion_item_failure_source_reason_at
    ON ingestion_item_failure (source, failure_reason, created_at DESC);

COMMENT ON TABLE ingestion_item_failure IS '정규화 실패 단건 (Spec 5 — raw_payload 7일 후 hash redact, 30일 후 삭제)';
COMMENT ON COLUMN ingestion_item_failure.failure_reason IS 'VALIDATION | PARSING | MAPPING | DEDUPLICATION_CONFLICT | OTHER';
