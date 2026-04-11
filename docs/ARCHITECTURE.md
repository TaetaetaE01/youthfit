# ARCHITECTURE.md

**프로젝트명**: YouthFit
**버전**: v0 (MVP)
**최종 업데이트**: 2026-04-11

---

## 1. 기술 스펙

### 언어 + 프레임워크

| 레이어 | 스택 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 3.x, Gradle |
| 프론트엔드 | React 18 + Vite (SPA). 추후 Next.js 전환 가능 |
| 배치·스케줄 | n8n (self-host on EC2) + Spring 경량 스케줄러 |
| DB | PostgreSQL 16 + pgvector 확장 |
| 캐시 | Redis (EC2 Docker Compose 내부) |
| 인증 | Spring Security + JWT + Kakao OAuth2 |
| 파일 스토리지 | AWS S3 (PDF 원본) |

### 외부 의존성

| 용도 | 선택 |
| --- | --- |
| 정책 소스 (v0 마스터) | 청년몽땅정보통 크롤링 |
| 정책 소스 (v1 확장) | 온통청년 Open API |
| 임베딩 모델 | OpenAI `text-embedding-3-small` (1536차원, $0.02/1M tokens) |
| 답변 생성 LLM | OpenAI GPT (gpt-4o-mini 또는 gpt-4o) |
| PDF 텍스트 추출 | Apache PDFBox |
| HTML 파싱 | Jsoup |
| 벡터 DB | PostgreSQL pgvector (별도 벡터 DB 없음) |
| 워크플로우 | n8n self-host |
| 알림 | Slack Incoming Webhook |

### 실행 환경

- AWS EC2 단일 인스턴스 (t3.small 또는 medium)
- Docker Compose (Spring Boot + Redis + n8n + nginx)
- AWS RDS PostgreSQL (pgvector extension)
- AWS S3 (PDF 원본)
- 시크릿: 초기 `.env` + `.gitignore`, 추후 AWS Secrets Manager
- 로컬 개발: 동일 docker-compose로 재현

---

## 2. 레이어 구조 + 의존성 방향

모놀리식 단일 배포, 패키지 경계로 레이어 분리.

```
Presentation (REST Controller, SSE, Swagger)
        ↓
Application (UseCase/Service, 트랜잭션 경계)
        ↓
Domain (Entity, 룰 엔진, 값 객체)
        ↓
Infrastructure (JPA, S3, OpenAI, pgvector, Kakao OAuth)
```

### 의존성 규칙
- 단방향: Presentation → Application → Domain. Infrastructure는 Domain Port 인터페이스를 구현 (Hexagonal 스타일).
- Domain 패키지는 Spring/JPA/OpenAI SDK를 import하지 않는다.
- Infrastructure는 Application·Domain을 참조할 수 있으나, 반대 방향은 Port 인터페이스로만.

---

## 3. 패키지 구조 (com.youthfit)

```
com.youthfit
├── ingestion       n8n raw 데이터 수신 창구
├── policy          정책 도메인, 중복 제거, 엔티티 매칭
├── rag             임베딩, chunking, 벡터 검색
├── guide           LLM 기반 구조화 가이드 생성
├── eligibility     룰 엔진 적합도 판정 + LLM 해석
├── qna             RAG 질의, SSE 스트리밍
├── user            카카오 OAuth, JWT, 프로필, 북마크
└── common          공통 예외·유틸·응답 포맷
```

### 패키지별 책임

**ingestion**
- 입력: `POST /internal/ingestion/raw` (n8n에서 호출)
- 출력: `policy_source_raw` 저장 후 즉시 응답, 후처리는 비동기
- 구성: `IngestionController`, `RawPayloadService`

**policy**
- raw → `policy` + `policy_source` 정규화, 중복 제거
- 매칭 전략: ①제목 정규화+기관 비교 → ②임베딩 유사도 → ③실패 시 `manual_review_queue`
- 공개 API: 목록, 상세, 검색, 필터링

**rag**
- 원문 chunking(섹션 번호 `^\d+\.\s` 기준), 임베딩, 벡터 검색
- chunk 본문에 정책명·섹션명 접두사 포함
- 검색 시 `WHERE policy_id = ?` 필터 필수

**guide**
- 정책 수집·변경 시점에 LLM으로 `policy_guide.structured_json` 생성
- 실행 조건: `source_hash` 변경 시에만
- 유저 요청 시점에는 DB SELECT만

**eligibility**
- 하이브리드: 룰 엔진 1차 → 애매한 경우만 LLM 2차
- 결과를 `eligibility_check` 에 `profile_hash` 키로 캐시

**qna**
- 흐름: 인증 → 캐시 → 질문 임베딩 → 벡터 검색 → 프로필 조회 → 프롬프트 조립 → LLM 스트리밍 → 로그
- 환각 방지: 프롬프트 강제 + 근거 chunk 필수 인용

