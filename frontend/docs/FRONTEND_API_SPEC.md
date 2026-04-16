# Frontend API Spec

> 각 화면에서 사용하는 백엔드 API, Query/Mutation 훅, 주요 로직을 정리한 문서.
>
> **최종 갱신**: 2026-04-16

---

## 1. API 엔드포인트 요약

### 1.1 Policy (인증 불필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| GET | `/api/v1/policies` | 정책 목록 (필터/정렬/페이지네이션) | 직접 반환 |
| GET | `/api/v1/policies/search` | 키워드 검색 | 직접 반환 |
| GET | `/api/v1/policies/{policyId}` | 정책 상세 | 직접 반환 |

### 1.2 Guide (인증 불필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| GET | `/api/v1/guides/{policyId}` | AI 가이드 요약 (HTML) | `ApiResponse<T>` |

### 1.3 Auth

| Method | Endpoint | 설명 | 인증 | 응답 래퍼 |
|--------|----------|------|------|-----------|
| POST | `/api/auth/kakao` | 카카오 로그인 | 불필요 | `ApiResponse<T>` |
| POST | `/api/auth/refresh` | 토큰 갱신 | 불필요 | `ApiResponse<T>` |
| POST | `/api/auth/logout` | 로그아웃 | 필요 | `ApiResponse<T>` |

### 1.4 Bookmark (인증 필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| GET | `/api/v1/bookmarks` | 북마크 목록 | `ApiResponse<T>` |
| POST | `/api/v1/bookmarks` | 북마크 추가 | `ApiResponse<T>` |
| DELETE | `/api/v1/bookmarks/{bookmarkId}` | 북마크 삭제 | `ApiResponse<T>` |

### 1.5 User Profile (인증 필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| GET | `/api/v1/users/me` | 내 프로필 조회 | `ApiResponse<T>` |
| PATCH | `/api/v1/users/me` | 프로필 수정 | `ApiResponse<T>` |

### 1.6 Notification (인증 필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| GET | `/api/v1/notifications/settings` | 알림 설정 조회 | `ApiResponse<T>` |
| PUT | `/api/v1/notifications/settings` | 알림 설정 변경 | `ApiResponse<T>` |

### 1.7 Eligibility (인증 필요)

| Method | Endpoint | 설명 | 응답 래퍼 |
|--------|----------|------|-----------|
| POST | `/api/v1/eligibility/judge` | 적합도 판정 | `ApiResponse<T>` |

### 1.8 Q&A (인증 필요)

| Method | Endpoint | 설명 | 응답 형식 |
|--------|----------|------|-----------|
| POST | `/api/v1/qna/ask` | 정책 Q&A (SSE 스트리밍) | `text/event-stream` |

---

## 2. 화면별 API 연결 상세

### 2.1 LandingPage (`/`)

| 항목 | 내용 |
|------|------|
| API 호출 | 없음 |
| 로직 | 정적 마케팅 페이지. IntersectionObserver 기반 스크롤 애니메이션, 카운트업 효과 |

---

### 2.2 PolicyListPage (`/policies`)

| 항목 | 내용 |
|------|------|
| Query 훅 | `usePolicies({ keyword, category, status, regionCode, sortBy, ascending, page, size })` |
| API | `GET /api/v1/policies` 또는 `GET /api/v1/policies/search` (keyword 유무에 따라 분기) |
| 상태 관리 | URL searchParams로 필터/정렬/페이지 상태 관리 |

**주요 로직:**
- **검색**: 입력 300ms 디바운스 후 URL keyword 파라미터 업데이트 → `usePolicies` 자동 재요청
- **필터**: 카테고리(7종), 모집상태(3종), 지역(17종)을 URL 파라미터로 관리
- **정렬**: `createdAt:desc`(최신순) / `applyEnd:asc`(마감임박순)
- **페이지네이션**: 페이지당 6개, `keepPreviousData`로 이전 데이터 유지하며 전환
- **모바일 필터**: 바텀시트로 필터 UI 제공

