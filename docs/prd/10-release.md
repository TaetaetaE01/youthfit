# PRD — 릴리즈 계획 & 위험 요소

---

## 1. 릴리즈 계획

### Phase 1 — 기반 구축 (완료)
- [x] 프로젝트 스켈레톤 및 패키지 구조
- [x] common 패키지 (ApiResponse, ErrorCode, GlobalExceptionHandler)
- [x] auth 모듈 (카카오 로그인, JWT 발급·갱신·로그아웃)
- [x] user 모듈 (프로필 조회·수정)
- [x] policy 모듈 (목록 조회, 상세 조회, 키워드 검색)

### Phase 2 — 핵심 기능
- [ ] user 모듈: 북마크 CRUD → [06-bookmark.md](06-bookmark.md)
- [ ] eligibility 모듈: 규칙 기반 적합도 판정 → [04-eligibility.md](04-eligibility.md)
- [ ] ingestion 모듈: 복지로·온통청년 공공 API 수집 엔드포인트 → [08-ingestion.md](08-ingestion.md)
- [ ] policy 모듈: PolicySource 관리, 중복 제거

### Phase 3 — AI 기능
- [ ] rag 모듈: 문서 청크 분할, 임베딩 생성, 벡터 조회 → [05-qna.md](05-qna.md)
- [ ] qna 모듈: RAG 기반 Q&A, SSE 스트리밍 응답 → [05-qna.md](05-qna.md)
- [ ] guide 모듈: AI 가이드 사전 생성, 캐시 관리 → [05-qna.md](05-qna.md)

### Phase 4 — 알림 및 마무리
- [ ] user 모듈: 마감일 이메일 알림 스케줄링·발송 → [07-notification.md](07-notification.md)
- [ ] user 모듈: 적합도 기반 맞춤 정책 추천 알림 스케줄링·발송 → [07-notification.md](07-notification.md)
- [ ] 비기능 요구사항 점검 (성능, 보안, 모니터링) → [09-common.md](09-common.md)
- [ ] 프론트엔드 통합 및 E2E 테스트
- [ ] MVP 출시

---

## 2. 위험 요소 및 의존성

### 2.1 외부 의존성

| 의존성 | 영향 범위 | 위험 수준 | 대응 방안 |
|--------|----------|----------|----------|
| 카카오 OAuth API | 로그인 전체 | 높음 | 카카오 API 장애 시 로그인 불가 — 에러 안내 페이지 표시 |
| OpenAI API (LLM) | Q&A, 적합도, 가이드 | 높음 | 사전 생성 캐시 활용, 장애 시 정책 탐색은 정상 동작 |
| PostgreSQL | 전체 | 높음 | 일 1회 백업, 장애 시 전체 서비스 중단 |
| Redis | 세션/캐시 | 중간 | Redis 장애 시 DB 직접 조회 fallback |
| n8n (수집) | 정책 데이터 갱신 | 중간 | 수집 실패해도 기존 정책 데이터로 서비스 유지 |

### 2.2 기술 위험

| 위험 | 영향 | 대응 방안 |
|------|------|----------|
| LLM 비용 초과 | 운영비 급증 | 캐시, 변경 감지, 비로그인 차단, 일일 비용 상한 설정 |
| LLM 환각(hallucination) | 사용자 신뢰 하락 | RAG 기반 출처 강제, 불확실성 인정, disclaimer 상시 노출 |
| 정책 원문 변경 | 오래된 정보 노출 | source hash 기반 변경 감지, 주기적 재수집 |
| 개인정보 처리 | 법적 리스크 | 최소 수집 원칙, 카카오 제공 정보만 저장, 개인정보 처리방침 명시 |

### 2.3 비즈니스 위험

| 위험 | 영향 | 대응 방안 |
|------|------|----------|
| 적합도 판정 오해 | 사용자가 법적 판정으로 오인 | "참고용" disclaimer 상시 노출, 공식 채널 안내 병행 |
| 공공 API 장애·정책 변경 | 원천 API 응답 실패 또는 스펙 변경 | 기존 저장분으로 서비스 유지, 응답 스키마 파싱 실패 시 알림, 식별 가능 User-Agent·rate limit 준수 |
| 사용자 유입 부족 | 서비스 지속 불가 | MVP 핵심 가치(탐색·해석)에 집중, 초기 타겟 커뮤니티 공략 |

---

## 3. 성공 지표

### 사용자 활성 지표
- DAU / WAU / MAU
- 신규 가입자 수 (카카오 로그인 전환율)
- 세션 당 정책 조회 수
- 재방문율 (7일, 30일)

### 핵심 기능 사용 지표
- 정책 검색 횟수 및 검색 결과 클릭률 (CTR)
- 적합도 판정 요청 수 및 완료율
- Q&A 질문 수 및 답변 만족도
- 북마크 추가 수 / 사용자 당 평균 북마크 수
- 이메일 알림 발송 수 및 이메일 내 링크 클릭률

### 전환 지표
- 정책 상세 페이지 → 공식 신청 채널 이동률
- 적합도 판정 → 공식 신청 채널 이동률
- 비로그인 → 로그인 전환율

### 품질 지표
- API 응답 시간 (p50, p95, p99)
- API 에러율 (5xx 비율)
- Q&A 답변의 출처 연결률
- 적합도 판정 시 "불명확" 비율

### 비용 지표
- LLM API 호출 수 및 토큰 사용량
- 캐시 히트율 (가이드, 임베딩)
- 사용자 당 LLM 비용

---

## 4. 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Database | PostgreSQL |
| Cache | Redis |
| ORM | Spring Data JPA |
| Auth | Spring Security + OAuth 2.0 + JWT (jjwt) |
| AI/LLM | OpenAI API (예정) |
| 수집 자동화 | n8n |
| Build | Gradle |
