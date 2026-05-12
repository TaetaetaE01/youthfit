-- 2026-05-12 youth-center enrichment 컬럼 추가
ALTER TABLE policy
    ADD COLUMN IF NOT EXISTS enrichment JSONB;

COMMENT ON COLUMN policy.enrichment IS
    '온통청년 외부 페이지에서 LLM 으로 자동 추출한 보조 정보. sections/extraAttachments/status/confidence/sourceUrl/fetchedAt/extractor 포함';
