# Frontend CLAUDE.md

> React 프론트엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`, 코드 컨벤션은 `.claude/rules/frontend/` 참조.

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
npm install
npm run dev           # 포트 5173 — /api → localhost:8080 프록시
npm run build && npm run preview
npm run test
```

## API 연동 핵심
- Vite proxy 로 `/api` → `localhost:8080`
- 모든 호출은 `apis/client.ts` 의 ky 인스턴스 경유
- 인증 토큰은 `beforeRequest` 훅에서 자동 첨부, 401 시 자동 갱신 재시도

## 코드 규칙 (반드시 따른다)
- @.claude/rules/frontend/directory.md         # apis/hooks/stores/pages 구조, 컴포넌트 규칙
- @.claude/rules/frontend/state-management.md  # TanStack vs Zustand vs URL
- @.claude/rules/frontend/styling.md           # Tailwind, cn(), 반응형, 터치 타겟

## 디자인 레퍼런스
- @frontend/docs/DESIGN.md   # 컬러·타이포·간격 토큰 (UI 작업 시 단일 진실 소스)
