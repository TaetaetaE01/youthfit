<div align="center">
  <h1>
    🧭 YouthFit — 청년 정책 큐레이션 서비스 (Backend) 🧭
  </h1>
  <strong>흩어진 청년 정책을 한곳에, 쉽게 이해하도록</strong>
  <p>YOUTHFIT_BACKEND</p>

  <br/>

  <p>
    <strong>"이 정책, 나도 받을 수 있나?" 복잡한 청년 정책, 이제 한눈에!</strong>
  </p>
  <p>
    YouthFit 은 흩어진 청년 정책 정보를 한곳에 모아 쉬운 설명·가벼운 적합도 판정·<br/>
    출처 기반 Q&A 를 제공하여, 사용자가 자격 요건·준비사항·다음 행동을 이해하도록 돕는 서비스입니다.
  </p>
  <p>
    공식 정책 포털을 대체하지 않고, 정책을 더 쉽게 찾고 이해하도록 도운 뒤<br/>
    최종 신청은 공식 채널로 연결하는 <strong>보완형 서비스</strong>입니다.
  </p>

  <br/>
</div>

<!-- TODO: 서비스 대표 이미지 / 데모 스크린샷 삽입 -->
<!-- ![main](이미지_URL) -->
<!-- ![screen1](이미지_URL) -->
<!-- ![screen2](이미지_URL) -->




## 📌 기본 설명

- **목표**
    - 흩어진 청년 정책을 수집·정규화·중복 제거하여 **목록·상세·검색**으로 제공
    - 규칙 기반 **적합도 판정**으로 "내가 받을 수 있는 정책"을 가볍게 안내
    - 정책 원문·첨부를 임베딩하여 **출처 기반 RAG Q&A** 제공 (스트리밍 응답)
    - 잘못 이해하기 쉬운 자격 요건을 **쉬운 설명(AI 가이드)** 으로 풀어서 제공
    - 관심 정책 **북마크·마감 임박 이메일 알림**으로 신청 타이밍을 놓치지 않도록 지원


---

## ⚙️ Backend 기술 스택

- Gradle project
- Spring Boot 4.0.x
- Java 21
- PostgreSQL + pgvector (`hibernate-vector`)
- Spring Data JPA
- Spring Security + OAuth2 Client (Kakao)
- JWT (`jjwt`)
- Redis (캐시 / 레이트리밋)
- Thymeleaf (이메일 템플릿)
- Spring Boot Actuator
- Apache Tika + hwplib (첨부 문서 텍스트 추출)
- jtokkit (LLM 토큰 카운팅)
- Resilience4j (외부 호출 회복탄력성)
- OpenAI API (임베딩 / 챗 / 적합도 규칙 / Q&A)


---

## ☁️ 인프라 기술 스택

- **AWS EC2** + **Docker**
- **AWS RDS (PostgreSQL)**
- **AWS S3** (정책 첨부 저장)
- **AWS SES** (이메일 알림 발송)
- **Redis**
- **n8n** (외부 정책 수집·전처리 파이프라인)
- **GitHub Actions** (CI/CD)
- **Terraform** (IaC)
---

## 🚀 주요 기능

- **인증/회원** (`auth`)
    - 카카오 OAuth2 로그인
    - JWT 발급/재발급, 로그아웃
- **정책 탐색** (`policy`)
    - 정책 목록·상세·검색 (지역·카테고리 필터)
    - 정책 캘린더(마감일 기준) 조회
    - 정책 첨부 다운로드 / redirect 처리
    - 정규화·중복 제거(타이틀 정규화, 참조 사이트 병합)
- **지역** (`region`)
    - 시/도·시군구·읍면동 지역 코드 매핑 및 조회
- **적합도 판정** (`eligibility`)
    - 사용자 프로필 기반 규칙 평가로 적합 여부 판정
    - LLM 으로 정책별 적합도 규칙 자동 생성
- **AI 가이드** (`guide`)
    - 정책 자격 요건·준비사항을 구조화된 쉬운 설명으로 생성
