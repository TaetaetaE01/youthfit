# 온통청년 청년정책 통합검색 API 수집 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년 청년정책 통합검색 API를 두 번째 수집원으로 추가하고, 복지로와 동일한 정책은 자동 스킵해 사용자에게 중복 노출되지 않게 한다.

**Architecture:** n8n 워크플로우가 매일 04:00에 외부 API를 전체 페이징으로 호출하고 응답에서 서울 26개 행정코드를 포함하는 정책만 백엔드 intake로 전송한다. 백엔드는 `(source_type, external_id)` UNIQUE로 동시성을 막고, YOUTH_CENTER 등록 시 정규화 제목으로 BOKJIRO 매칭을 검사해 우선권을 부여한다. 정규화 제목은 Postgres GENERATED 컬럼으로 자동 유지된다.

**Tech Stack:** Java 21 + Spring Boot 4.0.5, JPA + Hibernate, PostgreSQL 17 (GENERATED ALWAYS AS STORED, regexp_replace), n8n workflow (JSON), JUnit 5 + Mockito + AssertJ.

**연관 문서:**
- 스펙: `docs/superpowers/specs/2026-05-08-youth-center-ingestion-design.md`
- 코드 사전: `docs/prd/reference/youth-center-codes.xlsx`

---

## Phase 0 — 사전 회귀 점검 (사용자 액션)

이 단계는 코드 변경 전에 운영 환경 상태를 점검해 마이그레이션 실패 위험을 차단한다.

### Task 0.1: 운영 DB `(source_type, external_id)` 중복 점검

**Files:** (없음 — 운영 DB 조회만)

- [ ] **Step 1: 운영 DB에 다음 쿼리 실행**

```sql
SELECT source_type, external_id, COUNT(*) AS cnt
FROM policy_source
GROUP BY source_type, external_id
HAVING COUNT(*) > 1;
```

- [ ] **Step 2: 결과 확인**

| 결과 | 판정 |
|---|---|
| 0건 | ✅ 통과. Phase 1로 진행 |
| 1건 이상 | ⚠️ 사전 정리 필요 — 다음 Step 진행 |

- [ ] **Step 3 (조건부): 중복 row 정리**

각 중복 그룹에서 가장 최신 `created_at`만 남기고 나머지 삭제:

```sql
WITH duplicates AS (
  SELECT id, source_type, external_id, created_at,
         ROW_NUMBER() OVER (PARTITION BY source_type, external_id ORDER BY created_at DESC) AS rn
  FROM policy_source
)
SELECT * FROM duplicates WHERE rn > 1;
-- 위 결과 검토 후 삭제 진행
DELETE FROM policy_source WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY source_type, external_id ORDER BY created_at DESC) AS rn
    FROM policy_source
  ) t WHERE rn > 1
);
```

- [ ] **Step 4: 재검증**

Step 1 쿼리 재실행 → 0건 확인.

---

## Phase 1 — 백엔드 인프라

