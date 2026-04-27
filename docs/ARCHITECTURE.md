# Architecture

> YouthFit 백엔드 아키텍처 문서. 현재 구현된 상태를 기준으로 작성되었다.

---

## 1. 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.5 |
| ORM | Hibernate + Spring Data JPA | - |
| Database | PostgreSQL + pgvector | 17 |
| Cache | Redis | 7 |
| Auth | Kakao OAuth2 + JWT (jjwt) | - |
| AI/LLM | OpenAI API (Embedding, Chat) | - |
| API 문서 | springdoc-openapi (Swagger UI) | 2.8.6 |
| 워크플로우 | n8n | latest |
| 빌드 | Gradle | - |
| 컨테이너 | Docker + Docker Compose | - |
| 테스트 | JUnit 5 + JaCoCo | - |

---

## 2. 아키텍처 원칙

### DDD + Clean Architecture

의존 방향은 항상 바깥에서 안쪽으로 흐른다.

```
Presentation  →  Application  →  Domain
                                    ↑
                              Infrastructure
                          (포트 구현, 의존 역전)
```

- **Domain**: 엔티티, 값 객체, 도메인 서비스, 리포지토리 인터페이스. 프레임워크 의존 없음(JPA 어노테이션 제외).
- **Application**: 유스케이스 오케스트레이션, 트랜잭션 경계, Command/Result DTO.
- **Presentation**: REST Controller, Request/Response DTO. Swagger 어노테이션은 `*Api` 인터페이스에만 선언.
- **Infrastructure**: JPA 리포지토리 구현체, 외부 API 클라이언트, 설정 클래스.

### 핵심 규칙

- 트랜잭션 경계는 Application Service에만 둔다.
- Controller 응답에 Entity를 직접 노출하지 않는다.
- Presentation DTO를 Application/Domain에서 import하지 않는다.
- 비즈니스 규칙은 가능하면 도메인 모델 안에 둔다.
- 외부 의존은 Port(인터페이스) + Adapter(구현체) 패턴으로 격리한다.

---

## 3. 모듈 구조

```
com.youthfit/
├── auth/           # 소셜 로그인, JWT 발급/갱신/검증
├── user/           # 프로필, 북마크, 알림 설정/발송
├── policy/         # 정책 도메인, 정규화, 검색
├── eligibility/    # 규칙 기반 적합도 판정
├── guide/          # AI 가이드 콘텐츠 생성
├── rag/            # 임베딩, 청크 분할, 벡터 조회
├── qna/            # 정책 Q&A, SSE 스트리밍 응답
├── ingestion/      # n8n 외부 수집 파이프라인 수신
└── common/         # 공통 설정, 예외, 유틸
```

각 모듈의 내부 레이어 구조:

```
{module}/
├── presentation/
│   ├── controller/    # *Api (Swagger 인터페이스), *Controller
│   └── dto/
│       ├── request/   # 요청 DTO
│       └── response/  # 응답 DTO
├── application/
│   ├── service/       # 유스케이스 서비스
│   ├── dto/
│   │   ├── command/   # 입력 커맨드
│   │   └── result/    # 출력 결과
│   └── port/          # 외부 의존 포트 인터페이스
├── domain/
│   ├── model/         # 엔티티, 값 객체, Enum
│   ├── repository/    # 리포지토리 인터페이스
│   └── service/       # 도메인 서비스
└── infrastructure/
    ├── persistence/   # JPA 구현체, Specification
    ├── external/      # 외부 API 클라이언트
    ├── config/        # 모듈별 설정
    └── scheduler/     # 스케줄러 (해당 모듈만)
```

---

## 4. 모듈별 상세

### 4.1 auth

카카오 OAuth2 로그인과 JWT 기반 인증을 담당한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `AuthController` | 로그인, 토큰 갱신, 로그아웃 엔드포인트 |
| Application | `AuthService` | OAuth 코드 교환 → 사용자 생성/조회 → 토큰 발급 |
| Infrastructure | `KakaoOAuthClient` | 카카오 토큰 교환 및 사용자 정보 조회 |
| Infrastructure | `JwtProvider` | JWT 생성, 검증, 클레임 추출 (HMAC-SHA) |
| Infrastructure | `JwtAuthenticationFilter` | 요청마다 JWT 검증하는 Security Filter |

**토큰 설정**:
- Access Token: 30분
- Refresh Token: 14일

### 4.2 user