**user**
- Kakao OAuth → JWT access(30분) + refresh(14일)
- 프로필: 생년·지역·소득구간·고용상태·가구원수·주택소유 (PII 최소화, 구간·불리언만)

**common**
- `YouthFitException`, 에러 응답 포맷, 시간 유틸, 로깅 MDC

---

## 4. 데이터 모델 개요

```
policy (논리적 정책)
  ├─ id, title, summary, category, region_code,
  │  apply_start, apply_end, status, detail_level
  │
  └─ policy_source (소스별 raw, N:1)
       ├─ source_type, external_id, source_url, raw_json, source_hash
       │
       └─ policy_document
            ├─ doc_type(html|pdf), s3_key, extracted_text, source_hash
            │
            └─ policy_chunk
                 ├─ section_label, content,
                 │  embedding vector(1536), source_hash

policy_guide (policy 1:1)
  └─ structured_json, summary_markdown, llm_model, token_cost, source_hash

user_account ─ user_profile (1:1)
bookmark (user_id, policy_id)
eligibility_check (user_id, policy_id, status, reasoning, profile_hash)
interpretation_log (question, answer, model, token_in/out, cost, cited_chunk_ids)
ingestion_job (source_type, status, fetched_count, error_message)
manual_review_queue (type, raw_refs, reason)
```

### 핵심 설계
- `policy` vs `policy_source` 2층: 여러 소스 중복 제거를 안전하게
- `source_hash`: 변경 감지와 재처리 판단 기준 (모든 관련 테이블 보유)
- `detail_level`: LITE(메타만) / MEDIUM(본문) / FULL(PDF+구조화 가이드). 화면 배지 표시.

---

## 5. 데이터 파이프라인 (n8n ↔ Spring Boot)

### 역할 분리
- **n8n**: 크롤링, HTTP 요청, 파일 다운로드, 스케줄, Slack 알림
- **Spring Boot**: 정규화, 중복 제거, 임베딩, 가이드 생성, 룰 엔진

### 경계면 API

```
POST /internal/ingestion/raw
Headers: X-Internal-Api-Key: <shared secret>
Body: {
  "sourceType": "YOUTH_SEOUL_CRAWL",
  "externalId": "2026-1109",
  "sourceUrl": "https://...",
  "title": "...",
  "rawJson": {...},
  "documents": [{"docType":"pdf","s3Key":"..."}]
}
```

### n8n 워크플로우 A — 청년몽땅정보통 크롤링 (매일 03:00)

```
Cron 03:00
 → HTTP Request: 정책 목록 (페이지네이션 루프)
 → HTML Extract: id·제목·상세 URL
 → Loop: 상세 페이지 순회
 → HTTP Request: 상세 HTML
 → HTML Extract: 본문·PDF 링크·메타
 → HTTP Request: PDF 다운로드
 → S3 Upload
 → HTTP Request: POST /internal/ingestion/raw
 → IF Error → Slack
```

### n8n 워크플로우 B — 모니터링 (매시)
- `/actuator/health` 호출, 최근 24시간 수집 수 조회
- 0건이면 Slack 경고 (조용한 실패 방지)

### Spring Boot 내부 처리 (raw 수신 후 비동기)

```
Job 1: 정규화·중복 제거
  raw → policy_source → policy 매칭, 신규/수정/삭제 분류
  애매한 건 manual_review_queue

Job 2: 원문 처리 (변경분만)
  PDFBox 텍스트 추출 → 섹션 파싱 → policy_chunk 생성

Job 3: 임베딩 + 가이드 생성 (변경분만)
  chunk 임베딩 (text-embedding-3-small)
  LLM 구조화 가이드 → policy_guide
  detail_level 승격 (LITE → FULL)
```

**중요**: Job 2·3은 반드시 `source_hash` 비교로 **변경분만** 처리. 비용 방어의 핵심.

---

## 6. RAG 파이프라인 (유저 질문 시점)

```
유저 질문
 → 인증·rate limit 체크
 → 캐시 조회 (user_id, policy_id, question_hash, profile_hash)
 → 질문 임베딩 (OpenAI)
 → pgvector 검색 (WHERE policy_id = ? ORDER BY embedding <=> ? LIMIT 5)
 → 유저 프로필 조회
 → 프롬프트 조립 (system + chunks + profile + question)
 → OpenAI GPT 스트리밍
 → SSE 토큰 단위 전송
 → interpretation_log 기록 (token, cost, cited_chunk_ids)
 → 캐시 저장 (TTL 24h)
```

### 환각 방지
- 시스템 프롬프트: "정책 원문에 없는 내용은 '확실하지 않음, 공식 기관 문의 필요'로 답하라"
- 답변에 근거 chunk 필수 인용
- 인용 없으면 답변 자체를 경고 문구로 대체
- `cited_chunk_ids` 전량 로깅 → 품질 감사

---

## 7. 적합도 판정 상세