### Task 1.1: `TitleNormalizer` 유틸 (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/service/TitleNormalizer.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/service/TitleNormalizerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    @Test
    @DisplayName("null·empty 입력은 빈 문자열을 반환한다")
    void emptyInput() {
        assertThat(TitleNormalizer.normalize(null)).isEmpty();
        assertThat(TitleNormalizer.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("공백·특수문자를 제거하고 영어는 소문자화한다")
    void stripsWhitespaceAndPunctuation() {
        assertThat(TitleNormalizer.normalize("청년월세 지원사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("청년 월세 지원사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("청년월세-지원·사업")).isEqualTo("청년월세지원사업");
        assertThat(TitleNormalizer.normalize("YOUTH Rent")).isEqualTo("youthrent");
    }

    @Test
    @DisplayName("동일 의미·다른 표기를 같은 정규화 결과로 만든다")
    void equivalentTitles() {
        String a = TitleNormalizer.normalize("청년월세 지원사업");
        String b = TitleNormalizer.normalize("청년 월세지원사업");
        String c = TitleNormalizer.normalize("청년월세-지원사업");
        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    @DisplayName("한글·영숫자 외 모든 글자를 제거한다")
    void onlyAllowsKoreanAndAlnum() {
        assertThat(TitleNormalizer.normalize("Test_Policy 2026!")).isEqualTo("testpolicy2026");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.TitleNormalizerTest" -i`
Expected: COMPILATION ERROR — `TitleNormalizer` 클래스 없음

- [ ] **Step 3: 최소 구현 작성**

```java
package com.youthfit.policy.domain.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TitleNormalizer {

    private static final Pattern STRIP = Pattern.compile("[^\\p{Alnum}가-힣]");

    private TitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        return STRIP.matcher(title.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.TitleNormalizerTest" -i`
Expected: PASS — 4개 테스트 모두 성공

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/service/TitleNormalizer.java \
        backend/src/test/java/com/youthfit/policy/domain/service/TitleNormalizerTest.java
git commit -m "feat(policy): 정규화 제목 비교용 TitleNormalizer 유틸 추가

복지로 우선 dedup 구현을 위해 한글·영숫자 외 문자를 제거하고 영문은
소문자화하는 정규화 함수. Postgres GENERATED 컬럼의 regexp_replace
규칙과 동일 결과를 보장하기 위해 패턴 정의 일치."
```

---

### Task 1.2: `PolicyIngestionResult` Outcome enum 도입

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyIngestionResult.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyIngestionResultTest.java`

> 시그니처 `(Long, boolean)` → `(Long, Outcome)` 변경. Task 1.3에서 호출자 11곳을 일괄 변경하므로 이 task에선 *동시에* 컴파일 끝까지 가지 않음을 알아둘 것 (한 번에 머지). 실수 방지를 위해 두 task를 한 commit에 묶어도 무방.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.application.dto.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyIngestionResultTest {

    @Test
    @DisplayName("registered 팩토리는 Outcome.REGISTERED 와 isNew()=true 를 반환한다")
    void registered() {
        PolicyIngestionResult r = PolicyIngestionResult.registered(1L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.REGISTERED);
        assertThat(r.isNew()).isTrue();
        assertThat(r.policyId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("updated 팩토리는 Outcome.UPDATED 와 isNew()=false 를 반환한다")
    void updated() {
        PolicyIngestionResult r = PolicyIngestionResult.updated(2L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.UPDATED);
        assertThat(r.isNew()).isFalse();
        assertThat(r.policyId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("skippedDuplicate 팩토리는 Outcome.SKIPPED_DUPLICATE 와 isNew()=false 를 반환한다")
    void skippedDuplicate() {
        PolicyIngestionResult r = PolicyIngestionResult.skippedDuplicate(3L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.SKIPPED_DUPLICATE);
        assertThat(r.isNew()).isFalse();
        assertThat(r.policyId()).isEqualTo(3L);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.dto.result.PolicyIngestionResultTest" -i`
Expected: COMPILATION ERROR — `Outcome`, `registered/updated/skippedDuplicate` 미존재

- [ ] **Step 3: 구현 작성**

`PolicyIngestionResult.java` 전체 내용 교체:

```java
package com.youthfit.policy.application.dto.result;

public record PolicyIngestionResult(Long policyId, Outcome outcome) {

    public enum Outcome {
        REGISTERED,
        UPDATED,
        SKIPPED_DUPLICATE
    }

    public boolean isNew() {
        return outcome == Outcome.REGISTERED;
    }

    public static PolicyIngestionResult registered(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.REGISTERED);
    }

    public static PolicyIngestionResult updated(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.UPDATED);
    }

    public static PolicyIngestionResult skippedDuplicate(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.SKIPPED_DUPLICATE);
    }
}
```

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.dto.result.PolicyIngestionResultTest" -i`
Expected: PASS — 3개 테스트 모두 성공

- [ ] **Step 5: 컴파일 깨짐 확인 (의도적)**

Run: `cd backend && ./gradlew compileJava 2>&1 | tail -20`
Expected: 컴파일 성공 (record 시그니처는 변경됐지만 호출자가 record 타입만 받으면 OK)

Run: `cd backend && ./gradlew compileTestJava 2>&1 | tail -20`
Expected: COMPILATION ERROR (`new PolicyIngestionResult(1L, true)` 형태가 boolean → Outcome 미스매치)

→ 이 에러를 Task 1.3에서 일괄 해결한다.

- [ ] **Step 6: (커밋 보류)** Task 1.3 완료 후 함께 커밋. 이 변경만으로는 빌드가 깨지므로 단독 커밋 금지.

---

### Task 1.3: `PolicyIngestionResult` 호출자 11곳 일괄 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java` (2곳)
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java` (9곳)

- [ ] **Step 1: 메인 코드 변경 — `PolicyIngestionService.java`**

Modify `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`:

찾기: `return new PolicyIngestionResult(source.getPolicy().getId(), false);`
바꾸기: `return PolicyIngestionResult.updated(source.getPolicy().getId());`

찾기: `return new PolicyIngestionResult(savedPolicy.getId(), true);`
바꾸기: `return PolicyIngestionResult.registered(savedPolicy.getId());`

- [ ] **Step 2: 테스트 코드 변경 — `IngestionServiceTest.java`**

`new PolicyIngestionResult(<id>, true)` → `PolicyIngestionResult.registered(<id>)` 로 9곳 일괄 치환:

```bash
cd backend && \
sed -i '' -E 's/new PolicyIngestionResult\(([^,]+), true\)/PolicyIngestionResult.registered(\1)/g' \
  src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
```

- [ ] **Step 3: 잔여 직접 생성자 호출이 없는지 확인**

Run: `cd backend && grep -rn "new PolicyIngestionResult" src/`
Expected: 결과 0건. 1건이라도 나오면 수동 수정.

- [ ] **Step 4: 컴파일 + 전체 테스트 통과**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 테스트 회귀 없음 (특히 `IngestionServiceTest` 9개 테스트 모두 PASS)

- [ ] **Step 5: 커밋 (Task 1.2 + 1.3 묶음)**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/PolicyIngestionResult.java \
        backend/src/test/java/com/youthfit/policy/application/dto/result/PolicyIngestionResultTest.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "refactor(policy): PolicyIngestionResult 시그니처를 Outcome enum 으로 확장

기존 (Long, boolean) → (Long, Outcome) 으로 변경. boolean 으로 표현
못했던 SKIPPED_DUPLICATE 상태를 추가하기 위한 사전 작업이며,
호환성을 위해 isNew() 메서드는 유지 (REGISTERED 일 때만 true).

호출자 11곳(메인 2, 테스트 9) 모두 팩토리 메서드(registered/updated)로
일괄 치환. 동작 변경은 없음."
```

---

### Task 1.4: 마이그레이션 SQL 파일 작성

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-08-youth-center-prep.sql`

- [ ] **Step 1: SQL 파일 작성**

```sql
-- 온통청년 ingestion 도입 사전 마이그레이션
-- 실행 전 필수 점검 (Phase 0 Task 0.1):
--   SELECT source_type, external_id, COUNT(*) FROM policy_source
--    GROUP BY source_type, external_id HAVING COUNT(*) > 1;
--   결과 0건이어야 ① ALTER 가 성공한다.

-- ① (source_type, external_id) UNIQUE 제약
-- 자치구별/페이지별 동시 호출에서의 중복 row 차단
ALTER TABLE policy_source
  ADD CONSTRAINT uq_policy_source_type_external_id
  UNIQUE (source_type, external_id);

-- ② BOKJIRO 우선 dedup 용 정규화 제목 컬럼 (GENERATED ALWAYS AS STORED)
-- title 변경 시 자동 재계산. Java TitleNormalizer 와 동일 규칙
-- (소문자화 + 한글·영숫자 외 문자 제거)
ALTER TABLE policy
  ADD COLUMN normalized_title TEXT
  GENERATED ALWAYS AS (
    lower(regexp_replace(title, '[^[:alnum:]가-힣]', '', 'g'))
  ) STORED;

-- ③ 정규화 제목 인덱스 — 복지로 dedup 조회 O(log n)
CREATE INDEX idx_policy_normalized_title ON policy (normalized_title);
```

- [ ] **Step 2: 다른 마이그레이션 파일과 형식 일치 확인**

Run: `head -3 backend/src/main/resources/sql/2026-05-05-ingestion-health.sql`
참고용으로 기존 파일 헤더 스타일과 일관성 확인.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-08-youth-center-prep.sql
git commit -m "feat(db): 온통청년 ingestion 사전 마이그레이션 SQL

- policy_source(source_type, external_id) UNIQUE 제약
- policy.normalized_title (Postgres GENERATED ALWAYS AS STORED)
- normalized_title 인덱스
"
```

---

### Task 1.5: `Policy` 엔티티에 `normalizedTitle` 읽기 전용 컬럼 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`

> `regionCode` length 는 그대로 (한글 라벨이 기존 20자 안에 들어감).

- [ ] **Step 1: import 추가는 불필요 (이미 jakarta.persistence.* 와일드카드)**

- [ ] **Step 2: 필드 추가**

`Policy.java` 의 `private String contact;` 다음 줄(또는 컬럼 선언부 적당한 위치)에 추가:

```java
@Column(name = "normalized_title", insertable = false, updatable = false)
private String normalizedTitle;
```

> `insertable=false, updatable=false` 가 핵심. DB GENERATED 컬럼이므로 INSERT/UPDATE 문에 포함시키면 Postgres 가 거부한다.

- [ ] **Step 3: getter 자동 생성 확인**

`@Getter` 가 클래스 레벨에 이미 붙어있으므로 `getNormalizedTitle()` 자동 생성됨. 별도 작업 없음.

- [ ] **Step 4: Builder/updateInfo 시그니처는 그대로**

`@Builder private Policy(...)` 와 `updateInfo(...)` 모두 변경 없음 — 새 필드는 read-only이므로.

- [ ] **Step 5: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 단위 테스트 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: PASS — 기존 테스트 모두 통과 (read-only 필드 추가는 동작 변경 없음)

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/Policy.java
git commit -m "feat(policy): Policy 엔티티에 normalizedTitle 읽기 전용 컬럼 추가

DB의 GENERATED ALWAYS AS STORED 컬럼을 JPA 에서 매핑한다.
insertable=false, updatable=false 로 설정해 INSERT/UPDATE 문에
포함되지 않도록 한다. Builder 와 updateInfo 시그니처는 영향 없음."
```

---

### Task 1.6: `PolicySource` 엔티티 `@UniqueConstraint` 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicySource.java`

- [ ] **Step 1: `@Table` 어노테이션 변경**

찾기:
```java
@Table(name = "policy_source")
```

바꾸기:
```java
@Table(name = "policy_source", uniqueConstraints = {
        @UniqueConstraint(name = "uq_policy_source_type_external_id",
                          columnNames = {"source_type", "external_id"})
})
```

> `jakarta.persistence.UniqueConstraint` import 자동 (이미 `jakarta.persistence.*` 와일드카드).

- [ ] **Step 2: 컴파일 + 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — `ddl-auto: validate` 환경(test profile)에서 실제 DB 스키마와의 일치를 검증하므로, 마이그레이션 SQL 미적용 시 통합 테스트가 실패할 수 있음. 그 경우 통합 테스트 실패는 Phase 3에서 마이그레이션 적용 후 자연스럽게 해결됨.

> 만약 통합 테스트가 마이그레이션 미적용으로 실패한다면, 일단 `./gradlew test --tests "*Test"` 로 단위 테스트만 돌려 회귀 없음 확인.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicySource.java
git commit -m "feat(policy): PolicySource 에 (source_type, external_id) UNIQUE 제약

n8n 워크플로우 동시 호출에서의 동시성 race 안전망. 마이그레이션 SQL
2026-05-08-youth-center-prep.sql 의 ① ALTER 와 일치한다."
```

---

### Task 1.7: `PolicyRepository` 정규화 제목 조회 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`

- [ ] **Step 1: 현재 파일 내용 확인**

Run: `cat backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java`

import 영역에 `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `java.util.Optional`, `com.youthfit.policy.domain.model.Policy` 가 있는지 확인. 없으면 추가.

- [ ] **Step 2: 메서드 추가**

`PolicyRepository` 인터페이스 내부에 다음 메서드 추가:

```java
/**
 * 정규화 제목이 일치하면서 BOKJIRO_CENTRAL 출처가 등록된 Policy 를 찾는다.
 * 온통청년 ingestion 시점에 복지로 우선 중복 스킵 판단에 사용한다.
 */
@org.springframework.data.jpa.repository.Query("""
    SELECT p FROM Policy p
    WHERE p.normalizedTitle = :normalizedTitle
      AND EXISTS (
        SELECT 1 FROM PolicySource s
        WHERE s.policy = p
          AND s.sourceType = com.youthfit.policy.domain.model.SourceType.BOKJIRO_CENTRAL
      )
""")
java.util.Optional<com.youthfit.policy.domain.model.Policy> findByNormalizedTitleWithBokjiroSource(
        @org.springframework.data.repository.query.Param("normalizedTitle") String normalizedTitle);
```

> import 정리는 IDE 또는 다음 단계 컴파일 시 보강. 위 fully-qualified 형태는 그대로도 컴파일 됨.

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java
git commit -m "feat(policy): findByNormalizedTitleWithBokjiroSource 쿼리 메서드

YOUTH_CENTER 정책 등록 시점에 동일 정규화 제목의 BOKJIRO_CENTRAL
정책이 이미 존재하는지 조회한다. EXISTS 서브쿼리로 sourceType 검증."
```

---

## Phase 2 — 백엔드 비즈니스 로직

### Task 2.1: `PolicyIngestionService.registerPolicy` YOUTH_CENTER dedup 분기 추가 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/PolicyIngestionServiceTest.java` (신규)

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyIngestionServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicySourceRepository policySourceRepository;
    @Mock private PolicyAttachmentApplicationService policyAttachmentApplicationService;

    @InjectMocks private PolicyIngestionService policyIngestionService;

    @Nested
    @DisplayName("YOUTH_CENTER 우선 스킵 분기")
    class YouthCenterSkipBranch {

        @Test
        @DisplayName("동일 정규화 제목의 BOKJIRO 정책이 존재하면 SKIPPED_DUPLICATE 를 반환하고 저장하지 않는다")
        void skipsWhenBokjiroExists() {
            // given
            RegisterPolicyCommand command = command(SourceType.YOUTH_CENTER, "WLF99999",
                    "청년월세 지원사업");
            Policy bokjiro = mockPolicy(42L);
            given(policyRepository.findByNormalizedTitleWithBokjiroSource("청년월세지원사업"))
                    .willReturn(Optional.of(bokjiro));

            // when
            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            // then
            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.SKIPPED_DUPLICATE);
            assertThat(result.policyId()).isEqualTo(42L);
            verify(policySourceRepository, never()).save(any());
            verify(policyRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 정규화 제목의 BOKJIRO 정책이 없으면 정상 등록한다")
        void registersWhenBokjiroAbsent() {
            // given
            RegisterPolicyCommand command = command(SourceType.YOUTH_CENTER, "WLF99999",
                    "신규 정책");
            given(policyRepository.findByNormalizedTitleWithBokjiroSource("신규정책"))
                    .willReturn(Optional.empty());
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.YOUTH_CENTER, "WLF99999"))
                    .willReturn(Optional.empty());
            Policy saved = mockPolicy(99L);
            given(policyRepository.save(any())).willReturn(saved);

            // when
            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            // then
            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.REGISTERED);
            assertThat(result.isNew()).isTrue();
        }
    }

    @Nested
    @DisplayName("BOKJIRO 흐름 회귀")
    class BokjiroRegression {

        @Test
        @DisplayName("BOKJIRO_CENTRAL 등록은 정규화 제목 조회를 호출하지 않는다")
        void bokjiroDoesNotCheckNormalizedTitle() {
            // given
            RegisterPolicyCommand command = command(SourceType.BOKJIRO_CENTRAL, "WLF00001",
                    "복지로 정책");
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.BOKJIRO_CENTRAL, "WLF00001"))
                    .willReturn(Optional.empty());
            Policy saved = mockPolicy(7L);
            given(policyRepository.save(any())).willReturn(saved);

            // when
            policyIngestionService.registerPolicy(command);

            // then
            verify(policyRepository, never()).findByNormalizedTitleWithBokjiroSource(any());
        }
    }

    // ── 헬퍼 ──

    private RegisterPolicyCommand command(SourceType sourceType, String externalId, String title) {
        return new RegisterPolicyCommand(
                title, "summary", "[개요]\nbody", null, null, null,
                "org", "contact", Category.WELFARE, "전국",
                null, null, null, null, null,
                Set.of(), Set.of(), Set.of(),
                List.of(), List.of(), List.of(),
                sourceType, externalId, "https://example.com",
                "{\"json\":\"raw\"}", "hash"
        );
    }

    private Policy mockPolicy(Long id) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getId()).willReturn(id);
        return p;
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyIngestionServiceTest" -i`
Expected: FAIL — `findByNormalizedTitleWithBokjiroSource` 호출 없이 BOKJIRO 흐름이 그대로 진행됨 → `skipsWhenBokjiroExists` 가 등록 흐름을 타서 실패

- [ ] **Step 3: `PolicyIngestionService.registerPolicy` 분기 추가**

Modify `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`:

import 추가:
```java
import com.youthfit.policy.domain.service.TitleNormalizer;
```

`registerPolicy` 메서드 본문 시작부에 다음 분기 삽입 (기존 `Optional<PolicySource> existingSource = ...` 직전):

```java
    public PolicyIngestionResult registerPolicy(RegisterPolicyCommand command) {
        if (command.sourceType() == SourceType.YOUTH_CENTER) {
            String normalized = TitleNormalizer.normalize(command.title());
            Optional<Policy> bokjiroPolicy = policyRepository.findByNormalizedTitleWithBokjiroSource(normalized);
            if (bokjiroPolicy.isPresent()) {
                return PolicyIngestionResult.skippedDuplicate(bokjiroPolicy.get().getId());
            }
        }

        Optional<PolicySource> existingSource = policySourceRepository
                .findBySourceTypeAndExternalId(command.sourceType(), command.externalId());
        // (이하 기존 로직 그대로)
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.PolicyIngestionServiceTest" -i`
Expected: PASS — 3개 테스트 모두 성공

- [ ] **Step 5: 전체 회귀 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 테스트 모두 통과

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java \
        backend/src/test/java/com/youthfit/policy/application/service/PolicyIngestionServiceTest.java
git commit -m "feat(policy): YOUTH_CENTER 등록 시 BOKJIRO 우선 dedup 분기

정규화 제목으로 BOKJIRO_CENTRAL 정책 검색해 일치 시 SKIPPED_DUPLICATE
반환. BOKJIRO 흐름은 분기 진입 안 해 회귀 없음."
```

---

### Task 2.2: `IngestionService.receivePolicy` SKIPPED_DUPLICATE 응답 분기 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`

- [ ] **Step 1: 실패 테스트 추가**

`IngestionServiceTest` 의 `ReceivePolicy` `@Nested` 클래스 내부에 다음 테스트 추가:

```java
        @Test
        @DisplayName("PolicyIngestionService 가 SKIPPED_DUPLICATE 를 반환하면 status 를 SKIPPED_DUPLICATE 로 응답하고 이벤트/첨부 다운로드를 트리거하지 않는다")
        void respondsSkippedDuplicateWithoutSideEffects() {
            // given
            IngestPolicyCommand command = command("YOUTH_CENTER", "주거");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.skippedDuplicate(42L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("SKIPPED_DUPLICATE");
            then(eventPublisher).should(never()).publishEvent(any());
            then(attachmentDownloadService).should(never()).downloadForPolicyAsync(any());
        }
```

> import 부족 시: `import static org.mockito.BDDMockito.then;`, `import static org.mockito.Mockito.never;`. 이미 위쪽에 다른 mockito import 가 있으므로 부족분만 추가.

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServiceTest.ReceivePolicy.respondsSkippedDuplicateWithoutSideEffects" -i`
Expected: FAIL — 현재 코드는 항상 "RECEIVED" 반환

- [ ] **Step 3: `IngestionService.receivePolicy` 본문 수정**

Modify `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`:

import 추가:
```java
import com.youthfit.policy.application.dto.result.PolicyIngestionResult.Outcome;
```

`receivePolicy` 메서드의 핵심 분기 부분을 다음으로 교체:

찾기 (line 111~116 근방):
```java
            PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
            duplicate = !ingestionResult.isNew();
            eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
            triggerAttachmentDownload(ingestionResult.policyId());

            return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

바꾸기:
```java
            PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
            duplicate = ingestionResult.outcome() != Outcome.REGISTERED;

            if (ingestionResult.outcome() == Outcome.SKIPPED_DUPLICATE) {
                return new IngestPolicyResult(UUID.randomUUID(), "SKIPPED_DUPLICATE");
            }

            eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
            triggerAttachmentDownload(ingestionResult.policyId());

            return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServiceTest" -i`
Expected: PASS — 신규 테스트 + 기존 9개 테스트 모두 성공

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "feat(ingestion): SKIPPED_DUPLICATE 응답 분기 추가

PolicyIngestionService 가 SKIPPED_DUPLICATE 를 반환하면 IngestPolicyResult
의 status 를 SKIPPED_DUPLICATE 로 응답하고 이벤트 publish 와 첨부 다운로드
트리거를 건너뛴다. 복지로 흐름(REGISTERED/UPDATED 만 도달)에는 영향 없음."
```

---

### Task 2.3: 빌드 + 전체 회귀 테스트

- [ ] **Step 1: 클린 빌드**

Run: `cd backend && ./gradlew clean build -x test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 단위 테스트 모두 통과. 통합 테스트(@SpringBootTest)는 운영 DB 마이그레이션 미적용 시 `ddl-auto: validate` 단계에서 normalized_title 누락으로 실패할 수 있음 → Phase 3 적용 후 재실행으로 통과 확인.

- [ ] **Step 3: (커밋 없음, 검증만)**

---

## Phase 3 — 운영 마이그레이션 (사용자 액션)

### Task 3.1: 운영 DB SQL 적용

**Files:** (없음 — 운영 DB 작업)

- [ ] **Step 1: 백업 (선택, 권장)**

```bash
pg_dump -h <prod-host> -U <prod-user> -d youthfit \
  --table=policy --table=policy_source \
  > /tmp/youthfit-policy-backup-$(date +%Y%m%d-%H%M).sql
```

- [ ] **Step 2: SQL 적용**

```bash
psql -h <prod-host> -U <prod-user> -d youthfit \
  -f backend/src/main/resources/sql/2026-05-08-youth-center-prep.sql
```

- [ ] **Step 3: 적용 검증**

```sql
-- ① UNIQUE 제약 확인
SELECT conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
JOIN pg_class t ON c.conrelid = t.oid
WHERE t.relname = 'policy_source' AND conname = 'uq_policy_source_type_external_id';
-- 결과: 1행 (uq_policy_source_type_external_id ... UNIQUE (source_type, external_id))

-- ② normalized_title 컬럼 + GENERATED 확인
SELECT column_name, generation_expression
FROM information_schema.columns
WHERE table_name = 'policy' AND column_name = 'normalized_title';
-- 결과: 1행 (normalized_title, lower(regexp_replace(title, ...)))

-- ③ 기존 row 자동 채워짐 확인
SELECT COUNT(*) FROM policy WHERE normalized_title IS NULL;
-- 결과: 0
SELECT title, normalized_title FROM policy LIMIT 3;
-- 결과: title 의 영문 소문자화 + 한글·영숫자 외 제거 결과
```

- [ ] **Step 4: 백엔드 재기동 + 헬스체크**

`ddl-auto: validate` 가 통과해야 재기동 성공. 실패 시 `validate` 로그를 보고 누락된 컬럼/제약 확인 후 수정.

```bash
curl -fsS http://<backend-host>/actuator/health
# {"status":"UP"} 확인
```

- [ ] **Step 5: 통합 테스트 재실행 (개발/스테이징 DB)**

Run (개발 DB 기준): `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 통합 테스트 회귀 없음

---

## Phase 4 — n8n 워크플로우

### Task 4.1: 워크플로우 JSON 골격 + 트리거 + 페이지 초기화 + getPlcy 호출

**Files:**
- Create: `n8n/workflows/youth-center-seoul.json`

> 복지로 워크플로우(`bokjiro-central-welfare.json`)를 reference 로 동일 구조 채택. JSON 길이가 길어 task 4.1~4.4 로 점진 작성.

- [ ] **Step 1: 골격 작성**

```json
{
  "id": "youth-center-seoul",
  "name": "YouthFit - 온통청년 서울 청년 정책 수집",
  "nodes": [
    {
      "parameters": {
        "rule": {
          "interval": [
            {
              "field": "cronExpression",
              "expression": "0 4 * * *"
            }
          ]
        }
      },
      "id": "schedule-trigger",
      "name": "매일 새벽 4시 실행",
      "type": "n8n-nodes-base.scheduleTrigger",
      "typeVersion": 1.2,
      "position": [0, 0]
    },
    {
      "parameters": {
        "httpMethod": "POST",
        "path": "youth-center-manual",
        "responseMode": "lastNode",
        "options": {}
      },
      "id": "manual-webhook-trigger",
      "name": "수동 실행 트리거",
      "type": "n8n-nodes-base.webhook",
      "typeVersion": 2,
      "position": [0, 200],
      "webhookId": "youth-center-manual-trigger"
    },
    {
      "parameters": {
        "jsCode": "return [{ json: { pageNum: 1 } }];"
      },
      "id": "init-page",
      "name": "페이지 초기화",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [220, 0]
    },
    {
      "parameters": {
        "url": "https://www.youthcenter.go.kr/go/ythip/getPlcy",
        "sendQuery": true,
        "queryParameters": {
          "parameters": [
            { "name": "apiKeyNm", "value": "={{ $env.YOUTH_CENTER_API_KEY }}" },
            { "name": "rtnType", "value": "json" },
            { "name": "pageNum", "value": "={{ $json.pageNum }}" },
            { "name": "pageSize", "value": "100" }
          ]
        },
        "options": {
          "response": {
            "response": {
              "fullResponse": false,
              "responseFormat": "json"
            }
          },
          "timeout": 30000
        },
        "sendHeaders": true,
        "headerParameters": {
          "parameters": [
            { "name": "User-Agent", "value": "YouthFit-Bot/1.0 (+https://youthfit.kr/bot)" },
            { "name": "Accept", "value": "application/json" }
          ]
        }
      },
      "id": "fetch-list",
      "name": "getPlcy 호출",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 0]
    }
  ],
  "connections": {
    "매일 새벽 4시 실행": {
      "main": [[{ "node": "페이지 초기화", "type": "main", "index": 0 }]]
    },
    "수동 실행 트리거": {
      "main": [[{ "node": "페이지 초기화", "type": "main", "index": 0 }]]
    },
    "페이지 초기화": {
      "main": [[{ "node": "getPlcy 호출", "type": "main", "index": 0 }]]
    }
  },
  "settings": {
    "executionOrder": "v1"
  },
  "tags": [
    { "name": "api-ingest" },
    { "name": "youth-center" },
    { "name": "youth-policy" }
  ],
  "meta": {
    "instanceId": "youthfit"
  }
}
```

- [ ] **Step 2: JSON 유효성 확인**

Run: `python3 -m json.tool n8n/workflows/youth-center-seoul.json > /dev/null`
Expected: 출력 없음(=유효한 JSON)

- [ ] **Step 3: (커밋 보류)** Task 4.4 까지 완성 후 한 번에 커밋.

---

### Task 4.2: JSON 파싱 + 서울 필터 Code 노드 추가

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: nodes 배열에 노드 추가**

`getPlcy 호출` 다음 노드로 추가 (nodes 배열 안에 element 추가):

```json
    {
      "parameters": {
        "jsCode": "const SEOUL_CODES = new Set(['11000','11110','11140','11170','11200','11215','11230','11260','11290','11305','11320','11350','11380','11410','11440','11470','11500','11530','11545','11560','11590','11620','11650','11680','11710','11740']);\n\nconst raw = $input.first().json;\nif (!raw || raw.resultCode !== 200) {\n  throw new Error(`온통청년 API error: ${raw && raw.resultCode} ${raw && raw.resultMessage}`);\n}\nconst result = raw.result || {};\nconst pagging = result.pagging || {};\nconst totCount = pagging.totCount || 0;\nconst pageNum = pagging.pageNum || $('페이지 초기화').first()?.json?.pageNum || $('다음 페이지 이동').first()?.json?.pageNum || 1;\nconst pageSize = pagging.pageSize || 100;\n// TEST MODE: 첫 1페이지만 처리 (검증용). 풀 페이징 활성화 시 아래 줄로 교체.\nconst lastPage = 1;\n// const lastPage = Math.max(1, Math.ceil(totCount / pageSize));\n\nconst list = result.youthPolicyList || [];\nconst seoulItems = [];\nfor (const it of list) {\n  const codes = (it.zipCd || '').split(',').map(s => s.trim()).filter(Boolean);\n  if (codes.some(c => SEOUL_CODES.has(c))) {\n    seoulItems.push({ json: { ...it, _pageNum: pageNum, _lastPage: lastPage, _totCount: totCount, _seoulCodes: codes.filter(c => SEOUL_CODES.has(c)) } });\n  }\n}\n\nif (seoulItems.length === 0) {\n  return [{ json: { _empty: true, _pageNum: pageNum, _lastPage: lastPage, _totCount: totCount } }];\n}\nreturn seoulItems;"
      },
      "id": "parse-and-filter",
      "name": "JSON 파싱 + 서울 필터",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [660, 0]
    }
```

- [ ] **Step 2: connections 에 추가**

`connections` 객체에 추가:

```json
    "getPlcy 호출": {
      "main": [[{ "node": "JSON 파싱 + 서울 필터", "type": "main", "index": 0 }]]
    },
```

- [ ] **Step 3: JSON 유효성 확인**

Run: `python3 -m json.tool n8n/workflows/youth-center-seoul.json > /dev/null`
Expected: 출력 없음

---

### Task 4.3: 정책 → IngestPolicyRequest 변환 Code 노드 추가

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

> 코드 사전 + 본문 결합 + 카테고리 매핑 + 한글 라벨 정규화 + 신청기간 파싱 + 인코딩 정제 모두 한 Code 노드 안에서 처리. 길이 길지만 함수로 잘 분리.

- [ ] **Step 1: SplitInBatches + Wait + 변환 + POST 노드들 추가**

`nodes` 배열에 다음 4개 노드 추가:

```json
    {
      "parameters": {
        "batchSize": 1,
        "options": {}
      },
      "id": "loop-policies",
      "name": "정책별 순차 처리",
      "type": "n8n-nodes-base.splitInBatches",
      "typeVersion": 3,
      "position": [880, 0]
    },
    {
      "parameters": {
        "amount": 1,
        "unit": "seconds"
      },
      "id": "rate-limit-wait",
      "name": "1초 대기",
      "type": "n8n-nodes-base.wait",
      "typeVersion": 1.1,
      "position": [1100, 100]
    },
    {
      "parameters": {
        "jsCode": "// === 코드 사전 (data.go.kr 공식 엑셀 출처) ===\nconst CODE = {\n  mrgSttsCd: { '0055001':'기혼', '0055002':'미혼', '0055003':'제한없음' },\n  earnCndSeCd: { '0043001':'무관', '0043002':'연소득', '0043003':'기타' },\n  jobCd: { '0013001':'재직자','0013002':'자영업자','0013003':'미취업자','0013004':'프리랜서','0013005':'일용근로자','0013006':'(예비)창업자','0013007':'단기근로자','0013008':'영농종사자','0013009':'기타','0013010':'제한없음' },\n  schoolCd: { '0049001':'고졸 미만','0049002':'고교 재학','0049003':'고졸 예정','0049004':'고교 졸업','0049005':'대학 재학','0049006':'대졸 예정','0049007':'대학 졸업','0049008':'석·박사','0049009':'기타','0049010':'제한없음' },\n  plcyMajorCd: { '0011001':'인문계열','0011002':'사회계열','0011003':'상경계열','0011004':'이학계열','0011005':'공학계열','0011006':'예체능계열','0011007':'농산업계열','0011008':'기타','0011009':'제한없음' },\n  sbizCd: { '0014001':'중소기업','0014002':'여성','0014003':'기초생활수급자','0014004':'한부모가정','0014005':'장애인','0014006':'농업인','0014007':'군인','0014008':'지역인재','0014009':'기타','0014010':'제한없음' },\n  plcyPvsnMthdCd: { '0042001':'인프라 구축','0042002':'프로그램','0042003':'직접대출','0042004':'공공기관','0042005':'계약(위탁운영)','0042006':'보조금','0042007':'대출보증','0042008':'공적보험','0042009':'조세지출','0042010':'바우처','0042011':'정보제공','0042012':'경제적 규제','0042013':'기타' },\n  bizPrdSeCd: { '0056001':'특정기간','0056002':'기타' },\n  aplyPrdSeCd: { '0057001':'특정기간','0057002':'상시','0057003':'마감' }\n};\n\n// === 자치구 코드 → 한글명 ===\nconst GU = { '11110':'종로구','11140':'중구','11170':'용산구','11200':'성동구','11215':'광진구','11230':'동대문구','11260':'중랑구','11290':'성북구','11305':'강북구','11320':'도봉구','11350':'노원구','11380':'은평구','11410':'서대문구','11440':'마포구','11470':'양천구','11500':'강서구','11530':'구로구','11545':'금천구','11560':'영등포구','11590':'동작구','11620':'관악구','11650':'서초구','11680':'강남구','11710':'송파구','11740':'강동구' };\nconst SEOUL_CODES = new Set(Object.keys(GU).concat(['11000']));\n\n// === 인코딩 정제 ===\nfunction clean(s) {\n  if (s == null) return '';\n  return String(s)\n    .replace(/\\u1B3C/g, '·')\n    .replace(/\\u00A0/g, ' ')\n    .replace(/\\s+/g, ' ')\n    .trim();\n}\n\nfunction splitTokens(s, sep) {\n  if (!s) return [];\n  return clean(s).split(sep || /[,\\u30FB\\uFF65·]/).map(x => x.trim()).filter(Boolean);\n}\n\n// === lclsfNm → Category (한글) ===\nfunction mapCategory(lclsf) {\n  const tokens = splitTokens(lclsf);\n  const order = [\n    [['일자리'], '일자리'],\n    [['주거'], '주거'],\n    [['교육','직업훈련'], '교육'],\n    [['금융'], '금융'],\n    [['문화','여가'], '문화'],\n    [['복지','복지문화'], '복지'],\n    [['참여','권리','참여권리'], '참여']\n  ];\n  for (const [keys, label] of order) {\n    for (const t of tokens) {\n      if (keys.some(k => t.includes(k))) return label;\n    }\n  }\n  return '복지';\n}\n\n// === aplyYmd 파싱 ===\nfunction parseApplyPeriod(aplyYmd) {\n  if (!aplyYmd) return { start: null, end: null };\n  const m = String(aplyYmd).match(/(\\d{8})\\s*~\\s*(\\d{8})/);\n  if (!m) return { start: null, end: null };\n  function fmt(s) { return `${s.slice(0,4)}-${s.slice(4,6)}-${s.slice(6,8)}`; }\n  return { start: fmt(m[1]), end: fmt(m[2]) };\n}\n\n// === zipCd → 한글 라벨 ===\nfunction regionLabel(zipCd) {\n  const codes = splitTokens(zipCd, ',');\n  const seoul = codes.filter(c => SEOUL_CODES.has(c));\n  const guCount = seoul.filter(c => c !== '11000').length;\n  if (guCount >= 25) return '서울특별시';\n  if (guCount === 1) {\n    const guCode = seoul.find(c => c !== '11000');\n    return `서울특별시 ${GU[guCode] || ''}`.trim();\n  }\n  return '서울특별시';\n}\n\n// === 본문 섹션 결합 ===\nfunction buildBody(p) {\n  const lines = [];\n  if (clean(p.plcyExplnCn)) lines.push('[개요]', clean(p.plcyExplnCn), '');\n\n  const tgt = [];\n  const minA = parseInt(p.sprtTrgtMinAge || '0', 10);\n  const maxA = parseInt(p.sprtTrgtMaxAge || '0', 10);\n  if (p.sprtTrgtAgeLmtYn === 'N') tgt.push('- 연령: 제한없음');\n  else if (minA || maxA) tgt.push(`- 연령: ${minA}~${maxA}세`);\n  if (CODE.mrgSttsCd[p.mrgSttsCd]) tgt.push(`- 결혼상태: ${CODE.mrgSttsCd[p.mrgSttsCd]}`);\n  if (CODE.earnCndSeCd[p.earnCndSeCd]) {\n    let earn = `- 소득조건: ${CODE.earnCndSeCd[p.earnCndSeCd]}`;\n    const eMin = parseInt(p.earnMinAmt || '0', 10);\n    const eMax = parseInt(p.earnMaxAmt || '0', 10);\n    if (eMin || eMax) earn += ` (${eMin.toLocaleString()}~${eMax.toLocaleString()}원)`;\n    if (clean(p.earnEtcCn) && clean(p.earnEtcCn) !== '-') earn += ` ${clean(p.earnEtcCn)}`;\n    tgt.push(earn);\n  }\n  if (CODE.jobCd[p.jobCd]) tgt.push(`- 취업상태: ${CODE.jobCd[p.jobCd]}`);\n  if (CODE.schoolCd[p.schoolCd]) tgt.push(`- 학력: ${CODE.schoolCd[p.schoolCd]}`);\n  if (CODE.plcyMajorCd[p.plcyMajorCd]) tgt.push(`- 전공: ${CODE.plcyMajorCd[p.plcyMajorCd]}`);\n  if (CODE.sbizCd[p.sbizCd]) tgt.push(`- 특화요건: ${CODE.sbizCd[p.sbizCd]}`);\n  if (clean(p.ptcpPrpTrgtCn)) tgt.push(`- 참여 제한 대상: ${clean(p.ptcpPrpTrgtCn)}`);\n  if (tgt.length) { lines.push('[지원대상]'); lines.push(...tgt); lines.push(''); }\n\n  const sel = [];\n  if (clean(p.srngMthdCn)) sel.push(clean(p.srngMthdCn));\n  if (clean(p.addAplyQlfcCndCn) && clean(p.addAplyQlfcCndCn) !== '해당사항 없음') sel.push(`추가 자격: ${clean(p.addAplyQlfcCndCn)}`);\n  if (sel.length) { lines.push('[선정기준]'); lines.push(...sel); lines.push(''); }\n  else { lines.push('[선정기준]', '별도 문의', ''); }\n\n  const sup = [];\n  if (clean(p.plcySprtCn)) sup.push(clean(p.plcySprtCn));\n  if (CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd]) sup.push(`제공방식: ${CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd]}`);\n  const sclCnt = parseInt(p.sprtSclCnt || '0', 10);\n  if (sclCnt) {\n    const arvl = p.sprtArvlSeqYn === 'Y' ? ' (선착순)' : '';\n    sup.push(`지원규모: ${sclCnt.toLocaleString()}명${arvl}`);\n  }\n  if (sup.length) { lines.push('[지원내용]'); lines.push(...sup); lines.push(''); }\n\n  if (clean(p.sbmsnDcmntCn)) lines.push('[제출서류]', clean(p.sbmsnDcmntCn), '');\n\n  const bizStart = clean(p.bizPrdBgngYmd);\n  const bizEnd = clean(p.bizPrdEndYmd);\n  if (bizStart && bizEnd) {\n    function fmt(s) { return s.length === 8 ? `${s.slice(0,4)}-${s.slice(4,6)}-${s.slice(6,8)}` : s; }\n    let line = `${fmt(bizStart)} ~ ${fmt(bizEnd)}`;\n    if (CODE.bizPrdSeCd[p.bizPrdSeCd]) line += ` (${CODE.bizPrdSeCd[p.bizPrdSeCd]})`;\n    lines.push('[사업기간]', line);\n    if (clean(p.bizPrdEtcCn)) lines.push(clean(p.bizPrdEtcCn));\n    lines.push('');\n  }\n\n  if (clean(p.etcMttrCn)) lines.push('[기타]', clean(p.etcMttrCn));\n\n  return lines.join('\\n').trim();\n}\n\n// === 메인 ===\nconst p = $input.first().json;\nif (p._empty) return [{ json: { _empty: true } }];\n\nconst { start, end } = parseApplyPeriod(p.aplyYmd);\nconst category = mapCategory(p.lclsfNm);\nconst region = regionLabel(p.zipCd);\n\nconst themeTags = Array.from(new Set([\n  ...splitTokens(p.lclsfNm),\n  ...splitTokens(p.mclsfNm),\n  ...splitTokens(p.plcyKywdNm)\n].map(clean).filter(Boolean)));\n\nconst targetTags = [];\nconst sbiz = CODE.sbizCd[p.sbizCd];\nif (sbiz && sbiz !== '제한없음' && sbiz !== '기타') targetTags.push(sbiz);\n\nconst orgParts = [];\nif (clean(p.sprvsnInstCdNm)) orgParts.push(clean(p.sprvsnInstCdNm));\nif (clean(p.operInstCdNm) && clean(p.operInstCdNm) !== clean(p.sprvsnInstCdNm)) orgParts.push(clean(p.operInstCdNm));\nlet organization = orgParts.join(' / ');\nif (organization.length > 200) organization = organization.slice(0, 200);\n\nconst contact = clean(p.sprvsnInstPicNm) ? `담당: ${clean(p.sprvsnInstPicNm)}` : '';\n\nconst referenceSites = [];\nif (clean(p.aplyUrlAddr)) referenceSites.push({ name: '신청 페이지', url: clean(p.aplyUrlAddr) });\nif (clean(p.refUrlAddr1)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr1) });\nif (clean(p.refUrlAddr2)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr2) });\n\nconst applyMethods = [];\nif (clean(p.plcyAplyMthdCn)) applyMethods.push({ stageName: '신청 절차', description: clean(p.plcyAplyMthdCn) });\n\nconst referenceYear = (p.frstRegDt && p.frstRegDt.length >= 4) ? parseInt(p.frstRegDt.slice(0, 4), 10) : null;\n\nconst result = {\n  source: {\n    url: `https://www.youthcenter.go.kr/youngPlcyUnif/youngPlcyUnifDtl.do?plcyNo=${p.plcyNo}`,\n    type: 'YOUTH_CENTER',\n    fetchedAt: new Date().toISOString().replace('Z', '')\n  },\n  rawData: {\n    externalId: p.plcyNo,\n    title: clean(p.plcyNm) || `(정책 ${p.plcyNo})`,\n    summary: clean(p.plcyExplnCn) || clean(p.plcyNm),\n    body: buildBody(p),\n    category,\n    region,\n    applyStart: start,\n    applyEnd: end,\n    referenceYear,\n    supportCycle: null,\n    provideType: CODE.plcyPvsnMthdCd[p.plcyPvsnMthdCd] || null,\n    organization,\n    contact,\n    lifeTags: ['청년'],\n    themeTags,\n    targetTags,\n    attachments: [],\n    referenceSites,\n    applyMethods\n  }\n};\nreturn [{ json: result }];"
      },
      "id": "transform",
      "name": "정책 → IngestPolicyRequest 변환",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [1320, 100]
    }
```

- [ ] **Step 2: connections 에 부분 연결 추가**

`connections` 객체에 추가:

```json
    "JSON 파싱 + 서울 필터": {
      "main": [[{ "node": "정책별 순차 처리", "type": "main", "index": 0 }]]
    },
    "정책별 순차 처리": {
      "main": [
        [{ "node": "다음 페이지 확인", "type": "main", "index": 0 }],
        [{ "node": "1초 대기", "type": "main", "index": 0 }]
      ]
    },
    "1초 대기": {
      "main": [[{ "node": "정책 → IngestPolicyRequest 변환", "type": "main", "index": 0 }]]
    },
```

> "다음 페이지 확인" 노드는 Task 4.4 에서 추가하므로, 지금 단계에선 connection 정의는 미리 두되 노드 미존재로 워크플로우 import 시 경고가 나올 수 있음. 4.4 까지 마쳐야 완전체.

- [ ] **Step 3: JSON 유효성**

Run: `python3 -m json.tool n8n/workflows/youth-center-seoul.json > /dev/null`

---

### Task 4.4: 백엔드 POST + 페이지네이션 + 완료 노드

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: 5개 노드 추가 (백엔드 POST, 다음 페이지 확인, IF, 다음 페이지 이동, 수집 완료)**

`nodes` 배열에 추가:

```json
    {
      "parameters": {
        "method": "POST",
        "url": "={{ $env.BACKEND_URL || 'http://backend:8080' }}/api/internal/ingestion/policies",
        "sendHeaders": true,
        "headerParameters": {
          "parameters": [
            { "name": "Content-Type", "value": "application/json" },
            { "name": "X-Internal-Api-Key", "value": "={{ $env.INTERNAL_API_KEY }}" }
          ]
        },
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify($json) }}",
        "options": {
          "timeout": 30000
        }
      },
      "id": "send-to-backend",
      "name": "백엔드 API 전송",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "onError": "continueRegularOutput",
      "position": [1540, 100]
    },
    {
      "parameters": {
        "jsCode": "const items = $('JSON 파싱 + 서울 필터').all();\nconst first = items[0]?.json || {};\nconst current = first._pageNum || 1;\nconst last = first._lastPage || 1;\nif (current < last) return [{ json: { pageNum: current + 1, hasNext: true } }];\nreturn [{ json: { pageNum: current, hasNext: false } }];"
      },
      "id": "check-next-page",
      "name": "다음 페이지 확인",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [1100, -200]
    },
    {
      "parameters": {
        "conditions": {
          "options": { "caseSensitive": true, "leftValue": "", "typeValidation": "strict" },
          "conditions": [
            {
              "id": "has-next",
              "leftValue": "={{ $json.hasNext }}",
              "rightValue": true,
              "operator": { "type": "boolean", "operation": "equals" }
            }
          ],
          "combinator": "and"
        },
        "options": {}
      },
      "id": "if-has-next",
      "name": "다음 페이지 존재?",
      "type": "n8n-nodes-base.if",
      "typeVersion": 2,
      "position": [1320, -200]
    },
    {
      "parameters": {
        "jsCode": "return [{ json: { pageNum: $input.first().json.pageNum } }];"
      },
      "id": "next-page",
      "name": "다음 페이지 이동",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [1540, -300]
    },
    {
      "parameters": {
        "jsCode": "const items = $('JSON 파싱 + 서울 필터').all();\nconst first = items[0]?.json || {};\nreturn [{ json: { message: '온통청년 수집 완료', totalPolicies: first._totCount || 0, totalPages: first._lastPage || 0, completedAt: new Date().toISOString() } }];"
      },
      "id": "complete",
      "name": "수집 완료",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [1540, -100]
    }
```

- [ ] **Step 2: connections 보강**

전체 `connections` 가 다음과 같이 구성되도록 정리:

```json
  "connections": {
    "매일 새벽 4시 실행": {
      "main": [[{ "node": "페이지 초기화", "type": "main", "index": 0 }]]
    },
    "수동 실행 트리거": {
      "main": [[{ "node": "페이지 초기화", "type": "main", "index": 0 }]]
    },
    "페이지 초기화": {
      "main": [[{ "node": "getPlcy 호출", "type": "main", "index": 0 }]]
    },
    "getPlcy 호출": {
      "main": [[{ "node": "JSON 파싱 + 서울 필터", "type": "main", "index": 0 }]]
    },
    "JSON 파싱 + 서울 필터": {
      "main": [[{ "node": "정책별 순차 처리", "type": "main", "index": 0 }]]
    },
    "정책별 순차 처리": {
      "main": [
        [{ "node": "다음 페이지 확인", "type": "main", "index": 0 }],
        [{ "node": "1초 대기", "type": "main", "index": 0 }]
      ]
    },
    "1초 대기": {
      "main": [[{ "node": "정책 → IngestPolicyRequest 변환", "type": "main", "index": 0 }]]
    },
    "정책 → IngestPolicyRequest 변환": {
      "main": [[{ "node": "백엔드 API 전송", "type": "main", "index": 0 }]]
    },
    "백엔드 API 전송": {
      "main": [[{ "node": "정책별 순차 처리", "type": "main", "index": 0 }]]
    },
    "다음 페이지 확인": {
      "main": [[{ "node": "다음 페이지 존재?", "type": "main", "index": 0 }]]
    },
    "다음 페이지 존재?": {
      "main": [
        [{ "node": "다음 페이지 이동", "type": "main", "index": 0 }],
        [{ "node": "수집 완료", "type": "main", "index": 0 }]
      ]
    },
    "다음 페이지 이동": {
      "main": [[{ "node": "getPlcy 호출", "type": "main", "index": 0 }]]
    }
  }
```

- [ ] **Step 3: JSON 최종 유효성**

Run: `python3 -m json.tool n8n/workflows/youth-center-seoul.json > /dev/null`
Expected: 출력 없음

- [ ] **Step 4: 노드 ID 중복 확인**

Run: `python3 -c "import json; ids=[n['id'] for n in json.load(open('n8n/workflows/youth-center-seoul.json'))['nodes']]; assert len(ids)==len(set(ids)), 'duplicate id'; print('OK', len(ids), 'nodes')"`
Expected: `OK 12 nodes`

- [ ] **Step 5: 커밋 (Task 4.1~4.4 묶음)**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): 온통청년 서울 청년 정책 수집 워크플로우 (테스트 모드 1페이지)

- /go/ythip/getPlcy 전체 페이징 호출 (zipCd 필터 없음)
- 응답 youthPolicyList 에서 zipCd 가 서울 26개 코드 중 하나라도 포함된
  정책만 백엔드로 전송
- Code 노드에서 lclsfNm → category, mclsfNm/plcyKywdNm → themeTags,
  sbizCd → targetTags, aplyYmd 파싱, zipCd → 한글 라벨 정규화
- 코드 사전(엑셀 출처)을 인라인 JS 객체로 박아 mrgSttsCd/jobCd/schoolCd
  등을 본문 [지원대상]/[지원내용]/[사업기간] 섹션 풀이로 전환
- 첫 배포는 lastPage=1 강제 (1페이지만 처리). 검증 후 풀 페이징 활성화

배경 스펙: docs/superpowers/specs/2026-05-08-youth-center-ingestion-design.md"
```

---

### Task 4.5: `source.url` 패턴 검증 (사용자 액션)

**Files:** (없음 — 브라우저 검증)

- [ ] **Step 1: 응답 1건의 plcyNo 로 URL 접근**

브라우저에서 다음 URL 접속 (plcyNo 는 실제 응답에서 골라 사용):

```
https://www.youthcenter.go.kr/youngPlcyUnif/youngPlcyUnifDtl.do?plcyNo=20260430005400113009
```

- [ ] **Step 2: 결과 분기**

| 결과 | 다음 액션 |
|---|---|
| 정책 상세 페이지 정상 표시 | ✅ Task 4.6 으로 진행 |
| 404 / 검색 페이지로 리다이렉트 | URL 패턴 정정 — Task 4.3 의 `source.url` 부분 수정 후 재커밋 |

- [ ] **Step 3 (조건부): URL 패턴 정정**

대안 패턴 후보:
- `https://www.youthcenter.go.kr/youngPlcyUnif/youngPlcyUnifList.do?bizId={plcyNo}`
- `https://www.youthcenter.go.kr/main.do` 등 검색 폴백

브라우저에서 작동하는 패턴 확인 후 `transform` 노드의 jsCode 내 `source.url` 부분 수정 + 별도 commit.

---

### Task 4.6: 1페이지 테스트 모드 배포 + 검증 (사용자 액션)

**Files:** (없음 — 운영 액션)

- [ ] **Step 1: n8n 환경변수 설정**

운영 n8n 인스턴스 환경변수에 추가:
- `YOUTH_CENTER_API_KEY` = `.env` 의 값 그대로

- [ ] **Step 2: 워크플로우 import**

n8n UI에서 `n8n/workflows/youth-center-seoul.json` import. activate 는 OFF 유지.

- [ ] **Step 3: 수동 실행 트리거**

```bash
curl -fsS -X POST https://<n8n-host>/webhook/youth-center-manual
```

또는 n8n UI 의 "Execute Workflow" 버튼 클릭.

- [ ] **Step 4: n8n executions 결과 확인**

- 모든 노드 정상 실행
- `JSON 파싱 + 서울 필터` 출력에서 서울 정책 수 확인 (보통 50~150건)
- `백엔드 API 전송` 응답 status 분포 확인

- [ ] **Step 5: 백엔드 DB 검증**

```sql
-- 신규 등록된 YOUTH_CENTER 정책 수
SELECT COUNT(*) FROM policy_source WHERE source_type = 'YOUTH_CENTER';

-- 샘플 5건 시각 검증
SELECT p.id, p.title, p.category, p.region_code, p.normalized_title,
       p.apply_start, p.apply_end, p.reference_year,
       LEFT(p.body, 200) AS body_preview
FROM policy p
JOIN policy_source ps ON ps.policy_id = p.id
WHERE ps.source_type = 'YOUTH_CENTER'
ORDER BY p.id DESC
LIMIT 5;

-- SKIPPED_DUPLICATE 비율 (n8n executions 에서 백엔드 응답 status 카운트)
-- 또는 ingestion_run_log 의 duplicate 카운트 확인
SELECT * FROM ingestion_run_log
WHERE source_label = 'YOUTH_CENTER'
ORDER BY created_at DESC LIMIT 5;
```

- [ ] **Step 6: 매핑 정확성 시각 검증**

5건 샘플의 다음 필드를 직접 눈으로 확인:
- `title` — 인코딩 깨짐 (`᭼`) 없는지
- `category` — `lclsfNm` 풀이가 적절한지
- `region_code` — `"서울특별시"` 또는 `"서울특별시 ○○구"` 형식인지
- `body` — `[개요]/[지원대상]/[선정기준]/[지원내용]` 4섹션 + 추가 섹션이 풍부하게 포함되는지
- `apply_start/end` — `aplyYmd` 가 빈 값이면 둘 다 null, 형식 있으면 LocalDate
- `theme_tags` 와 `target_tags`

- [ ] **Step 7: 결과 분기**

| 결과 | 다음 액션 |
|---|---|
| 매핑 정확, 샘플 모두 정상 | ✅ Task 4.7 풀 페이징 활성화 |
| 일부 필드 이상 | Task 4.3 의 변환 로직 수정 → 재배포 → 재검증 (Task 4.6 반복) |

---

### Task 4.7: 풀 페이징 + cron 활성화

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: 테스트 모드 해제**

`parse-and-filter` Code 노드의 jsCode 안에서 다음 부분을 변경:

찾기:
```javascript
// TEST MODE: 첫 1페이지만 처리 (검증용). 풀 페이징 활성화 시 아래 줄로 교체.
const lastPage = 1;
// const lastPage = Math.max(1, Math.ceil(totCount / pageSize));
```

바꾸기:
```javascript
const lastPage = Math.max(1, Math.ceil(totCount / pageSize));
```

- [ ] **Step 2: 워크플로우 재import + activate ON**

n8n UI 에서 새 JSON import (덮어쓰기). 그리고 워크플로우 activate ON.

- [ ] **Step 3: 다음 04:00 또는 수동 실행**

자동 cron 다음 실행은 04:00. 즉시 검증하려면 다시 webhook 호출.

- [ ] **Step 4: 풀 실행 결과 검증**

```sql
-- YOUTH_CENTER 정책 수가 서울 단위 totCount 와 비슷한 수준 (450~500)
SELECT COUNT(*) FROM policy_source WHERE source_type = 'YOUTH_CENTER';

-- BOKJIRO 와 정규화 제목 충돌로 SKIPPED 된 비율
SELECT
  (SELECT COUNT(*) FROM policy_source WHERE source_type = 'YOUTH_CENTER') AS registered,
  (SELECT duplicate_count FROM ingestion_run_log WHERE source_label = 'YOUTH_CENTER' ORDER BY created_at DESC LIMIT 1) AS skipped_duplicate;
```

- [ ] **Step 5: 커밋**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "chore(n8n): 온통청년 워크플로우 풀 페이징 활성화

테스트 모드 lastPage=1 해제. 1페이지 검증 통과 후 전체 페이지를
처리하도록 변경. cron(0 4 * * *) 도 활성화."
```

---

## Phase 5 — 문서

### Task 5.1: PRD `08-ingestion.md` 정정

**Files:**
- Modify: `docs/prd/08-ingestion.md`

- [ ] **Step 1: 헤더의 "구현 상태" 줄 정정**

찾기:
```
> **구현 상태**: 백엔드 수신 API 확장 완료 / 복지로 중앙부처 n8n 워크플로우 완료 / 온통청년 지자체 n8n 워크플로우 대기 (API 키 발급 중)
```

바꾸기:
```
> **구현 상태**: 백엔드 수신 API 확장 완료 / 복지로 중앙부처 n8n 워크플로우 완료 / 온통청년 지자체 n8n 워크플로우 완료 (서울 스코프, 풀 페이징)
```

- [ ] **Step 2: v0-B 섹션의 "엔드포인트(예정)" 와 매핑표 정정**

`### v0-B — 온통청년 API` 섹션 전체를 다음으로 교체:

```markdown
### v0-B — 온통청년 API (구현 완료)

| 항목 | 내용 |
|------|------|
| **제공기관** | 청년정책조정위원회 (국무조정실) |
| **도메인** | www.youthcenter.go.kr/go/ythip |
| **sourceType** | `YOUTH_CENTER` |
| **data.go.kr 서비스 ID** | 15128179 (청년정책 통합검색 API) |
| **응답 포맷** | JSON |
| **커버리지** | 지자체(시·도·구) 청년 정책 보충 (복지로 중앙부처와 상호보완) |
| **호출 전략** | 전체 페이징 (zipCd 필터 없음) + 응답측 서울 필터 |
| **스코프 (v0)** | 서울특별시(11000) + 서울 25개 자치구(11110~11740) |

#### 엔드포인트

```
GET https://www.youthcenter.go.kr/go/ythip/getPlcy
  ?apiKeyNm={인증키}
  &rtnType=json
  &pageNum={페이지}
  &pageSize=100
```

#### 응답 → DB 매핑 (실응답 기반 검증)

| 응답 필드 | 매핑 대상 | 비고 |
|-----------|-----------|------|
| `plcyNo` | `policy_source.external_id` | UNIQUE 키 |
| `plcyNm` | `policy.title` | 인코딩 정제 (᭼→·) |
| `plcyExplnCn` | `policy.summary` | 빈 값이면 title 폴백 |
| `lclsfNm` | `policy.category` | `･` 구분 다중값 → 첫 매치, 5종 분류 |
| `mclsfNm` + `plcyKywdNm` | `policy.theme_tags` | 표준 17·17종 |
| `aplyYmd` ("YYYYMMDD ~ YYYYMMDD") | `policy.apply_start` / `apply_end` | 빈 값/수시 → null |
| `zipCd` (콤마 구분 다중값) | `policy.region_code` | 서울 자치구 25개 모두 → "서울특별시", 단일 → "서울특별시 ○○구" |
| `sprvsnInstCdNm` + `operInstCdNm` | `policy.organization` | 200자 컷 |
| `sprvsnInstPicNm` | `policy.contact` | "담당: {이름}" |
| `aplyUrlAddr`, `refUrlAddr1/2` | `policy.reference_sites` | jsonb |
| `plcyAplyMthdCn` | `policy.apply_methods` | 단일 entry |
| `frstRegDt` 연도 | `policy.reference_year` | |
| `mrgSttsCd`, `jobCd`, `schoolCd`, `plcyMajorCd`, `sbizCd`, `plcyPvsnMthdCd`, `bizPrdSeCd` | `policy.body` 본문 풀이 | data.go.kr 공식 코드 사전(`docs/prd/reference/youth-center-codes.xlsx`)으로 한글 풀이 |
| `sprtTrgtMinAge/MaxAge`, `earnMin/MaxAmt`, `sbmsnDcmntCn`, `etcMttrCn` | `policy.body` 본문 섹션 | [지원대상]/[제출서류]/[기타] |
| — | `policy.life_tags` | `["청년"]` 고정 |
| — | `attachments` | 항상 빈 배열 (응답에 첨부 필드 없음) |
```

- [ ] **Step 3: "중복 제거 전략" 섹션의 "자연 스킵" 문구 정정**

찾기 (159~161 line 근방):
```
- 순서에 관계없이 결과가 같도록 `YOUTH_CENTER`가 먼저 들어온 뒤 `BOKJIRO_CENTRAL`이 들어오는 경우에는 `BOKJIRO_CENTRAL`이 별개 정책으로 등록된 후, 다음 주기 스케줄에서 `YOUTH_CENTER` 중복건은 자연 스킵된다.
```

바꾸기:
```
- 정상 스케줄에서는 BOKJIRO 03:00 → YOUTH_CENTER 04:00 순으로 BOKJIRO 가 항상 먼저 들어오므로 자연스럽게 우선권이 부여된다. 엣지 케이스(YOUTH_CENTER 가 먼저 들어가있는데 며칠 뒤 동일 제목의 BOKJIRO 가 신규 등록)에서는 별개 정책 2건이 일시적으로 공존할 수 있으며, v0 에서는 미처리한다(빈도 매우 낮음, 어드민 수동 머지 또는 v1 보강).
```

- [ ] **Step 4: n8n 워크플로우 의사 코드 섹션을 실제 워크플로우 참조로 단순화**

찾기:
```
#### 온통청년 수집 (`n8n/workflows/youth-center-seoul.json` — 대기)
```
부터
```
- `YOUTH_CENTER_API_KEY`: data.go.kr 인증키 (발급 대기 중)
```
까지의 블록 전체 교체:

```
#### 온통청년 수집 (`n8n/workflows/youth-center-seoul.json` — 구현 완료)

흐름:

```
[Schedule Trigger / Webhook]   매일 04:00 또는 수동
       ↓
[페이지 초기화]                pageNum=1
       ↓
[getPlcy 호출]                 zipCd 필터 없음, pageSize=100
       ↓
[JSON 파싱 + 서울 필터]        zipCd 에 서울 26개 코드 중 하나라도 포함되는 정책만 통과
       ↓
[SplitInBatches batchSize=1]
       ↓
[1초 대기]                     백엔드 보호
       ↓
[변환 Code 노드]               코드 사전 풀이 + 본문 섹션 결합
       ↓
[POST /api/internal/ingestion/policies]
       ↓
[페이지 루프]                  pageNum < lastPage 면 다음 페이지
```

**환경변수**:
- `YOUTH_CENTER_API_KEY`: data.go.kr 인증키
```

- [ ] **Step 5: 다른 곳에 남은 "대기" / "API 키 발급 중" 문구 검색 및 정정**

Run: `grep -n "대기\|발급 중\|발급중" docs/prd/08-ingestion.md`
Expected: 0건. 1건이라도 나오면 컨텍스트 보고 정정.

- [ ] **Step 6: 커밋**

```bash
git add docs/prd/08-ingestion.md
git commit -m "docs(prd): 온통청년 ingestion 실응답 기반 매핑·흐름·자연 스킵 문구 정정

- 호출 전략: 자치구 26회 호출 가정 → 전체 페이징 + 응답측 서울 필터
- 응답 매핑: aplyYmdBgn/End 가정 → aplyYmd 단일 문자열 파싱 명시
- 카테고리 매핑: 5종 공식 분류 + 우선순위 명시
- 코드 사전(엑셀)로 본문 풀이하는 항목들 추가
- 중복 제거 자연 스킵 문구를 정확한 우선순위 기반 설명으로 정정
- 구현 상태: 대기 → 완료"
```

---

## 자체 점검

### 스펙 커버리지

| 스펙 섹션 | 구현 task |
|---|---|
| §3 호출 전략 (전체 페이징) | Task 4.2 (Code 노드의 zipCd 필터 없음) |
| §3 region_code 한글 라벨 | Task 4.3 (`regionLabel` 함수) |
| §3 복지로 우선 dedup | Task 1.7 + 2.1 |
| §3 UNIQUE 안전망 | Task 1.4 + 1.6 |
| §3 정규화 제목 GENERATED | Task 1.4 + 1.5 |
| §3 코드 사전 인라인 | Task 4.3 |
| §4.1 직접 매핑 | Task 4.3 |
| §4.2 변환 매핑 | Task 4.3 |
| §4.3 lclsfNm → Category | Task 4.3 (`mapCategory`) |
| §4.4 zipCd → 한글 라벨 26가지 | Task 4.3 (`regionLabel`) |
| §4.5 body 섹션 결합 | Task 4.3 (`buildBody`) |
| §4.6 태그 매핑 | Task 4.3 |
| §4.7 첨부 빈 배열 / source.url | Task 4.3 + 4.5 |
| §5 워크플로우 노드 | Task 4.1 + 4.2 + 4.3 + 4.4 |
| §5.5 테스트 모드 | Task 4.6 |
| §6 백엔드 변경 | Task 1.1~1.7 + 2.1~2.2 |
| §7 변경 파일 목록 | 전체 task |
| §8 단위/통합 테스트 | Task 1.1, 1.2, 2.1, 2.2 |
| §9 사전 점검 + 롤아웃 | Task 0.1, 3.1, 4.5, 4.6, 4.7 |
| §10 운영 모니터링 | Task 4.6 (로그 SQL 확인) |
| §11 비범위 | 명시적으로 미포함 |
| §12 위험 대응 | Task 0.1, 1.3, 4.5 |

### Placeholder 스캔

전체 plan 에서 다음 패턴 검색:
- "TBD" / "TODO" / "fill in" / "implement later" / "Add appropriate" / "Similar to Task" → 0건
- 모든 step 에 실제 코드/명령/검증 명시
- 테스트 코드는 모두 풀로 (단순 `// 비슷하게...` 미사용)

### Type / 메서드명 일관성

- `TitleNormalizer.normalize` (Task 1.1) ↔ `PolicyIngestionService` 호출 (Task 2.1) — 동일 시그니처 ✓
- `PolicyIngestionResult.Outcome` enum (Task 1.2) ↔ `IngestionService` import (Task 2.2) — 동일 ✓
- `findByNormalizedTitleWithBokjiroSource` (Task 1.7) ↔ 호출 (Task 2.1) — 동일 ✓
- `IngestPolicyResult.status` 새 값 `"SKIPPED_DUPLICATE"` (Task 2.2) ↔ Task 4.6 검증 SQL — 일관 ✓
- 워크플로우 `source.type = 'YOUTH_CENTER'` (Task 4.3) ↔ 백엔드 `SourceType.YOUTH_CENTER` (이미 존재) — 일관 ✓

자체 점검 통과.
