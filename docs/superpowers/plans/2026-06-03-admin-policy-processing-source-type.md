# 어드민 정책처리현황 출처 타입 표시·필터 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민 "정책 처리 현황" 목록에서 각 정책의 출처(청년몽땅정보통/복지로/온통청년)를 한글 뱃지로 보여주고, 출처별로 필터링할 수 있게 한다.

**Architecture:** `PolicySource`(1:N) 의 `source_type` 을 정책별로 일괄 조회(기존 stepMap/attachMap 패턴)해 응답에 `sources` 목록으로 싣는다. 출처 필터는 정확한 페이징을 위해 `findForAdminProcessing` JPQL 에 `EXISTS` 서브쿼리로 SQL 레벨에서 적용한다. 프론트는 출처 `code` → 테두리 색 매핑으로 뱃지를 렌더하고, 필터 select 를 `searchParams` 에 연동한다.

**Tech Stack:** Java 21, Spring Boot 4.x, Spring Data JPA(JPQL), React 19 + TypeScript, TanStack Query, Tailwind v4, Vitest + Testing Library.

---

## File Structure

**백엔드 — 도메인/인프라 (출처 조회·필터)**
- Modify: `policy/domain/repository/PolicySourceRepository.java` — 정책별 출처 타입 목록 일괄 조회 메서드 추가
- Modify: `policy/infrastructure/persistence/PolicySourceRepositoryImpl.java` — 위 구현
- Modify: `policy/domain/repository/PolicyRepository.java` — `findForAdminProcessing` 에 sourceType 파라미터 추가
- Modify: `policy/infrastructure/persistence/PolicyRepositoryImpl.java` — sourceType 전달
- Modify: `policy/infrastructure/persistence/PolicyJpaRepository.java` — JPQL 에 EXISTS 필터 추가

**백엔드 — admin DTO/서비스/컨트롤러**
- Create: `admin/application/dto/SourceTagResult.java` — `{code, label}` Result
- Modify: `admin/application/dto/PolicyProcessingItemResult.java` — `sources` 필드 추가
- Modify: `admin/application/dto/PolicyProcessingListCommand.java` — `sourceType` 필드 추가
- Create: `admin/presentation/dto/response/SourceTagResponse.java` — `{code, label}` Response
- Modify: `admin/presentation/dto/response/PolicyProcessingItemResponse.java` — `sources` 필드 + from 매핑
- Modify: `admin/application/service/AdminPolicyProcessingService.java` — sourceMap 조립 + 필터 전달
- Modify: `admin/presentation/controller/AdminPolicyProcessingController.java` — `sourceType` 쿼리 파라미터 + parse
- Modify: `admin/presentation/api/AdminPolicyProcessingApi.java` — `sourceType` `@Parameter` + Swagger 설명

**프론트엔드**
- Modify: `frontend/src/types/adminPolicyProcessing.ts` — `SourceTag` 타입, `PolicyProcessingItem.sources`, `PolicyProcessingListParams.sourceType`
- Modify: `frontend/src/apis/adminPolicyProcessing.api.ts` — `cleanParams` 에 sourceType
- Create: `frontend/src/pages/admin/policy-processing/SourceBadges.tsx` — 출처 뱃지(테두리 색 매핑)
- Create: `frontend/src/pages/admin/policy-processing/__tests__/SourceBadges.test.tsx`
- Modify: `frontend/src/pages/admin/policy-processing/PolicyProcessingTable.tsx` — 제목 셀에 뱃지
- Modify: `frontend/src/pages/admin/policy-processing/PolicyProcessingFilters.tsx` — 출처 select
- Modify: `frontend/src/pages/admin/AdminPolicyProcessingPage.tsx` — `sourceType` searchParams 연동

---

## 공통 참조 사실 (모든 Task 공통)