사용자 프로필, 북마크, 알림 설정(마감일 알림 + 적합도 기반 맞춤 정책 추천 알림) 및 이메일 발송을 담당한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `UserProfileController`, `BookmarkController`, `NotificationController` | 프로필/북마크/알림 CRUD |
| Application | `UserProfileService` | 프로필 조회/수정 |
| Application | `BookmarkService` | 북마크 생성/삭제/목록(정책 정보 포함) |
| Application | `NotificationSettingService` | 알림 설정 조회/수정 |
| Application | `NotificationScheduleService` | 마감 임박 정책 알림 대상 조회 및 발송 |
| Application | `RecommendationNotificationService` | 적합도 기반 맞춤 정책 추천 알림 대상 조회 및 발송 |
| Domain | `User` | 프로필 수정, 적합도 프로필 업데이트, 토큰 관리 |
| Domain | `Bookmark` | userId + policyId 유니크 제약 |
| Domain | `NotificationSetting` | 마감 알림 활성화 여부, 마감 전 알림 일수, 맞춤 추천 알림 활성화 여부 |
| Domain | `NotificationHistory` | 발송 이력 (중복 방지) |
| Domain Service | `NotificationTargetResolver` | 알림 대상 사용자 결정 |
| Infrastructure | `LoggingEmailSender` | EmailSender 포트 구현체 (현재 로그 출력) |
| Infrastructure | `NotificationScheduler` | 매일 09:00 실행 (`0 0 9 * * *`) — 마감 알림 |
| Infrastructure | `RecommendationScheduler` | 주 1회 월요일 09:00 실행 (`0 0 9 * * MON`) — 맞춤 추천 알림 |

**도메인 Enum**: `AuthProvider(KAKAO)`, `Role(USER, ADMIN)`, `EmploymentStatus`, `EducationLevel`

### 4.3 policy

정책 데이터의 등록, 조회, 검색을 담당한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `PolicyController` | 정책 목록(페이징/필터), 상세 조회 |
| Application | `PolicyQueryService` | 검색 조건 기반 정책 조회 |
| Application | `PolicyIngestionService` | 외부 소스에서 정책 등록/업데이트 |
| Domain | `Policy` | 상태 전이(`open()`, `close()`), 만료 판단, 상세 레벨 업그레이드 |
| Domain | `PolicySource` | 외부 출처 추적, 소스 해시 기반 변경 감지 |
| Infrastructure | `PolicySpecification` | JPA Criteria API 기반 동적 필터링 |

**도메인 Enum**: `Category(JOBS, HOUSING, EDUCATION, WELFARE, FINANCE, CULTURE, PARTICIPATION)`, `PolicyStatus(UPCOMING, OPEN, CLOSED)`, `DetailLevel(LITE, FULL)`, `SourceType(BOKJIRO_CENTRAL, YOUTH_CENTER, YOUTH_SEOUL_CRAWL)`

**상태 전이**:
```
UPCOMING  →  OPEN  →  CLOSED
```

### 4.4 eligibility

사용자 프로필과 정책 자격 규칙을 비교하여 적합도를 판정한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `EligibilityController` | 적합도 판정 요청 |
| Application | `EligibilityService` | 정책 규칙 조회 → 사용자 프로필 대조 → 결과 반환 |
| Domain | `EligibilityRule` | 정책별 자격 조건 (field, operator, value) |
| Domain | `EligibilityResult` | 판정 결과 Enum |
| Domain Service | `EligibilityEvaluator` | 규칙 평가 엔진 |
| Domain Service | `CriterionEvaluation` | 개별 조건 평가 결과 |

**판정 결과**: `LIKELY_ELIGIBLE`, `UNCERTAIN`, `LIKELY_INELIGIBLE`

**지원 연산자** (`RuleOperator`): `EQ`, `NOT_EQ`, `GTE`, `LTE`, `IN`, `BETWEEN`

### 4.5 guide

정책 원문을 바탕으로 AI가 쉽게 풀어쓴 가이드 콘텐츠를 생성한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `GuideController` | 가이드 조회, 생성 요청 |
| Application | `GuideGenerationService` | Policy 구조화 필드(필수) + PolicyDocument 청크(옵션) 결합 → LLM 호출 → 가이드 저장 |
| Application Port | `GuideLlmProvider` | LLM 호출 추상화 (구조화 JSON 출력) |
| Domain | `Guide` | 정책과 1:1, 소스 해시 기반 변경 감지 및 재생성 |
| Infrastructure | `OpenAiChatClient` | OpenAI Chat API 호출 (gpt-4o-mini) |

