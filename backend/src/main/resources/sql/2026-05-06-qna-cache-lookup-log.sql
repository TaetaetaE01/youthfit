CREATE TABLE qna_cache_lookup_log (
    id                 BIGSERIAL PRIMARY KEY,
    policy_id          BIGINT NOT NULL,
    question_text      TEXT NOT NULL,
    normalized_text    TEXT NOT NULL,
    result             VARCHAR(20) NOT NULL,
    matched_cached_id  BIGINT,
    similarity_score   DECIMAL(6,5),
    threshold_applied  DECIMAL(6,5) NOT NULL,
    llm_call_made      BOOLEAN NOT NULL,
    looked_up_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_qna_cache_lookup_at        ON qna_cache_lookup_log(looked_up_at DESC);
CREATE INDEX idx_qna_cache_lookup_result_at ON qna_cache_lookup_log(result, looked_up_at DESC);
CREATE INDEX idx_qna_cache_lookup_policy_at ON qna_cache_lookup_log(policy_id, looked_up_at DESC);
