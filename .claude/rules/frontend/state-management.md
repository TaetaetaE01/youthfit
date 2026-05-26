# 프론트엔드 상태 관리 규칙

상태 유형별 도구를 일관되게 사용한다.

| 상태 유형 | 도구 | 예시 |
|-----------|------|------|
| 서버 데이터 | TanStack Query | 정책, 프로필, 북마크 |
| 인증 토큰 | Zustand + localStorage | accessToken, isAuthenticated |
| 글로벌 UI | Zustand | 모바일 메뉴, 필터 시트 |
| 로컬 UI | React useState | 입력값, 토글 |
| URL 상태 | React Router searchParams | 필터, 검색어, 페이지 |

## 결정 가이드
- 백엔드에서 오는 데이터 → **TanStack Query**. 캐시·자동 재요청·에러 핸들링.
- 토큰처럼 영속화가 필요한 인증 상태 → **Zustand + localStorage middleware**.
- 다른 페이지에서 공유하는 UI 상태 → **Zustand**.
- 한 컴포넌트 안에서만 쓰는 입력값·토글 → **useState**.
- URL 로 공유·복원돼야 하는 상태 (필터, 검색어, 페이지 번호) → **React Router searchParams**.
