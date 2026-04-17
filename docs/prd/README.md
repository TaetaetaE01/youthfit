# PRD 문서 인덱스

YouthFit PRD를 도메인/기능별로 분리한 문서 목록이다.
구현 계획 수립 시 해당 도메인 문서만 참조하면 된다.

## 공통

| 문서 | 내용 |
|------|------|
| [00-overview.md](00-overview.md) | 제품 개요, 문제 정의, 페르소나, MVP 범위 |
| [09-common.md](09-common.md) | 공통 응답 규격, 에러 코드, 비기능 요구사항, 비용 방어 |
| [10-release.md](10-release.md) | 릴리즈 계획, 위험 요소, 기술 스택 |

## 도메인별

| 문서 | 모듈 | 기능 ID | 내용 |
|------|------|---------|------|
| [01-policy.md](01-policy.md) | policy | F-01, F-02, F-03 | 정책 목록, 상세, 키워드 검색 |
| [02-auth.md](02-auth.md) | auth | F-04 | 카카오 로그인, JWT 발급/갱신/로그아웃 |
| [03-user.md](03-user.md) | user | F-05 | 프로필 조회/수정 |
| [04-eligibility.md](04-eligibility.md) | eligibility | F-06 | 규칙 기반 적합도 판정 |
| [05-qna.md](05-qna.md) | qna | F-07 | RAG 기반 정책 Q&A |
| [06-bookmark.md](06-bookmark.md) | user(bookmark) | F-08 | 북마크 CRUD |
| [07-notification.md](07-notification.md) | user(notification) | F-09 | 마감일 알림 + 적합도 기반 맞춤 정책 추천 알림 |
| [08-ingestion.md](08-ingestion.md) | ingestion | - | n8n 연동 정책 수집 |
