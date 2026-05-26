# 프론트엔드 디렉토리·컴포넌트 규칙

## `src/` 디렉토리 구조
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
├── lib/            # 유틸리티 (cn, constants, format, token)
├── assets/         # 이미지·폰트 등 정적 자원
└── test/           # 테스트 셋업·헬퍼
```

## 컴포넌트 규칙
- 파일명은 PascalCase (`PolicyCard.tsx`)
- 도메인별로 `components/{domain}/` 아래에 그룹핑
- 페이지 컴포넌트는 `pages/` 아래에 `{Name}Page.tsx` 형식
- shadcn/ui 컴포넌트는 `components/ui/` 에 생성 (CLI 로 자동 생성)

## API 연동 패턴
1. `apis/{domain}.api.ts` 에 API 함수 정의
2. 조회는 `hooks/queries/use{Name}.ts` 에 useQuery 래퍼
3. 변경은 `hooks/mutations/use{Name}.ts` 에 useMutation 래퍼
4. 컴포넌트에서 훅을 직접 사용