- `SourceType` enum (`policy/domain/model/SourceType.java`): `YOUTH_SEOUL_CRAWL`("청년몽땅정보통"), `BOKJIRO_CENTRAL`("복지로"), `YOUTH_CENTER`("온통청년"). `getLabel()` 보유.
- 백엔드는 도메인 enum 누수를 막기 위해 응답에서 `{code:String, label:String}` 형태로 평탄화한다 (기존 stepStatuses 가 `Map<String,String>` 으로 평탄화하는 것과 동일 원칙).
- 빌드 검증: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm run test`.

---

## Task 1: 정책별 출처 타입 일괄 조회 (도메인 + 인프라)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java`
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java` (없으면 생성)

기존 `findFirstByPolicyIds` 가 `jpaRepository.findAllByPolicyIdInOrderByIdAsc(policyIds)` 를 재사용하므로, 동일한 호출을 재사용해 정책별로 `List<SourceType>` 을 모은다(삽입 순서 유지, 중복 제거).

- [ ] **Step 1: 도메인 인터페이스에 메서드 선언 추가**

`PolicySourceRepository.java` 에 추가 (import `java.util.List`, `java.util.Map` 이미 존재):

```java
    /**
     * 정책별 출처 타입 목록 일괄 조회.
     * id 오름차순으로 모으며, 같은 정책에 동일 출처가 중복 등록돼도 한 번만 담는다.
     * 조회된 출처가 없는 정책은 결과 맵에 키가 없다(호출자가 빈 리스트로 기본 처리).
     */
    Map<Long, List<SourceType>> findSourceTypesByPolicyIds(List<Long> policyIds);
```

- [ ] **Step 2: 실패하는 구현체 테스트 작성**

`PolicySourceRepositoryImplTest.java` (`@DataJpaTest` + `@Import(PolicySourceRepositoryImpl.class)`). Policy 2건, policy1 에 YOUTH_SEOUL_CRAWL + BOKJIRO_CENTRAL, policy2 에 YOUTH_CENTER 를 저장한 뒤:

```java
@Test
void 정책별_출처타입_목록을_일괄조회한다() {
    Map<Long, List<SourceType>> result =
            repository.findSourceTypesByPolicyIds(List.of(policy1Id, policy2Id));

    assertThat(result.get(policy1Id))
            .containsExactly(SourceType.YOUTH_SEOUL_CRAWL, SourceType.BOKJIRO_CENTRAL);
    assertThat(result.get(policy2Id)).containsExactly(SourceType.YOUTH_CENTER);
}

@Test
void 빈_입력은_빈_맵을_반환한다() {
    assertThat(repository.findSourceTypesByPolicyIds(List.of())).isEmpty();
}
```

> 기존 admin/policy 슬라이스 테스트의 엔티티 빌더 사용법(`Policy.builder()`, `PolicySource.builder()`)을 따른다. `PolicySource.builder()` 는 `policy`, `sourceType`, `externalId`, `sourceHash` 가 필수다(`source_url`/`raw_json` 은 nullable).

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySourceRepositoryImplTest"`
Expected: FAIL — `findSourceTypesByPolicyIds` 미구현 (컴파일 에러 또는 메서드 없음)

- [ ] **Step 4: 구현체 작성**

`PolicySourceRepositoryImpl.java` 에 추가 (`import java.util.ArrayList;` 추가):

```java
    @Override
    public Map<Long, List<SourceType>> findSourceTypesByPolicyIds(List<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        List<PolicySource> all = jpaRepository.findAllByPolicyIdInOrderByIdAsc(policyIds);
        Map<Long, List<SourceType>> result = new HashMap<>();
        for (PolicySource source : all) {
            List<SourceType> types =
                    result.computeIfAbsent(source.getPolicy().getId(), k -> new ArrayList<>());
            if (!types.contains(source.getSourceType())) {
                types.add(source.getSourceType());
            }
        }
        return result;
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySourceRepositoryImplTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java
git commit -m "feat(policy): 정책별 출처 타입 목록 일괄 조회 추가"
```

---

## Task 2: 출처 타입 SQL 필터 (findForAdminProcessing)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java:114-121`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java:102-112`
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepositoryTest.java` (있으면 추가, 없으면 생성)

출처 필터는 `sourceType` 이 `null` 이면 전체, 값이 있으면 해당 출처를 가진 정책만 반환한다. enum 파라미터는 `String` 과 달리 Hibernate 가 타입을 명확히 추론하므로 `:sourceType IS NULL` sentinel 을 그대로 쓸 수 있다(BYTEA 추론 이슈는 String 한정).

- [ ] **Step 1: 도메인 인터페이스 시그니처 변경**

`PolicyRepository.java` 의 `findForAdminProcessing` 시그니처에 `SourceType sourceType` 추가 (import `com.youthfit.policy.domain.model.SourceType` 추가):

```java
    Page<Policy> findForAdminProcessing(String query, String region, SourceType sourceType,
                                        Sort sort, Pageable pageable);