**입력 모델 — 하이브리드**
- **필수**: `Policy` 구조화 필드 (`title`, `summary`, `body`, `supportTarget`, `selectionCriteria`, `supportContent` 등). 청크 인덱싱이 비어있어도 가이드는 동작한다.
- **옵션 (보강)**: 동일 `policyId`의 `PolicyDocument` 청크가 있으면 입력에 합쳐진다. 첨부파일 텍스트가 RAG 인덱싱을 통해 들어오면 자동으로 가이드 입력이 풍부해진다.
- 첨부파일 추출/임베딩은 별도 트랙(`ingestion` 모듈)에서 수행되며, 가이드 모듈은 이 트랙의 진행과 무관하게 동작한다.

### 4.6 rag

정책 문서를 청크로 분할하고 벡터 임베딩을 생성하여 시맨틱 검색을 지원한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Application | `RagIndexingService` | 문서 청크 분할 → 임베딩 생성 → 저장 |
| Application | `RagSearchService` | 벡터 유사도 검색 (키워드 폴백 포함) |
| Application Port | `EmbeddingProvider` | 임베딩 생성 추상화 |
| Domain | `PolicyDocument` | 청크 인덱스, 콘텐츠, 소스 해시, 임베딩 벡터 |
| Domain Service | `DocumentChunker` | 문단/문장 단위 분할 (최대 500자), SHA-256 해시 |
| Infrastructure | `OpenAiEmbeddingClient` | OpenAI Embedding API (text-embedding-3-small, 1536차원) |

**벡터 저장**: `policy_document.embedding` 컬럼 — pgvector `vector(1536)`, 코사인 유사도 검색

### 4.7 qna

RAG 검색 결과를 컨텍스트로 활용하여 정책 관련 질문에 SSE 스트리밍으로 응답한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `QnaController` | SSE 스트리밍 Q&A 엔드포인트 |
| Application | `QnaService` | RAG 검색 → 컨텍스트 조합 → LLM 스트리밍 호출 |
| Application Port | `QnaLlmProvider` | 스트리밍 LLM 호출 추상화 |
| Domain | `QnaHistory` | Q&A 이력 저장 (질문, 답변, 출처) |
| Infrastructure | `OpenAiQnaClient` | OpenAI Chat API 스트리밍 호출 (gpt-4o-mini) |

### 4.8 ingestion

n8n 워크플로우에서 수집한 정책 데이터를 수신하는 내부 API를 제공한다.

| 레이어 | 주요 클래스 | 역할 |
|--------|------------|------|
| Presentation | `IngestionController` | 정책 수신 엔드포인트 (`/api/internal/ingestion/policies`) |
| Application | `IngestionService` | 카테고리 매핑, 소스 타입 검증, 콘텐츠 해시 계산 → PolicyIngestionService 위임 |
| Infrastructure | `InternalApiKeyFilter` | API 키 기반 내부 인증 |

### 4.9 common

횡단 관심사와 공용 유틸리티를 제공한다.

| 클래스 | 역할 |
|--------|------|
| `SecurityConfig` | Spring Security 필터 체인, CORS, 인가 규칙 |
| `SwaggerConfig` | OpenAPI 문서 설정 |
| `JpaAuditingConfig` | `@CreatedDate`, `@LastModifiedDate` 활성화 |
| `BaseTimeEntity` | `createdAt`, `updatedAt` 공통 감사 필드 |
| `ApiResponse<T>` | 통일된 API 응답 래퍼 |
| `YouthFitException` | 커스텀 예외 |
| `ErrorCode` | 에러 코드 Enum |
| `GlobalExceptionHandler` | 전역 예외 핸들링 |
| `DateTimeUtil` | 날짜/시간 유틸리티 |

---

## 5. 데이터 모델

### 테이블 관계도

```
users ──1:N── bookmark ──N:1── policy
  │                               │
  ├──1:1── notification_setting   ├──1:N── policy_source
  │                               ├──1:N── eligibility_rule
  ├──1:N── notification_history   ├──1:1── guide
  │                               ├──1:N── policy_document (pgvector)
  └──1:N── qna_history ──N:1─────┘
```

### 테이블 목록