- **RAG Q&A** (`qna`, `rag`)
    - 정책 원문·첨부 임베딩 → 벡터 조회
    - 출처 기반 질의응답 (스트리밍 응답)
    - 쿼리 리라이팅 + Q&A 캐시로 비용 방어
- **수집 파이프라인** (`ingestion`)
    - n8n 등 외부 수집 결과 수신 (작은 내부 수신 표면)
    - 첨부 텍스트 추출·재색인, 기간 정보 백필
- **사용자** (`user`)
    - 프로필·적합도 프로필 관리
    - 북마크
    - 정책 알림 구독 / 마감 임박 이메일 알림 (SES)
    - schedule 기반 추천·알림 발송
- **비용/메트릭** (`metrics`)
    - LLM API 호출 비용 추적·사용량 집계 (이벤트 드리븐)
- **어드민** (`admin`)
    - 정책 enrichment 리뷰, RAG 미리보기, 이메일 로그
    - Q&A 캐시, LLM 비용, ingestion 헬스, 대시보드
---

## 🧱 패키지 구조도

각 도메인은 **DDD Bounded Context** 단위로 분리되고, 모듈 내부는
`presentation → application → domain → infrastructure` 4계층으로 구성됩니다.

```bash
com.youthfit/
├── 📁 auth/          # 🔐 카카오 OAuth + JWT
├── 📁 policy/        # 📋 정책 도메인·정규화·중복 제거 (대표 모듈, 아래 상세)
├── 📁 region/        # 🗺️ 지역 조건 매핑·지역 정보 조회
├── 📁 eligibility/   # ✅ 규칙 기반 적합도 판정
├── 📁 guide/         # 📖 구조화된 AI 가이드 콘텐츠 생성
├── 📁 qna/           # 💬 정책 Q&A·스트리밍 응답
├── 📁 rag/           # 🧠 임베딩·청크 분할·벡터 조회
├── 📁 ingestion/     # 🔄 n8n·외부 수집 파이프라인 수신
├── 📁 user/          # 👤 프로필·북마크·알림
├── 📁 metrics/       # 📊 LLM API 호출 비용 추적·사용량 집계
├── 📁 admin/         # 🛠️ 어드민 도구 (리뷰·미리보기·대시보드)
└── 📁 common/        # 🧩 공통 유틸·횡단 관심사 (config, response, exception, event, openai)
```

### 대표 모듈 상세 — `policy`

