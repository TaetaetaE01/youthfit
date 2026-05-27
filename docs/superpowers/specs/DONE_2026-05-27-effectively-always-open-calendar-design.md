# 사실상 상시 정책 캘린더 분류 — Design

> 작성일: 2026-05-27
> 상태: 디자인 승인됨, 구현 plan 작성 대기

## 배경

YouthFit 캘린더 (`/api/v1/policies/calendar`) 에서 정책 신청 기간이 1 년 통째로 점유되는 막대가 다수 표시되어 UX 를 저해한다. 정책 본문에 "신청기간 ~ 2026.12.31" 또는 "사업기간 2026.1.1 ~ 12.31" 같은 표현이 적혀 있고, 기간 추출 파이프라인의 정규식 (`PeriodRegexPatterns`) 이 이를 그대로 `applyEnd = 2026-12-31` 로 매칭하기 때문이다.

LLM 직접 추출 (`OpenAiPolicyPeriodExtractor`) 은 정확한 연/월/일이 확인될 때만 채우라고 가드돼 있으며, 정규식 후보가 없을 때만 fallback 으로 호출된다 (`PeriodResolver.java:33-36`). 따라서 "12.31" 의 진짜 원천은 LLM 이 아니라 본문에 실제로 적힌 12 월 31 일 표현을 정규식이 매칭한 것이다.

DB 원본은 추출 정확도 측면에서 그대로 두는 것이 맞다. 대신 캘린더 응답 매핑 시점에 "사실상 상시" 인 정책을 식별해 상시 목록으로 옮긴다.

## 목표

- 캘린더 (`/calendar`) 에서 1 년 통째로 점유되는 막대 제거
- 해당 정책들을 `/calendar/always-open` 목록에 포함시켜 노출은 유지
- 진짜 상시 ("상시모집") 와 사실상 상시 ("26 년 상시모집") 를 라벨로 구분
- DB 원본 (`apply_start` / `apply_end`) 은 손대지 않음 — 정책 상세, 알림, 적합도 판정 등 다른 흐름에는 영향 없음

## 비목표

- 기간 추출 정확도 자체 개선 (별도 작업)
- "사업기간/운영기간" 라벨 마스킹 강화 (별도 작업)
- LLM 프롬프트 수정 (현재 12.31 의 원인이 아님)

## 판정 조건 — "사실상 상시"

`Policy.isEffectivelyAlwaysOpen()` 도메인 메서드.

```
end.month == 12 AND end.day == 31
AND (start == null OR ChronoUnit.DAYS.between(start, end) >= 270)
```

- `end == null` → false (이 메서드는 "사실상" 케이스만 책임. 진짜 상시는 별개)
- `end.month != 12 || end.day != 31` → false (예: 11 월 30 일 마감)
- `start == null`, `end == 2026-12-31` → true (마감일만 본문에 적힌 케이스)
- `start == 2026-01-15`, `end == 2026-12-31` → true (350 일 ≥ 270 일)
- `start == 2026-12-20`, `end == 2026-12-31` → false (11 일, 진짜 12 월 단기 모집)

임계값 270 일 근거: 약 9 개월. 8 개월 (240 일) 이하면 진짜 분기·반기 모집일 가능성이 있고, 9 개월 이상이면 "사실상 연중" 으로 봐도 안전. 추후 실측 데이터 보고 조정 가능.

## 데이터 흐름 변경

### 현재
```
GET /calendar          → PolicyQueryService.findByDateRange()
                       → policyRepository.findByCalendarRange()
                       → applyStart/applyEnd 가 있는 정책

GET /calendar/always-open → PolicyQueryService.findAlwaysOpen()
                          → policyRepository.findAlwaysOpen()
                          → applyStart IS NULL AND applyEnd IS NULL
```

### 변경 후
```
GET /calendar          → findByDateRange()
                       → repo 조회 후 .filter(!isEffectivelyAlwaysOpen) 적용
                       → 사실상 상시 정책 제외

GET /calendar/always-open → findAlwaysOpen()
                          → PolicySpecification.alwaysOpen 확장:
                            (applyStart IS NULL AND applyEnd IS NULL)
                            OR
                            (applyEnd IS NOT NULL
                             AND MONTH(applyEnd)=12 AND DAY(applyEnd)=31
                             AND (applyStart IS NULL
                                  OR DATEDIFF(applyEnd, applyStart) >= 270))
```

`/calendar` 의 제외 처리는 Application 레이어 필터로 충분 (조회 결과가 페이지네이션 없는 리스트). `/calendar/always-open` 은 페이지네이션이 있으므로 Repository (Specification) 레벨에서 OR 결합해야 정확한 totalCount 가 나온다.

## 응답 스키마 변경

`PolicyCalendarResult` 와 `PolicyCalendarResponse` (record) 에 `deadlineYear: Integer (nullable)` 필드 추가.

```java
public record PolicyCalendarResult(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel,
        Integer deadlineYear        // 새 필드
) { ... }
```

