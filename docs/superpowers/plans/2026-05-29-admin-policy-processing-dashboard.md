# 어드민 정책 처리 현황 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 모든 정책의 5단계 처리 status + 첨부 임베딩 + 참고 사이트 fetch 결과를 한 화면에서 보고, 미흡 정책을 직접 재실행할 수 있는 대시보드 구축.

**Architecture:** Phase A 가 만든 `policy_processing_step` 테이블 + 기존 `policy_attachment` / `policy_document` 테이블을 admin 모듈의 `AdminPolicyProcessingService` 가 일괄 조회하여 종합 완성도와 상세 데이터를 도출. 프론트엔드는 신규 페이지 `/admin/policies/processing` 에서 1차 표 (요약 8컬럼) + 행 펼침 (5단계·첨부·참조 표 3개 + 액션) 으로 표시. 재실행은 기존 `AttachmentReindexService`·`RagIndexingService` 등을 admin endpoint 가 트리거하면서 `PolicyProcessingStepService` 로 step 기록.

**Tech Stack:** Spring Boot 4 + JPA + TestContainers Postgres / React 19 + TypeScript + TanStack Query v5 + Zustand + Tailwind v4 + shadcn/ui.

**Spec:** [2026-05-29-admin-policy-processing-dashboard-design.md](../specs/2026-05-29-admin-policy-processing-dashboard-design.md)

---

## 파일 구조

### Backend (신규)

```
backend/src/main/java/com/youthfit/
├── admin/
│   ├── application/
│   │   ├── dto/
│   │   │   ├── PolicyProcessingListCommand.java          # 검색·필터·정렬·페이징 명령 record
│   │   │   ├── PolicyProcessingFilter.java               # enum: ALL, INCOMPLETE, PARTIAL, RAG_FAILED, ATTACHMENT_EMBEDDING_MISSING, REFERENCE_FETCH_FAILED, GUIDE_RULE_FAILED, RECENT_24H
│   │   │   ├── PolicyProcessingSort.java                 # enum: UPDATED_DESC, COMPLETENESS_ASC, ID_ASC
│   │   │   ├── PolicyProcessingListResult.java           # 응답 result
│   │   │   ├── PolicyProcessingDetailResult.java
│   │   │   ├── PolicyProcessingStatsResult.java
│   │   │   ├── PolicyProcessingItemResult.java
│   │   │   ├── AttachmentSummaryResult.java
│   │   │   ├── ReferenceSummaryResult.java
│   │   │   ├── StepDetailResult.java
│   │   │   ├── AttachmentDetailResult.java
│   │   │   └── ReferenceDetailResult.java
│   │   └── service/
│   │       └── AdminPolicyProcessingService.java         # 7종 메서드 (조회 3 + 재실행 5 - 통합 1 = 7)
│   ├── domain/model/
│   │   └── PolicyProcessingCompleteness.java             # enum: COMPLETE, PARTIAL, INCOMPLETE
│   └── presentation/
│       ├── api/
│       │   └── AdminPolicyProcessingApi.java             # Swagger 인터페이스
│       ├── controller/
│       │   └── AdminPolicyProcessingController.java
│       └── dto/
│           ├── request/
│           │   └── ReprocessPolicyRequest.java
│           └── response/
│               ├── PolicyProcessingListResponse.java
│               ├── PolicyProcessingItemResponse.java
│               ├── PolicyProcessingDetailResponse.java
│               ├── PolicyProcessingStatsResponse.java
│               ├── StepDetailResponse.java
│               ├── AttachmentSummaryResponse.java
│               ├── AttachmentDetailResponse.java
│               ├── ReferenceSummaryResponse.java
│               ├── ReferenceDetailResponse.java
│               └── ReprocessResponse.java
```

### Backend (수정)

```
backend/src/main/java/com/youthfit/
├── policy/
│   ├── domain/repository/
│   │   ├── PolicyProcessingStepRepository.java          # findLatestByPolicyIds, findStatusMapByPolicyIds 추가
│   │   └── PolicyAttachmentRepository.java              # aggregateExtractionByPolicyIds 추가
│   └── infrastructure/persistence/
│       ├── PolicyProcessingStepRepositoryImpl.java       # 신규 메서드 구현
│       ├── PolicyProcessingStepJpaRepository.java        # 신규 쿼리
│       ├── PolicyAttachmentRepositoryImpl.java           # 신규 메서드 구현
│       └── PolicyAttachmentJpaRepository.java            # 신규 집계 쿼리
└── rag/
    ├── domain/repository/
    │   └── PolicyDocumentRepository.java                 # countAttachmentEmbeddingsByPolicyIds, findEmbeddedAttachmentIds 추가
    └── infrastructure/persistence/
        ├── PolicyDocumentRepositoryImpl.java
        └── PolicyDocumentJpaRepository.java
```

### Frontend (신규)

```
frontend/src/
├── pages/admin/
│   ├── AdminPolicyProcessingPage.tsx
│   └── policy-processing/
│       ├── PolicyProcessingKpiCards.tsx
│       ├── PolicyProcessingFilters.tsx
│       ├── PolicyProcessingTable.tsx
│       ├── PolicyProcessingDetailPanel.tsx
│       ├── PolicyProcessingRowActions.tsx
│       └── ReprocessConfirmDialog.tsx
├── apis/
│   └── adminPolicyProcessing.api.ts
├── hooks/
│   ├── queries/
│   │   ├── useAdminPolicyProcessingList.ts
│   │   ├── useAdminPolicyProcessingDetail.ts
│   │   └── useAdminPolicyProcessingStats.ts
│   └── mutations/
│       ├── useRetryProcessingStep.ts
│       ├── useReindexAttachment.ts
│       ├── useReindexAllAttachments.ts
│       ├── useReindexRag.ts
│       └── useReprocessPolicy.ts
└── types/
    └── adminPolicyProcessing.ts
```

### Frontend (수정)
- `frontend/src/App.tsx` — 신규 라우트 + Lazy import 추가
- `frontend/src/components/layout/AdminLayout.tsx` (사이드바 컴포넌트가 분리되어 있으면 그것) — 메뉴 항목 추가

---

# Phase 1 — 백엔드 데이터 조회 layer

목록·KPI·상세 3종 endpoint 가 동작하고, 더미 데이터로 테스트 통과. 재실행 없이도 운영자가 화면 데이터를 볼 수 있는 상태.

## Task 1: PolicyProcessingStepRepository — 일괄 조회 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java`
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImplTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`PolicyProcessingStepRepositoryImplTest` 에 다음 테스트 추가 (TestContainers Postgres 기반 기존 패턴 따름).

```java
@Test
void findLatestStatusMapByPolicyIds_returnsAllStepsPerPolicy() {
    // policy 100: INGESTION SUCCESS, RAG_INDEXING FAILED
    persistStep(100L, ProcessingStep.INGESTION, ProcessingStatus.SUCCESS, 1);
    persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED, 1);
    persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS, 2); // 최신 attempt
    // policy 101: ENRICHMENT SKIPPED
    persistStep(101L, ProcessingStep.ENRICHMENT, ProcessingStatus.SKIPPED, 1);

    Map<Long, Map<ProcessingStep, ProcessingStatus>> result =
            repository.findLatestStatusMapByPolicyIds(List.of(100L, 101L));

    assertThat(result.get(100L)).containsEntry(ProcessingStep.INGESTION, ProcessingStatus.SUCCESS);
    assertThat(result.get(100L)).containsEntry(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS);
    assertThat(result.get(101L)).containsEntry(ProcessingStep.ENRICHMENT, ProcessingStatus.SKIPPED);
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyProcessingStepRepositoryImplTest.findLatestStatusMapByPolicyIds_returnsAllStepsPerPolicy`
Expected: `findLatestStatusMapByPolicyIds` 미존재로 컴파일 실패.

- [ ] **Step 3: Repository 인터페이스에 메서드 추가**

`PolicyProcessingStepRepository.java`:
```java
/**
 * 정책 다건의 각 step 별 최신 attempt status 일괄 조회.
 *
 * @return policyId -> (step -> latestStatus). 정책에 step 행이 없으면 그 정책 키 자체가 없음.
 */
Map<Long, Map<ProcessingStep, ProcessingStatus>> findLatestStatusMapByPolicyIds(List<Long> policyIds);
```

`import java.util.Map;` 추가.

- [ ] **Step 4: JpaRepository 쿼리 추가**

`PolicyProcessingStepJpaRepository.java` 에 native 또는 JPQL 쿼리 추가:

```java
@Query("""
    select pps from PolicyProcessingStep pps
    where pps.policyId in :policyIds
      and pps.attempt = (
        select max(p2.attempt) from PolicyProcessingStep p2
        where p2.policyId = pps.policyId and p2.step = pps.step
      )
""")
List<PolicyProcessingStep> findLatestForPolicies(@Param("policyIds") List<Long> policyIds);
```

- [ ] **Step 5: Repository 구현**

`PolicyProcessingStepRepositoryImpl.java` 에 메서드 추가:

```java
@Override
public Map<Long, Map<ProcessingStep, ProcessingStatus>> findLatestStatusMapByPolicyIds(List<Long> policyIds) {
    if (policyIds.isEmpty()) return Map.of();
    List<PolicyProcessingStep> rows = jpaRepository.findLatestForPolicies(policyIds);
    return rows.stream().collect(Collectors.groupingBy(
        PolicyProcessingStep::getPolicyId,
        Collectors.toMap(
            PolicyProcessingStep::getStep,
            PolicyProcessingStep::getStatus,
            (a, b) -> b
        )
    ));
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyProcessingStepRepositoryImplTest.findLatestStatusMapByPolicyIds_returnsAllStepsPerPolicy`
Expected: PASS.

- [ ] **Step 7: 추가 메서드 — 펼침용 단일 정책의 모든 step + detail_json 조회**

`PolicyProcessingStepRepository.java` 에 메서드 추가:
```java
/** 정책의 5단계 각 step 의 최신 attempt 행 전체 반환 (펼침 영역 표시용). */
List<PolicyProcessingStep> findLatestRowsByPolicyId(Long policyId);
```

`PolicyProcessingStepJpaRepository.java`:
```java
@Query("""
    select pps from PolicyProcessingStep pps
    where pps.policyId = :policyId
      and pps.attempt = (
        select max(p2.attempt) from PolicyProcessingStep p2
        where p2.policyId = :policyId and p2.step = pps.step
      )
    order by pps.step
""")
List<PolicyProcessingStep> findLatestByPolicyIdAllSteps(@Param("policyId") Long policyId);
```

Impl:
```java
@Override
public List<PolicyProcessingStep> findLatestRowsByPolicyId(Long policyId) {
    return jpaRepository.findLatestByPolicyIdAllSteps(policyId);
}
```

- [ ] **Step 8: 추가 메서드 테스트**

```java
@Test
void findLatestRowsByPolicyId_returnsOnlyLatestAttemptPerStep() {
    persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED, 1);
    persistStep(100L, ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS, 2);
    persistStep(100L, ProcessingStep.INGESTION, ProcessingStatus.SUCCESS, 1);

    List<PolicyProcessingStep> rows = repository.findLatestRowsByPolicyId(100L);

    assertThat(rows).hasSize(2);
    assertThat(rows).extracting(PolicyProcessingStep::getStep)
        .containsExactly(ProcessingStep.INGESTION, ProcessingStep.RAG_INDEXING);
    assertThat(rows).extracting(PolicyProcessingStep::getAttempt)
        .containsExactly(1, 2);
}
```

Run: `cd backend && ./gradlew test --tests PolicyProcessingStepRepositoryImplTest`
Expected: 모든 테스트 PASS.

- [ ] **Step 9: 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyProcessingStepRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepJpaRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImpl.java backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyProcessingStepRepositoryImplTest.java
git commit -m "feat: PolicyProcessingStepRepository 일괄 조회 메서드 추가 (findLatestStatusMapByPolicyIds, findLatestRowsByPolicyId)"
```

## Task 2: PolicyAttachmentRepository — 정책별 extraction 집계

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyAttachmentRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentRepositoryImpl.java`
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentRepositoryImplTest.java`

- [ ] **Step 1: 집계 record 도메인 정의**

`backend/src/main/java/com/youthfit/policy/domain/model/AttachmentExtractionCounts.java` 신규:

```java
package com.youthfit.policy.domain.model;

/**
 * 정책 1건의 첨부 extraction_status 카운트 집계.
 * total: 전체 첨부 수
 * downloaded: extraction_status >= DOWNLOADED 인 카운트 (DOWNLOADED + EXTRACTING + EXTRACTED + FAILED + SKIPPED)
 * extracted: extraction_status = EXTRACTED 인 카운트
 */
public record AttachmentExtractionCounts(
    long total,
    long downloaded,
    long extracted
) {
    public static AttachmentExtractionCounts empty() {
        return new AttachmentExtractionCounts(0, 0, 0);
    }
}
```

- [ ] **Step 2: 실패 테스트**

```java
@Test
void aggregateExtractionByPolicyIds_countsCorrectly() {
    persistAttachment(100L, AttachmentStatus.EXTRACTED);
    persistAttachment(100L, AttachmentStatus.EXTRACTED);
    persistAttachment(100L, AttachmentStatus.DOWNLOADED);
    persistAttachment(100L, AttachmentStatus.PENDING);
    persistAttachment(101L, AttachmentStatus.FAILED);

    Map<Long, AttachmentExtractionCounts> result =
        repository.aggregateExtractionByPolicyIds(List.of(100L, 101L));

    assertThat(result.get(100L)).isEqualTo(new AttachmentExtractionCounts(4, 3, 2));
    assertThat(result.get(101L)).isEqualTo(new AttachmentExtractionCounts(1, 1, 0));
}
```

- [ ] **Step 3: 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentRepositoryImplTest.aggregateExtractionByPolicyIds_countsCorrectly`
Expected: 컴파일 실패.