```

- [ ] **Step 2: 실패하는 JPQL 슬라이스 테스트 작성**

`PolicyJpaRepositoryTest.java` (`@DataJpaTest`). policyA(YOUTH_SEOUL_CRAWL), policyB(BOKJIRO_CENTRAL) 저장 후:

```java
@Test
void 출처타입_필터가_있으면_해당_출처_정책만_반환한다() {
    Page<Policy> page = jpaRepository.findForAdminProcessing(
            "", "", SourceType.BOKJIRO_CENTRAL, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(Policy::getId).containsExactly(policyBId);
}

@Test
void 출처타입_필터가_null이면_전체를_반환한다() {
    Page<Policy> page = jpaRepository.findForAdminProcessing(
            "", "", null, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(Policy::getId)
            .contains(policyAId, policyBId);
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicyJpaRepositoryTest"`
Expected: FAIL — 파라미터 개수 불일치(컴파일 에러)

- [ ] **Step 4: JpaRepository JPQL 수정**

`PolicyJpaRepository.java:114-121` 의 메서드를 교체:

```java
    @Query("""
        SELECT p FROM Policy p
        WHERE (:query = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:region = '' OR p.regionCode = :region)
          AND (:sourceType IS NULL OR EXISTS (
                SELECT 1 FROM PolicySource s
                WHERE s.policy = p AND s.sourceType = :sourceType
          ))
        """)
    Page<Policy> findForAdminProcessing(@Param("query") String query,
                                        @Param("region") String region,
                                        @Param("sourceType") SourceType sourceType,
                                        Pageable pageable);
```

`import com.youthfit.policy.domain.model.SourceType;` 추가.

- [ ] **Step 5: RepositoryImpl 위임 수정**

`PolicyRepositoryImpl.java:102-112` 의 `findForAdminProcessing` 을 교체 (`import com.youthfit.policy.domain.model.SourceType;` 는 이미 존재):

```java
    @Override
    public Page<Policy> findForAdminProcessing(String query, String region, SourceType sourceType,
                                               Sort sort, Pageable pageable) {
        Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                sort == null ? Sort.unsorted() : sort);
        String queryParam = orEmpty(normalizeKeyword(query));
        String regionParam = orEmpty(normalizeRegion(region));
        return jpaRepository.findForAdminProcessing(queryParam, regionParam, sourceType, effective);
    }
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicyJpaRepositoryTest"`
Expected: PASS

> 이 시점에 `AdminPolicyProcessingService` 의 `findForAdminProcessing` 호출부가 컴파일 에러가 난다. Task 4 에서 인자를 채운다. 단독 커밋 대신 Task 4 까지 진행 후 커밋해도 되지만, 빠른 검증을 위해 Step 7 에서 호출부를 임시로 `null` 인자로 맞춘 뒤 Task 4 에서 실제 값으로 바꾼다.

- [ ] **Step 7: 호출부 임시 컴파일 픽스 + 커밋**

`AdminPolicyProcessingService.java:100-105` 의 호출에 `null` 인자를 임시로 끼워 컴파일만 통과시킨다(Task 4 에서 `command.sourceType()` 으로 교체):

```java
        Page<Policy> policyPage = policyRepository.findForAdminProcessing(
                command.query(),
                command.region(),
                null, // TODO(Task 4): command.sourceType()
                toSpringSort(command.sort()),
                PageRequest.of(command.page(), command.size())
        );
```

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java \
        backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepositoryTest.java
git commit -m "feat(policy): 어드민 처리현황 조회에 출처 타입 SQL 필터 추가"
```

---

## Task 3: 출처 DTO 추가 (Result/Response/Command)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/SourceTagResult.java`
- Modify: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingItemResult.java`
- Modify: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingListCommand.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/SourceTagResponse.java`
- Modify: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingItemResponse.java`

- [ ] **Step 1: SourceTagResult 생성**

```java
package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.SourceType;

/**
 * 정책 출처 태그 (application Result).
 * code 는 {@link SourceType#name()}, label 은 한글 표시명.
 */
public record SourceTagResult(String code, String label) {
    public static SourceTagResult from(SourceType sourceType) {
        return new SourceTagResult(sourceType.name(), sourceType.getLabel());
    }
}
```

- [ ] **Step 2: PolicyProcessingItemResult 에 sources 추가**

`PolicyProcessingItemResult.java` 에 `import java.util.List;` 추가 후 record 컴포넌트에 `List<SourceTagResult> sources` 를 `references` 와 `updatedAt` 사이에 추가:

```java
public record PolicyProcessingItemResult(
    Long policyId,
    String title,
    String region,
    PolicyProcessingCompleteness completeness,
    Map<ProcessingStep, ProcessingStatus> stepStatuses,
    AttachmentSummaryResult attachments,
    ReferenceSummaryResult references,
    List<SourceTagResult> sources,
    LocalDateTime updatedAt
) {}
```

- [ ] **Step 3: PolicyProcessingListCommand 에 sourceType 추가**

`PolicyProcessingListCommand.java` 에 `import com.youthfit.policy.domain.model.SourceType;` 추가 후 `sourceType` 컴포넌트 추가(null 허용 — 기본값 보정 없음):

```java
public record PolicyProcessingListCommand(
    String query,
    String region,
    SourceType sourceType,
    PolicyProcessingFilter filter,
    PolicyProcessingSort sort,
    int page,
    int size
) {
    public PolicyProcessingListCommand {
        if (filter == null) filter = PolicyProcessingFilter.ALL;
        if (sort == null) sort = PolicyProcessingSort.UPDATED_DESC;
        if (size <= 0 || size > 200) size = 50;
        if (page < 0) page = 0;
    }
}
```

- [ ] **Step 4: SourceTagResponse 생성**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.SourceTagResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정책 출처 태그")
public record SourceTagResponse(
        @Schema(description = "출처 코드", example = "YOUTH_SEOUL_CRAWL") String code,
        @Schema(description = "출처 한글 표시명", example = "청년몽땅정보통") String label
) {
    public static SourceTagResponse from(SourceTagResult r) {
        return new SourceTagResponse(r.code(), r.label());
    }
}
```

- [ ] **Step 5: PolicyProcessingItemResponse 에 sources 추가**

`PolicyProcessingItemResponse.java` 에 `import java.util.List;` 추가. record 컴포넌트에 `sources` 추가 + `from` 매핑 갱신:

```java
        @Schema(description = "첨부 집계") AttachmentSummaryResponse attachments,
        @Schema(description = "참조 사이트 집계") ReferenceSummaryResponse references,
        @Schema(description = "정책 출처 태그 목록 (여러 소스가 묶이면 전부)")
        List<SourceTagResponse> sources,
        @Schema(description = "처리 단계 최근 갱신 시각") LocalDateTime updatedAt
```

`from` 메서드의 생성자 호출에 `r.sources().stream().map(SourceTagResponse::from).toList()` 를 `ReferenceSummaryResponse.from(...)` 와 `r.updatedAt()` 사이에 추가:

```java
        return new PolicyProcessingItemResponse(
                r.policyId(),
                r.title(),
                r.region(),
                r.completeness(),
                stepStatuses,
                AttachmentSummaryResponse.from(r.attachments()),
                ReferenceSummaryResponse.from(r.references()),
                r.sources().stream().map(SourceTagResponse::from).toList(),
                r.updatedAt()
        );
```

- [ ] **Step 6: 컴파일 확인**

`AdminPolicyProcessingService` 가 `PolicyProcessingItemResult` 를 생성하는 부분(현재 `sources` 인자 없음)에서 컴파일 에러가 난다. 이는 Task 4 에서 채운다. 지금은 DTO 만 검증:

Run: `cd backend && ./gradlew compileJava 2>&1 | head -30`
Expected: `PolicyProcessingItemResult` 생성자 인자 불일치 에러가 `AdminPolicyProcessingService.java` 에서 발생 (DTO 자체는 정상). Task 4 에서 해소.

> 이 Task 는 단독 커밋하지 않고 Task 4 와 함께 커밋한다(컴파일이 Task 4 완료 시 통과).

---

## Task 4: 서비스에서 sourceMap 조립 + 필터 전달

**Files:**
- Modify: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java:99-147`
- Test: `backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java` (있으면 추가, 없으면 생성)

- [ ] **Step 1: 필드 주입 추가**

`AdminPolicyProcessingService.java` 의 의존성에 `PolicySourceRepository` 추가:
- import: `import com.youthfit.policy.domain.repository.PolicySourceRepository;`, `import com.youthfit.policy.domain.model.SourceType;`, `import com.youthfit.admin.application.dto.SourceTagResult;`
- 필드(생성자 주입, `@RequiredArgsConstructor` 이므로 final 필드만 추가): `private final PolicySourceRepository policySourceRepository;`

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`AdminPolicyProcessingServiceTest.java` (Mockito 기반 단위 테스트, 기존 테스트가 있으면 그 스타일을 따른다). `policySourceRepository.findSourceTypesByPolicyIds(...)` 가 `{1L: [YOUTH_SEOUL_CRAWL, BOKJIRO_CENTRAL]}` 을 반환하도록 스텁하고, 결과 item 의 `sources` 검증:

```java
@Test
void 처리현황_목록의_각_항목에_출처_태그를_채운다() {
    // given: policyRepository.findForAdminProcessing(...) 가 policyId=1 단일 Policy 페이지 반환하도록 스텁
    // (기존 다른 batch 리포지토리 스텁은 빈 맵으로 둔다)
    given(policySourceRepository.findSourceTypesByPolicyIds(List.of(1L)))
            .willReturn(Map.of(1L, List.of(SourceType.YOUTH_SEOUL_CRAWL, SourceType.BOKJIRO_CENTRAL)));

    PolicyProcessingListResult result = service.findProcessingPolicies(
            new PolicyProcessingListCommand(null, null, null, null, null, 0, 50));

    assertThat(result.items().get(0).sources())
            .extracting(SourceTagResult::code)
            .containsExactly("YOUTH_SEOUL_CRAWL", "BOKJIRO_CENTRAL");
    assertThat(result.items().get(0).sources().get(0).label()).isEqualTo("청년몽땅정보통");
}

@Test
void 출처가_없는_정책은_빈_출처목록을_가진다() {
    given(policySourceRepository.findSourceTypesByPolicyIds(List.of(1L)))
            .willReturn(Map.of());

    PolicyProcessingListResult result = service.findProcessingPolicies(
            new PolicyProcessingListCommand(null, null, null, null, null, 0, 50));

    assertThat(result.items().get(0).sources()).isEmpty();
}
```

> 기존 서비스 테스트가 없다면 `@ExtendWith(MockitoExtension.class)` + `@Mock` 으로 모든 리포지토리/서비스 의존성을 모킹하고 `@InjectMocks AdminPolicyProcessingService service` 로 구성한다. `policyRepository.findForAdminProcessing(any(), any(), any(), any(), any())` 가 단일 Policy(`Policy.builder()...`, id 는 리플렉션/빌더 따라 세팅) 를 담은 `PageImpl` 을 반환하도록 스텁한다. 나머지 batch 리포지토리(step/attach/embed)는 `Map.of()` 를 반환하도록 둔다.

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.admin.application.service.AdminPolicyProcessingServiceTest"`
Expected: FAIL — `sources()` 미존재/미채움 (컴파일 에러 또는 NPE)

- [ ] **Step 4: findProcessingPolicies 구현 수정**

`AdminPolicyProcessingService.java:99-147` 을 수정한다.

(a) 호출부의 임시 `null` 을 실제 값으로 교체:

```java
        Page<Policy> policyPage = policyRepository.findForAdminProcessing(
                command.query(),
                command.region(),
                command.sourceType(),
                toSpringSort(command.sort()),
                PageRequest.of(command.page(), command.size())
        );
```

(b) batch 조회 블록(stepMap/attachMap/embedMap 옆)에 sourceMap 추가:

```java
        Map<Long, List<SourceType>> sourceMap =
                policySourceRepository.findSourceTypesByPolicyIds(policyIds);
```

(c) item 생성 람다에서 sources 를 만들어 생성자에 추가 (`references` 와 `updatedAt` 사이):

```java
            List<SourceTagResult> sources =
                    sourceMap.getOrDefault(p.getId(), List.of()).stream()
                            .map(SourceTagResult::from)
                            .toList();

            return new PolicyProcessingItemResult(
                    p.getId(),
                    p.getTitle(),
                    p.getRegionCode(),
                    computeCompleteness(stepStatuses, attachCounts, embeddedCount),
                    stepStatuses,
                    new AttachmentSummaryResult(attachCounts.total(), attachCounts.extracted(), embeddedCount),
                    ReferenceSummaryResult.placeholder(),
                    sources,
                    p.getUpdatedAt()
            );
```

> `findProcessingStats()` 는 출처와 무관하므로 변경하지 않는다. `findProcessingDetail()` 은 이번 범위(비목표)에서 제외하므로 변경하지 않는다.

- [ ] **Step 5: 테스트 통과 + 전체 빌드 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.admin.application.service.AdminPolicyProcessingServiceTest"`
Expected: PASS

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋 (Task 3 + Task 4)**

```bash
git add backend/src/main/java/com/youthfit/admin/application/dto/SourceTagResult.java \
        backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingItemResult.java \
        backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingListCommand.java \
        backend/src/main/java/com/youthfit/admin/presentation/dto/response/SourceTagResponse.java \
        backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingItemResponse.java \
        backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java \
        backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat(admin): 정책 처리현황 응답에 출처 태그 추가"
```

---

## Task 5: 컨트롤러·Api 에 sourceType 파라미터

**Files:**
- Modify: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java`
- Modify: `backend/src/main/java/com/youthfit/admin/presentation/api/AdminPolicyProcessingApi.java`

- [ ] **Step 1: Controller 에 sourceType 파라미터 + parse 추가**

`AdminPolicyProcessingController.java`:
- import 추가: `import com.youthfit.policy.domain.model.SourceType;`
- `getPolicies` 시그니처에 파라미터 추가 + Command 생성에 전달:

```java
    @Override
    @GetMapping
    public ResponseEntity<PolicyProcessingListResponse> getPolicies(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "UPDATED_DESC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PolicyProcessingListCommand command = new PolicyProcessingListCommand(
                q,
                region,
                parseSourceType(sourceType),
                parseFilter(filter),
                parseSort(sort),
                page,
                size
        );
        return ResponseEntity.ok(PolicyProcessingListResponse.from(service.findProcessingPolicies(command)));
    }
```

- parse 헬퍼 추가 (parseFilter/parseSort 와 동일 패턴, null/빈 문자열은 "필터 없음" 으로 처리):

```java
    /**
     * {@code sourceType} 쿼리 파라미터를 {@link SourceType} 으로 변환.
     *
     * <p>null/빈 문자열은 "출처 필터 없음"(null) 으로 처리한다.
     * 잘못된 값이면 {@link ErrorCode#INVALID_INPUT} 으로 400 응답을 던진다.</p>
     */
    private static SourceType parseSourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SourceType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 sourceType: " + raw);
        }
    }
