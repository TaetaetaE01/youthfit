# 어드민 페이지 — Spec 1: 공통 기반 (Foundation) 설계

> **상태**: Draft
> **작성일**: 2026-05-05
> **범위**: 어드민 영역의 인증·라우팅·레이아웃 토대 (시리즈의 첫 번째 spec)
> **언어**: 백엔드 Java 21 / Spring Boot 4 / 프론트 React 19 + TS5

---

## 1. 배경

YouthFit은 v0에서 관리자 대시보드를 제외했으나, 운영 단계에 들어서면서 다음 항목을 자체 어드민에서 추적할 필요가 생겼다.

- ① 이메일 발송 추적 (성공/실패/바운스)
- ② Q&A semantic-cache hit/miss 로그
- ③ LLM 비용/토큰 사용량
- ④ Ingestion(n8n 수신) 헬스

DAU·예외·인프라 메트릭 등 *시스템 모니터링*은 Grafana로 분리하고, 본 어드민은 **비즈니스 로그·운영 추적** 영역만 담당한다.

## 2. 시리즈 구성 (5개 Spec으로 분할)

본 문서는 **Spec 1: 공통 기반**만 다룬다. 후속 4개 spec은 동일한 패턴(자기 도메인에 적재 + admin 모듈에 ReadModel 컨트롤러 추가)을 따른다.

| # | Spec | 산출물 |
|---|---|---|
| 1 | **공통 기반 (이 문서)** | 인증·라우팅·레이아웃·뼈대 admin 모듈 |
| 2 | 이메일 발송 추적 | `EmailSendLog` 적재 + 건별/집계 화면 |
| 3 | Q&A 캐시 hit/miss 로그 | semantic-cache 인프라에 logging 추가 + 매칭 디버깅 화면 |
| 4 | LLM 비용 대시보드 | Q&A·가이드·임베딩 표준 metrics 인터셉터 + 집계 차트 (집계만) |
| 5 | Ingestion 헬스 | 수신/정규화 통계·실패 로그 화면 |

각 후속 spec은 별도 brainstorming → spec → plan 사이클로 진행한다.

## 3. 목표 / 비목표

### 목표
- 본인 한 명(카카오 계정 1개)이 `/admin`에 진입해 어드민 화면을 볼 수 있다.
- 일반 사용자가 `/admin` 또는 `/api/admin/**`에 접근하면 막힌다.
- ROLE_ADMIN 사용자는 일반 서비스(정책·Q&A·북마크 등)를 그대로 사용 가능하다.
- 후속 4개 spec이 같은 라우트 트리·인증 구조에 합류할 수 있는 토대가 갖춰진다.

### 비목표 (이 spec에선 안 함)
- 어드민 임명 화면 — 첫 출시 시 DB에서 직접 UPDATE.
- 실제 데이터 화면(이메일/Q&A/LLM/Ingestion 4개) — 후속 spec.
- 어드민 전용 SPA 분리 빌드.
- 권한 세분화(VIEWER/EDITOR 등) — 본인 한 명이라 YAGNI.

## 4. 기술 결정 사항

### 4.1 인증 방식 — 카카오 로그인 + `user.role=ADMIN` 재사용

이미 `user.domain.model.Role` enum에 `USER`, `ADMIN`이 정의되어 있고, JWT 토큰에 role이 포함되어 `JwtAuthenticationFilter`가 `ROLE_USER`/`ROLE_ADMIN` 권한을 부여 중이다. 별도 admin 인증 인프라를 만들 필요 없이 이 흐름을 그대로 사용한다.

### 4.2 라우팅 — 기존 React SPA에 `/admin/*` 추가

별도 admin SPA 빌드는 본인 한 명이 사용하는 상황에서 오버. 기존 `App.tsx`의 `BrowserRouter`에 라우트만 추가한다.

### 4.3 ADMIN 임명 — DB 수동 UPDATE

```sql
UPDATE users SET role = 'ADMIN' WHERE email = '본인 카카오 이메일';
```

마이그레이션·시드·관리 화면 모두 첫 spec에서는 만들지 않는다. 본인 1명만 임명한다.

### 4.4 모듈 배치 — 새 `admin` 도메인 모듈 신설