- [ ] **Step 4: Repository 인터페이스 메서드 추가**

```java
/**
 * 정책 다건의 첨부 extraction_status 카운트 집계.
 * 첨부가 없는 정책은 결과 맵에서 누락됨 (호출자가 empty() 로 처리).
 */
Map<Long, AttachmentExtractionCounts> aggregateExtractionByPolicyIds(List<Long> policyIds);
```

- [ ] **Step 5: JpaRepository 쿼리 추가**

`PolicyAttachmentJpaRepository.java`:
```java
@Query("""
    select new com.youthfit.policy.domain.model.AttachmentExtractionCounts(
        count(a),
        sum(case when a.extractionStatus in (com.youthfit.policy.domain.model.AttachmentStatus.DOWNLOADED,
                                              com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTING,
                                              com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED,
                                              com.youthfit.policy.domain.model.AttachmentStatus.FAILED,
                                              com.youthfit.policy.domain.model.AttachmentStatus.SKIPPED) then 1 else 0 end),
        sum(case when a.extractionStatus = com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED then 1 else 0 end)
    )
    from PolicyAttachment a
    where a.policyId in :policyIds
    group by a.policyId
""")
List<Object[]> aggregateExtractionByPolicyIdsRaw(@Param("policyIds") List<Long> policyIds);
```

(JPQL 의 record constructor 매핑이 까다로우면 `select a.policyId, count(a), ...` 로 반환 후 impl 에서 매핑)

- [ ] **Step 6: Impl 매핑**

```java
@Override
public Map<Long, AttachmentExtractionCounts> aggregateExtractionByPolicyIds(List<Long> policyIds) {
    if (policyIds.isEmpty()) return Map.of();
    List<Object[]> rows = jpaRepository.aggregateExtractionByPolicyIdsRaw(policyIds);
    Map<Long, AttachmentExtractionCounts> result = new HashMap<>();
    for (Object[] row : rows) {
        Long policyId = (Long) row[0];
        long total = ((Number) row[1]).longValue();
        long downloaded = ((Number) row[2]).longValue();
        long extracted = ((Number) row[3]).longValue();
        result.put(policyId, new AttachmentExtractionCounts(total, downloaded, extracted));
    }
    return result;
}
```

(쿼리는 `select a.policyId, count(a), sum(...), sum(...) from PolicyAttachment a where a.policyId in :ids group by a.policyId` 로 변경.)

- [ ] **Step 7: 테스트 통과 확인 + 커밋**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentRepositoryImplTest.aggregateExtractionByPolicyIds_countsCorrectly`
Expected: PASS.

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/AttachmentExtractionCounts.java backend/src/main/java/com/youthfit/policy/domain/repository/PolicyAttachmentRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentJpaRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentRepositoryImpl.java backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentRepositoryImplTest.java
git commit -m "feat: PolicyAttachmentRepository.aggregateExtractionByPolicyIds 추가 (정책별 카운트 집계)"
```

## Task 3: PolicyDocumentRepository — 첨부 임베딩 카운트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java`
- Test: `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImplTest.java`

- [ ] **Step 1: 실패 테스트**

```java
@Test
void countAttachmentEmbeddingsByPolicyIds_countsDistinctAttachmentIds() {
    persistDocument(100L, DocumentSource.BODY, null, 0);
    persistDocument(100L, DocumentSource.ATTACHMENT, 401L, 0);
    persistDocument(100L, DocumentSource.ATTACHMENT, 401L, 1); // 같은 attachment 의 다른 chunk
    persistDocument(100L, DocumentSource.ATTACHMENT, 402L, 0);
    persistDocument(101L, DocumentSource.ATTACHMENT, 501L, 0);

    Map<Long, Long> result = repository.countAttachmentEmbeddingsByPolicyIds(List.of(100L, 101L));

    assertThat(result).containsEntry(100L, 2L); // distinct attachment_id
    assertThat(result).containsEntry(101L, 1L);
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyDocumentRepositoryImplTest.countAttachmentEmbeddingsByPolicyIds_countsDistinctAttachmentIds`
Expected: 컴파일 실패.

- [ ] **Step 3: PolicyDocument 모델에 `attachmentId` 필드 존재 확인**

먼저 `PolicyDocument` 도메인 모델에 `source` 와 `attachmentId` 필드가 있는지 확인. 없으면 다음 step 에서 별도 결정 필요. 보통 `policy_document` 테이블의 컬럼 매핑.

Run: `grep -E "attachmentId|source" backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java`

만약 `attachmentId` 가 없고 다른 방식 (예: metadata JSON) 으로 표현된다면, 그 방식에 맞게 쿼리 조정.

- [ ] **Step 4: Repository 인터페이스 메서드 추가**

```java
/**
 * 정책 다건의 첨부 임베딩 (source=ATTACHMENT) 개수 일괄 조회.
 * @return policyId -> distinct attachmentId count. 첨부 임베딩이 없는 정책은 결과 맵에서 누락.
 */
Map<Long, Long> countAttachmentEmbeddingsByPolicyIds(List<Long> policyIds);

/** 펼침용: 정책 1건에서 임베딩 완료된 attachment_id set. */
Set<Long> findEmbeddedAttachmentIds(Long policyId);
```

- [ ] **Step 5: JpaRepository 쿼리**

```java
@Query("""
    select d.policyId, count(distinct d.attachmentId)
    from PolicyDocument d
    where d.policyId in :policyIds and d.source = com.youthfit.rag.domain.model.DocumentSource.ATTACHMENT
    group by d.policyId
""")
List<Object[]> countAttachmentEmbeddingsByPolicyIdsRaw(@Param("policyIds") List<Long> policyIds);

@Query("""
    select distinct d.attachmentId from PolicyDocument d
    where d.policyId = :policyId and d.source = com.youthfit.rag.domain.model.DocumentSource.ATTACHMENT
""")
List<Long> findDistinctAttachmentIds(@Param("policyId") Long policyId);
```

- [ ] **Step 6: Impl 매핑 + 테스트 통과 확인**

```java
@Override
public Map<Long, Long> countAttachmentEmbeddingsByPolicyIds(List<Long> policyIds) {
    if (policyIds.isEmpty()) return Map.of();
    return jpaRepository.countAttachmentEmbeddingsByPolicyIdsRaw(policyIds).stream()
        .collect(Collectors.toMap(
            row -> (Long) row[0],
            row -> ((Number) row[1]).longValue()
        ));
}

@Override
public Set<Long> findEmbeddedAttachmentIds(Long policyId) {
    return new HashSet<>(jpaRepository.findDistinctAttachmentIds(policyId));
}
```

Run: `cd backend && ./gradlew test --tests PolicyDocumentRepositoryImplTest.countAttachmentEmbeddingsByPolicyIds_countsDistinctAttachmentIds`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImplTest.java
git commit -m "feat: PolicyDocumentRepository 에 첨부 임베딩 카운트 메서드 추가"
```

## Task 4: 도메인 enum + Application DTO record

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/domain/model/PolicyProcessingCompleteness.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingFilter.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingSort.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingListCommand.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/AttachmentSummaryResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/ReferenceSummaryResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingItemResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingListResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingStatsResult.java`

- [ ] **Step 1: PolicyProcessingCompleteness enum**

```java
package com.youthfit.admin.domain.model;

public enum PolicyProcessingCompleteness {
    COMPLETE,
    PARTIAL,
    INCOMPLETE
}
```

- [ ] **Step 2: PolicyProcessingFilter / Sort enum**

`PolicyProcessingFilter.java`:
```java
package com.youthfit.admin.application.dto;

public enum PolicyProcessingFilter {
    ALL,
    INCOMPLETE,
    PARTIAL,
    RAG_FAILED,
    ATTACHMENT_EMBEDDING_MISSING,
    REFERENCE_FETCH_FAILED,
    GUIDE_RULE_FAILED,
    RECENT_24H
}
```

`PolicyProcessingSort.java`:
```java
package com.youthfit.admin.application.dto;

public enum PolicyProcessingSort {
    UPDATED_DESC,
    COMPLETENESS_ASC,
    ID_ASC
}
```

- [ ] **Step 3: Command + Result record**

`PolicyProcessingListCommand.java`:
```java
package com.youthfit.admin.application.dto;

public record PolicyProcessingListCommand(
    String query,
    String region,
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

`AttachmentSummaryResult.java`:
```java
package com.youthfit.admin.application.dto;

public record AttachmentSummaryResult(long total, long extracted, long embedded) {}
```

`ReferenceSummaryResult.java`:
```java
package com.youthfit.admin.application.dto;

public record ReferenceSummaryResult(long total, long succeeded) {
    public static ReferenceSummaryResult placeholder() {
        return new ReferenceSummaryResult(0, 0);
    }
}
```

`PolicyProcessingItemResult.java`:
```java
package com.youthfit.admin.application.dto;

import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.time.LocalDateTime;
import java.util.Map;

public record PolicyProcessingItemResult(
    Long policyId,
    String title,
    String region,
    PolicyProcessingCompleteness completeness,
    Map<ProcessingStep, ProcessingStatus> stepStatuses,
    AttachmentSummaryResult attachments,
    ReferenceSummaryResult references,
    LocalDateTime updatedAt
) {}
```

`PolicyProcessingListResult.java`:
```java
package com.youthfit.admin.application.dto;

import java.util.List;

public record PolicyProcessingListResult(
    long totalCount,
    int page,
    int size,
    List<PolicyProcessingItemResult> items
) {}
```

`PolicyProcessingStatsResult.java`:
```java
package com.youthfit.admin.application.dto;

public record PolicyProcessingStatsResult(
    long totalCount,
    long completeCount,
    long partialCount,
    long incompleteCount,
    long recent24hCount
) {}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: 성공.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/domain/model/PolicyProcessingCompleteness.java backend/src/main/java/com/youthfit/admin/application/dto/
git commit -m "feat: admin 정책 처리 현황 enum + Command/Result record 추가"
```

## Task 5: AdminPolicyProcessingService.findProcessingPolicies (목록)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java`
- Test: `backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java`

- [ ] **Step 1: 완성도 계산 단위 테스트**

```java
@ExtendWith(MockitoExtension.class)
class AdminPolicyProcessingServiceTest {

    @Mock PolicyRepository policyRepository;
    @Mock PolicyProcessingStepRepository stepRepository;
    @Mock PolicyAttachmentRepository attachmentRepository;
    @Mock PolicyDocumentRepository documentRepository;

    @InjectMocks AdminPolicyProcessingService service;

    @Test
    void completenessIsCompleteWhenRagSuccessAndNoAttachments() {
        // policy 100: RAG SUCCESS, attachments 0
        givenPolicy(100L, "월세 지원");
        givenStepStatuses(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS));
        givenAttachmentCounts(100L, new AttachmentExtractionCounts(0, 0, 0));
        givenEmbeddingCount(100L, 0L);

        PolicyProcessingListResult result = service.findProcessingPolicies(
            new PolicyProcessingListCommand(null, null, PolicyProcessingFilter.ALL, PolicyProcessingSort.UPDATED_DESC, 0, 50)
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness()).isEqualTo(PolicyProcessingCompleteness.COMPLETE);
    }

    @Test
    void completenessIsPartialWhenAttachmentsExtractedButEmbeddingMissing() {
        givenPolicy(100L, "도전지원금");
        givenStepStatuses(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS));
        givenAttachmentCounts(100L, new AttachmentExtractionCounts(5, 5, 4)); // 4 EXTRACTED
        givenEmbeddingCount(100L, 2L); // 2개만 임베딩

        PolicyProcessingListResult result = service.findProcessingPolicies(
            new PolicyProcessingListCommand(null, null, PolicyProcessingFilter.ALL, PolicyProcessingSort.UPDATED_DESC, 0, 50)
        );

        assertThat(result.items().get(0).completeness()).isEqualTo(PolicyProcessingCompleteness.PARTIAL);
    }

    @Test
    void completenessIsIncompleteWhenRagFailed() {
        givenPolicy(100L, "월세대출");
        givenStepStatuses(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED));
        givenAttachmentCounts(100L, AttachmentExtractionCounts.empty());
        givenEmbeddingCount(100L, 0L);

        PolicyProcessingListResult result = service.findProcessingPolicies(
            new PolicyProcessingListCommand(null, null, PolicyProcessingFilter.ALL, PolicyProcessingSort.UPDATED_DESC, 0, 50)
        );

        assertThat(result.items().get(0).completeness()).isEqualTo(PolicyProcessingCompleteness.INCOMPLETE);
    }
}
```

