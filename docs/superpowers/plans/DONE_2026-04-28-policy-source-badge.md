# 정책 출처 뱃지 노출 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 상세/목록 응답에 `sourceType`/`sourceLabel`을 노출하고, 프론트에 출처별 로고 이미지 뱃지를 추가한다.

**Architecture:**
- 백엔드: 기존 `SourceType` enum 에 한글 라벨 추가, `PolicySourceRepository`에 일괄 조회 메서드(`findFirstByPolicyIds`) 추가, `PolicyQueryService`가 `PolicySource` 객체를 통째로 Result에 전달.
- 프론트: 신규 `SourceBadge` 컴포넌트가 `Record<SourceType, string>` 매핑으로 mock SVG 로고를 렌더. 카드/상세 헤더에 부착.

**Tech Stack:** Java 21 / Spring Boot 4 / JPA / JUnit 5 / Mockito / React 19 / TypeScript / Tailwind / Vitest

**Spec:** `docs/superpowers/specs/DONE_2026-04-28-policy-source-badge-design.md`

---

## File Structure

### 백엔드 (수정/생성)
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/SourceType.java`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicySummaryResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicySummaryResponse.java`
- Create: `backend/src/test/java/com/youthfit/policy/domain/model/SourceTypeTest.java`
- Create: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyDetailResultTest.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/dto/result/PolicySummaryResultTest.java`

### 프론트엔드 (수정/생성)
- Modify: `frontend/src/types/policy.ts`
- Create: `frontend/src/assets/source-logos/bokjiro.svg`
- Create: `frontend/src/assets/source-logos/youth-center.svg`
- Create: `frontend/src/assets/source-logos/youth-seoul.svg`
- Create: `frontend/src/components/policy/SourceBadge.tsx`
- Create: `frontend/src/components/policy/SourceBadge.test.tsx`
- Modify: `frontend/src/components/policy/PolicyCard.tsx`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

---

## Task 1: `SourceType` enum 한글 라벨 추가

**Files:**
- Create: `backend/src/test/java/com/youthfit/policy/domain/model/SourceTypeTest.java`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/SourceType.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/youthfit/policy/domain/model/SourceTypeTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceType")
class SourceTypeTest {

    @ParameterizedTest(name = "{0} → 라벨 {1}")
    @CsvSource({
            "BOKJIRO_CENTRAL,복지로",
            "YOUTH_CENTER,온통청년",
            "YOUTH_SEOUL_CRAWL,청년 서울"
    })
    @DisplayName("getLabel 은 한글 라벨을 반환한다")
    void getLabel_returnsKoreanLabel(SourceType type, String expectedLabel) {
        assertThat(type.getLabel()).isEqualTo(expectedLabel);
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.SourceTypeTest"`
Expected: FAIL — compile error "cannot find symbol: method getLabel()" 또는 "no suitable constructor"

- [ ] **Step 3: Implement `SourceType` 라벨**

Replace contents of `backend/src/main/java/com/youthfit/policy/domain/model/SourceType.java`:

```java
package com.youthfit.policy.domain.model;

public enum SourceType {
    YOUTH_SEOUL_CRAWL("청년 서울"),
    BOKJIRO_CENTRAL("복지로"),
    YOUTH_CENTER("온통청년");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.SourceTypeTest"`
Expected: PASS — 3 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/SourceType.java \
        backend/src/test/java/com/youthfit/policy/domain/model/SourceTypeTest.java
git commit -m "feat(policy): SourceType enum 한글 라벨 추가"
```

---

## Task 2: `PolicySourceRepository.findFirstByPolicyIds` 일괄 조회 메서드

**Files:**
- Create: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java`:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PolicySourceRepositoryImpl.class})
@DisplayName("PolicySourceRepositoryImpl.findFirstByPolicyIds")
class PolicySourceRepositoryImplTest {

