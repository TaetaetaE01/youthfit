# Backend CLAUDE.md

> Spring Boot 백엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`, 코드 컨벤션은 `.claude/rules/backend/` 참조.

## 기술 스택
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
| 빌드 | Gradle | - |
| 테스트 | JUnit 5 + JaCoCo | - |

## 빌드 및 실행
```bash
cd backend
./gradlew build && ./gradlew test
./gradlew bootRun     # 포트 8080
```

## 백엔드 모듈 목록
- `admin` 어드민 도구 / `auth` 카카오 로그인+JWT
- `common` 공통 / `eligibility` 적합도 판정
- `eval` RAG retrieval 평가 러너 (dev 전용, eval 프로파일)
- `guide` AI 가이드 / `ingestion` 외부 수집 수신
- `metrics` LLM 비용·사용량 / `policy` 정책 도메인
- `qna` Q&A 스트리밍 / `rag` 임베딩·청크
- `region` 지역 매핑·조회 / `user` 프로필·북마크·알림

## 모듈 내부 레이어 구조
```
{module}/
├── presentation/   (controller, dto/request, dto/response)
├── application/    (service, dto/command, dto/result, port)
├── domain/         (model, repository, service)
└── infrastructure/ (persistence, external, config, scheduler)
```

## 코드 규칙 (반드시 따른다)
- @.claude/rules/backend/architecture.md   # DDD, 의존방향, 트랜잭션
- @.claude/rules/backend/naming.md         # Service 메서드, 패키지
- @.claude/rules/backend/dto.md            # record + Command/Result/Request/Response
- @.claude/rules/backend/swagger.md        # Api 인터페이스, ErrorCode
- @.claude/rules/backend/lombok.md         # Lombok, 예외 처리, Controller

## 도메인 레퍼런스
- @backend/docs/ENTITIES.md                # 모듈별 엔티티·테이블 스키마
- @backend/docs/INGESTION_PIPELINE.md      # n8n → DB 흐름
- @backend/docs/CONTENT_GENERATION_FLOW.md # 가이드·룰·RAG·Q&A fan-out
