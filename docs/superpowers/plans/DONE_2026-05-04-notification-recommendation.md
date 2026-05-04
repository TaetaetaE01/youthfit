# 알림 설정·맞춤 정책 추천 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마감 알림 백엔드 정합을 회복하고, 관심 분야(카테고리·시/도)를 입력받아 매주 월요일 적합도 + 관심분야 매칭으로 맞춤 정책 5건을 이메일 추천한다.

**Architecture:** 모든 변경은 `user` 모듈에 한정. 추천 산출은 신설 `RecommendationDispatchService`(application) + `PolicyRecommender`(domain service)가 `eligibility.EligibilityService.judgeEligibility(userId, policyId)`를 N번 호출. 발송은 `RecommendationScheduler`로 매주 월 09:00 트리거. 3단계 PR로 분리하여 각 단계가 독립적으로 빌드·배포 가능하게 한다.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA + PostgreSQL, JUnit 5, Spring Scheduling. Frontend: React 19, TypeScript 5, TanStack Query v5, Tailwind v4.

**관련 문서**: `docs/superpowers/specs/2026-05-04-notification-recommendation-design.md`

---

## File Map

### Phase 1 — 백엔드 정합 회복 + 프론트 키 통일

**Backend (modify)**
- `backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java`
- `backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java`
- `backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java`
- `backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java`
- `backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java`
- `backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java`

**Backend (create)**
- `backend/src/main/resources/sql/2026-05-04-notification-recommendation-toggle.sql`
- `backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java`
- `backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java`

**Frontend (modify)**
- `frontend/src/types/policy.ts`
- `frontend/src/pages/MyPage.tsx`
- `frontend/src/components/policy/NotificationPromptSheet.tsx`

### Phase 2 — 관심 분야 모델 + API + UI

**Backend (create)**
- `backend/src/main/java/com/youthfit/user/domain/model/RegionSidoCode.java`
- `backend/src/main/resources/sql/2026-05-04-notification-interest.sql`
- `backend/src/test/java/com/youthfit/user/domain/model/RegionSidoCodeTest.java`

**Backend (modify)**
- `backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java`
- `backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java`
- `backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java`
- `backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java`
- `backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java`
- `backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java`

**Frontend (create)**
- `frontend/src/lib/labels/region.ts`
- `frontend/src/lib/labels/category.ts` (only if 미존재 — 확인)
- `frontend/src/components/notification/InterestCategoryChips.tsx`
- `frontend/src/components/notification/InterestRegionChips.tsx`
- `frontend/src/components/notification/RecommendationSection.tsx`

**Frontend (modify)**
- `frontend/src/types/policy.ts`
- `frontend/src/apis/user.api.ts` (필요 시)
- `frontend/src/pages/MyPage.tsx` (추천 섹션 교체)

### Phase 3 — 추천 발송 파이프라인

**Backend (create)**
- `backend/src/main/java/com/youthfit/user/domain/model/NotificationType.java`
- `backend/src/main/java/com/youthfit/user/domain/service/PolicyRecommender.java`
- `backend/src/main/java/com/youthfit/user/application/service/RecommendationDispatchService.java`
- `backend/src/main/java/com/youthfit/user/infrastructure/scheduler/RecommendationScheduler.java`
- `backend/src/test/java/com/youthfit/user/domain/service/PolicyRecommenderTest.java`
- `backend/src/test/java/com/youthfit/user/application/service/RecommendationDispatchServiceTest.java`

**Backend (modify)**
- `backend/src/main/java/com/youthfit/user/application/port/EmailSender.java`
- `backend/src/main/java/com/youthfit/user/infrastructure/email/LoggingEmailSender.java`
- `backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java` (`notificationType` 컬럼 enum 매핑)
- `backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java` (`"DEADLINE"` 문자열 → enum 사용)

---

# Phase 1 — 백엔드 정합 회복 + 프론트 키 통일

> **PR 끝 상태**: 사용자가 `recommendationEnabled` 토글을 변경하면 백엔드에 정상 저장되고 다시 조회 시 유지된다. 발송 로직은 아직 없으므로 토글이 켜져도 메일은 안 나간다.

## Task 1.1: NotificationSetting 도메인 모델에 recommendationEnabled 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java`
- Test: `backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java`

- [ ] **Step 1: Write failing tests**

`backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java`:
```java
package com.youthfit.user.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingTest {

    @Test
    void 새로_생성된_설정의_기본값() {
        NotificationSetting setting = new NotificationSetting(1L);

        assertThat(setting.isEmailEnabled()).isTrue();
        assertThat(setting.getDaysBeforeDeadline()).isEqualTo(7);
        assertThat(setting.isRecommendationEnabled()).isFalse();
    }

    @Test
    void updateSetting은_세_필드를_갱신한다() {
        NotificationSetting setting = new NotificationSetting(1L);

        setting.updateSetting(false, 14, true);

        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.getDaysBeforeDeadline()).isEqualTo(14);
        assertThat(setting.isRecommendationEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && ./gradlew test --tests NotificationSettingTest
```
Expected: FAIL — `isRecommendationEnabled` 메서드 없음 / `updateSetting`이 인자 2개.

- [ ] **Step 3: Update NotificationSetting**

`backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java` 전체:
```java
package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "days_before_deadline", nullable = false)
    private int daysBeforeDeadline;

    @Column(name = "recommendation_enabled", nullable = false)
    private boolean recommendationEnabled;

    public NotificationSetting(Long userId) {
        this.userId = userId;
        this.emailEnabled = true;
        this.daysBeforeDeadline = 7;
        this.recommendationEnabled = false;
    }

    public void updateSetting(boolean emailEnabled, int daysBeforeDeadline,
                              boolean recommendationEnabled) {
        this.emailEnabled = emailEnabled;
        this.daysBeforeDeadline = daysBeforeDeadline;
        this.recommendationEnabled = recommendationEnabled;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
cd backend && ./gradlew test --tests NotificationSettingTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java \
        backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java
git commit -m "feat(user): NotificationSetting에 recommendationEnabled 필드 추가"
```

## Task 1.2: 마이그레이션 SQL 추가

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-04-notification-recommendation-toggle.sql`

- [ ] **Step 1: Write SQL**

`backend/src/main/resources/sql/2026-05-04-notification-recommendation-toggle.sql`:
```sql
-- 알림 설정에 추천 알림 토글 추가
ALTER TABLE notification_setting
    ADD COLUMN recommendation_enabled boolean NOT NULL DEFAULT false;
```

- [ ] **Step 2: 로컬 DB에 적용 (선택)**

Dev 환경은 `ddl-auto=update`라 `bootRun`/`gradle build` 실행 시 자동 반영되지만, prod 배포 전 명시적 SQL이 필요하다. 로컬에 prod-style PostgreSQL을 띄워 점검하는 사람이 있다면:
```
psql -h localhost -U youthfit -d youthfit -f backend/src/main/resources/sql/2026-05-04-notification-recommendation-toggle.sql
```
실패 시 컬럼이 이미 존재할 수 있음 (`ddl-auto=update` 잔재).

- [ ] **Step 3: Commit**

```
git add backend/src/main/resources/sql/2026-05-04-notification-recommendation-toggle.sql
git commit -m "chore(user): notification_setting.recommendation_enabled 마이그레이션"
```

## Task 1.3: Command/Result/Request/Response DTO에 recommendationEnabled 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java`
- Modify: `backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java`
- Modify: `backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java`
- Modify: `backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java`

- [ ] **Step 1: UpdateNotificationSettingCommand**

전체 교체:
```java
package com.youthfit.user.application.dto.command;

public record UpdateNotificationSettingCommand(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled
) {
}
```

- [ ] **Step 2: NotificationSettingResult**

전체 교체:
```java
package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.NotificationSetting;

import java.time.LocalDateTime;

public record NotificationSettingResult(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isEmailEnabled(),
                setting.getDaysBeforeDeadline(),
                setting.isRecommendationEnabled(),
                setting.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 3: UpdateNotificationSettingRequest**

전체 교체:
```java
package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull(message = "이메일 알림 수신 여부는 필수입니다")
        Boolean emailEnabled,

        @NotNull(message = "알림 시점(daysBeforeDeadline)은 필수입니다")
        Integer daysBeforeDeadline,

        @NotNull(message = "추천 알림 수신 여부는 필수입니다")
        Boolean recommendationEnabled
) {

    @AssertTrue(message = "알림 시점은 3, 7, 14 중 하나여야 합니다")
    public boolean isDaysBeforeDeadlineValid() {
        if (daysBeforeDeadline == null) {
            return true;
        }
        return daysBeforeDeadline == 3 || daysBeforeDeadline == 7 || daysBeforeDeadline == 14;
    }

    public UpdateNotificationSettingCommand toCommand() {
        return new UpdateNotificationSettingCommand(emailEnabled, daysBeforeDeadline, recommendationEnabled);
    }
}
```

- [ ] **Step 4: NotificationSettingResponse**

전체 교체:
```java
package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.NotificationSettingResult;

import java.time.LocalDateTime;

public record NotificationSettingResponse(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.emailEnabled(),
                result.daysBeforeDeadline(),
                result.recommendationEnabled(),
                result.updatedAt()
        );
    }
}
```

- [ ] **Step 5: Build to verify compile**

```
cd backend && ./gradlew compileJava
```
Expected: compile 성공. (다음 task에서 service까지 갱신해야 전체 빌드 통과)

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java \
        backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java \
        backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java \
        backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java
git commit -m "feat(user): 알림 설정 DTO에 recommendationEnabled 필드 추가"
```

