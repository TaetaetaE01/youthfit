-- youth-center API 의 raw 데이터에는 담당자 전화번호 필드가 없다 (이름 sprvsnInstPicNm 만).
-- transform 노드가 contact 컬럼에 "담당: 김서윤" 만 넣고 있어 정책 상세 "문의처" 영역이 무의미하게 보였다.
-- mega-node enrichment + LLM 추출이 mohw.go.kr 등 외부 페이지에서 contactPhone 을 잡아주므로,
-- transform 노드는 enrichment.sections.contactPhone 도 합쳐 "담당: 김서윤 / 전화: 044-202-3887" 형태로 만들도록 갱신됐다 (2026-05-19).
-- 이 마이그레이션은 기존에 적재된 정책의 contact 컬럼을 동일 규칙으로 일회성 정리한다.
--
-- 적용 방법:
--   docker compose exec -T postgres psql -U youthfit -d youthfit -f /sql/2026-05-19-policy-contact-merge-phone.sql
-- 또는 운영 DB 에 직접 실행.
--
-- 영향: enrichment.sections.contactPhone 이 있고 contact 에 그 번호가 아직 안 들어가 있는 정책 행만 갱신.

BEGIN;

UPDATE policy
SET contact = CASE
  WHEN enrichment->'sections'->>'contactPhone' IS NOT NULL
       AND enrichment->'sections'->>'contactPhone' != ''
       AND contact IS NOT NULL AND contact != ''
       AND contact NOT LIKE '%' || (enrichment->'sections'->>'contactPhone') || '%'
    THEN contact || ' / 전화: ' || (enrichment->'sections'->>'contactPhone')
  WHEN enrichment->'sections'->>'contactPhone' IS NOT NULL
       AND enrichment->'sections'->>'contactPhone' != ''
       AND (contact IS NULL OR contact = '')
    THEN '전화: ' || (enrichment->'sections'->>'contactPhone')
  ELSE contact
END
WHERE enrichment->'sections'->>'contactPhone' IS NOT NULL
  AND enrichment->'sections'->>'contactPhone' != ''
  AND (contact IS NULL OR contact NOT LIKE '%' || (enrichment->'sections'->>'contactPhone') || '%');

COMMIT;