```

- [ ] **Step 2: Api 인터페이스에 @Parameter 추가**

`AdminPolicyProcessingApi.java` 의 `getPolicies` 시그니처에 파라미터 추가 (region 과 filter 사이):

```java
            @Parameter(description = "지역 코드 필터")
            @RequestParam(required = false) String region,
            @Parameter(description = "출처 타입 필터 (YOUTH_SEOUL_CRAWL/BOKJIRO_CENTRAL/YOUTH_CENTER)")
            @RequestParam(required = false) String sourceType,
            @Parameter(description = "처리 상태 필터")
            @RequestParam(defaultValue = "ALL") String filter,
```

- [ ] **Step 3: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java \
        backend/src/main/java/com/youthfit/admin/presentation/api/AdminPolicyProcessingApi.java
git commit -m "feat(admin): 정책 처리현황 목록에 sourceType 필터 파라미터 추가"
```

---

## Task 6: 프론트 타입 + API 파라미터

**Files:**
- Modify: `frontend/src/types/adminPolicyProcessing.ts`
- Modify: `frontend/src/apis/adminPolicyProcessing.api.ts:49-58`

- [ ] **Step 1: 타입 추가**

`adminPolicyProcessing.ts`:
- `SourceTag` 인터페이스 추가 (AttachmentSummary 근처):

