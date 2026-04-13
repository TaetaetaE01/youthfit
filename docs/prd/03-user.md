# PRD — 사용자 프로필 (user 모듈)

> **기능 ID**: F-05
> **모듈**: `com.youthfit.user`
> **우선순위**: P0
> **구현 상태**: 완료

---

## 유저 스토리

### 프로필 조회 및 수정
민지가 마이페이지에서 카카오에서 가져온 닉네임과 프로필 이미지를 확인한다. 닉네임을 "민지_취준생"으로 변경하고 저장한다. 이후 적합도 판정에 필요한 추가 정보(나이, 거주 지역, 소득 수준, 고용 상태 등)를 입력한다.

---

## 기능 요구사항

### F-05-1. 내 프로필 조회

**설명**: 로그인한 사용자의 프로필 정보를 조회한다.

**비즈니스 규칙**:
- 인증 필수
- JWT에서 userId를 추출하여 조회

**API 스펙**:

```
GET /api/v1/users/me
Authorization: Bearer {accessToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "minji@kakao.com",
    "nickname": "민지",
    "profileImageUrl": "https://...",
    "createdAt": "2026-04-01T09:00:00"
  }
}
```

### F-05-2. 내 프로필 수정

**설명**: 닉네임과 프로필 이미지를 수정한다.

**비즈니스 규칙**:
- 인증 필수
- 닉네임은 필수, 최대 50자
- 프로필 이미지 URL은 선택

**API 스펙**:

```
PATCH /api/v1/users/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**요청**:
```json
{
  "nickname": "민지_취준생",
  "profileImageUrl": "https://..."
}
```

| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| nickname | String | Y | @NotBlank, @Size(max=50) |
| profileImageUrl | String | N | - |

**응답 (200 OK)**: 프로필 조회(F-05-1)와 동일한 구조

---

## 데이터 모델

### User 엔티티

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | 사용자 ID |
| email | String | NOT NULL, UNIQUE | 이메일 |
| nickname | String(50) | NOT NULL | 닉네임 |
| profileImageUrl | String | - | 프로필 이미지 URL |
| authProvider | Enum(AuthProvider) | NOT NULL | KAKAO |
| providerId | String | NOT NULL | OAuth provider 사용자 ID |
| role | Enum(Role) | NOT NULL | USER, ADMIN |
| refreshToken | String | - | JWT refresh token |
| createdAt | LocalDateTime | NOT NULL | 가입 시각 |
| updatedAt | LocalDateTime | NOT NULL | 수정 시각 |

**도메인 규칙**:
- 닉네임은 빈 값 불허
- 기본 role은 USER
- 프로필 수정은 `updateProfile(nickname, profileImageUrl)` 도메인 메서드로 수행
