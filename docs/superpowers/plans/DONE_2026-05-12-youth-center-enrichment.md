# Youth Center Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년 서울 스코프 정책의 변동분에 한해 n8n 안에서 외부 정책 안내 페이지를 HTTP fetch → cheerio boilerplate 제거 → OpenAI gpt-4o-mini 로 구조화 추출한 결과(`enrichment`)를 백엔드가 수신·저장하고, 신뢰 임계값을 통과한 경우에만 별도 섹션·라벨로 사용자에게 노출한다.

**Architecture:** n8n 이 풍부화의 주체이며 백엔드는 `enrichment` 객체를 수신·저장만 한다. 변동 식별을 위해 백엔드가 `GET /api/internal/ingestion/policies/external-hashes` 를 새로 노출하고, intake 스키마에 `enrichment` 선택 필드를 추가한다. `policy.enrichment` 컬럼(jsonb)을 신설하고, 응답 DTO 는 `status="OK"` AND `confidence >= 0.6` 일 때만 노출한다. 프론트엔드는 새 컴포넌트 `PolicyEnrichmentSection` 으로 출처 라벨·원문 링크와 함께 분리 표시한다.

**Tech Stack:** Java 21 / Spring Boot 4.0.5 / Hibernate JsonType / PostgreSQL 17 / React + TypeScript / n8n (Code 노드 + cheerio + OpenAI node)

**Spec:** `docs/superpowers/specs/2026-05-12-youth-center-enrichment-design.md`

**Naming note:** 스펙에서 `contentHash` 라 부른 것은 코드베이스의 기존 `PolicySource.sourceHash` 와 동일 개념이다. n8n 페이로드 키, intake DTO 필드, DB 컬럼 모두 **`sourceHash` 이름 그대로 사용**한다. 단, n8n 이 직접 hash 를 계산해 보내는 것은 새 동작이다 (기존엔 백엔드가 raw JSON 직렬화 결과로 자체 계산).

---

## File Structure

### 백엔드 — 신규/수정

| 파일 | 종류 | 책임 |
|------|------|------|
| `backend/src/main/resources/sql/2026-05-12-policy-enrichment.sql` | 신규 | `policy.enrichment jsonb` 컬럼 추가 |
| `backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java` | 신규 | 풍부화 값 객체 (jsonb 매핑 대상) |
| `backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentStatus.java` | 신규 | enum (`OK`, `NO_LINK`, `FETCH_FAILED`, `TOO_SHORT`, `LLM_FAILED`, `PARSE_FAILED`, `LOW_CONFIDENCE`) |
| `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java` | 수정 | enrichment 필드 + 매핑 + replaceEnrichment 메서드 |
| `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java` | 수정 | `findExternalIdHashMap(SourceType)` 메서드 |
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java` | 수정 | 위 인터페이스 구현용 쿼리 메서드 |
| `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java` | 수정 | 위 구현 |
| `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java` | 수정 | `rawData.enrichment`, `rawData.sourceHash` optional 필드 추가 + `toCommand()` 확장 |
| `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java` | 수정 | `enrichment`, `providedSourceHash` 필드 추가 |
| `backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java` | 수정 | `enrichment` 필드 추가 |
| `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java` | 수정 | `enrichment` 필드 추가 |
| `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java` | 수정 | enrichment 저장 매핑 |
| `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java` | 수정 | enrichment 마스킹 (status/confidence) |
| `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java` | 수정 | `findExternalIdHashes`, providedSourceHash 우선 사용, enrichment 매핑 |
| `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalApi.java` | 수정 | GET external-hashes Swagger 시그니처 |
| `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java` | 수정 | GET external-hashes 매핑 |
| `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java` | 수정 | enrichment 필드 노출 + 마스킹 결과 반영 |

### 백엔드 — 테스트

| 파일 | 종류 |
|------|------|
| `backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerTest.java` | 수정(있다면) 또는 신규 |
| `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceEnrichmentTest.java` | 신규 |
| `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceEnrichmentTest.java` | 신규 |

### 프론트엔드 — 신규/수정

| 파일 | 종류 | 책임 |
|------|------|------|
| `frontend/src/types/policy.ts` (또는 동등 경로) | 수정 | `PolicyEnrichment` 타입 추가 |
| `frontend/src/components/policy/PolicyEnrichmentSection.tsx` | 신규 | enrichment 섹션 렌더링 |
| `frontend/src/pages/PolicyDetailPage.tsx` | 수정 | PolicyEnrichmentSection 통합 |
| `frontend/src/components/policy/__tests__/PolicyEnrichmentSection.test.tsx` | 신규 | 단위 테스트 |

### n8n

| 파일 | 종류 |
|------|------|
| `n8n/workflows/youth-center-seoul.json` | 수정 (노드 추가) |

---

## Task 1 — DB 마이그레이션: `policy.enrichment` 컬럼

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-12-policy-enrichment.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- 2026-05-12 youth-center enrichment 컬럼 추가
ALTER TABLE policy
    ADD COLUMN IF NOT EXISTS enrichment JSONB;

COMMENT ON COLUMN policy.enrichment IS
    '온통청년 외부 페이지에서 LLM 으로 자동 추출한 보조 정보. sections/extraAttachments/status/confidence/sourceUrl/fetchedAt/extractor 포함';
```

- [ ] **Step 2: 로컬 DB 에 적용해 검증**

```bash
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-12-policy-enrichment.sql
psql "$YOUTHFIT_DB_URL" -c "\d policy" | grep enrichment
```
Expected: `enrichment | jsonb |` 출력

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/sql/2026-05-12-policy-enrichment.sql
git commit -m "feat(policy): add enrichment jsonb column"
```

---

## Task 2 — EnrichmentStatus enum + PolicyEnrichment 값 객체

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentStatus.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java`

- [ ] **Step 1: EnrichmentStatus 작성**

```java
package com.youthfit.policy.domain.model;

public enum EnrichmentStatus {
    OK,
    NO_LINK,
    FETCH_FAILED,
    TOO_SHORT,
    LLM_FAILED,
    PARSE_FAILED,
    LOW_CONFIDENCE
}
```

- [ ] **Step 2: PolicyEnrichment 값 객체 작성**

