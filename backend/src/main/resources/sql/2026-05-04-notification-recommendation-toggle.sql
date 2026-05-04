-- 알림 설정에 추천 알림 토글 추가
ALTER TABLE notification_setting
    ADD COLUMN IF NOT EXISTS recommendation_enabled boolean NOT NULL DEFAULT false;
