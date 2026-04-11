# CLAUDE.md

**프로젝트명**: YouthFit
**버전**: v0 (MVP)
**최종 업데이트**: 2026-04-11

이 문서는 Claude Code가 YouthFit 프로젝트에서 작업할 때 참조하는 **루트 인덱스**이다. 프로젝트의 목적·핵심 기능·모듈 경계·참조 문서를 한곳에 정리한다.

---

## 1. 프로젝트 설명 + 목적

YouthFit은 **흩어진 청년 정책을 한 곳에 모으고, 공고 원문을 기반으로 AI가 내 상황에 맞게 해석·판정해주는 서비스**다.

### 해결하려는 문제
1. 청년 정책이 여러 플랫폼에 흩어져 있어 전수 조사가 어렵다
2. "중위소득 150% 이하" 같은 행정 용어를 일반 청년이 본인 상황에 대입하기 어렵다
3. SNS로 정책 존재는 알지만 실제 신청 방법을 몰라 포기하는 경우가 많다

### 타겟 사용자
만 24~29세 사회 초년생·이직 준비자. 졸업·첫 취업·자취 시작이 겹쳐 소득·거주·고용 상태가 가장 자주 변하는 층.

### 기존 방법의 한계
- **온통청년**: 공식 자격 검증은 강력하지만 인지도 낮음, 문서 난해, 진입 장벽 높음
- **블로그/유튜브**: 나열형 콘텐츠 위주, 개인 상황에 대한 해석 부재

### YouthFit의 차별화
온통청년이 "공식 자격 검증"에 집중한다면, YouthFit은 **"쉬운 탐색 + 내 상황에 대한 쉬운 해석 + 원문 RAG 기반 Q&A + 가벼운 적합도 판정"**에 집중한다. 최종 신청은 온통청년·청년몽땅정보통으로 이탈시키는 **보완형 포지셔닝**.

---

## 2. 핵심 기능 요약

### MVP (v0)

| # | 기능 | 입력 | 출력 | 로그인 필요 |
| --- | --- | --- | --- | --- |
| F1 | 정책 목록 조회 | 필터(지역/카테고리/모집상태), 정렬 | 정책 카드 리스트 | ❌ |
| F2 | 정책 상세 조회 | 정책 ID | 기본 정보 + 구조화 가이드 | ❌ |
| F3 | 키워드·의미 검색 | 검색어 | 매칭 정책 리스트 | ❌ |
| F4 | 카카오 소셜 로그인 | Kakao OAuth 코드 | JWT | - |
| F5 | 유저 프로필 입력/수정 | 생년/지역/소득구간/고용상태/가구원수/주택소유 | 저장된 프로필 | ✅ |
| F6 | 적합도 판정 | 유저 프로필 + 정책 ID | 해당가능/애매/해당안됨 + 이유 | ✅ |
| F7 | 정책 AI Q&A (RAG) | 정책 ID + 질문 | SSE 스트리밍 답변 + 근거 chunk 인용 | ✅ |
| F8 | 북마크 | 정책 ID | 저장/해제 | ✅ |
| F9 | 마감일 이메일 알림 | 북마크 기반 | D-3 자동 발송 | ✅ |

### v0에서 제외 (v1 이후 확장)

- 온통청년 Open API 소스 연계
- 커뮤니티·댓글·별점
- 모바일 앱, 푸시 알림
- 관리자 대시보드 (DB 직접 조회로 대체)
- Hybrid search (v0은 벡터 검색만)
- 이벤트 드리븐 아키텍처 (v0은 순차 배치)

---

## 3. 모듈 간 데이터 인터페이스

단일 프로세스 내 패키지 간 통신은 **Spring Service 호출**이 기본. 외부 시스템(n8n ↔ Spring Boot)은 **HTTP + 내부 API 키**로 연결.

### n8n → Spring Boot 단일 창구

```
POST /internal/ingestion/raw
Headers:
  X-Internal-Api-Key: <shared secret in .env>
Body:
  {
    "sourceType": "YOUTH_SEOUL_CRAWL",
    "externalId": "2026-1109",
    "sourceUrl": "https://...",
    "title": "...",
    "rawJson": { ... },
    "documents": [
      { "docType": "pdf", "s3Key": "policies/2026-1109/공고문.pdf" }
    ]
  }
```

n8n은 이 엔드포인트 하나만 알면 된다. 도메인 로직은 전부 Spring Boot 안.

### 내부 패키지 간 규칙

- **단방향 의존**: Presentation → Application → Domain (Infrastructure는 Domain 인터페이스 구현)
- **트랜잭션 경계는 Application 레이어**에만 위치
- **패키지 간 호출은 public Service 메서드로만**. Repository·Entity 직접 노출 금지
- **이벤트 필요 시 Spring Application Events 우선 검토** (SQS는 v1)

---

## 4. 디렉토리 구조

