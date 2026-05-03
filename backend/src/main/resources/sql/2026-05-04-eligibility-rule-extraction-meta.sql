-- backend/src/main/resources/sql/2026-05-04-eligibility-rule-extraction-meta.sql
-- 적합도 룰 LLM 추출 메타데이터 컬럼 추가. 운영 환경에 수동 적용한다.

ALTER TABLE eligibility_rule
    ADD COLUMN IF NOT EXISTS confidence VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS source_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS extraction_version VARCHAR(10);
