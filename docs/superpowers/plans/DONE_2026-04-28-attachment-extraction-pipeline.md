# 첨부파일 추출 / 임베딩 파이프라인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 복지로 등 외부 출처에서 들어오는 정책 첨부(PDF/HWP/Office)를 비동기 파이프라인으로 다운로드 → 텍스트 추출 → 정책 단위로 합쳐 RAG 인덱싱 + 가이드 재생성까지 자동 진행.

**Architecture:** ingestion 모듈이 `@Async` + `@Scheduled` 기반 파이프라인 운영. policy 모듈에 상태 머신을 가진 `PolicyAttachment` 도메인 모델 확장. rag/guide 모듈은 변경 없이 기존 API 재사용.

**Tech Stack:** Java 21 + Spring Boot 4.0.5, JPA + Postgres + pgvector, Apache Tika, hwplib, AWS SDK v2 S3 (선택적), JUnit 5 + AssertJ + WireMock.

**Spec:** `docs/superpowers/specs/DONE_2026-04-28-attachment-extraction-pipeline-design.md`

**커밋 컨벤션:**
- 영역 태그: `feat(be)`, `fix(be)`, `refactor(be)`, `chore(be)`, `test(be)`
- 각 Task 끝마다 커밋. PR 단위(Phase)는 §13에 따라 분할
- 모든 커밋은 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` 푸터 포함

---

## Phase 0: 사전 확인 (선결 / 0.5h)

### Task 0.1: GuideGenerationService 입력 hash 가드 확인

**Files:**
- Read: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Read: `backend/src/main/java/com/youthfit/guide/application/dto/command/GenerateGuideCommand.java`

- [ ] **Step 1: 코드 확인 — 같은 입력 hash 면 LLM 호출 스킵하는 가드가 있는지**

Run: `grep -n "hash\|cache\|already\|exists" backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`

- [ ] **Step 2: 결과 기록**

가드 있음 → 본 플랜 그대로 진행. AttachmentReindexService 가 두 번째 호출 시 자연 스킵.
가드 없음 → 본 플랜 §Phase 5 끝에 Task 5.6 (가이드 입력 hash 캐시 추가) 를 새로 끼워 넣을 것. 본 문서를 그 시점에 업데이트.

- [ ] **Step 3: 결정 확인 (커밋 없음)**

체크리스트만 갱신. 코드 변경 없음.

---

## Phase 1: 도메인 모델 + 상태 머신 (PR1 / 4h)

**범위**: PolicyAttachment 컬럼 확장, AttachmentStatus / SkipReason enum, 상태 전이 도메인 메서드, 리포지토리, application service.
**비활성**: 이 PR 머지 후에도 실제 다운로드/추출은 동작하지 않음. 모든 첨부는 PENDING 상태로 머무름.

### Task 1.1: AttachmentStatus enum

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/AttachmentStatus.java`

- [ ] **Step 1: enum 작성**

```java
package com.youthfit.policy.domain.model;

public enum AttachmentStatus {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    EXTRACTING,
    EXTRACTED,
    FAILED,
    SKIPPED;

    public boolean isTerminal() {
        return this == EXTRACTED || this == SKIPPED;
    }

    public boolean isInFlight() {
        return this == DOWNLOADING || this == EXTRACTING;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/AttachmentStatus.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentStatus enum 추가

PolicyAttachment 상태 머신용 enum. PENDING/DOWNLOADING/DOWNLOADED/
EXTRACTING/EXTRACTED/FAILED/SKIPPED 7가지 상태와 헬퍼 메서드 포함.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.2: SkipReason enum

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/SkipReason.java`

- [ ] **Step 1: enum 작성**

```java
package com.youthfit.policy.domain.model;

public enum SkipReason {
    SCANNED_PDF,
    UNSUPPORTED_MIME,
    OVERSIZED;
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/SkipReason.java
git commit -m "$(cat <<'EOF'
feat(be): SkipReason enum 추가

영구 스킵 사유 — SCANNED_PDF / UNSUPPORTED_MIME / OVERSIZED.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.3: PolicyAttachment 확장 — 컬럼 + 도메인 메서드 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentTest.java`

- [ ] **Step 1: 실패 테스트 먼저 작성 — 초기 상태 PENDING**

`backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyAttachmentTest {

    private PolicyAttachment newAttachment() {
        return PolicyAttachment.builder()
                .name("공고문.pdf")
                .url("https://example.com/file.pdf")
                .mediaType("application/pdf")
                .build();
    }

    @Test
    void 신규_첨부는_PENDING_상태로_생성된다() {
        PolicyAttachment a = newAttachment();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
        assertThat(a.getExtractedText()).isNull();
        assertThat(a.getSkipReason()).isNull();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentTest -i`
Expected: COMPILE FAIL (getExtractionStatus 등 미정의)

- [ ] **Step 3: PolicyAttachment 컬럼 + getter 추가 (최소 구현)**

`backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java`:

```java
package com.youthfit.policy.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "policy_attachment",
        indexes = {
                @Index(name = "idx_policy_attachment_status_updated", columnList = "extraction_status,updated_at")
        }
)
public class PolicyAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "media_type", length = 100)
    private String mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private AttachmentStatus extractionStatus = AttachmentStatus.PENDING;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "extraction_retry_count", nullable = false)
    private int extractionRetryCount = 0;

    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 30)
    private SkipReason skipReason;

    @Builder
    private PolicyAttachment(String name, String url, String mediaType) {
        this.name = name;
        this.url = url;
        this.mediaType = mediaType;
        this.extractionStatus = AttachmentStatus.PENDING;
        this.extractionRetryCount = 0;
    }

    void assignTo(Policy policy) {
        this.policy = policy;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentTest -i`
Expected: PASS

- [ ] **Step 5: 상태 전이 테스트 추가 (TDD)**

`PolicyAttachmentTest.java` 에 추가:

```java
    @Test
    void markDownloading_은_PENDING에서만_허용된다() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADING);
    }

    @Test
    void markDownloading_을_DOWNLOADING_상태에서_다시_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        assertThatThrownBy(a::markDownloading)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid transition");
    }

    @Test
    void markDownloaded_는_DOWNLOADING에서만_허용되며_storageKey와_fileHash를_세팅() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("attachments/2026/04/abc.pdf", "abc123hash");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADED);
        assertThat(a.getStorageKey()).isEqualTo("attachments/2026/04/abc.pdf");
        assertThat(a.getFileHash()).isEqualTo("abc123hash");
    }

    @Test
    void markExtracting_은_DOWNLOADED에서만_허용() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.EXTRACTING);
    }

    @Test
    void markExtracted_는_EXTRACTING에서만_허용하며_텍스트_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("추출된 텍스트");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.EXTRACTED);
        assertThat(a.getExtractedText()).isEqualTo("추출된 텍스트");
    }

    @Test
    void markFailed_는_재시도카운트_증가_및_에러메시지_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("network timeout");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.FAILED);
        assertThat(a.getExtractionRetryCount()).isEqualTo(1);
        assertThat(a.getExtractionError()).isEqualTo("network timeout");
    }

    @Test
    void markFailed_에러메시지_500자_초과시_truncate() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        String longError = "x".repeat(600);
        a.markFailed(longError);
        assertThat(a.getExtractionError()).hasSize(500);
    }

    @Test
    void markSkipped_는_사유_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markSkipped(SkipReason.SCANNED_PDF);
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.SKIPPED);
        assertThat(a.getSkipReason()).isEqualTo(SkipReason.SCANNED_PDF);
    }

    @Test
    void markSkipped_는_종료상태_EXTRACTED_에서_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("text");
        assertThatThrownBy(() -> a.markSkipped(SkipReason.SCANNED_PDF))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markPendingReextraction_은_종료상태에서_PENDING_으로_복귀_및_필드_초기화() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("text");
        a.markPendingReextraction();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
        assertThat(a.getExtractionError()).isNull();
        assertThat(a.getSkipReason()).isNull();
    }

    @Test
    void markPendingReextraction_은_FAILED에서도_허용() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("err");
        a.markPendingReextraction();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
    }

    @Test
    void markPendingReextraction_은_PENDING_상태에서_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        assertThatThrownBy(a::markPendingReextraction)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetFailedToPending_은_FAILED에서_PENDING으로_복귀하지만_retryCount는_유지() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("err1");  // retryCount=1
        a.resetFailedToPending();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isEqualTo(1);  // 유지
    }
}
```

