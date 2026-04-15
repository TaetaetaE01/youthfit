# PRD — 북마크 (user/bookmark)

> **기능 ID**: F-08
> **모듈**: `com.youthfit.user` (bookmark 하위 도메인)
> **우선순위**: P1
> **구현 상태**: 미구현

---

## 유저 스토리

### 북마크 추가 및 관리
민지(페르소나 A)가 관심 있는 정책 3개를 북마크한다. 마이페이지의 북마크 탭에서 저장된 정책을 모아 볼 수 있다. 더 이상 관심 없는 정책은 북마크를 해제한다.

---

## 기능 요구사항

### F-08-1. 북마크 추가

**설명**: 정책을 북마크 목록에 추가한다.

**비즈니스 규칙**:
- 인증 필수
- 동일 정책 중복 북마크 시 409 응답
- 존재하지 않는 정책 ID 시 404 응답

**API 스펙**:

```
POST /api/v1/bookmarks
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**요청**:
```json
{
  "policyId": 1
}
```

**응답 (201 Created)**:
```json
{
  "success": true,
  "data": {
    "bookmarkId": 10,
    "policyId": 1,
    "createdAt": "2026-04-13T14:00:00"
  }
}
```

### F-08-2. 북마크 삭제

**비즈니스 규칙**:
- 인증 필수
- 본인의 북마크만 삭제 가능

```
DELETE /api/v1/bookmarks/{bookmarkId}
Authorization: Bearer {accessToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": null
}
```

### F-08-3. 내 북마크 목록 조회

**비즈니스 규칙**:
- 인증 필수
- 페이지네이션 지원
- 정책 요약 정보 함께 반환

```
GET /api/v1/bookmarks?page={page}&size={size}
Authorization: Bearer {accessToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "totalElements": 3,
    "totalPages": 1,
    "currentPage": 0,
    "size": 20,
    "content": [
      {
        "bookmarkId": 10,
        "policy": {
          "id": 1,
          "title": "2026 청년월세지원",
          "category": "HOUSING",
          "status": "OPEN",
          "applyEnd": "2026-05-31"
        },
        "createdAt": "2026-04-13T14:00:00"
      }
    ]
  }
}
```

---

## 데이터 모델

### Bookmark

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | 북마크 ID |
| userId | Long | FK, NOT NULL | 사용자 FK |
| policyId | Long | FK, NOT NULL | 정책 FK |
| createdAt | LocalDateTime | NOT NULL | 생성 시각 |

**유니크 제약**: (userId, policyId) 복합 유니크

---

## 의존 관계

- `user(bookmark)` → `policy.domain`: 정책 존재 여부 확인, 정책 요약 정보 조회
