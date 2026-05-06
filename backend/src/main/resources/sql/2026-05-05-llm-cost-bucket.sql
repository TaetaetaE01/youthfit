-- llm_cost_bucket: 1시간 단위 LLM 호출/비용 집계 (Spec 4)
CREATE TABLE llm_cost_bucket (
    id                  BIGSERIAL PRIMARY KEY,
    bucket_at           TIMESTAMP NOT NULL,
    module              VARCHAR(20) NOT NULL,
    model               VARCHAR(60) NOT NULL,
    call_count          INT NOT NULL DEFAULT 0,
    prompt_tokens       BIGINT NOT NULL DEFAULT 0,
    completion_tokens   BIGINT NOT NULL DEFAULT 0,
    total_tokens        BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd  NUMERIC(12, 6) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT uk_llm_cost_bucket_at_module_model UNIQUE (bucket_at, module, model)
);

CREATE INDEX idx_llm_cost_bucket_at ON llm_cost_bucket (bucket_at DESC);
CREATE INDEX idx_llm_cost_bucket_module_at ON llm_cost_bucket (module, bucket_at DESC);

COMMENT ON TABLE llm_cost_bucket IS 'LLM 호출 1시간 버킷 집계 (Spec 4 — admin LLM 비용 대시보드)';
COMMENT ON COLUMN llm_cost_bucket.bucket_at IS 'UTC, hour 단위 truncate';
COMMENT ON COLUMN llm_cost_bucket.module IS 'QNA | GUIDE | EMBEDDING | INGESTION | ELIGIBILITY';
