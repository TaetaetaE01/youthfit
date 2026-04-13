# 테스트 베스트 프랙티스

> Java 21 + Spring Boot 4.x 환경에서의 테스트 베스트 프랙티스.

---

## 1. 테스트 피라미드

```
         /\
        /  \       Integration (10%)
       /----\      @SpringBootTest — 전체 플로우
      /      \
     / Slice  \    Slice (20%)
    /  Tests   \   @WebMvcTest, @DataJpaTest — 레이어별
   /--------------\
  /  Unit Tests    \  Unit (70%)
 / Fast & Isolated  \ @ExtendWith(MockitoExtension.class), 순수 Java
/--------------------\
```

**원칙**: 가장 가벼운 테스트 유형으로 충분히 검증할 수 있으면 그것을 사용한다.

---

## 2. 독립성 (Test Isolation)

**각 테스트는 다른 테스트에 의존하지 않는다.**

```java
// BAD: 테스트 간 상태 공유
static Policy sharedPolicy;

@Test void test1_create() { sharedPolicy = createPolicy(); }
@Test void test2_open() { sharedPolicy.open(); }  // test1에 의존

// GOOD: 각 테스트가 독립적으로 데이터 준비
@Test void open_fromUpcoming_statusChanges() {
    Policy policy = createMockPolicy();  // 독립적으로 준비
    policy.open();
    assertThat(policy.getStatus()).isEqualTo(PolicyStatus.OPEN);
}
```

---

## 3. 하나의 관심사만 검증

```java
// BAD: 여러 관심사를 한 테스트에서 검증
@Test void loginWithKakao_test() {
    // 사용자 조회 + 토큰 발급 + 리프레시 토큰 저장 + 응답 구성...
}

// GOOD: 관심사별 분리
@Test void loginWithKakao_existingUser_returnsToken() { ... }
@Test void loginWithKakao_newUser_registersUser() { ... }
@Test void loginWithKakao_success_updatesRefreshToken() { ... }
```

---

## 4. 행위 검증 vs 구현 검증

**구현 세부사항이 아닌 행위(결과)를 검증한다.**

```java
// BAD: 내부 구현에 의존 (리팩토링 시 깨짐)
@Test void findPolicyById() {
    service.findPolicyById(1L);
    verify(repository).findById(any());    // 어떤 메서드를 호출했는지 확인 -> 구현 의존
    verify(mapper).toResult(any());        // 매퍼 호출 확인 -> 구현 의존
}

// GOOD: 결과(행위)를 검증
@Test void findPolicyById_exists_returnsDetail() {
    given(policyRepository.findById(1L)).willReturn(Optional.of(mockPolicy));

    PolicyDetailResult result = policyQueryService.findPolicyById(1L);

    assertThat(result.title()).isEqualTo("청년 주거 지원");
    assertThat(result.status()).isEqualTo(PolicyStatus.OPEN);
}
```

**예외**: 부수 효과(side effect)가 핵심 비즈니스 요구사항인 경우는 `then().should()` 사용.
예: "로그아웃 시 리프레시 토큰이 반드시 삭제되어야 한다"

---

## 5. @Nested로 구조화

**테스트 케이스가 5개 이상이면 @Nested로 메서드/시나리오별 그룹화한다.**

```java
@DisplayName("PolicyQueryService")
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {

    @Nested
    @DisplayName("findPolicyById")
    class FindPolicyById {
        @Test
        @DisplayName("존재하는 정책 ID로 조회하면 상세 결과를 반환한다")
        void exists_returnsDetail() { ... }

        @Test
        @DisplayName("존재하지 않는 정책 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        void notExists_throwsNotFoundException() { ... }
    }

    @Nested
    @DisplayName("findPoliciesByFilters")
    class FindPoliciesByFilters {
        @Test
        @DisplayName("카테고리 필터로 정책을 조회한다")
        void withCategory_returnsFilteredPage() { ... }

        @Test
        @DisplayName("필터 없이 전체 정책을 페이징 조회한다")
        void noFilters_returnsAllPaged() { ... }
    }
}
```

---

## 6. @ParameterizedTest 활용

**동일 로직에 대해 여러 입력을 테스트할 때 사용한다.**

```java
@ParameterizedTest
@DisplayName("정책 상태 전이 규칙 검증")
@CsvSource({
    "UPCOMING, OPEN, true",
    "OPEN, CLOSED, true",
    "UPCOMING, CLOSED, false",
    "CLOSED, OPEN, false"
})
void statusTransition_rules(PolicyStatus from, PolicyStatus to, boolean valid) {
    Policy policy = createPolicyWithStatus(from);
    if (valid) {
        assertThatCode(() -> transitionTo(policy, to)).doesNotThrowAnyException();
    } else {
        assertThatThrownBy(() -> transitionTo(policy, to))
                .isInstanceOf(YouthFitException.class);
    }
}

@ParameterizedTest
@DisplayName("ErrorCode별 HTTP 상태 코드 매핑")
@EnumSource(ErrorCode.class)
void errorCode_hasValidHttpStatus(ErrorCode errorCode) {
    assertThat(errorCode.getStatus())
            .isBetween(400, 599);
}
```

---

## 7. 예외 테스트 패턴

