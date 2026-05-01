-- backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql
-- Q&A 의미 캐시 테이블. 운영 환경에 수동 적용한다.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS qna_question_cache (
    id            BIGSERIAL PRIMARY KEY,
    policy_id     BIGINT       NOT NULL,
    source_hash   VARCHAR(64)  NOT NULL,
    question_text TEXT         NOT NULL,
    embedding     vector(1536) NOT NULL,
    answer        TEXT         NOT NULL,
    sources_json  JSONB        NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_qna_question_cache_policy
    ON qna_question_cache (policy_id);

CREATE INDEX IF NOT EXISTS idx_qna_question_cache_embedding
    ON qna_question_cache
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
