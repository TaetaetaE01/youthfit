-- 2026-05-21-enrichment-job.sql
-- Admin enrichment review 기능을 위한 잡 라이프사이클 테이블.
-- 스펙: docs/superpowers/specs/2026-05-21-admin-enrichment-review-design.md §3.1
BEGIN;

CREATE TABLE IF NOT EXISTS enrichment_job (
  id              BIGSERIAL PRIMARY KEY,
  policy_id       BIGINT      NOT NULL REFERENCES policy(id),
  requested_by    VARCHAR(64) NOT NULL,
  requested_urls  JSONB       NOT NULL,
  status          VARCHAR(16) NOT NULL,
  attempt         INT         NOT NULL,
  error_message   TEXT,
  requested_at    TIMESTAMP   NOT NULL,
  started_at      TIMESTAMP,
  finished_at     TIMESTAMP
);

-- 정책당 진행 중 잡은 1건만 허용
CREATE UNIQUE INDEX IF NOT EXISTS idx_enrichment_job_one_active
  ON enrichment_job (policy_id)
  WHERE status IN ('PENDING', 'RUNNING');

-- 최근 잡 조회·이력 표시용
CREATE INDEX IF NOT EXISTS idx_enrichment_job_policy_recent
  ON enrichment_job (policy_id, requested_at DESC);

-- 타임아웃 스캔용
CREATE INDEX IF NOT EXISTS idx_enrichment_job_active_requested_at
  ON enrichment_job (requested_at)
  WHERE status IN ('PENDING', 'RUNNING');

COMMIT;
