# PRD — 정책 탐색 (policy 모듈)

> **기능 ID**: F-01, F-02, F-03
> **모듈**: `com.youthfit.policy`
> **우선순위**: P0
> **구현 상태**: 완료

---

## 유저 스토리

### 비로그인 사용자의 탐색 경험
서연(페르소나 C)이 처음 YouthFit에 접속한다. 정책 목록 화면에서 "교육" 카테고리를 선택하면 교육 관련 정책만 필터링되어 보인다. 각 정책 카드에는 제목, 한 줄 요약, 카테고리, 지역, 신청 기간, 모집 상태가 표시된다. "장학" 이라는 키워드로 검색하면 제목과 요약에 해당 키워드가 포함된 정책이 나온다. 관심 있는 정책을 클릭하면 상세 페이지에서 정책 원문 정보, AI가 정리한 쉬운 요약, 신청 절차를 확인할 수 있다.

### 다중 필터 조합
준혁(페르소나 B)이 "서울" 지역 + "주거" 카테고리 + "모집중(OPEN)" 상태를 동시에 필터링한다. 결과는 신청 마감일이 가까운 순으로 정렬하여 긴급한 정책부터 확인한다.

---

## 기능 요구사항

### F-01. 정책 목록 조회

**설명**: 정책을 카테고리, 지역, 모집 상태로 필터링하고 정렬하여 페이지네이션된 목록으로 조회한다.

**비즈니스 규칙**:
- 비로그인 사용자도 접근 가능
- 기본 정렬은 생성일 내림차순
- 페이지 크기 기본값 20, 최대 100

**API 스펙**:

```
GET /api/v1/policies
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| regionCode | String | N | - | 지역 코드 (예: "SEOUL", "GYEONGGI") |
| category | Enum | N | - | JOBS, HOUSING, EDUCATION, WELFARE, FINANCE, CULTURE, PARTICIPATION |
| status | Enum | N | - | UPCOMING, OPEN, CLOSED |
| sortBy | String | N | createdAt | 정렬 기준 필드 |
| ascending | boolean | N | false | 오름차순 여부 |
| page | int | N | 0 | 페이지 번호 (0-based) |
| size | int | N | 20 | 페이지 크기 |

**응답 (200 OK)**:
```json
{
  "totalElements": 142,
  "totalPages": 8,
  "currentPage": 0,
  "size": 20,
  "content": [
    {
      "id": 1,
      "title": "2026 청년월세지원",
      "summary": "월세 부담을 줄이기 위한 ...",
      "category": "HOUSING",
      "regionCode": "SEOUL",
      "applyStart": "2026-03-01",
      "applyEnd": "2026-05-31",
      "status": "OPEN",
      "detailLevel": "FULL"
    }
  ]
}
```

### F-02. 정책 상세 조회

**설명**: 단일 정책의 전체 정보를 조회한다.

**비즈니스 규칙**:
- 비로그인 사용자도 접근 가능
- 존재하지 않는 ID 요청 시 404 응답

**API 스펙**:

```
GET /api/v1/policies/{policyId}
```

**응답 (200 OK)**:
```json
{
  "id": 1,
  "title": "2026 청년월세지원",
  "summary": "월세 부담을 줄이기 위한 ...",
  "category": "HOUSING",
  "regionCode": "SEOUL",
  "applyStart": "2026-03-01",
  "applyEnd": "2026-05-31",
  "status": "OPEN",
  "detailLevel": "FULL",
  "createdAt": "2026-02-15T10:30:00",
  "updatedAt": "2026-03-01T09:00:00"
}
```

**에러 응답 (404)**:
```json
{
  "success": false,
  "error": {
    "code": "YF-004",
    "message": "리소스를 찾을 수 없습니다"
  }
}
```

### F-03. 키워드 검색

**설명**: 제목·요약 텍스트에서 키워드를 검색하여 결과를 반환한다.

**비즈니스 규칙**:
- 비로그인 사용자도 접근 가능
- keyword 파라미터 필수
- 빈 키워드 요청 시 400 응답

**API 스펙**:

```
GET /api/v1/policies/search?keyword={keyword}&page={page}&size={size}
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| keyword | String | Y | - | 검색 키워드 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**응답**: 정책 목록 조회(F-01)와 동일한 구조

---

## 데이터 모델

### Policy 엔티티

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | 정책 ID |
| title | String | NOT NULL | 정책명 |
| summary | TEXT | - | 정책 요약 |
| category | Enum(Category) | NOT NULL | JOBS, HOUSING, EDUCATION, WELFARE, FINANCE, CULTURE, PARTICIPATION |
| regionCode | String(20) | - | 지역 코드 |
| applyStart | LocalDate | - | 신청 시작일 |
| applyEnd | LocalDate | - | 신청 마감일 |
| status | Enum(PolicyStatus) | NOT NULL | UPCOMING, OPEN, CLOSED |
| detailLevel | Enum(DetailLevel) | NOT NULL | LITE, FULL |
| createdAt | LocalDateTime | NOT NULL | 생성 시각 |
| updatedAt | LocalDateTime | NOT NULL | 수정 시각 |

**도메인 규칙**:
- 상태 전이: UPCOMING → OPEN → CLOSED (역방향 불가)
- detailLevel은 상향만 가능 (하향 무시)
- applyEnd가 현재 날짜 이전이면 `isExpired() = true`

### PolicySource (미구현)

| 필드 | 타입 | 설명 |
|------|------|------|
| policyId | Long | 정책 FK |
| sourceUrl | String | 원천 URL |
| sourceType | Enum | 출처 유형 |
| sourceHash | String | 변경 감지용 해시 |

---

## 사용자 플로우

```
사용자 접속
  → 정책 목록 (필터: 카테고리/지역/상태, 정렬)
  → 정책 카드 클릭 → 상세 페이지
  → AI 요약 확인 / 신청 절차 확인
  → "적합도 확인" or "Q&A" → 로그인 유도 (비로그인 시)
```
