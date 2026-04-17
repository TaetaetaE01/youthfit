# PRD — 이메일 알림 (user/notification)

> **기능 ID**: F-09
> **모듈**: `com.youthfit.user` (notification 하위 도메인)
> **우선순위**: P2
> **구현 상태**: 미구현
> **선행 조건**: 북마크(F-08) 구현 완료, 적합도 판정(F-06) 구현 완료

---

## 유저 스토리

### 마감일 알림
민지(페르소나 A)가 북마크한 정책 중 "청년내일채움공제"의 마감일이 7일 남았을 때 이메일 알림을 받는다. 이메일에는 정책명, 마감일, YouthFit 상세 페이지 링크, 공식 신청 채널 링크가 포함된다.

### 맞춤 정책 추천 알림
준혁(페르소나 B)이 마이페이지에서 프로필과 적합도 정보를 입력한 상태로 "맞춤 정책 추천" 알림을 활성화했다. 이후 새로 수집된 정책 중 준혁의 프로필 기준 적합도가 `LIKELY_ELIGIBLE`로 판정되는 정책이 생기면 주기적으로 이메일로 추천 목록을 받는다.

---

## 기능 요구사항

### F-09-1. 알림 설정 관리

**설명**: 마감일 알림과 맞춤 정책 추천 알림 수신을 각각 설정한다.

**비즈니스 규칙**:
- 인증 필수
- 이메일이 등록되지 않은 사용자는 토글 활성화 시점에 JIT(Just-in-Time) 시트에서 이메일을 입력해야 한다
- 마감일 알림 기본값: 활성화, 마감 7일 전
- 설정 가능 시점 옵션: 3일 전, 7일 전, 14일 전
- 맞춤 정책 추천 기본값: 비활성화
- 맞춤 추천은 프로필 적합도 정보(나이, 지역, 고용 상태 등)가 입력되어야 의미 있는 결과를 제공하므로 미입력 시 UI에서 안내 문구를 노출한다
- CLOSED 상태의 정책에는 알림을 발송하지 않음

**API 스펙**:

```
PUT /api/v1/notifications/settings
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**요청**:
```json
{
  "emailEnabled": true,
  "daysBeforeDeadline": 7,
  "eligibilityRecommendationEnabled": true
}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "emailEnabled": true,
    "daysBeforeDeadline": 7,
    "eligibilityRecommendationEnabled": true,
    "updatedAt": "2026-04-13T14:00:00"
  }
}
```

### F-09-2. 마감일 알림 발송 (내부 스케줄러)

**설명**: 매일 정해진 시각에 스케줄러가 실행되어 마감 임박 정책을 북마크한 사용자에게 이메일을 발송한다.

**비즈니스 규칙**:
- 스케줄러가 매일 09:00에 실행
- `emailEnabled = true` 인 사용자만 대상
- 각 사용자의 `daysBeforeDeadline` 설정에 따라 대상 선별
- 북마크한 정책 중 status가 OPEN이고, `applyEnd - today <= daysBeforeDeadline` 인 정책 대상
- 동일 정책에 대해 중복 발송 방지 (발송 이력 관리, `type = DEADLINE`)

**이메일 내용 포함 항목**:
- 정책명
- 마감일
- YouthFit 정책 상세 페이지 링크
- 공식 신청 채널 링크

### F-09-3. 맞춤 정책 추천 알림 발송 (내부 스케줄러)

**설명**: 정해진 주기로 스케줄러가 실행되어 사용자의 적합도 프로필 기준으로 자격 가능성이 높은 신규 정책을 이메일로 추천한다.

**비즈니스 규칙**:
- 발송 주기: 주 1회 (예: 월요일 09:00), v0 기준
- `eligibilityRecommendationEnabled = true` 이고 `email`이 등록된 사용자만 대상
- 사용자 프로필에 나이·지역·고용상태 중 하나라도 없으면 해당 사용자에게는 발송하지 않음 (데이터 부족)
- 적합도 판정 엔진(F-06)을 이용해 신규 수집/업데이트된 OPEN 정책 중 `overallResult = LIKELY_ELIGIBLE` 인 정책을 추천 후보로 수집
- 북마크 이미 등록된 정책은 제외 (중복 추천 방지)
- 동일 정책에 대해 동일 사용자에게는 중복 추천 금지 (발송 이력 관리, `type = RECOMMENDATION`)
- 1회 발송 시 최대 5건, 추천할 정책이 없으면 발송 스킵
- 비싼 LLM 호출 없이 규칙 기반 적합도만 사용하여 비용 방어

**이메일 내용 포함 항목**:
- 제목: "이번 주 당신에게 맞을 수 있는 정책 N개"
- 정책명, 핵심 자격 요건 요약, YouthFit 정책 상세 페이지 링크
- 추천 해제(설정 페이지 링크) 안내

---

## 데이터 모델

### NotificationSetting

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK | 설정 ID |
| userId | Long | FK, UNIQUE | 사용자 FK |
| emailEnabled | boolean | NOT NULL, default true | 마감일 알림 수신 여부 |
| daysBeforeDeadline | int | NOT NULL, default 7 | 마감 N일 전 알림 |
| eligibilityRecommendationEnabled | boolean | NOT NULL, default false | 맞춤 정책 추천 알림 수신 여부 |
| updatedAt | LocalDateTime | NOT NULL | 수정 시각 |

### NotificationHistory

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK | 이력 ID |
| userId | Long | FK | 사용자 FK |
| policyId | Long | FK | 정책 FK |
| sentAt | LocalDateTime | NOT NULL | 발송 시각 |
| type | String | NOT NULL | `DEADLINE` \| `RECOMMENDATION` |

**유니크 제약**: `(userId, policyId, type)` — 동일 채널/정책/사용자 중복 발송 방지

---

## 의존 관계

- `user(notification)` → `user(bookmark)`: 마감일 알림 대상 조회
- `user(notification)` → `user.profile`: 적합도 프로필 조회 (추천 알림)
- `user(notification)` → `policy.domain`: 정책 마감일·상태·신규 정책 조회
- `user(notification)` → `eligibility.domain`: 적합도 판정 (추천 알림)
- `user(notification)` → 이메일 발송 인프라 (AWS SES 등)
