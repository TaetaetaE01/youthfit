-- llm_cost_bucket module check 제약에 ATTACHMENT_GATE 추가 (#177)
--
-- 원인: Hibernate 가 @Enumerated(STRING) enum 컬럼에 대해 "테이블 생성 시점의" enum 값으로
-- CHECK 제약을 생성한다. 이후 LlmModule 에 ATTACHMENT_GATE 가 추가됐지만(첨부 임베딩 게이트)
-- ddl-auto=update 는 기존 CHECK 를 갱신하지 않아, 첨부 게이트 LLM 호출의 비용 버킷 insert 가
-- 제약 위반으로 실패하고 비용 집계가 유실됐다 (2026-07-03 #167 재인덱싱 중 실측).
--
-- 대상: 테이블이 ATTACHMENT_GATE 추가 이전에 생성된 기존 DB (prod 스냅샷 복원본 포함).
-- 신규 DB 는 Hibernate 가 현행 enum 전체로 제약을 만들므로 불필요 (IF EXISTS 로 안전).
-- LlmModule 에 값을 또 추가하면 이 스크립트처럼 ALTER 가 다시 필요하다 — enum javadoc 참조.

ALTER TABLE llm_cost_bucket DROP CONSTRAINT IF EXISTS llm_cost_bucket_module_check;
ALTER TABLE llm_cost_bucket ADD CONSTRAINT llm_cost_bucket_module_check
    CHECK (module::text = ANY (ARRAY[
        'QNA', 'GUIDE', 'EMBEDDING', 'INGESTION', 'ELIGIBILITY', 'QUERY_REWRITE', 'ATTACHMENT_GATE'
    ]::text[]));

COMMENT ON COLUMN llm_cost_bucket.module IS 'QNA | GUIDE | EMBEDDING | INGESTION | ELIGIBILITY | QUERY_REWRITE | ATTACHMENT_GATE';