(헬퍼 메서드 `givenPolicy`, `givenStepStatuses`, `givenAttachmentCounts`, `givenEmbeddingCount` 는 mock when-then 으로 작성.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingServiceTest`
Expected: `AdminPolicyProcessingService` 미존재 또는 메서드 미존재.

- [ ] **Step 3: 서비스 구현**

`AdminPolicyProcessingService.java`:
```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dto.*;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPolicyProcessingService {

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepRepository stepRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PolicyDocumentRepository documentRepository;

    public PolicyProcessingListResult findProcessingPolicies(PolicyProcessingListCommand command) {
        // 1) 정책 페이지 조회 (검색·지역 적용. filter 는 step+attachment 결합 후 적용)
        Page<Policy> policyPage = policyRepository.findForAdminProcessing(
            command.query(),
            command.region(),
            toSpringSort(command.sort()),
            PageRequest.of(command.page(), command.size())
        );

        List<Long> policyIds = policyPage.getContent().stream().map(Policy::getId).toList();

        // 2) 일괄 조회 — step / attachment / embedding
        Map<Long, Map<ProcessingStep, ProcessingStatus>> stepMap =
            stepRepository.findLatestStatusMapByPolicyIds(policyIds);
        Map<Long, AttachmentExtractionCounts> attachMap =
            attachmentRepository.aggregateExtractionByPolicyIds(policyIds);
        Map<Long, Long> embedMap =
            documentRepository.countAttachmentEmbeddingsByPolicyIds(policyIds);

        // 3) 정책별 item 조립
        List<PolicyProcessingItemResult> items = policyPage.getContent().stream().map(p -> {
            Map<ProcessingStep, ProcessingStatus> stepStatuses =
                stepMap.getOrDefault(p.getId(), Map.of());
            AttachmentExtractionCounts attachCounts =
                attachMap.getOrDefault(p.getId(), AttachmentExtractionCounts.empty());
            long embeddedCount = embedMap.getOrDefault(p.getId(), 0L);

            return new PolicyProcessingItemResult(
                p.getId(),
                p.getTitle(),
                p.getRegion(),
                computeCompleteness(stepStatuses, attachCounts, embeddedCount),
                stepStatuses,
                new AttachmentSummaryResult(attachCounts.total(), attachCounts.extracted(), embeddedCount),
                ReferenceSummaryResult.placeholder(), // Phase D 후 채워짐
                p.getUpdatedAt()
            );
        }).toList();

        // 4) filter 적용 (in-memory) — TODO: 성능 개선 필요 시 SQL 로 이동
        items = applyFilter(items, command.filter());

        return new PolicyProcessingListResult(
            policyPage.getTotalElements(),
            command.page(),
            command.size(),
            items
        );
    }

    PolicyProcessingCompleteness computeCompleteness(
        Map<ProcessingStep, ProcessingStatus> stepStatuses,
        AttachmentExtractionCounts attachCounts,
        long embeddedCount
    ) {
        ProcessingStatus ragStatus = stepStatuses.get(ProcessingStep.RAG_INDEXING);
        if (ragStatus != ProcessingStatus.SUCCESS) {
            return PolicyProcessingCompleteness.INCOMPLETE;
        }
        boolean noAttachments = attachCounts.total() == 0;
        boolean allEmbedded = attachCounts.total() > 0
            && attachCounts.extracted() == attachCounts.total()
            && embeddedCount == attachCounts.total();
        if (noAttachments || allEmbedded) {
            return PolicyProcessingCompleteness.COMPLETE;
        }
        return PolicyProcessingCompleteness.PARTIAL;
    }

    private List<PolicyProcessingItemResult> applyFilter(
        List<PolicyProcessingItemResult> items, PolicyProcessingFilter filter
    ) {
        return switch (filter) {
            case ALL -> items;
            case INCOMPLETE -> items.stream()
                .filter(i -> i.completeness() == PolicyProcessingCompleteness.INCOMPLETE).toList();
            case PARTIAL -> items.stream()
                .filter(i -> i.completeness() == PolicyProcessingCompleteness.PARTIAL).toList();
            case RAG_FAILED -> items.stream()
                .filter(i -> i.stepStatuses().get(ProcessingStep.RAG_INDEXING) == ProcessingStatus.FAILED).toList();
            case ATTACHMENT_EMBEDDING_MISSING -> items.stream()
                .filter(i -> i.attachments().total() > 0
                    && i.attachments().embedded() < i.attachments().total()).toList();
            case GUIDE_RULE_FAILED -> items.stream()
                .filter(i -> i.stepStatuses().get(ProcessingStep.GUIDE) == ProcessingStatus.FAILED
                    || i.stepStatuses().get(ProcessingStep.RULE) == ProcessingStatus.FAILED).toList();
            case REFERENCE_FETCH_FAILED -> List.of(); // Phase D 후 활성
            case RECENT_24H -> items; // SQL 단계에서 처리 (정책 페이지 조회에 created_at >= now-24h)
        };
    }

    private org.springframework.data.domain.Sort toSpringSort(PolicyProcessingSort sort) {
        return switch (sort) {
            case UPDATED_DESC -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt");
            case COMPLETENESS_ASC -> org.springframework.data.domain.Sort.by("id"); // service 후처리 정렬 필요
            case ID_ASC -> org.springframework.data.domain.Sort.by("id");
        };
    }
}
```

(`PolicyRepository.findForAdminProcessing(query, region, sort, pageable)` 메서드는 별도 task 에서 추가.)

- [ ] **Step 4: PolicyRepository 메서드 추가**

`backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` 에 추가:
```java
Page<Policy> findForAdminProcessing(String query, String region, Sort sort, Pageable pageable);
```

JpaRepository + Impl 도 동일 패턴 (기존 `findFiltered` 패턴 따름. 없으면 spec 의 7.1 명세 따라 신규).

쿼리 예 (Impl):
```java
@Override
public Page<Policy> findForAdminProcessing(String query, String region, Sort sort, Pageable pageable) {
    // QueryDSL 또는 spec 사용. 단순화: 다음 조건
    // - query: title like % OR id == query (숫자인 경우)
    // - region: equals (null 무시)
    // pageable + sort
    return jpaRepository.findFilteredForProcessing(
        query == null ? null : "%" + query + "%",
        region,
        pageable
    );
}
```

JpaRepository:
```java
@Query("""
    select p from Policy p
    where (:query is null or p.title like :query)
      and (:region is null or p.region = :region)
""")
Page<Policy> findFilteredForProcessing(@Param("query") String query, @Param("region") String region, Pageable pageable);
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingServiceTest`
Expected: 3개 테스트 모두 PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyRepositoryImpl.java backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat: AdminPolicyProcessingService.findProcessingPolicies + 완성도 계산 로직"
```

## Task 6: AdminPolicyProcessingService.findProcessingStats (KPI)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java`
- Test: `backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java`

- [ ] **Step 1: 실패 테스트**

```java
@Test
void findProcessingStats_aggregatesAllCompletenessBuckets() {
    // 5개 정책 mock: 3 COMPLETE, 1 PARTIAL, 1 INCOMPLETE, 2 created within 24h
    givenPolicies(List.of(
        policyMock(1L, "A", LocalDateTime.now().minusHours(2)),
        policyMock(2L, "B", LocalDateTime.now().minusDays(2)),
        policyMock(3L, "C", LocalDateTime.now().minusHours(10)),
        policyMock(4L, "D", LocalDateTime.now().minusDays(5)),
        policyMock(5L, "E", LocalDateTime.now().minusDays(7))
    ));
    givenStepStatusesBatch(Map.of(
        1L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
        2L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
        3L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
        4L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
        5L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED)
    ));
    givenAttachmentCountsBatch(Map.of(
        1L, AttachmentExtractionCounts.empty(),
        2L, AttachmentExtractionCounts.empty(),
        3L, AttachmentExtractionCounts.empty(),
        4L, new AttachmentExtractionCounts(3, 3, 1), // PARTIAL
        5L, AttachmentExtractionCounts.empty()
    ));
    givenEmbeddingCountsBatch(Map.of(4L, 1L));

    PolicyProcessingStatsResult stats = service.findProcessingStats();

    assertThat(stats.totalCount()).isEqualTo(5);
    assertThat(stats.completeCount()).isEqualTo(3);
    assertThat(stats.partialCount()).isEqualTo(1);
    assertThat(stats.incompleteCount()).isEqualTo(1);
    assertThat(stats.recent24hCount()).isEqualTo(2);
}
```

- [ ] **Step 2: 서비스에 `findProcessingStats` 메서드 추가**

```java
public PolicyProcessingStatsResult findProcessingStats() {
    List<Policy> all = policyRepository.findAllForStats();
    List<Long> ids = all.stream().map(Policy::getId).toList();
    Map<Long, Map<ProcessingStep, ProcessingStatus>> stepMap =
        stepRepository.findLatestStatusMapByPolicyIds(ids);
    Map<Long, AttachmentExtractionCounts> attachMap =
        attachmentRepository.aggregateExtractionByPolicyIds(ids);
    Map<Long, Long> embedMap =
        documentRepository.countAttachmentEmbeddingsByPolicyIds(ids);

    long complete = 0, partial = 0, incomplete = 0, recent = 0;
    LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
    for (Policy p : all) {
        PolicyProcessingCompleteness c = computeCompleteness(
            stepMap.getOrDefault(p.getId(), Map.of()),
            attachMap.getOrDefault(p.getId(), AttachmentExtractionCounts.empty()),
            embedMap.getOrDefault(p.getId(), 0L)
        );
        switch (c) {
            case COMPLETE -> complete++;
            case PARTIAL -> partial++;
            case INCOMPLETE -> incomplete++;
        }
        if (p.getCreatedAt() != null && p.getCreatedAt().isAfter(twentyFourHoursAgo)) {
            recent++;
        }
    }

    return new PolicyProcessingStatsResult(all.size(), complete, partial, incomplete, recent);
}
```

- [ ] **Step 3: PolicyRepository.findAllForStats 추가**

전체 정책 ID + 필수 필드만. 메모리 부담 고려해 limit 가 필요할 수 있지만 정책 N=수백 수준이므로 일단 전체 조회.
```java
List<Policy> findAllForStats();
```

JpaRepository:
```java
@Query("select p from Policy p")
List<Policy> findAllForStats();
```

- [ ] **Step 4: 테스트 통과 확인 + 커밋**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingServiceTest.findProcessingStats_aggregatesAllCompletenessBuckets`
Expected: PASS.

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat: AdminPolicyProcessingService.findProcessingStats (KPI 4종 집계)"
```

## Task 7: AdminPolicyProcessingService.findProcessingDetail (펼침)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/StepDetailResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/AttachmentDetailResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/ReferenceDetailResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingDetailResult.java`
- Modify: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java`
- Test: `backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java`

- [ ] **Step 1: Detail record 정의**

`StepDetailResult.java`:
```java
package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import java.time.LocalDateTime;

public record StepDetailResult(
    ProcessingStep step,
    ProcessingStatus status,
    Long durationMs,
    int attempt,
    String reason,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {}
```

`AttachmentDetailResult.java`:
```java
package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.AttachmentStatus;

public record AttachmentDetailResult(
    Long attachmentId,
    String filename,
    AttachmentStatus extractionStatus,
    boolean embedded
) {}
```

`ReferenceDetailResult.java`:
```java
package com.youthfit.admin.application.dto;

public record ReferenceDetailResult(
    String url,
    String status,    // SUCCESS / SPA_DETECTED / TIMEOUT / HTTP_4XX / FETCH_FAILED (Phase D 후 채워짐)
    int chunkCount
) {}
```

`PolicyProcessingDetailResult.java`:
```java
package com.youthfit.admin.application.dto;

import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import java.util.List;

public record PolicyProcessingDetailResult(
    Long policyId,
    String title,
    PolicyProcessingCompleteness completeness,
    List<StepDetailResult> steps,
    List<AttachmentDetailResult> attachments,
    List<ReferenceDetailResult> references
) {}
```

- [ ] **Step 2: 실패 테스트**

```java
@Test
void findProcessingDetail_assemblesAllSignals() {
    givenPolicy(100L, "월세 지원");
    givenStepRows(100L, List.of(
        stepRow(ProcessingStep.INGESTION, ProcessingStatus.SUCCESS, 1200L, 1, null),
        stepRow(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS, 2000L, 1, null)
    ));
    givenAttachments(100L, List.of(
        attachmentMock(401L, "신청서.hwp", AttachmentStatus.EXTRACTED),
        attachmentMock(402L, "안내문.pdf", AttachmentStatus.EXTRACTED)
    ));
    givenEmbeddedIds(100L, Set.of(401L)); // 402 누락

    PolicyProcessingDetailResult result = service.findProcessingDetail(100L);

    assertThat(result.policyId()).isEqualTo(100L);
    assertThat(result.steps()).hasSize(2);
    assertThat(result.attachments()).extracting(AttachmentDetailResult::embedded)
        .containsExactly(true, false);
}
```

- [ ] **Step 3: 서비스 메서드 구현**

```java
public PolicyProcessingDetailResult findProcessingDetail(Long policyId) {
    Policy policy = policyRepository.findById(policyId)
        .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));

    List<PolicyProcessingStep> stepRows = stepRepository.findLatestRowsByPolicyId(policyId);
    List<StepDetailResult> steps = stepRows.stream().map(this::toStepDetail).toList();

    List<PolicyAttachment> attachments = attachmentRepository.findByPolicyId(policyId);
    Set<Long> embeddedIds = documentRepository.findEmbeddedAttachmentIds(policyId);
    List<AttachmentDetailResult> attachmentDetails = attachments.stream()
        .map(a -> new AttachmentDetailResult(
            a.getId(),
            a.getFilename(),
            a.getExtractionStatus(),
            embeddedIds.contains(a.getId())
        ))
        .toList();

    // 참고 사이트: Phase D 후 ENRICHMENT step.detail_json 에서 파싱. 그 전엔 빈 리스트.
    List<ReferenceDetailResult> references = parseReferenceResults(stepRows);

    // 완성도 재계산
    Map<ProcessingStep, ProcessingStatus> statusMap = steps.stream()
        .collect(Collectors.toMap(StepDetailResult::step, StepDetailResult::status, (a, b) -> b));
    AttachmentExtractionCounts counts = aggregateInMemory(attachments);
    PolicyProcessingCompleteness completeness =
        computeCompleteness(statusMap, counts, embeddedIds.size());

    return new PolicyProcessingDetailResult(
        policyId, policy.getTitle(), completeness, steps, attachmentDetails, references
    );
}