    @Autowired
    private PolicySourceRepository policySourceRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    @DisplayName("정책 N개 각각 source 1개 → Map 사이즈 N")
    void multiplePolicies_eachWithOneSource_returnsAllMapped() {
        Policy p1 = saveMinimalPolicy("정책A");
        Policy p2 = saveMinimalPolicy("정책B");
        saveSource(p1, SourceType.BOKJIRO_CENTRAL, "ext-1");
        saveSource(p2, SourceType.YOUTH_CENTER, "ext-2");

        Map<Long, PolicySource> result = policySourceRepository.findFirstByPolicyIds(
                List.of(p1.getId(), p2.getId()));

        assertThat(result).hasSize(2);
        assertThat(result.get(p1.getId()).getSourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
        assertThat(result.get(p2.getId()).getSourceType()).isEqualTo(SourceType.YOUTH_CENTER);
    }

    @Test
    @DisplayName("한 정책에 source 2개 → Map 에는 첫 번째(id 오름차순)만")
    void singlePolicyWithTwoSources_returnsFirstOnly() {
        Policy p1 = saveMinimalPolicy("정책A");
        PolicySource first = saveSource(p1, SourceType.BOKJIRO_CENTRAL, "ext-1");
        saveSource(p1, SourceType.YOUTH_CENTER, "ext-2");

        Map<Long, PolicySource> result = policySourceRepository.findFirstByPolicyIds(
                List.of(p1.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(p1.getId()).getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("source 없는 정책 ID는 Map 에서 누락")
    void policyWithoutSource_isMissingFromMap() {
        Policy p1 = saveMinimalPolicy("정책A");
        Policy p2 = saveMinimalPolicy("정책B");
        saveSource(p1, SourceType.BOKJIRO_CENTRAL, "ext-1");

        Map<Long, PolicySource> result = policySourceRepository.findFirstByPolicyIds(
                List.of(p1.getId(), p2.getId()));

        assertThat(result).hasSize(1);
        assertThat(result).containsKey(p1.getId());
        assertThat(result).doesNotContainKey(p2.getId());
    }

    @Test
    @DisplayName("빈 입력 리스트 → 빈 Map (DB 호출 회피)")
    void emptyInput_returnsEmptyMap() {
        Map<Long, PolicySource> result = policySourceRepository.findFirstByPolicyIds(List.of());

        assertThat(result).isEmpty();
    }

    private Policy saveMinimalPolicy(String title) {
        return policyRepository.save(Policy.builder()
                .title(title)
                .summary("요약")
                .category(Category.HOUSING)
                .regionCode("11")
                .build());
    }

    private PolicySource saveSource(Policy policy, SourceType type, String externalId) {
        return policySourceRepository.save(PolicySource.builder()
                .policy(policy)
                .sourceType(type)
                .externalId(externalId)
                .sourceUrl("https://example.com/" + externalId)
                .rawJson("{}")
                .sourceHash("hash-" + externalId)
                .build());
    }
}
```

> NOTE: 프로젝트가 이미 Testcontainers + PostgreSQL 통합 테스트 패턴을 쓰는지 확인 필요. `PolicySpecificationTest` 가 이미 있으므로 동일 컨벤션(테스트 베이스 클래스/`@DataJpaTest` 설정)을 따르도록 첫 step에서 점검하고, 필요 시 `extends` 추가 또는 `@Import` 조정.

- [ ] **Step 2: Run test to verify it fails (compile error)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySourceRepositoryImplTest"`
Expected: FAIL — compile error "cannot find symbol: method findFirstByPolicyIds"

- [ ] **Step 3: Add domain method signature**

Modify `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java` — add new method signature and import:

```java
package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PolicySourceRepository {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyId(Long policyId);

    Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds);

    PolicySource save(PolicySource policySource);
}
```

- [ ] **Step 4: Add JPA query method**

Modify `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java`:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicySourceJpaRepository extends JpaRepository<PolicySource, Long> {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyIdOrderByIdAsc(Long policyId);

    List<PolicySource> findAllByPolicyIdInOrderByIdAsc(List<Long> policyIds);
}
```

- [ ] **Step 5: Implement the impl**

Modify `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java`:

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PolicySourceRepositoryImpl implements PolicySourceRepository {

    private final PolicySourceJpaRepository jpaRepository;

    public PolicySourceRepositoryImpl(PolicySourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId) {
        return jpaRepository.findBySourceTypeAndExternalId(sourceType, externalId);
    }

    @Override
    public Optional<PolicySource> findFirstByPolicyId(Long policyId) {
        return jpaRepository.findFirstByPolicyIdOrderByIdAsc(policyId);
    }

    @Override
    public Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        List<PolicySource> all = jpaRepository.findAllByPolicyIdInOrderByIdAsc(policyIds);
        Map<Long, PolicySource> result = new HashMap<>();
        for (PolicySource source : all) {
            result.putIfAbsent(source.getPolicy().getId(), source);
        }
        return result;
    }

    @Override
    public PolicySource save(PolicySource policySource) {
        return jpaRepository.save(policySource);
    }
}
```

`putIfAbsent` 가 첫 번째 매칭만 유지함 (JPA가 `id ASC` 로 정렬해 반환했으므로 가장 작은 id가 첫 번째).

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.persistence.PolicySourceRepositoryImplTest"`
Expected: PASS — 4 tests passed

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImplTest.java
git commit -m "feat(policy): PolicySourceRepository.findFirstByPolicyIds 일괄 조회 추가"
```

---

## Task 3: Result/Response DTO + `PolicyQueryService` 변경

이 task 는 시그니처 변경이 연쇄되므로 한 묶음에서 마무리한다 (중간에 빌드 깨짐).

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicySummaryResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicySummaryResponse.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyDetailResultTest.java`
- Modify: `backend/src/test/java/com/youthfit/policy/application/dto/result/PolicySummaryResultTest.java`

- [ ] **Step 1: Update `PolicyDetailResult` 시그니처**

Replace fields and `from(...)` 시그니처. Modify `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java`:

```java
package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PolicyDetailResult(
        Long id,
        String title,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String organization,
        String contact,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        String supportCycle,
        String provideType,
        PolicyStatus status,
        DetailLevel detailLevel,
        Set<String> lifeTags,
        Set<String> themeTags,
        Set<String> targetTags,
        List<Attachment> attachments,
        List<ReferenceSite> referenceSites,
        List<ApplyMethod> applyMethods,
        SourceType sourceType,
        String sourceLabel,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Attachment(String name, String url, String mediaType) {
        public static Attachment from(PolicyAttachment attachment) {
            return new Attachment(attachment.getName(), attachment.getUrl(), attachment.getMediaType());
        }
    }

    public record ReferenceSite(String name, String url) {
        public static ReferenceSite from(PolicyReferenceSite site) {
            return new ReferenceSite(site.name(), site.url());
        }
    }

    public record ApplyMethod(String stageName, String description) {
        public static ApplyMethod from(PolicyApplyMethod method) {
            return new ApplyMethod(method.stageName(), method.description());
        }
    }

    public static PolicyDetailResult from(Policy policy, PolicySource source) {
        SourceType sourceType = source != null ? source.getSourceType() : null;
        String sourceLabel = sourceType != null ? sourceType.getLabel() : null;
        String sourceUrl = source != null ? source.getSourceUrl() : null;
        return new PolicyDetailResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getBody(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getOrganization(),
                policy.getContact(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getReferenceYear(),
                policy.getSupportCycle(),
                policy.getProvideType(),
                policy.getStatus(),
                policy.getDetailLevel(),
                Set.copyOf(policy.getLifeTags()),
                Set.copyOf(policy.getThemeTags()),
                Set.copyOf(policy.getTargetTags()),
                policy.getAttachments().stream().map(Attachment::from).toList(),
                policy.getReferenceSites().stream().map(ReferenceSite::from).toList(),
                policy.getApplyMethods().stream().map(ApplyMethod::from).toList(),
                sourceType,
                sourceLabel,
                sourceUrl,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: Update `PolicySummaryResult` 시그니처**

Modify `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicySummaryResult.java`:

```java
package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.*;

import java.time.LocalDate;

public record PolicySummaryResult(
        Long id,
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        PolicyStatus status,
        DetailLevel detailLevel,
        String organization,
        SourceType sourceType,
        String sourceLabel
) {
    public static PolicySummaryResult from(Policy policy, PolicySource source) {
        SourceType sourceType = source != null ? source.getSourceType() : null;
        String sourceLabel = sourceType != null ? sourceType.getLabel() : null;
        return new PolicySummaryResult(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getCategory(),
                policy.getRegionCode(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getReferenceYear(),
                policy.getStatus(),
                policy.getDetailLevel(),
                policy.getOrganization(),
                sourceType,
                sourceLabel
        );
    }
}
```

- [ ] **Step 3: Update `PolicyQueryService`**

Modify `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`:

```java
package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyPageResult findPoliciesByFilters(String regionCode, Category category,
                                                  PolicyStatus status,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.findAllByFilters(regionCode, category, status, pageable);
        return toPageResult(policyPage);
    }

    public PolicyDetailResult findPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다: " + policyId));
        PolicySource source = policySourceRepository.findFirstByPolicyId(policyId).orElse(null);
        return PolicyDetailResult.from(policy, source);
    }

    public PolicyPageResult searchPoliciesByKeyword(String keyword, PolicyStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.searchByKeyword(keyword, status, pageable);
        return toPageResult(policyPage);
    }

    private PolicyPageResult toPageResult(Page<Policy> policyPage) {
        List<Long> ids = policyPage.getContent().stream().map(Policy::getId).toList();
        Map<Long, PolicySource> sourceMap = policySourceRepository.findFirstByPolicyIds(ids);
        return new PolicyPageResult(
                policyPage.getContent().stream()
                        .map(p -> PolicySummaryResult.from(p, sourceMap.get(p.getId())))
                        .toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }
}
```

- [ ] **Step 4: Update `PolicyDetailResponse`**

Modify `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java` — add fields + import + `from(...)` 매핑:

`SourceType` import 추가:
```java
import com.youthfit.policy.domain.model.SourceType;
```

Record 시그니처에 두 필드 삽입(기존 `sourceUrl` 바로 앞):
```java
SourceType sourceType,
String sourceLabel,
String sourceUrl,
```

`PolicyDetailResponse.from(...)` 호출부에 두 필드 전달 (`result.sourceUrl()` 호출 바로 앞에 삽입):
```java
result.sourceType(),
result.sourceLabel(),
result.sourceUrl(),
```

- [ ] **Step 5: Update `PolicySummaryResponse`**

Modify `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicySummaryResponse.java`:

```java
package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;

import java.time.LocalDate;

public record PolicySummaryResponse(
        Long id,
        String title,
        String summary,
        Category category,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        Integer referenceYear,
        PolicyStatus status,
        DetailLevel detailLevel,
        String organization,
        SourceType sourceType,
        String sourceLabel
) {
    public static PolicySummaryResponse from(PolicySummaryResult result) {
        return new PolicySummaryResponse(
                result.id(),
                result.title(),
                result.summary(),
                result.category(),
                result.regionCode(),
                result.applyStart(),
                result.applyEnd(),
                result.referenceYear(),
                result.status(),
                result.detailLevel(),
                result.organization(),
                result.sourceType(),
                result.sourceLabel()
        );
    }
}
```

- [ ] **Step 6: Update existing tests for new signatures**

(a) `PolicyDetailResultTest`: `PolicyDetailResult.from(policy, sourceUrl)` 호출은 이제 컴파일 깨짐. `from(policy, source)` 로 교체 필요. 먼저 파일을 열어 호출부 확인:

```bash
grep -n "PolicyDetailResult.from" backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyDetailResultTest.java
```

각 호출에 대해 다음 패턴으로 교체:
- 기존: `PolicyDetailResult.from(policy, "https://example.com/policy/1")`
- 변경: source 객체를 만들어 전달
  ```java
  PolicySource source = PolicySource.builder()
          .policy(policy)
          .sourceType(SourceType.BOKJIRO_CENTRAL)
          .externalId("ext-1")
          .sourceUrl("https://example.com/policy/1")
          .rawJson("{}")
          .sourceHash("hash")
          .build();
  PolicyDetailResult result = PolicyDetailResult.from(policy, source);
  ```
- source 가 null 인 케이스도 테스트되어 있다면 그대로 `PolicyDetailResult.from(policy, null)` 사용.

`sourceUrl`/`sourceType`/`sourceLabel` 단언이 필요하면 다음 케이스 추가 (기존 테스트 끝에):
```java
@Test
@DisplayName("source 가 있을 때 sourceType/sourceLabel/sourceUrl 모두 채워진다")
void sourcePresent_populatesAllSourceFields() {
    Policy policy = createPolicyWithIdAndTitle(1L, "테스트 정책");
    PolicySource source = PolicySource.builder()
            .policy(policy)
            .sourceType(SourceType.BOKJIRO_CENTRAL)
            .externalId("ext-1")
            .sourceUrl("https://example.com/policy/1")
            .rawJson("{}")
            .sourceHash("hash")
            .build();

    PolicyDetailResult result = PolicyDetailResult.from(policy, source);

    assertThat(result.sourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
    assertThat(result.sourceLabel()).isEqualTo("복지로");
    assertThat(result.sourceUrl()).isEqualTo("https://example.com/policy/1");
}

@Test
@DisplayName("source 가 null 일 때 sourceType/sourceLabel/sourceUrl 모두 null")
void sourceNull_allSourceFieldsNull() {
    Policy policy = createPolicyWithIdAndTitle(1L, "테스트 정책");

    PolicyDetailResult result = PolicyDetailResult.from(policy, null);

    assertThat(result.sourceType()).isNull();
    assertThat(result.sourceLabel()).isNull();
    assertThat(result.sourceUrl()).isNull();
}
```

(`createPolicyWithIdAndTitle` 가 없다면 기존 헬퍼 이름에 맞춰 호출). `import com.youthfit.policy.domain.model.PolicySource;`, `import com.youthfit.policy.domain.model.SourceType;` 추가.

(b) `PolicySummaryResultTest`: 동일하게 `PolicySummaryResult.from(policy)` → `PolicySummaryResult.from(policy, source)` 로 교체. 추가 케이스 2개 (source 있/없).

- [ ] **Step 7: Update `PolicyQueryServiceTest`**

Modify `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java`:

기존 `findPolicyById.exists_returnsDetail` 의 `findPoliciesByFilters` mock 흐름은 이제 `findFirstByPolicyIds(List<Long>)` 호출이 추가되므로, 다음 두 곳을 수정/추가:

(1) `FindPolicyById` nested 안에 source 매핑 검증 케이스 추가:
```java
@Test
@DisplayName("source 가 있으면 sourceType/sourceLabel/sourceUrl 이 채워진다")
void sourcePresent_returnsAllSourceFields() {
    Policy policy = createMockPolicy();
    PolicySource source = PolicySource.builder()
            .policy(policy)
            .sourceType(SourceType.BOKJIRO_CENTRAL)
            .externalId("ext-1")
            .sourceUrl("https://example.com/policy/1")
            .rawJson("{}")
            .sourceHash("hash")
            .build();
    given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
    given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.of(source));

    PolicyDetailResult result = policyQueryService.findPolicyById(1L);

    assertThat(result.sourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
    assertThat(result.sourceLabel()).isEqualTo("복지로");
    assertThat(result.sourceUrl()).isEqualTo("https://example.com/policy/1");
}
```
`SourceType` import 추가.

(2) `FindPoliciesByFilters` / `SearchPoliciesByKeyword` 의 기존 케이스에 `findFirstByPolicyIds` mock 추가:
```java
given(policySourceRepository.findFirstByPolicyIds(anyList()))
        .willReturn(Map.of());
```
필요 import:
```java
import java.util.Map;
import static org.mockito.ArgumentMatchers.anyList;
```
빈 `Page` 케이스(`allNull_returnsPage`)는 `policyPage.getContent()` 가 빈 리스트이므로 `findFirstByPolicyIds` 가 빈 리스트로 호출됨 → 동일 mock 으로 OK (lenient 가정), 또는 케이스별로 명시적으로 stub.

새 케이스 1개 추가 — source 가 매핑되는 시나리오:
```java
@Test
@DisplayName("페이지 결과의 각 항목에 source 가 매핑된다")
void filterPage_mapsSourceToEachSummary() {
    Policy policy = createMockPolicy();
    Page<Policy> mockPage = new PageImpl<>(List.of(policy), Pageable.ofSize(20), 1);
    PolicySource source = PolicySource.builder()
            .policy(policy)
            .sourceType(SourceType.YOUTH_CENTER)
            .externalId("ext-y")
            .sourceUrl("https://example.com/y")
            .rawJson("{}")
            .sourceHash("hash")
            .build();
    given(policyRepository.findAllByFilters(any(), any(), any(), any(Pageable.class)))
            .willReturn(mockPage);
    given(policySourceRepository.findFirstByPolicyIds(List.of(1L)))
            .willReturn(Map.of(1L, source));

    PolicyPageResult result = policyQueryService.findPoliciesByFilters(null, null, null, 0, 20);

    assertThat(result.policies()).hasSize(1);
    assertThat(result.policies().getFirst().sourceType()).isEqualTo(SourceType.YOUTH_CENTER);
    assertThat(result.policies().getFirst().sourceLabel()).isEqualTo("온통청년");
}
```

- [ ] **Step 8: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: PASS — all tests green. 새로 추가/수정한 케이스 모두 포함.

빌드 실패 시 컴파일 오류부터 잡고, 그다음 mock 설정 빠진 곳 파악 (보통 `findFirstByPolicyIds` stub 누락 → `null` 반환 → NPE).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java \
        backend/src/main/java/com/youthfit/policy/application/dto/result/PolicySummaryResult.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicySummaryResponse.java \
        backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyDetailResultTest.java \
        backend/src/test/java/com/youthfit/policy/application/dto/result/PolicySummaryResultTest.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceTest.java
git commit -m "feat(policy): 정책 응답에 sourceType/sourceLabel 노출"
```

---

## Task 4: 백엔드 전체 빌드 확인

**Files:** (변경 없음, 검증만)

- [ ] **Step 1: Clean build**

Run: `cd backend && ./gradlew clean build`
Expected: `BUILD SUCCESSFUL`. 모든 테스트 통과.

- [ ] **Step 2: Swagger/OpenAPI 응답 확인 (선택)**

로컬 부팅 후 (`./gradlew bootRun`), `http://localhost:8080/swagger-ui/index.html` 의 정책 상세/목록 응답 스키마에 `sourceType`, `sourceLabel` 필드가 보이는지 확인.

> 빌드만 통과하면 BE 슬라이스는 종료. 다음으로 프론트.

---

## Task 5: 프론트 타입 추가

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: Add `SourceType` type and Policy fields**

Modify `frontend/src/types/policy.ts` — 파일 상단(타입 union 정의 영역) 에 추가:

```ts
export type SourceType = 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER';
```

`Policy` 인터페이스에 두 필드 추가 (마지막 필드 `organization` 다음 줄):

```ts
export interface Policy {
  id: number;
  title: string;
  summary: string;
  category: PolicyCategory;
  regionCode: string;
  applyStart: string | null;
  applyEnd: string | null;
  referenceYear: number | null;
  status: PolicyStatus;
  detailLevel: DetailLevel;
  organization: string | null;
  sourceType: SourceType | null;
  sourceLabel: string | null;
}
```

`PolicyDetail` 은 `Policy`를 extends 하므로 자동 상속됨 — 별도 수정 불필요.

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: `tsc` 에서 새 필드를 채우지 않은 mock 객체가 있으면 에러. 그 위치들을 모두 수정 (보통 테스트 픽스처). 없으면 통과.

만약 mock 데이터에서 에러가 나면 각 위치에 `sourceType: null, sourceLabel: null` 추가.

- [ ] **Step 3: Commit (자산 추가 후 함께 커밋해도 됨, 여기선 잠시 보류)**

> 다음 task 와 함께 한 번에 커밋. 일단 워킹트리에 둔다.

---

## Task 6: Mock SVG 로고 자산 생성

**Files:**
- Create: `frontend/src/assets/source-logos/bokjiro.svg`
- Create: `frontend/src/assets/source-logos/youth-center.svg`
- Create: `frontend/src/assets/source-logos/youth-seoul.svg`

- [ ] **Step 1: Create directory**

Run: `mkdir -p frontend/src/assets/source-logos`

- [ ] **Step 2: Create `bokjiro.svg`**

Create `frontend/src/assets/source-logos/bokjiro.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 24" role="img" aria-label="복지로">
  <rect width="96" height="24" rx="6" fill="#1F6FEB"/>
  <text x="48" y="16" font-family="Pretendard, system-ui, sans-serif" font-size="12" font-weight="700" fill="white" text-anchor="middle">복지로</text>
</svg>
```

- [ ] **Step 3: Create `youth-center.svg`**

Create `frontend/src/assets/source-logos/youth-center.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 24" role="img" aria-label="온통청년">
  <rect width="96" height="24" rx="6" fill="#16A34A"/>
  <text x="48" y="16" font-family="Pretendard, system-ui, sans-serif" font-size="12" font-weight="700" fill="white" text-anchor="middle">온통청년</text>
</svg>
```

- [ ] **Step 4: Create `youth-seoul.svg`**

Create `frontend/src/assets/source-logos/youth-seoul.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 24" role="img" aria-label="청년 서울">
  <rect width="96" height="24" rx="6" fill="#7C3AED"/>
  <text x="48" y="16" font-family="Pretendard, system-ui, sans-serif" font-size="11" font-weight="700" fill="white" text-anchor="middle">청년 서울</text>
</svg>
```

> 색상은 출처 구분 위한 mock — S3 실제 로고 받으면 SVG 파일만 교체.

- [ ] **Step 5: Vite 가 SVG 모듈을 import 가능한지 확인**

Vite 는 기본적으로 `.svg` 를 string URL 로 import 지원. 만약 `vite-env.d.ts` 에 별도 선언이 필요하면(타입스크립트 strict 환경), `frontend/src/vite-env.d.ts` 에 다음 라인이 있는지 확인:

```ts
/// <reference types="vite/client" />
```

이 한 줄이 있으면 `import logo from '*.svg'` 가 string 으로 추론됨. 없으면 추가.

- [ ] **Step 6: Commit (Task 5 의 타입 변경과 함께)**

```bash
git add frontend/src/types/policy.ts frontend/src/assets/source-logos
# vite-env.d.ts 수정한 경우 함께
# git add frontend/src/vite-env.d.ts
git commit -m "feat(fe): SourceType 타입 + 출처 로고 mock 자산 추가"
```

---

## Task 7: `SourceBadge` 컴포넌트 + 테스트

**Files:**
- Create: `frontend/src/components/policy/SourceBadge.tsx`
- Create: `frontend/src/components/policy/SourceBadge.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/policy/SourceBadge.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SourceBadge from './SourceBadge';

describe('SourceBadge', () => {
  it('renders nothing when sourceType is null', () => {
    const { container } = render(
      <SourceBadge sourceType={null} sourceLabel={null} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when sourceLabel is null', () => {
    const { container } = render(
      <SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel={null} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders an img with alt and title for BOKJIRO_CENTRAL', () => {
    render(<SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel="복지로" />);
    const img = screen.getByRole('img', { name: '복지로' });
    expect(img).toBeInTheDocument();
    expect(img.parentElement).toHaveAttribute('title', '출처: 복지로');
  });

  it('uses different image source for each sourceType', () => {
    const { rerender } = render(
      <SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel="복지로" />,
    );
    const bokjiroSrc = screen.getByRole('img').getAttribute('src');

    rerender(<SourceBadge sourceType="YOUTH_CENTER" sourceLabel="온통청년" />);
    const youthCenterSrc = screen.getByRole('img').getAttribute('src');

    rerender(<SourceBadge sourceType="YOUTH_SEOUL_CRAWL" sourceLabel="청년 서울" />);
    const youthSeoulSrc = screen.getByRole('img').getAttribute('src');

    expect(bokjiroSrc).not.toBe(youthCenterSrc);
    expect(bokjiroSrc).not.toBe(youthSeoulSrc);
    expect(youthCenterSrc).not.toBe(youthSeoulSrc);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm run test -- SourceBadge`
Expected: FAIL — `Cannot find module './SourceBadge'`

- [ ] **Step 3: Implement `SourceBadge`**

Create `frontend/src/components/policy/SourceBadge.tsx`:

```tsx
import bokjiroLogo from '@/assets/source-logos/bokjiro.svg';
import youthCenterLogo from '@/assets/source-logos/youth-center.svg';
import youthSeoulLogo from '@/assets/source-logos/youth-seoul.svg';
import { cn } from '@/lib/cn';
import type { SourceType } from '@/types/policy';

const LOGO_MAP: Record<SourceType, string> = {
  BOKJIRO_CENTRAL: bokjiroLogo,
  YOUTH_CENTER: youthCenterLogo,
  YOUTH_SEOUL_CRAWL: youthSeoulLogo,
};

interface Props {
  sourceType: SourceType | null;
  sourceLabel: string | null;
  size?: 'sm' | 'md';
}

export default function SourceBadge({ sourceType, sourceLabel, size = 'sm' }: Props) {
  if (!sourceType || !sourceLabel) return null;
  const heightCls = size === 'sm' ? 'h-5' : 'h-6';
  return (
    <span
      className="inline-flex items-center"
      title={`출처: ${sourceLabel}`}
    >
      <img
        src={LOGO_MAP[sourceType]}
        alt={sourceLabel}
        className={cn(heightCls, 'w-auto')}
      />
    </span>
  );
}
```

> SVG 자체에 `rx`/배경색이 들어 있어서 추가 border/padding 불필요. 카드/상세 컨텍스트에 따라 크기만 sm/md로 분기.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm run test -- SourceBadge`
Expected: PASS — 4 tests passed.

만약 `Cannot find module '@/assets/source-logos/bokjiro.svg'` 에러가 나면 Vite alias 설정 (`@/` → `src/`) 이 vitest config 에도 적용되어 있는지 확인. `vitest.config.ts` 또는 `vite.config.ts` 의 `resolve.alias` 점검.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/policy/SourceBadge.tsx \
        frontend/src/components/policy/SourceBadge.test.tsx
git commit -m "feat(fe): SourceBadge 컴포넌트 + 테스트 추가"
```

---

## Task 8: `PolicyCard` / `PolicyDetailPage` 에 `SourceBadge` 부착

**Files:**
- Modify: `frontend/src/components/policy/PolicyCard.tsx`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: Attach to `PolicyCard`**

Modify `frontend/src/components/policy/PolicyCard.tsx`:

상단 import 에 추가:
```tsx
import SourceBadge from '@/components/policy/SourceBadge';
```

기존 상단 뱃지 줄(`StatusBadge` 다음, `D-N` 뱃지 앞) 에 `SourceBadge` 삽입:

```tsx
{/* 상단: 배지 + 북마크 */}
<div className="mb-3 flex items-center gap-2">
  <CategoryBadge category={policy.category} />
  <StatusBadge status={effectiveStatus} />
  <SourceBadge sourceType={policy.sourceType} sourceLabel={policy.sourceLabel} size="sm" />
  {dDay != null && dDay <= 7 && dDay >= 0 && (
    <span className="rounded-full bg-warning-500 px-2 py-0.5 text-xs font-bold text-white">
      D-{dDay}
    </span>
  )}
  {/* ... 기존 북마크 버튼 */}
</div>
```

- [ ] **Step 2: Attach to `PolicyDetailPage`**

Modify `frontend/src/pages/PolicyDetailPage.tsx` 의 헤더(line 117 근처):

상단 import 에 추가:
```tsx
import SourceBadge from '@/components/policy/SourceBadge';
```

기존 헤더 뱃지 줄 (line 118-136 영역) 에 `SourceBadge` 삽입:
```tsx
<div className="mb-3 flex items-center gap-2">
  <CategoryBadge category={policy.category} />
  <StatusBadge status={getEffectiveStatus(policy)} />
  <SourceBadge sourceType={policy.sourceType} sourceLabel={policy.sourceLabel} size="md" />
  <button
    onClick={onBookmarkToggle}
    className="ml-auto flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-gray-50"
    /* ... 기존 그대로 */
  >
    {/* ... */}
  </button>
</div>
```

- [ ] **Step 3: Build (타입체크 포함)**

Run: `cd frontend && npm run build`
Expected: 빌드 성공. 타입 에러 없음.

만약 mock 데이터에서 `sourceType`/`sourceLabel` 누락 에러가 나면 (테스트 픽스처/Storybook 등) 각각 `null` 로 채움.

- [ ] **Step 4: 수동 시각 확인 (브라우저)**

```bash
# 백엔드 (별도 터미널)
cd backend && ./gradlew bootRun
# 프론트
cd frontend && npm run dev
```

`http://localhost:5173` 접속:
- 정책 목록 페이지 → 카드 상단에 출처 뱃지(예: 파란 "복지로" 배경) 가 보이는지
- 정책 상세 페이지 → 헤더 영역에 약간 더 큰(`h-6`) 뱃지가 보이는지
- source 가 없는 정책(있다면) → 뱃지가 아예 렌더되지 않는지

스크린샷 확인. 정렬·간격·반응형 깨짐 없으면 OK.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/policy/PolicyCard.tsx \
        frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(fe): PolicyCard/PolicyDetailPage 에 SourceBadge 부착"
```

---

## Task 9: 최종 빌드 + spec 후속 메모 정리

**Files:** (변경 없음)

- [ ] **Step 1: 백엔드 + 프론트 전체 빌드**

```bash
cd backend && ./gradlew clean build
cd ../frontend && npm run build
```

Expected: 양쪽 모두 SUCCESSFUL.

- [ ] **Step 2: 작업 요약 / PR 준비**

브랜치 푸시 및 PR 생성은 별도. plan 자체는 여기서 종료.

> 후속 작업 (별도 spec): ingestion 다중 출처 정책 dedup — `docs/superpowers/2026-04-28-next-steps.md` 의 보조 후속 항목 참조.
