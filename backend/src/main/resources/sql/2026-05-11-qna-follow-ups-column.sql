-- backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql
-- Q&A 의미 캐시에 후속 추천질문 컬럼 추가. NULL 허용으로 기존 entry 호환.

ALTER TABLE qna_question_cache
    ADD COLUMN IF NOT EXISTS follow_ups_json JSONB NULL;
