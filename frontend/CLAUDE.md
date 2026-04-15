# Frontend CLAUDE.md

> React 프론트엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`를 참조한다.

## 기술 스택
| 영역 | 기술 | 버전 |
|------|------|------|
| 프레임워크 | React + TypeScript | 19 / 5 |
| 빌드 | Vite | 6 |
| 라우팅 | React Router | v7 |
| 서버 상태 | TanStack Query | v5 |
| 클라이언트 상태 | Zustand | v5 |
| 스타일링 | Tailwind CSS + shadcn/ui | v4 |
| HTTP | ky | - |
| 폼 | React Hook Form + Zod | - |
| 폰트 | Pretendard Variable | - |
| 테스트 | Vitest + Testing Library | - |

## 빌드 및 실행
```bash
cd frontend
npm install            # 의존성 설치
npm run dev            # 개발 서버 (포트 5173)
npm run build          # 프로덕션 빌드
npm run preview        # 빌드 미리보기
npm run test           # 테스트
```

## API 연동
- Vite proxy로 `/api` → `localhost:8080`으로 전달
- `apis/client.ts`의 ky 인스턴스를 통해 모든 API 호출
- 인증 토큰은 `beforeRequest` 훅에서 자동 첨부
- 401 응답 시 자동 토큰 갱신 후 재시도

## 디렉토리 구조 규칙
```
src/
├── apis/           # API 함수 (도메인별 파일)
├── hooks/
│   ├── queries/    # useQuery 래퍼
│   └── mutations/  # useMutation 래퍼
├── stores/         # Zustand 스토어
├── pages/          # 라우트 1:1 매핑 페이지
├── components/
│   ├── layout/     # AppLayout, Header, BottomNav
│   ├── ui/         # shadcn/ui 원자 컴포넌트
│   └── {domain}/   # 도메인별 컴포넌트 그룹
├── types/          # TypeScript 타입 (도메인별 파일)
└── lib/            # 유틸리티 (cn, constants, format, token)
```

## 상태 관리 원칙
| 상태 유형 | 도구 | 예시 |
|-----------|------|------|
| 서버 데이터 | TanStack Query | 정책, 프로필, 북마크 |
| 인증 토큰 | Zustand + localStorage | accessToken, isAuthenticated |
| 글로벌 UI | Zustand | 모바일 메뉴, 필터 시트 |
| 로컬 UI | React useState | 입력값, 토글 |
| URL 상태 | React Router searchParams | 필터, 검색어, 페이지 |

## 컴포넌트 규칙
- 파일명은 PascalCase (`PolicyCard.tsx`)
- 도메인별로 `components/{domain}/` 아래에 그룹핑
- 페이지 컴포넌트는 `pages/` 아래에 `{Name}Page.tsx` 형식
- shadcn/ui 컴포넌트는 `components/ui/`에 생성 (CLI로 자동 생성)

## API 연동 패턴
1. `apis/{domain}.api.ts`에 API 함수 정의
2. 조회는 `hooks/queries/use{Name}.ts`에 useQuery 래퍼
3. 변경은 `hooks/mutations/use{Name}.ts`에 useMutation 래퍼
4. 컴포넌트에서 훅을 직접 사용

## 스타일 규칙
- Tailwind CSS 유틸리티 클래스 우선 사용
- `cn()` 유틸 (clsx + tailwind-merge)로 조건부 클래스 조합
- 색상: 브랜드 Blue-500(#3B82F6), 적합도 Green/Amber/Red
- 모바일 우선 반응형 (`md:` 브레이크포인트 기준)
- 터치 타겟 최소 44x44px

## 수정 전에 읽기
- 새 페이지나 컴포넌트 추가 전에 이 파일의 디렉토리 규칙을 확인한다.
- API 연동 변경 시 `docs/PRD.md`의 API 스펙을 확인한다.
- 적합도/Q&A UI 변경 시 `docs/PRODUCT.md`의 해석 원칙을 확인한다.