| 테이블 | 설명 | 주요 제약 |
|--------|------|----------|
| `users` | 사용자 계정 | email UNIQUE |
| `bookmark` | 사용자 북마크 | (user_id, policy_id) UNIQUE |
| `notification_setting` | 알림 설정 | user_id 별 1건 |
| `notification_history` | 알림 발송 이력 | (user_id, policy_id, notification_type) UNIQUE |
| `policy` | 정책 정보 | - |
| `policy_source` | 외부 출처 추적 | source_hash로 변경 감지 |
| `eligibility_rule` | 자격 조건 규칙 | policy_id FK |
| `guide` | AI 가이드 | policy_id UNIQUE |
| `policy_document` | RAG 청크 + 임베딩 | embedding vector(1536) |
| `qna_history` | Q&A 이력 | user_id, policy_id FK |

---

## 6. 외부 연동

### OpenAI API

| 용도 | 모델 | 사용처 |
|------|------|--------|
| 임베딩 | text-embedding-3-small (1536차원) | `RagIndexingService` |
| 가이드 생성 | gpt-4o-mini (max 2048 tokens) | `GuideGenerationService` |
| Q&A 스트리밍 | gpt-4o-mini (max 1024 tokens) | `QnaService` |

### Kakao OAuth2

- 토큰 교환: `https://kauth.kakao.com/oauth/token`
- 사용자 정보: `https://kapi.kakao.com/v2/user/me`
- Redirect URI: `http://localhost:5173/auth/kakao/callback` (기본값)

### n8n

- 워크플로우 자동화 도구로 정책 수집 파이프라인을 오케스트레이션한다.
- 수집된 데이터는 `/api/internal/ingestion/policies` 엔드포인트로 전달된다.
- 내부 API 키로 인증한다.

---

## 7. API 엔드포인트

### 공개 (비로그인 허용)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/kakao/login` | 카카오 로그인 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/v1/policies` | 정책 목록 (페이징, 필터) |
| GET | `/api/v1/policies/{id}` | 정책 상세 |

### 인증 필요

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/users/me` | 내 프로필 조회 |
| PUT | `/api/v1/users/me` | 프로필 수정 |
| GET | `/api/v1/bookmarks` | 북마크 목록 |
| POST | `/api/v1/bookmarks` | 북마크 추가 |
| DELETE | `/api/v1/bookmarks/{id}` | 북마크 삭제 |
| GET | `/api/v1/notifications/settings` | 알림 설정 조회 |
| PATCH | `/api/v1/notifications/settings` | 알림 설정 수정 |
| POST | `/api/v1/eligibility/judge` | 적합도 판정 |
| GET | `/api/v1/guides/{policyId}` | 가이드 조회 |
| POST | `/api/v1/guides/generate` | 가이드 생성 |
| GET | `/api/v1/qna/ask` | Q&A (SSE 스트리밍) |

### 내부 API (API 키 인증)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/internal/ingestion/policies` | 정책 수집 데이터 수신 |

---

## 8. 보안

### 인증/인가 구조

```
요청 → InternalApiKeyFilter → JwtAuthenticationFilter → Controller
```

- **세션**: Stateless (JWT 기반)
- **CSRF**: 비활성화 (REST API)
- **CORS**: `localhost:5173`, `localhost:3000` 허용

### 인가 규칙

| 대상 | 접근 |
|------|------|
| Swagger UI, API Docs | 전체 허용 |
| `GET /api/v1/policies/**` | 전체 허용 |
| `/api/auth/**` | 전체 허용 |
| `/actuator/health` | 전체 허용 |
| `/api/internal/**` | API 키 필터로 보호 |
| 그 외 | JWT 인증 필요 |

---

## 9. Port & Adapter 패턴

외부 의존을 도메인/애플리케이션 레이어로부터 격리하기 위해 포트 인터페이스를 사용한다.

| Port (인터페이스) | Adapter (구현체) | 위치 |
|-------------------|-----------------|------|
| `EmbeddingProvider` | `OpenAiEmbeddingClient` | rag |
| `GuideLlmProvider` | `OpenAiChatClient` | guide |
| `QnaLlmProvider` | `OpenAiQnaClient` | qna |
| `EmailSender` | `LoggingEmailSender` | user |

---

## 10. 주요 데이터 흐름

### 정책 수집 파이프라인

```
외부 소스 → n8n 워크플로우 → /api/internal/ingestion/policies
  → IngestionService → PolicyIngestionService
    → Policy 생성/업데이트 + PolicySource 저장
    → RagIndexingService (청크 분할 + 임베딩)
```

### 적합도 판정

```
사용자 요청 → EligibilityService
  → EligibilityRule 조회 (정책별)
  → EligibilityEvaluator (사용자 프로필 vs 규칙)
  → CriterionEvaluation 목록
  → LIKELY_ELIGIBLE / UNCERTAIN / LIKELY_INELIGIBLE
```