private StepDetailResult toStepDetail(PolicyProcessingStep row) {
    Long durationMs = (row.getStartedAt() != null && row.getFinishedAt() != null)
        ? java.time.Duration.between(row.getStartedAt(), row.getFinishedAt()).toMillis()
        : null;
    return new StepDetailResult(
        row.getStep(), row.getStatus(), durationMs, row.getAttempt(),
        row.getReason(), row.getStartedAt(), row.getFinishedAt()
    );
}

private List<ReferenceDetailResult> parseReferenceResults(List<PolicyProcessingStep> stepRows) {
    // Phase D 전: 빈 리스트. Phase D 후: ENRICHMENT step 의 detail_json.skippedUrls 파싱.
    return List.of();
}

private AttachmentExtractionCounts aggregateInMemory(List<PolicyAttachment> attachments) {
    long total = attachments.size();
    long downloaded = attachments.stream()
        .filter(a -> a.getExtractionStatus().ordinal() >= AttachmentStatus.DOWNLOADED.ordinal())
        .count();
    long extracted = attachments.stream()
        .filter(a -> a.getExtractionStatus() == AttachmentStatus.EXTRACTED)
        .count();
    return new AttachmentExtractionCounts(total, downloaded, extracted);
}
```

- [ ] **Step 4: 테스트 통과 확인 + 커밋**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingServiceTest`
Expected: 모든 테스트 PASS.

```bash
git add backend/src/main/java/com/youthfit/admin/application/dto/StepDetailResult.java backend/src/main/java/com/youthfit/admin/application/dto/AttachmentDetailResult.java backend/src/main/java/com/youthfit/admin/application/dto/ReferenceDetailResult.java backend/src/main/java/com/youthfit/admin/application/dto/PolicyProcessingDetailResult.java backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat: AdminPolicyProcessingService.findProcessingDetail (펼침용 상세 조립)"
```

## Task 8: Presentation — Api 인터페이스 + Response DTO

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/api/AdminPolicyProcessingApi.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingItemResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingListResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingDetailResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/PolicyProcessingStatsResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/StepDetailResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/AttachmentSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/AttachmentDetailResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/ReferenceSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/ReferenceDetailResponse.java`

- [ ] **Step 1: Response record 작성 (대표 예시)**

`PolicyProcessingItemResponse.java`:
```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingItemResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "정책 처리 현황 목록 행")
public record PolicyProcessingItemResponse(
    @Schema(description = "정책 ID") Long policyId,
    String title,
    String region,
    PolicyProcessingCompleteness completeness,
    Map<ProcessingStep, ProcessingStatus> stepStatuses,
    AttachmentSummaryResponse attachments,
    ReferenceSummaryResponse references,
    LocalDateTime updatedAt
) {
    public static PolicyProcessingItemResponse from(PolicyProcessingItemResult r) {
        return new PolicyProcessingItemResponse(
            r.policyId(), r.title(), r.region(), r.completeness(), r.stepStatuses(),
            AttachmentSummaryResponse.from(r.attachments()),
            ReferenceSummaryResponse.from(r.references()),
            r.updatedAt()
        );
    }
}
```

`PolicyProcessingListResponse.java`:
```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import java.util.List;

public record PolicyProcessingListResponse(
    long totalCount, int page, int size,
    List<PolicyProcessingItemResponse> items
) {
    public static PolicyProcessingListResponse from(PolicyProcessingListResult r) {
        return new PolicyProcessingListResponse(
            r.totalCount(), r.page(), r.size(),
            r.items().stream().map(PolicyProcessingItemResponse::from).toList()
        );
    }
}
```

(나머지 record 도 같은 패턴 — `from(Result)` static 메서드.)

- [ ] **Step 2: AdminPolicyProcessingApi 인터페이스 (Swagger)**

```java
package com.youthfit.admin.presentation.api;

import com.youthfit.admin.presentation.dto.request.ReprocessPolicyRequest;
import com.youthfit.admin.presentation.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "어드민 - 정책 처리 현황", description = "정책 5단계 진행 + 첨부 임베딩 + 참고 사이트 fetch 결과 대시보드")
public interface AdminPolicyProcessingApi {

    @Operation(summary = "정책 처리 현황 목록", description = "검색·필터·정렬·페이징.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)")
    })
    ResponseEntity<PolicyProcessingListResponse> getPolicies(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String region,
        @RequestParam(defaultValue = "ALL") String filter,
        @RequestParam(defaultValue = "UPDATED_DESC") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    );

    @Operation(summary = "정책 처리 현황 KPI", description = "완전/부분/미흡/최근24h 카운트.")
    ResponseEntity<PolicyProcessingStatsResponse> getStats();

    @Operation(summary = "정책 처리 현황 상세", description = "5단계 + 첨부별 + 참조별 상세 (펼침 영역).")
    @ApiResponses({
        @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<PolicyProcessingDetailResponse> getDetail(@PathVariable Long id);

    @Operation(summary = "단계 재실행", description = "ENRICHMENT/GUIDE/RULE/RAG_INDEXING 중 1단계 재실행. INGESTION 은 미지원 (400).")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
        @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)"),
        @ApiResponse(responseCode = "409", description = "이미 존재하는 리소스입니다 (YF-005)")
    })
    ResponseEntity<ReprocessResponse> retryStep(
        @PathVariable Long id,
        @Parameter(description = "재실행할 단계") @PathVariable String step
    );

    @Operation(summary = "첨부 1건 임베딩 재실행")
    ResponseEntity<ReprocessResponse> reindexAttachment(@PathVariable Long id, @PathVariable Long attachmentId);

    @Operation(summary = "정책의 모든 첨부 임베딩 재실행")
    ResponseEntity<ReprocessResponse> reindexAllAttachments(@PathVariable Long id);

    @Operation(summary = "정책 RAG 본문 재인덱싱")
    ResponseEntity<ReprocessResponse> reindexRag(@PathVariable Long id);

    @Operation(summary = "전체 재처리", description = "ENRICHMENT/GUIDE/RULE/RAG 모두 재실행 큐잉.")
    ResponseEntity<ReprocessResponse> reprocess(
        @PathVariable Long id,
        @RequestBody ReprocessPolicyRequest request
    );
}
```

- [ ] **Step 3: 컴파일 확인 + 커밋**

Run: `cd backend && ./gradlew compileJava`
Expected: 성공.

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/api/AdminPolicyProcessingApi.java backend/src/main/java/com/youthfit/admin/presentation/dto/response/ backend/src/main/java/com/youthfit/admin/presentation/dto/request/
git commit -m "feat: AdminPolicyProcessingApi Swagger 인터페이스 + Response DTO record"
```

