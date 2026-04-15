# PRD — 적합도 판정 (eligibility 모듈)

> **기능 ID**: F-06
> **모듈**: `com.youthfit.eligibility`
> **우선순위**: P1
> **구현 상태**: 미구현 (package-info.java만 존재)

---

## 유저 스토리

### 규칙 기반 적합도 확인
준혁(페르소나 B)이 "청년월세지원" 정책의 상세 페이지에서 "내 적합도 확인" 버튼을 누른다. 시스템이 준혁의 프로필(나이 29세, 서울 거주, 연봉 3,200만 원, 독립 가구)과 정책 조건을 대조하여 결과를 보여준다.

출력 예시:
- **해당 가능성 높음**: 연령(만 19~34세), 거주지(서울)
- **불명확 / 추가 확인 필요**: 가구 소득 기준 — 부모님 소득 합산 여부에 따라 결과가 달라질 수 있음
- **판단 근거 원문**: 해당 공고의 자격 요건 섹션 인용

적합도 판정은 법적 효력이 있는 자격 판정이 아니라는 안내 문구가 항상 노출된다.

---

## 기능 요구사항

### F-06-1. 적합도 판정 요청

**설명**: 사용자 프로필 정보와 정책 조건을 대조하여 해당 가능성을 판정한다.

**비즈니스 규칙**:
- 인증 필수
- 판정 결과는 법적 효력이 없는 참고용 안내이며, 해당 안내 문구 상시 노출
- 출력 등급: `해당 가능성 높음` / `불명확 / 추가 확인 필요` / `해당 가능성 낮음`
- 각 결과에 반드시 포함: 주요 판단 이유, 누락·불명확 조건, 판단 근거 원문 필드

**API 스펙**:

```
POST /api/v1/eligibility/judge
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**요청**:
```json
{
  "policyId": 1
}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "policyId": 1,
    "policyTitle": "2026 청년월세지원",
    "overallResult": "LIKELY_ELIGIBLE",
    "criteria": [
      {
        "field": "age",
        "label": "연령",
        "result": "LIKELY_ELIGIBLE",
        "reason": "만 29세 — 자격 요건(만 19~34세) 충족",
        "sourceReference": "자격 요건 > 연령 항목"
      },
      {
        "field": "income",
        "label": "소득 기준",
        "result": "UNCERTAIN",
        "reason": "가구 소득 기준 미확인 — 부모님 소득 합산 여부에 따라 결과가 달라질 수 있음",
        "sourceReference": "자격 요건 > 소득 항목: '중위소득 150% 이하'"
      }
    ],
    "missingFields": ["householdIncome"],
    "disclaimer": "본 결과는 참고용이며, 법적 효력이 있는 자격 판정이 아닙니다. 최종 확인은 공식 신청 채널에서 진행해 주세요."
  }
}
```

**결과 Enum**:

| 값 | 의미 |
|----|------|
| LIKELY_ELIGIBLE | 해당 가능성 높음 |
| UNCERTAIN | 불명확 / 추가 확인 필요 |
| LIKELY_INELIGIBLE | 해당 가능성 낮음 |

---

## 데이터 모델 (미구현)

### EligibilityRule

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| policyId | Long | 정책 FK |
| field | String | 판정 기준 필드 (age, income, region 등) |
| operator | String | 비교 연산자 (EQ, GTE, LTE, IN, BETWEEN 등) |
| value | String | 기준값 |
| label | String | 사용자에게 보여줄 기준명 |
| sourceReference | String | 원문 근거 |

---

## 의존 관계

- `eligibility` → `user.domain`: 사용자 프로필 정보 조회
- `eligibility` → `policy.domain`: 정책 조건 조회

## 확장 방향

- v0: 규칙 기반 단순 매칭
- 이후: 룰 엔진 도입, 감사 메타데이터, 판정 이력 저장
