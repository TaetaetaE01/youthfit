# 어드민 정책처리현황 — 출처 타입 표시

- 작성일: 2026-06-03
- 상태: 설계 승인 완료

## 배경 / 문제

어드민 "정책처리현황"(`AdminPolicyProcessingPage`) 테이블은 정책을 제목으로만 식별한다.
정책이 어느 소스에서 수집됐는지(청년몽땅정보통 / 복지로 / 온통청년) 화면에서 알 수 없어,
제목만으로 특정 정책을 찾기가 어렵다.

## 목표

정책처리현황 목록에서 각 정책의 **출처 타입**을 한눈에 보이게 하고, **출처별로 필터링**할 수 있게 한다.

## 비목표 (YAGNI)

- 출처별 통계/카운트 KPI 추가 (이번 범위 아님)
- 상세 패널(`PolicyProcessingDetailPanel`)의 출처 메타데이터(URL, externalId) 노출
- 출처 데이터 자체의 수정/관리 기능

## 데이터 출처

- `SourceType` enum (`policy/domain/model/SourceType.java`):
  - `YOUTH_SEOUL_CRAWL` → "청년몽땅정보통"
  - `BOKJIRO_CENTRAL` → "복지로"
  - `YOUTH_CENTER` → "온통청년"
- `PolicySource` 엔티티가 `Policy` 와 1:N. `source_type` 컬럼 보유.
- 한 정책에 출처가 여러 개일 수 있음(중복 제거로 묶인 경우) → **전부 표시**.

## UI 설계

- 정책 행의 제목 셀에 **출처 뱃지**를 출처 개수만큼 나열.
- 뱃지 = 한글 라벨 + **출처별 테두리 색**(배경 투명, 테두리·텍스트만 색):
  - 청년몽땅정보통 → 블루 계열
  - 복지로 → 그린 계열
  - 온통청년 → 퍼플 계열
  - 출처 없음 → 회색 "출처없음" 뱃지
- 색 토큰/구체 클래스는 `frontend/docs/DESIGN.md` 와 기존 뱃지 패턴을 따른다.
- 필터 영역(`PolicyProcessingFilters`)에 **출처 select** 추가. 선택 시 해당 출처를 가진 정책만 조회. `searchParams` 로 상태 보존.

## 백엔드 변경

1. `PolicySourceRepository` 에 배치 조회 추가
   - `findByPolicyIdIn(Collection<Long> policyIds)` (또는 동등 쿼리) → policyId → `List<SourceType>` 매핑.
   - 기존 stepMap/attachMap/embedMap 일괄 로딩 패턴을 따른다(행마다 개별 조회 금지).
2. 내부/응답 DTO 에 출처 필드 추가
   - `PolicyProcessingItemResult` 에 출처 목록 추가.
   - `PolicyProcessingItemResponse` 에 `List<SourceTagResponse>` 추가. 각 `SourceTagResponse(String code, String label)` — 프론트가 code 로 색 매핑, label 로 표시.
3. `AdminPolicyProcessingService.findProcessingPolicies()` 에서 sourceMap 을 조립해 Result 에 채운다.
4. **출처 필터**: 목록 조회에 선택적 `sourceType` 파라미터 추가 → 해당 출처를 가진 정책만 반환.
   - `AdminPolicyProcessingApi` 인터페이스에 `@Parameter` + Swagger 명세 갱신(컨벤션상 Api 인터페이스에만 작성).
   - Controller 는 Spring MVC 어노테이션만.

## 프론트엔드 변경

1. `types/adminPolicyProcessing.ts`
   - `PolicyProcessingItem` 에 `sources: { code: string; label: string }[]` 추가.
   - 목록 조회 파라미터 타입에 `sourceType?: string` 추가.
2. 출처 뱃지 렌더
   - 출처 `code` → 테두리 색 매핑 상수 정의.
   - `PolicyProcessingTable` 의 제목 셀에 뱃지 나열.
3. 필터
   - `PolicyProcessingFilters` 에 출처 select 추가, `searchParams` 연동, API 파라미터로 전달.

## 검증

- 백엔드: `AdminPolicyProcessingService` 의 sourceMap 조립 + 필터 동작 단위/슬라이스 테스트.
- 프론트: 출처 뱃지 렌더 및 필터 동작 확인(로컬).
- 회귀: 출처가 없는 정책에서도 목록이 정상 렌더되는지.

## 리스크 / 주의

- N+1 회피: 반드시 배치 조회로 sourceMap 구성.
- 출처 필터 + 페이징 동시 적용 시 카운트/페이지 일관성 유지.
