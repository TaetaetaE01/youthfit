# 알림 설정 — 마감 알림 정합 회복 + 맞춤 정책 추천 알림

작성일: 2026-05-04
관련 PRD: F-09 이메일 알림

## 1. 배경

PRD F-09 "이메일 알림"은 두 가지 알림을 정의한다.

1. 북마크/구독한 정책의 마감 N일 전 이메일 알림 — *이미 구현 완료*
2. 적합도 기반 맞춤 정책 추천 알림 — *백엔드 미구현*

현재 상태를 확인한 결과 다음 두 가지 문제를 발견했다.

- **정합 깨짐**: 프론트 (`MyPage.tsx`, `NotificationPromptSheet.tsx`)는 이미 `eligibilityRecommendationEnabled` 필드를 `PUT /api/v1/notifications/settings` 요청에 포함시키고, 토글 UI도 마련되어 있다. 그러나 백엔드 `UpdateNotificationSettingRequest` / `NotificationSetting` / `NotificationSettingResponse`에는 해당 필드가 없어, 사용자가 토글을 켜도 저장되지 않고 다시 조회 시 사라진다.
- **관심 분야 미반영**: 사용자는 "적합한 정책"뿐 아니라 "관심 있어 하는" 분야(카테고리·지역)를 직접 지정하기를 원한다. 현재 모델에는 관심 분야 데이터가 없다.

본 문서는 (1) 정합 회복, (2) 관심 분야 입력 도입, (3) 추천 알림 발송 파이프라인의 설계를 정의한다.

## 2. 결정 요약

| 결정 항목 | 선택 | 근거 |
|----------|------|------|
| 관심 분야 차원 | 카테고리 + 시/도 다중선택 | 정책 매칭 정확도와 UX 단순성의 균형. 자유 키워드는 v0 YAGNI. |
| 지역 입력 출처 | 적합도 프로필과 별개의 "관심 지역" 다중선택 | 거주지와 관심 지역은 다를 수 있고, 적합도 프로필 미입력자도 관심 분야는 지정 가능. |
| 추천 알고리즘 | 단순 룰 필터링 | v0 데이터 규모에서 가장 투명하고 디버깅 쉬움. 점수화는 v1로. |
| 발송 주기 | 주 1회 (월요일 09:00) | "주간 추천" 멘탈 모델, 이메일 피로감 최소. ingestion 일배치성과 정합. |
| 회당 발송 건수 상한 | 5건 | PRD 그대로. 마감 임박 → 신규 등록 순으로 절단. |
| 데이터 모델 | `NotificationSetting`에 `@ElementCollection` 두 개 + 토글 추가 | 카디널리티 낮아(7+17) join 부담 미미. JPA 자연스러움. |
| 적합도 프로필 미입력 시 | 발송 제외 (필수: `legalDongCode`, `age`) | 부정확한 추천보다 미발송이 신뢰 손실 적음. PRD 일치. |
| 이메일 미등록 시 | 토글 활성화 시 JIT 시트 (이미 구현, 재사용) | 조용히 실패 방지. 마감 알림과 일관. |
| 관심 지역 단위 | 시/도(SIDO)만 | 정책 `regionCode` 정합도와 후보 수를 고려. 시군구는 v1. |

## 3. 모듈 경계 및 의존 방향

- 변경은 모두 `user` 모듈 내부.
- `user.application.service.RecommendationDispatchService` (신설) → `eligibility.application.service.EligibilityService.judgeEligibility(userId, policyId)` 호출.
- `user.application.service.RecommendationDispatchService` → `policy.domain.repository.PolicyRepository`로 후보 조회.
- 의존 방향은 `user.application` → 다른 모듈의 `application`/`domain`, 기존 `NotificationScheduleService`와 동일 패턴.

## 4. 작업 단계 (PR 분리)

각 단계는 그 자체로 빌드/배포 가능하도록 분리한다.