## Task 9: AdminPolicyProcessingController (조회 3종만)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java`
- Test: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingControllerTest.java`

- [ ] **Step 1: MockMvc 테스트 작성 (목록·KPI·상세)**

```java
@WebMvcTest(AdminPolicyProcessingController.class)
class AdminPolicyProcessingControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminPolicyProcessingService service;

    @Test
    void getPolicies_returns200WithList() throws Exception {
        when(service.findProcessingPolicies(any())).thenReturn(
            new PolicyProcessingListResult(1, 0, 50, List.of(
                new PolicyProcessingItemResult(100L, "월세 지원", "서울",
                    PolicyProcessingCompleteness.COMPLETE,
                    Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                    new AttachmentSummaryResult(0, 0, 0),
                    ReferenceSummaryResult.placeholder(),
                    LocalDateTime.now())
            ))
        );

        mockMvc.perform(get("/admin/policies/processing").param("filter", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].policyId").value(100))
            .andExpect(jsonPath("$.items[0].completeness").value("COMPLETE"));
    }

    @Test
    void getStats_returns200WithCounts() throws Exception {
        when(service.findProcessingStats()).thenReturn(
            new PolicyProcessingStatsResult(247, 182, 51, 14, 8)
        );

        mockMvc.perform(get("/admin/policies/processing/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(247))
            .andExpect(jsonPath("$.completeCount").value(182));
    }

    @Test
    void getDetail_returns404WhenPolicyNotFound() throws Exception {
        when(service.findProcessingDetail(999L))
            .thenThrow(new YouthFitException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/admin/policies/999/processing"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Controller 구현**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.dto.*;
import com.youthfit.admin.application.service.AdminPolicyProcessingService;
import com.youthfit.admin.presentation.api.AdminPolicyProcessingApi;
import com.youthfit.admin.presentation.dto.request.ReprocessPolicyRequest;
import com.youthfit.admin.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/policies/processing")
@RequiredArgsConstructor
public class AdminPolicyProcessingController implements AdminPolicyProcessingApi {

    private final AdminPolicyProcessingService service;

    @GetMapping
    @Override
    public ResponseEntity<PolicyProcessingListResponse> getPolicies(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String region,
        @RequestParam(defaultValue = "ALL") String filter,
        @RequestParam(defaultValue = "UPDATED_DESC") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        PolicyProcessingListCommand command = new PolicyProcessingListCommand(
            q, region,
            PolicyProcessingFilter.valueOf(filter),
            PolicyProcessingSort.valueOf(sort),
            page, size
        );
        return ResponseEntity.ok(PolicyProcessingListResponse.from(service.findProcessingPolicies(command)));
    }

    @GetMapping("/stats")
    @Override
    public ResponseEntity<PolicyProcessingStatsResponse> getStats() {
        return ResponseEntity.ok(PolicyProcessingStatsResponse.from(service.findProcessingStats()));
    }

    @GetMapping("/{id}")
    @Override
    public ResponseEntity<PolicyProcessingDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(PolicyProcessingDetailResponse.from(service.findProcessingDetail(id)));
    }

    // Phase 2 에서 추가
    @Override public ResponseEntity<ReprocessResponse> retryStep(Long id, String step) { throw new UnsupportedOperationException("Phase 2"); }
    @Override public ResponseEntity<ReprocessResponse> reindexAttachment(Long id, Long attachmentId) { throw new UnsupportedOperationException("Phase 2"); }
    @Override public ResponseEntity<ReprocessResponse> reindexAllAttachments(Long id) { throw new UnsupportedOperationException("Phase 2"); }
    @Override public ResponseEntity<ReprocessResponse> reindexRag(Long id) { throw new UnsupportedOperationException("Phase 2"); }
    @Override public ResponseEntity<ReprocessResponse> reprocess(Long id, ReprocessPolicyRequest request) { throw new UnsupportedOperationException("Phase 2"); }
}
```

- [ ] **Step 3: 테스트 통과 확인 + 커밋**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingControllerTest`
Expected: 3개 PASS.

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java backend/src/test/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingControllerTest.java
git commit -m "feat: AdminPolicyProcessingController 조회 endpoint 3종 (목록/KPI/상세)"
```

---

# Phase 2 — 백엔드 재실행 endpoint

5종 재실행 endpoint 가 동작하고, 호출 시 `PolicyProcessingStepService` 로 step 기록.

## Task 10: 단계 재실행 endpoint

**Files:**
- Modify: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java`
- Modify: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java`
- Test: `backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java`

- [ ] **Step 1: 실패 테스트 (Service)**

```java
@Test
void retryStep_rejectsIngestion() {
    assertThatThrownBy(() -> service.retryStep(100L, ProcessingStep.INGESTION))
        .isInstanceOf(YouthFitException.class)
        .hasMessageContaining("INGESTION");
}

@Test
void retryStep_invokesRagIndexingForRagStep() {
    givenPolicy(100L, "월세 지원");
    when(ragIndexingService.indexPolicyDocument(any())).thenReturn(IndexingResult.success(...));

    ReprocessResult result = service.retryStep(100L, ProcessingStep.RAG_INDEXING);

    verify(stepService).markStarted(100L, ProcessingStep.RAG_INDEXING);
    verify(ragIndexingService).indexPolicyDocument(any());
    assertThat(result.queued()).isTrue();
}
```

- [ ] **Step 2: 서비스 메서드 구현**

`AdminPolicyProcessingService` 에 의존성 추가:
```java
private final PolicyProcessingStepService stepService;
private final RagIndexingService ragIndexingService;
private final AttachmentReindexService attachmentReindexService;
private final ApplicationEventPublisher eventPublisher; // GUIDE/RULE 재실행용 이벤트
```

```java
@Transactional
public ReprocessResult retryStep(Long policyId, ProcessingStep step) {
    if (step == ProcessingStep.INGESTION) {
        throw new YouthFitException(ErrorCode.INVALID_INPUT, "INGESTION 단계는 n8n 재크롤이 필요해 어드민에서 재실행할 수 없습니다.");
    }
    Policy policy = policyRepository.findById(policyId)
        .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));

    Long stepRowId = stepService.markStarted(policyId, step);
    try {
        switch (step) {
            case RAG_INDEXING -> ragIndexingService.indexPolicyDocument(
                new IndexPolicyDocumentCommand(policyId, policy.getBody(), policy.getEnrichment())
            );
            case GUIDE -> eventPublisher.publishEvent(new GuideGenerationRequestedEvent(policyId));
            case RULE -> eventPublisher.publishEvent(new EligibilityRuleGenerationRequestedEvent(policyId));
            case ENRICHMENT -> eventPublisher.publishEvent(new EnrichmentRequestedEvent(policyId));
            default -> throw new YouthFitException(ErrorCode.INVALID_INPUT);
        }
        stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
        throw e;
    }
    return new ReprocessResult(true, List.of(stepRowId), "재실행 큐잉됨");
}
```

(`GuideGenerationRequestedEvent`, `EligibilityRuleGenerationRequestedEvent`, `EnrichmentRequestedEvent` 가 존재하지 않으면 신규 생성하거나, 기존 listener trigger 패턴 확인 후 맞춤. Phase A 의 listener 들이 이미 어떤 이벤트로 trigger 되는지 grep 으로 확인.)

- [ ] **Step 3: ReprocessResult application DTO 신규**

`backend/src/main/java/com/youthfit/admin/application/dto/ReprocessResult.java`:
```java
package com.youthfit.admin.application.dto;
import java.util.List;
public record ReprocessResult(boolean queued, List<Long> stepIds, String message) {}
```

- [ ] **Step 4: ReprocessResponse presentation DTO 신규**

`backend/src/main/java/com/youthfit/admin/presentation/dto/response/ReprocessResponse.java`:
```java
package com.youthfit.admin.presentation.dto.response;
import com.youthfit.admin.application.dto.ReprocessResult;
import java.util.List;
public record ReprocessResponse(boolean queued, List<Long> stepIds, String message) {
    public static ReprocessResponse from(ReprocessResult r) {
        return new ReprocessResponse(r.queued(), r.stepIds(), r.message());
    }
}
```

- [ ] **Step 5: Controller endpoint 활성화**

```java
@PostMapping("/{id}/steps/{step}/retry")
@Override
public ResponseEntity<ReprocessResponse> retryStep(@PathVariable Long id, @PathVariable String step) {
    ProcessingStep processingStep = ProcessingStep.valueOf(step.toUpperCase());
    return ResponseEntity.ok(ReprocessResponse.from(service.retryStep(id, processingStep)));
}
```

- [ ] **Step 6: 테스트 통과 확인 + 커밋**

Run: `cd backend && ./gradlew test --tests AdminPolicyProcessingServiceTest --tests AdminPolicyProcessingControllerTest`
Expected: PASS.

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java backend/src/main/java/com/youthfit/admin/application/dto/ReprocessResult.java backend/src/main/java/com/youthfit/admin/presentation/dto/response/ReprocessResponse.java backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat: 단계별 재실행 endpoint POST /admin/policies/{id}/steps/{step}/retry"
```

## Task 11: 첨부 1건 임베딩 재실행

**Files:**
- Modify: `backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java`
- Modify: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java`

- [ ] **Step 1: 실패 테스트**

```java
@Test
void reindexAttachment_invokesAttachmentReindex() {
    when(attachmentRepository.findById(401L)).thenReturn(Optional.of(attachmentMock(401L, "안내문.pdf", AttachmentStatus.EXTRACTED, 100L)));

    ReprocessResult result = service.reindexAttachment(100L, 401L);

    verify(attachmentReindexService).reindex(100L); // 정책 단위로 일괄 재인덱싱 (기존 시그니처)
    assertThat(result.queued()).isTrue();
}

@Test
void reindexAttachment_throws404WhenNotBelongToPolicy() {
    when(attachmentRepository.findById(401L)).thenReturn(Optional.of(attachmentMock(401L, "안내문.pdf", AttachmentStatus.EXTRACTED, 999L)));

    assertThatThrownBy(() -> service.reindexAttachment(100L, 401L))
        .isInstanceOf(YouthFitException.class);
}
```

- [ ] **Step 2: 서비스 구현**

```java
@Transactional
public ReprocessResult reindexAttachment(Long policyId, Long attachmentId) {
    PolicyAttachment attachment = attachmentRepository.findById(attachmentId)
        .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    if (!attachment.getPolicyId().equals(policyId)) {
        throw new YouthFitException(ErrorCode.NOT_FOUND);
    }
    Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
    attachmentReindexService.reindex(policyId); // 정책 전체 첨부 재인덱싱 (기존 시그니처)
    stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, "첨부 1건 재실행 트리거", null);
    return new ReprocessResult(true, List.of(stepRowId), "첨부 재인덱싱 큐잉됨");
}
```

> 주의: `AttachmentReindexService.reindex(policyId)` 는 정책 단위 동작이라 첨부 1건만 재인덱싱하는 API 가 없다. MVP 에선 1건 트리거 시에도 정책 단위 재인덱싱이 이루어지며, 이를 UX 에 명시 (펼침 영역 hover tooltip 으로 "정책의 첨부 전체가 다시 인덱싱됩니다").

- [ ] **Step 3: Controller endpoint 활성화**

```java
@PostMapping("/{id}/attachments/{attachmentId}/reindex")
@Override
public ResponseEntity<ReprocessResponse> reindexAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
    return ResponseEntity.ok(ReprocessResponse.from(service.reindexAttachment(id, attachmentId)));
}
```

- [ ] **Step 4: 테스트 통과 + 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminPolicyProcessingService.java backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPolicyProcessingController.java backend/src/test/java/com/youthfit/admin/application/service/AdminPolicyProcessingServiceTest.java
git commit -m "feat: 첨부 1건 임베딩 재실행 endpoint (내부적으로 정책 단위 reindex)"
```

## Task 12: 정책의 모든 첨부 재인덱싱

**Files:** 동일

- [ ] **Step 1: 서비스 메서드 + Controller endpoint**

```java
@Transactional
public ReprocessResult reindexAllAttachments(Long policyId) {
    policyRepository.findById(policyId).orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
    attachmentReindexService.reindex(policyId);
    stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, "전체 첨부 재인덱싱 트리거", null);
    return new ReprocessResult(true, List.of(stepRowId), "전체 첨부 재인덱싱 큐잉됨");
}
```

Controller:
```java
@PostMapping("/{id}/attachments/reindex")
@Override
public ResponseEntity<ReprocessResponse> reindexAllAttachments(@PathVariable Long id) {
    return ResponseEntity.ok(ReprocessResponse.from(service.reindexAllAttachments(id)));
}
```

- [ ] **Step 2: 테스트 + 커밋**

```java
@Test
void reindexAllAttachments_invokesPolicyReindex() {
    givenPolicy(100L, "월세 지원");
    ReprocessResult result = service.reindexAllAttachments(100L);
    verify(attachmentReindexService).reindex(100L);
    assertThat(result.queued()).isTrue();
}
```

```bash
git commit -m "feat: 정책 단위 첨부 임베딩 재실행 endpoint"
```

## Task 13: RAG 본문 재인덱싱

**Files:** 동일

- [ ] **Step 1: 서비스 메서드 + Controller**

```java
@Transactional
public ReprocessResult reindexRag(Long policyId) {
    Policy policy = policyRepository.findById(policyId).orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
    try {
        ragIndexingService.indexPolicyDocument(
            new IndexPolicyDocumentCommand(policyId, policy.getBody(), policy.getEnrichment())
        );
        stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
    } catch (Exception e) {
        stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
        throw e;
    }
    return new ReprocessResult(true, List.of(stepRowId), "RAG 본문 재인덱싱 완료");
}
```

Controller:
```java
@PostMapping("/{id}/rag/reindex")
@Override
public ResponseEntity<ReprocessResponse> reindexRag(@PathVariable Long id) {
    return ResponseEntity.ok(ReprocessResponse.from(service.reindexRag(id)));
}
```

- [ ] **Step 2: 테스트 + 커밋**

```bash
git commit -m "feat: RAG 본문 재인덱싱 endpoint"
```

## Task 14: 전체 재처리 endpoint (reason 입력 필수)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/request/ReprocessPolicyRequest.java`
- Modify: 서비스 + 컨트롤러

- [ ] **Step 1: Request record**

```java
package com.youthfit.admin.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReprocessPolicyRequest(
    @NotBlank(message = "재처리 사유는 필수입니다.") String reason
) {}
```

- [ ] **Step 2: 서비스 메서드**

```java
@Transactional
public ReprocessResult reprocess(Long policyId, String reason) {
    Policy policy = policyRepository.findById(policyId).orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    List<Long> stepIds = new ArrayList<>();
    // ENRICHMENT → GUIDE → RULE → RAG 순서로 큐잉
    for (ProcessingStep step : List.of(
        ProcessingStep.ENRICHMENT, ProcessingStep.GUIDE,
        ProcessingStep.RULE, ProcessingStep.RAG_INDEXING
    )) {
        Long id = stepService.markStarted(policyId, step);
        stepIds.add(id);
    }
    // 실제 trigger: 적절한 이벤트 발행 (Phase 1 의 trigger 패턴 동일)
    eventPublisher.publishEvent(new PolicyReprocessRequestedEvent(policyId, reason, stepIds));
    return new ReprocessResult(true, stepIds, "전체 재처리 큐잉됨 (사유: " + reason + ")");
}
```

(`PolicyReprocessRequestedEvent` 가 없으면 신규 생성, 또는 기존 enrichment/guide/rule 이벤트 4개 발행.)

- [ ] **Step 3: Controller + Validation**

```java
@PostMapping("/{id}/reprocess")
@Override
public ResponseEntity<ReprocessResponse> reprocess(
    @PathVariable Long id,
    @Valid @RequestBody ReprocessPolicyRequest request
) {
    return ResponseEntity.ok(ReprocessResponse.from(service.reprocess(id, request.reason())));
}
```

- [ ] **Step 4: 테스트 + 커밋**

```java
@Test
void reprocess_requiresReasonAndQueuesAllSteps() throws Exception {
    when(service.reprocess(eq(100L), eq("LLM 모델 업데이트"))).thenReturn(
        new ReprocessResult(true, List.of(1L, 2L, 3L, 4L), "전체 재처리 큐잉됨")
    );

    mockMvc.perform(post("/admin/policies/processing/100/reprocess")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"LLM 모델 업데이트\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stepIds.length()").value(4));
}

@Test
void reprocess_rejectsEmptyReason() throws Exception {
    mockMvc.perform(post("/admin/policies/processing/100/reprocess")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest());
}
```

```bash
git commit -m "feat: 전체 재처리 endpoint (reason 입력 필수, 4단계 일괄 큐잉)"
```

---

# Phase 3 — 프론트엔드 데이터 layer

타입·API 호출·훅 — 화면 없이 데이터 흐름만 동작.

## Task 15: TypeScript 타입 정의

**Files:**
- Create: `frontend/src/types/adminPolicyProcessing.ts`

- [ ] **Step 1: 타입 정의**

```typescript
export type ProcessingStep = 'INGESTION' | 'ENRICHMENT' | 'GUIDE' | 'RULE' | 'RAG_INDEXING';
export type ProcessingStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'SKIPPED' | 'FAILED';
export type Completeness = 'COMPLETE' | 'PARTIAL' | 'INCOMPLETE';
export type Filter =
  | 'ALL' | 'INCOMPLETE' | 'PARTIAL'
  | 'RAG_FAILED' | 'ATTACHMENT_EMBEDDING_MISSING'
  | 'REFERENCE_FETCH_FAILED' | 'GUIDE_RULE_FAILED' | 'RECENT_24H';
export type Sort = 'UPDATED_DESC' | 'COMPLETENESS_ASC' | 'ID_ASC';

export interface AttachmentSummary {
  total: number;
  extracted: number;
  embedded: number;
}

export interface ReferenceSummary {
  total: number;
  succeeded: number;
}

export interface PolicyProcessingItem {
  policyId: number;
  title: string;
  region: string;
  completeness: Completeness;
  stepStatuses: Partial<Record<ProcessingStep, ProcessingStatus>>;
  attachments: AttachmentSummary;
  references: ReferenceSummary;
  updatedAt: string;
}

export interface PolicyProcessingList {
  totalCount: number;
  page: number;
  size: number;
  items: PolicyProcessingItem[];
}

export interface PolicyProcessingStats {
  totalCount: number;
  completeCount: number;
  partialCount: number;
  incompleteCount: number;
  recent24hCount: number;
}

export interface StepDetail {
  step: ProcessingStep;
  status: ProcessingStatus;
  durationMs: number | null;
  attempt: number;
  reason: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface AttachmentDetail {
  attachmentId: number;
  filename: string;
  extractionStatus: 'PENDING' | 'DOWNLOADING' | 'DOWNLOADED' | 'EXTRACTING' | 'EXTRACTED' | 'FAILED' | 'SKIPPED';
  embedded: boolean;
}

export interface ReferenceDetail {
  url: string;
  status: string;
  chunkCount: number;
}

export interface PolicyProcessingDetail {
  policyId: number;
  title: string;
  completeness: Completeness;
  steps: StepDetail[];
  attachments: AttachmentDetail[];
  references: ReferenceDetail[];
}

export interface ReprocessResult {
  queued: boolean;
  stepIds: number[];
  message: string;
}

export interface PolicyProcessingListParams {
  q?: string;
  region?: string;
  filter?: Filter;
  sort?: Sort;
  page?: number;
  size?: number;
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/types/adminPolicyProcessing.ts
git commit -m "feat(frontend): admin 정책 처리 현황 TypeScript 타입 정의"
```

