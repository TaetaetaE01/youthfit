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

-- 멱등성 검사는 position() 으로 한다. LIKE 패턴을 쓰면 LLM 이 추출한
-- contactPhone 값이 LIKE 메타문자(%, _, \)를 포함할 때 잘못된 매칭이 일어날 수 있다.

BEGIN;

UPDATE policy
SET contact = CASE
  WHEN enrichment->'sections'->>'contactPhone' IS NOT NULL
       AND enrichment->'sections'->>'contactPhone' != ''
       AND contact IS NOT NULL AND contact != ''
       AND position((enrichment->'sections'->>'contactPhone') IN contact) = 0
    THEN contact || ' / 전화: ' || (enrichment->'sections'->>'contactPhone')
  WHEN enrichment->'sections'->>'contactPhone' IS NOT NULL
       AND enrichment->'sections'->>'contactPhone' != ''
       AND (contact IS NULL OR contact = '')
    THEN '전화: ' || (enrichment->'sections'->>'contactPhone')
  ELSE contact
END
WHERE enrichment->'sections'->>'contactPhone' IS NOT NULL
  AND enrichment->'sections'->>'contactPhone' != ''
  AND (contact IS NULL OR position((enrichment->'sections'->>'contactPhone') IN contact) = 0);

COMMIT;

-- 이어서: contact value 가 한 줄에 들어가지 않아 UI 정렬이 깨지는 문제를 위해
-- 담당과 전화 사이 구분자를 ' / ' 에서 줄바꿈으로 정정한다 (PolicyMetaSummary 가
-- whitespace-pre-line 으로 두 줄 노출).

BEGIN;

UPDATE policy
SET contact = replace(contact, ' / 전화: ', E'\n전화: ')
WHERE contact LIKE '%담당:%' AND contact LIKE '% / 전화: %';

COMMIT;