```typescript
export interface SourceTag {
  /** 백엔드 SourceType#name() — 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER' */
  code: string;
  /** 한글 표시명 */
  label: string;
}
```

- `PolicyProcessingItem` 에 `sources` 추가 (references 와 updatedAt 사이):

```typescript
export interface PolicyProcessingItem {
  policyId: number;
  title: string;
  region: string;
  completeness: Completeness;
  stepStatuses: Partial<Record<ProcessingStep, ProcessingStatus>>;
  attachments: AttachmentSummary;
  references: ReferenceSummary;
  sources: SourceTag[];
  updatedAt: string;
}
```

- `PolicyProcessingListParams` 에 `sourceType` 추가:

```typescript
export interface PolicyProcessingListParams {
  q?: string;
  region?: string;
  sourceType?: string;
  filter?: Filter;
  sort?: Sort;
  page?: number;
  size?: number;
}
```

- [ ] **Step 2: cleanParams 에 sourceType 반영**

`adminPolicyProcessing.api.ts:49-58` 의 `cleanParams` 에 추가 (`region` 다음):

```typescript
  if (params.q) out.q = params.q;
  if (params.region) out.region = params.region;
  if (params.sourceType) out.sourceType = params.sourceType;
  if (params.filter) out.filter = params.filter;
```

