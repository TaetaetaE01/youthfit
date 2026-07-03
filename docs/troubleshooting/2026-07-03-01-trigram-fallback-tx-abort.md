# hybrid 검색 trigram 폴백이 무효화되던 문제 — PostgreSQL aborted tx + REQUIRES_NEW 격리

- 작성일: 2026-07-03
- 작성자: TaetaetaE01
- 관련 커밋: `cd30189` (fix(be): trigram 조회를 REQUIRES_NEW 로 격리 — hybrid 폴백 tx abort 수정), 후속 `refactor(be)` 커밋 (tx 경계를 application 헬퍼로 이동 + 본 문서)
- 관련 이슈: #173
- 관련 모듈: `backend/rag` (`RagSearchService`, `PolicyDocumentRepositoryImpl`)

## 한 줄 요약

> hybrid 검색은 `findTopByTrigram` 실패를 catch 해 vector 결과로 폴백하도록 설계돼
> 있었지만, 두 호출부 모두 `@Transactional(readOnly = true)` 안에 있어 PostgreSQL 이
> 트랜잭션을 aborted 상태로 만든 뒤에는 catch 해도 커밋 시점에 다시 예외가 터져
> **검색 호출 전체가 실패**했다. pg_trgm 미설치 DB 로 eval 을 돌리면 73/73 케이스가
> 모두 실패. trigram 조회를 `@Transactional(REQUIRES_NEW)` 로 별도 트랜잭션 격리해
> 해결. 단 트랜잭션 경계는 Application Service 에만 둔다는 컨벤션에 따라, 최종적으로는
> Infrastructure(`PolicyDocumentRepositoryImpl`) 가 아닌 application 레이어의
> 전용 헬퍼 빈(`TrigramSearchExecutor`) 에 이 경계를 둔다.

## 1. 상황 (Context)

- `RagSearchService` 의 hybrid 검색 경로(`hybridSearch`, `searchRelevantChunksWithTrace`)는
  vector 검색과 trigram(pg_trgm `similarity()`) 검색을 각각 수행한 뒤 RRF 로 병합한다.
  trigram 쪽은 pg_trgm extension 이 없는 환경(예: extension 미설치 DB, 새 컨테이너)에서도
  검색 자체는 죽지 않도록 `try/catch (RuntimeException e)` 로 감싸 vector 단독 결과로
  폴백하게 설계돼 있었다.
- eval 러너(`backend/eval`)로 hybrid-on 시나리오를 pg_trgm 미설치 DB 에 대해 돌렸더니
  **73/73 케이스가 전부 실패**. 로그에는 의도한 WARN(`trigram 쿼리 실패, vector 결과로
  폴백: policyId=..., error=...`) 이 정상적으로 찍히는데도 최종적으로 호출 자체가
  예외로 끝났다 — "폴백 로그는 찍히는데 폴백이 안 먹힌다"는 모순적 증상.

## 2. 원인 (Root Cause)

- `hybridSearch`, `searchRelevantChunksWithTrace` 둘 다 `@Transactional(readOnly = true)`
  안에서 실행된다.
- PostgreSQL 은 트랜잭션 내에서 쿼리가 실패하면(예: `function similarity(text, character
  varying) does not exist`) 그 **트랜잭션 자체를 aborted 상태**로 만든다. 같은 트랜잭션
  안의 이후 모든 쿼리는 커밋 여부와 무관하게 즉시 실패한다.
- Java 레벨에서 `catch (RuntimeException e)` 로 예외를 잡아도, JDBC 커넥션/트랜잭션은
  여전히 aborted 상태이므로 메서드 종료 시 Spring 이 커밋을 시도하는 순간
  `UnexpectedRollbackException: Transaction silently rolled back because it has been
  marked as rollback-only` 가 다시 던져진다.
- 재현 스택(회귀 테스트로 확보):
  ```
  org.springframework.transaction.UnexpectedRollbackException:
  Transaction silently rolled back because it has been marked as rollback-only
    at ...AbstractPlatformTransactionManager.processCommit(...)
    at ...TransactionInterceptor.invoke(...)
    at RagSearchService$$SpringCGLIB$$0.searchRelevantChunksWithTrace(<generated>)
  ```
  직전 로그:
  ```
  WARN c.y.r.a.service.RagSearchService : trigram 쿼리 실패, vector 결과로 폴백:
  policyId=1, error=...InvalidDataAccessResourceUsageException: ...
  function similarity(text, character varying) does not exist ...
  ```
