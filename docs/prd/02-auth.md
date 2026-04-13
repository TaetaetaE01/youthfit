# PRD — 인증 (auth 모듈)

> **기능 ID**: F-04
> **모듈**: `com.youthfit.auth`
> **우선순위**: P0
> **구현 상태**: 완료

---

## 유저 스토리

### 카카오 로그인 후 토큰 발급
민지(페르소나 A)가 "카카오로 시작하기" 버튼을 누르면 카카오 인증 페이지로 이동한다. 동의 후 YouthFit으로 돌아오면 자동으로 계정이 생성되고 JWT access token과 refresh token이 발급된다. 이후 인증이 필요한 기능(프로필, 북마크, 적합도 판정)을 이용할 수 있다.

### 토큰 갱신 및 로그아웃
Access token이 만료되면 클라이언트가 refresh token으로 새 토큰을 발급받는다. 로그아웃 시 서버의 refresh token이 폐기되어 해당 세션으로는 더 이상 갱신이 불가능하다.

---

## 기능 요구사항

### F-04-1. 카카오 로그인

**설명**: 카카오 OAuth 인가 코드를 받아 사용자를 인증하고 JWT를 발급한다. 최초 로그인 시 자동으로 회원 가입이 진행된다.

**비즈니스 규칙**:
- 카카오에서 발급한 인가 코드(authorization code)를 서버로 전달
- 서버가 카카오 API를 호출하여 사용자 정보(이메일, 닉네임, 프로필 이미지) 획득
- 기존 회원이면 로그인 처리, 신규 사용자이면 자동 회원 가입 후 로그인
- access token과 refresh token을 함께 발급

**API 스펙**:

```
POST /api/auth/kakao
Content-Type: application/json
```

**요청**:
```json
{
  "code": "카카오_인가_코드"
}
```

| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| code | String | Y | @NotBlank |

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG..."
  }
}
```

### F-04-2. 토큰 갱신

**설명**: refresh token으로 새로운 access token을 발급받는다.

**비즈니스 규칙**:
- 유효한 refresh token이어야 함
- 만료되었거나 DB에 저장된 값과 불일치 시 401 응답

**API 스펙**:

```
POST /api/auth/refresh
Content-Type: application/json
```

**요청**:
```json
{
  "refreshToken": "eyJhbG..."
}
```

**응답 (200 OK)**: 카카오 로그인(F-04-1)과 동일한 구조

### F-04-3. 로그아웃

**설명**: 사용자의 refresh token을 폐기한다.

**비즈니스 규칙**:
- 인증 필수 (Authorization: Bearer {accessToken})
- 서버의 refresh token을 null로 초기화

**API 스펙**:

```
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

**응답 (200 OK)**:
```json
{
  "success": true,
  "data": null
}
```

---

## 보안 요구사항

| 항목 | 요구사항 |
|------|---------|
| access token 만료 | 30분 이내 |
| refresh token 저장 | 서버 DB (User.refreshToken) |
| 클라이언트 저장 | httpOnly 쿠키 또는 안전한 저장소 |
| 로그아웃 | refresh token 서버에서 폐기 |

---

## 의존 관계

- `auth` → `user.domain`: 사용자 조회 및 생성 (UserRepository)
- `auth`는 `user.application`에 의존하지 않고 `user.domain.repository` 직접 사용