- [ ] **Step 3: 타입체크 확인**

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음 (sources 는 다음 Task 에서 사용)

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/types/adminPolicyProcessing.ts frontend/src/apis/adminPolicyProcessing.api.ts
git commit -m "feat(admin): 정책 처리현황 프론트 타입에 출처 태그·필터 추가"
```

---

## Task 7: SourceBadges 컴포넌트 (테두리 색 매핑)

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/SourceBadges.tsx`
- Create: `frontend/src/pages/admin/policy-processing/__tests__/SourceBadges.test.tsx`

기존 `CompletenessBadge` 가 `Record<…, string>` 스타일 매핑을 쓰는 패턴을 따른다. 단, 출처 뱃지는 **테두리 + 텍스트 색**(배경 투명) 으로 출처를 구분한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`__tests__/SourceBadges.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SourceBadges } from '../SourceBadges';

describe('SourceBadges', () => {
  it('출처가 여러 개면 전부 한글 라벨로 렌더한다', () => {
    render(
      <SourceBadges
        sources={[
          { code: 'YOUTH_SEOUL_CRAWL', label: '청년몽땅정보통' },
          { code: 'BOKJIRO_CENTRAL', label: '복지로' },
        ]}
      />,
    );
    expect(screen.getByText('청년몽땅정보통')).toBeInTheDocument();
    expect(screen.getByText('복지로')).toBeInTheDocument();
  });

  it('출처가 없으면 출처없음 뱃지를 렌더한다', () => {
    render(<SourceBadges sources={[]} />);
    expect(screen.getByText('출처없음')).toBeInTheDocument();
  });

  it('알 수 없는 코드는 라벨을 그대로 중립 색으로 렌더한다', () => {
    render(<SourceBadges sources={[{ code: 'UNKNOWN', label: '기타' }]} />);
    expect(screen.getByText('기타')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd frontend && npm run test -- SourceBadges`