- `backend/src/main/java/com/youthfit/admin/{application,domain,infrastructure,presentation}` 구조 (DDD + Clean Architecture 준수).
- 첫 spec에선 `presentation`만 채워지고 도메인 모델은 비어 있음.
- 후속 spec은 자기 도메인(user/qna/...)에 데이터 적재 → admin 모듈에 ReadModel 조회 컨트롤러를 추가하는 패턴.
- admin 모듈은 다른 도메인의 application port를 통해 조회한다. 다른 도메인은 admin을 모른다 (의존 방향: admin → other domains).

### 4.5 역할 계층 — `ROLE_ADMIN > ROLE_USER`

Spring Security `RoleHierarchyImpl` 빈으로 등록. 한 카카오 계정으로 일반 사용자 기능과 어드민 기능을 모두 사용할 수 있도록 한다.

## 5. 변경 사항 상세

### 5.1 백엔드

#### (1) `UserProfileResponse`에 role 노출

- `application/dto/result/UserProfileResult` — `Role role` 필드 추가
- `presentation/dto/response/UserProfileResponse` — `String role` 필드 추가 (도메인 enum 직접 노출 금지, `Role.name()` 사용)
- `UserProfileService` (또는 변환 로직) — User 엔티티의 role을 결과 DTO에 매핑

#### (2) `SecurityConfig` 업데이트

`backend/src/main/java/com/youthfit/common/config/SecurityConfig.java`

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/v1/policies/**").permitAll()
    // ... 기존 permitAll 규칙들 ...
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")   // ← 추가
    .anyRequest().authenticated()
)
```

`RoleHierarchy` 빈 추가:

```java
@Bean
public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
}
```

#### (3) `admin` 모듈 신설 (뼈대만)

```
backend/src/main/java/com/youthfit/admin/
└── presentation/
    ├── controller/
    │   ├── AdminPingApi.java          # @Tag("Admin"), @Operation
    │   └── AdminPingController.java   # implements AdminPingApi
    └── dto/response/
        └── AdminPingResponse.java     # record (message: "pong", serverTime)
```

- 엔드포인트: `GET /api/v1/admin/ping` → `200 { message: "pong", serverTime: <ISO> }`
- 도메인 모델·application 서비스·infrastructure 없음. 컨트롤러가 직접 응답 (스모크 테스트용).
- Swagger: `@Tag(name = "Admin")` 인터페이스에 부착.

### 5.2 프론트엔드

#### (1) 타입 & store 갱신

- `types/policy.ts`(또는 적절한 user 타입 파일)의 `UserProfile`에 `role: 'USER' | 'ADMIN'` 추가
- `stores/authStore.ts`에 `role: 'USER' | 'ADMIN' | null` 추가
  - login 시: 기존 토큰 저장 + me() 호출 결과에서 role 저장 (혹은 callback 페이지에서 me 호출 후 저장)
  - logout 시: role을 null로 초기화

#### (2) 가드 컴포넌트

`frontend/src/components/auth/RequireAdmin.tsx`

```tsx
export function RequireAdmin() {
  const { isAuthenticated, role } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login?redirect_to=/admin" replace />;
  }
  if (role !== 'ADMIN') {
    // 토스트 + 홈 redirect
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
```

현재 코드베이스에는 라우트 레벨 인증 가드가 없으므로, 본 spec에서 신설하는 `RequireAdmin`이 첫 번째 가드 컴포넌트가 된다. 일반 사용자용 가드(`RequireAuth`)가 필요해지면 같은 패턴으로 추후 추가한다.

#### (3) 어드민 레이아웃 & 페이지

- `frontend/src/components/layout/AdminLayout.tsx`
  - 좌측 사이드바: 메뉴 4개 항목 표시 (이메일 발송 / Q&A 캐시 로그 / LLM 비용 / Ingestion 헬스)
  - 첫 spec에선 메뉴 클릭 시 `/admin/coming-soon` 또는 placeholder 페이지로 이동 (각 메뉴는 후속 spec에서 실제 라우트로 교체)
  - 헤더: "관리자" 타이틀 + 본인 닉네임 + 로그아웃
- `frontend/src/pages/admin/AdminDashboardPage.tsx`
  - 빈 카드 4개 (각 항목 자리만 잡기)
  - ping 호출 결과를 페이지 하단에 한 줄로 표시 (스모크 확인용)

#### (4) API 함수 & 훅

- `frontend/src/apis/admin.api.ts`

```ts
import api from './client';

export interface AdminPingResponse { message: string; serverTime: string; }

export async function pingAdmin(): Promise<AdminPingResponse> {
  const res = await api.get('v1/admin/ping').json<{ data: AdminPingResponse }>();
  return res.data;
}
```

- `frontend/src/hooks/queries/useAdminPing.ts` — useQuery 래퍼

> 본 spec의 모든 admin API 경로는 `/api/v1/admin/**`로 통일한다 (기존 `/api/v1/policies`, `/api/v1/users/me` 등과 일관). ky 클라이언트의 baseURL 규약상 호출은 `v1/admin/...` 형태로 작성한다.

#### (5) 라우팅 (`App.tsx`)

```tsx
<Route element={<RequireAdmin />}>
  <Route path="/admin" element={<AdminLayout />}>
    <Route index element={<AdminDashboardPage />} />
    {/* 후속 spec에서 자식 라우트 추가 */}
  </Route>