```java
package com.youthfit.policy.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record PolicyEnrichment(
        String sourceUrl,
        Instant fetchedAt,
        String extractor,
        Double confidence,
        EnrichmentStatus status,
        Sections sections,
        List<ExtraAttachment> extraAttachments
) {
    public static final double EXPOSURE_CONFIDENCE_THRESHOLD = 0.6;

    public boolean isExposable() {
        return status == EnrichmentStatus.OK
                && confidence != null
                && confidence >= EXPOSURE_CONFIDENCE_THRESHOLD;
    }

    public record Sections(
            String supportTarget,
            String supportContent,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote
    ) {
        @JsonCreator
        public Sections(
                @JsonProperty("supportTarget") String supportTarget,
                @JsonProperty("supportContent") String supportContent,
                @JsonProperty("applyMethod") String applyMethod,
                @JsonProperty("requiredDocuments") String requiredDocuments,
                @JsonProperty("deadlineNote") String deadlineNote
        ) {
            this.supportTarget = supportTarget;
            this.supportContent = supportContent;
            this.applyMethod = applyMethod;
            this.requiredDocuments = requiredDocuments;
            this.deadlineNote = deadlineNote;
        }
    }

    public record ExtraAttachment(String name, String url) {}
}
```

- [ ] **Step 3: 단위 테스트 — isExposable 분기**

```java
// backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyEnrichmentTest {

    @Test
    void status_ok_and_confidence_above_threshold_is_exposable() {
        var e = new PolicyEnrichment("https://x", Instant.now(), "openai:gpt-4o-mini",
                0.8, EnrichmentStatus.OK, null, List.of());
        assertThat(e.isExposable()).isTrue();
    }

    @Test
    void status_ok_but_low_confidence_is_not_exposable() {
        var e = new PolicyEnrichment("https://x", Instant.now(), "openai:gpt-4o-mini",
                0.4, EnrichmentStatus.OK, null, List.of());
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void non_ok_status_is_not_exposable_even_if_high_confidence() {
        var e = new PolicyEnrichment("https://x", Instant.now(), "openai:gpt-4o-mini",
                0.95, EnrichmentStatus.LOW_CONFIDENCE, null, List.of());
        assertThat(e.isExposable()).isFalse();
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd backend && ./gradlew test --tests "*PolicyEnrichmentTest"
```
Expected: 3 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentStatus.java \
        backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java \
        backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java
git commit -m "feat(policy): add PolicyEnrichment value object and status enum"
```

---

## Task 3 — Policy 엔티티에 enrichment 매핑

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`

- [ ] **Step 1: enrichment 필드 + jsonb 매핑 추가**

`Policy.java` 의 attachments 선언 아래 (line 148 근처)에 추가:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "enrichment", columnDefinition = "jsonb")
private PolicyEnrichment enrichment;
```

- [ ] **Step 2: replaceEnrichment 비즈니스 메서드 추가**

`replaceAttachments(...)` 메서드 아래에 추가:

```java
public void replaceEnrichment(PolicyEnrichment newEnrichment) {
    this.enrichment = newEnrichment;
}
```

- [ ] **Step 3: Builder 시그니처는 변경하지 않음 (enrichment 는 등록 후 별도 셋팅)**

이유: enrichment 는 정책 등록·갱신과 별개 라이프사이클 (실패 시 null, 성공 시 객체). Builder 에 끼우지 않고 `replaceEnrichment` 로만 셋팅한다.

- [ ] **Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/Policy.java
git commit -m "feat(policy): map enrichment jsonb column on Policy entity"
```

---

## Task 4 — PolicySourceRepository: external-id → sourceHash 맵 조회

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java`

- [ ] **Step 1: 도메인 리포지토리 인터페이스 시그니처 추가**

`PolicySourceRepository` 에 추가:

```java
import java.util.Map;
...
Map<String, String> findExternalIdHashMap(SourceType sourceType);
```

- [ ] **Step 2: JPA 리포지토리에 쿼리 메서드 추가**

`PolicySourceJpaRepository` 에 추가:

```java
import org.springframework.data.jpa.repository.Query;
...
@Query("""
       select s.externalId as eid, s.sourceHash as hash
       from PolicySource s
       where s.sourceType = :sourceType
       """)
List<Object[]> findExternalIdAndHashBySourceType(@Param("sourceType") SourceType sourceType);
```

- [ ] **Step 3: Adapter 구현에서 Map 으로 변환**

`PolicySourceRepositoryImpl` 에 추가:

```java
@Override
public Map<String, String> findExternalIdHashMap(SourceType sourceType) {
    List<Object[]> rows = jpaRepository.findExternalIdAndHashBySourceType(sourceType);
    Map<String, String> result = new HashMap<>(rows.size());
    for (Object[] row : rows) {
        result.put((String) row[0], (String) row[1]);
    }
    return result;
}
```

- [ ] **Step 4: 단위 테스트 (DataJpaTest 슬라이스)**

```java
// backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryExternalHashesTest.java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PolicySourceRepositoryExternalHashesTest {

    @Autowired private PolicySourceRepositoryImpl repository;

    @Test
    void returns_empty_map_when_no_sources_for_type() {
        Map<String, String> hashes = repository.findExternalIdHashMap(SourceType.YOUTH_CENTER);
        assertThat(hashes).isEmpty();
    }
    // TODO 다른 테스트는 기존 fixture 패턴 (testdata.Fixtures 등) 으로 작성
}
```

- [ ] **Step 5: 빌드·테스트 통과**

```bash
cd backend && ./gradlew test --tests "*PolicySourceRepositoryExternalHashesTest"
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicySourceRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceJpaRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryImpl.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/persistence/PolicySourceRepositoryExternalHashesTest.java
git commit -m "feat(policy): add findExternalIdHashMap repository method"
```

---

## Task 5 — Request/Command DTO 에 enrichment, sourceHash 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java`

- [ ] **Step 1: IngestPolicyRequest.RawData 에 필드 추가**

`RawData` record 시그니처 끝에 추가:

