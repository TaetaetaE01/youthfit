-- Postgres 17 contrib 기본 탑재. 멱등 보장.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- content 컬럼 trigram GIN 인덱스. similarity() 함수 호출에 자동 활용됨.
CREATE INDEX IF NOT EXISTS policy_document_content_trgm_idx
    ON policy_document USING GIN (content gin_trgm_ops);