</Route>
```

## 6. 데이터 흐름

```
[로그인 시]
사용자 → 카카오 → /api/auth/kakao/callback → JWT(role 포함) 발급
       → 프론트: 토큰 저장 + /api/v1/users/me 호출
       → me 응답에서 role 받아 authStore에 저장

[/admin 진입 시]
RequireAdmin → authStore.isAuthenticated, role 체크
   - 비인증 → /login?redirect_to=/admin
   - role !== ADMIN → / (토스트)
   - role === ADMIN → AdminLayout 렌더 → ping 호출 → "pong" 표시

[보안 이중 가드]
프론트 가드를 우회해 /api/v1/admin/** 호출 시
   → JwtAuthenticationFilter가 인증 처리
   → SecurityConfig가 hasRole("ADMIN") 체크 → 미달 시 403
```

## 7. 에러 처리

| 상황 | 처리 |
|---|---|
| 비로그인 사용자가 `/admin` 진입 | `RequireAdmin`가 `/login?redirect_to=/admin`으로 redirect |
| `role=USER`가 `/admin` 진입 | `/`로 redirect + 토스트 "관리자 권한이 필요합니다" |
| 프론트 우회로 `/api/v1/admin/**` 직접 호출 | 백엔드: 미인증 401 (`JwtAuthenticationEntryPoint`), 권한 부족 403 (Spring Security 기본) |
| `/api/v1/users/me` 응답에 role 누락 | 프론트는 role을 `null`로 처리 — ADMIN 아닌 것으로 안전 디폴트 |
| ping 호출 실패 (네트워크/서버 다운) | AdminDashboardPage에서 토스트 + 재시도 버튼. 페이지 자체는 렌더 |

**원칙**: 보안 결정은 백엔드가 최종 권위. 프론트 가드는 UX(불필요한 화면 안 그리기) 목적.

## 8. 테스트

### 8.1 백엔드 (JUnit 5)

- **`SecurityConfigTest`** (`@WebMvcTest` 슬라이스)
  - `/api/v1/admin/ping` — 비인증 → 401
  - `/api/v1/admin/ping` — `ROLE_USER` → 403
  - `/api/v1/admin/ping` — `ROLE_ADMIN` → 200
- **`RoleHierarchyTest`**
  - `ROLE_ADMIN`이 일반 `/api/v1/users/me` 같은 ROLE_USER 전용 엔드포인트도 통과하는지
- **`UserProfileServiceTest`** — me() 결과에 role 매핑이 올바른지 (USER/ADMIN 두 케이스)
- **`AdminPingControllerTest`** — 200 응답 + 본문 형태 (`message`, `serverTime` 존재) 검증

### 8.2 프론트엔드 (Vitest + Testing Library)

- **`RequireAdmin.test.tsx`**
  - 비인증 → `/login`으로 navigate
  - `role=USER` → `/`로 navigate
  - `role=ADMIN` → children 렌더
- **`AdminLayout.test.tsx`** — 사이드바 메뉴 4개 노출, 각 클릭 시 "준비 중" 안내
- **`AdminDashboardPage.test.tsx`** — ping 성공 시 "pong" 표시, 실패 시 에러 토스트
- **`authStore.test.ts`** — login/logout 시 role 변경

### 8.3 수동 검증

- 본인 카카오 계정으로 로그인 → DB에서 `role`을 ADMIN으로 UPDATE → `/admin` 접속 가능 확인
- 다른 일반 계정 → `/admin` 접속 시 `/`로 redirect 확인
- ROLE_ADMIN 계정으로 일반 페이지(/policies, /mypage 등) 정상 동작 확인 (RoleHierarchy 검증)

## 9. 마이그레이션 / 운영

- 별도 DB 마이그레이션 없음. `users.role` 컬럼은 이미 존재한다고 가정 (Role enum 사용 중).
  - **확인 필요**: 실제 DB 컬럼이 있는지, 있다면 디폴트 `'USER'`인지. 없으면 Flyway 마이그레이션 1줄 추가.
- 환경 변수 변경 없음.
- 배포 후 본인 카카오로 1회 로그인 → DB UPDATE → 재로그인(JWT 재발급) → `/admin` 접근.
  - JWT에 role이 박혀 있으므로 DB UPDATE 후에는 **반드시 재로그인** 필요. 운영 노트에 기록.

## 10. 의존 / 후속 작업

### 이 spec이 통과해야 후속 spec이 가능
- Spec 2~5 모두 `/api/v1/admin/**` 경로 + `RequireAdmin` 가드를 그대로 재사용한다.

### 후속 spec에서 정해질 것
- 데이터 적재 위치(각 도메인) 및 ReadModel 조회 패턴
- 보관 정책 (스킬 가이드: 항목별 다르게)
- 차트/테이블 컴포넌트 라이브러리 선택 (shadcn/ui + 차트 라이브러리)

## 11. 열려 있는 질문 (후속 spec에서 다룸)

- 차트/테이블 라이브러리 선택 (shadcn/ui + recharts 등) — Spec 2에서 첫 화면 만들 때 결정.
- 항목별 데이터 보관 기간 — 각 후속 spec에서 결정.
- 어드민 작업 감사 로그(audit trail) 필요 여부 — 본 시리즈가 조회 위주라 우선 도입 안 함, 향후 변경 작업 추가 시 재검토.

---

## 부록 A. 변경 파일 목록 (요약)

**백엔드 (신규)**
- `admin/presentation/controller/AdminPingApi.java`
- `admin/presentation/controller/AdminPingController.java`
- `admin/presentation/dto/response/AdminPingResponse.java`

**백엔드 (수정)**
- `common/config/SecurityConfig.java` — `/api/v1/admin/**` 보호 + `RoleHierarchy` 빈
- `user/application/dto/result/UserProfileResult.java` — `Role role` 필드 추가
- `user/presentation/dto/response/UserProfileResponse.java` — `String role` 필드 추가
- `user/application/service/UserProfileService.java`(또는 동등 위치) — role 매핑

**프론트엔드 (신규)**
- `components/auth/RequireAdmin.tsx`
- `components/layout/AdminLayout.tsx`
- `pages/admin/AdminDashboardPage.tsx`
- `apis/admin.api.ts`
- `hooks/queries/useAdminPing.ts`

**프론트엔드 (수정)**
- `App.tsx` — `/admin/*` 라우트 추가
- `stores/authStore.ts` — role 필드
- `types/policy.ts`(또는 user 타입) — `UserProfile.role`
- `apis/auth.api.ts` 또는 콜백 페이지 — 로그인 직후 me() 호출하여 role 저장 (현재 흐름에 맞게)

**테스트 (신규)**
- 백엔드: `SecurityConfigTest`, `RoleHierarchyTest`, `AdminPingControllerTest`, `UserProfileServiceTest` 갱신
- 프론트엔드: `RequireAdmin.test.tsx`, `AdminLayout.test.tsx`, `AdminDashboardPage.test.tsx`, `authStore.test.ts` 갱신