Expected: FAIL — `SourceBadges` 모듈 없음

- [ ] **Step 3: 컴포넌트 구현**

`SourceBadges.tsx`:

```typescript
import type { SourceTag } from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

/** 출처 code → 테두리/텍스트 색. 알 수 없는 코드는 중립 색으로 폴백. */
const SOURCE_STYLE: Record<string, string> = {
  YOUTH_SEOUL_CRAWL: 'border-blue-300 text-blue-700',
  BOKJIRO_CENTRAL: 'border-green-300 text-green-700',
  YOUTH_CENTER: 'border-purple-300 text-purple-700',
};

const FALLBACK_STYLE = 'border-neutral-300 text-neutral-600';
const EMPTY_STYLE = 'border-neutral-200 text-neutral-400';

interface Props {
  sources: SourceTag[];
}

/**
 * 정책 출처 태그 뱃지 묶음.
 * 배경은 투명하고 테두리·텍스트 색으로 출처를 구분한다(요구사항: 테두리 색 구분).
 * 출처가 여러 개면 전부 나열하고, 없으면 "출처없음" 중립 뱃지를 보여준다.
 */
export function SourceBadges({ sources }: Props) {
  if (sources.length === 0) {
    return (
      <span className={cn(BADGE_BASE, EMPTY_STYLE)}>출처없음</span>
    );
  }
  return (
    <span className="flex flex-wrap gap-1">
      {sources.map((s) => (
        <span
          key={s.code}
          className={cn(BADGE_BASE, SOURCE_STYLE[s.code] ?? FALLBACK_STYLE)}
          title={s.code}
        >
          {s.label}
        </span>
      ))}
    </span>
  );
}

const BADGE_BASE =
  'inline-block rounded-full border bg-transparent px-2 py-0.5 text-[10px] font-medium leading-tight';
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npm run test -- SourceBadges`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/admin/policy-processing/SourceBadges.tsx \
        frontend/src/pages/admin/policy-processing/__tests__/SourceBadges.test.tsx
git commit -m "feat(admin): 정책 출처 태그 뱃지 컴포넌트 추가"
```

---

## Task 8: 테이블 제목 셀에 출처 뱃지 렌더

**Files:**
- Modify: `frontend/src/pages/admin/policy-processing/PolicyProcessingTable.tsx:9,62`

- [ ] **Step 1: import 추가**

`PolicyProcessingTable.tsx` 상단(`CompletenessBadge` import 옆)에 추가:

```typescript
import { SourceBadges } from './SourceBadges';
```

- [ ] **Step 2: 제목 셀에 뱃지 추가**

`PolicyProcessingTable.tsx:62` 의 제목 `<td>` 를 제목 + 출처 뱃지를 세로로 쌓는 형태로 교체:

```typescript
                  <td className={cn(TD_BASE, 'text-neutral-900')}>
                    <div className="flex flex-col gap-1">
                      <span>{item.title}</span>
                      <SourceBadges sources={item.sources} />
                    </div>
                  </td>