**응답 타입:**
```typescript
interface PolicyPage {
  content: Policy[];      // 정책 목록
  totalCount: number;     // 전체 개수
  page: number;           // 현재 페이지 (0-based)
  size: number;           // 페이지 크기
  totalPages: number;     // 전체 페이지 수
  hasNext: boolean;       // 다음 페이지 존재 여부
}
```

---

### 2.3 PolicyDetailPage (`/policies/:policyId`)

| 항목 | 내용 |
|------|------|
| Query 훅 | `usePolicy(policyId)`, `useGuide(policyId)` |
| Mutation 훅 | `useAddBookmark()`, `useRemoveBookmark()`, `useJudgeEligibility()` |
| API (SSE) | `fetchQnaAnswer()` → `POST /api/v1/qna/ask` |
| 인증 확인 | `useAuthStore` — 적합도 판정, Q&A, 북마크는 로그인 필요 |

**주요 로직:**
- **정책 상세**: `usePolicy`로 기본 정보 조회
- **AI 가이드**: `useGuide`로 AI 요약 HTML 조회 (별도 API, 가이드 미생성 시 표시 안 함)
- **북마크**: `useAddBookmark` / `useRemoveBookmark`로 토글, 성공 시 로컬 상태 업데이트 + 캐시 무효화
- **적합도 판정**: `useJudgeEligibility` mutation, 버튼 클릭 시 1회 호출, 결과를 로컬 상태에 저장
- **Q&A 스트리밍**: `fetchQnaAnswer`로 SSE fetch, 청크 단위로 메시지 상태 업데이트, 완료 시 출처 표시

**적합도 응답 타입:**
```typescript
interface EligibilityResponse {
  policyId: number;
  policyTitle: string;
  overallResult: 'LIKELY_ELIGIBLE' | 'UNCERTAIN' | 'LIKELY_INELIGIBLE';
  criteria: CriterionItem[];    // 항목별 판정 결과
  missingFields: string[];      // 추가 입력 필요 필드
  disclaimer: string;           // 면책 문구
}

interface CriterionItem {
  field: string;                // 필드 ID
  label: string;                // 표시명 (예: "연령 조건")
  result: EligibilityResult;    // 판정 결과
  reason: string;               // 판정 사유
  sourceReference: string;      // 원문 근거
}
```

**Q&A SSE 요청/응답:**
```typescript
// Request body
{ policyId: number; question: string }

// SSE event data 형식
{ type: 'content', text: string }    // 답변 청크
{ type: 'sources', sources: string[] } // 출처 목록
'[DONE]'                              // 스트림 종료
```

---

### 2.4 LoginPage (`/login`)

| 항목 | 내용 |
|------|------|
| API 호출 | 없음 (카카오 OAuth 리다이렉트만 수행) |
| 로직 | 카카오 인증 URL로 `window.location.href` 이동 |

---

### 2.5 KakaoCallbackPage (`/auth/kakao/callback`)

| 항목 | 내용 |
|------|------|
| API | `POST /api/auth/kakao` |
| 로직 | URL의 `code` 파라미터로 백엔드에 토큰 교환 요청 → `useAuthStore.login(accessToken)` → 리다이렉트 |

**응답 타입:**
```typescript
interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}
```

---

### 2.6 MyPage (`/mypage`)

| 항목 | 내용 |
|------|------|
| Query 훅 | `useProfile()`, `useBookmarks()`, `useNotificationSettings()` |
| Mutation 훅 | `useUpdateProfile()`, `useRemoveBookmark()`, `useUpdateNotificationSettings()` |
| 인증 | 필수 — 미인증 시 `/login?redirect=/mypage`로 리다이렉트 |

**주요 로직:**

#### 프로필 섹션
- `useProfile` → `GET /api/v1/users/me`로 닉네임, 이메일, 프로필 이미지 조회
- 닉네임 인라인 수정 → `useUpdateProfile` → `PATCH /api/v1/users/me`
- 성공 시 `setQueryData`로 즉시 캐시 반영

#### 북마크 탭
- `useBookmarks` → `GET /api/v1/bookmarks`로 북마크 목록 조회
- 북마크 삭제: fade-out 애니메이션(300ms) → `useRemoveBookmark` → `DELETE /api/v1/bookmarks/{id}`
- 삭제 후 Toast로 "되돌리기" 안내

