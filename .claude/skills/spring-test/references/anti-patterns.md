# 테스트 안티패턴

> 작성된 테스트의 품질을 검증할 때 사용하는 안티패턴 체크리스트.
> `--fix-only` 모드에서 이 체크리스트를 기반으로 기존 테스트를 개선한다.

---

## HIGH 심각도 (반드시 수정)

### 1. 항상 @SpringBootTest 사용

**증상**: 모든 테스트에 `@SpringBootTest` 사용, Service 단위 테스트에도.
**문제**: 불필요한 컨텍스트 로딩으로 테스트 속도 저하.
**수정**: 레이어에 맞는 최소 테스트 유형 사용.

```java
// BAD
@SpringBootTest
class PolicyQueryServiceTest {
    @MockitoBean PolicyRepository repo;
}

// GOOD
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {
    @Mock PolicyRepository repo;
    @InjectMocks PolicyQueryService service;
}
```

### 2. 테스트 간 상태 공유

**증상**: static 필드로 데이터 공유, 테스트 실행 순서 의존.
**문제**: 병렬 실행 불가, 간헐적 실패.
**수정**: 각 테스트에서 독립적으로 데이터 준비.

### 3. 구현 세부사항 검증

**증상**: `verify()` 호출이 3개 이상, 내부 메서드 호출 순서 검증.
**문제**: 리팩토링 시 테스트가 깨짐 (거짓 음성).
**수정**: 행위/결과를 검증, `verify`는 핵심 부수 효과에만.

### 4. 예외 경로 미테스트

**증상**: Happy path만 테스트, 예외/에러 경로 없음.
**문제**: 에러 핸들링 회귀 버그 미발견.
**수정**: 각 public 메서드에 최소 1개 예외 테스트. YouthFitException의 ErrorCode까지 검증.

---

## MEDIUM 심각도 (개선 권장)

### 5. Over-Mocking

**증상**: Mock 설정이 테스트 로직보다 길다. `given()` 5줄 이상.
**문제**: 테스트가 구현에 강하게 결합, 읽기 어려움.
**수정**: Mock 대상 줄이기, 테스트 유형 재고 (Unit -> Integration).

### 6. @DisplayName 누락

**증상**: 메서드명만으로 의도 파악 어려움.
**문제**: 실패 시 어떤 비즈니스 규칙이 깨졌는지 불분명.
**수정**: 한국어 @DisplayName으로 비즈니스 의도 서술.

### 7. 하나의 테스트에 다중 관심사

**증상**: 하나의 테스트 메서드에서 여러 독립적인 동작 검증.
**문제**: 실패 시 어떤 관심사가 실패했는지 불분명.
**수정**: 관심사별 테스트 메서드 분리.

### 8. 하드코딩 매직 값

**증상**: `assertThat(result).hasSize(5)` — 5가 왜 5인지 불분명.
**문제**: 테스트 의도 불명확.
**수정**: 상수 추출 또는 주석으로 의미 설명.

### 9. @DirtiesContext 남용

**증상**: 여러 테스트 메서드에 `@DirtiesContext`.
**문제**: 컨텍스트 매번 재로딩 -> 극심한 속도 저하.
**수정**: `@Transactional` 또는 `@AfterEach` cleanup.

### 10. 중복 테스트 코드

**증상**: 동일한 given 설정이 5개 이상 테스트에 반복.
**문제**: 유지보수 비용 증가.
**수정**: `createMock...()` 헬퍼 메서드 추출, `@BeforeEach`에 공통 설정.

---

## LOW 심각도 (시간 있으면 개선)

### 11. Given-When-Then 주석 누락

**증상**: 섹션 구분 없이 코드 나열.
**수정**: `// given`, `// when`, `// then` 주석 추가.

### 12. 불필요한 assertion

**증상**: `assertThat(result).isNotNull()` 후 바로 필드 접근.
**문제**: NPE가 이미 실패를 나타내므로 중복.
**수정**: 의미 있는 검증만 남기기.

### 13. 'test' 접두사

**증상**: `testFindPolicyById()`.
**문제**: JUnit 4 잔재, `@Test`가 이미 표시.
**수정**: `findPolicyById_exists_returnsDetail()` 패턴으로 변경.

### 14. Raw JUnit assertions

**증상**: `assertEquals`, `assertTrue` 사용.
**문제**: 실패 메시지 불명확, 체이닝 불가.
**수정**: AssertJ `assertThat()` 으로 교체.

---

## 품질 검증 체크리스트

테스트 작성 후 아래 항목을 순서대로 확인:

- [ ] 컴파일 성공 (`./gradlew compileTestJava`)
- [ ] 테스트 실행 성공 (`./gradlew test --tests "{TestClass}"`)
- [ ] HIGH 안티패턴 해당 없음
- [ ] 각 테스트 독립적 실행 가능
- [ ] @DisplayName 한국어 작성
- [ ] Given-When-Then 구조
- [ ] BDDMockito 스타일
- [ ] AssertJ fluent API
- [ ] 예외 경로 포함 (YouthFitException + ErrorCode 검증)
- [ ] 불필요한 Mock 없음
