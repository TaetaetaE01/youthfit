ALTER TABLE policy_document
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'BODY';

UPDATE policy_document
   SET source = 'ATTACHMENT'
 WHERE attachment_id IS NOT NULL;

-- DEFAULT 는 마이그레이션 적용 후 운영자가 별도 단계에서 제거한다.
-- (롤백 안전성을 위해 spec §11 의 5단계 롤백 경로 참조)