생성 로직:
- 진짜 상시 (`start == null && end == null`) → `deadlineYear = null`
- 사실상 상시 (`isEffectivelyAlwaysOpen() == true`) → `deadlineYear = applyEnd.getYear()`
- 그 외 → `deadlineYear = null`

`applyStart` / `applyEnd` 응답 값은 DB 원본 그대로 유지 (변환하지 않음). 프론트는 `deadlineYear` 유무로 표시 분기.

`/calendar` 응답에서는 사실상 상시가 이미 필터로 제외되었으므로 `deadlineYear` 는 사실상 항상 null. 단순화를 위해 같은 record 를 양쪽 엔드포인트가 공유.

## 프론트엔드 변경

`frontend/src/types/policy.ts` 의 `PolicyCalendarItem` 에 `deadlineYear?: number` 추가.

always-open 리스트 컴포넌트 (이미 존재하는 항상모집 섹션 표시 컴포넌트) 에서 라벨 분기:
```ts
const label = item.deadlineYear
  ? `${String(item.deadlineYear).slice(2)}년 상시모집`  // "26년 상시모집"
  : '상시모집';
```

캘린더 막대 그리기 로직 (`calendarLayout.ts`, `CalendarMonthGrid.tsx`) 은 수정 불필요 — 백엔드에서 이미 제외했음.

## 영향받는 파일

### 백엔드
1. `policy/domain/model/Policy.java` — `isEffectivelyAlwaysOpen()` 도메인 메서드
2. `policy/infrastructure/persistence/PolicySpecification.java` — `alwaysOpen` Specification OR 조건 확장
3. `policy/application/service/PolicyQueryService.java` — `findByDateRange` 결과 필터
4. `policy/application/dto/result/PolicyCalendarResult.java` — `deadlineYear` 필드 추가, `from()` 갱신
5. `policy/presentation/dto/response/PolicyCalendarResponse.java` — `deadlineYear` 필드 추가, `from()` 갱신
6. 테스트:
   - `PolicyEffectivelyAlwaysOpenTest` — 경계값 5 케이스
   - `PolicyQueryServiceTest.findByDateRange_excludesEffectivelyAlwaysOpen`
   - `PolicyRepositoryImplTest.findAlwaysOpen_includesEffectivelyAlwaysOpen` (통합 테스트)

### 프론트엔드
7. `frontend/src/types/policy.ts` — `PolicyCalendarItem.deadlineYear?: number`
8. 항상모집 섹션 표시 컴포넌트 — 라벨 분기 (구현 plan 단계에서 정확한 파일 식별)

## 예상 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| 270 일 임계값이 실제 데이터와 안 맞음 | 진짜 9 개월 모집이 상시로 분류되거나, 사실상 1 년 정책이 캘린더 막대로 남음 | 임계값을 상수로 두고 추후 측정 후 조정 |
| Specification OR 조건이 인덱스를 못 탐 | 페이지네이션 쿼리 성능 저하 | `apply_end` 인덱스가 이미 있고, 함수 인덱스 (month, day) 가 없어 풀스캔될 가능성. 성능 측정 후 부분 인덱스 (`WHERE apply_end >= '2024-01-01'`) 또는 generated column 검토 |
| 진짜 12-31 마감 단기 모집이 상시로 오분류 | 단기 정책이 캘린더에서 사라짐 | 270 일 조건으로 막아짐. 추가로 모니터링 로그 추가 검토 |
| `/calendar` 응답에 `deadlineYear` 가 항상 null 인데 필드만 존재 | 응답 크기 약간 증가 | 무시할 수준. 양쪽 record 공유로 코드 단순화 우선 |

## 검증 기준

1. `Policy.isEffectivelyAlwaysOpen()` 단위 테스트 5 케이스 모두 통과
2. `/calendar` 응답에 `applyEnd == YYYY-12-31` 이고 270 일 이상인 정책이 포함되지 않음
3. `/calendar/always-open` 응답에 위 정책이 포함되고 `deadlineYear` 가 채워짐
4. `/calendar/always-open` 응답에서 진짜 상시 (`applyStart=null, applyEnd=null`) 는 `deadlineYear=null`
5. 정책 상세 (`/policies/{id}`) 응답의 `applyStart/applyEnd` 는 DB 원본 그대로 유지
6. 프론트엔드 항상모집 섹션에서 "상시모집" 과 "26 년 상시모집" 이 올바르게 구분 표시

## 미해결 / 후속 작업 (별도 작업으로 분리)

- 270 일 임계값 데이터 기반 튜닝
- `PolicyLabels.NEGATIVE` 마스킹 강화 ("사업기간" 표현이 본문 곳곳에 흩어진 경우 잡기)
- DB 인덱스 최적화 — 함수 인덱스 또는 generated column
- 다년 사업 (`end == YYYY-12-31` 이고 미래 연도) 의 의미 재검토 — 현재는 같은 규칙 적용
