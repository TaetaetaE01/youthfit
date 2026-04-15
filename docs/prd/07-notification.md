# PRD — 마감일 이메일 알림 (user/notification)

> **기능 ID**: F-09
> **모듈**: `com.youthfit.user` (notification 하위 도메인)
> **우선순위**: P2
> **구현 상태**: 미구현
> **선행 조건**: 북마크(F-08) 구현 완료

---

## 유저 스토리

### 이메일 알림 설정
민지(페르소나 A)가 북마크한 정책 중 "청년내일채움공제"의 마감일이 7일 남았을 때 이메일 알림을 받는다. 이메일에는 정책명, 마감일, YouthFit 상세 페이지 링크, 공식 신청 채널 링크가 포함된다.

---

## 기능 요구사항

### F-09-1. 알림 설정 관리

**설명**: 북마크한 정책의 마감일 전 이메일 알림 수신을 설정한다.

**비즈니스 규칙**:
- 인증 필수
- 알림 기본값: 마감 7일 전
- 설정 가능 옵션: 3일 전, 7일 전, 14일 전
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
  "daysBeforeDeadline": 7
}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "emailEnabled": true,
    "daysBeforeDeadline": 7,
    "updatedAt": "2026-04-13T14:00:00"
  }
}
```

### F-09-2. 알림 발송 (내부 스케줄러)

**설명**: 매일 정해진 시각에 스케줄러가 실행되어 마감 임박 정책을 가진 사용자에게 이메일을 발송한다.

**비즈니스 규칙**:
- 스케줄러가 매일 09:00에 실행
- 각 사용자의 daysBeforeDeadline 설정에 따라 대상 선별
- 북마크한 정책 중 status가 OPEN이고, applyEnd - today <= daysBeforeDeadline인 정책 대상
- 동일 정책에 대해 중복 발송 방지 (발송 이력 관리)

**이메일 내용 포함 항목**:
- 정책명
- 마감일
- YouthFit 정책 상세 페이지 링크
- 공식 신청 채널 링크

---

## 데이터 모델

### NotificationSetting

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK | 설정 ID |
| userId | Long | FK, UNIQUE | 사용자 FK |
| emailEnabled | boolean | NOT NULL, default true | 이메일 수신 여부 |
| daysBeforeDeadline | int | NOT NULL, default 7 | 마감 N일 전 알림 |
| updatedAt | LocalDateTime | NOT NULL | 수정 시각 |

### NotificationHistory

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK | 이력 ID |
| userId | Long | FK | 사용자 FK |
| policyId | Long | FK | 정책 FK |
| sentAt | LocalDateTime | NOT NULL | 발송 시각 |
| type | String | NOT NULL | EMAIL |

**유니크 제약**: (userId, policyId, type) — 중복 발송 방지

---

## 의존 관계

- `user(notification)` → `user(bookmark)`: 북마크 목록 조회
- `user(notification)` → `policy.domain`: 정책 마감일, 상태 조회
- `user(notification)` → 이메일 발송 인프라 (AWS SES 등)
