-- 온통청년 정책 상세 정보 보강을 위한 11개 컬럼 추가.
-- 모두 nullable 또는 default 있어 비파괴적이며, BOKJIRO 등 다른 source는 NULL 유지.

ALTER TABLE policy
  ADD COLUMN screening_method TEXT,
  ADD COLUMN submission_documents TEXT,
  ADD COLUMN additional_qualification TEXT,
  ADD COLUMN participation_restriction TEXT,
  ADD COLUMN additional_notes TEXT,
  ADD COLUMN business_period_start DATE,
  ADD COLUMN business_period_end DATE,
  ADD COLUMN business_period_note TEXT,
  ADD COLUMN support_scale INTEGER,
  ADD COLUMN first_come_first_served BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN apply_url VARCHAR(500);