- [ ] **Step 6: 실패 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentTest -i`
Expected: FAIL — 메서드 미정의

- [ ] **Step 7: PolicyAttachment 에 도메인 메서드 추가**

`PolicyAttachment.java` 클래스 끝에 추가:

```java
    private static final int ERROR_MAX_LENGTH = 500;

    public void markDownloading() {
        require(extractionStatus == AttachmentStatus.PENDING, AttachmentStatus.DOWNLOADING);
        this.extractionStatus = AttachmentStatus.DOWNLOADING;
    }

    public void markDownloaded(String storageKey, String fileHash) {
        require(extractionStatus == AttachmentStatus.DOWNLOADING, AttachmentStatus.DOWNLOADED);
        this.extractionStatus = AttachmentStatus.DOWNLOADED;
        this.storageKey = storageKey;
        this.fileHash = fileHash;
    }

    public void markExtracting() {
        require(extractionStatus == AttachmentStatus.DOWNLOADED, AttachmentStatus.EXTRACTING);
        this.extractionStatus = AttachmentStatus.EXTRACTING;
    }

    public void markExtracted(String text) {
        require(extractionStatus == AttachmentStatus.EXTRACTING, AttachmentStatus.EXTRACTED);
        this.extractionStatus = AttachmentStatus.EXTRACTED;
        this.extractedText = text;
        this.extractionError = null;
    }

    public void markSkipped(SkipReason reason) {
        require(!extractionStatus.isTerminal(), AttachmentStatus.SKIPPED);
        this.extractionStatus = AttachmentStatus.SKIPPED;
        this.skipReason = reason;
    }

    public void markFailed(String error) {
        require(!extractionStatus.isTerminal(), AttachmentStatus.FAILED);
        this.extractionStatus = AttachmentStatus.FAILED;
        this.extractionRetryCount += 1;
        this.extractionError = truncate(error);
    }

    public void markPendingReextraction() {
        if (extractionStatus != AttachmentStatus.EXTRACTED
                && extractionStatus != AttachmentStatus.SKIPPED
                && extractionStatus != AttachmentStatus.FAILED) {
            throw invalidTransition(AttachmentStatus.PENDING);
        }
        this.extractionStatus = AttachmentStatus.PENDING;
        this.extractionRetryCount = 0;
        this.extractionError = null;
        this.skipReason = null;
    }

    public void resetFailedToPending() {
        require(extractionStatus == AttachmentStatus.FAILED, AttachmentStatus.PENDING);
        this.extractionStatus = AttachmentStatus.PENDING;
        // retryCount 보존 — 한도 추적용
    }

    private void require(boolean condition, AttachmentStatus target) {
        if (!condition) throw invalidTransition(target);
    }

    private IllegalStateException invalidTransition(AttachmentStatus target) {
        return new IllegalStateException(
                "invalid transition: " + extractionStatus + " → " + target);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_MAX_LENGTH ? s : s.substring(0, ERROR_MAX_LENGTH);
    }
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentTest -i`
Expected: PASS (모든 테스트)

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentTest.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyAttachment 상태 머신 도메인 메서드 추가

- 추출 상태 컬럼 (extractionStatus, storageKey, fileHash, extractedText,
  extractionRetryCount, extractionError, skipReason) 추가
- 상태 전이 도메인 메서드 (mark*) — 위반 시 IllegalStateException
- 폴링 효율을 위한 인덱스 (extraction_status, updated_at) 추가
- 단위 테스트로 모든 전이/위반 케이스 검증

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.4: PolicyAttachmentRepository

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyAttachmentRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentJpaRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyAttachmentJpaRepositoryAdapter.java`

- [ ] **Step 1: 도메인 리포지토리 인터페이스 작성**

```java
package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicyAttachment;

import java.util.List;
import java.util.Optional;

public interface PolicyAttachmentRepository {
    Optional<PolicyAttachment> findById(Long id);
    PolicyAttachment save(PolicyAttachment attachment);

    List<PolicyAttachment> findPendingForDownload(int limit);
    List<PolicyAttachment> findDownloadedForExtraction(int limit);
    List<PolicyAttachment> findFailedRetryable(int limit, int retryLimit);
    List<PolicyAttachment> findByPolicyIdAndStatusEquals(Long policyId, com.youthfit.policy.domain.model.AttachmentStatus status);

    /**
     * 정책에 PENDING/DOWNLOADING/DOWNLOADED/EXTRACTING/FAILED 가 하나도 없는지.
     * true = 모두 EXTRACTED 또는 SKIPPED.
     */
    boolean isAllTerminalForPolicy(Long policyId);

    List<PolicyAttachment> findExtractedByPolicyId(Long policyId);
    List<PolicyAttachment> findByPolicyId(Long policyId);
}
```

- [ ] **Step 2: Spring Data JPA 인터페이스 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PolicyAttachmentJpaRepository extends JpaRepository<PolicyAttachment, Long> {

    List<PolicyAttachment> findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus status, Limit limit);

    @Query("SELECT a FROM PolicyAttachment a "
            + "WHERE a.extractionStatus = com.youthfit.policy.domain.model.AttachmentStatus.FAILED "
            + "AND a.extractionRetryCount < :retryLimit "
            + "ORDER BY a.updatedAt ASC")
    List<PolicyAttachment> findFailedRetryable(int retryLimit, Limit limit);

    List<PolicyAttachment> findByPolicy_IdAndExtractionStatus(Long policyId, AttachmentStatus status);

    List<PolicyAttachment> findByPolicy_Id(Long policyId);

    @Query("SELECT COUNT(a) FROM PolicyAttachment a "
            + "WHERE a.policy.id = :policyId "
            + "AND a.extractionStatus NOT IN ("
            + "  com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED, "
            + "  com.youthfit.policy.domain.model.AttachmentStatus.SKIPPED)")
    long countNonTerminalByPolicyId(Long policyId);
}
```

- [ ] **Step 3: 어댑터 구현 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyAttachmentJpaRepositoryAdapter implements PolicyAttachmentRepository {

    private final PolicyAttachmentJpaRepository jpa;

    @Override
    public Optional<PolicyAttachment> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public PolicyAttachment save(PolicyAttachment attachment) {
        return jpa.save(attachment);
    }

    @Override
    public List<PolicyAttachment> findPendingForDownload(int limit) {
        return jpa.findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus.PENDING, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findDownloadedForExtraction(int limit) {
        return jpa.findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus.DOWNLOADED, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findFailedRetryable(int limit, int retryLimit) {
        return jpa.findFailedRetryable(retryLimit, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findByPolicyIdAndStatusEquals(Long policyId, AttachmentStatus status) {
        return jpa.findByPolicy_IdAndExtractionStatus(policyId, status);
    }

    @Override
    public boolean isAllTerminalForPolicy(Long policyId) {
        return jpa.countNonTerminalByPolicyId(policyId) == 0L;
    }

    @Override
    public List<PolicyAttachment> findExtractedByPolicyId(Long policyId) {
        return findByPolicyIdAndStatusEquals(policyId, AttachmentStatus.EXTRACTED);
    }

    @Override
    public List<PolicyAttachment> findByPolicyId(Long policyId) {
        return jpa.findByPolicy_Id(policyId);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyAttachmentRepository.java backend/src/main/java/com/youthfit/policy/infrastructure/persistence/
git commit -m "$(cat <<'EOF'
feat(be): PolicyAttachmentRepository 도메인 포트 + JPA 어댑터 추가

- 도메인 인터페이스: PolicyAttachmentRepository
- Spring Data JPA: PolicyAttachmentJpaRepository
- 어댑터: PolicyAttachmentJpaRepositoryAdapter
- 폴링 쿼리 4종 (PENDING, DOWNLOADED, FAILED retryable, terminal 체크)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.5: PolicyAttachmentApplicationService

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/service/PolicyAttachmentApplicationService.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/PolicyAttachmentApplicationServiceTest.java`

- [ ] **Step 1: 서비스 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyAttachmentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAttachmentApplicationService.class);

    private final PolicyAttachmentRepository repository;

    @Transactional
    public void markDownloading(Long id) {
        load(id).markDownloading();
    }

    @Transactional
    public void markDownloaded(Long id, String storageKey, String fileHash) {
        load(id).markDownloaded(storageKey, fileHash);
    }

    @Transactional
    public void markExtracting(Long id) {
        load(id).markExtracting();
    }

    @Transactional
    public void markExtracted(Long id, String text) {
        load(id).markExtracted(text);
    }

    @Transactional
    public void markSkipped(Long id, SkipReason reason) {
        load(id).markSkipped(reason);
    }

    @Transactional
    public void markFailed(Long id, String error) {
        load(id).markFailed(error);
    }

    @Transactional
    public int resetFailedToPending(int limit, int retryLimit) {
        List<PolicyAttachment> failed = repository.findFailedRetryable(limit, retryLimit);
        failed.forEach(PolicyAttachment::resetFailedToPending);
        if (!failed.isEmpty()) {
            log.info("Reset {} FAILED attachments to PENDING", failed.size());
        }
        return failed.size();
    }

    @Transactional
    public void markPendingReextraction(Long policyId) {
        List<PolicyAttachment> all = repository.findByPolicyId(policyId);
        all.forEach(PolicyAttachment::markPendingReextraction);
        log.info("Marked {} attachments PENDING for reextraction (policyId={})", all.size(), policyId);
    }

    private PolicyAttachment load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    }
}
```

> **Note**: `ErrorCode.NOT_FOUND` 가 없을 수 있음. 그 경우 `common/exception/ErrorCode.java` 를 열어서 적절한 코드 사용 (예: `POLICY_NOT_FOUND` 가 가장 가까우면 그것 사용, 없으면 `ATTACHMENT_NOT_FOUND` 추가). 추가가 필요하면 이 Task 의 별도 step 으로 enum 추가 후 진행.

- [ ] **Step 2: 단위 테스트 작성 (Mockito)**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyAttachmentApplicationServiceTest {

    @Mock private PolicyAttachmentRepository repository;
    @InjectMocks private PolicyAttachmentApplicationService sut;

    private PolicyAttachment attachment;

    @BeforeEach
    void setUp() {
        attachment = PolicyAttachment.builder()
                .name("a.pdf")
                .url("http://x")
                .mediaType("application/pdf")
                .build();
    }

    @Test
    void markDownloading_은_도메인_메서드를_위임_호출한다() {
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));
        sut.markDownloading(1L);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADING);
    }

    @Test
    void resetFailedToPending_은_FAILED_목록을_PENDING_으로_되돌리고_개수반환() {
        attachment.markDownloading();
        attachment.markFailed("err");
        when(repository.findFailedRetryable(20, 3)).thenReturn(List.of(attachment));
        int count = sut.resetFailedToPending(20, 3);
        assertThat(count).isEqualTo(1);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
    }

    @Test
    void markPendingReextraction_은_정책의_모든_첨부를_PENDING_으로() {
        attachment.markDownloading();
        attachment.markDownloaded("k", "h");
        attachment.markExtracting();
        attachment.markExtracted("text");
        when(repository.findByPolicyId(99L)).thenReturn(List.of(attachment));
        sut.markPendingReextraction(99L);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
    }
}
```

