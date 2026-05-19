-- ingestion_item_failure: n8n 파이프라인 추적 + stack trace 보강
ALTER TABLE ingestion_item_failure
    ADD COLUMN IF NOT EXISTS n8n_workflow_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS n8n_execution_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS n8n_node_name     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS error_stack       TEXT;

COMMENT ON COLUMN ingestion_item_failure.n8n_workflow_name IS 'n8n workflow display name (운영자 가독용)';
COMMENT ON COLUMN ingestion_item_failure.n8n_execution_id  IS 'n8n executionId — n8n UI 딥링크 키';
COMMENT ON COLUMN ingestion_item_failure.n8n_node_name     IS '백엔드로 POST 보낸 직전 노드 이름';
COMMENT ON COLUMN ingestion_item_failure.error_stack       IS '백엔드 예외 stack trace (truncate, 디버깅용)';
