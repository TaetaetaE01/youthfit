-- policy_processing_step: 정책 5단계 처리 진행 상태 추적 (Phase A)
CREATE TABLE policy_processing_step (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    step            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    attempt         INTEGER NOT NULL,
    reason          VARCHAR(500),
    detail_json     JSONB,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     INTEGER,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_pps_policy_step_attempt UNIQUE (policy_id, step, attempt)
);
CREATE INDEX idx_pps_policy_step ON policy_processing_step (policy_id, step);
CREATE INDEX idx_pps_status_finished ON policy_processing_step (status, finished_at DESC);
CREATE INDEX idx_pps_step_status ON policy_processing_step (step, status);

COMMENT ON TABLE policy_processing_step IS '정책 5단계(NORMALIZATION → DEDUPLICATION → ENRICHMENT → GUIDE_GENERATION → RAG_INDEXING) 처리 진행 상태 추적 (Phase A)';
COMMENT ON COLUMN policy_processing_step.step IS 'NORMALIZATION | DEDUPLICATION | ENRICHMENT | GUIDE_GENERATION | RAG_INDEXING';
COMMENT ON COLUMN policy_processing_step.status IS 'IN_PROGRESS | SUCCESS | FAILURE | SKIPPED';