```java
// YouthFitException 검증 패턴
@Test
@DisplayName("존재하지 않는 정책 ID로 조회하면 NOT_FOUND 예외가 발생한다")
void findPolicyById_notExists_throwsNotFoundException() {
    given(policyRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> policyQueryService.findPolicyById(999L))
            .isInstanceOf(YouthFitException.class)
            .satisfies(ex -> {
                YouthFitException yfe = (YouthFitException) ex;
                assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
            });
}

// 예외가 발생하지 않아야 하는 경우
@Test
@DisplayName("UPCOMING 상태에서 open 호출 시 예외 없이 전이된다")
void open_fromUpcoming_noException() {
    Policy policy = createUpcomingPolicy();
    assertThatCode(() -> policy.open())
            .doesNotThrowAnyException();
}
```

---

## 8. Controller (Slice) 테스트 패턴

```java
@WebMvcTest(PolicyController.class)
@Import(TestSecurityConfig.class)
class PolicyControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PolicyQueryService policyQueryService;

    @Test
    @DisplayName("정책 상세 조회 API가 200과 정책 정보를 반환한다")
    void getPolicyDetail_returns200() throws Exception {
        // given
        given(policyQueryService.findPolicyById(1L))
                .willReturn(createMockDetailResult());

        // when & then
        mockMvc.perform(get("/api/v1/policies/{policyId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("청년 주거 지원"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("키워드 검색 API가 페이징된 결과를 반환한다")
    void searchPolicies_withKeyword_returnsPage() throws Exception {
        // given
        given(policyQueryService.searchPoliciesByKeyword(eq("주거"), eq(0), eq(20)))
                .willReturn(createMockPageResult());

        // when & then
        mockMvc.perform(get("/api/v1/policies/search")
                        .param("keyword", "주거"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray());
    }
}
```

---

## 9. Repository (Slice) 테스트 패턴

```java
@DataJpaTest
@ActiveProfiles("test")
class PolicyJpaRepositoryTest {

    @Autowired private PolicyJpaRepository policyJpaRepository;
    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("카테고리와 상태로 정책을 필터링 조회한다")
    void findByFilters_withCategoryAndStatus_returnsFiltered() {
        // given
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .category(Category.HOUSING)
                .build();
        em.persistAndFlush(policy);
        em.clear();  // 1차 캐시 초기화

        // when
        List<Policy> result = policyJpaRepository
                .findAll(PolicySpecification.withFilters("11", Category.HOUSING, null));

        // then
        assertThat(result)
                .hasSize(1)
                .extracting(Policy::getCategory)
                .containsOnly(Category.HOUSING);
    }
}
```

---

## 10. Entity (Unit) 테스트 패턴

```java
class PolicyTest {

    @Test
    @DisplayName("정책 생성 시 기본 상태는 UPCOMING이다")
    void create_defaultStatus_isUpcoming() {
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .category(Category.HOUSING)
                .build();

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.UPCOMING);
        assertThat(policy.getDetailLevel()).isEqualTo(DetailLevel.LITE);
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {
        @Test
        @DisplayName("UPCOMING -> OPEN 전이 성공")
        void open_fromUpcoming_success() {
            Policy policy = createUpcomingPolicy();
            policy.open();
            assertThat(policy.getStatus()).isEqualTo(PolicyStatus.OPEN);
        }

        @Test
        @DisplayName("OPEN 상태에서 open 호출 시 INVALID_INPUT 예외")
        void open_fromOpen_throwsException() {
            Policy policy = createOpenPolicy();
            assertThatThrownBy(() -> policy.open())
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });
        }
    }

    @Test
    @DisplayName("마감일이 오늘 이전이면 만료 상태로 판단한다")
    void isExpired_pastEndDate_returnsTrue() {
        Policy policy = Policy.builder()
                .title("만료된 정책")
                .category(Category.EMPLOYMENT)
                .applyEnd(LocalDate.now().minusDays(1))
                .build();

        assertThat(policy.isExpired()).isTrue();
    }
}
```

---

## 11. Soft Assertions (다중 검증)

**하나의 객체에 대해 여러 필드를 검증할 때 SoftAssertions로 한 번에 실패 리포트.**

```java
@Test
@DisplayName("PolicyDetailResult가 Entity의 모든 필드를 올바르게 변환한다")
void from_mapsAllFields() {
    Policy policy = createMockPolicy();

    PolicyDetailResult result = PolicyDetailResult.from(policy);

    SoftAssertions.assertSoftly(softly -> {
        softly.assertThat(result.id()).isEqualTo(policy.getId());
        softly.assertThat(result.title()).isEqualTo(policy.getTitle());
        softly.assertThat(result.category()).isEqualTo(policy.getCategory());
        softly.assertThat(result.status()).isEqualTo(policy.getStatus());
    });
}
```

---

## 12. Mock 사용 원칙

### Mock 대상 (외부 의존성)

- Repository (Unit Test에서)
- 외부 API 클라이언트 (KakaoOAuthClient, LLM 클라이언트 등)
- JwtProvider (토큰 생성/검증)
- 시간/랜덤 같은 비결정적 요소

### Mock 금지 (Real 사용)

- Entity 생성 및 도메인 로직 (`policy.open()`, `user.updateProfile()`)
- DTO record 변환 (`PolicyDetailResult.from(policy)`)
- 유틸리티 메서드 (`DateTimeUtil`)
- Enum, VO

### Over-Mocking 징후

- Mock 설정이 테스트 로직보다 긴 경우
- `verify()`가 3개 이상인 경우
- given() 체인이 5줄 이상인 경우

-> 테스트 대상 클래스의 책임이 과도하거나, 테스트 유형이 부적절한 신호.