### 1차: 룰 엔진 (대부분)
`policy_guide.structured_json` 의 구조화 조건과 `user_profile` 단순 비교.

```
age_range      → user.age
region         → user.region_code
income_thresh  → user.income_bracket
homeowner      → user.is_homeowner
```

- 전체 PASS → `likely`
- 일부 FAIL → `unlikely` + 실패 이유
- 해석 불가 → `ambiguous` → 2차로

### 2차: LLM (선택적)
`ambiguous` 거나 유저가 "자세히 해석" 요청 시만. 비용 방어.

결과는 `eligibility_check` 에 `profile_hash` 키로 캐시, 프로필 변경 시 재계산.

---

## 8. 인증·인가

- Kakao OAuth2 단일
- JWT access(30분) + refresh(14일)
- 비로그인 허용: 목록·검색·상세·가이드 조회
- 로그인 필수: Q&A, 프로필, 북마크, 적합도, 알림

---

## 9. 배포 아키텍처

```
AWS EC2 (Docker Compose)
 ├─ nginx (reverse proxy, SSL, static)
 ├─ Spring Boot :8080
 ├─ Redis :6379
 └─ n8n :5678

AWS RDS PostgreSQL (pgvector extension)
AWS S3 (PDF 원본 보관)
External: OpenAI, Kakao OAuth, 청년몽땅정보통, Slack Webhook
```

- 로컬: 동일 docker-compose.yml + 로컬 PostgreSQL 컨테이너
- 운영: docker-compose.prod.yml, RDS 연결
- 프론트 배포: nginx `/var/www/` 정적 배포 (추후 CloudFront+S3 분리 가능)

---

## 10. v0 → v1 확장 계획

| 확장 항목 | 내용 | 학습 포인트 |
| --- | --- | --- |
| **v1-1. 온통청년 API 추가** | n8n 워크플로우 B 추가, `SourceType.YOUTHCENTER_API`, PolicyMatcher 강화, manual_review 운영 프로세스 | 엔티티 매칭·중복 제거 (블로그 글감) |
| **v1-2. Hybrid Search** | PostgreSQL tsvector(BM25) + pgvector 가중 결합 (0.6·vector + 0.4·bm25) | precision@5 before/after 측정 |
| **v1-3. 이벤트 드리븐** | SQS 또는 Spring Events. 각 Job 독립 재시도·DLQ | 분산 시스템 관측성 |
| **v1-4. 지역 확장** | 경기데이터드림, 인천 열린데이터광장 소스 추가 | 커버리지 확장 + 지역별 중복 처리 |
| **v1-5. 알림 고도화** | SQS+Lambda 발송 워커, 개인화 추천 알림 | 비동기 워커 패턴 |
| **v1-6. 관측성** | CloudWatch+Grafana, 구조화 로그, OpenTelemetry, k6 부하 테스트 | 운영 스토리 |

v1-1 선행 조건: 온통청년 API 키 발급 (1주 전 신청 필요)

---

## 11. 설계 결정 기록

| 결정 | 대안 | 이유 |
| --- | --- | --- |
| 모놀리식 + 패키지 경계 | MSA | 사이드 프로젝트 규모에 MSA 과함. 경계만 명확히 |
| pgvector | Pinecone, OpenSearch | 운영 복잡도 최소. PostgreSQL 하나로 통합 |
| text-embedding-3-small | -large, 한국어 특화 | 비용 6배. MVP엔 small 충분, 업그레이드는 재임베딩만 |
| n8n self-host on EC2 | n8n Cloud | 비용 0, 같은 인스턴스 로컬 통신, 학습 가치 |
| EC2 + Docker Compose | ECS Fargate, EKS | 운영 단순. 면접에서 "왜 안 썼는지" 설명 가능이 더 강함 |
| 청년몽땅 마스터 / 온통청년 보조 | 온통청년 마스터 | 청년몽땅이 PDF 원문·신청 목적지 보유 |
| 룰+LLM 하이브리드 판정 | LLM만 | 룰로 90% 커버, 비용·설명가능성 우위 |
| Kakao OAuth 단일 | 다중 공급자 | 타겟 사용자 점유율 압도적, 진입 마찰 최소 |

---

## 12. 금지·지양 사항

- Domain 패키지에 Spring/JPA/OpenAI SDK import 금지
- Controller에서 DB 직접 접근 금지
- 유저 요청 핫패스에서 LLM 직접 호출 금지 (미리 생성 또는 캐시 경유)
- 동일 정책 반복 임베딩 금지 (`source_hash` 확인 후 변경분만)
- 비로그인 유저에게 LLM 호출 노출 금지
- 정책 원문 전문 복사 금지 (요약·인용 + 출처 링크)
- 크롤링: robots.txt 준수, 1초당 1건 이하, 심야 시간대
- `.env`, API 키, DB 비밀번호 Git 커밋 금지
- n8n 워크플로우 JSON은 `ops/n8n/` 에 export해 Git 관리
