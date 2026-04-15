# Backend CLAUDE.md

> Spring Boot 백엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`를 참조한다.

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

## 빌드 및 테스트
```bash
cd backend
./gradlew build        # 빌드
./gradlew test         # 테스트
./gradlew bootRun      # 로컬 실행 (포트 8080)
```

## 절대 지켜야 할 아키텍처 규칙
- **DDD + Clean Architecture**를 따른다.
- 의존 방향은 반드시 **Presentation -> Application -> Domain** 을 유지한다.
- Infrastructure는 포트를 구현할 수 있지만, 의존 방향을 역전시키면 안 된다.
- 트랜잭션 경계는 오직 **Application Service** 에만 둔다.
- Controller 응답에 Entity를 직접 노출하지 않는다.
- `presentation` DTO를 `application` 또는 `domain` 에서 import하지 않는다.
- `domain` 레이어에 Spring, JPA, OpenAI SDK 등 프레임워크 의존을 넣지 않는다.
- 비즈니스 규칙을 표현하는 동작은 가능하면 도메인 모델 안에 둔다.

## 모듈 내부 레이어 구조
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

## Swagger (OpenAPI) 규칙
- Swagger 어노테이션은 **Controller가 아닌 `{도메인}Api` 인터페이스**에 작성한다.
- 새로운 Controller를 추가할 때 반드시 같은 패키지에 `{도메인}Api` 인터페이스를 먼저 만들고, Controller가 이를 구현(`implements`)한다.
- 인터페이스에 `@Tag`를 클래스 레벨에, `@Operation`을 각 메서드에 붙인다.
- PathVariable, 필수 RequestParam에는 `@Parameter(description = "...")` 를 붙인다.
- 각 메서드에 `@ApiResponses`로 에러 응답을 명세한다.
- Controller에는 Swagger 어노테이션을 두지 않고 Spring MVC 어노테이션만 둔다.

## DTO 규칙
- **DTO(Command, Result, Request, Response)는 반드시 Java `record`로 생성한다.**
- Request DTO → `Request`로 끝남, Response DTO → `Response`로 끝남
- 입력 DTO → `Command`로 끝남, 출력 DTO → `Result`로 끝남
- Request → Command 변환, Result → Response 생성

## Entity 및 도메인 모델 규칙
- 비즈니스 상태 변경을 위한 public setter를 두지 않는다.
- 상태 변경은 의미 있는 도메인 메서드로 표현한다.
- Entity를 단순 데이터 컨테이너로만 사용하지 않는다.

## Lombok 규칙
허용: `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
지양: `@Data`, `@Setter`, Domain Entity에 대한 public all-args constructor

## 예외 처리
- 도메인 전용 커스텀 예외를 사용한다.
- 전역 예외 핸들러에서 일관되게 매핑한다.
- persistence 또는 프레임워크 내부 오류 상세를 클라이언트에 노출하지 않는다.

## Service 메서드 네이밍
- `find...`: 조회, `register.../create...`: 생성, `change.../update...`: 변경
- `cancel.../delete...`: 취소/삭제, `judge.../evaluate...`: 평가
- `generate...`: LLM 콘텐츠 생성, `send...`: 외부 알림 발송
- 모호한 이름(`get`, `save`, `check`, `list`) 피하기