### 단계 1 — 백엔드 정합 회복 + 프론트 키 통일
- `NotificationSetting`에 `recommendationEnabled boolean` 추가 (default false).
- `UpdateNotificationSettingRequest`/`UpdateNotificationSettingCommand`/`NotificationSettingResult`/`NotificationSettingResponse`에 동일 필드 추가.
- Flyway: `notification_setting.recommendation_enabled boolean not null default false`.
- 프론트 `NotificationSettings` 타입과 mutation payload의 키 `eligibilityRecommendationEnabled` → `recommendationEnabled`로 리네임. 사용처(`MyPage.tsx`, `NotificationPromptSheet.tsx`, `useNotificationSettings`/`useUpdateNotificationSettings` 등) 일괄 수정.
- 효과: 프론트가 보내고 있던 토글 값이 이제 정상 저장된다. 발송 로직은 아직 없으므로 토글이 켜져도 메일은 안 나간다.

### 단계 2 — 관심 분야 모델 + API + UI
- `RegionSidoCode` enum 신설 (17개 시/도, `displayName`, `legalDongPrefix`, 정책 `regionCode` 매칭 메서드 포함).
- `NotificationSetting`에 `Set<Category> interestCategories`, `Set<RegionSidoCode> interestRegions` 추가 (`@ElementCollection`).
- API 요청/응답에 두 필드 추가 + 검증 규칙 (`recommendationEnabled == true`이면 둘 중 하나 이상은 비어있지 않아야 함).
- Flyway: `notification_interest_category`, `notification_interest_region` 테이블 생성.
- 프론트:
  - `InterestCategoryChips`, `InterestRegionChips`, `RecommendationSection` 컴포넌트 추가.
  - `MyPage` 알림 탭의 추천 토글 섹션 확장.
  - 라벨 매핑 (`lib/labels/region.ts` 신규).

### 단계 3 — 추천 발송 파이프라인
- `NotificationType` enum 도입 (`DEADLINE`, `RECOMMENDATION`). `NotificationHistory.type` 컬럼 enum 매핑.
- `NotificationHistory` 유니크 제약 점검: `(user_id, policy_id, type)`.
- `EmailSender`에 `sendRecommendationNotification(String to, List<Policy> picks)` 추가.
- `RecommendationDispatchService` (신규) — 5번 알고리즘.
- `RecommendationScheduler` (신규) — 매주 월요일 09:00.
- 추천 이메일 템플릿 추가.

## 5. 데이터 모델

### 5.1 `NotificationSetting` (변경)

```java
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {
    @Id @GeneratedValue private Long id;
    @Column(unique = true) private Long userId;

    private boolean emailEnabled;            // 마감 알림 토글 (기존)
    private int daysBeforeDeadline;          // 3 / 7 / 14 (기존)
    private boolean recommendationEnabled;   // 신규, 기본 false

    @ElementCollection(targetClass = Category.class)
    @CollectionTable(
        name = "notification_interest_category",
        joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Set<Category> interestCategories = new HashSet<>();

    @ElementCollection(targetClass = RegionSidoCode.class)
    @CollectionTable(
        name = "notification_interest_region",
        joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sido_code", length = 10, nullable = false)
    private Set<RegionSidoCode> interestRegions = new HashSet<>();
}
```

도메인 메서드:
- `enableRecommendation()`, `disableRecommendation()`
- `replaceInterestCategories(Set<Category>)`, `replaceInterestRegions(Set<RegionSidoCode>)`
- `boolean canDispatchRecommendation(EligibilityProfile profile)` — 토글 ON + 카테고리·지역 중 1개 이상 + `profile.legalDongCode != null` + `profile.age != null`

### 5.2 `RegionSidoCode` enum (신설)

```java
public enum RegionSidoCode implements LabeledEnum {
    SEOUL("서울", "11"),
    BUSAN("부산", "26"),
    // ... 17개 시/도
    NATIONAL("전국", null);  // 정책 regionCode가 null/NATIONAL인 경우 매칭용 sentinel

    private final String displayName;
    private final String legalDongPrefix;

    public boolean matches(String policyRegionCode) {
        if (policyRegionCode == null || "NATIONAL".equals(policyRegionCode)) return true;
        if (this.name().equals(policyRegionCode)) return true;
        if (legalDongPrefix != null && policyRegionCode.startsWith(legalDongPrefix)) return true;
        return false;
    }
}
```

위치는 `user.domain.model` (관심 지역이 user 도메인 개념). 정책 매칭 형식이 영문(`"SEOUL"`)이든 법정동 prefix(`"11"`)든 enum 안에서 흡수.