```

- [ ] **Step 3: 빌드 + 기존 테이블 테스트 확인**

Run: `cd frontend && npm run test -- PolicyProcessingTable`
Expected: PASS (기존 테스트가 있으면 통과. `sources` 누락으로 mock 데이터 타입 에러가 나면 해당 테스트 fixture 에 `sources: []` 를 추가한다.)

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/admin/policy-processing/PolicyProcessingTable.tsx
git commit -m "feat(admin): 정책 처리현황 표 제목 셀에 출처 뱃지 표시"
```

---

## Task 9: 출처 필터 select + 페이지 searchParams 연동

**Files:**
- Modify: `frontend/src/pages/admin/policy-processing/PolicyProcessingFilters.tsx`
- Modify: `frontend/src/pages/admin/AdminPolicyProcessingPage.tsx:35-62`

- [ ] **Step 1: Filters 에 출처 select 추가**

`PolicyProcessingFilters.tsx` 의 상단 입력 행(`sort` select 옆, 닫는 `</div>` 앞)에 출처 select 추가:

```typescript
        <select
          className="rounded-md border border-neutral-200 bg-white px-3 py-1.5 text-sm text-neutral-900 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15"
          value={params.sourceType ?? ''}
          onChange={(e) =>
            onChange({ ...params, sourceType: e.target.value || undefined, page: 0 })
          }
        >
          <option value="">전체 출처</option>
          <option value="YOUTH_SEOUL_CRAWL">청년몽땅정보통</option>
          <option value="BOKJIRO_CENTRAL">복지로</option>
          <option value="YOUTH_CENTER">온통청년</option>
        </select>
```

- [ ] **Step 2: 페이지 params 읽기/쓰기에 sourceType 반영**

`AdminPolicyProcessingPage.tsx:35-45` 의 `params` useMemo 에 추가:

```typescript
      q: searchParams.get('q') ?? undefined,
      region: searchParams.get('region') ?? undefined,
      sourceType: searchParams.get('sourceType') ?? undefined,
      filter: (searchParams.get('filter') as Filter | null) ?? 'ALL',
```

`AdminPolicyProcessingPage.tsx:53-62` 의 `onParamsChange` 에 추가 (`region` 다음):

```typescript
    if (next.q) sp.set('q', next.q);
    if (next.region) sp.set('region', next.region);
    if (next.sourceType) sp.set('sourceType', next.sourceType);
    if (next.filter && next.filter !== 'ALL') sp.set('filter', next.filter);
```

- [ ] **Step 3: 빌드 + 타입체크 + 테스트**

Run: `cd frontend && npx tsc --noEmit && npm run test`
Expected: 에러 없음, 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/admin/policy-processing/PolicyProcessingFilters.tsx \
        frontend/src/pages/admin/AdminPolicyProcessingPage.tsx
git commit -m "feat(admin): 정책 처리현황 출처 필터 select 추가"
```

---

## 최종 검증

- [ ] **백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **프론트 전체 테스트 + 빌드**

Run: `cd frontend && npm run test && npm run build`
Expected: 모든 테스트 PASS, 빌드 성공

- [ ] **수동 확인 (선택)**

`./gradlew bootRun` + `npm run dev` 후 `/admin` 정책 처리 현황에서:
- 각 정책 제목 아래 출처 뱃지가 테두리 색으로 구분되어 표시되는지
- 출처 select 에서 특정 출처 선택 시 해당 출처 정책만 목록에 남는지(URL 에 `sourceType` 보존)
- 출처 없는 정책에 "출처없음" 뱃지가 보이는지

---

## Self-Review 결과

- **Spec coverage:** 출처 표시(Task 7,8) / 한글 라벨(SourceTagResponse.label, SourceBadges) / 테두리 색(SourceBadges) / 다중 출처 전부 표시(findSourceTypesByPolicyIds + SourceBadges map) / 출처 필터(Task 2,5,9) / N+1 회피(batch findSourceTypesByPolicyIds) — 모두 매핑됨.
- **타입 일관성:** `SourceTagResult{code,label}` ↔ `SourceTagResponse{code,label}` ↔ 프론트 `SourceTag{code,label}` 일치. `findSourceTypesByPolicyIds` 이름 전 Task 통일. `findForAdminProcessing` 새 시그니처(`String, String, SourceType, Sort, Pageable`) 가 Task 2·4 에서 동일.
- **Placeholder:** 없음. 모든 코드 스텝에 실제 코드 포함.
- **주의:** Task 2 Step 7 의 임시 `null` 은 Task 4 Step 4(a) 에서 `command.sourceType()` 으로 반드시 교체된다.