```java
public record RawData(
        ... 기존 필드 유지 ...,
        @Valid RawCodes rawCodes,
        // ── NEW ──
        String sourceHash,             // n8n 이 계산한 hash. 없으면 백엔드가 계산
        @Valid EnrichmentPayload enrichment
) {}

public record EnrichmentPayload(
        @NotBlank String sourceUrl,
        @NotNull LocalDateTime fetchedAt,
        @NotBlank String extractor,
        Double confidence,
        @NotBlank String status,
        EnrichmentSectionsPayload sections,
        List<ExtraAttachmentPayload> extraAttachments
) {}

public record EnrichmentSectionsPayload(
        String supportTarget,
        String supportContent,
        String applyMethod,
        String requiredDocuments,
        String deadlineNote
) {}

public record ExtraAttachmentPayload(
        @NotBlank String name,
        @NotBlank String url
) {}
```

- [ ] **Step 2: IngestPolicyCommand 에 필드 추가**

```java
public record IngestPolicyCommand(
        ... 기존 필드 유지 ...,
        RawCodes rawCodes,
        // ── NEW ──
        String providedSourceHash,
        PolicyEnrichment enrichment
) {
    ... (기존 nested records 유지) ...
}
```

import 추가: `import com.youthfit.policy.domain.model.PolicyEnrichment;`

- [ ] **Step 3: IngestPolicyRequest.toCommand() 확장**

기존 호출 끝에 enrichment·sourceHash 매핑 추가. 매핑 시 status 문자열을 enum 으로 변환, 알 수 없는 값이면 null:

```java
public IngestPolicyCommand toCommand() {
    return new IngestPolicyCommand(
            ... 기존 인자 ...,
            rawData.rawCodes() == null ? null : new IngestPolicyCommand.RawCodes(...),
            rawData.sourceHash(),
            mapEnrichment(rawData.enrichment())
    );
}

private static PolicyEnrichment mapEnrichment(EnrichmentPayload p) {
    if (p == null) return null;
    EnrichmentStatus status;
    try {
        status = EnrichmentStatus.valueOf(p.status());
    } catch (IllegalArgumentException e) {
        return null;
    }
    PolicyEnrichment.Sections sections = p.sections() == null ? null
            : new PolicyEnrichment.Sections(
                    p.sections().supportTarget(),
                    p.sections().supportContent(),
                    p.sections().applyMethod(),
                    p.sections().requiredDocuments(),
                    p.sections().deadlineNote()
            );
    List<PolicyEnrichment.ExtraAttachment> atts = p.extraAttachments() == null ? List.of()
            : p.extraAttachments().stream()
                    .map(a -> new PolicyEnrichment.ExtraAttachment(a.name(), a.url()))
                    .toList();
    return new PolicyEnrichment(
            p.sourceUrl(),
            p.fetchedAt().toInstant(java.time.ZoneOffset.UTC),
            p.extractor(),
            p.confidence(),
            status,
            sections,
            atts
    );
}
```

import 추가: `PolicyEnrichment`, `EnrichmentStatus`.

- [ ] **Step 4: 빌드**

```bash
cd backend && ./gradlew compileJava compileTestJava
```
Expected: BUILD SUCCESSFUL (Command 시그니처 변경으로 IngestionService 가 깨질 수 있으나 Task 7 에서 처리)

만약 compileJava 단계에서 IngestionService 가 깨지면 임시로 `null, null` 을 IngestPolicyCommand 생성자에 추가하는 wide pass 대신, **Task 6 까지 묶어서 한 번에 commit** 한다.

- [ ] **Step 5: Commit (Task 6 까지 함께 — 컴파일 가능 시점에 한 번)**

(Task 6 끝에서 함께 커밋)

---

## Task 6 — RegisterPolicyCommand·PolicyIngestionService·IngestionService 에 enrichment 전달

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: RegisterPolicyCommand 에 enrichment 추가**

기존 record 마지막 인자(sourceHash) 다음에 추가:

```java
String sourceHash,
PolicyEnrichment enrichment      // NEW
```

import: `import com.youthfit.policy.domain.model.PolicyEnrichment;`

- [ ] **Step 2: PolicyIngestionService 에서 enrichment 저장**

기존 정책 신규 등록 분기에서 `policyRepository.save(policy)` 직후, 그리고 갱신 분기에서 `policy.updateInfo(...)` 직후에 다음을 추가:

```java
if (command.enrichment() != null) {
    policy.replaceEnrichment(command.enrichment());
}
```

주의:
- `enrichment` 가 null 이면 기존 값을 **유지** (덮어쓰지 않음). 이유: 미변동분이 우연히 null 로 들어오는 경우에도 과거 enrichment 가 사라지면 안 됨
- 단, 풍부화가 명시적으로 실패(status != OK) 도 객체로 저장 → 마스킹은 응답 단계에서

- [ ] **Step 3: IngestionService.receivePolicy 확장**

`sourceHash` 계산 부분(line 75)을 다음과 같이 바꾼다:

```java
String rawJson = serialize(command);
String sourceHash = command.providedSourceHash() != null && !command.providedSourceHash().isBlank()
        ? command.providedSourceHash()
        : sha256(rawJson);
```

`RegisterPolicyCommand` 생성자 호출(line 89~129)의 마지막 인자에 `command.enrichment()` 를 추가.

- [ ] **Step 4: 빌드 통과**

```bash
cd backend && ./gradlew compileJava compileTestJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit (Task 5 + Task 6 합쳐서)**

```bash
git add backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java \
        backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java \
        backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java \
        backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
git commit -m "feat(ingestion): accept enrichment and providedSourceHash on intake"
```

---

## Task 7 — IngestionService: getExternalHashes 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: 의존 주입에 PolicySourceRepository 추가**

`@RequiredArgsConstructor` 가 있으므로 final 필드 한 줄 추가:

```java
private final com.youthfit.policy.domain.repository.PolicySourceRepository policySourceRepository;
```

- [ ] **Step 2: 메서드 추가**

```java
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public Map<String, String> findExternalIdHashes(String sourceType) {
    SourceType type;
    try {
        type = SourceType.valueOf(sourceType);
    } catch (IllegalArgumentException e) {
        return Map.of();
    }
    return policySourceRepository.findExternalIdHashMap(type);
}
```

- [ ] **Step 3: 빌드 통과**

```bash
cd backend && ./gradlew compileJava
```

- [ ] **Step 4: Commit (Task 8 끝에서 함께)**

---

## Task 8 — Internal API: GET external-hashes 엔드포인트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalApi.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java`
- Create: `backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerExternalHashesTest.java`

- [ ] **Step 1: API 인터페이스에 Swagger 메서드 추가**

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import java.util.Map;
...

