-- 2026-06-07-policy-attachment-embedding-decision.sql
-- policy_attachment 에 임베딩 가치 판정 결과 컬럼 추가.
-- embedding_included: LLM 이 RAG 임베딩에 가치 있다고 판정한 경우 true,
--                     제외 판정 시 false, 미판정(신규·재추출) 시 NULL.
-- embedding_decision_reason: 판정 사유 (최대 500자). LLM 응답 reason 필드 저장.
-- 로컬은 JPA ddl-auto 로 컬럼이 자동 생성되므로 이 SQL 은 prod ALTER 용.
ALTER TABLE policy_attachment
    ADD COLUMN IF NOT EXISTS embedding_included BOOLEAN,
    ADD COLUMN IF NOT EXISTS embedding_decision_reason VARCHAR(500);