- [ ] **Step 3: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests PolicyAttachmentApplicationServiceTest -i`
Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyAttachmentApplicationService.java backend/src/test/java/com/youthfit/policy/application/service/PolicyAttachmentApplicationServiceTest.java
git commit -m "$(cat <<'EOF'
feat(be): PolicyAttachmentApplicationService 추가

@Transactional 트랜잭션 경계로 상태 전이 도메인 메서드를 위임 호출.
resetFailedToPending / markPendingReextraction 등 일괄 처리 메서드 포함.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.6: 빌드 검증 + Phase 1 마무리

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: local 프로필로 백엔드 기동 → ddl-auto:update 가 컬럼 추가했는지 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
docker compose up -d --build backend
sleep 15
docker compose logs backend | tail -50
```

Expected: 시작 로그에 hibernate가 `policy_attachment` 테이블에 컬럼 추가하는 SQL 보임. 에러 없음.

- [ ] **Step 3: DB에서 직접 확인 — psql 또는 docker exec**

```bash
docker compose exec postgres psql -U youthfit -d youthfit -c "\d policy_attachment"
```

Expected: 추가된 7개 컬럼 (`extraction_status`, `storage_key`, `file_hash`, `extracted_text`, `extraction_retry_count`, `extraction_error`, `skip_reason`) 존재.

- [ ] **Step 4: 기존 첨부 데이터 백필 (PENDING 으로)**

```bash
docker compose exec postgres psql -U youthfit -d youthfit -c "UPDATE policy_attachment SET extraction_status = 'PENDING', extraction_retry_count = 0 WHERE extraction_status IS NULL;"
```

Expected: 기존 행 수만큼 UPDATE.

- [ ] **Step 5: PR 생성 권고 — Phase 1 끝**

이 시점에서 `[BE] feat: 정책 첨부 추출 파이프라인 — 도메인 모델 (Phase 1/5)` 제목으로 PR 분리 권장. main 머지 후 Phase 2 시작.

```bash
# create-pr 스킬을 사용하거나 다음 명령:
git push -u origin <branch>
gh pr create --title "[BE] feat: 정책 첨부 추출 파이프라인 — 도메인 모델 (Phase 1/5)" --body "..."
```

---

## Phase 2: AttachmentStorage 추상화 (PR2 / 3h)

**범위**: 첨부 원본 저장을 위한 포트 + LocalAttachmentStorage / S3AttachmentStorage 구현 + 환경변수 분기.
**비활성**: 이 PR 머지 후에도 사용처가 없어 코드 동작 안 함 (다음 PR 에서 사용).

### Task 2.1: AttachmentStorage 포트 + StorageReference

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentStorage.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/StorageReference.java`

- [ ] **Step 1: StorageReference record 작성**

```java
package com.youthfit.ingestion.application.port;

public record StorageReference(String key, long sizeBytes, String sha256Hex) {
}
```

- [ ] **Step 2: AttachmentStorage 인터페이스 작성**

```java
package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentStorage {
    /**
     * 스트림을 저장하고 SHA-256 / 크기를 함께 계산하여 StorageReference 반환.
     * @param key  저장 식별자. 호출자 책임으로 unique 보장 (예: attachments/{yyyy}/{mm}/{uuid}.{ext})
     */
    StorageReference put(InputStream content, String key, String mediaType);

    InputStream get(String key);

    boolean exists(String key);
}
```

- [ ] **Step 3: 컴파일 확인 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/application/port/
git commit -m "$(cat <<'EOF'
feat(be): AttachmentStorage 포트 + StorageReference 추가

첨부 원본 저장 추상화. 구현체는 다음 커밋에서 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.2: LocalAttachmentStorage 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/LocalAttachmentStorage.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/LocalAttachmentStorageTest.java`