#### 알림 설정 탭
- `useNotificationSettings` → `GET /api/v1/notifications/settings`
- 이메일 알림 토글 / 알림 시점(3일, 7일, 14일 전) 변경 → `useUpdateNotificationSettings` → `PUT /api/v1/notifications/settings`
- Optimistic UI: 로컬 상태 먼저 변경 후 API 호출

**프로필 응답 타입:**
```typescript
interface UserProfile {
  id: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  createdAt: string;
}
```

**북마크 응답 타입:**
```typescript
interface BookmarkPage {
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
  content: Bookmark[];
}

interface Bookmark {
  bookmarkId: number;
  policy: BookmarkPolicy;    // 간소화된 정책 정보
  createdAt: string;
}

interface BookmarkPolicy {
  id: number;
  title: string;
  category: string;          // enum name (예: "HOUSING")
  status: string;            // enum name (예: "OPEN")
  applyEnd: string;          // 마감일
}
```

---

## 3. 공통 인프라

### 3.1 HTTP Client (`apis/client.ts`)

- **라이브러리**: ky
- **Base prefix**: `/api` (Vite proxy → `localhost:8080`)
- **인증**: `beforeRequest` 훅에서 `Authorization: Bearer {token}` 자동 첨부
- **401 처리**: `afterResponse` 훅에서 `useAuthStore.logout()` 호출

### 3.2 상태 관리

| 상태 유형 | 도구 | 예시 |
|-----------|------|------|
| 서버 데이터 | TanStack Query | 정책, 프로필, 북마크, 알림 설정 |
| 인증 토큰 | Zustand + localStorage | accessToken, isAuthenticated |
| URL 상태 | React Router searchParams | 필터, 검색어, 페이지 |
| 로컬 UI | React useState | 모달, 편집 모드, Q&A 메시지 |

### 3.3 캐시 무효화 전략

| Mutation | 무효화 대상 |
|----------|------------|
| `useAddBookmark` | `['bookmarks']`, `['policy', policyId]` |
| `useRemoveBookmark` | `['bookmarks']`, `['policies']` |
| `useUpdateProfile` | `['profile']` (setQueryData) |
| `useUpdateNotificationSettings` | `['notificationSettings']` (setQueryData) |

### 3.4 Query 기본 설정

```typescript
{
  staleTime: 5 * 60 * 1000,  // 5분
  retry: 1,
}
```

---

## 4. 백엔드 응답 래퍼

Policy 엔드포인트 3개(`/api/v1/policies`, `/search`, `/{id}`)는 응답을 **직접 반환**한다.
나머지 모든 엔드포인트는 `ApiResponse<T>` 래퍼를 사용한다.

```typescript
interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string };  // NON_NULL — 에러 시에만 포함
}
```

---

## 5. 미구현 / 갭 사항

| 항목 | 현재 상태 | 비고 |
|------|----------|------|
| 사용자 확장 프로필 (나이, 지역, 소득, 고용상태) | 백엔드 미구현 | `UserProfileResponse`에 기본 필드만 존재. 적합도 판정에 필요한 확장 필드는 백엔드 추가 작업 필요 |
| Q&A SSE 이벤트 형식 | 추정 기반 구현 | 백엔드의 `SseEmitter` 이벤트 데이터 형식을 확인 후 `fetchQnaAnswer` 파싱 로직 조정 필요 |
| 토큰 자동 갱신 | 미구현 | `POST /api/auth/refresh` API는 존재하나, 401 발생 시 자동 갱신 후 재시도 로직 미구현 (현재 logout 처리) |
| 정책 상세 추가 필드 | 백엔드 미포함 | 신청 방법, 제출 서류, 문의처 등은 `PolicyDetailResponse`에 미포함. Guide `summaryHtml`에 포함될 수 있음 |
| 정책 신청 URL | 백엔드 미포함 | `PolicyDetailResponse`에 `applyUrl` 필드 없음. 공식 채널 연결 CTA 비활성 상태 |
