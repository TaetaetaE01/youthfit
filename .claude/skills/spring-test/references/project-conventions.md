# YouthFit 프로젝트 테스트 컨벤션

> YouthFit 프로젝트에서 확립된 테스트 작성 규칙.
> 새 테스트 작성 시 반드시 이 컨벤션을 따른다.

---

## 1. 테스트 어노테이션

| 테스트 유형 | 어노테이션 | 사용처 |
|-----------|----------|-------|
| Unit (Service) | `@ExtendWith(MockitoExtension.class)` | Service, 유틸 |
| Unit (Entity) | 없음 (순수 JUnit5) | Entity 도메인 로직 |
| Slice (Controller) | `@WebMvcTest(XxxController.class)` | HTTP 계층 |
| Slice (Repository) | `@DataJpaTest` | JPA 쿼리, Fetch 전략 |
| Integration | `@SpringBootTest` + `@ActiveProfiles("test")` | 전체 플로우 (최소한) |

## 2. 메서드 네이밍

**패턴**: `methodName_scenario_expectedResult`

```java
// Good
void open_fromUpcoming_statusChangesToOpen()
void open_fromOpen_throwsYouthFitException()
void findPolicyById_notExists_throwsNotFoundException()
void loginWithKakao_newUser_registersAndReturnsToken()

// Bad
void testOpen()                          // 'test' 접두사 금지
void open()                              // 시나리오 불명확
void shouldOpenPolicySuccessfully()      // 다른 프로젝트 패턴
```

## 3. @DisplayName

**한국어로 테스트 의도를 서술한다.** 메서드명으로는 표현이 제한되는 비즈니스 맥락을 여기에 쓴다.

```java
@Test
@DisplayName("UPCOMING 상태의 정책을 모집 시작하면 OPEN으로 전이한다")
void open_fromUpcoming_statusChangesToOpen() { ... }

@Test
@DisplayName("유효하지 않은 리프레시 토큰으로 갱신하면 UNAUTHORIZED 예외가 발생한다")
void refreshAccessToken_invalidToken_throwsUnauthorized() { ... }
```

## 4. BDDMockito 스타일

**`given` / `willReturn` / `then().should()` 패턴을 사용한다.** Mockito의 `when`/`verify` 대신.

```java
// Given (스텁 설정)
given(policyRepository.findById(1L)).willReturn(Optional.of(mockPolicy));

// When (실행)
PolicyDetailResult result = policyQueryService.findPolicyById(1L);

// Then (검증)
assertThat(result.id()).isEqualTo(1L);
assertThat(result.title()).isEqualTo("청년 주거 지원");
then(policyRepository).should().findById(1L);
```

## 5. AssertJ 검증

**모든 검증에 AssertJ fluent API를 사용한다.** JUnit의 `assertEquals` 금지.

```java
// 기본 검증
assertThat(result).isNotNull();
assertThat(result.id()).isEqualTo(1L);
assertThat(result.status()).isEqualTo(PolicyStatus.OPEN);

// 컬렉션 검증
assertThat(policies)
    .hasSize(3)
    .extracting(PolicySummaryResult::category)
    .contains(Category.EMPLOYMENT, Category.HOUSING);

// 예외 검증 — YouthFitException 패턴
assertThatThrownBy(() -> policy.open())
    .isInstanceOf(YouthFitException.class)
    .satisfies(ex -> {
        YouthFitException yfe = (YouthFitException) ex;
        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    });
```

## 6. Given-When-Then 주석

**섹션 구분 주석을 반드시 작성한다.**

```java
@Test
@DisplayName("필터 조건으로 정책 목록을 페이징 조회한다")
void findPoliciesByFilters_withCategory_returnsFilteredPage() {
    // given
    Page<Policy> mockPage = new PageImpl<>(List.of(createMockPolicy()));
    given(policyRepository.findAllByFilters(any(), eq(Category.EMPLOYMENT), any(), any()))
            .willReturn(mockPage);

    // when
    PolicyPageResult result = policyQueryService.findPoliciesByFilters(
            null, Category.EMPLOYMENT, null, "createdAt", false, 0, 20);

    // then
    assertThat(result.policies()).hasSize(1);
    assertThat(result.totalElements()).isEqualTo(1);
}
```

## 7. Mock 관련

### @MockitoBean (Slice Test)

```java
// Slice Test에서 사용
@WebMvcTest(PolicyController.class)
class PolicyControllerTest {
    @MockitoBean PolicyQueryService policyQueryService;  // @MockBean 대신
    @Autowired MockMvc mockMvc;
}
```

### @Mock + @InjectMocks (Unit Test)

```java
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {
    @InjectMocks private PolicyQueryService policyQueryService;
    @Mock private PolicyRepository policyRepository;
}
```

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @InjectMocks private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private KakaoOAuthClient kakaoOAuthClient;
    @Mock private JwtProvider jwtProvider;
}
```

## 8. 헬퍼 메서드

**반복되는 Mock 객체 생성은 `createMock...()` 패턴으로 추출한다.**

```java
private Policy createMockPolicy() {
    Policy policy = Policy.builder()
            .title("청년 주거 지원")
            .summary("월세 지원 프로그램")
            .category(Category.HOUSING)
            .regionCode("11")
            .applyStart(LocalDate.of(2026, 1, 1))
            .applyEnd(LocalDate.of(2026, 12, 31))
            .build();
    ReflectionTestUtils.setField(policy, "id", 1L);
    return policy;
}

private User createMockUser() {
    User user = User.builder()
            .email("test@example.com")
            .nickname("테스트유저")
            .profileImageUrl(null)
            .authProvider(AuthProvider.KAKAO)
            .providerId("kakao_123")
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
}
```

## 9. Security 테스트

Controller 슬라이스 테스트에서 Spring Security가 개입하므로, 테스트용 SecurityConfig를 별도로 구성한다.

```java
@TestConfiguration
public class TestSecurityConfig {
    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
```

인증이 필요한 테스트의 경우 `@WithMockUser` 또는 커스텀 어노테이션을 사용한다.

## 10. 디렉토리 구조

**소스 코드와 동일한 패키지 구조를 테스트에서도 유지한다.**

```
src/test/java/com/youthfit/
├── policy/
│   ├── domain/
│   │   └── model/
│   │       └── PolicyTest.java
│   ├── application/
│   │   └── service/
│   │       └── PolicyQueryServiceTest.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── PolicyRepositoryTest.java
│   └── presentation/
│       └── controller/
│           └── PolicyControllerTest.java
├── auth/
│   └── application/
│       └── service/
│           └── AuthServiceTest.java
├── user/
│   ├── domain/
│   │   └── model/
│   │       └── UserTest.java
│   └── ...
└── common/
    └── support/          ← 테스트 유틸리티
        └── TestSecurityConfig.java
```

## 11. 테스트 설정

- **프로파일**: `@ActiveProfiles("test")` → `application-test.yml` 로드
- **DB**: H2 인메모리 (`jdbc:h2:mem:youthfit`)
- **JPA**: `ddl-auto: create-drop` (테스트마다 스키마 재생성)
- **외부 서비스**: 모두 Mock 또는 비활성화 (KakaoOAuthClient, LLM 등)