### 5.3 `NotificationHistory` (변경)

- `type String` → `type NotificationType` (enum 매핑).
- 유니크 제약 `(user_id, policy_id, type)` — 이미 존재하지 않으면 Flyway로 추가.
- 기존 row의 `"DEADLINE"` 문자열은 `NotificationType.DEADLINE`과 매핑되어 호환.

### 5.4 Flyway 마이그레이션

```sql
-- V_x__notification_recommendation.sql
ALTER TABLE notification_setting
    ADD COLUMN recommendation_enabled boolean NOT NULL DEFAULT false;

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

ALTER TABLE notification_history
    ADD CONSTRAINT uk_notification_history_user_policy_type
    UNIQUE (user_id, policy_id, type);
```

## 6. API 명세

### 6.1 `PUT /api/v1/notifications/settings`

요청:
```json
{
  "emailEnabled": true,
  "daysBeforeDeadline": 7,
  "recommendationEnabled": true,
  "interestCategories": ["HOUSING", "JOBS"],
  "interestRegions": ["SEOUL", "GYEONGGI"]
}
```

검증:
- `daysBeforeDeadline ∈ {3, 7, 14}` (기존)
- `interestCategories`, `interestRegions`의 각 원소는 enum의 valid 값
- `recommendationEnabled == true`이면 `interestCategories.size() + interestRegions.size() >= 1`. 둘 다 비어있으면 400 (`YF-001`).
- 이메일 미등록 사용자 처리는 프론트 JIT가 1차, 백엔드 발송 시점 스킵이 2차 안전망. PUT 자체는 막지 않음.

응답 (200):
```json
{
  "success": true,
  "data": {
    "emailEnabled": true,
    "daysBeforeDeadline": 7,
    "recommendationEnabled": true,
    "interestCategories": ["HOUSING", "JOBS"],
    "interestRegions": ["SEOUL", "GYEONGGI"],
    "updatedAt": "2026-05-04T14:00:00"
  }
}
```

### 6.2 `GET /api/v1/notifications/settings`

스키마 동일. 첫 조회 시 기본값(`emailEnabled=true, daysBeforeDeadline=7, recommendationEnabled=false, interestCategories=[], interestRegions=[]`)으로 생성 후 반환.

### 6.3 메타 엔드포인트

추가하지 않는다. 카테고리(7), 시/도(17)의 라벨은 프론트에 매핑한다(다른 enum과 같은 패턴).

## 7. 추천 산출 알고리즘

### 7.1 의사코드

```
List<Policy> recommend(NotificationSetting s, EligibilityProfile p, LocalDate today):
    if !s.recommendationEnabled: return []
    if user.email is blank: return []
    if p.legalDongCode == null || p.age == null: return []
    if s.interestCategories.isEmpty() && s.interestRegions.isEmpty(): return []

    candidates = policyRepository.findCandidates(
        statuses = [OPEN],
        categories = s.interestCategories  // 비어있으면 전체 카테고리 허용
    )

    candidates = candidates.filter(p ->
        s.interestRegions.isEmpty() ||
        s.interestRegions.anyMatch(region -> region.matches(p.regionCode))
    )

    candidates = candidates.filter(p ->
        !bookmarkRepository.exists(userId, p.id) &&
        !historyRepository.exists(userId, p.id, RECOMMENDATION)
    )

    eligible = candidates.filter(p ->
        eligibilityService.judgeEligibility(userId, p.id).overall == LIKELY_ELIGIBLE
    )

    return eligible
        .sortedBy(applyEnd asc, then createdAt desc)
        .limit(5)
```

### 7.2 핵심 규칙

- **카테고리 또는 지역 한쪽이 비어있으면 그 차원은 제약 없음**으로 처리. 둘 다 비어있는 경우만 미발송 (요청 검증으로도 막힘).
- **NATIONAL/null 정책은 항상 매칭** — 어느 시/도를 선택했든 전국 정책은 후보가 됨.
- **북마크된 정책 제외** — 사용자가 이미 인지한 정책에 대한 추천은 노이즈.
- **이미 추천한 정책 재추천 금지** — `(userId, policyId, RECOMMENDATION)` 이력 기준.
- **상한 5건** — 정렬은 마감 임박 우선, 그다음 최신 등록.

