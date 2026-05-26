# 프론트엔드 스타일 규칙

## Tailwind 우선
- Tailwind CSS 유틸리티 클래스 우선 사용
- `cn()` 유틸 (clsx + tailwind-merge) 로 조건부 클래스 조합

## 색상 토큰
- 브랜드 Blue-500 (`#3B82F6`)
- 적합도 Green / Amber / Red (`@frontend/docs/DESIGN.md` 참조)

## 반응형
- 모바일 우선 (`md:` 브레이크포인트 기준으로 데스크톱 추가)
- 터치 타겟 최소 44 × 44 px

## 디자인 토큰
컬러·타이포·간격의 단일 진실 소스는 `@frontend/docs/DESIGN.md`. 새 토큰을 추가할 때 그 파일에 먼저 반영한 뒤 컴포넌트에 적용.