@Operation(summary = "특정 source 의 external_id → source_hash 맵",
           description = "n8n 워크플로우가 변동 식별을 위해 호출. X-Internal-Api-Key 필수")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "external_id → hash 맵"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "400", description = "알 수 없는 source 타입")
})
ResponseEntity<Map<String, String>> getExternalHashes(
    @Parameter(description = "source type (예: YOUTH_CENTER)", required = true)
    String source);
```

- [ ] **Step 2: Controller 구현**

```java
@GetMapping("/policies/external-hashes")
@Override
public ResponseEntity<Map<String, String>> getExternalHashes(
        @RequestParam("source") String source) {
    return ResponseEntity.ok(ingestionService.findExternalIdHashes(source));
}
```

GET 매핑이므로 InternalApiKeyFilter 가 이미 `/api/internal/**` 전체에 적용되어 있으면 그대로 동작. 적용 범위가 POST 만이면 필터 설정 확장 필요 — 코드 확인 후 분기.

- [ ] **Step 3: 슬라이스 테스트 작성 (실패하는 테스트 먼저)**

```java
// IngestionInternalControllerExternalHashesTest.java
package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.application.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionInternalController.class)
class IngestionInternalControllerExternalHashesTest {

    @Autowired MockMvc mvc;
    @MockBean IngestionService ingestionService;

    @Test
    void returns_external_id_hash_map_with_valid_api_key() throws Exception {
        given(ingestionService.findExternalIdHashes("YOUTH_CENTER"))
            .willReturn(Map.of("PLY001", "h1", "PLY002", "h2"));

        mvc.perform(get("/api/internal/ingestion/policies/external-hashes")
                .param("source", "YOUTH_CENTER")
                .header("X-Internal-Api-Key", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.PLY001").value("h1"))
            .andExpect(jsonPath("$.PLY002").value("h2"));
    }

    @Test
    void rejects_without_api_key() throws Exception {
        mvc.perform(get("/api/internal/ingestion/policies/external-hashes")
                .param("source", "YOUTH_CENTER"))
            .andExpect(status().isUnauthorized());
    }
}
```

(테스트 프로파일의 `app.internal.api-key` 가 "test-key" 라고 가정. 기존 테스트 패턴 확인 후 동일 키로 맞춤.)

- [ ] **Step 4: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*IngestionInternalControllerExternalHashesTest"
```
Expected: 컨트롤러 메서드/라우팅이 아직 없으므로 404 또는 컴파일 실패

- [ ] **Step 5: 컨트롤러 작성 (Step 2 의 코드 실제 반영)**

- [ ] **Step 6: 테스트 실행 — PASS**

```bash
cd backend && ./gradlew test --tests "*IngestionInternalControllerExternalHashesTest"
```
Expected: 2 tests passed

- [ ] **Step 7: Commit (Task 7 + Task 8 합쳐)**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalApi.java \
        backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java \
        backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerExternalHashesTest.java
git commit -m "feat(ingestion): GET /policies/external-hashes for n8n diff"
```

---

## Task 9 — PolicyDetailResult + PolicyDetailResponse + 마스킹

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java`
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java`
- Create: `backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceEnrichmentMaskingTest.java`

- [ ] **Step 1: PolicyDetailResult 에 enrichment 추가**

result record 마지막 필드 다음에:

```java
PolicyDetailResult.Enrichment enrichment   // null 가능
```

그리고 nested record:

```java
public record Enrichment(
        String sourceUrl,
        Instant fetchedAt,
        Sections sections,
        List<ExtraAttachment> extraAttachments
) {
    public record Sections(
            String supportTarget,
            String supportContent,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote
    ) {}
    public record ExtraAttachment(String name, String url) {}
}
```

내부 status·confidence·extractor 는 응답 DTO 에 노출하지 않으므로 result 에도 제외 (관리자 API 추가 시 별도 result 사용).

- [ ] **Step 2: PolicyQueryService 에서 마스킹 적용**

`Policy` → `PolicyDetailResult` 변환 부분에서:

```java
PolicyDetailResult.Enrichment enrichmentResult = null;
PolicyEnrichment e = policy.getEnrichment();
if (e != null && e.isExposable()) {
    enrichmentResult = new PolicyDetailResult.Enrichment(
            e.sourceUrl(),
            e.fetchedAt(),
            e.sections() == null ? null : new PolicyDetailResult.Enrichment.Sections(
                    e.sections().supportTarget(),
                    e.sections().supportContent(),
                    e.sections().applyMethod(),
                    e.sections().requiredDocuments(),
                    e.sections().deadlineNote()
            ),
            e.extraAttachments() == null ? List.of() : e.extraAttachments().stream()
                    .map(a -> new PolicyDetailResult.Enrichment.ExtraAttachment(a.name(), a.url()))
                    .toList()
    );
}
```

`new PolicyDetailResult(...)` 호출의 마지막 인자로 `enrichmentResult` 추가.

- [ ] **Step 3: PolicyDetailResponse 에 enrichment 노출**

`PolicyDetailResponse` record 의 마지막 필드 다음에:

```java
Enrichment enrichment   // null 가능
```

nested record + `from` 변환 추가:

```java
public record Enrichment(
        String sourceUrl,
        Instant fetchedAt,
        Sections sections,
        List<ExtraAttachment> extraAttachments
) {
    static Enrichment from(PolicyDetailResult.Enrichment src) {
        if (src == null) return null;
        return new Enrichment(
                src.sourceUrl(),
                src.fetchedAt(),
                src.sections() == null ? null : new Sections(
                        src.sections().supportTarget(),
                        src.sections().supportContent(),
                        src.sections().applyMethod(),
                        src.sections().requiredDocuments(),
                        src.sections().deadlineNote()
                ),
                src.extraAttachments().stream()
                        .map(a -> new ExtraAttachment(a.name(), a.url()))
                        .toList()
        );
    }
    public record Sections(
            String supportTarget,
            String supportContent,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote
    ) {}
    public record ExtraAttachment(String name, String url) {}
}
```

`PolicyDetailResponse.from(result)` 의 마지막 인자에 `Enrichment.from(result.enrichment())` 추가.

- [ ] **Step 4: 마스킹 단위 테스트**

```java
// PolicyQueryServiceEnrichmentMaskingTest.java — 통합/슬라이스 중 빠른 방식
// 핵심: Policy 엔티티에 enrichment 셋팅 후 PolicyQueryService.findPolicyDetail(...)
// 호출 시 status/confidence 에 따라 result.enrichment() 가 null / non-null 인지 검증
```

기존 fixture / TestContainers 패턴에 맞춰 작성. (구현 시 기존 `PolicyQueryService` 테스트 파일 참조)

- [ ] **Step 5: 테스트 실행 — 통과**

```bash
cd backend && ./gradlew test --tests "*EnrichmentMasking*"
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyDetailResult.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyQueryService.java \
        backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyDetailResponse.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyQueryServiceEnrichmentMaskingTest.java
git commit -m "feat(policy): expose enrichment on detail response with masking"
```

---

## Task 10 — n8n: 외부 hash 조회 + 변동 판정 노드

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

수동 편집이 어려우면 n8n UI 에서 export 한 JSON 으로 교체. 아래는 추가할 노드의 핵심 코드.

- [ ] **Step 1: 새 HTTP Request 노드 — `외부 hash 조회`**

위치: `[JSON 파싱 + 서울 필터]` 와 `[정책별 순차 처리(SplitInBatches)]` 사이.

설정:
- Method: GET
- URL: `={{ $env.BACKEND_URL }}/api/internal/ingestion/policies/external-hashes`
- Query: `source=YOUTH_CENTER`
- Headers: `X-Internal-Api-Key: ={{ $env.INTERNAL_API_KEY }}`
- Options.Response.Response Format: json
- Options.Continue On Fail: false (실패 시 워크플로우 전체 실패가 맞음 — 변동 판정 불가)
- Retry on Fail: 3

- [ ] **Step 2: 새 Code 노드 — `변동 판정`**

위치: 위 hash 조회 노드 직후.

```javascript
// 입력: '외부 hash 조회' 응답 1건 + 'JSON 파싱 + 서울 필터' 의 정책 리스트
const crypto = require('crypto');

const hashMapItem = $('외부 hash 조회').first().json;
const hashMap = hashMapItem || {};

const policies = $('JSON 파싱 + 서울 필터').all();

// hash 계산 대상 필드 (응답 변동 의미가 있는 필드만 화이트리스트)
const HASH_FIELDS = [
  'plcyNm','plcyExplnCn','aplyYmd','sprvsnInstCdNm','operInstCdNm',
  'aplyUrlAddr','refUrlAddr1','refUrlAddr2','zipCd',
  'mrgSttsCd','jobCd','schoolCd','plcyMajorCd','sbizCd','plcyPvsnMthdCd','bizPrdSeCd',
  'sprtTrgtMinAge','sprtTrgtMaxAge','earnMinAmt','earnMaxAmt',
  'sbmsnDcmntCn','etcMttrCn'
];

function computeSourceHash(p) {
  const subset = {};
  for (const k of HASH_FIELDS.sort()) {
    subset[k] = p[k] == null ? null : String(p[k]);
  }
  const json = JSON.stringify(subset);
  return crypto.createHash('sha256').update(json, 'utf8').digest('hex');
}

const out = [];
for (const item of policies) {
  if (item.json._empty) continue;
  const p = item.json;
  const sourceHash = computeSourceHash(p);
  const existing = hashMap[p.plcyNo];
  let diffStatus;
  if (existing == null) diffStatus = 'NEW';
  else if (existing !== sourceHash) diffStatus = 'CHANGED';
  else diffStatus = 'UNCHANGED';
  out.push({
    json: {
      ...p,
      _sourceHash: sourceHash,
      _diffStatus: diffStatus,
      _enrich: diffStatus === 'NEW' || diffStatus === 'CHANGED'
    }
  });
}
return out;
```

- [ ] **Step 3: 수동 검증 (워크플로우 export → 로컬 또는 dev n8n import 후 manual webhook trigger)**

- [ ] **Step 4: Commit (Task 11/12 와 함께)**

---

## Task 11 — n8n: Enrichment 브랜치 (fetch + cheerio + OpenAI)

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: IF 노드 — `enrich 여부`**

조건: `={{ $json._enrich === true }}`. TRUE branch 만 풍부화, FALSE branch 는 변환 노드로 직행.

- [ ] **Step 2: Code 노드 — `링크 선택`**

```javascript
const p = $input.first().json;
const url = (p.aplyUrlAddr && p.aplyUrlAddr.trim()) ||
            (p.refUrlAddr1 && p.refUrlAddr1.trim()) ||
            (p.refUrlAddr2 && p.refUrlAddr2.trim()) || null;
return [{ json: { ...p, _enrichUrl: url } }];
```

- [ ] **Step 3: IF 노드 — `링크 존재`**

조건: `={{ $json._enrichUrl !== null }}`.
- TRUE → HTTP fetch 분기
- FALSE → 새 Code 노드 `enrichment skip: NO_LINK` 로 status 부착 후 변환 노드로

```javascript
// enrichment skip: NO_LINK
const p = $input.first().json;
const e = {
  sourceUrl: null,
  fetchedAt: new Date().toISOString(),
  extractor: 'openai:gpt-4o-mini',
  confidence: null,
  status: 'NO_LINK',
  sections: null,
  extraAttachments: []
};
return [{ json: { ...p, _enrichment: e } }];
```

- [ ] **Step 4: HTTP Request 노드 — `외부 페이지 fetch`**

- URL: `={{ $json._enrichUrl }}`
- Headers: `User-Agent: YouthFit-Bot/1.0 (+https://youthfit.kr/bot)`, `Accept: text/html`
- Options.Response.Response Format: text
- Options.Timeout: 10000
- Options.Continue On Fail: true
- Options.Redirect.Follow Redirects: true
- "Limit Response Size" 또는 동등 옵션이 있으면 3MB

- [ ] **Step 5: Code 노드 — `boilerplate 제거 + 첨부 후보 수집`**

n8n 의 Code 노드는 외부 npm 모듈을 직접 import 할 수 없을 수 있다. 두 가지 옵션:
- (a) n8n 인스턴스에 `NODE_FUNCTION_ALLOW_EXTERNAL=cheerio` 설정 + require('cheerio')
- (b) cheerio 미사용, regex 기반 fallback (덜 정확하나 의존 0)

배포 환경에 맞춰 (a) 권장. 코드 (cheerio 가용 가정):

```javascript
const cheerio = require('cheerio');
const item = $input.first();
const p = item.json;
const html = item.binary ? null : (typeof item.json.data === 'string' ? item.json.data : null);
// HTTP Request 의 response text 가 어디로 들어오는지에 따라 위 경로 조정 필요. n8n
// "Response Format: text" 인 경우 $input.first().json 이 문자열이 아닌 객체일 수 있다.
// 실측 후 정확한 키로 교체. 아래는 raw HTML 문자열을 `html` 로 가정.

const rawHtml = typeof p === 'string' ? p : (p.data || p.body || '');

const reasonIfEmpty = () => {
  // Item 이 빈 응답이거나 4xx/5xx 시 본문 없음
  return rawHtml ? null : 'FETCH_FAILED';
};

let status = reasonIfEmpty();
let cleaned = '';
let extraAttachments = [];

if (!status) {
  const $ = cheerio.load(rawHtml);
  $('script, style, nav, footer, aside, header, noscript').remove();
  const root = $('main').first().length ? $('main').first()
            : $('article').first().length ? $('article').first()
            : $('[role="main"]').first().length ? $('[role="main"]').first()
            : $('#content').first().length ? $('#content').first()
            : $('body').first();
  cleaned = root.text().replace(/\s+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
  if (cleaned.length > 8000) cleaned = cleaned.slice(0, 8000);

  $('a[href]').each((_, el) => {
    const href = $(el).attr('href') || '';
    if (/\.(pdf|hwp|hwpx)(\?|$)/i.test(href)) {
      const name = ($(el).text().trim() || href.split('/').pop()).slice(0, 200);
      extraAttachments.push({ name, url: href });
    }
  });
  if (cleaned.length < 200) status = 'TOO_SHORT';
}

return [{
  json: {
    ...$('정책별 순차 처리').first()?.json,
    _enrichUrl: $('링크 선택').first()?.json?._enrichUrl,
    _cleanedText: cleaned,
    _extraAttachments: extraAttachments,
    _enrichmentStatus: status
  }
}];
```

(노드 이름 참조 표현은 실제 n8n 환경의 입출력 구조에 맞춰 조정 필수.)

- [ ] **Step 6: IF 노드 — `cleaned 통과 여부`**

조건: `={{ $json._enrichmentStatus == null }}` (즉 FETCH_FAILED / TOO_SHORT 아님)
- TRUE → OpenAI 노드로
- FALSE → 새 Code 노드 `enrichment skip: <status>` 로 enrichment 객체 부착 후 변환 노드로

- [ ] **Step 7: OpenAI Chat 노드 — `LLM 구조화 추출`**

- Model: `gpt-4o-mini`
- Response Format: `json_schema`
- Schema:
  ```json
  {
    "type": "object",
    "properties": {
      "supportTarget": {"type": ["string", "null"]},
      "supportContent": {"type": ["string", "null"]},
      "applyMethod": {"type": ["string", "null"]},
      "requiredDocuments": {"type": ["string", "null"]},
      "deadlineNote": {"type": ["string", "null"]},
      "confidence": {"type": "number", "minimum": 0, "maximum": 1}
    },
    "required": ["supportTarget","supportContent","applyMethod","requiredDocuments","deadlineNote","confidence"],
    "additionalProperties": false
  }
  ```
- System message:
  ```
  너는 한국 청년 정책 안내 페이지의 본문을 읽고 핵심 구조화 정보를 뽑는다.
  - 텍스트에 명시되지 않은 내용을 임의로 만들지 마라. 모르면 null.
  - 출력은 JSON 만. 자연어 설명 금지.
  - confidence 는 입력 정보가 정책 안내 문서로서 충분한 정도를 0~1 로 자체 평가.
  ```
- User message: `={{ "원본 API body:\n" + $json.plcyExplnCn + "\n\n외부 페이지 본문:\n" + $json._cleanedText }}`
- Options.Continue On Fail: true

- [ ] **Step 8: Code 노드 — `enrichment 객체 조립`**

```javascript
const upstream = $input.first().json;
const llmOut = $('LLM 구조화 추출').first()?.json;
const llmError = $('LLM 구조화 추출').first()?.error;

let status, sections = null, confidence = null;
if (llmError) {
  status = 'LLM_FAILED';
} else if (!llmOut) {
  status = 'PARSE_FAILED';
} else {
  // OpenAI 노드 응답 구조 (message.content 등) 에서 JSON 파싱
  let parsed;
  try {
    const content = llmOut?.message?.content
                  || llmOut?.choices?.[0]?.message?.content
                  || llmOut?.text;
    parsed = typeof content === 'string' ? JSON.parse(content) : content;
  } catch (e) {
    parsed = null;
  }
  if (!parsed || typeof parsed.confidence !== 'number') {
    status = 'PARSE_FAILED';
  } else {
    confidence = parsed.confidence;
    sections = {
      supportTarget: parsed.supportTarget || null,
      supportContent: parsed.supportContent || null,
      applyMethod: parsed.applyMethod || null,
      requiredDocuments: parsed.requiredDocuments || null,
      deadlineNote: parsed.deadlineNote || null
    };
    status = confidence >= 0.6 ? 'OK' : 'LOW_CONFIDENCE';
  }
}

const enrichment = {
  sourceUrl: upstream._enrichUrl,
  fetchedAt: new Date().toISOString(),
  extractor: 'openai:gpt-4o-mini',
  confidence,
  status,
  sections,
  extraAttachments: upstream._extraAttachments || []
};

return [{ json: { ...upstream, _enrichment: enrichment } }];
```

- [ ] **Step 9: Commit (Task 10 + 11 합쳐)**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): add enrichment branch (hash diff, fetch, cheerio, openai)"
```

---

## Task 12 — n8n: 변환 노드에 enrichment·sourceHash 부착

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json` (기존 변환 Code 노드 수정)

- [ ] **Step 1: 기존 `변환 Code 노드` 의 IngestPolicyRequest 본문 구성에 두 필드 추가**

```javascript
// 기존 rawData 객체 생성 마지막에 추가
rawData: {
  ...기존 필드들...,
  sourceHash: $json._sourceHash,
  enrichment: $json._enrichment || null
}
```

- [ ] **Step 2: 미변동(UNCHANGED) 정책 처리**

- UNCHANGED 라도 백엔드는 hash 비교로 자체 skip 하지만, n8n 단에서 미리 걸러내면 트래픽 절약
- 단순화 위해 v0 에서는 그대로 백엔드 전송 (백엔드가 hash 비교 후 동일하면 갱신 안 함). 추후 최적화

- [ ] **Step 3: 수동 검증 — 워크플로우 manual webhook trigger**

dev 환경에서 실행해 백엔드 로그·DB 에 enrichment 객체가 들어가는지 확인.

```bash
psql "$YOUTHFIT_DB_URL" -c "select id, title, enrichment from policy where enrichment is not null limit 5;"
```

- [ ] **Step 4: Commit**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): attach sourceHash and enrichment to intake request"
```

---

## Task 13 — 프론트엔드: 타입·컴포넌트·통합

**Files:**
- Modify: `frontend/src/types/policy.ts` (실 경로 확인 후 조정 — 정책 타입 정의 파일)
- Create: `frontend/src/components/policy/PolicyEnrichmentSection.tsx`
- Create: `frontend/src/components/policy/__tests__/PolicyEnrichmentSection.test.tsx`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 타입 추가**

```typescript
export type PolicyEnrichmentSections = {
  supportTarget: string | null;
  supportContent: string | null;
  applyMethod: string | null;
  requiredDocuments: string | null;
  deadlineNote: string | null;
};

export type PolicyEnrichmentAttachment = {
  name: string;
  url: string;
};

export type PolicyEnrichment = {
  sourceUrl: string;
  fetchedAt: string;
  sections: PolicyEnrichmentSections | null;
  extraAttachments: PolicyEnrichmentAttachment[];
};

// 기존 PolicyDetail 타입에 추가
export type PolicyDetail = {
  // ... 기존 필드들 ...
  enrichment: PolicyEnrichment | null;
};
```

- [ ] **Step 2: 단위 테스트 (실패 케이스 먼저)**

```tsx
// PolicyEnrichmentSection.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyEnrichmentSection } from '../PolicyEnrichmentSection';

const baseEnrichment = {
  sourceUrl: 'https://example.com/policy/1',
  fetchedAt: '2026-05-12T04:12:00Z',
  sections: {
    supportTarget: '서울 거주 19~34세 청년',
    supportContent: '월 최대 20만원',
    applyMethod: '온라인 신청',
    requiredDocuments: null,
    deadlineNote: null,
  },
  extraAttachments: [{ name: '신청서.hwp', url: 'https://example.com/file.hwp' }],
};

describe('PolicyEnrichmentSection', () => {
  it('renders nothing when enrichment is null', () => {
    const { container } = render(<PolicyEnrichmentSection enrichment={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders only non-null sections', () => {
    render(<PolicyEnrichmentSection enrichment={baseEnrichment} />);
    expect(screen.getByText('서울 거주 19~34세 청년')).toBeInTheDocument();
    expect(screen.getByText('월 최대 20만원')).toBeInTheDocument();
    expect(screen.queryByText(/제출서류/)).toBeNull();
    expect(screen.queryByText(/마감안내/)).toBeNull();
  });

  it('shows source label and original link', () => {
    render(<PolicyEnrichmentSection enrichment={baseEnrichment} />);
    expect(screen.getByText(/자동 수집/)).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /원문 보기/ });
    expect(link).toHaveAttribute('href', baseEnrichment.sourceUrl);
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
  });

  it('renders extra attachments with external link', () => {
    render(<PolicyEnrichmentSection enrichment={baseEnrichment} />);
    const att = screen.getByRole('link', { name: /신청서\.hwp/ });
    expect(att).toHaveAttribute('href', baseEnrichment.extraAttachments[0].url);
    expect(att).toHaveAttribute('target', '_blank');
  });
});
```

- [ ] **Step 3: 컴포넌트 구현**

```tsx
// PolicyEnrichmentSection.tsx
import type { PolicyEnrichment } from '@/types/policy';

type Props = { enrichment: PolicyEnrichment | null };

const SECTION_LABELS: Array<[keyof NonNullable<PolicyEnrichment['sections']>, string]> = [
  ['supportTarget', '지원대상'],
  ['supportContent', '지원내용'],
  ['applyMethod', '신청방법'],
  ['requiredDocuments', '제출서류'],
  ['deadlineNote', '마감안내'],
];

export function PolicyEnrichmentSection({ enrichment }: Props) {
  if (!enrichment) return null;

  const visibleSections = SECTION_LABELS.filter(([key]) => {
    const v = enrichment.sections?.[key];
    return v != null && v.trim() !== '';
  });
  if (visibleSections.length === 0 && enrichment.extraAttachments.length === 0) return null;

  return (
    <section
      aria-label="정책 안내 페이지에서 자동 수집한 정보"
      className="policy-enrichment"
    >
      <header>
        <span className="enrichment-badge" title="AI 가 외부 페이지에서 자동으로 정리한 정보입니다">
          AI 자동 수집
        </span>
        <a
          href={enrichment.sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="enrichment-source-link"
        >
          원문 보기 →
        </a>
        <time dateTime={enrichment.fetchedAt} className="enrichment-fetched-at">
          수집: {new Date(enrichment.fetchedAt).toLocaleString('ko-KR')}
        </time>
      </header>

      {visibleSections.length > 0 && (
        <dl>
          {visibleSections.map(([key, label]) => (
            <div key={key}>
              <dt>{label}</dt>
              <dd>{enrichment.sections?.[key]}</dd>
            </div>
          ))}
        </dl>
      )}

      {enrichment.extraAttachments.length > 0 && (
        <div className="enrichment-attachments">
          <h4>첨부 파일 (자동 발견)</h4>
          <ul>
            {enrichment.extraAttachments.map((a) => (
              <li key={a.url}>
                <a href={a.url} target="_blank" rel="noopener noreferrer">
                  {a.name}
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
```

(CSS 는 기존 정책 상세 페이지 스타일 컨벤션에 맞춰 별도 모듈/Tailwind 클래스로 작성)

- [ ] **Step 4: PolicyDetailPage 에 통합**

`PolicyDetailPage.tsx` 의 본문 섹션 뒤, 첨부파일·신청 채널 앞 위치에:

```tsx
<PolicyEnrichmentSection enrichment={policy.enrichment ?? null} />
```

- [ ] **Step 5: 테스트 실행 — 통과**

```bash
cd frontend && npm test -- PolicyEnrichmentSection
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/policy.ts \
        frontend/src/components/policy/PolicyEnrichmentSection.tsx \
        frontend/src/components/policy/__tests__/PolicyEnrichmentSection.test.tsx \
        frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(frontend): add PolicyEnrichmentSection with source label"
```

---

## Task 14 — IngestionService: enrichment 수신 로그

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

스펙의 관찰 가능성 항목 중 v0 범위 = **status 분포 로그만**. `IngestionRunLog` 통계 컬럼 추가는 어드민 대시보드와 함께 v1 로 미룬다 (현재 plan 표면을 좁게 유지).

- [ ] **Step 1: receivePolicy 끝부분에 enrichment 상태 로그 한 줄**

```java
if (command.enrichment() != null) {
    log.info("enrichment received: externalId={}, status={}, confidence={}",
            command.externalId(),
            command.enrichment().status(),
            command.enrichment().confidence());
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
git commit -m "chore(ingestion): log enrichment status per policy"
```

---

## Task 15 — RAG: enrichment.sections 를 청크 입력에 포함

스펙 §3.5 의 RAG 연동. RAG 모듈은 별도 청크·임베딩 파이프라인이므로 변경 표면이 작지 않다. 다만 v0 enrichment 가치(Q&A 품질 향상)와 직결되므로 같은 사이클에 포함한다.

**Files (예상):**
- Modify: `backend/src/main/java/com/youthfit/rag/...` 의 청크 빌더 (정책 → 청크 변환 지점)
- Modify: 관련 테스트

청크 빌더의 실제 경로·시그니처는 RAG 모듈 구조에 따라 달라 task 펼치기 어려움. 다음 단계로 진행:

- [ ] **Step 1: RAG 청크 빌더 위치 파악**

```bash
grep -rn "정책.*청크\|policy.*chunk\|Chunk.*Builder\|RagChunk" backend/src/main/java/com/youthfit/rag | head -20
```

- [ ] **Step 2: enrichment.sections 가 청크 입력에 들어가도록 빌더 확장**

각 섹션(`supportTarget`, `supportContent`, `applyMethod`, `requiredDocuments`, `deadlineNote`) 중 null 아닌 것을 별도 청크 또는 본문 부록 형태로 포함. 메타데이터에 `source: "enriched"` 기록 (검색 결과 인용 시 출처 구분 가능하게).

- [ ] **Step 3: 단위 테스트 — enrichment 있는 정책의 청크 수와 메타데이터 검증**

- [ ] **Step 4: 통합 검증 — RAG 검색 결과에 enriched 메타가 노출되는지 dev 환경에서 확인**

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/...
git commit -m "feat(rag): include policy enrichment sections in chunk input"
```

**NOTE:** Step 2 의 구체 코드는 RAG 청크 빌더의 기존 시그니처에 의존하므로 plan 펼치기에서 빠짐. 실행 시점에 빌더 파일을 읽고 정확한 변경 위치·코드를 정한 뒤 작성.

---

## Task 16 — 점진적 롤아웃 + 수동 검증 체크리스트

- [ ] **Step 1: 백엔드 PR 머지 후 dev 환경 배포**

마이그레이션 적용·앱 부팅 확인. `GET /api/internal/ingestion/policies/external-hashes?source=YOUTH_CENTER` 200 + 빈 맵 또는 기존 데이터 응답.

- [ ] **Step 2: n8n 워크플로우 import — Enrichment IF 노드를 FALSE 고정**

기존 흐름이 그대로 도는지 확인 (변경분 없음 상태).

- [ ] **Step 3: 테스트 모드 (`lastPage = 1`, 25건 제한) 로 manual webhook trigger**

- 백엔드 intake 로그에서 sourceHash 가 n8n 제공 값으로 들어오는지 확인
- DB 의 `policy.enrichment` 가 모두 null 인지 확인 (IF FALSE 고정 상태)

- [ ] **Step 4: IF 노드 TRUE 활성화 — 다시 manual trigger**

- 외부 페이지 fetch / OpenAI 호출 / DB 적재 확인
- `SELECT enrichment->>'status', count(*) FROM policy WHERE enrichment IS NOT NULL GROUP BY 1;` 으로 status 분포 확인
- 최소 1건 `OK` 가 나오는지 확인

- [ ] **Step 5: 프론트엔드 dev 빌드로 정책 상세 페이지 확인**

enrichment 가 있는 정책 상세 페이지에서 별도 섹션이 라벨·원문 링크와 함께 노출되는지 시각 검증.

- [ ] **Step 6: 풀 페이징 활성화 (`lastPage = Math.max(1, Math.ceil(totCount/pageSize))`) 후 정상 작동 확인**

- [ ] **Step 7: 운영 배포 — 03:00 BOKJIRO, 04:00 YOUTH_CENTER 스케줄에서 1일 검증**

- [ ] **Step 8: 검증 완료 후 spec/plan 에 DONE_ prefix 적용**

```bash
git mv docs/superpowers/plans/2026-05-12-youth-center-enrichment.md \
       docs/superpowers/plans/DONE_2026-05-12-youth-center-enrichment.md
git mv docs/superpowers/specs/2026-05-12-youth-center-enrichment-design.md \
       docs/superpowers/specs/DONE_2026-05-12-youth-center-enrichment-design.md
git commit -m "chore(docs): mark youth-center enrichment cycle DONE"
```

---

## 검증 명령 모음

```bash
# 백엔드 빌드·테스트
cd backend && ./gradlew build

# 백엔드 부분 테스트
cd backend && ./gradlew test --tests "*Enrichment*"

# 프론트엔드 테스트
cd frontend && npm test

# DB enrichment 분포 확인
psql "$YOUTHFIT_DB_URL" -c \
  "select enrichment->>'status' as status, count(*) from policy
   where enrichment is not null group by 1;"

# external-hashes 엔드포인트 스모크
curl -H "X-Internal-Api-Key: $INTERNAL_API_KEY" \
  "http://localhost:8080/api/internal/ingestion/policies/external-hashes?source=YOUTH_CENTER" | jq 'length'
```
