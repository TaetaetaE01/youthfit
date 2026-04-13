# 테스트 유형별 상세 가이드

> 각 테스트 유형의 목적, 사용 시점, 템플릿.
> YouthFit 프로젝트의 DDD + Clean Architecture 레이어에 맞춘 가이드.

---

## Unit Test (단위 테스트)

### 목적
Spring 컨텍스트 없이 순수 Java로 비즈니스 로직을 검증한다.

### 사용 시점
- Service 비즈니스 로직 (`application/service`)
- Entity 도메인 행위 — 상태 전이, 불변식, 계산 (`domain/model`)
- DTO record 변환 로직 (`from()` 메서드)
- 유틸리티 클래스 (`common/util`)

### 템플릿: Service

```java
@ExtendWith(MockitoExtension.class)
class {ServiceName}Test {

    @InjectMocks
    private {ServiceName} {fieldName};

    @Mock
    private {RepositoryInterface} {repositoryName};
    // ... 추가 Mock (외부 클라이언트, JwtProvider 등)

    @Test
    @DisplayName("{한국어 설명}")
    void {methodName}_{scenario}_{expectedResult}() {
        // given
        {given 설정 — BDDMockito}

        // when
        {실행}

        // then
        {AssertJ 검증}
    }

    // 헬퍼 메서드
    private {Entity} createMock{Entity}() {
        {Entity} entity = {Entity}.builder()
                .{field}({value})
                .build();
        ReflectionTestUtils.setField(entity, "id", 1L);
        return entity;
    }
}
```

### 템플릿: Entity

```java
class {EntityName}Test {

    @Test
    @DisplayName("{한국어 설명}")
    void {methodName}_{scenario}_{expectedResult}() {
        // given
        {Entity} entity = {Entity}.builder()
                .{field}({value})
                .build();

        // when
        {도메인 메서드 호출}

        // then
        {AssertJ 검증}
    }
}
```

### 템플릿: DTO record 변환

```java
class {RecordName}Test {

    @Test
    @DisplayName("{RecordName}이 Entity로부터 올바르게 변환된다")
    void from_mapsAllFields() {
        // given
        {Entity} entity = createMock{Entity}();

        // when
        {RecordName} result = {RecordName}.from(entity);

        // then
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.{field}()).isEqualTo(entity.get{Field}());
            // ...
        });
    }
}
```

### 성능
- 실행 시간: ~1ms per test
- Spring 컨텍스트: 불필요
- DB: 불필요

---

## Slice Test (슬라이스 테스트)

### 목적
특정 레이어만 로드하여 해당 레이어의 동작을 검증한다.

### @WebMvcTest (Controller)

**검증 대상:**
- HTTP 요청/응답 매핑
- 입력 유효성 검사 (@Valid)
- 인증/인가 동작
- 응답 JSON 구조
- HTTP 상태 코드

**로드되는 것:** Controller, ControllerAdvice, Filter, Converter
**로드 안 되는 것:** Service, Repository, Entity

```java
@WebMvcTest({ControllerClass}.class)
@Import(TestSecurityConfig.class)
class {ControllerClass}Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private {ServiceClass} {serviceName};

    @Test
    @DisplayName("{한국어 설명}")
    void {endpoint}_{scenario}_{expectedStatus}() throws Exception {
        // given
        given({serviceName}.{method}(any())).willReturn({mockResult});

        // when & then
        mockMvc.perform({httpMethod}("/api/v1/{resource}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString({request})))
                .andExpect(status().{expectedStatus}())
                .andExpect(jsonPath("$.{field}").value({expected}));
    }
}
```

### @DataJpaTest (Repository)

**검증 대상:**
- JPQL/Native 쿼리 정확성
- Specification 동적 쿼리
- Fetch Join / EntityGraph 동작
- 정렬/페이징 동작

**로드되는 것:** JPA 관련 빈, Repository, TestEntityManager
**로드 안 되는 것:** Controller, Service

```java
@DataJpaTest
@ActiveProfiles("test")
class {JpaRepositoryClass}Test {

    @Autowired
    private {JpaRepositoryClass} {repositoryName};

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("{한국어 설명}")
    void {queryMethod}_{scenario}_{expectedResult}() {
        // given
        {Entity} entity = {Entity}.builder().{...}.build();
        em.persistAndFlush(entity);
        em.clear();  // 1차 캐시 초기화 — 실제 쿼리 발생 보장

        // when
        {result} = {repositoryName}.{queryMethod}({params});

        // then
        {AssertJ 검증}
    }
}
```

**참고**: YouthFit은 도메인 Repository 인터페이스(`PolicyRepository`)와 인프라 구현체(`PolicyRepositoryImpl` + `PolicyJpaRepository`)를 분리한다. `@DataJpaTest`에서는 `PolicyJpaRepository`(Spring Data JPA)를 직접 테스트한다.

### 성능
- @WebMvcTest: ~200-300ms 컨텍스트 로딩
- @DataJpaTest: ~150-250ms 컨텍스트 로딩
- 캐시: 동일 설정이면 컨텍스트 재사용

---

## Integration Test (통합 테스트)

### 목적
전체 Spring 컨텍스트를 로드하여 레이어 간 상호작용을 검증한다.

### 사용 시점 (최소한만)
- Service -> Repository -> DB 전체 플로우
- 트랜잭션 전파 동작
- 여러 Service가 협력하는 복합 비즈니스 로직
- 실제 DB 제약조건 검증 (Unique, FK 등)

### 템플릿

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // 테스트 후 자동 롤백
class {ClassName}IntegrationTest {

    @Autowired
    private {ServiceClass} {serviceName};

    @Autowired
    private {JpaRepositoryClass} {repositoryName};

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;  // 외부 API는 Mock

    @Test
    @DisplayName("{한국어 설명}")
    void {scenario}_fullFlow() {
        // given
        {데이터 준비}

        // when
        {Service 호출}

        // then
        {DB 상태 검증}
    }
}
```

### 성능
- 실행 시간: ~1000-2000ms 컨텍스트 로딩 (캐시 시 재사용)
- DB: H2 인메모리
- 주의: `@DirtiesContext` 남용 금지 (컨텍스트 재로딩)

---

## 테스트 유형 선택 Decision Tree

```
대상 코드가 뭔가?
|
+- Entity 도메인 로직 (domain/model)
|  -> Unit Test (순수 Java)
|
+- Service 비즈니스 로직 (application/service)
|  +- 단일 Service 내 로직 -> Unit Test (Mockito)
|  +- Service + DB 연동 필수 -> Integration Test
|
+- Controller HTTP 처리 (presentation/controller)
|  -> Slice Test (@WebMvcTest)
|
+- Repository 쿼리 (infrastructure/persistence)
|  -> Slice Test (@DataJpaTest)
|
+- DTO record 변환 (dto)
|  -> Unit Test (순수 Java)
|
+- 전체 플로우 (레이어 간 상호작용)
   -> Integration Test (@SpringBootTest)
```