- 결론: **폴백 코드는 정상이었지만, 트랜잭션 경계 설계가 폴백을 무효화**시키고 있었다.
  Java try/catch 는 애플리케이션 레벨 제어 흐름을 제어할 뿐, DB 트랜잭션의 aborted
  상태를 리셋하지 못한다.

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. `findTopByTrigram` 을 `@Transactional(REQUIRES_NEW)` 로 별도 트랜잭션 격리 | 실패해도 바깥 트랜잭션을 오염시키지 않음. 변경 범위 최소(어노테이션 1개 + 위치) | 호출마다 순간적으로 커넥션 2개(바깥 tx + 새 tx) 점유 |
| B. `NESTED` propagation (JDBC savepoint) | REQUIRES_NEW 대비 별도 커넥션 불필요 — 같은 커넥션에서 savepoint 로 롤백 | PostgreSQL JDBC savepoint 동작이 Hibernate 세션 캐시·2차 캐시와 상호작용할 때 엣지케이스 존재. 이번 이슈 진단에서 검증된 표준 해법(REQUIRES_NEW)에 비해 검증 비용이 더 큼 |
| C. hybrid 검색 진입 전에 pg_trgm 설치 여부를 애플리케이션이 사전 점검 후 분기 | 트랜잭션을 건드리지 않음 | extension 존재 여부 조회 자체가 추가 쿼리·캐시 필요. "설치돼 있다가 런타임에 실패"하는 경우(권한 변경 등) 는 못 잡음 — 근본 해결 아님 |
| D. catch 블록에서 트랜잭션 자체를 강제 rollback 후 재개 | 코드가 직접 트랜잭션 상태를 제어 | Spring `@Transactional` 선언적 관리와 충돌, `TransactionAspectSupport` 저수준 API 직접 조작은 유지보수 위험 큼 |

## 4. 선택과 이유 (Decision)

- **채택한 대안: A. REQUIRES_NEW 로 trigram 조회를 별도 트랜잭션 격리.**
- **결정의 핵심 근거**:
  1. PostgreSQL aborted-tx 의 표준 해법 — 실패 가능성이 있는 하위 조회를 물리적으로
     분리된 트랜잭션(별도 커넥션)에 두면 실패가 그 트랜잭션 범위 안에서만 소멸한다.
  2. 변경 범위가 작고 가역적 — 어노테이션 하나의 위치를 옮기는 수준.
  3. hybrid 검색은 이미 vector + trigram 2단 조회 구조라 커넥션 2개 추가 점유의
     상대적 비용이 크지 않다고 판단(운영 트래픽 규모 기준).
- **트랜잭션 경계 위치 재조정** (self-review 후속): 최초 구현은 `PolicyDocumentRepositoryImpl`
  (infrastructure) 에 `@Transactional(REQUIRES_NEW)` 를 직접 부여했으나, 이는
  "트랜잭션 경계는 Application Service 에만 둔다" 컨벤션(`.claude/rules/backend/architecture.md`)
  위반이었다. 최종적으로는 application 레이어의 전용 헬퍼 빈 `TrigramSearchExecutor`
  (`backend/src/main/java/com/youthfit/rag/application/service/TrigramSearchExecutor.java`)
  로 트랜잭션 경계를 옮기고, `RagSearchService` 가 repository 대신 이 헬퍼를 호출하도록
  변경했다. `PolicyDocumentRepositoryImpl.findTopByTrigram` 은 순수 조회 메서드로 원복.
- **트레이드오프로 받아들인 것**:
  - **순간 커넥션 2개 점유**: hybrid 검색 요청 1건당 바깥 tx(vector 조회 등) + 새 tx
    (trigram 조회) 가 동시에 활성화되는 짧은 구간이 생긴다. HikariCP
    `maximum-pool-size` 기본값(10, 현재 `application.yml` 에 명시적 override 없음)
    기준으로는 hybrid 트래픽이 급증하면 커넥션 경합 여지가 있다 — **hybrid 검색을
    prod 에서 기본 활성화(`RAG_HYBRID_ENABLED=true`)하기 전에 Hikari
    maximum-pool-size 재산정이 필요**하다(운영 전제로 별도 기록).
  - `PolicyDocumentRepositoryTrigramTest`(`@DataJpaTest`) 가 `PolicyDocumentRepositoryImpl`
    을 직접 테스트하므로 REQUIRES_NEW 의 영향을 받지 않지만, 과거 시행착오
    (아래 §7 참고)로 테스트 트랜잭션 커밋/정리 패턴이 남아 있다.
- **가역성**: 높음. `TrigramSearchExecutor` 만 제거하고 `RagSearchService` 가 다시
  repository 를 직접 호출하도록 되돌리면 원상 복구.
- **재검토 신호**: (a) HikariCP 커넥션 타임아웃/대기 경고가 운영 로그에 나타나면
  pool size 조정 또는 NESTED propagation(§3 대안 B) 재검토, (b) pg_trgm 이 항상
  설치된 환경으로 운영 전제가 바뀌면 이 격리 자체가 불필요해질 수 있음.

## 5. 해결 (Solution)

### 5.1 핵심 구현

- `backend/src/main/java/com/youthfit/rag/application/service/TrigramSearchExecutor.java`
  (신규, `@Service`):
  ```java
  @Service
  @RequiredArgsConstructor
  public class TrigramSearchExecutor {
      private final PolicyDocumentRepository policyDocumentRepository;

      @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
      public List<SimilarChunk> searchTopByTrigram(Long policyId, String query,
                                                     double threshold, int limit) {
          return policyDocumentRepository.findTopByTrigram(policyId, query, threshold, limit);
      }
  }
  ```
