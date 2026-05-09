-- Policy 엔티티에 region_codes 컬럼 추가
-- 콤마 구분 시군구 코드 문자열. 예: "11680,26350,47730"
-- 기존 region_code(단일 라벨)와 별개로 정책이 커버하는 시군구 전체를 보존한다.

ALTER TABLE policy ADD COLUMN region_codes TEXT;