### 7.3 비용 추정

- 사용자당 후보 정책 수: 카테고리 2~3 × 시/도 2~3 × OPEN 상태 = 평균 30~50건.
- 각 후보에 룰 평가 1회 (LLM 없음, 인메모리 매칭) ≪ 1ms.
- 1,000명 × 평균 40 후보 = 40,000 평가/주. 분당 처리량 부담 없음.

### 7.4 N+1 방어

`EligibilityService.judgeEligibility`를 후보 정책마다 호출(N번). v0 데이터 규모에서 충분. 후보가 100건 이상으로 늘면 batch API(`judgeEligibilityBatch`) 추가 검토.

## 8. 발송 흐름

### 8.1 스케줄러

```java
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {
    private final RecommendationDispatchService dispatch;

    @Scheduled(cron = "0 0 9 ? * MON")  // 매주 월요일 09:00
    public void sendWeekly() {
        dispatch.dispatchWeekly();
    }
}
```

위치: `user.infrastructure.scheduler` (기존 `NotificationScheduler`와 동일).

### 8.2 `RecommendationDispatchService`

```java
public void dispatchWeekly() {
    LocalDate today = LocalDate.now();
    List<NotificationSetting> settings =
        settingRepository.findAllByRecommendationEnabled(true);

    for (NotificationSetting s : settings) {
        try {
            dispatchOne(s, today);
        } catch (Exception e) {
            log.error("추천 발송 실패 userId={}", s.getUserId(), e);
            // 한 사용자 실패가 다른 사용자 발송을 막지 않음
        }
    }
}

@Transactional
protected void dispatchOne(NotificationSetting s, LocalDate today) {
    User user = userRepository.findById(s.getUserId()).orElse(null);
    if (user == null || isBlank(user.getEmail())) return;

    EligibilityProfile profile = profileRepository.findByUserId(s.getUserId())
        .orElse(null);
    if (profile == null) return;

    List<Policy> picks = recommender.recommend(s, profile, today);
    if (picks.isEmpty()) return;       // 빈 추천은 메일 미발송

    emailSender.sendRecommendationNotification(user.getEmail(), picks);

    for (Policy p : picks) {
        historyRepository.save(
            new NotificationHistory(s.getUserId(), p.getId(),
                                    NotificationType.RECOMMENDATION));
    }
}
```

- 사용자 단위 트랜잭션 분리(별도 메서드 + 자체 호출 시 `REQUIRES_NEW` 적용 필요 — 구현 시 spring AOP self-invocation 함정 주의. 대안: `RecommendationDispatchService`를 두 빈으로 쪼개거나, 사용자 단위 처리를 별도 클래스로 분리).
- 빈 추천 시 메일 미발송.
- 이력 기록은 발송 직후 — SMTP 응답 200이지만 도달 못 한 경우 중복 인지 불가는 마감 알림과 동일 트레이드오프, 수용.

### 8.3 `EmailSender` 포트 확장

```java
public interface EmailSender {
    void sendDeadlineNotification(String to, Policy policy);                  // 기존
    void sendRecommendationNotification(String to, List<Policy> policies);    // 신규
}
```

### 8.4 이메일 템플릿

- 제목: `[YouthFit] 이번 주 맞춤 정책 N건 추천`
- 본문 항목 (정책당):
  - 정책명 + 카테고리 배지
  - 정책 `summary` 1~2줄
  - 신청 마감일 (D-day 표기)
  - YouthFit 상세 페이지 링크
- 푸터:
  - "추천 해제하기" 마이페이지 링크
  - 본 메일은 자동 추천이며 최종 자격은 공식 신청 채널에서 확인하라는 안내

## 9. 프론트엔드 변경

### 9.1 컴포넌트

- `components/notification/InterestCategoryChips.tsx` — `Set<Category>` 다중선택 칩.
- `components/notification/InterestRegionChips.tsx` — `Set<RegionSidoCode>` 다중선택 칩.
- `components/notification/RecommendationSection.tsx` — 토글 + 두 칩 그룹 + 안내 메시지 + 저장 버튼.

### 9.2 `MyPage.tsx` 알림 탭 변경