```
youthfit/
├── backend/
│   └── src/main/java/kr/youthfit/
│       ├── YouthFitApplication.java
│       ├── ingestion/          # n8n raw 수신 창구
│       ├── policy/             # 정책 도메인, 중복 제거
│       ├── rag/                # 임베딩, 벡터 검색
│       ├── guide/              # LLM 가이드 생성
│       ├── eligibility/        # 룰 엔진 적합도 판정
│       ├── qna/                # RAG 질의, SSE
│       ├── user/               # 인증, 프로필, 북마크
│       └── common/             # 공통 유틸
│
├── frontend/                   # React + Vite SPA
│   └── src/
│
├── ops/
│   ├── docker-compose.yml      # local
│   ├── docker-compose.prod.yml
│   ├── nginx/
│   └── n8n/                    # n8n 워크플로우 JSON export (버전 관리)
│
├── docs/
│   ├── ARCHITECTURE.md
│   └── CLAUDE.md               # 이 파일
│
├── .env.example
└── README.md
```

---

## 5. 참조 문서 목록

| 문서 | 역할 |
| --- | --- |
| `docs/ARCHITECTURE.md` | 기술 스펙, 레이어 구조, 패키지별 책임, 데이터 모델, 파이프라인, 배포 아키텍처, v1 확장 계획, 설계 결정 기록 |
| `docs/CLAUDE.md` | (이 파일) 프로젝트 목적·핵심 기능·모듈 인터페이스·디렉토리 구조 인덱스 |

v0에서는 위 2개 문서만 작성한다. `IMPLEMENTATION_STEPS.md`, `TEST_REPORT.md`, `CHANGELOG.md`는 추후 필요 시 추가.

---

## 6. Claude Code 작업 가이드

바이브 코딩으로 진행하되, 구조가 무너지지 않도록 아래 규칙을 세션마다 준수.

### 6-1. 레이어 격리

- Domain 패키지(`com.youthfit.policy`, `eligibility` 등)에 Spring/JPA/OpenAI SDK를 **import하지 않는다**. 외부 의존성은 Infrastructure Port 인터페이스로만 접근.
- Controller에서 DB 직접 접근 금지. 반드시 Application Service 경유.
- 트랜잭션은 Application Service 메서드에만 선언(`@Transactional`).

### 6-2. 세션당 한 레이어만 수정

- 하나의 세션에서는 **하나의 패키지 또는 하나의 기능 슬라이스**만 수정한다
- 세션 시작 시 "이번 작업 범위: `ingestion` 패키지만" 식으로 명시
- 여러 패키지에 걸친 변경이 필요하면 **먼저 ARCHITECTURE.md 업데이트부터**

### 6-3. 비용 방어 필수

- LLM·임베딩 호출은 반드시 `source_hash` 비교로 변경분만 처리
- 유저 요청 핫패스에서 LLM 직접 호출 금지 (미리 생성된 `policy_guide` 사용 또는 캐시 경유)
- 비로그인 유저에게 LLM 호출 노출 금지

### 6-4. 시크릿 관리

- `.env`, API 키, DB 비밀번호는 **절대 Git에 커밋하지 않는다**
- `.gitignore`에 `.env`, `*.pem`, `credentials/` 초반부터 포함
- n8n Credential은 n8n UI에서 설정, export JSON에서 민감값 제거 후 커밋

### 6-5. 크롤링 윤리

- `robots.txt` 준수
- User-Agent에 프로젝트명·연락처 명시
- Rate limit: 1초당 1건 이하, 가급적 심야 시간대
- 수집한 원문 텍스트는 요약·인용 형태로만 노출, 전문 복사 금지

### 6-6. 변경 감지 규칙

다음 테이블은 모두 `source_hash` 컬럼을 가진다. 변경 여부 판단은 오직 해시 비교로만:
- `policy_source`
- `policy_document`
- `policy_chunk`
- `policy_guide`

### 6-7. 의존성 방향 자가 점검

새 클래스·메서드 추가 시 스스로 질문:
1. 이 코드는 어느 패키지에 속하는가? (불명확하면 작성 중단)
2. 이 패키지가 의존해도 되는 방향인가? (Domain이 Infrastructure를 참조하면 중단)
3. 이 의존성은 인터페이스(Port)를 거치는가? 구현체를 직접 참조하는가?

---

## 7. 환경 변수 (예시)

`.env.example` 에 포함될 키 (실제 값은 `.env`에만):

```
# Spring Boot
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080

# PostgreSQL (RDS)
DB_HOST=...
DB_PORT=5432
DB_NAME=youthfit
DB_USER=...
DB_PASSWORD=...

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# OpenAI
OPENAI_API_KEY=...
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
OPENAI_CHAT_MODEL=gpt-4o-mini

# Kakao OAuth
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
KAKAO_REDIRECT_URI=...

# JWT
JWT_SECRET=...
JWT_ACCESS_TTL_SECONDS=1800
JWT_REFRESH_TTL_SECONDS=1209600

# AWS S3
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=youthfit-policies
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...

# Internal API (n8n → Spring Boot)
INTERNAL_API_KEY=...

# Slack (알림)
SLACK_WEBHOOK_URL=...

# n8n (self-host)
N8N_BASIC_AUTH_USER=...
N8N_BASIC_AUTH_PASSWORD=...
```