```bash
policy/                                          # 📋 정책 도메인
├── 📁 presentation/                             # Presentation Layer (REST Controllers)
│   ├── 📁 controller/
│   │   ├── 📄 PolicyController.java              # 정책 목록·상세·검색 API
│   │   ├── 📄 PolicyApi.java                     # Swagger 문서 인터페이스
│   │   ├── 📄 PolicyAttachmentController.java    # 정책 첨부 API
│   │   ├── 📄 PolicyAttachmentApi.java           # Swagger 문서
│   │   ├── 📄 RegionController.java              # 정책-지역 조회 API
│   │   └── 📄 RegionApi.java                     # Swagger 문서
│   └── 📁 dto/response/                          # Response DTO (record)
│       ├── 📄 PolicyPageResponse.java
│       ├── 📄 PolicyDetailResponse.java
│       ├── 📄 PolicyCalendarPageResponse.java
│       └── 📄 ...
├── 📁 application/                              # Application Layer (Use Cases & Services)
│   ├── 📁 service/
│   │   ├── 📄 PolicyQueryService.java           # 정책 조회 서비스
│   │   ├── 📄 PolicyIngestionService.java       # 정책 적재 서비스
│   │   ├── 📄 EnrichmentJobService.java         # enrichment 작업 서비스
│   │   ├── 📄 PolicyAttachmentApplicationService.java
│   │   └── 📄 ...
│   ├── 📁 dto/
│   │   ├── 📁 command/                          # 입력 DTO (Command)
│   │   └── 📁 result/                           # 출력 DTO (Result)
│   └── 📁 port/                                 # 아웃바운드 포트 인터페이스
│       ├── 📄 RegionCodeRegistry.java
│       ├── 📄 ForceEnrichTrigger.java
│       └── 📄 ...
├── 📁 domain/                                   # Domain Layer (핵심 비즈니스 로직)
│   ├── 📁 model/                                # Entity·Enum·VO
│   │   ├── 📄 Policy.java                        # 정책 메인 엔티티
│   │   ├── 📄 PolicyAttachment.java              # 정책 첨부
│   │   ├── 📄 PolicyEnrichment.java              # enrichment 결과
│   │   ├── 📄 Category.java / SourceType.java    # 분류 enum
│   │   └── 📄 ...
│   ├── 📁 repository/                           # Repository 인터페이스 (포트)
│   │   ├── 📄 PolicyRepository.java
│   │   └── 📄 ...
│   ├── 📁 service/                              # 도메인 서비스
│   │   ├── 📄 TitleNormalizer.java              # 타이틀 정규화
│   │   ├── 📄 PolicyReferenceSiteMerger.java    # 참조 사이트 병합
│   │   └── 📄 EnrichmentReviewPolicy.java
│   └── 📁 exception/                            # 도메인 전용 예외
├── 📁 infrastructure/                          # Infrastructure Layer (외부 연동)
│   ├── 📁 persistence/                          # JPA 구현체 (Repository Adapter)
│   │   ├── 📄 PolicyJpaRepository.java
│   │   ├── 📄 PolicyRepositoryImpl.java
│   │   ├── 📄 PolicySpecification.java          # 동적 쿼리
│   │   └── 📄 ...
│   ├── 📁 external/                             # 외부 시스템 어댑터
│   │   ├── 📄 N8nForceEnrichClient.java         # n8n 호출
│   │   ├── 📄 JsonRegionCodeRegistry.java
│   │   └── 📄 YamlIncomeBracketReferenceLoader.java
│   └── 📁 scheduler/                            # 스케줄러
│       ├── 📄 EnrichmentJobTimeoutScheduler.java
│       └── 📄 PolicyProcessingStepTimeoutScheduler.java
└── (common 모듈의 config·response·exception·event·openai 를 공유)
```

### 계층의 의존 관계 흐름

> 의존 방향은 항상 **Presentation → Application → Domain** 을 유지하며,
> Infrastructure 는 포트를 구현하되 의존 방향을 역전시키지 않습니다.

- **presentation** : `{도메인}Controller`, `{도메인}Api`, Request/Response DTO
  - HTTP 관심사만 처리 (요청 검증·응답 변환)
  - Request DTO → Command, Result → Response DTO 변환 책임
  - Swagger 어노테이션은 `{도메인}Api` 인터페이스에 분리, Controller 는 `implements`

- **application** : `{도메인}Service`, Command/Result, Port
  - 유스케이스 조립과 **트랜잭션 경계** 담당
  - 도메인 모델·리포지토리를 조합하여 비즈니스 흐름 실행
  - 외부 의존은 아웃바운드 **Port 인터페이스**로 추상화

- **domain** : Entity, Enum/VO, Domain Service, Repository 인터페이스
  - 시스템의 핵심 비즈니스 로직을 담는 모듈
  - Spring·JPA·OpenAI SDK 등 **프레임워크 의존이 침투하지 않는** 순수 영역
  - 상태 변경은 public setter 가 아닌 **의미 있는 도메인 메서드**로 표현

- **infrastructure** : `{도메인}RepositoryImpl`, `{외부호출}Client/Adapter`, Scheduler
  - 외부 시스템과의 연동 담당 (DB, S3, SES, OpenAI, n8n 등)
  - **포트 & 어댑터 패턴**으로 application/domain 의 포트를 구현 → 언제든 교체 가능

- **common** : 공통 설정 및 횡단 관심사
  - 전체 모듈이 공유하는 config, 공통 응답 형식, 전역 예외 처리, 이벤트, OpenAI 클라이언트

## 🗂️ ERD
<!-- TODO: ERD 이미지 삽입 -->
<!-- ![erd](이미지_URL) -->

## 🏗️ 아키텍처
<!-- TODO: 아키텍처 다이어그램 삽입 -->
<!-- ![architecture](이미지_URL) -->
