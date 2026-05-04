-- notification_history: 상태 전이(PENDING/SENT/FAILED) 도입
-- 적용 절차: psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-05-notification-history-status.sql
-- 기존 행은 status='SENT' 로 백필 (이미 발송 완료로 간주)

ALTER TABLE notification_history ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'SENT';
ALTER TABLE notification_history ADD COLUMN created_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failed_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failure_reason VARCHAR(500);

ALTER TABLE notification_history ALTER COLUMN sent_at DROP NOT NULL;

UPDATE notification_history SET created_at = sent_at WHERE created_at IS NULL;

ALTER TABLE notification_history ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notification_history ALTER COLUMN status DROP DEFAULT;
