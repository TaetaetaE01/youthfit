-- 2026-05-21-policy-period-meta.sql
-- 신청기간 추출 메타 (source/confidence/evidence) — nullable, 기존 행은 NULL
ALTER TABLE policy
    ADD COLUMN apply_period_source     VARCHAR(32),
    ADD COLUMN apply_period_confidence DOUBLE PRECISION,
    ADD COLUMN apply_period_evidence   VARCHAR(200);