- `RagSearchService` 의 `hybridSearch`, `searchRelevantChunksWithTrace` 두 호출부가
  `policyDocumentRepository.findTopByTrigram(...)` 대신
  `trigramSearchExecutor.searchTopByTrigram(...)` 을 호출하도록 변경. catch 블록 로직은
  그대로 유지(WARN 로그 + `tri = List.of()`), 주석만 "REQUIRES_NEW 로 격리돼 있어 실패해도
  바깥 트랜잭션을 오염시키지 않는다"는 취지로 갱신.
- `PolicyDocumentRepositoryImpl.findTopByTrigram` 은 `@Transactional` 없이 순수 조회
  메서드로 원복 — infrastructure 레이어에 트랜잭션 경계를 두지 않는다.

### 5.2 운영 전제

- **pg_trgm extension 은 hybrid 검색이 정상 동작하기 위한 필수 전제**다. REQUIRES_NEW
  격리는 "미설치 시 우아하게 vector 로 폴백"하기 위한 안전망일 뿐, 정상 운영에서는
  pg_trgm 이 설치돼 trigram 결과가 실제로 RRF 병합에 기여해야 hybrid 검색의 이점이
  발휘된다.
- prod/dev DB 는 `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql`
  로 `CREATE EXTENSION IF NOT EXISTS pg_trgm` + GIN 인덱스를 적용한다.
- **로컬 개발 환경은 이 SQL 이 자동 적용되지 않는 구성이면 수동으로
  `CREATE EXTENSION IF NOT EXISTS pg_trgm;` 을 로컬 DB 에 직접 실행해야 한다** —
  안 하더라도 이번 수정 덕분에 검색 자체가 죽지는 않지만(vector 폴백), trigram 이
  기여하는 키워드 정확 매칭 이점 없이 vector-only 와 동일하게 동작한다는 점을
  인지해야 한다.

## 6. 검증 (Result)

- 신규 회귀 테스트: `backend/src/test/java/com/youthfit/rag/application/service/RagSearchTrigramFallbackIntegrationTest.java`
  — Testcontainers `pgvector/pgvector:pg17` 에 `CREATE EXTENSION vector` 만 실행하고
  pg_trgm 은 의도적으로 미설치.
  - RED(수정 전): 2/2 실패, 전부 `UnexpectedRollbackException`.
  - GREEN(수정 후): 2/2 통과 — `trace.trigramTopN()` 은 비고 `trace.merged()` 는
    vector 결과로 채워짐(폴백 성공), 리스트 반환 경로(`rag.hybrid.enabled=true`)도
    예외 없이 결과 반환.
- 기존 `PolicyDocumentRepositoryTrigramTest`(`@DataJpaTest`, pg_trgm 설치된 컨테이너)
  5건 전부 회귀 없이 통과.
- `./gradlew test --tests "com.youthfit.rag.*"` — rag 패키지 전체 테스트 클래스
  `failures="0" errors="0"` 확인(hybrid/trigram/키워드부스트 등 기존 테스트 포함).

## 7. 시행착오 (Trial and Error)

- REQUIRES_NEW 를 도입하자 기존 `PolicyDocumentRepositoryTrigramTest`(`@DataJpaTest`)
  5건 중 2건이 새로 실패(`Expecting actual not to be empty`). 원인: trigram 조회가
  별도 커넥션의 새 트랜잭션에서 실행되므로, `@DataJpaTest` 기본 롤백 트랜잭션 안에서
  아직 커밋되지 않은 `@BeforeEach` 시딩 데이터를 그 별도 트랜잭션이 보지 못함
  (READ COMMITTED + 다른 커넥션 → uncommitted row 미가시).
- 1차 시도(폐기): 테스트 클래스에 `@Transactional(propagation = NOT_SUPPORTED)` 를
  부여해 테스트 트랜잭션 래핑 자체를 껐더니, 이번엔 `jpaRepository.deleteByPolicyId(...)`
  (커스텀 derived delete 쿼리) 가 `TransactionRequiredException: No EntityManager with
  actual transaction available` 로 실패(5건 중 4건 실패, 오히려 악화). `@DataJpaTest` +
  클래스-레벨 `NOT_SUPPORTED` 조합에서 리포지토리 기본 폴백 트랜잭션이 기대대로
  걸리지 않아 폐기.
- 채택한 수정: `@BeforeEach` 끝에 Spring TestContext 의 `TestTransaction.flagForCommit();
  TestTransaction.end(); TestTransaction.start();` 를 추가해 시딩 데이터를 실제로
  커밋한 뒤 새 test-managed 트랜잭션을 열어 assertion 을 실행. `@AfterEach` 에도
  같은 패턴으로 `deleteByPolicyId` 후 커밋해 테스트 간 격리를 명시적으로 보장.

## 8. 참고 (References)

- 이슈: #173
- 관련 컨벤션: `.claude/rules/backend/architecture.md` (트랜잭션 경계는 Application
  Service 에만)
- `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql`
  (pg_trgm extension + GIN 인덱스)
- `backend/src/test/java/com/youthfit/rag/application/service/RagSearchTrigramFallbackIntegrationTest.java`
- `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryTrigramTest.java`
