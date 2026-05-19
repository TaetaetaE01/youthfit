-- youth-center API 의 raw 텍스트는 들여쓰기 line-wrap 을 포함한다
-- (예: "...정신건강\n  의학과 진료..."). transform 노드의 clean() 이
-- 들여쓰기로 이어지는 줄을 공백으로 풀도록 갱신됐다 (정책 173 이후 ingest).
-- 이 마이그레이션은 기존에 적재된 정책 텍스트 컬럼들에서 동일 패턴을 일회성 정리한다.
--
-- 적용 방법:
--   docker compose exec -T postgres psql -U youthfit -d youthfit -f /sql/2026-05-19-policy-text-unwrap.sql
-- 또는 운영 DB 에 직접 실행.
--
-- 예상 영향: line-wrap 이 들어가 있던 정책 행만 갱신. softwrap 없는 정책은 패스.

BEGIN;

UPDATE policy SET
  support_target            = regexp_replace(regexp_replace(coalesce(support_target,''),            E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  support_content           = regexp_replace(regexp_replace(coalesce(support_content,''),           E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  selection_criteria        = regexp_replace(regexp_replace(coalesce(selection_criteria,''),        E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  screening_method          = regexp_replace(regexp_replace(coalesce(screening_method,''),          E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  submission_documents      = regexp_replace(regexp_replace(coalesce(submission_documents,''),      E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  additional_qualification  = regexp_replace(regexp_replace(coalesce(additional_qualification,''),  E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  participation_restriction = regexp_replace(regexp_replace(coalesce(participation_restriction,''), E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  additional_notes          = regexp_replace(regexp_replace(coalesce(additional_notes,''),          E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  business_period_note      = regexp_replace(regexp_replace(coalesce(business_period_note,''),      E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  body                      = regexp_replace(regexp_replace(coalesce(body,''),                      E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g'),
  summary                   = regexp_replace(regexp_replace(coalesce(summary,''),                   E'\n[ \t]+', ' ', 'g'), '[ \t]+', ' ', 'g')
WHERE
  support_target ~ E'\n[ \t]+'
  OR support_content ~ E'\n[ \t]+'
  OR selection_criteria ~ E'\n[ \t]+'
  OR screening_method ~ E'\n[ \t]+'
  OR submission_documents ~ E'\n[ \t]+'
  OR additional_qualification ~ E'\n[ \t]+'
  OR participation_restriction ~ E'\n[ \t]+'
  OR additional_notes ~ E'\n[ \t]+'
  OR business_period_note ~ E'\n[ \t]+'
  OR body ~ E'\n[ \t]+'
  OR summary ~ E'\n[ \t]+';

COMMIT;
