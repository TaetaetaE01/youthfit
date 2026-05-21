-- 2026-05-21-ingestion-run-log-period-meta.sql
ALTER TABLE ingestion_run_log
    ADD COLUMN period_resolve_meta JSONB;
