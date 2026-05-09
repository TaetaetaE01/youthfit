-- RuleOperator enum에 ANY 추가에 따른 CHECK 제약 조건 갱신.
-- 기존 EQ/GTE/LTE/IN/BETWEEN/NOT_EQ 6개 + ANY = 7개 허용.

ALTER TABLE eligibility_rule
  DROP CONSTRAINT IF EXISTS eligibility_rule_operator_check;

ALTER TABLE eligibility_rule
  ADD CONSTRAINT eligibility_rule_operator_check
  CHECK (operator::text = ANY (ARRAY[
    'EQ'::varchar, 'GTE'::varchar, 'LTE'::varchar, 'IN'::varchar,
    'BETWEEN'::varchar, 'NOT_EQ'::varchar, 'ANY'::varchar
  ]::text[]));
