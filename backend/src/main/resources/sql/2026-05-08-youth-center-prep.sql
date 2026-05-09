-- 온통청년 ingestion 도입 사전 마이그레이션
-- 실행 전 필수 점검:
--   SELECT source_type, external_id, COUNT(*) FROM policy_source
--    GROUP BY source_type, external_id HAVING COUNT(*) > 1;
--   결과 0건이어야 ① ALTER 가 성공한다.

-- ① (source_type, external_id) UNIQUE 제약
-- 자치구별/페이지별 동시 호출에서의 중복 row 차단
ALTER TABLE policy_source
  ADD CONSTRAINT uq_policy_source_type_external_id
  UNIQUE (source_type, external_id);

-- ② BOKJIRO 우선 dedup 용 정규화 제목 컬럼 (GENERATED ALWAYS AS STORED)
-- title 변경 시 자동 재계산. Java TitleNormalizer 와 동일 규칙
-- (소문자화 + 한글·영숫자 외 문자 제거)
ALTER TABLE policy
  ADD COLUMN normalized_title TEXT
  GENERATED ALWAYS AS (
    lower(regexp_replace(title, '[^[:alnum:]가-힣]', '', 'g'))
  ) STORED;

-- ③ 정규화 제목 인덱스 — 복지로 dedup 조회 O(log n)
CREATE INDEX idx_policy_normalized_title ON policy (normalized_title);