- 기존 "맞춤 정책 추천" 토글 영역(line 860~)을 `RecommendationSection`으로 교체.
- 토글 ON일 때만 두 칩 그룹과 저장 버튼 노출.
- 토글 ON 상태에서 카테고리·지역 모두 비면 저장 버튼 disabled + 안내 ("관심 분야를 1개 이상 선택해주세요").
- 적합도 프로필 (`legalDongCode`/`age`) 미입력 시 정보 박스 노출 ("적합도 정보를 입력하면 더 정확해요" + 적합도 프로필 페이지 CTA). 토글은 켤 수 있도록 두되, 발송은 백엔드에서 스킵된다는 사실을 메시지로 알림.
- 이메일 미등록 시 기존 `NotificationPromptSheet`를 정책 ID 없는 모드로 호출하거나, MyPage 자체의 이메일 게이트 흐름(line 309~)을 재사용. (구현 시점에 더 자연스러운 쪽 선택)

### 9.3 타입 / API / 라벨

- `types/policy.ts` `NotificationSettings`:
  ```ts
  export interface NotificationSettings {
    emailEnabled: boolean;
    daysBeforeDeadline: number;
    recommendationEnabled: boolean;  // 단계 1에서 eligibilityRecommendationEnabled → 리네임 완료
    interestCategories: Category[];   // 단계 2에서 추가
    interestRegions: RegionSidoCode[]; // 단계 2에서 추가
  }
  ```
  단계 1에서 프론트·백엔드 모두 `recommendationEnabled`로 키를 통일했고, 단계 2에서 `interestCategories`/`interestRegions`가 추가된다.

- `apis/user.api.ts`: `updateNotificationSettings` payload 확장.

- `lib/labels/region.ts` (신규): `RegionSidoCode → 한국어 라벨` 매핑.
- `lib/labels/category.ts`: 기존이 있으면 그대로, 없으면 추가.

### 9.4 UX 디테일

- 시/도 17개는 모바일에서 2~3줄 wrap.
- 카테고리 7개는 1~2줄 wrap.
- 변경된 경우에만 [저장] 활성화 (dirty state).
- 저장 성공 시 토스트 ("알림 설정을 저장했어요").

## 10. 테스트 전략

### 10.1 단위 / 슬라이스
- `NotificationSetting.canDispatchRecommendation()` 분기 테스트.
- `RegionSidoCode.matches()` — 영문 코드, 법정동 prefix, NATIONAL/null 케이스.
- `RecommendationDispatchService.dispatchOne()` — Mockist 단위 테스트로 빈 추천 미발송, 북마크/이력 제외, 5건 상한, 정렬 검증.
- 컨트롤러 슬라이스 — 검증 규칙 (toggle ON + 빈 관심분야 → 400).

### 10.2 통합
- 스케줄러 cron 표현식 검증 (`@SpringBootTest` + `@SchedulerLock` 미도입 상태이므로 단일 인스턴스 가정).
- Flyway 마이그레이션 적용 검증.

## 11. 비범위 (v0 제외)

- 추천 점수화·랭킹 (v1).
- 시/군/구 단위 관심 지역.
- 이메일 발송 큐/리트라이 로직 (현재는 `EmailSender` 동기 호출).
- 메타 엔드포인트(`/options`).
- 알림 이력 화면(사용자가 발송된 추천을 다시 볼 수 있는 UI).

## 12. 운영 고려

- 스케줄러는 단일 인스턴스 가정. 다중 인스턴스 도입 시 분산 락 필요(별도 작업).
- SMTP 장애 시 한 사용자 실패가 전체를 막지 않도록 try/catch.
- 발송량 모니터링: 주간 발송 대상 수, 실제 발송 수, 실패 수 로그.

## 13. 마이그레이션 / 롤백

- 단계 1 롤백: `recommendation_enabled` 컬럼 drop.
- 단계 2 롤백: 두 ElementCollection 테이블 drop, `recommendationEnabled` 자동으로 false 처리.
- 단계 3 롤백: `RecommendationScheduler` 빈 등록 해제, `RECOMMENDATION` 이력 row 보존.

각 단계는 독립적이며, 단계 3까지 진행되어 있어도 단계 1만 롤백할 일은 없다(단방향 의존).
