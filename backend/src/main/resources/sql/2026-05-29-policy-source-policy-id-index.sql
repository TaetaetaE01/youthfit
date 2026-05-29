-- 2026-05-29-policy-source-policy-id-index.sql
-- 정책 목록 출처 필터(EXISTS 서브쿼리) 및 findFirstByPolicyId(s) 조회를 위한 인덱스.
-- PostgreSQL 은 FK 컬럼에 자동 인덱스를 만들지 않으므로 명시적으로 추가한다.
CREATE INDEX IF NOT EXISTS idx_policy_source_policy_id_source_type
    ON policy_source (policy_id, source_type);
