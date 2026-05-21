-- 2026-05-21-policy-reference-site-source.sql
-- 기존 policy.reference_sites JSONB 의 각 원소에 source 필드를 'AUTO' 로 백필한다.
-- 이미 source 키가 있는 원소는 그대로 둔다 (멱등).
-- 백필이 필요한 row 만 갱신하여 락 범위를 좁힌다.
BEGIN;

UPDATE policy
SET reference_sites = (
  SELECT jsonb_agg(
    CASE
      WHEN elem ? 'source' THEN elem
      ELSE elem || jsonb_build_object('source', 'AUTO')
    END
  )
  FROM jsonb_array_elements(reference_sites) AS elem
)
WHERE jsonb_typeof(reference_sites) = 'array'
  AND jsonb_array_length(reference_sites) > 0
  AND EXISTS (
        SELECT 1
          FROM jsonb_array_elements(reference_sites) AS e
         WHERE NOT (e ? 'source')
      );

COMMIT;