### AI 가이드 생성

```
가이드 생성 요청 → GuideGenerationService
  → 입력 결합:
      [필수] Policy 구조화 필드 (supportTarget, selectionCriteria, supportContent 등)
      [옵션] 동일 policyId의 PolicyDocument 청크 (있으면 보강)
  → GuideLlmProvider (LLM 호출, 구조화 JSON 출력)
  → Guide 엔티티 저장 (구조화 섹션 + sourceHash)
```

가이드의 페어드 레이아웃(원문 ↔ 쉬운 해석)을 위해 출력은 원문 구조화 섹션과 1:1 매핑되는 형태로 생성된다. 정확한 스키마는 별도 spec에서 정의한다.

### Q&A 스트리밍

```
사용자 질문 → QnaService
  → RagSearchService (벡터 유사도 검색)
  → 컨텍스트 조합
  → QnaLlmProvider (SSE 스트리밍)
  → QnaHistory 저장
```

### 마감일 알림

```
NotificationScheduler (매일 09:00)
  → NotificationScheduleService
    → 마감 임박 정책 조회 (북마크 기준)
    → NotificationTargetResolver (emailEnabled=true 사용자 필터)
    → EmailSender 발송
    → NotificationHistory 기록 (type=DEADLINE, 중복 방지)
```

### 맞춤 정책 추천 알림

```
RecommendationScheduler (주 1회 월요일 09:00)
  → RecommendationNotificationService
    → 신규/업데이트된 OPEN 정책 조회
    → 대상 사용자 필터
       (eligibilityRecommendationEnabled=true
        ∧ email 등록됨
        ∧ 프로필 적합도 정보 보유)
    → eligibility.domain 으로 LIKELY_ELIGIBLE 정책 선별
    → 북마크·발송 이력 기반 중복 제거 (type=RECOMMENDATION)
    → 사용자별 최대 5건 선정 → EmailSender 발송
    → NotificationHistory 기록
```

---

## 11. 인프라 구성

### Docker Compose 서비스

| 서비스 | 이미지 | 포트 | 용도 |
|--------|--------|------|------|
| postgres | postgres:17-alpine | 5432 | 주 데이터베이스 (pgvector) |
| redis | redis:7-alpine | 6379 | 캐시/세션 |
| n8n | n8nio/n8n:latest | 5678 | 워크플로우 자동화 |
| backend | Dockerfile (JDK 21) | 8080 | Spring Boot 애플리케이션 |

### Dockerfile

- **빌드 스테이지**: `eclipse-temurin:21-jdk` — Gradle bootJar
- **런타임 스테이지**: `eclipse-temurin:21-jre` — 최소 이미지 크기

### 프로파일

| 프로파일 | DDL 전략 | SQL 로깅 | 로그 레벨 |
|----------|---------|----------|----------|
| local | update | true | DEBUG |
| prod | validate | false | INFO |

---

## 12. 테스트 전략

- **단위 테스트**: 도메인 모델, 도메인 서비스, Application Service (Mock 기반)
- **인프라 테스트**: JPA Specification, JWT Provider, API Key Filter
- **슬라이스 테스트**: `@WebMvcTest` 기반 Controller 테스트
- **커버리지**: JaCoCo — config, response, exception, Application 클래스 제외
- **테스트 파일**: 총 40개 이상

---

## 13. 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 접속 | localhost, 5432, youthfit |
| `DB_USER`, `DB_PASSWORD` | DB 인증 | youthfit, (없음) |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 | localhost, 6379 |
| `JWT_SECRET` | JWT 서명 키 | 개발용 기본값 |
| `JWT_ACCESS_EXPIRATION` | Access Token 만료 (ms) | 1800000 (30분) |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 만료 (ms) | 1209600000 (14일) |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 카카오 OAuth | (없음) |
| `KAKAO_REDIRECT_URI` | 카카오 콜백 URI | http://localhost:5173/auth/kakao/callback |
| `OPENAI_API_KEY` | OpenAI API 키 | (없음) |
| `OPENAI_EMBEDDING_MODEL` | 임베딩 모델 | text-embedding-3-small |
| `OPENAI_CHAT_MODEL` | 가이드 생성 모델 | gpt-4o-mini |
| `OPENAI_QNA_MODEL` | Q&A 모델 | gpt-4o-mini |
| `INTERNAL_API_KEY` | 내부 API 인증 키 | (없음) |
| `SERVER_PORT` | 서버 포트 | 8080 |