- [ ] **Step 1: 실패 테스트 먼저**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.StorageReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAttachmentStorageTest {

    @Test
    void put_은_파일을_저장하고_sha256과_크기를_반환(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        StorageReference ref = sut.put(new ByteArrayInputStream(data), "test/key.txt", "text/plain");

        assertThat(ref.key()).isEqualTo("test/key.txt");
        assertThat(ref.sizeBytes()).isEqualTo(5);
        // sha256("hello") 알려진 값
        assertThat(ref.sha256Hex())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void get_은_저장된_바이트를_반환(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        sut.put(new ByteArrayInputStream("hi".getBytes()), "k", "text/plain");
        try (InputStream in = sut.get("k")) {
            assertThat(in.readAllBytes()).isEqualTo("hi".getBytes());
        }
    }

    @Test
    void exists_는_저장유무에_따라_true_false(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        assertThat(sut.exists("k")).isFalse();
        sut.put(new ByteArrayInputStream("x".getBytes()), "k", "text/plain");
        assertThat(sut.exists("k")).isTrue();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests LocalAttachmentStorageTest -i`
Expected: COMPILE FAIL

- [ ] **Step 3: 구현 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path basePath;

    public LocalAttachmentStorage(@Value("${attachment.storage.local-path:/data/attachments}") String localPath) {
        this.basePath = Path.of(localPath);
    }

    @Override
    public StorageReference put(InputStream content, String key, String mediaType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream dis = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(target)) {
                size = dis.transferTo(out);
            }
            String hex = HexFormat.of().formatHex(digest.digest());
            return new StorageReference(key, size, hex);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("local storage put failed: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("local storage get failed: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    private Path resolve(String key) {
        return basePath.resolve(key.replace("..", "_")).normalize();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests LocalAttachmentStorageTest -i`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/LocalAttachmentStorage.java backend/src/test/java/com/youthfit/ingestion/infrastructure/external/LocalAttachmentStorageTest.java
git commit -m "$(cat <<'EOF'
feat(be): LocalAttachmentStorage 구현 추가

로컬 파일시스템 기반 첨부 저장. attachment.storage.type=local
(또는 미설정) 시 활성화. SHA-256 + 크기 함께 계산.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.3: AWS SDK 의존성 추가

**Files:**
- Modify: `backend/build.gradle`

- [ ] **Step 1: build.gradle 에 의존성 추가**

`dependencies { ... }` 블록 안에 추가:

```groovy
    implementation platform('software.amazon.awssdk:bom:2.28.16')
    implementation 'software.amazon.awssdk:s3'
```

- [ ] **Step 2: 의존성 다운로드 + 컴파일 확인**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/build.gradle
git commit -m "$(cat <<'EOF'
chore(be): AWS SDK v2 S3 의존성 추가

S3AttachmentStorage 구현 준비. attachment.storage.type=s3 일 때만 활성화.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.4: S3AttachmentStorage 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorage.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentStorageS3Config.java`

> **Note**: 현재는 S3 버킷/키 발급 전이라 실제로 활성화되지 않음. `attachment.storage.type=s3` 일 때만 빈 등록되도록 `@ConditionalOnProperty`. 단위 테스트는 mock S3 client 로 진행.

- [ ] **Step 1: S3 client 설정**

```java
package com.youthfit.ingestion.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.beans.factory.annotation.Value;

@Configuration
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "s3")
public class AttachmentStorageS3Config {

    @Bean
    public S3Client attachmentS3Client(@Value("${attachment.storage.s3.region:ap-northeast-2}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
```

- [ ] **Step 2: S3AttachmentStorage 구현**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "s3")
@RequiredArgsConstructor
public class S3AttachmentStorage implements AttachmentStorage {

    private final S3Client s3;

    @Value("${attachment.storage.s3.bucket}")
    private String bucket;

    @Override
    public StorageReference put(InputStream content, String key, String mediaType) {
        try {
            byte[] bytes = content.readAllBytes();
            String hex = sha256Hex(bytes);
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(mediaType)
                            .build(),
                    RequestBody.fromBytes(bytes));
            return new StorageReference(key, bytes.length, hex);
        } catch (IOException e) {
            throw new IllegalStateException("s3 put failed: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorage.java backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentStorageS3Config.java
git commit -m "$(cat <<'EOF'
feat(be): S3AttachmentStorage 구현 (조건부 활성화)

attachment.storage.type=s3 일 때만 빈 등록. 현재는 미사용 (키 발급 전).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.5: application.yml 환경변수 추가

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 첨부 관련 설정 블록 추가**

`youthfit:` 블록 아래 또는 별도 섹션으로 (기본 프로필):

```yaml
attachment:
  storage:
    type: ${ATTACHMENT_STORAGE_TYPE:local}
    local-path: ${ATTACHMENT_STORAGE_LOCAL_PATH:/data/attachments}
    s3:
      bucket: ${ATTACHMENT_STORAGE_S3_BUCKET:}
      region: ${ATTACHMENT_STORAGE_S3_REGION:ap-northeast-2}
  download:
    connect-timeout-seconds: ${ATTACHMENT_DOWNLOAD_CONNECT_TIMEOUT_SECONDS:10}
    read-timeout-seconds: ${ATTACHMENT_DOWNLOAD_READ_TIMEOUT_SECONDS:60}
    max-size-mb: ${ATTACHMENT_DOWNLOAD_MAX_SIZE_MB:50}
  extraction:
    min-text-chars: ${ATTACHMENT_EXTRACTION_MIN_TEXT_CHARS:100}
    retry-limit: ${ATTACHMENT_EXTRACTION_RETRY_LIMIT:3}
  reindex:
    max-content-kb: ${ATTACHMENT_REINDEX_MAX_CONTENT_KB:200}
  scheduler:
    fixed-delay-ms: ${ATTACHMENT_SCHEDULER_FIXED_DELAY_MS:60000}
    batch-size: ${ATTACHMENT_SCHEDULER_BATCH_SIZE:20}
```

local 프로필 블록에 옵션:

```yaml
attachment:
  storage:
    local-path: /tmp/youthfit-attachments
```

- [ ] **Step 2: 빌드 검증**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋 + Phase 2 마무리**

```bash
git add backend/src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
chore(be): 첨부 추출 파이프라인 환경변수 추가

attachment.* 네임스페이스로 storage / download / extraction / reindex /
scheduler 설정을 일괄 추가. 기본값은 v0 운영 가능 수준.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

이 시점에서 PR 분리 권장: `[BE] feat: 정책 첨부 추출 — Storage 추상화 (Phase 2/5)`

---

## Phase 3: Extractor 추상화 + Tika/Hwp 구현 (PR3 / 4h)

**범위**: ExtractionResult, AttachmentExtractor 포트, Tika/Hwp 구현, Dispatcher.

### Task 3.1: Tika + hwplib 의존성 추가

**Files:**
- Modify: `backend/build.gradle`

- [ ] **Step 1: 의존성 추가**

`dependencies { ... }` 블록에 추가:

```groovy
    implementation 'org.apache.tika:tika-core:2.9.2'
    implementation 'org.apache.tika:tika-parsers-standard-package:2.9.2'
    implementation 'kr.dogfoot:hwplib:1.1.8'
```

- [ ] **Step 2: 빌드 + 의존성 다운로드 확인**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL (수십 MB 다운로드)

- [ ] **Step 3: 커밋**

```bash
git add backend/build.gradle
git commit -m "$(cat <<'EOF'
chore(be): Apache Tika + hwplib 의존성 추가

PDF/Office/HTML 추출은 Tika, HWP 5.x 추출은 hwplib 사용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.2: ExtractionResult sealed

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/ExtractionResult.java`

- [ ] **Step 1: sealed 인터페이스 작성**

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.policy.domain.model.SkipReason;

public sealed interface ExtractionResult {

    record Success(String text) implements ExtractionResult {}
    record Skipped(SkipReason reason) implements ExtractionResult {}
    record Failed(String error) implements ExtractionResult {}

    static ExtractionResult success(String text) { return new Success(text); }
    static ExtractionResult skipped(SkipReason reason) { return new Skipped(reason); }
    static ExtractionResult failed(String error) { return new Failed(error); }
}
```

- [ ] **Step 2: 컴파일 확인 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/application/port/ExtractionResult.java
git commit -m "$(cat <<'EOF'
feat(be): ExtractionResult sealed 인터페이스 추가

추출 결과: Success(text) | Skipped(reason) | Failed(error).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.3: AttachmentExtractor 포트

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentExtractor.java`

- [ ] **Step 1: 인터페이스 작성**

```java
package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentExtractor {
    boolean supports(String mediaType);
    ExtractionResult extract(InputStream stream, long sizeBytes);
}
```

- [ ] **Step 2: 컴파일 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentExtractor.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentExtractor 포트 추가

mediaType 기반으로 추출 구현체 분기. 다음 커밋에서 Tika/Hwp 구현.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.4: TikaAttachmentExtractor

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java`
- Create: `backend/src/test/resources/extractor/sample.pdf` (단순 텍스트 PDF)
- Create: `backend/src/test/resources/extractor/empty.pdf` (스캔 추정 — 텍스트 거의 없음)
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractorTest.java`

> **Note**: fixture PDF 만들기:
> - `sample.pdf`: 본인이 만든 짧은 텍스트 PDF (예: "안녕하세요. 이것은 정책 공고문입니다." 가 200자 이상)
> - `empty.pdf`: 거의 빈 PDF 또는 이미지만 있는 PDF (Pages.app, MS Word, libreoffice 어디서 export 가능)
>
> 만들기 어려우면 Tika 의 test fixtures 에서 차용하거나, libreoffice 명령으로 자동 생성:
> ```bash
> echo "정책 공고: 청년 월세 지원금 신청 안내. 만 19세부터 34세까지 지원하며, 신청 기간은 2026년 5월 1일부터 6월 30일까지입니다. 자세한 사항은 첨부 파일을 참고하세요. 이는 테스트용 PDF입니다." > /tmp/sample.txt
> libreoffice --headless --convert-to pdf --outdir /tmp /tmp/sample.txt
> mv /tmp/sample.pdf backend/src/test/resources/extractor/sample.pdf
> ```

- [ ] **Step 1: fixture 디렉토리 + 샘플 PDF 준비**

```bash
mkdir -p backend/src/test/resources/extractor
# sample.pdf 와 empty.pdf 를 위 방법으로 생성하거나 손으로 추가
ls backend/src/test/resources/extractor
```

- [ ] **Step 2: 실패 테스트 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.ExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TikaAttachmentExtractorTest {

    private final TikaAttachmentExtractor sut = new TikaAttachmentExtractor();

    @Test
    void supports_는_PDF_DOC_HTML_등을_true_로() {
        assertThat(sut.supports("application/pdf")).isTrue();
        assertThat(sut.supports("application/msword")).isTrue();
        assertThat(sut.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
        assertThat(sut.supports("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
        assertThat(sut.supports("text/html")).isTrue();
        assertThat(sut.supports("text/plain")).isTrue();
    }

    @Test
    void supports_는_HWP_는_false_로() {
        assertThat(sut.supports("application/x-hwp")).isFalse();
        assertThat(sut.supports("application/haansofthwp")).isFalse();
    }

    @Test
    void supports_는_이미지_등은_false_로() {
        assertThat(sut.supports("image/png")).isFalse();
        assertThat(sut.supports("image/jpeg")).isFalse();
        assertThat(sut.supports("video/mp4")).isFalse();
    }

    @Test
    void extract_는_샘플PDF에서_텍스트를_뽑는다() throws Exception {
        try (InputStream in = new ClassPathResource("extractor/sample.pdf").getInputStream()) {
            ExtractionResult result = sut.extract(in, 10000);
            assertThat(result).isInstanceOf(ExtractionResult.Success.class);
            String text = ((ExtractionResult.Success) result).text();
            assertThat(text).contains("청년 월세");
        }
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd backend && ./gradlew test --tests TikaAttachmentExtractorTest -i`
Expected: COMPILE FAIL

- [ ] **Step 4: 구현 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
@Order(20)  // HWP 구현이 더 우선 (10)
public class TikaAttachmentExtractor implements AttachmentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/html",
            "text/plain"
    );

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.contains(mediaType.toLowerCase());
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            // -1 means unlimited content size — we already validated sizeBytes upstream.
            BodyContentHandler handler = new BodyContentHandler(-1);
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
            String text = handler.toString().trim();
            return ExtractionResult.success(text);
        } catch (IOException | SAXException | TikaException e) {
            return ExtractionResult.failed("tika extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests TikaAttachmentExtractorTest -i`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java backend/src/test/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractorTest.java backend/src/test/resources/extractor/
git commit -m "$(cat <<'EOF'
feat(be): TikaAttachmentExtractor 추가

Apache Tika 로 PDF/DOC/DOCX/XLS/XLSX/HTML/TXT 추출.
HWP 보다 낮은 우선순위(@Order(20))로 등록.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.5: HwpAttachmentExtractor

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/HwpAttachmentExtractor.java`
- Create: `backend/src/test/resources/extractor/sample.hwp` (가능하면)
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/HwpAttachmentExtractorTest.java`

> **Note**: HWP fixture 가 없으면 일단 mock-only 테스트로 진행하고 fixture 는 후속 PR 에서 추가 가능. supports() 메서드는 fixture 없이 검증 가능.

- [ ] **Step 1: 실패 테스트 (supports 만)**

```java
package com.youthfit.ingestion.infrastructure.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HwpAttachmentExtractorTest {

    private final HwpAttachmentExtractor sut = new HwpAttachmentExtractor();

    @Test
    void supports_는_HWP_관련_mediaType만_true() {
        assertThat(sut.supports("application/x-hwp")).isTrue();
        assertThat(sut.supports("application/haansofthwp")).isTrue();
        assertThat(sut.supports("application/vnd.hancom.hwp")).isTrue();
    }

    @Test
    void supports_는_PDF_등은_false() {
        assertThat(sut.supports("application/pdf")).isFalse();
        assertThat(sut.supports("text/plain")).isFalse();
        assertThat(sut.supports(null)).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests HwpAttachmentExtractorTest -i`
Expected: COMPILE FAIL

- [ ] **Step 3: 구현 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

@Component
@Order(10)  // Tika 보다 우선
public class HwpAttachmentExtractor implements AttachmentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/x-hwp",
            "application/haansofthwp",
            "application/vnd.hancom.hwp"
    );

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.contains(mediaType.toLowerCase());
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            HWPFile hwp = HWPReader.fromInputStream(stream);
            String text = TextExtractor.extract(hwp, TextExtractMethod.AppendControlTextAfterParagraphText);
            return ExtractionResult.success(text == null ? "" : text.trim());
        } catch (Exception e) {
            return ExtractionResult.failed("hwp extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests HwpAttachmentExtractorTest -i`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/HwpAttachmentExtractor.java backend/src/test/java/com/youthfit/ingestion/infrastructure/external/HwpAttachmentExtractorTest.java
git commit -m "$(cat <<'EOF'
feat(be): HwpAttachmentExtractor 추가

hwplib 로 HWP 5.x 추출. @Order(10) — Tika 보다 우선 등록.
실제 HWP 추출 결과 검증은 fixture 확보 후 후속 PR 에서.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.6: ExtractionDispatcher

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/ExtractionDispatcher.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/ExtractionDispatcherTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionDispatcherTest {

    @Test
    void dispatch_는_supports_가_true_인_첫_extractor를_사용() {
        AttachmentExtractor a = new StubExtractor("application/pdf", "from-A");
        AttachmentExtractor b = new StubExtractor("application/pdf", "from-B");
        ExtractionDispatcher sut = new ExtractionDispatcher(List.of(a, b));

        ExtractionResult r = sut.dispatch(new ByteArrayInputStream(new byte[0]), 0, "application/pdf");

        assertThat(r).isInstanceOf(ExtractionResult.Success.class);
        assertThat(((ExtractionResult.Success) r).text()).isEqualTo("from-A");
    }

    @Test
    void dispatch_는_지원하는_extractor가_없으면_UNSUPPORTED_MIME_skip() {
        ExtractionDispatcher sut = new ExtractionDispatcher(List.of(
                new StubExtractor("application/pdf", "x")
        ));
        ExtractionResult r = sut.dispatch(new ByteArrayInputStream(new byte[0]), 0, "image/png");
        assertThat(r).isInstanceOf(ExtractionResult.Skipped.class);
        assertThat(((ExtractionResult.Skipped) r).reason())
                .isEqualTo(com.youthfit.policy.domain.model.SkipReason.UNSUPPORTED_MIME);
    }

    private record StubExtractor(String mime, String returnText) implements AttachmentExtractor {
        @Override public boolean supports(String mediaType) { return mime.equalsIgnoreCase(mediaType); }
        @Override public ExtractionResult extract(java.io.InputStream s, long size) {
            return ExtractionResult.success(returnText);
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests ExtractionDispatcherTest -i`
Expected: COMPILE FAIL

- [ ] **Step 3: 구현 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.domain.model.SkipReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtractionDispatcher {

    private final List<AttachmentExtractor> extractors; // Spring 이 @Order 순으로 주입

    public ExtractionResult dispatch(InputStream stream, long sizeBytes, String mediaType) {
        return extractors.stream()
                .filter(e -> e.supports(mediaType))
                .findFirst()
                .map(e -> e.extract(stream, sizeBytes))
                .orElse(ExtractionResult.skipped(SkipReason.UNSUPPORTED_MIME));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests ExtractionDispatcherTest -i`
Expected: PASS

- [ ] **Step 5: 커밋 + Phase 3 마무리**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/ExtractionDispatcher.java backend/src/test/java/com/youthfit/ingestion/application/service/ExtractionDispatcherTest.java
git commit -m "$(cat <<'EOF'
feat(be): ExtractionDispatcher 추가

@Order 순으로 주입된 AttachmentExtractor 빈 중 첫 supports 통과 구현체
사용. 지원 없으면 UNSUPPORTED_MIME 으로 스킵.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

PR 권장: `[BE] feat: 정책 첨부 추출 — Tika/HWP Extractor (Phase 3/5)`

---

## Phase 4: Download Service + IngestionService 변경 (PR4 / 4h)

**범위**: AsyncConfig, AttachmentDownloadService (`@Async`), IngestionService 호출 추가, PolicyIngestionService 본문 hash 변경 시 재추출 마킹.

### Task 4.1: AsyncConfig

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentAsyncConfig.java`

- [ ] **Step 1: 설정 작성**

```java
package com.youthfit.ingestion.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AttachmentAsyncConfig {

    @Bean(name = "attachmentDownloadExecutor")
    public Executor attachmentDownloadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("attach-dl-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: 컴파일 확인 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentAsyncConfig.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentAsyncConfig 추가

@EnableAsync + 다운로드 전용 ThreadPoolTaskExecutor (core 4, max 8).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 4.2: AttachmentDownloadService.downloadOne (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentDownloadService.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/AttachmentHttpClient.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentDownloader.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentDownloadServiceTest.java`

> 설계: HTTP 다운로드 자체는 포트(`AttachmentDownloader`)로 추상화 → 단위 테스트에서는 stub, 실제 구현은 RestClient.

- [ ] **Step 1: AttachmentDownloader 포트 작성**

```java
package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentDownloader {
    DownloadedFile download(String url, long maxBytes);

    record DownloadedFile(InputStream stream, long sizeBytes, String detectedMediaType) implements AutoCloseable {
        @Override public void close() throws Exception { stream.close(); }
    }

    class DownloadException extends RuntimeException {
        public DownloadException(String msg, Throwable cause) { super(msg, cause); }
    }

    class OversizedException extends RuntimeException {
        public OversizedException(String msg) { super(msg); }
    }
}
```

- [ ] **Step 2: 단위 테스트 작성 (Mockito stub)**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentDownloadServiceTest {

    @Mock private PolicyAttachmentRepository repository;
    @Mock private PolicyAttachmentApplicationService stateService;
    @Mock private AttachmentStorage storage;
    @Mock private AttachmentDownloader downloader;
    @InjectMocks private AttachmentDownloadService sut;

    private PolicyAttachment attachment;

    @BeforeEach
    void setUp() {
        attachment = PolicyAttachment.builder()
                .name("a.pdf").url("http://x/a.pdf").mediaType("application/pdf")
                .build();
        sut.setMaxSizeMb(50);
        sut.setMimeWhitelist(Set.of("application/pdf"));
    }

    @Test
    void downloadOne_정상흐름_markDownloading_저장_markDownloaded() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));
        when(downloader.download(eq("http://x/a.pdf"), anyLong()))
                .thenReturn(new AttachmentDownloader.DownloadedFile(
                        new ByteArrayInputStream("data".getBytes()), 4, "application/pdf"));
        when(storage.put(any(), anyString(), eq("application/pdf")))
                .thenReturn(new StorageReference("attachments/1.pdf", 4, "deadbeef"));

        sut.downloadOne(1L);

        verify(stateService).markDownloading(1L);
        verify(stateService).markDownloaded(1L, "attachments/1.pdf", "deadbeef");
        verify(stateService, never()).markFailed(anyLong(), anyString());
        verify(stateService, never()).markSkipped(anyLong(), any());
    }

    @Test
    void downloadOne_화이트리스트_외_mediaType_은_SKIPPED() {
        attachment = PolicyAttachment.builder()
                .name("a.png").url("http://x/a.png").mediaType("image/png").build();
        when(repository.findById(2L)).thenReturn(Optional.of(attachment));

        sut.downloadOne(2L);

        verify(stateService).markDownloading(2L);
        verify(stateService).markSkipped(2L, SkipReason.UNSUPPORTED_MIME);
        verifyNoInteractions(downloader, storage);
    }

    @Test
    void downloadOne_OVERSIZED_시_SKIPPED() {
        when(repository.findById(3L)).thenReturn(Optional.of(attachment));
        when(downloader.download(anyString(), anyLong()))
                .thenThrow(new AttachmentDownloader.OversizedException("too big"));

        sut.downloadOne(3L);

        verify(stateService).markSkipped(3L, SkipReason.OVERSIZED);
    }

    @Test
    void downloadOne_HTTP_실패_시_FAILED() {
        when(repository.findById(4L)).thenReturn(Optional.of(attachment));
        when(downloader.download(anyString(), anyLong()))
                .thenThrow(new AttachmentDownloader.DownloadException("timeout", new RuntimeException()));

        sut.downloadOne(4L);

        verify(stateService).markFailed(eq(4L), contains("timeout"));
    }
}
```

- [ ] **Step 3: AttachmentDownloadService 구현**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadService.class);

    private final PolicyAttachmentRepository repository;
    private final PolicyAttachmentApplicationService stateService;
    private final AttachmentStorage storage;
    private final AttachmentDownloader downloader;

    @Setter
    @Value("${attachment.download.max-size-mb:50}")
    private int maxSizeMb;

    @Setter
    @Value("#{'${attachment.mime-whitelist:application/pdf,application/x-hwp,application/haansofthwp,application/vnd.hancom.hwp,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/html,text/plain}'.split(',')}")
    private Set<String> mimeWhitelist;

    @Async("attachmentDownloadExecutor")
    public void downloadForPolicyAsync(Long policyId) {
        List<PolicyAttachment> attachments = repository.findByPolicyId(policyId);
        for (PolicyAttachment a : attachments) {
            if (a.getExtractionStatus() == com.youthfit.policy.domain.model.AttachmentStatus.PENDING) {
                downloadOne(a.getId());
            }
        }
    }

    public void downloadOne(Long attachmentId) {
        PolicyAttachment a = repository.findById(attachmentId).orElse(null);
        if (a == null) {
            log.warn("attachment not found: {}", attachmentId);
            return;
        }

        try {
            stateService.markDownloading(attachmentId);
        } catch (IllegalStateException e) {
            log.debug("attachment already in flight: {}", attachmentId);
            return;
        }

        if (!isAllowed(a.getMediaType())) {
            stateService.markSkipped(attachmentId, SkipReason.UNSUPPORTED_MIME);
            return;
        }

        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        try (AttachmentDownloader.DownloadedFile file = downloader.download(a.getUrl(), maxBytes)) {
            String key = buildStorageKey(a);
            StorageReference ref = storage.put(file.stream(), key, a.getMediaType());
            stateService.markDownloaded(attachmentId, ref.key(), ref.sha256Hex());
            log.info("downloaded attachment: id={} key={} bytes={}", attachmentId, ref.key(), ref.sizeBytes());
        } catch (AttachmentDownloader.OversizedException e) {
            stateService.markSkipped(attachmentId, SkipReason.OVERSIZED);
            log.info("attachment oversized skipped: id={}", attachmentId);
        } catch (Exception e) {
            stateService.markFailed(attachmentId, e.getClass().getSimpleName() + ": " + safeMessage(e));
            log.warn("attachment download failed: id={} err={}", attachmentId, e.getMessage());
        }
    }

    private boolean isAllowed(String mediaType) {
        return mediaType != null && mimeWhitelist.contains(mediaType.toLowerCase());
    }

    private String buildStorageKey(PolicyAttachment a) {
        LocalDate now = LocalDate.now();
        String ext = inferExt(a.getMediaType(), a.getName());
        return String.format("attachments/%d/%02d/%d-%s.%s",
                now.getYear(), now.getMonthValue(), a.getId(),
                UUID.randomUUID().toString().substring(0, 8), ext);
    }

    private String inferExt(String mediaType, String name) {
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (mediaType == null ? "" : mediaType.toLowerCase()) {
            case "application/pdf" -> "pdf";
            case "application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp" -> "hwp";
            case "text/html" -> "html";
            case "text/plain" -> "txt";
            default -> "bin";
        };
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "no message" : (m.length() > 200 ? m.substring(0, 200) : m);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests AttachmentDownloadServiceTest -i`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentDownloader.java backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentDownloadService.java backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentDownloadServiceTest.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentDownloadService 추가

downloadOne(id): 단일 첨부 다운로드 (sync, 스케줄러/테스트용)
downloadForPolicyAsync(policyId): @Async, ingestion 직후 호출
화이트리스트 / 50MB / 네트워크 오류 분기 모두 적절한 상태 전이 호출.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 4.3: AttachmentHttpClient (RestClient 기반 다운로더 구현)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/AttachmentHttpClient.java`

- [ ] **Step 1: 구현 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Component
public class AttachmentHttpClient implements AttachmentDownloader {

    private final RestClient restClient;

    public AttachmentHttpClient(
            @Value("${attachment.download.connect-timeout-seconds:10}") int connectTimeout,
            @Value("${attachment.download.read-timeout-seconds:60}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        this.restClient = RestClient.builder()
                .requestFactory((ClientHttpRequestFactory) factory)
                .build();
    }

    @Override
    public DownloadedFile download(String url, long maxBytes) {
        try {
            byte[] body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);
            if (body == null) throw new DownloadException("empty body: " + url, null);
            if (body.length > maxBytes) throw new OversizedException("size=" + body.length + " > max=" + maxBytes);
            String contentType = guessContentType(url);
            return new DownloadedFile(new ByteArrayInputStream(body), body.length, contentType);
        } catch (OversizedException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadException("download failed: " + url, e);
        }
    }

    private String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".hwp")) return "application/x-hwp";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
```

> **Note**: 메모리 기반 (byte[]) 다운로드 — 50MB 한도 검증 직관적. 향후 스트리밍/chunked 한도 검증이 필요하면 별도 리팩토링.

- [ ] **Step 2: 컴파일 확인 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/AttachmentHttpClient.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentHttpClient — RestClient 기반 첨부 다운로더 구현

connect/read timeout 환경변수, 50MB 한도 후속 검증, URL 확장자로
contentType 추정. 메모리 기반 (byte[]) — v0 충분.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 4.4: IngestionService 변경 — 다운로드 트리거 호출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: receivePolicy 끝에 호출 추가**

`IngestionService.java` 의 `receivePolicy` 메서드 끝, return 전에:

기존:
```java
        PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
        triggerGuideGeneration(ingestionResult.policyId(), command.title());

        return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

변경:
```java
        PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
        triggerGuideGeneration(ingestionResult.policyId(), command.title());
        triggerAttachmentDownload(ingestionResult.policyId());

        return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

그리고 클래스 하단에 메서드 추가:

```java
    private void triggerAttachmentDownload(Long policyId) {
        if (policyId == null) return;
        try {
            attachmentDownloadService.downloadForPolicyAsync(policyId);
        } catch (Exception e) {
            log.warn("첨부 다운로드 트리거 실패: policyId={}", policyId, e);
        }
    }
```

또한 클래스 상단에 의존성 주입:
- `private final AttachmentDownloadService attachmentDownloadService;`
- import 추가: `import com.youthfit.ingestion.application.service.AttachmentDownloadService;`

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 기존 IngestionService 단위 테스트가 있다면 mock 추가**

```bash
ls backend/src/test/java/com/youthfit/ingestion/application/service/IngestionService*
```

- 있으면 `@Mock private AttachmentDownloadService attachmentDownloadService;` 추가
- 없으면 스킵 (이번 변경으로 deps 추가만, 동작 변경 무 — 새 호출 추가됨)

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
# 테스트 변경이 있다면:
# git add backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "$(cat <<'EOF'
feat(be): IngestionService 에 첨부 다운로드 트리거 추가

receivePolicy 끝에 attachmentDownloadService.downloadForPolicyAsync()
호출. 다운로드 트리거 실패는 ingestion 자체를 막지 않음 (catch+log).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 4.5: PolicyIngestionService — 본문 hash 변경 시 재추출 마킹

**Files:**
- Read: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`

- [ ] **Step 1: 현재 코드 확인**

Run: `cat backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java | head -100`

확인 포인트:
- 정책이 같은 `externalId` 로 다시 들어왔을 때 `sourceHash` 비교 후 본문 업데이트 하는지
- 업데이트 분기 위치

- [ ] **Step 2: 본문 변경 감지 분기에 markPendingReextraction 호출 추가**

기존 분기 (예시 — 실제 코드와 라인 다를 수 있음):
```java
if (existing != null) {
    if (!existing.getSourceHash().equals(command.sourceHash())) {
        existing.updateContent(...);  // 또는 동등 메서드
    }
    ...
}
```

변경:
```java
if (existing != null) {
    if (!existing.getSourceHash().equals(command.sourceHash())) {
        existing.updateContent(...);
        policyAttachmentApplicationService.markPendingReextraction(existing.getId());
    }
    ...
}
```

의존성 주입 추가:
```java
private final PolicyAttachmentApplicationService policyAttachmentApplicationService;
```

> **주의**: 동일 트랜잭션 안에서 호출해야 함 (이미 PolicyIngestionService 가 @Transactional 일 것). markPendingReextraction 자체도 @Transactional 이지만 propagation REQUIRED 이라 자연스럽게 같은 TX 에 합류.

- [ ] **Step 3: 빌드 확인**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL (테스트 포함)

- [ ] **Step 4: 커밋 + Phase 4 마무리**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java
git commit -m "$(cat <<'EOF'
feat(be): 정책 본문 hash 변경 시 첨부 강제 재추출

PolicyIngestionService 가 sourceHash 다름 감지 시 그 정책의 모든 첨부를
PENDING 으로 되돌림. 다음 스케줄러 사이클이 자동으로 재처리.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

PR 권장: `[BE] feat: 정책 첨부 추출 — Download (Phase 4/5)`

---

## Phase 5: Scheduler + Reindex (PR5 / 4h)

### Task 5.1: SchedulingConfig

**Files:**
- Create or modify: `backend/src/main/java/com/youthfit/common/config/SchedulingConfig.java`

> 이미 `@EnableScheduling` 이 있는지 먼저 확인:
> Run: `grep -rln "@EnableScheduling" backend/src/main/java`

있으면 Task 5.1 스킵, 없으면 다음.

- [ ] **Step 1: 설정 작성**

```java
package com.youthfit.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

- [ ] **Step 2: 컴파일 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/common/config/SchedulingConfig.java
git commit -m "$(cat <<'EOF'
feat(be): @EnableScheduling 활성화

정책 첨부 추출 스케줄러를 위한 글로벌 스케줄링 활성화.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 5.2: AttachmentReindexService (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java`

- [ ] **Step 1: 합치기/200KB 가드 테스트 먼저**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import com.youthfit.guide.application.service.GuideGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentReindexServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository attachmentRepository;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private GuideGenerationService guideGenerationService;
    @InjectMocks private AttachmentReindexService sut;

    @BeforeEach
    void setUp() {
        sut.setMaxContentKb(200);
    }

    @Test
    void reindex_정상_정책본문과_첨부텍스트를_합쳐_RagIndexing_호출() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("정책 본문");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        PolicyAttachment a1 = pa("공고문.pdf", "내용 A");
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(a1));
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 5, true));

        sut.reindex(1L);

        ArgumentCaptor<IndexPolicyDocumentCommand> captor = ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        verify(ragIndexingService).indexPolicyDocument(captor.capture());
        String content = captor.getValue().content();
        assertThat(content).contains("정책 본문");
        assertThat(content).contains("공고문.pdf");
        assertThat(content).contains("내용 A");

        verify(guideGenerationService).generateGuide(any());
    }

    @Test
    void reindex_wasIndexed_false_면_가이드_재생성_안함() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("body");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 0, false));

        sut.reindex(1L);

        verify(guideGenerationService, never()).generateGuide(any());
    }

    @Test
    void reindex_200KB_초과_시_초과분_첨부_생략() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("본문");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        PolicyAttachment big1 = pa("a.pdf", "X".repeat(150_000));
        PolicyAttachment big2 = pa("b.pdf", "Y".repeat(100_000));
        PolicyAttachment skipped = pa("c.pdf", "Z".repeat(50_000));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(big1, big2, skipped));
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 1, true));

        sut.reindex(1L);

        ArgumentCaptor<IndexPolicyDocumentCommand> captor = ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        verify(ragIndexingService).indexPolicyDocument(captor.capture());
        String content = captor.getValue().content();
        // 200KB = 200 * 1024 = 204800
        assertThat(content.length()).isLessThanOrEqualTo(204_800);
        assertThat(content).contains("a.pdf");  // 첫 첨부는 들어감
        // 두 번째 / 세 번째는 잘려서 부분 들어가거나 빠짐
    }

    private PolicyAttachment pa(String name, String text) {
        PolicyAttachment a = mock(PolicyAttachment.class);
        when(a.getName()).thenReturn(name);
        when(a.getExtractedText()).thenReturn(text);
        return a;
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests AttachmentReindexServiceTest -i`
Expected: COMPILE FAIL

- [ ] **Step 3: 구현 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttachmentReindexService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentReindexService.class);

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final RagIndexingService ragIndexingService;
    private final GuideGenerationService guideGenerationService;

    @Setter
    @Value("${attachment.reindex.max-content-kb:200}")
    private int maxContentKb;

    public void reindex(Long policyId) {
        Optional<Policy> policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isEmpty()) {
            log.warn("policy not found for reindex: {}", policyId);
            return;
        }
        Policy policy = policyOpt.get();

        List<PolicyAttachment> attachments = attachmentRepository.findExtractedByPolicyId(policyId);
        String merged = mergeContent(policy, attachments);

        IndexPolicyDocumentCommand cmd = new IndexPolicyDocumentCommand(policyId, merged);
        IndexingResult result = ragIndexingService.indexPolicyDocument(cmd);
        log.info("reindex policyId={} chunks={} wasIndexed={}", policyId, result.chunkCount(), result.wasIndexed());

        if (result.wasIndexed()) {
            guideGenerationService.generateGuide(new GenerateGuideCommand(policyId, policy.getTitle(), null));
            log.info("guide regenerated: policyId={}", policyId);
        }
    }

    String mergeContent(Policy policy, List<PolicyAttachment> attachments) {
        long maxBytes = (long) maxContentKb * 1024L;
        StringBuilder sb = new StringBuilder();
        sb.append("=== 정책 본문 ===\n");
        sb.append(policy.getBody() == null ? "" : policy.getBody());

        long remaining = maxBytes - sb.length();
        for (PolicyAttachment a : attachments) {
            String header = "\n\n=== 첨부: " + a.getName() + " ===\n";
            String body = a.getExtractedText() == null ? "" : a.getExtractedText();
            long needed = header.length() + body.length();
            if (needed <= remaining) {
                sb.append(header).append(body);
                remaining -= needed;
            } else if (remaining > header.length() + 50) {
                int allowedBody = (int) (remaining - header.length());
                sb.append(header).append(body, 0, allowedBody);
                log.info("attachment truncated: id={} truncated_to={}/{}", a.getId(), allowedBody, body.length());
                remaining = 0;
                break;
            } else {
                log.info("attachment skipped from reindex (no room): id={}", a.getId());
                break;
            }
        }
        return sb.toString();
    }
}
```

> **Note**: `IndexPolicyDocumentCommand` / `IndexingResult` / `GenerateGuideCommand` 이미 존재하는지 확인 필요 (기존 코드에 있음). `Policy.getTitle()` / `getBody()` 도 기존 entity 메서드. `PolicyRepository.findById` 가 도메인 포트에 있는지 확인 — 없으면 추가 task 끼워 넣기.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests AttachmentReindexServiceTest -i`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentReindexService 추가

정책별 본문 + EXTRACTED 첨부 텍스트를 200KB 가드 적용해 합치고
RagIndexingService 호출. wasIndexed=true 면 가이드 재생성.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 5.3: AttachmentExtractionScheduler

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentExtractionScheduler.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentExtractionSchedulerTest.java`

- [ ] **Step 1: 사이클 구조 테스트 먼저**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentExtractionSchedulerTest {

    @Mock private PolicyAttachmentRepository repository;
    @Mock private PolicyAttachmentApplicationService stateService;
    @Mock private AttachmentStorage storage;
    @Mock private ExtractionDispatcher dispatcher;
    @Mock private AttachmentDownloadService downloadService;
    @Mock private AttachmentReindexService reindexService;
    @InjectMocks private AttachmentExtractionScheduler sut;

    @BeforeEach
    void setUp() {
        sut.setBatchSize(20);
        sut.setRetryLimit(3);
        sut.setMinTextChars(100);
    }

    @Test
    void runCycle_4_1_resetFailedToPending_먼저() {
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of());

        sut.runCycle();

        verify(stateService).resetFailedToPending(20, 3);
    }

    @Test
    void runCycle_4_2_PENDING_은_downloadOne_으로_위임() {
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(1L);
        when(repository.findPendingForDownload(20)).thenReturn(List.of(p));
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of());

        sut.runCycle();

        verify(downloadService).downloadOne(1L);
    }

    @Test
    void runCycle_4_3_DOWNLOADED_은_dispatcher_호출_후_markExtracted() throws Exception {
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(2L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(mockPolicy(99L));
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("X".repeat(150)));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(true);

        sut.runCycle();

        verify(stateService).markExtracting(2L);
        verify(stateService).markExtracted(2L, "X".repeat(150));
        verify(reindexService).reindex(99L);
    }

    @Test
    void runCycle_추출_텍스트_100자_미만이면_SCANNED_PDF_skip() throws Exception {
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(3L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(mockPolicy(99L));
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("짧다"));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(true);

        sut.runCycle();

        verify(stateService).markSkipped(3L, SkipReason.SCANNED_PDF);
        verify(stateService, never()).markExtracted(anyLong(), anyString());
    }

    @Test
    void runCycle_정책의_모든_첨부가_종료되지_않으면_reindex_안함() throws Exception {
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(4L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(mockPolicy(99L));
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("X".repeat(200)));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(false);

        sut.runCycle();

        verify(reindexService, never()).reindex(anyLong());
    }

    private com.youthfit.policy.domain.model.Policy mockPolicy(long id) {
        com.youthfit.policy.domain.model.Policy policy = mock(com.youthfit.policy.domain.model.Policy.class);
        when(policy.getId()).thenReturn(id);
        return policy;
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests AttachmentExtractionSchedulerTest -i`
Expected: COMPILE FAIL

- [ ] **Step 3: 구현 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentExtractionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentExtractionScheduler.class);

    private final PolicyAttachmentRepository repository;
    private final PolicyAttachmentApplicationService stateService;
    private final AttachmentStorage storage;
    private final ExtractionDispatcher dispatcher;
    private final AttachmentDownloadService downloadService;
    private final AttachmentReindexService reindexService;

    @Setter
    @Value("${attachment.scheduler.batch-size:20}")
    private int batchSize;

    @Setter
    @Value("${attachment.extraction.retry-limit:3}")
    private int retryLimit;

    @Setter
    @Value("${attachment.extraction.min-text-chars:100}")
    private int minTextChars;

    @Scheduled(fixedDelayString = "${attachment.scheduler.fixed-delay-ms:60000}")
    public void runCycle() {
        // 4-1. FAILED 중 retryCount<3 → PENDING
        stateService.resetFailedToPending(batchSize, retryLimit);

        // 4-2. PENDING 다운로드 (백필 / @Async 실패 fallback)
        for (PolicyAttachment p : repository.findPendingForDownload(batchSize)) {
            downloadService.downloadOne(p.getId());
        }

        // 4-3. DOWNLOADED → 추출
        Set<Long> reindexCandidates = new HashSet<>();
        for (PolicyAttachment p : repository.findDownloadedForExtraction(batchSize)) {
            extractOne(p, reindexCandidates);
        }

        // 4-4. 정책별 완료 체크 → 재인덱스
        for (Long policyId : reindexCandidates) {
            if (repository.isAllTerminalForPolicy(policyId)) {
                try {
                    reindexService.reindex(policyId);
                } catch (Exception e) {
                    log.warn("reindex failed: policyId={}", policyId, e);
                }
            }
        }
    }

    private void extractOne(PolicyAttachment p, Set<Long> reindexCandidates) {
        Long id = p.getId();
        try {
            stateService.markExtracting(id);
        } catch (IllegalStateException e) {
            log.debug("extracting already in flight: {}", id);
            return;
        }

        try (InputStream in = storage.get(p.getStorageKey())) {
            ExtractionResult result = dispatcher.dispatch(in, 0, p.getMediaType());
            switch (result) {
                case ExtractionResult.Success s -> {
                    String text = s.text();
                    if (text == null || text.length() < minTextChars) {
                        stateService.markSkipped(id, SkipReason.SCANNED_PDF);
                    } else {
                        stateService.markExtracted(id, text);
                    }
                }
                case ExtractionResult.Skipped sk -> stateService.markSkipped(id, sk.reason());
                case ExtractionResult.Failed f -> stateService.markFailed(id, f.error());
            }
        } catch (Exception e) {
            stateService.markFailed(id, e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (p.getPolicy() != null) {
            reindexCandidates.add(p.getPolicy().getId());
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests AttachmentExtractionSchedulerTest -i`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentExtractionScheduler.java backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentExtractionSchedulerTest.java
git commit -m "$(cat <<'EOF'
feat(be): AttachmentExtractionScheduler 추가

@Scheduled 사이클 — resetFailedToPending → PENDING 다운로드 (fallback)
→ DOWNLOADED 추출 → 정책별 완료 체크 → reindex.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 5.4: 통합 테스트 — e2e 사이클 (선택적, 시간 허용 시)

**Files:**
- Test: `backend/src/test/java/com/youthfit/ingestion/integration/AttachmentPipelineIntegrationTest.java`

> **시간이 부족하면 스킵 가능.** 단위 테스트가 이미 각 단계 커버. 통합 테스트는 ingestion → DB → scheduler 사이클 전체 검증.

- [ ] **Step 1: @SpringBootTest + Testcontainers (있으면) 또는 H2 in-memory**

```java
package com.youthfit.ingestion.integration;

// (시간 허용 시) WireMock 으로 첨부 URL stub, fixture PDF 제공,
// IngestionService.receivePolicy() 호출, scheduler 한 사이클 수동 실행,
// 최종 상태 EXTRACTED 검증, 가이드 재생성 호출 검증 (mock)
```

> 이 task 는 Phase 5 끝까지 시간 확인 후 결정. 완성도 vs 시간 트레이드오프.

### Task 5.5: 환경/배포 가이드 문서

**Files:**
- Modify: `docs/OPS.md` (있으면) 또는 Create: `docs/superpowers/operations/2026-04-28-attachment-extraction-runbook.md`

- [ ] **Step 1: 운영 노트 작성**

추가 내용:
- 환경변수 일람 (Phase 2 §Task 2.5 의 application.yml 참조)
- 첫 배포 백필 절차: `UPDATE policy_attachment SET extraction_status='PENDING'`
- 모니터링 키워드 (slf4j 로그)
- 로컬 개발 시 `attachment.storage.local-path` 마운트
- S3 키 발급 후 전환 절차 (env 변수만 변경)
- 알려진 한계: HWP 추출 품질 불안정, 스캔 PDF 처리 안 됨

- [ ] **Step 2: 커밋 + Phase 5 마무리**

```bash
git add docs/...
git commit -m "$(cat <<'EOF'
docs(be): 첨부 추출 파이프라인 운영 가이드 추가

환경변수, 백필 절차, 모니터링, S3 전환 절차 정리.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

PR 권장: `[BE] feat: 정책 첨부 추출 — Scheduler + Reindex (Phase 5/5)`

---

## Phase 6: 통합 검증 (선택적, 1h)

### Task 6.1: 로컬에서 e2e 동작 확인

- [ ] **Step 1: 백엔드 재빌드 + 기동**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
docker compose up -d --build backend
sleep 20
docker compose logs backend | tail -100
```

- [ ] **Step 2: 백필 실행 + 스케줄러 사이클 대기**

```bash
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "UPDATE policy_attachment SET extraction_status='PENDING', extraction_retry_count=0 WHERE extraction_status='PENDING' OR extraction_status IS NULL;"

# 1~2분 후
docker compose logs backend | grep -i "attach"
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT id, name, extraction_status, extraction_retry_count, skip_reason FROM policy_attachment;"
```

Expected: 일부 EXTRACTED, 일부 SKIPPED, 일부 FAILED. 모두 PENDING 그대로면 스케줄러 동작 안 함 — 로그 확인.

- [ ] **Step 3: 추출 결과 가이드 반영 확인**

```bash
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT policy_id, length(content) FROM policy_document ORDER BY policy_id LIMIT 10;"
```

기존 (Phase 1 전) length 와 비교 — 첨부 텍스트만큼 길어졌으면 정상.

- [ ] **Step 4: 결과 정리 → 사용자 보고**

---

## 자가 점검 (Plan Self-Review)

### Spec coverage

- §3 아키텍처 → Phase 1-5 모듈별 구성 ✓
- §4 도메인 모델 → Task 1.1-1.3 ✓
- §5 컴포넌트 → Phase 1-5 전반 ✓
- §6 데이터 흐름 → Task 4.4 (ingestion 트리거), 5.3 (스케줄러 사이클), 5.2 (reindex) ✓
- §7 에러/리트라이 → Task 1.3 (도메인 메서드), 4.2 (downloadService 분기), 5.3 (스케줄러) ✓
- §8 비용 가드 → Task 2.5 (env), 4.2 (50MB), 5.2 (200KB), 5.3 (100자) ✓
- §9 테스트 → 각 Task 의 TDD step ✓
- §10 운영 → Task 5.5 ✓
- §11 마이그레이션 → Task 1.6 (백필) ✓
- §12 의존성 → Task 2.3 (AWS), 3.1 (Tika/Hwp) ✓
- §13 PR 분할 → Phase 1-5 종료 시점 PR 권장 ✓
- §16 미결 → Phase 0 (가이드 hash 가드 확인) ✓

### Placeholder scan

- TBD/TODO/이후 처리 없음 — 모든 step 에 코드 또는 명령 포함 ✓
- 단 Task 5.4 통합 테스트는 시간 허용 시 옵션으로 표기 (의도된 deferral)

### Type consistency

- `AttachmentStatus` enum 값 7개 (Phase 1-3 / Phase 4-5 일관) ✓
- `markDownloading()` / `markDownloaded(key, hash)` 시그니처 — 모든 호출 동일 ✓
- `IndexPolicyDocumentCommand` / `IndexingResult` 기존 코드 재사용 — Task 5.2 검증 시점 확인 필요 (실제 시그니처 다르면 plan 수정)
- `ExtractionResult` sealed — Success / Skipped / Failed 일관 ✓

---

## Plan 완료 후 Execution

이 시점에서 plan 은 완성. 다음 두 가지 실행 옵션 중 선택:

**1. Subagent-Driven Execution (추천)**: superpowers:subagent-driven-development 스킬로 task 별 fresh subagent 발사, 단계마다 두 단계 리뷰 (spec 준수 + 코드 품질).

**2. Inline Execution**: superpowers:executing-plans 스킬로 본 세션에서 직접 task 진행, batch 별 체크포인트.