## Task 16: API 클라이언트 함수

**Files:**
- Create: `frontend/src/apis/adminPolicyProcessing.api.ts`

- [ ] **Step 1: API 함수**

```typescript
import { client } from './client';
import type {
  PolicyProcessingList, PolicyProcessingDetail, PolicyProcessingStats,
  PolicyProcessingListParams, ReprocessResult, ProcessingStep
} from '@/types/adminPolicyProcessing';

const BASE = 'admin/policies/processing';

export const adminPolicyProcessingApi = {
  list: (params: PolicyProcessingListParams) =>
    client.get(BASE, { searchParams: cleanParams(params) }).json<PolicyProcessingList>(),

  stats: () =>
    client.get(`${BASE}/stats`).json<PolicyProcessingStats>(),

  detail: (policyId: number) =>
    client.get(`${BASE}/${policyId}`).json<PolicyProcessingDetail>(),

  retryStep: (policyId: number, step: ProcessingStep) =>
    client.post(`${BASE}/${policyId}/steps/${step}/retry`).json<ReprocessResult>(),

  reindexAttachment: (policyId: number, attachmentId: number) =>
    client.post(`${BASE}/${policyId}/attachments/${attachmentId}/reindex`).json<ReprocessResult>(),

  reindexAllAttachments: (policyId: number) =>
    client.post(`${BASE}/${policyId}/attachments/reindex`).json<ReprocessResult>(),

  reindexRag: (policyId: number) =>
    client.post(`${BASE}/${policyId}/rag/reindex`).json<ReprocessResult>(),

  reprocess: (policyId: number, reason: string) =>
    client.post(`${BASE}/${policyId}/reprocess`, { json: { reason } }).json<ReprocessResult>(),
};

function cleanParams(params: PolicyProcessingListParams): Record<string, string> {
  const out: Record<string, string> = {};
  if (params.q) out.q = params.q;
  if (params.region) out.region = params.region;
  if (params.filter) out.filter = params.filter;
  if (params.sort) out.sort = params.sort;
  if (params.page !== undefined) out.page = String(params.page);
  if (params.size !== undefined) out.size = String(params.size);
  return out;
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/apis/adminPolicyProcessing.api.ts
git commit -m "feat(frontend): adminPolicyProcessing API 클라이언트 함수"
```

## Task 17: TanStack Query 훅 3종

**Files:**
- Create: `frontend/src/hooks/queries/useAdminPolicyProcessingList.ts`
- Create: `frontend/src/hooks/queries/useAdminPolicyProcessingStats.ts`
- Create: `frontend/src/hooks/queries/useAdminPolicyProcessingDetail.ts`

- [ ] **Step 1: Query 훅 3개**

`useAdminPolicyProcessingList.ts`:
```typescript
import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import type { PolicyProcessingListParams } from '@/types/adminPolicyProcessing';

export const adminPolicyProcessingKeys = {
  all: ['admin-policy-processing'] as const,
  list: (params: PolicyProcessingListParams) =>
    [...adminPolicyProcessingKeys.all, 'list', params] as const,
  stats: () => [...adminPolicyProcessingKeys.all, 'stats'] as const,
  detail: (policyId: number) =>
    [...adminPolicyProcessingKeys.all, 'detail', policyId] as const,
};

export function useAdminPolicyProcessingList(params: PolicyProcessingListParams) {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.list(params),
    queryFn: () => adminPolicyProcessingApi.list(params),
    placeholderData: (prev) => prev,
  });
}
```

`useAdminPolicyProcessingStats.ts`:
```typescript
import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from './useAdminPolicyProcessingList';

export function useAdminPolicyProcessingStats() {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.stats(),
    queryFn: () => adminPolicyProcessingApi.stats(),
  });
}
```

`useAdminPolicyProcessingDetail.ts`:
```typescript
import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from './useAdminPolicyProcessingList';

export function useAdminPolicyProcessingDetail(policyId: number | null) {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.detail(policyId ?? 0),
    queryFn: () => adminPolicyProcessingApi.detail(policyId!),
    enabled: policyId !== null,
  });
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/hooks/queries/useAdminPolicyProcessing*.ts
git commit -m "feat(frontend): admin 정책 처리 현황 Query 훅 3종 (목록/KPI/상세)"
```

---

# Phase 4 — 프론트엔드 UI 컴포넌트

화면 컴포넌트 — KPI / 필터 / 표 / 펼침. 재실행은 Phase 5.

## Task 18: PolicyProcessingKpiCards

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/PolicyProcessingKpiCards.tsx`
- Test: `frontend/src/pages/admin/policy-processing/__tests__/PolicyProcessingKpiCards.test.tsx`

- [ ] **Step 1: 테스트 작성**

```typescript
import { render, screen } from '@testing-library/react';
import { PolicyProcessingKpiCards } from '../PolicyProcessingKpiCards';