## Task 1.4: NotificationSettingService 갱신

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java`
- Test: `backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java`

- [ ] **Step 1: Write failing test**

`backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java`:
```java
package com.youthfit.user.application.service;

import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSettingServiceTest {

    @Test
    void updateNotificationSetting은_recommendationEnabled까지_저장한다() {
        NotificationSettingRepository repo = mock(NotificationSettingRepository.class);
        NotificationSetting existing = new NotificationSetting(1L);
        when(repo.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationSettingService service = new NotificationSettingService(repo);

        NotificationSettingResult result = service.updateNotificationSetting(
                1L, new UpdateNotificationSettingCommand(false, 14, true));

        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.daysBeforeDeadline()).isEqualTo(14);
        assertThat(result.recommendationEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && ./gradlew test --tests NotificationSettingServiceTest
```
Expected: FAIL — service가 아직 3번째 인자를 처리 안 함.

- [ ] **Step 3: Update service**

`backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java`의 `updateNotificationSetting` 메서드 수정:
```java
@Transactional
public NotificationSettingResult updateNotificationSetting(Long userId, UpdateNotificationSettingCommand command) {
    NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
            .orElseGet(() -> notificationSettingRepository.save(new NotificationSetting(userId)));
    setting.updateSetting(command.emailEnabled(), command.daysBeforeDeadline(), command.recommendationEnabled());
    return NotificationSettingResult.from(setting);
}
```

- [ ] **Step 4: Run all tests**

```
cd backend && ./gradlew test
```
Expected: PASS. 전체 빌드도 함께 검증 (`./gradlew build` 권장).

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java \
        backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java
git commit -m "feat(user): updateNotificationSetting이 recommendationEnabled를 처리"
```

## Task 1.5: 프론트 타입 / 사용처에서 키 리네임

**Files:**
- Modify: `frontend/src/types/policy.ts`
- Modify: `frontend/src/pages/MyPage.tsx`
- Modify: `frontend/src/components/policy/NotificationPromptSheet.tsx`

- [ ] **Step 1: types/policy.ts 수정**

`frontend/src/types/policy.ts`의 `NotificationSettings` 인터페이스에서 `eligibilityRecommendationEnabled` → `recommendationEnabled`. 다른 필드 변경 없음.

```ts
export interface NotificationSettings {
  emailEnabled: boolean;
  daysBeforeDeadline: number;
  recommendationEnabled: boolean;
}
```

(주의: `interestCategories`, `interestRegions`는 Phase 2에서 추가. 이번에는 손대지 않음.)

- [ ] **Step 2: 키 리네임 사용처 일괄 치환**

다음 명령으로 사용처를 찾는다:
```
cd frontend && rg -n "eligibilityRecommendationEnabled" src/
```

각 파일에서 `eligibilityRecommendationEnabled` → `recommendationEnabled`로 일괄 치환. 예상 파일:
- `frontend/src/pages/MyPage.tsx`
- `frontend/src/components/policy/NotificationPromptSheet.tsx`

state 변수명(`eligibilityRecommendationEnabled` `setEligibilityRecommendationEnabled`)도 함께 짧게 정리해도 좋고, 그대로 둬도 무방. 가독성을 위해 state도 `recommendationEnabled`/`setRecommendationEnabled`로 통일 권장.

- [ ] **Step 3: 타입체크 + 빌드**

```
cd frontend && npx tsc --noEmit
cd frontend && npm run build
```
Expected: 에러 없음.

- [ ] **Step 4: 수동 확인 (선택)**

`npm run dev`로 띄우고 마이페이지 알림 탭에서 추천 토글을 끄고 다시 켜보고 새로고침. 토글 상태가 유지되는지 확인.

- [ ] **Step 5: Commit**

```
git add frontend/src/types/policy.ts \
        frontend/src/pages/MyPage.tsx \
        frontend/src/components/policy/NotificationPromptSheet.tsx
git commit -m "refactor(frontend): 알림 설정 키를 recommendationEnabled로 통일"
```

## Task 1.6: Phase 1 PR 생성

- [ ] **Step 1: Branch + push**

```
git checkout -b feat/notification-recommendation-phase1
git push -u origin feat/notification-recommendation-phase1
```

- [ ] **Step 2: PR 생성**

```
gh pr create --title "feat(user): 알림 설정 recommendationEnabled 정합 회복" \
  --body "$(cat <<'EOF'
## Summary
- `NotificationSetting`/Command/Result/Request/Response에 `recommendationEnabled` 필드 추가
- 프론트 키를 `eligibilityRecommendationEnabled` → `recommendationEnabled`로 통일
- 마이그레이션 SQL 추가 (`recommendation_enabled boolean default false`)

## Test plan
- [ ] 백엔드 단위 테스트 통과 (`./gradlew test`)
- [ ] 프론트 타입체크/빌드 통과 (`npm run build`)
- [ ] 마이페이지 알림 탭에서 추천 토글 변경 후 새로고침 시 상태 유지 확인

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

# Phase 2 — 관심 분야 모델 + API + UI

> **PR 끝 상태**: 사용자가 마이페이지에서 카테고리·시/도 다중선택을 저장하고, 백엔드에 정상 보존된다. 발송 로직은 아직 없으므로 메일은 안 나간다.

## Task 2.1: RegionSidoCode enum 신설

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/domain/model/RegionSidoCode.java`
- Test: `backend/src/test/java/com/youthfit/user/domain/model/RegionSidoCodeTest.java`

- [ ] **Step 1: Write failing test**

`backend/src/test/java/com/youthfit/user/domain/model/RegionSidoCodeTest.java`:
```java
package com.youthfit.user.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RegionSidoCodeTest {

    @Test
    void null_또는_NATIONAL_정책_regionCode는_모든_시도와_매칭된다() {
        assertThat(RegionSidoCode.SEOUL.matches(null)).isTrue();
        assertThat(RegionSidoCode.SEOUL.matches("NATIONAL")).isTrue();
        assertThat(RegionSidoCode.GYEONGGI.matches(null)).isTrue();
    }

    @Test
    void 영문_시도_코드와_매칭된다() {
        assertThat(RegionSidoCode.SEOUL.matches("SEOUL")).isTrue();
        assertThat(RegionSidoCode.SEOUL.matches("BUSAN")).isFalse();
    }

    @Test
    void 법정동_prefix와_매칭된다() {
        assertThat(RegionSidoCode.SEOUL.matches("11110000")).isTrue();
        assertThat(RegionSidoCode.SEOUL.matches("26110000")).isFalse();
        assertThat(RegionSidoCode.BUSAN.matches("26110000")).isTrue();
    }

    @Test
    void displayName과_legalDongPrefix가_정의되어_있다() {
        assertThat(RegionSidoCode.SEOUL.getDisplayName()).isEqualTo("서울");
        assertThat(RegionSidoCode.SEOUL.getLegalDongPrefix()).isEqualTo("11");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && ./gradlew test --tests RegionSidoCodeTest
```
Expected: FAIL — class 미존재.

- [ ] **Step 3: Implement RegionSidoCode**

`backend/src/main/java/com/youthfit/user/domain/model/RegionSidoCode.java`:
```java
package com.youthfit.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegionSidoCode implements LabeledEnum {
    SEOUL("서울", "11"),
    BUSAN("부산", "26"),
    DAEGU("대구", "27"),
    INCHEON("인천", "28"),
    GWANGJU("광주", "29"),
    DAEJEON("대전", "30"),
    ULSAN("울산", "31"),
    SEJONG("세종", "36"),
    GYEONGGI("경기", "41"),
    GANGWON("강원", "42"),
    CHUNGBUK("충북", "43"),
    CHUNGNAM("충남", "44"),
    JEONBUK("전북", "45"),
    JEONNAM("전남", "46"),
    GYEONGBUK("경북", "47"),
    GYEONGNAM("경남", "48"),
    JEJU("제주", "50");

    private final String displayName;
    private final String legalDongPrefix;

    public boolean matches(String policyRegionCode) {
        if (policyRegionCode == null || "NATIONAL".equals(policyRegionCode)) {
            return true;
        }
        if (this.name().equals(policyRegionCode)) {
            return true;
        }
        if (legalDongPrefix != null && policyRegionCode.startsWith(legalDongPrefix)) {
            return true;
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
cd backend && ./gradlew test --tests RegionSidoCodeTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/domain/model/RegionSidoCode.java \
        backend/src/test/java/com/youthfit/user/domain/model/RegionSidoCodeTest.java
git commit -m "feat(user): RegionSidoCode enum + 정책 regionCode 매칭 로직 추가"
```

## Task 2.2: NotificationSetting에 ElementCollection 두 개 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java`
- Modify: `backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java`

- [ ] **Step 1: Write failing tests** (기존 테스트 파일에 추가)

```java
@Test
void 새_설정의_관심분야는_빈_집합이다() {
    NotificationSetting setting = new NotificationSetting(1L);

    assertThat(setting.getInterestCategories()).isEmpty();
    assertThat(setting.getInterestRegions()).isEmpty();
}

@Test
void replaceInterestCategories는_기존_집합을_대체한다() {
    NotificationSetting setting = new NotificationSetting(1L);

    setting.replaceInterestCategories(java.util.Set.of(
            com.youthfit.policy.domain.model.Category.HOUSING,
            com.youthfit.policy.domain.model.Category.JOBS));

    assertThat(setting.getInterestCategories())
            .containsExactlyInAnyOrder(
                com.youthfit.policy.domain.model.Category.HOUSING,
                com.youthfit.policy.domain.model.Category.JOBS);

    setting.replaceInterestCategories(java.util.Set.of(
            com.youthfit.policy.domain.model.Category.EDUCATION));

    assertThat(setting.getInterestCategories())
            .containsExactly(com.youthfit.policy.domain.model.Category.EDUCATION);
}

@Test
void replaceInterestRegions는_기존_집합을_대체한다() {
    NotificationSetting setting = new NotificationSetting(1L);

    setting.replaceInterestRegions(java.util.Set.of(RegionSidoCode.SEOUL, RegionSidoCode.GYEONGGI));

    assertThat(setting.getInterestRegions())
            .containsExactlyInAnyOrder(RegionSidoCode.SEOUL, RegionSidoCode.GYEONGGI);
}

@Test
void canDispatchRecommendation은_네_조건이_모두_맞아야_true() {
    NotificationSetting setting = new NotificationSetting(1L);
    setting.updateSetting(true, 7, true);
    setting.replaceInterestCategories(java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING));

    EligibilityProfile fullProfile = EligibilityProfile.empty(1L);
    fullProfile.changeLegalDongCode("1111000000");
    fullProfile.changeAge(28);

    EligibilityProfile profileNoLegal = EligibilityProfile.empty(1L);
    profileNoLegal.changeAge(28);

    EligibilityProfile profileNoAge = EligibilityProfile.empty(1L);
    profileNoAge.changeLegalDongCode("1111000000");

    assertThat(setting.canDispatchRecommendation(fullProfile)).isTrue();
    assertThat(setting.canDispatchRecommendation(profileNoLegal)).isFalse();
    assertThat(setting.canDispatchRecommendation(profileNoAge)).isFalse();
}

@Test
void canDispatchRecommendation은_관심분야가_모두_비면_false() {
    NotificationSetting setting = new NotificationSetting(1L);
    setting.updateSetting(true, 7, true);

    EligibilityProfile fullProfile = EligibilityProfile.empty(1L);
    fullProfile.changeLegalDongCode("1111000000");
    fullProfile.changeAge(28);

    assertThat(setting.canDispatchRecommendation(fullProfile)).isFalse();
}

@Test
void canDispatchRecommendation은_토글_off면_false() {
    NotificationSetting setting = new NotificationSetting(1L);
    setting.replaceInterestCategories(java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING));

    EligibilityProfile fullProfile = EligibilityProfile.empty(1L);
    fullProfile.changeLegalDongCode("1111000000");
    fullProfile.changeAge(28);

    assertThat(setting.canDispatchRecommendation(fullProfile)).isFalse();
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd backend && ./gradlew test --tests NotificationSettingTest
```
Expected: FAIL — getter / replace / canDispatch 메서드 모두 미존재.

- [ ] **Step 3: Update NotificationSetting**

`backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java` 전체:
```java
package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import com.youthfit.policy.domain.model.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "days_before_deadline", nullable = false)
    private int daysBeforeDeadline;

    @Column(name = "recommendation_enabled", nullable = false)
    private boolean recommendationEnabled;

    @ElementCollection(targetClass = Category.class, fetch = FetchType.LAZY)
    @CollectionTable(
            name = "notification_interest_category",
            joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Set<Category> interestCategories = new HashSet<>();

    @ElementCollection(targetClass = RegionSidoCode.class, fetch = FetchType.LAZY)
    @CollectionTable(
            name = "notification_interest_region",
            joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sido_code", length = 10, nullable = false)
    private Set<RegionSidoCode> interestRegions = new HashSet<>();

    public NotificationSetting(Long userId) {
        this.userId = userId;
        this.emailEnabled = true;
        this.daysBeforeDeadline = 7;
        this.recommendationEnabled = false;
    }

    public void updateSetting(boolean emailEnabled, int daysBeforeDeadline,
                              boolean recommendationEnabled) {
        this.emailEnabled = emailEnabled;
        this.daysBeforeDeadline = daysBeforeDeadline;
        this.recommendationEnabled = recommendationEnabled;
    }

    public void replaceInterestCategories(Set<Category> categories) {
        this.interestCategories.clear();
        if (categories != null) {
            this.interestCategories.addAll(categories);
        }
    }

    public void replaceInterestRegions(Set<RegionSidoCode> regions) {
        this.interestRegions.clear();
        if (regions != null) {
            this.interestRegions.addAll(regions);
        }
    }

    public boolean canDispatchRecommendation(EligibilityProfile profile) {
        if (!recommendationEnabled) return false;
        if (interestCategories.isEmpty() && interestRegions.isEmpty()) return false;
        if (profile == null) return false;
        if (profile.getLegalDongCode() == null || profile.getAge() == null) return false;
        return true;
    }
}
```

- [ ] **Step 4: Run tests**

```
cd backend && ./gradlew test --tests NotificationSettingTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/domain/model/NotificationSetting.java \
        backend/src/test/java/com/youthfit/user/domain/model/NotificationSettingTest.java
git commit -m "feat(user): NotificationSetting에 관심 카테고리·지역 ElementCollection 추가"
```

## Task 2.3: 관심 분야 마이그레이션 SQL

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-04-notification-interest.sql`

- [ ] **Step 1: Write SQL**

```sql
-- 관심 분야(카테고리·시/도) 저장 테이블
CREATE TABLE notification_interest_category (
    notification_setting_id bigint NOT NULL
        REFERENCES notification_setting(id) ON DELETE CASCADE,
    category varchar(20) NOT NULL,
    PRIMARY KEY (notification_setting_id, category)
);

CREATE TABLE notification_interest_region (
    notification_setting_id bigint NOT NULL
        REFERENCES notification_setting(id) ON DELETE CASCADE,
    sido_code varchar(10) NOT NULL,
    PRIMARY KEY (notification_setting_id, sido_code)
);
```

- [ ] **Step 2: Commit**

```
git add backend/src/main/resources/sql/2026-05-04-notification-interest.sql
git commit -m "chore(user): 관심 분야 테이블 마이그레이션 추가"
```

## Task 2.4: DTO에 interestCategories / interestRegions 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java`
- Modify: `backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java`
- Modify: `backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java`
- Modify: `backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java`

- [ ] **Step 1: UpdateNotificationSettingCommand**

전체 교체:
```java
package com.youthfit.user.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.util.Set;

public record UpdateNotificationSettingCommand(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions
) {
    public UpdateNotificationSettingCommand {
        interestCategories = interestCategories == null ? Set.of() : Set.copyOf(interestCategories);
        interestRegions = interestRegions == null ? Set.of() : Set.copyOf(interestRegions);
    }
}
```

- [ ] **Step 2: NotificationSettingResult**

전체 교체:
```java
package com.youthfit.user.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.time.LocalDateTime;
import java.util.Set;

public record NotificationSettingResult(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isEmailEnabled(),
                setting.getDaysBeforeDeadline(),
                setting.isRecommendationEnabled(),
                Set.copyOf(setting.getInterestCategories()),
                Set.copyOf(setting.getInterestRegions()),
                setting.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 3: UpdateNotificationSettingRequest**

전체 교체:
```java
package com.youthfit.user.presentation.dto.request;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.application.dto.command.UpdateNotificationSettingCommand;
import com.youthfit.user.domain.model.RegionSidoCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Set;

public record UpdateNotificationSettingRequest(
        @NotNull(message = "이메일 알림 수신 여부는 필수입니다")
        Boolean emailEnabled,

        @NotNull(message = "알림 시점(daysBeforeDeadline)은 필수입니다")
        Integer daysBeforeDeadline,

        @NotNull(message = "추천 알림 수신 여부는 필수입니다")
        Boolean recommendationEnabled,

        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions
) {

    @AssertTrue(message = "알림 시점은 3, 7, 14 중 하나여야 합니다")
    public boolean isDaysBeforeDeadlineValid() {
        if (daysBeforeDeadline == null) return true;
        return daysBeforeDeadline == 3 || daysBeforeDeadline == 7 || daysBeforeDeadline == 14;
    }

    @AssertTrue(message = "추천 알림 활성화 시 카테고리 또는 지역을 1개 이상 선택해야 합니다")
    public boolean isInterestNotEmptyWhenRecommendationEnabled() {
        if (recommendationEnabled == null || !recommendationEnabled) return true;
        int totalSize = (interestCategories == null ? 0 : interestCategories.size())
                + (interestRegions == null ? 0 : interestRegions.size());
        return totalSize > 0;
    }

    public UpdateNotificationSettingCommand toCommand() {
        return new UpdateNotificationSettingCommand(
                emailEnabled,
                daysBeforeDeadline,
                recommendationEnabled,
                interestCategories == null ? Collections.emptySet() : interestCategories,
                interestRegions == null ? Collections.emptySet() : interestRegions
        );
    }
}
```

- [ ] **Step 4: NotificationSettingResponse**

전체 교체:
```java
package com.youthfit.user.presentation.dto.response;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.domain.model.RegionSidoCode;

import java.time.LocalDateTime;
import java.util.Set;

public record NotificationSettingResponse(
        boolean emailEnabled,
        int daysBeforeDeadline,
        boolean recommendationEnabled,
        Set<Category> interestCategories,
        Set<RegionSidoCode> interestRegions,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.emailEnabled(),
                result.daysBeforeDeadline(),
                result.recommendationEnabled(),
                result.interestCategories(),
                result.interestRegions(),
                result.updatedAt()
        );
    }
}
```

- [ ] **Step 5: Build to verify**

```
cd backend && ./gradlew compileJava
```
Expected: 컴파일 성공.

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/dto/command/UpdateNotificationSettingCommand.java \
        backend/src/main/java/com/youthfit/user/application/dto/result/NotificationSettingResult.java \
        backend/src/main/java/com/youthfit/user/presentation/dto/request/UpdateNotificationSettingRequest.java \
        backend/src/main/java/com/youthfit/user/presentation/dto/response/NotificationSettingResponse.java
git commit -m "feat(user): 알림 설정 DTO에 관심 카테고리·지역 추가 + 검증 규칙"
```

## Task 2.5: NotificationSettingService에서 관심 분야 저장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java`
- Modify: `backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java`

- [ ] **Step 1: Add failing test** (기존 테스트 파일에 추가)

```java
@Test
void updateNotificationSetting은_관심_카테고리와_지역도_저장한다() {
    NotificationSettingRepository repo = mock(NotificationSettingRepository.class);
    NotificationSetting existing = new NotificationSetting(1L);
    when(repo.findByUserId(1L)).thenReturn(Optional.of(existing));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    NotificationSettingService service = new NotificationSettingService(repo);

    NotificationSettingResult result = service.updateNotificationSetting(
            1L,
            new UpdateNotificationSettingCommand(
                    true, 7, true,
                    java.util.Set.of(com.youthfit.policy.domain.model.Category.HOUSING),
                    java.util.Set.of(com.youthfit.user.domain.model.RegionSidoCode.SEOUL)));

    assertThat(result.interestCategories())
            .containsExactly(com.youthfit.policy.domain.model.Category.HOUSING);
    assertThat(result.interestRegions())
            .containsExactly(com.youthfit.user.domain.model.RegionSidoCode.SEOUL);
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && ./gradlew test --tests NotificationSettingServiceTest
```
Expected: FAIL.

- [ ] **Step 3: Update service**

`updateNotificationSetting` 메서드를 다음으로 교체:
```java
@Transactional
public NotificationSettingResult updateNotificationSetting(Long userId, UpdateNotificationSettingCommand command) {
    NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
            .orElseGet(() -> notificationSettingRepository.save(new NotificationSetting(userId)));
    setting.updateSetting(command.emailEnabled(), command.daysBeforeDeadline(), command.recommendationEnabled());
    setting.replaceInterestCategories(command.interestCategories());
    setting.replaceInterestRegions(command.interestRegions());
    return NotificationSettingResult.from(setting);
}
```

- [ ] **Step 4: Run all tests**

```
cd backend && ./gradlew test
```
Expected: PASS. 전체 빌드도 (`./gradlew build`).

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/service/NotificationSettingService.java \
        backend/src/test/java/com/youthfit/user/application/service/NotificationSettingServiceTest.java
git commit -m "feat(user): 관심 카테고리·지역을 NotificationSetting에 저장"
```

## Task 2.6: 프론트 — 라벨 매핑 추가

**Files:**
- Create: `frontend/src/lib/labels/region.ts`
- Verify/Create: `frontend/src/lib/labels/category.ts`

- [ ] **Step 1: 카테고리 라벨 존재 확인**

```
cd frontend && rg -n "JOBS|HOUSING|EDUCATION" src/lib/labels/ src/types/ | head -20
```

이미 `Category` 라벨 매핑이 있으면 재사용한다. 없으면 다음 Step 2처럼 추가.

- [ ] **Step 2: category.ts (필요 시 추가)**

`frontend/src/lib/labels/category.ts`:
```ts
import type { PolicyCategory } from '@/types/policy';

export const CATEGORY_LABELS: Record<PolicyCategory, string> = {
  JOBS: '일자리',
  HOUSING: '주거',
  EDUCATION: '교육',
  WELFARE: '복지',
  FINANCE: '금융',
  CULTURE: '문화',
  PARTICIPATION: '참여',
};

export const CATEGORY_OPTIONS: PolicyCategory[] = [
  'JOBS', 'HOUSING', 'EDUCATION', 'WELFARE', 'FINANCE', 'CULTURE', 'PARTICIPATION',
];
```

(주의: `PolicyCategory` 타입이 이미 `types/policy.ts`에 정의되어 있는지 확인. 없으면 string union으로 정의 추가.)

- [ ] **Step 3: region.ts**

`frontend/src/lib/labels/region.ts`:
```ts
export type RegionSidoCode =
  | 'SEOUL' | 'BUSAN' | 'DAEGU' | 'INCHEON' | 'GWANGJU'
  | 'DAEJEON' | 'ULSAN' | 'SEJONG' | 'GYEONGGI' | 'GANGWON'
  | 'CHUNGBUK' | 'CHUNGNAM' | 'JEONBUK' | 'JEONNAM' | 'GYEONGBUK'
  | 'GYEONGNAM' | 'JEJU';

export const REGION_LABELS: Record<RegionSidoCode, string> = {
  SEOUL: '서울', BUSAN: '부산', DAEGU: '대구', INCHEON: '인천', GWANGJU: '광주',
  DAEJEON: '대전', ULSAN: '울산', SEJONG: '세종', GYEONGGI: '경기', GANGWON: '강원',
  CHUNGBUK: '충북', CHUNGNAM: '충남', JEONBUK: '전북', JEONNAM: '전남', GYEONGBUK: '경북',
  GYEONGNAM: '경남', JEJU: '제주',
};

export const REGION_OPTIONS: RegionSidoCode[] = [
  'SEOUL', 'BUSAN', 'DAEGU', 'INCHEON', 'GWANGJU',
  'DAEJEON', 'ULSAN', 'SEJONG', 'GYEONGGI', 'GANGWON',
  'CHUNGBUK', 'CHUNGNAM', 'JEONBUK', 'JEONNAM', 'GYEONGBUK',
  'GYEONGNAM', 'JEJU',
];
```

- [ ] **Step 4: 타입체크**

```
cd frontend && npx tsc --noEmit
```
Expected: 통과.

- [ ] **Step 5: Commit**

```
git add frontend/src/lib/labels/region.ts \
        frontend/src/lib/labels/category.ts
git commit -m "feat(frontend): 시/도·카테고리 라벨 매핑 추가"
```

## Task 2.7: 프론트 — types/policy.ts 확장

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: NotificationSettings에 두 필드 추가**

```ts
import type { RegionSidoCode } from '@/lib/labels/region';

export interface NotificationSettings {
  emailEnabled: boolean;
  daysBeforeDeadline: number;
  recommendationEnabled: boolean;
  interestCategories: PolicyCategory[];
  interestRegions: RegionSidoCode[];
}
```

(`PolicyCategory` 타입이 별도 정의되어 있지 않으면 같은 파일에 string union으로 추가.)

- [ ] **Step 2: 타입체크**

```
cd frontend && npx tsc --noEmit
```
Expected: 새 필드를 사용하는 곳이 아직 없으므로 통과. (사용 추가는 Task 2.9~2.10에서)

- [ ] **Step 3: Commit**

```
git add frontend/src/types/policy.ts
git commit -m "feat(frontend): NotificationSettings에 관심 카테고리·지역 필드 추가"
```

## Task 2.8: 프론트 — 칩 컴포넌트 두 개

**Files:**
- Create: `frontend/src/components/notification/InterestCategoryChips.tsx`
- Create: `frontend/src/components/notification/InterestRegionChips.tsx`

- [ ] **Step 1: InterestCategoryChips**

```tsx
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from '@/lib/labels/category';
import type { PolicyCategory } from '@/types/policy';
import { cn } from '@/lib/cn';

interface Props {
  selected: PolicyCategory[];
  onChange: (next: PolicyCategory[]) => void;
}

export default function InterestCategoryChips({ selected, onChange }: Props) {
  const toggle = (c: PolicyCategory) => {
    if (selected.includes(c)) onChange(selected.filter((x) => x !== c));
    else onChange([...selected, c]);
  };

  return (
    <div className="flex flex-wrap gap-2">
      {CATEGORY_OPTIONS.map((c) => {
        const active = selected.includes(c);
        return (
          <button
            key={c}
            type="button"
            onClick={() => toggle(c)}
            aria-pressed={active}
            className={cn(
              'h-9 rounded-full border px-3 text-sm font-medium transition-colors',
              active
                ? 'border-brand-800 bg-brand-100 text-brand-900'
                : 'border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300',
            )}
          >
            {CATEGORY_LABELS[c]}
          </button>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: InterestRegionChips**

```tsx
import { REGION_LABELS, REGION_OPTIONS, type RegionSidoCode } from '@/lib/labels/region';
import { cn } from '@/lib/cn';

interface Props {
  selected: RegionSidoCode[];
  onChange: (next: RegionSidoCode[]) => void;
}

export default function InterestRegionChips({ selected, onChange }: Props) {
  const toggle = (r: RegionSidoCode) => {
    if (selected.includes(r)) onChange(selected.filter((x) => x !== r));
    else onChange([...selected, r]);
  };

  return (
    <div className="flex flex-wrap gap-2">
      {REGION_OPTIONS.map((r) => {
        const active = selected.includes(r);
        return (
          <button
            key={r}
            type="button"
            onClick={() => toggle(r)}
            aria-pressed={active}
            className={cn(
              'h-9 rounded-full border px-3 text-sm font-medium transition-colors',
              active
                ? 'border-brand-800 bg-brand-100 text-brand-900'
                : 'border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300',
            )}
          >
            {REGION_LABELS[r]}
          </button>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 3: 타입체크**

```
cd frontend && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```
git add frontend/src/components/notification/InterestCategoryChips.tsx \
        frontend/src/components/notification/InterestRegionChips.tsx
git commit -m "feat(frontend): 관심 카테고리·지역 칩 컴포넌트 추가"
```

## Task 2.9: 프론트 — RecommendationSection 컴포넌트

**Files:**
- Create: `frontend/src/components/notification/RecommendationSection.tsx`

- [ ] **Step 1: Implement**

```tsx
import { useEffect, useState } from 'react';
import InterestCategoryChips from './InterestCategoryChips';
import InterestRegionChips from './InterestRegionChips';
import type { PolicyCategory } from '@/types/policy';
import type { RegionSidoCode } from '@/lib/labels/region';
import { cn } from '@/lib/cn';

interface Props {
  enabled: boolean;
  categories: PolicyCategory[];
  regions: RegionSidoCode[];
  hasEligibilityProfile: boolean;
  onToggle: (next: boolean) => void;
  onSave: (categories: PolicyCategory[], regions: RegionSidoCode[]) => void;
  saving?: boolean;
}

export default function RecommendationSection({
  enabled, categories, regions, hasEligibilityProfile,
  onToggle, onSave, saving,
}: Props) {
  const [draftCategories, setDraftCategories] = useState<PolicyCategory[]>(categories);
  const [draftRegions, setDraftRegions] = useState<RegionSidoCode[]>(regions);

  useEffect(() => { setDraftCategories(categories); }, [categories]);
  useEffect(() => { setDraftRegions(regions); }, [regions]);

  const dirty =
    JSON.stringify([...draftCategories].sort()) !== JSON.stringify([...categories].sort()) ||
    JSON.stringify([...draftRegions].sort()) !== JSON.stringify([...regions].sort());
  const totalSelected = draftCategories.length + draftRegions.length;
  const canSave = enabled && dirty && totalSelected > 0;

  return (
    <section className="rounded-2xl border border-neutral-100 bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-neutral-900">맞춤 정책 추천</h3>
          <p className="mt-1 text-sm text-neutral-500">
            적합도와 관심 분야로 매주 월요일 추천 메일을 보내드려요.
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          onClick={() => onToggle(!enabled)}
          className={cn(
            'relative h-6 w-11 shrink-0 rounded-full transition-colors',
            enabled ? 'bg-brand-800' : 'bg-neutral-200',
          )}
        >
          <span
            className={cn(
              'absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform',
              enabled && 'translate-x-5',
            )}
          />
        </button>
      </div>

      {enabled && (
        <div className="mt-5 space-y-5">
          {!hasEligibilityProfile && (
            <div className="rounded-xl bg-amber-50 px-4 py-3 text-xs text-amber-900">
              적합도 정보를 먼저 입력하면 더 정확한 추천을 받을 수 있어요.
            </div>
          )}

          <div>
            <p className="mb-2 text-sm font-medium text-neutral-700">관심 카테고리</p>
            <InterestCategoryChips selected={draftCategories} onChange={setDraftCategories} />
          </div>

          <div>
            <p className="mb-2 text-sm font-medium text-neutral-700">관심 지역 (시/도)</p>
            <InterestRegionChips selected={draftRegions} onChange={setDraftRegions} />
          </div>

          {totalSelected === 0 && (
            <p className="text-xs text-error-500">관심 분야를 1개 이상 선택해주세요</p>
          )}

          <button
            type="button"
            disabled={!canSave || saving}
            onClick={() => onSave(draftCategories, draftRegions)}
            className={cn(
              'h-11 w-full rounded-xl text-sm font-semibold transition-colors',
              canSave
                ? 'bg-brand-800 text-white hover:bg-brand-900'
                : 'bg-neutral-100 text-neutral-400 cursor-not-allowed',
            )}
          >
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: 타입체크**

```
cd frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```
git add frontend/src/components/notification/RecommendationSection.tsx
git commit -m "feat(frontend): RecommendationSection 컴포넌트 추가"
```

## Task 2.10: 프론트 — MyPage 알림 탭에 RecommendationSection 통합

**Files:**
- Modify: `frontend/src/pages/MyPage.tsx`

- [ ] **Step 1: 위치 파악**

```
cd frontend && rg -n "맞춤 정책 추천|eligibilityRecommendationEnabled|recommendationEnabled" src/pages/MyPage.tsx
```

`/* 맞춤 정책 추천 */` 주석부터 토글 closing 태그까지가 교체 대상 (디자인 문서 9.2 참조). 라인 번호는 코드 변경에 따라 다르므로 주석 marker로 식별.

- [ ] **Step 2: 교체**

기존 "맞춤 정책 추천" 섹션 전체를 다음 호출로 교체:

```tsx
<RecommendationSection
  enabled={recommendationEnabled}
  categories={interestCategories}
  regions={interestRegions}
  hasEligibilityProfile={Boolean(eligibilityProfile?.legalDongCode && eligibilityProfile?.age)}
  saving={updateNotificationMutation.isPending}
  onToggle={(next) => {
    /* 기존 토글 핸들러 흐름 유지 — 이메일 미등록이면 JIT 시트, 그 외에는 PUT */
    handleRecommendationToggle(next);
  }}
  onSave={(categories, regions) => {
    updateNotificationMutation.mutate({
      emailEnabled,
      daysBeforeDeadline,
      recommendationEnabled,
      interestCategories: categories,
      interestRegions: regions,
    });
    setInterestCategories(categories);
    setInterestRegions(regions);
  }}
/>
```

추가 사항:
- 컴포넌트 import: `import RecommendationSection from '@/components/notification/RecommendationSection';`
- 새 state 두 개:
  ```tsx
  const [interestCategories, setInterestCategories] = useState<PolicyCategory[]>([]);
  const [interestRegions, setInterestRegions] = useState<RegionSidoCode[]>([]);
  ```
- `notificationData` 동기화 useEffect에 두 필드 추가:
  ```tsx
  setInterestCategories(notificationData.interestCategories ?? []);
  setInterestRegions(notificationData.interestRegions ?? []);
  ```
- 적합도 프로필이 별도 hook으로 조회되지 않으면 추가:
  ```tsx
  const { data: eligibilityProfile } = useEligibilityProfile();
  ```
  (이미 있는 hook 재사용. 없으면 `useProfile`로 대체 — 단, profile에는 적합도 정보가 없을 수 있음에 유의.)

기존 토글 핸들러(`handleRecommendationToggle` 등)는 그대로 두되, 토글이 ON으로 바뀔 때 PUT 페이로드에 `interestCategories`/`interestRegions`도 함께 보내도록 수정. ON으로 처음 켤 때는 빈 배열을 보내고, 사용자가 칩 선택 후 [저장]을 누를 때 비로소 채워진 페이로드가 나간다는 흐름. (Note: 백엔드가 토글 ON + 빈 관심 분야를 거부하므로, 토글 ON 첫 PUT은 빈 배열로 보내면 400. 따라서 토글 ON은 *낙관적 UI 상태만* 바꾸고, 실제 PUT은 사용자가 칩을 1개 이상 선택 후 [저장]을 눌렀을 때 발생하도록 흐름을 조정.)

- [ ] **Step 3: 타입체크 + 빌드**

```
cd frontend && npx tsc --noEmit && npm run build
```
Expected: 통과.

- [ ] **Step 4: 수동 테스트**

`npm run dev`로 띄우고:
1. 마이페이지 알림 탭 → 추천 토글 ON
2. 카테고리 1개, 시/도 2개 선택 → [저장] → 토스트 / 새로고침
3. 새로고침 후 같은 선택이 유지되는지 확인
4. 토글 ON 상태에서 모든 칩 해제 → 저장 비활성화 + 안내 메시지 노출 확인

- [ ] **Step 5: Commit**

```
git add frontend/src/pages/MyPage.tsx
git commit -m "feat(frontend): MyPage에 관심 카테고리·지역 입력 섹션 추가"
```

## Task 2.11: Phase 2 PR 생성

- [ ] **Step 1: Branch + push**

```
git checkout -b feat/notification-recommendation-phase2
git push -u origin feat/notification-recommendation-phase2
```

- [ ] **Step 2: PR 생성**

```
gh pr create --title "feat(user): 추천 알림 관심 분야(카테고리·시/도) 입력" \
  --body "$(cat <<'EOF'
## Summary
- `RegionSidoCode` enum 신설 (17개 시/도)
- `NotificationSetting`에 `interestCategories`/`interestRegions` ElementCollection 추가
- API 요청에 `recommendationEnabled` ON일 때 관심 분야 1개 이상 검증
- 프론트: `RecommendationSection` + 칩 컴포넌트 + 라벨 매핑
- 마이페이지에서 카테고리·시/도 다중선택 후 저장 가능

## Test plan
- [ ] 백엔드 단위 테스트 통과
- [ ] 프론트 빌드 통과
- [ ] 마이페이지에서 추천 토글 ON → 카테고리·시/도 선택 → 저장 → 새로고침 후 유지
- [ ] 토글 ON 상태에서 칩 모두 해제 시 저장 버튼 비활성화

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

# Phase 3 — 추천 발송 파이프라인

> **PR 끝 상태**: 매주 월 09:00 스케줄러가 발화하여, 추천 토글 ON + 관심 분야 + 적합도 프로필 + 이메일을 가진 사용자에게 최대 5건의 정책을 이메일(현재는 로깅)로 발송한다.

## Task 3.1: NotificationType enum 신설 + NotificationHistory 매핑

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/domain/model/NotificationType.java`
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java`
- Modify: `backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java`

- [ ] **Step 1: Create enum**

```java
package com.youthfit.user.domain.model;

public enum NotificationType {
    DEADLINE,
    RECOMMENDATION
}
```

- [ ] **Step 2: NotificationHistory 매핑 변경**

`backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java`:
```java
package com.youthfit.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_user_policy_type",
                columnNames = {"user_id", "policy_id", "notification_type"})
})
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private NotificationType notificationType;

    @CreatedDate
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public NotificationHistory(Long userId, Long policyId, NotificationType notificationType) {
        this.userId = userId;
        this.policyId = policyId;
        this.notificationType = notificationType;
    }
}
```

- [ ] **Step 3: NotificationHistoryRepository 시그니처 확인 + 갱신**

```
cd backend && rg -n "NotificationHistoryRepository" backend/src/main/java/
```

Repository 인터페이스의 `existsByUserIdAndPolicyIdAndNotificationType` 시그니처를 `String` → `NotificationType`으로 변경. 구현체도 함께. 컴파일 오류가 길잡이가 됨.

- [ ] **Step 4: NotificationScheduleService 갱신**

`backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java`에서:
- `private static final String NOTIFICATION_TYPE_DEADLINE = "DEADLINE";` 상수 제거
- 기존 `NOTIFICATION_TYPE_DEADLINE` 사용처 → `NotificationType.DEADLINE`

- [ ] **Step 5: Build**

```
cd backend && ./gradlew build
```
Expected: 컴파일 + 기존 테스트 모두 통과.

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/youthfit/user/domain/model/NotificationType.java \
        backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java \
        backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java \
        backend/src/main/java/com/youthfit/user/domain/repository/NotificationHistoryRepository.java \
        backend/src/main/java/com/youthfit/user/infrastructure/persistence/NotificationHistoryRepositoryImpl.java \
        backend/src/main/java/com/youthfit/user/infrastructure/persistence/NotificationHistoryJpaRepository.java
git commit -m "refactor(user): NotificationType enum 도입 + History 컬럼 매핑 변경"
```
(repository 파일 경로는 `rg`로 확인한 실제 경로로 조정.)

## Task 3.2: EmailSender에 sendRecommendationNotification 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/port/EmailSender.java`
- Modify: `backend/src/main/java/com/youthfit/user/infrastructure/email/LoggingEmailSender.java`

- [ ] **Step 1: 포트 확장**

```java
package com.youthfit.user.application.port;

import com.youthfit.policy.domain.model.Policy;

import java.util.List;

public interface EmailSender {

    void sendDeadlineNotification(String recipientEmail, Policy policy);

    void sendRecommendationNotification(String recipientEmail, List<Policy> policies);
}
```

- [ ] **Step 2: LoggingEmailSender 구현**

```java
package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendDeadlineNotification(String recipientEmail, Policy policy) {
        log.info("[이메일 발송][마감] 수신: {}, 정책명: {}, 마감일: {}, 정책 ID: {}",
                recipientEmail, policy.getTitle(), policy.getApplyEnd(), policy.getId());
    }

    @Override
    public void sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        String summary = policies.stream()
                .map(p -> "[" + p.getId() + "] " + p.getTitle() + " (마감 " + p.getApplyEnd() + ")")
                .collect(Collectors.joining(", "));
        log.info("[이메일 발송][추천] 수신: {}, 추천 {}건: {}",
                recipientEmail, policies.size(), summary);
    }
}
```

- [ ] **Step 3: Build**

```
cd backend && ./gradlew build
```
Expected: 통과 (구현 추가만, 사용처 없으므로 빌드 그대로).

- [ ] **Step 4: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/port/EmailSender.java \
        backend/src/main/java/com/youthfit/user/infrastructure/email/LoggingEmailSender.java
git commit -m "feat(user): EmailSender에 sendRecommendationNotification 추가"
```

## Task 3.3: PolicyRecommender 도메인 서비스

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/domain/service/PolicyRecommender.java`
- Test: `backend/src/test/java/com/youthfit/user/domain/service/PolicyRecommenderTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRecommenderTest {

    private final PolicyRecommender recommender = new PolicyRecommender();

    private NotificationSetting setting(Set<Category> categories, Set<RegionSidoCode> regions) {
        NotificationSetting s = new NotificationSetting(1L);
        s.updateSetting(true, 7, true);
        s.replaceInterestCategories(categories);
        s.replaceInterestRegions(regions);
        return s;
    }

    @Test
    void 카테고리와_지역_매칭_정책만_추출() {
        Policy housingSeoul = policy(1L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(10));
        Policy jobsBusan = policy(2L, Category.JOBS, "BUSAN", LocalDate.now().plusDays(5));
        Policy housingBusan = policy(3L, Category.HOUSING, "BUSAN", LocalDate.now().plusDays(7));

        NotificationSetting s = setting(Set.of(Category.HOUSING), Set.of(RegionSidoCode.SEOUL));

        List<Policy> result = recommender.filterByInterest(s, List.of(housingSeoul, jobsBusan, housingBusan));

        assertThat(result).extracting(Policy::getId).containsExactly(1L);
    }

    @Test
    void 카테고리_비어있으면_전체_카테고리_허용() {
        Policy a = policy(1L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(10));
        Policy b = policy(2L, Category.JOBS, "SEOUL", LocalDate.now().plusDays(5));

        NotificationSetting s = setting(Set.of(), Set.of(RegionSidoCode.SEOUL));

        List<Policy> result = recommender.filterByInterest(s, List.of(a, b));

        assertThat(result).extracting(Policy::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 지역_비어있으면_전체_지역_허용() {
        Policy a = policy(1L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(10));
        Policy b = policy(2L, Category.HOUSING, "BUSAN", LocalDate.now().plusDays(5));

        NotificationSetting s = setting(Set.of(Category.HOUSING), Set.of());

        List<Policy> result = recommender.filterByInterest(s, List.of(a, b));

        assertThat(result).extracting(Policy::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void NATIONAL_정책은_어떤_시도_선택에도_매칭() {
        Policy national = policy(1L, Category.HOUSING, null, LocalDate.now().plusDays(10));
        NotificationSetting s = setting(Set.of(Category.HOUSING), Set.of(RegionSidoCode.SEOUL));

        List<Policy> result = recommender.filterByInterest(s, List.of(national));

        assertThat(result).extracting(Policy::getId).containsExactly(1L);
    }

    @Test
    void 마감_임박순으로_정렬되고_5건으로_절단() {
        List<Policy> many = List.of(
                policy(1L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(20)),
                policy(2L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(3)),
                policy(3L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(15)),
                policy(4L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(7)),
                policy(5L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(1)),
                policy(6L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(30)),
                policy(7L, Category.HOUSING, "SEOUL", LocalDate.now().plusDays(2))
        );

        List<Policy> result = recommender.sortAndLimit(many);

        assertThat(result).extracting(Policy::getId).containsExactly(5L, 7L, 2L, 4L, 3L);
    }

    private Policy policy(Long id, Category category, String regionCode, LocalDate applyEnd) {
        // 실제 빌더 시그니처에 맞춰 구성. Policy 가 reflection-friendly factory 가 없으면
        // 테스트 헬퍼나 ReflectionTestUtils 로 ID/필드 주입.
        // 프로젝트의 Policy 생성 헬퍼/팩토리 메서드를 사용하라.
        return TestPolicies.create(id, category, regionCode, PolicyStatus.OPEN, applyEnd);
    }
}
```

> 주의: `Policy` 엔티티 생성자가 보호 수준이 protected이므로, 테스트용 팩토리/헬퍼(`TestPolicies`)를 한 번만 만들어 재사용하는 것이 깔끔하다. 또는 `@SpringBootTest`로 띄워 실제 저장 후 조회. 단위 테스트 우선, 그 다음 결정.

- [ ] **Step 2: Run tests to verify they fail**

```
cd backend && ./gradlew test --tests PolicyRecommenderTest
```
Expected: FAIL — class 미존재.

- [ ] **Step 3: Implement PolicyRecommender**

```java
package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class PolicyRecommender {

    private static final int MAX_PICKS = 5;

    public List<Policy> filterByInterest(NotificationSetting setting, List<Policy> candidates) {
        Set<Category> categories = setting.getInterestCategories();
        Set<RegionSidoCode> regions = setting.getInterestRegions();

        return candidates.stream()
                .filter(p -> categories.isEmpty() || categories.contains(p.getCategory()))
                .filter(p -> regions.isEmpty() || regions.stream().anyMatch(r -> r.matches(p.getRegionCode())))
                .toList();
    }

    public List<Policy> sortAndLimit(List<Policy> policies) {
        return policies.stream()
                .sorted(Comparator
                        .comparing(Policy::getApplyEnd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Policy::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PICKS)
                .toList();
    }
}
```

- [ ] **Step 4: Run tests**

```
cd backend && ./gradlew test --tests PolicyRecommenderTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/domain/service/PolicyRecommender.java \
        backend/src/test/java/com/youthfit/user/domain/service/PolicyRecommenderTest.java
git commit -m "feat(user): PolicyRecommender 도메인 서비스 추가"
```

## Task 3.4: RecommendationDispatchService

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/application/service/RecommendationDispatchService.java`
- Test: `backend/src/test/java/com/youthfit/user/application/service/RecommendationDispatchServiceTest.java`

- [ ] **Step 1: 후보 조회 메서드를 PolicyRepository에 추가 (필요 시)**

`PolicyRepository`에 `List<Policy> findOpenPolicies()` 또는 동등 메서드가 이미 있는지 확인:
```
cd backend && rg -n "findOpenPolicies|findAllByStatus|findByStatus" backend/src/main/java/com/youthfit/policy/
```
없으면 추가:
```java
List<Policy> findAllByStatus(PolicyStatus status);
```
(실제 시그니처는 기존 코드 컨벤션에 맞춤.)

- [ ] **Step 2: Write failing test (Mockist)**

`RecommendationDispatchServiceTest.java`:
```java
package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.model.*;
import com.youthfit.user.domain.repository.*;
import com.youthfit.user.domain.service.PolicyRecommender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationDispatchServiceTest {

    @Test
    void 토글_off_사용자는_건너뛴다() {
        Mocks m = Mocks.create();
        NotificationSetting off = settingWithToggle(false);
        when(m.settingRepo.findAllByRecommendationEnabled(true)).thenReturn(List.of(off));

        m.service().dispatchWeekly();

        verifyNoInteractions(m.emailSender);
    }

    @Test
    void 이메일이_없으면_건너뛴다() {
        Mocks m = Mocks.create();
        NotificationSetting on = settingWithToggle(true);
        when(m.settingRepo.findAllByRecommendationEnabled(true)).thenReturn(List.of(on));
        when(m.userRepo.findById(on.getUserId())).thenReturn(Optional.of(userWithoutEmail()));

        m.service().dispatchWeekly();

        verifyNoInteractions(m.emailSender);
    }

    @Test
    void 적합도_프로필_미입력자는_건너뛴다() {
        Mocks m = Mocks.create();
        NotificationSetting on = fullSetting();
        when(m.settingRepo.findAllByRecommendationEnabled(true)).thenReturn(List.of(on));
        when(m.userRepo.findById(on.getUserId())).thenReturn(Optional.of(userWithEmail()));
        when(m.profileRepo.findByUserId(on.getUserId())).thenReturn(Optional.empty());

        m.service().dispatchWeekly();

        verifyNoInteractions(m.emailSender);
    }

    @Test
    void 빈_추천이면_메일_미발송() {
        Mocks m = Mocks.create();
        NotificationSetting on = fullSetting();
        EligibilityProfile profile = fullProfile();
        when(m.settingRepo.findAllByRecommendationEnabled(true)).thenReturn(List.of(on));
        when(m.userRepo.findById(on.getUserId())).thenReturn(Optional.of(userWithEmail()));
        when(m.profileRepo.findByUserId(on.getUserId())).thenReturn(Optional.of(profile));
        when(m.policyRepo.findAllByStatus(PolicyStatus.OPEN)).thenReturn(List.of());

        m.service().dispatchWeekly();

        verifyNoInteractions(m.emailSender);
    }

    @Test
    void 매칭된_정책_중_LIKELY_ELIGIBLE만_발송_그리고_이력기록() {
        Mocks m = Mocks.create();
        NotificationSetting on = fullSetting();
        EligibilityProfile profile = fullProfile();

        Policy matched = TestPolicies.create(10L, Category.HOUSING, "SEOUL", PolicyStatus.OPEN, LocalDate.now().plusDays(7));
        Policy uncertain = TestPolicies.create(11L, Category.HOUSING, "SEOUL", PolicyStatus.OPEN, LocalDate.now().plusDays(3));

        when(m.settingRepo.findAllByRecommendationEnabled(true)).thenReturn(List.of(on));
        when(m.userRepo.findById(on.getUserId())).thenReturn(Optional.of(userWithEmail()));
        when(m.profileRepo.findByUserId(on.getUserId())).thenReturn(Optional.of(profile));
        when(m.policyRepo.findAllByStatus(PolicyStatus.OPEN)).thenReturn(List.of(matched, uncertain));
        when(m.bookmarkRepo.existsByUserIdAndPolicyId(anyLong(), anyLong())).thenReturn(false);
        when(m.historyRepo.existsByUserIdAndPolicyIdAndNotificationType(anyLong(), anyLong(), eq(NotificationType.RECOMMENDATION)))
                .thenReturn(false);

        when(m.eligibility.judgeEligibility(eq(on.getUserId()), eq(new JudgeEligibilityCommand(10L))))
                .thenReturn(judgment(10L, EligibilityResult.LIKELY_ELIGIBLE));
        when(m.eligibility.judgeEligibility(eq(on.getUserId()), eq(new JudgeEligibilityCommand(11L))))
                .thenReturn(judgment(11L, EligibilityResult.UNCERTAIN));

        m.service().dispatchWeekly();

        ArgumentCaptor<List<Policy>> captor = ArgumentCaptor.forClass(List.class);
        verify(m.emailSender).sendRecommendationNotification(eq("u@example.com"), captor.capture());
        assertThat(captor.getValue()).extracting(Policy::getId).containsExactly(10L);

        verify(m.historyRepo).save(argThat(h ->
                h.getPolicyId() == 10L && h.getNotificationType() == NotificationType.RECOMMENDATION));
    }

    @Test
    void 북마크된_정책은_제외() { /* 위 패턴과 동일, bookmarkRepo.exists -> true */ }

    @Test
    void 이미_추천한_정책은_제외() { /* historyRepo.exists -> true */ }

    @Test
    void 한_사용자_실패가_다른_사용자_발송을_막지_않는다() { /* 첫 사용자 throw, 두 번째 정상 발송 검증 */ }

    /* ---- helpers ---- */
    private NotificationSetting settingWithToggle(boolean on) {
        NotificationSetting s = new NotificationSetting(1L);
        s.updateSetting(true, 7, on);
        if (on) s.replaceInterestCategories(Set.of(Category.HOUSING));
        return s;
    }

    private NotificationSetting fullSetting() {
        NotificationSetting s = new NotificationSetting(1L);
        s.updateSetting(true, 7, true);
        s.replaceInterestCategories(Set.of(Category.HOUSING));
        s.replaceInterestRegions(Set.of(RegionSidoCode.SEOUL));
        return s;
    }

    private EligibilityProfile fullProfile() {
        EligibilityProfile p = EligibilityProfile.empty(1L);
        p.changeLegalDongCode("1111000000");
        p.changeAge(28);
        return p;
    }

    // User 엔티티의 빌더/팩토리는 코드베이스 컨벤션에 따라 다름.
    // 직접 조회되는 도메인이 protected 생성자만 노출한다면 ReflectionTestUtils 사용.
    // 예: org.springframework.test.util.ReflectionTestUtils.setField(user, "email", "u@example.com");
    private User userWithEmail() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("u@example.com");
        return user;
    }

    private User userWithoutEmail() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn(null);
        return user;
    }

    private EligibilityJudgmentResult judgment(Long policyId, EligibilityResult overall) {
        // EligibilityJudgmentResult 시그니처에 맞춰 채움. 실제 record 필드 순서는
        // backend/src/main/java/com/youthfit/eligibility/application/dto/result/EligibilityJudgmentResult.java
        // 참조하여 작성.
        return new EligibilityJudgmentResult(
                policyId, "title", overall.name(),
                null, null, EligibilityJudgmentResult.DISCLAIMER_TEXT);
    }

    /* Mocks holder */
    static class Mocks {
        NotificationSettingRepository settingRepo;
        UserRepository userRepo;
        EligibilityProfileRepository profileRepo;
        PolicyRepository policyRepo;
        BookmarkRepository bookmarkRepo;
        NotificationHistoryRepository historyRepo;
        EligibilityService eligibility;
        EmailSender emailSender;
        PolicyRecommender recommender;

        static Mocks create() {
            Mocks m = new Mocks();
            m.settingRepo = mock(NotificationSettingRepository.class);
            m.userRepo = mock(UserRepository.class);
            m.profileRepo = mock(EligibilityProfileRepository.class);
            m.policyRepo = mock(PolicyRepository.class);
            m.bookmarkRepo = mock(BookmarkRepository.class);
            m.historyRepo = mock(NotificationHistoryRepository.class);
            m.eligibility = mock(EligibilityService.class);
            m.emailSender = mock(EmailSender.class);
            m.recommender = new PolicyRecommender();
            return m;
        }

        RecommendationDispatchService service() {
            return new RecommendationDispatchService(
                    settingRepo, userRepo, profileRepo, policyRepo, bookmarkRepo,
                    historyRepo, eligibility, emailSender, recommender);
        }
    }
}
```

> helper 메서드는 실제 도메인 객체 빌더에 맞춰 채운다. `User` 엔티티에 setter가 없으면 ReflectionTestUtils 또는 테스트 전용 팩토리.

- [ ] **Step 3: Run tests to verify they fail**

```
cd backend && ./gradlew test --tests RecommendationDispatchServiceTest
```
Expected: FAIL — class 미존재.

- [ ] **Step 4: BookmarkRepository에 existsByUserIdAndPolicyId 메서드 확인**

```
cd backend && rg -n "existsByUserIdAndPolicyId" backend/src/main/java/com/youthfit/user/domain/repository/BookmarkRepository.java
```
없으면 인터페이스에 메서드 추가 + JPA 구현체에서 derived query.

- [ ] **Step 5: PolicyRepository.findAllByStatus 추가 (확인)**

Task 3.4 Step 1 결과 그대로.

- [ ] **Step 6: Implement RecommendationDispatchService**

```java
package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.model.*;
import com.youthfit.user.domain.repository.*;
import com.youthfit.user.domain.service.PolicyRecommender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDispatchService {

    private final NotificationSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final EligibilityProfileRepository profileRepository;
    private final PolicyRepository policyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationHistoryRepository historyRepository;
    private final EligibilityService eligibilityService;
    private final EmailSender emailSender;
    private final PolicyRecommender recommender;

    public void dispatchWeekly() {
        List<NotificationSetting> settings = settingRepository.findAllByRecommendationEnabled(true);

        for (NotificationSetting setting : settings) {
            try {
                dispatchOne(setting);
            } catch (Exception e) {
                log.error("추천 발송 실패 userId={}", setting.getUserId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(NotificationSetting setting) {
        Long userId = setting.getUserId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

        EligibilityProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (!setting.canDispatchRecommendation(profile)) return;

        List<Policy> openPolicies = policyRepository.findAllByStatus(PolicyStatus.OPEN);
        List<Policy> matched = recommender.filterByInterest(setting, openPolicies);

        List<Policy> notSeen = matched.stream()
                .filter(p -> !bookmarkRepository.existsByUserIdAndPolicyId(userId, p.getId()))
                .filter(p -> !historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                        userId, p.getId(), NotificationType.RECOMMENDATION))
                .toList();

        List<Policy> eligible = notSeen.stream()
                .filter(p -> {
                    EligibilityJudgmentResult r = eligibilityService.judgeEligibility(
                            userId, new JudgeEligibilityCommand(p.getId()));
                    return EligibilityResult.LIKELY_ELIGIBLE.name().equals(r.overall());
                })
                .toList();

        List<Policy> picks = recommender.sortAndLimit(eligible);
        if (picks.isEmpty()) return;

        emailSender.sendRecommendationNotification(user.getEmail(), picks);

        for (Policy p : picks) {
            historyRepository.save(new NotificationHistory(userId, p.getId(), NotificationType.RECOMMENDATION));
        }
    }
}
```

> Note: `dispatchOne`에 `REQUIRES_NEW`를 두지만 `dispatchWeekly`가 같은 클래스 내부 메서드를 호출하므로 Spring AOP self-invocation 함정에 걸린다. 두 가지 해결책:
> 1. `dispatchOne`을 별도 빈으로 분리 (`RecommendationOneDispatcher`).
> 2. `ApplicationContext`에서 자기 빈을 다시 가져와 호출 (`self.dispatchOne(setting)`).
>
> 권장: **별도 빈 분리**. 위 코드는 한 클래스에 두 메서드가 있는 형태이므로, 구현 시 별도 클래스로 옮기고 `RecommendationDispatchService`가 그 빈을 주입받아 호출하도록 리팩터링한다. 테스트에서는 `dispatchOne`을 직접 호출해도 동작.

위 권장에 따라 다음과 같이 분리한다:

```java
// RecommendationDispatchService.java — 외부 진입점, 트랜잭션 없음
@Slf4j @Service @RequiredArgsConstructor
public class RecommendationDispatchService {
    private final NotificationSettingRepository settingRepository;
    private final RecommendationOneDispatcher one;

    public void dispatchWeekly() {
        for (NotificationSetting s : settingRepository.findAllByRecommendationEnabled(true)) {
            try { one.dispatchOne(s); }
            catch (Exception e) { log.error("추천 발송 실패 userId={}", s.getUserId(), e); }
        }
    }
}

// RecommendationOneDispatcher.java — 사용자별 트랜잭션
@Service @RequiredArgsConstructor
public class RecommendationOneDispatcher {
    private final UserRepository userRepository;
    private final EligibilityProfileRepository profileRepository;
    private final PolicyRepository policyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationHistoryRepository historyRepository;
    private final EligibilityService eligibilityService;
    private final EmailSender emailSender;
    private final PolicyRecommender recommender;

    @Transactional
    public void dispatchOne(NotificationSetting setting) { /* 위 코드와 동일 */ }
}
```

테스트는 `RecommendationOneDispatcher`를 별도로 작성하거나, `RecommendationDispatchServiceTest`에서 `one`을 mock 처리하여 분기 검증.

- [ ] **Step 7: Run tests**

```
cd backend && ./gradlew test
```
Expected: PASS.

- [ ] **Step 8: Commit**

```
git add backend/src/main/java/com/youthfit/user/application/service/RecommendationDispatchService.java \
        backend/src/main/java/com/youthfit/user/application/service/RecommendationOneDispatcher.java \
        backend/src/test/java/com/youthfit/user/application/service/RecommendationDispatchServiceTest.java \
        backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
        backend/src/main/java/com/youthfit/user/domain/repository/BookmarkRepository.java
git commit -m "feat(user): RecommendationDispatchService + 사용자 단위 트랜잭션 분리"
```
(repository 변경 파일은 실제 변경 여부에 따라 가감.)

## Task 3.5: RecommendationScheduler

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/infrastructure/scheduler/RecommendationScheduler.java`

- [ ] **Step 1: 스케줄링 활성화 확인**

```
cd backend && rg -n "@EnableScheduling" backend/src/main/java/com/youthfit/
```
이미 `SchedulingConfig` 또는 application 클래스에 `@EnableScheduling`이 있다면 그대로 사용. 없으면 `SchedulingConfig`에 추가.

- [ ] **Step 2: Implement scheduler**

```java
package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.application.service.RecommendationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationDispatchService dispatchService;

    @Scheduled(cron = "0 0 9 ? * MON")
    public void sendWeekly() {
        log.info("주간 추천 알림 스케줄러 실행");
        dispatchService.dispatchWeekly();
        log.info("주간 추천 알림 스케줄러 종료");
    }
}
```

- [ ] **Step 3: Build**

```
cd backend && ./gradlew build
```

- [ ] **Step 4: 수동 트리거 테스트 (선택)**

스케줄러를 1주 기다릴 수 없으므로, 임시로 cron을 `0 */1 * * * *`(매분)로 바꿔 dev 환경에서 발화 확인 후 원복하거나, `RecommendationDispatchService.dispatchWeekly()`를 호출하는 임시 admin 엔드포인트를 만들어 검증. v0에서는 단위 테스트로 충분.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/youthfit/user/infrastructure/scheduler/RecommendationScheduler.java
git commit -m "feat(user): 주간 추천 알림 스케줄러 추가"
```

## Task 3.6: Phase 3 PR 생성

- [ ] **Step 1: Branch + push**

```
git checkout -b feat/notification-recommendation-phase3
git push -u origin feat/notification-recommendation-phase3
```

- [ ] **Step 2: PR 생성**

```
gh pr create --title "feat(user): 주간 맞춤 정책 추천 알림 발송" \
  --body "$(cat <<'EOF'
## Summary
- `NotificationType` enum 도입, `NotificationHistory` 매핑 갱신
- `EmailSender.sendRecommendationNotification` 추가
- `PolicyRecommender` 도메인 서비스 (관심분야 필터 + 정렬·5건 제한)
- `RecommendationDispatchService` + `RecommendationOneDispatcher` (사용자 단위 트랜잭션)
- `RecommendationScheduler` (매주 월 09:00)

## Test plan
- [ ] 백엔드 단위 테스트 통과
- [ ] 토글 OFF / 이메일 미등록 / 프로필 미입력 / 빈 추천 / 북마크·이력 제외 / LIKELY_ELIGIBLE만 통과 시나리오 검증
- [ ] 한 사용자 실패가 다른 사용자 발송을 막지 않는 시나리오 검증
- [ ] 로컬에서 dispatchWeekly() 직접 호출하여 LoggingEmailSender 출력 확인

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

# 마무리 체크

- [ ] 3개 PR 모두 main에 머지된 후, 본 plan 파일과 spec 파일을 `DONE_` 접두사로 리네임:
  ```
  git mv docs/superpowers/specs/2026-05-04-notification-recommendation-design.md \
         docs/superpowers/specs/DONE_2026-05-04-notification-recommendation-design.md
  git mv docs/superpowers/plans/2026-05-04-notification-recommendation.md \
         docs/superpowers/plans/DONE_2026-05-04-notification-recommendation.md
  git commit -m "docs: 알림 추천 spec/plan 완료 표시 (DONE_ 접두사)"
  ```

- [ ] 운영 메모: 실제 SMTP 발송으로 전환할 때는 `LoggingEmailSender` 대신 SES/SMTP 구현체로 교체. 분산 인스턴스 운영 시 `@SchedulerLock` 또는 ShedLock 도입 검토 (별도 작업).