describe('PolicyProcessingKpiCards', () => {
  const stats = { totalCount: 247, completeCount: 182, partialCount: 51, incompleteCount: 14, recent24hCount: 8 };

  it('renders all four KPIs with values', () => {
    render(<PolicyProcessingKpiCards stats={stats} />);
    expect(screen.getByText('182')).toBeInTheDocument();
    expect(screen.getByText('51')).toBeInTheDocument();
    expect(screen.getByText('14')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('shows percentage for complete count', () => {
    render(<PolicyProcessingKpiCards stats={stats} />);
    expect(screen.getByText(/74%/)).toBeInTheDocument(); // 182/247 = 73.7%
  });
});
```

- [ ] **Step 2: 컴포넌트 구현**

```typescript
import type { PolicyProcessingStats } from '@/types/adminPolicyProcessing';

interface Props {
  stats: PolicyProcessingStats;
}

export function PolicyProcessingKpiCards({ stats }: Props) {
  const completePercent = stats.totalCount > 0
    ? Math.round((stats.completeCount / stats.totalCount) * 100)
    : 0;

  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4 mb-4">
      <KpiCard label="완전" value={stats.completeCount} suffix={`/ ${stats.totalCount} (${completePercent}%)`} tone="ok" />
      <KpiCard label="부분" value={stats.partialCount} suffix={`/ ${stats.totalCount}`} tone="warn" />
      <KpiCard label="미흡" value={stats.incompleteCount} suffix={`/ ${stats.totalCount}`} tone="fail" />
      <KpiCard label="최근 24h 적재" value={stats.recent24hCount} suffix="건" tone="neutral" />
    </div>
  );
}

function KpiCard({ label, value, suffix, tone }: { label: string; value: number; suffix: string; tone: 'ok' | 'warn' | 'fail' | 'neutral'; }) {
  const valueColor = {
    ok: 'text-green-500', warn: 'text-amber-500', fail: 'text-red-500', neutral: 'text-white',
  }[tone];
  return (
    <div className="rounded border border-slate-700 bg-slate-900 p-3">
      <div className="text-xs uppercase tracking-wider text-blue-300">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${valueColor}`}>
        {value} <span className="text-sm font-normal text-slate-500">{suffix}</span>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 테스트 통과 + 커밋**

Run: `cd frontend && npm run test -- PolicyProcessingKpiCards`
Expected: PASS.

```bash
git add frontend/src/pages/admin/policy-processing/PolicyProcessingKpiCards.tsx frontend/src/pages/admin/policy-processing/__tests__/PolicyProcessingKpiCards.test.tsx
git commit -m "feat(frontend): PolicyProcessingKpiCards (완전/부분/미흡/최근24h)"
```

## Task 19: PolicyProcessingFilters (검색·정렬·칩)

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/PolicyProcessingFilters.tsx`
- Test: `frontend/src/pages/admin/policy-processing/__tests__/PolicyProcessingFilters.test.tsx`

- [ ] **Step 1: 테스트**

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { PolicyProcessingFilters } from '../PolicyProcessingFilters';

describe('PolicyProcessingFilters', () => {
  const baseParams = { filter: 'ALL' as const, sort: 'UPDATED_DESC' as const, page: 0, size: 50 };

  it('calls onChange when chip clicked', () => {
    const onChange = vi.fn();
    render(<PolicyProcessingFilters params={baseParams} onChange={onChange} chipCounts={{ ALL: 247, INCOMPLETE: 14 }} />);
    fireEvent.click(screen.getByText('미흡만 14'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ filter: 'INCOMPLETE' }));
  });

  it('calls onChange when search input changes', () => {
    const onChange = vi.fn();
    render(<PolicyProcessingFilters params={baseParams} onChange={onChange} chipCounts={{}} />);
    fireEvent.change(screen.getByPlaceholderText(/검색/), { target: { value: '월세' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ q: '월세' }));
  });
});
```

- [ ] **Step 2: 컴포넌트 구현**

```typescript
import { useState } from 'react';
import type { Filter, PolicyProcessingListParams, Sort } from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

interface Props {
  params: PolicyProcessingListParams;
  onChange: (next: PolicyProcessingListParams) => void;
  chipCounts: Partial<Record<Filter, number>>;
}

const CHIPS: Array<{ key: Filter; label: string; tone: 'default' | 'fail' | 'warn' }> = [
  { key: 'ALL', label: '전체', tone: 'default' },
  { key: 'INCOMPLETE', label: '미흡만', tone: 'fail' },
  { key: 'PARTIAL', label: '부분만', tone: 'warn' },
  { key: 'RAG_FAILED', label: 'RAG 본문 FAILED', tone: 'default' },
  { key: 'ATTACHMENT_EMBEDDING_MISSING', label: '첨부 임베딩 누락', tone: 'default' },
  { key: 'REFERENCE_FETCH_FAILED', label: '참조 fetch 실패', tone: 'default' },
  { key: 'GUIDE_RULE_FAILED', label: 'GUIDE/RULE 실패', tone: 'default' },
  { key: 'RECENT_24H', label: '최근 24h', tone: 'default' },
];

export function PolicyProcessingFilters({ params, onChange, chipCounts }: Props) {
  return (
    <div className="space-y-3 mb-4">
      <div className="flex items-center gap-2">
        <input
          type="text"
          className="rounded border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm flex-1 max-w-xs"
          placeholder="정책 ID, 제목 검색…"
          defaultValue={params.q ?? ''}
          onChange={(e) => onChange({ ...params, q: e.target.value || undefined, page: 0 })}
        />
        <select
          className="rounded border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm"
          value={params.sort ?? 'UPDATED_DESC'}
          onChange={(e) => onChange({ ...params, sort: e.target.value as Sort })}
        >
          <option value="UPDATED_DESC">업데이트 최신순</option>
          <option value="COMPLETENESS_ASC">완성도 미흡순</option>
          <option value="ID_ASC">ID 순</option>
        </select>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-xs text-slate-500">빠른 필터</span>
        {CHIPS.map(chip => {
          const active = (params.filter ?? 'ALL') === chip.key;
          const count = chipCounts[chip.key];
          const disabled = chip.key === 'REFERENCE_FETCH_FAILED'; // Phase D 후 활성
          return (
            <button
              key={chip.key}
              type="button"
              disabled={disabled}
              onClick={() => onChange({ ...params, filter: chip.key, page: 0 })}
              className={cn(
                'rounded-full border px-3 py-1 text-xs',
                active && chip.tone === 'fail' && 'border-red-500 bg-red-900/30 text-red-400',
                active && chip.tone === 'warn' && 'border-amber-500 bg-amber-900/30 text-amber-400',
                active && chip.tone === 'default' && 'border-green-500 bg-green-900/30 text-green-400',
                !active && 'border-slate-700 bg-slate-900 text-slate-400',
                disabled && 'opacity-50 cursor-not-allowed'
              )}
            >
              {chip.label}{count !== undefined && ` ${count}`}
            </button>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 테스트 통과 + 커밋**

```bash
git commit -m "feat(frontend): PolicyProcessingFilters (검색·정렬·칩 8개)"
```

## Task 20: PolicyProcessingTable (1차 표)

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/PolicyProcessingTable.tsx`
- Test: `frontend/src/pages/admin/policy-processing/__tests__/PolicyProcessingTable.test.tsx`

- [ ] **Step 1: 테스트**

```typescript
describe('PolicyProcessingTable', () => {
  it('renders 5 step dots per row', () => {
    const items = [{
      policyId: 100, title: '월세 지원', region: '서울',
      completeness: 'COMPLETE' as const,
      stepStatuses: { INGESTION: 'SUCCESS', ENRICHMENT: 'SUCCESS', GUIDE: 'SUCCESS', RULE: 'SUCCESS', RAG_INDEXING: 'SUCCESS' },
      attachments: { total: 3, extracted: 3, embedded: 3 },
      references: { total: 0, succeeded: 0 },
      updatedAt: '2026-05-29T03:00:00Z',
    }];
    render(<PolicyProcessingTable items={items} expandedIds={new Set()} onToggle={() => {}} />);
    expect(screen.getAllByTestId('step-dot')).toHaveLength(5);
    expect(screen.getByText('월세 지원')).toBeInTheDocument();
  });

  it('toggles expansion on row click', () => {
    const onToggle = vi.fn();
    render(<PolicyProcessingTable items={[mockItem(100)]} expandedIds={new Set()} onToggle={onToggle} />);
    fireEvent.click(screen.getByText('월세 지원'));
    expect(onToggle).toHaveBeenCalledWith(100);
  });
});
```

- [ ] **Step 2: 컴포넌트 구현**

```typescript
import type { PolicyProcessingItem, ProcessingStatus, ProcessingStep } from '@/types/adminPolicyProcessing';
import { CompletenessBadge } from './CompletenessBadge';
import { cn } from '@/lib/cn';

const STEP_ORDER: ProcessingStep[] = ['INGESTION', 'ENRICHMENT', 'GUIDE', 'RULE', 'RAG_INDEXING'];

interface Props {
  items: PolicyProcessingItem[];
  expandedIds: Set<number>;
  onToggle: (policyId: number) => void;
  renderDetail?: (policyId: number) => React.ReactNode;
}

export function PolicyProcessingTable({ items, expandedIds, onToggle, renderDetail }: Props) {
  return (
    <table className="w-full text-xs border-collapse">
      <thead>
        <tr className="border-b border-slate-700 text-blue-300">
          <th className="w-6 p-2 text-left"></th>
          <th className="p-2 text-left">ID</th>
          <th className="p-2 text-left">제목</th>
          <th className="p-2 text-left">완성도</th>
          <th className="p-2 text-left">5단계</th>
          <th className="p-2 text-left">첨부 (임베딩/추출/총)</th>
          <th className="p-2 text-left">참조</th>
          <th className="p-2 text-left">업데이트</th>
        </tr>
      </thead>
      <tbody>
        {items.map(item => (
          <>
            <tr
              key={item.policyId}
              className={cn('border-b border-slate-800 hover:bg-slate-900 cursor-pointer',
                expandedIds.has(item.policyId) && 'bg-slate-900')}
              onClick={() => onToggle(item.policyId)}
            >
              <td className="p-2">{expandedIds.has(item.policyId) ? '▾' : '▸'}</td>
              <td className="p-2">{item.policyId}</td>
              <td className="p-2">{item.title}</td>
              <td className="p-2"><CompletenessBadge value={item.completeness} /></td>
              <td className="p-2"><StepDots statuses={item.stepStatuses} /></td>
              <td className={cn('p-2', attachmentTone(item.attachments))}>
                {item.attachments.total > 0
                  ? `${item.attachments.embedded}/${item.attachments.extracted}/${item.attachments.total}`
                  : '—'}
              </td>
              <td className="p-2">
                {item.references.total > 0
                  ? `${item.references.succeeded}/${item.references.total}`
                  : '—'}
              </td>
              <td className="p-2 text-slate-500">{formatRelative(item.updatedAt)}</td>
            </tr>
            {expandedIds.has(item.policyId) && renderDetail && (
              <tr className="bg-slate-950">
                <td colSpan={8} className="p-4">{renderDetail(item.policyId)}</td>
              </tr>
            )}
          </>
        ))}
      </tbody>
    </table>
  );
}

function StepDots({ statuses }: { statuses: Partial<Record<ProcessingStep, ProcessingStatus>> }) {
  return (
    <div className="flex gap-0.5">
      {STEP_ORDER.map(step => {
        const status = statuses[step];
        const tone =
          status === 'SUCCESS' ? 'bg-green-500'
          : status === 'FAILED' ? 'bg-red-500'
          : status === 'SKIPPED' ? 'bg-slate-500'
          : status === 'IN_PROGRESS' ? 'bg-blue-500 animate-pulse'
          : 'border border-slate-600';
        return (
          <span
            key={step}
            data-testid="step-dot"
            title={`${step}: ${status ?? '미실행'}`}
            className={cn('inline-block w-2 h-2 rounded-full', tone)}
          />
        );
      })}
    </div>
  );
}

function attachmentTone(a: { total: number; extracted: number; embedded: number }): string {
  if (a.total === 0) return 'text-slate-500';
  if (a.embedded === a.total) return 'text-green-500';
  if (a.embedded === 0) return 'text-red-500';
  return 'text-amber-500';
}

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 60) return `${min}m`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h`;
  return `${Math.floor(h / 24)}d`;
}
```

`CompletenessBadge.tsx` 도 신규 생성:
```typescript
import type { Completeness } from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

const STYLE: Record<Completeness, string> = {
  COMPLETE: 'bg-green-900/30 text-green-400',
  PARTIAL: 'bg-amber-900/30 text-amber-400',
  INCOMPLETE: 'bg-red-900/30 text-red-400',
};
const LABEL: Record<Completeness, string> = {
  COMPLETE: '완전', PARTIAL: '부분', INCOMPLETE: '미흡',
};

export function CompletenessBadge({ value }: { value: Completeness }) {
  return (
    <span className={cn('inline-block px-2 py-0.5 rounded text-[10px] font-semibold', STYLE[value])}>
      {LABEL[value]}
    </span>
  );
}
```

- [ ] **Step 3: 테스트 통과 + 커밋**

```bash
git commit -m "feat(frontend): PolicyProcessingTable + CompletenessBadge (1차 표 8컬럼 + 펼침)"
```

## Task 21: PolicyProcessingDetailPanel (펼침 영역 표 3개)

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/PolicyProcessingDetailPanel.tsx`
- Test: 동일 디렉토리

- [ ] **Step 1: 컴포넌트 구현 — 3개 표 + 액션 버튼 (action 은 Phase 5 에서 연결)**

```typescript
import { useAdminPolicyProcessingDetail } from '@/hooks/queries/useAdminPolicyProcessingDetail';
import type { StepDetail, AttachmentDetail, ReferenceDetail } from '@/types/adminPolicyProcessing';

interface Props {
  policyId: number;
  onAction?: {
    retryStep: (step: string) => void;
    reindexAttachment: (attachmentId: number) => void;
    reindexAllAttachments: () => void;
    reindexRag: () => void;
    reprocess: () => void;
  };
}

export function PolicyProcessingDetailPanel({ policyId, onAction }: Props) {
  const { data, isLoading } = useAdminPolicyProcessingDetail(policyId);

  if (isLoading) return <div className="text-xs text-slate-500">로딩 중…</div>;
  if (!data) return null;

  return (
    <div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <StepDetailTable steps={data.steps} onRetry={onAction?.retryStep} />
        <AttachmentDetailTable attachments={data.attachments} onReindex={onAction?.reindexAttachment} />
        <ReferenceDetailTable references={data.references} />
      </div>
      <div className="mt-3 flex gap-2 items-center">
        <span className="text-xs text-blue-300">통합 액션</span>
        <button onClick={onAction?.reindexAllAttachments} className="text-xs rounded border border-slate-700 bg-slate-900 px-3 py-1">첨부 임베딩 재인덱싱</button>
        <button onClick={onAction?.reindexRag} className="text-xs rounded border border-slate-700 bg-slate-900 px-3 py-1">RAG 본문 재인덱싱</button>
        <button onClick={onAction?.reprocess} className="text-xs rounded border border-slate-700 bg-slate-900 px-3 py-1">전체 재처리</button>
      </div>
    </div>
  );
}

function StepDetailTable({ steps, onRetry }: { steps: StepDetail[]; onRetry?: (step: string) => void }) {
  return (
    <div className="rounded border border-slate-700 overflow-hidden">
      <div className="bg-slate-800 px-3 py-1.5 text-[10px] uppercase tracking-wider text-blue-300">5단계 처리 이력</div>
      <table className="w-full text-[10px]">
        <thead><tr className="text-blue-300"><th className="p-1 text-left">단계</th><th className="p-1">STATUS</th><th className="p-1">소요</th><th className="p-1">시도</th><th className="p-1"></th></tr></thead>
        <tbody>
          {steps.map(s => (
            <tr key={s.step} className="border-t border-slate-800">
              <td className="p-1">{s.step}</td>
              <td className={`p-1 ${statusColor(s.status)}`}>{s.status}</td>
              <td className="p-1">{s.durationMs ? `${(s.durationMs / 1000).toFixed(1)}s` : '—'}</td>
              <td className="p-1">{s.attempt}</td>
              <td className="p-1">
                {s.step !== 'INGESTION' && (
                  <button onClick={() => onRetry?.(s.step)} className="text-blue-400 hover:text-green-400" title="재실행">⟲</button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AttachmentDetailTable({ attachments, onReindex }: { attachments: AttachmentDetail[]; onReindex?: (id: number) => void }) {
  if (attachments.length === 0) {
    return (
      <div className="rounded border border-slate-700 overflow-hidden">
        <div className="bg-slate-800 px-3 py-1.5 text-[10px] uppercase tracking-wider text-blue-300">첨부파일</div>
        <div className="p-3 text-xs text-slate-500">첨부 없음</div>
      </div>
    );
  }
  return (
    <div className="rounded border border-slate-700 overflow-hidden">
      <div className="bg-slate-800 px-3 py-1.5 text-[10px] uppercase tracking-wider text-blue-300">
        첨부파일 {attachments.length}건
      </div>
      <table className="w-full text-[10px]">
        <thead><tr className="text-blue-300"><th className="p-1 text-left">파일명</th><th className="p-1">EXT</th><th className="p-1">EMB</th><th className="p-1"></th></tr></thead>
        <tbody>
          {attachments.map(a => {
            const needsAction = a.extractionStatus === 'FAILED' || !a.embedded;
            return (
              <tr key={a.attachmentId} className="border-t border-slate-800">
                <td className="p-1 truncate max-w-[12ch]" title={a.filename}>{a.filename}</td>
                <td className={`p-1 ${a.extractionStatus === 'EXTRACTED' ? 'text-green-500' : a.extractionStatus === 'FAILED' ? 'text-red-500' : 'text-slate-500'}`}>
                  {a.extractionStatus === 'EXTRACTED' ? '✓' : a.extractionStatus === 'FAILED' ? 'FAIL' : '—'}
                </td>
                <td className={`p-1 ${a.embedded ? 'text-green-500' : 'text-amber-500'}`}>{a.embedded ? '✓' : '누락'}</td>
                <td className="p-1">{needsAction && <button onClick={() => onReindex?.(a.attachmentId)} className="text-blue-400" title="재인덱싱">⟲</button>}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ReferenceDetailTable({ references }: { references: ReferenceDetail[] }) {
  if (references.length === 0) {
    return (
      <div className="rounded border border-slate-700 overflow-hidden">
        <div className="bg-slate-800 px-3 py-1.5 text-[10px] uppercase tracking-wider text-blue-300">참고 사이트</div>
        <div className="p-3 text-[10px] text-slate-500">Phase D 완료 후 채워짐</div>
      </div>
    );
  }
  return (
    <div className="rounded border border-slate-700 overflow-hidden">
      <div className="bg-slate-800 px-3 py-1.5 text-[10px] uppercase tracking-wider text-blue-300">참고 사이트</div>
      <table className="w-full text-[10px]">
        <thead><tr className="text-blue-300"><th className="p-1 text-left">URL</th><th className="p-1">STATUS</th><th className="p-1">청크</th></tr></thead>
        <tbody>
          {references.map((r, i) => (
            <tr key={i} className="border-t border-slate-800">
              <td className="p-1 truncate max-w-[16ch]" title={r.url}>{r.url}</td>
              <td className={`p-1 ${r.status === 'SUCCESS' ? 'text-green-500' : 'text-amber-500'}`}>{r.status}</td>
              <td className="p-1">{r.chunkCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function statusColor(s: string): string {
  switch (s) {
    case 'SUCCESS': return 'text-green-500';
    case 'FAILED': return 'text-red-500';
    case 'SKIPPED': return 'text-slate-500';
    case 'IN_PROGRESS': return 'text-blue-500';
    default: return 'text-slate-400';
  }
}
```

- [ ] **Step 2: 테스트 + 커밋**

(컴포넌트는 데이터 조립 위주라 핵심 케이스 위주로 — 5단계 표시 / 첨부 0건 / 참조 placeholder)

```bash
git commit -m "feat(frontend): PolicyProcessingDetailPanel — 펼침 표 3개 + 통합 액션 버튼 (스텁)"
```

---

# Phase 5 — 페이지 통합 + 라우팅 + 재실행 mutation

## Task 22: 재실행 mutation 훅 5종

**Files:**
- Create: `frontend/src/hooks/mutations/useRetryProcessingStep.ts`
- Create: `frontend/src/hooks/mutations/useReindexAttachment.ts`
- Create: `frontend/src/hooks/mutations/useReindexAllAttachments.ts`
- Create: `frontend/src/hooks/mutations/useReindexRag.ts`
- Create: `frontend/src/hooks/mutations/useReprocessPolicy.ts`

- [ ] **Step 1: mutation 훅 5개 작성 — 모두 동일 패턴**

`useRetryProcessingStep.ts`:
```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';
import type { ProcessingStep } from '@/types/adminPolicyProcessing';

export function useRetryProcessingStep(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (step: ProcessingStep) => adminPolicyProcessingApi.retryStep(policyId, step),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
```

`useReindexAttachment.ts`:
```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';

export function useReindexAttachment(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: number) => adminPolicyProcessingApi.reindexAttachment(policyId, attachmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
```

`useReindexAllAttachments.ts` / `useReindexRag.ts`:
```typescript
export function useReindexAllAttachments(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminPolicyProcessingApi.reindexAllAttachments(policyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}

export function useReindexRag(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminPolicyProcessingApi.reindexRag(policyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
```

`useReprocessPolicy.ts`:
```typescript
export function useReprocessPolicy(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) => adminPolicyProcessingApi.reprocess(policyId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/hooks/mutations/use*.ts
git commit -m "feat(frontend): admin 정책 처리 현황 재실행 mutation 훅 5종"
```

## Task 23: ReprocessConfirmDialog

**Files:**
- Create: `frontend/src/pages/admin/policy-processing/ReprocessConfirmDialog.tsx`

- [ ] **Step 1: 다이얼로그 구현 (shadcn/ui Dialog 사용)**

```typescript
import { useState } from 'react';

interface Props {
  open: boolean;
  policyTitle: string;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  isSubmitting?: boolean;
}

export function ReprocessConfirmDialog({ open, policyTitle, onClose, onConfirm, isSubmitting }: Props) {
  const [reason, setReason] = useState('');

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div className="bg-slate-900 border border-slate-700 rounded-lg p-6 w-96" onClick={e => e.stopPropagation()}>
        <h3 className="text-base font-semibold mb-2">전체 재처리</h3>
        <p className="text-xs text-slate-400 mb-4">"{policyTitle}" 의 ENRICHMENT/GUIDE/RULE/RAG 단계가 모두 다시 큐잉됩니다. 사유를 입력해 주세요.</p>
        <textarea
          className="w-full rounded border border-slate-700 bg-slate-950 px-3 py-2 text-sm mb-4"
          rows={3}
          placeholder="예: LLM 모델 업데이트"
          value={reason}
          onChange={e => setReason(e.target.value)}
        />
        <div className="flex gap-2 justify-end">
          <button onClick={onClose} className="text-xs rounded border border-slate-700 px-3 py-1">취소</button>
          <button
            disabled={!reason.trim() || isSubmitting}
            onClick={() => onConfirm(reason)}
            className="text-xs rounded bg-red-600 text-white px-3 py-1 disabled:opacity-50"
          >
            {isSubmitting ? '처리 중…' : '재처리 실행'}
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 커밋**

```bash
git commit -m "feat(frontend): ReprocessConfirmDialog (사유 입력 + 확인)"
```

## Task 24: AdminPolicyProcessingPage 통합

**Files:**
- Create: `frontend/src/pages/admin/AdminPolicyProcessingPage.tsx`

- [ ] **Step 1: 페이지 구성**

```typescript
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAdminPolicyProcessingList } from '@/hooks/queries/useAdminPolicyProcessingList';
import { useAdminPolicyProcessingStats } from '@/hooks/queries/useAdminPolicyProcessingStats';
import { useRetryProcessingStep } from '@/hooks/mutations/useRetryProcessingStep';
import { useReindexAttachment } from '@/hooks/mutations/useReindexAttachment';
import { useReindexAllAttachments } from '@/hooks/mutations/useReindexAllAttachments';
import { useReindexRag } from '@/hooks/mutations/useReindexRag';
import { useReprocessPolicy } from '@/hooks/mutations/useReprocessPolicy';
import { PolicyProcessingKpiCards } from './policy-processing/PolicyProcessingKpiCards';
import { PolicyProcessingFilters } from './policy-processing/PolicyProcessingFilters';
import { PolicyProcessingTable } from './policy-processing/PolicyProcessingTable';
import { PolicyProcessingDetailPanel } from './policy-processing/PolicyProcessingDetailPanel';
import { ReprocessConfirmDialog } from './policy-processing/ReprocessConfirmDialog';
import type { Filter, Sort } from '@/types/adminPolicyProcessing';

export default function AdminPolicyProcessingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const params = {
    q: searchParams.get('q') ?? undefined,
    region: searchParams.get('region') ?? undefined,
    filter: (searchParams.get('filter') as Filter) ?? 'ALL',
    sort: (searchParams.get('sort') as Sort) ?? 'UPDATED_DESC',
    page: Number(searchParams.get('page') ?? 0),
    size: Number(searchParams.get('size') ?? 50),
  };
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [reprocessFor, setReprocessFor] = useState<{ id: number; title: string } | null>(null);

  const { data: list, isLoading } = useAdminPolicyProcessingList(params);
  const { data: stats } = useAdminPolicyProcessingStats();

  const onParamsChange = (next: typeof params) => {
    const sp = new URLSearchParams();
    if (next.q) sp.set('q', next.q);
    if (next.region) sp.set('region', next.region);
    if (next.filter && next.filter !== 'ALL') sp.set('filter', next.filter);
    if (next.sort && next.sort !== 'UPDATED_DESC') sp.set('sort', next.sort);
    if (next.page) sp.set('page', String(next.page));
    if (next.size && next.size !== 50) sp.set('size', String(next.size));
    setSearchParams(sp);
  };

  const toggleExpand = (id: number) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  return (
    <div className="p-4 md:p-6">
      <h1 className="text-lg font-semibold mb-1">정책 처리 현황</h1>
      <p className="text-xs text-slate-500 mb-4">5단계 적재 + 첨부 임베딩 + 참고 사이트 fetch — 전체 {stats?.totalCount ?? '…'}건</p>

      {stats && <PolicyProcessingKpiCards stats={stats} />}

      <PolicyProcessingFilters
        params={params}
        onChange={onParamsChange}
        chipCounts={stats ? {
          ALL: stats.totalCount,
          INCOMPLETE: stats.incompleteCount,
          PARTIAL: stats.partialCount,
          RECENT_24H: stats.recent24hCount,
        } : {}}
      />

      {isLoading && <div className="text-xs text-slate-500">로딩 중…</div>}
      {list && (
        <PolicyProcessingTable
          items={list.items}
          expandedIds={expandedIds}
          onToggle={toggleExpand}
          renderDetail={(policyId) => (
            <DetailWithActions
              policyId={policyId}
              onOpenReprocess={(title) => setReprocessFor({ id: policyId, title })}
            />
          )}
        />
      )}

      {reprocessFor && (
        <ReprocessConfirmDialog
          open
          policyTitle={reprocessFor.title}
          onClose={() => setReprocessFor(null)}
          onConfirm={(reason) => {
            // mutation 호출은 DetailWithActions 내부에서 처리하는 대신 lift 가능 — 단순화 위해 dialog 가 mutation 받는 형태로
            // 여기선 reason 만 받아 처리 위임
            window.dispatchEvent(new CustomEvent('reprocess', { detail: { policyId: reprocessFor.id, reason } }));
            setReprocessFor(null);
          }}
        />
      )}
    </div>
  );
}

function DetailWithActions({ policyId, onOpenReprocess }: { policyId: number; onOpenReprocess: (title: string) => void }) {
  const retryStep = useRetryProcessingStep(policyId);
  const reindexAttachment = useReindexAttachment(policyId);
  const reindexAllAttachments = useReindexAllAttachments(policyId);
  const reindexRag = useReindexRag(policyId);

  return (
    <PolicyProcessingDetailPanel
      policyId={policyId}
      onAction={{
        retryStep: (step) => retryStep.mutate(step as any),
        reindexAttachment: (id) => reindexAttachment.mutate(id),
        reindexAllAttachments: () => reindexAllAttachments.mutate(),
        reindexRag: () => reindexRag.mutate(),
        reprocess: () => onOpenReprocess('정책 ' + policyId),
      }}
    />
  );
}
```

> Note: 위 컴포넌트의 reprocess 다이얼로그 ↔ mutation 연결은 깔끔히 하려면 별도 hook 으로 lift. 위는 단순화된 1차 통합 코드. 코드 리뷰 단계에서 정리.

- [ ] **Step 2: 라우팅 + 사이드바 메뉴 추가**

`App.tsx` 에:
```typescript
import AdminPolicyProcessingPage from '@/pages/admin/AdminPolicyProcessingPage';

// 기존 admin 라우트 그룹 안에:
<Route path="/admin/policies/processing" element={<AdminPolicyProcessingPage />} />
```

AdminLayout 사이드바 컴포넌트 찾아서 메뉴 추가:
```typescript
{ href: '/admin/policies/processing', label: '정책 처리 현황' }
```

- [ ] **Step 3: 빌드·타입체크 확인 + 커밋**

Run: `cd frontend && npm run build`
Expected: 성공.

```bash
git add frontend/src/pages/admin/AdminPolicyProcessingPage.tsx frontend/src/App.tsx frontend/src/components/layout/AdminLayout.tsx
git commit -m "feat(frontend): AdminPolicyProcessingPage 통합 + 라우팅 + 사이드바 메뉴"
```

## Task 25: 실 데이터 검증 (정책 86~90 기준)

**Files:** 없음 (수동 검증)

- [ ] **Step 1: 백엔드 빌드 + 실행**

Run: `cd backend && ./gradlew bootRun`

- [ ] **Step 2: 프론트엔드 dev 서버 실행**

Run: `cd frontend && npm run dev`

- [ ] **Step 3: 브라우저 진입**

`http://localhost:5173/admin/policies/processing` 접근 (로그인 필요).

- [ ] **Step 4: 정책 86~90 행 확인**

- 5단계 dots 색 정상 (Phase A 의 수동 검증 결과와 일치)
- 첨부 카운트 정상
- 펼침 시 5단계 표 + 첨부 표 정상

- [ ] **Step 5: 필터 동작 확인**

- "미흡만" 칩 클릭 → RAG FAILED 정책만 표시
- "첨부 임베딩 누락" 칩 클릭 → 해당 정책만 표시
- 검색 → 정책 ID 100 입력 → 1행만 표시

- [ ] **Step 6: 재실행 액션 1건 시도 (안전한 정책 대상)**

- "RAG 본문 재인덱싱" 클릭 → 200 응답 + 표 자동 refresh + 새 attempt 기록 확인.

- [ ] **Step 7: 검증 commit (코드 변경 없으면 skip)**

---

## Self-Review

본 plan 의 spec 커버리지·placeholder·타입 일관성 self-review:

### 1. Spec coverage (spec 의 각 섹션을 task 에 매핑)

| Spec 섹션 | 대응 Task |
|----------|----------|
| §3 사용 시나리오 A·B·C | Task 19 (검색·정렬·칩) + Task 20 (표) + Task 21 (펼침) — 시나리오 모두 페이지 한 곳에서 |
| §5.1 완성도 정의 | Task 5 `computeCompleteness` |
| §6.1 페이지 위치 | Task 24 라우팅 + 사이드바 |
| §6.2 KPI 4개 | Task 18 KpiCards + Task 6 Stats |
| §6.2 검색·정렬·칩 8개 | Task 19 Filters |
| §6.3 표 8컬럼 | Task 20 Table |
| §6.4 펼침 3개 표 + 액션 | Task 21 DetailPanel + Task 22 mutations |
| §7.1 조회 endpoint 3종 | Task 9 |
| §7.2 재실행 endpoint 5종 | Task 10~14 |
| §8 프론트엔드 파일 구조 | Task 15~24 |
| §10 Phase 진행 순서 | 본 plan 전체 |
| §12 테스트 전략 | 각 task 의 TDD step |

**Gap**:
- §11 audit log: plan 에 명시적 task 없음. MVP 에선 `policy_processing_step.reason` 필드에 reason 저장으로 대체 (Task 14 의 reprocess 가 reason 을 step 의 reason 으로 기록). 별도 audit 테이블은 후속 작업.

### 2. Placeholder scan

- Task 10 "GuideGenerationRequestedEvent / EligibilityRuleGenerationRequestedEvent / EnrichmentRequestedEvent 가 없으면 신규 생성, 또는 기존 listener trigger 패턴 확인" — 실제 코드 확인 후 구체화 필요. 본 plan 실행 시 첫 task 로 grep 으로 확인 권장.
- Task 24 reprocess 다이얼로그 ↔ mutation 연결 "단순화된 1차 통합 코드. 코드 리뷰 단계에서 정리." — 의도된 cleanup 지점.

### 3. Type consistency

- `Completeness` enum: 백엔드 `PolicyProcessingCompleteness` (COMPLETE/PARTIAL/INCOMPLETE) ↔ 프론트엔드 `Completeness` ('COMPLETE'|'PARTIAL'|'INCOMPLETE') — 일치.
- `ProcessingStep`: 5종 enum 백/프 일치.
- `ProcessingStatus`: 5종 백/프 일치.
- API 응답 필드명 (camelCase) — Spring 기본 직렬화 일치.
- Filter enum 8종 백/프 일치.

수정사항 없음.

---

## Execution

Plan complete and saved to `docs/superpowers/plans/2026-05-29-admin-policy-processing-dashboard.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task + 두 단계 review. 빠른 iteration.

**2. Inline Execution** — 본 세션에서 executing-plans 로 batch 실행 + checkpoint review.

어느 방식으로 진행할까요?
