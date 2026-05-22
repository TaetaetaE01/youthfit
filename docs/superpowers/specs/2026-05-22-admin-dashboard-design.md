# Admin Dashboard — 지통실 (Command Center) Spec

- **작성일**: 2026-05-22
- **상태**: Design approved, awaiting implementation plan
- **범위**: `/admin` 첫 화면 (`AdminDashboardPage`) 전면 교체
- **선행 작업**: `DONE_2026-05-05-admin-foundation-design.md`, 각 admin 도메인 페이지(ingestion/enrichment/email/llm-cost/qna-cache) — 모두 완료

## 1. 목표

관리자가 `/admin` 첫 화면에서 다음 질문에 즉시 답한다:

> **"지금 손봐야 할 게 있는가?"**

현재 `AdminDashboardPage`는 KPI/차트/테이블이 전부 placeholder다. 각 admin 도메인 페이지(ingestion·enrichment·email·llm-cost·qna-cache)는 이미 충분히 구축되어 있어, 대시보드는 **"각 영역 요약 + 이상 감지 + 빠른 진입"**의 지휘통제실 역할로 재설계한다.

### 1.1. 1순위 용도

- 이상 감지·대응: 정상이 아닐 때 즉시 인지하고 해당 페이지로 진입
- 부차 목적: 평소엔 운영 현황 요약 (트렌드 + 핵심 숫자)

### 1.2. v1 비범위

- 슬랙/이메일 푸시 알림 (대시보드 방문 시 표시만)
- 실시간 SSE/WebSocket (30초 폴링으로 충분)
- 알림 음소거·확인 처리·히스토리
- 임계치 운영자 편집 UI (`application.yml`로 시작, 필요해지면 설정 UI 추가)

## 2. 레이아웃 (Action Queue + Status Grid)

```
┌────────────────────────────────────────────────────┐
│ 관리자 대시보드             [API 상태] [↻ 갱신중] │
│ 마지막 갱신 32초 전                                │
├────────────────────────────────────────────────────┤
│ ⚠ Action Required (3)                              │
│  ┌──────────────────────────────────────────────┐  │
│  │ 🔴 Ingestion 출처 2개가 7일 이상 미갱신      │  │
│  │    onlineyouthcenter.kr, gov24.go.kr  [확인] │  │
│  ├──────────────────────────────────────────────┤  │
│  │ 🔴 어제 LLM 비용 ₩42,310 (7일 평균 1.7배)    │  │
│  │                                       [확인] │  │
│  ├──────────────────────────────────────────────┤  │
│  │ 🟡 Enrichment 미리뷰 후보 24건 누적           │  │
│  │                                       [확인] │  │
│  └──────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────┤
│ 영역별 상태 (6)                          [최근 7일]│
│ ┌─────────┬─────────┬─────────┐                    │
│ │Ingestion│Enrichmt │ LLM 비용│                    │
│ │ 정상 ✓  │ 주의 ⚠  │ 경고 ⚠⚠ │  ← 미니 스파크라인 │
│ │ 12 출처 │ 24 대기 │ ₩142k/주│                    │
│ ├─────────┼─────────┼─────────┤                    │
│ │ Email   │QnA Cache│신규정책 │                    │
│ │ 정상 ✓  │ 정상 ✓  │ 주의 ⚠  │                    │
│ │ 98% 성공│ 적중 71%│ 오늘 4건│                    │
│ └─────────┴─────────┴─────────┘                    │
└────────────────────────────────────────────────────┘
```

### 2.1. 동작

- **액션 큐 비었을 때**: "✅ 현재 이상 없음 — 운영 정상" 배너로 대체
- **영역 카드 클릭**: 해당 기존 페이지(`/admin/ingestion` 등)로 이동
- **액션 큐 [확인] 버튼**: 해당 페이지의 *문제 항목 필터링된* URL (예: `/admin/ingestion?filter=stale`)로 이동
- **자동 갱신**: 30초 폴링, 윈도우 포커스 복귀 시 즉시 새로고침

## 3. 이상 신호 정의

총 9개 신호. 모든 임계치는 `application.yml`에서 조정 가능. 첫 운영 후 튜닝 예정.

| # | 신호 코드 | 조건 | Severity | 데이터 출처 |
|---|------|------|----------|------|
| 1 | `INGESTION_FAILURE` | 최근 24h 안 실패한 source ≥ 1 | HIGH | `IngestionSourceRepository` |
| 2 | `INGESTION_STALE` | active source 중 마지막 갱신 ≥ 7일 | HIGH | 위 동일 |
| 3 | `ENRICHMENT_FAILURE` | 최근 24h 실패한 enrichment job ≥ 1 | HIGH | `EnrichmentJobRepository` |
| 4 | `ENRICHMENT_BACKLOG` | 미리뷰 enrichment 후보 ≥ 20건 | MEDIUM | 위 동일 |
| 5 | `LLM_COST_SPIKE` | 어제 비용 ≥ 직전 7일 평균 × 1.5 | HIGH | `LlmCostRepository` |
| 6 | `LLM_WEEKLY_BUDGET` | 이번주 누적 ≥ `weekly-budget-krw` (default ₩100,000) | HIGH | 위 동일 |
| 7 | `EMAIL_FAILURE` | 최근 24h 실패율 ≥ 5% OR 절대 실패 ≥ 10건 | HIGH | `EmailAttemptRepository` |
| 8 | `QNA_CACHE_HIT_DROP` | 최근 7일 적중률이 직전 7일 대비 ≥ 10%p 하락 | MEDIUM | `QnaCacheLookupRepository` |
| 9 | `POLICY_INTAKE_STALL` | 오늘(KST 00시 이후) 신규 정책 < 직전 7일 일평균 × 0.3 | MEDIUM | `PolicyRepository` |

### 3.1. 정렬

액션 큐는 `severity DESC, detectedAt DESC` — HIGH가 먼저, 같은 severity면 최근 감지된 것 먼저.

### 3.2. "영역별 상태 카드" 매핑

| 카드 | 포함 신호 | status 산출 |
|------|-----------|------------|
| Ingestion | 1, 2 | 둘 다 정상=OK, 하나라도 MEDIUM=WARN, 하나라도 HIGH=CRITICAL |
| Enrichment | 3, 4 | 위 동일 |
| LLM 비용 | 5, 6 | 위 동일 |
| Email | 7 | 신호와 동일 |
| Q&A Cache | 8 | 신호와 동일 |
| 신규 정책 | 9 | 신호와 동일 |

신호는 9개지만 영역 카드는 6칸이다. Ingestion/Enrichment/LLM 비용은 카드 하나에 신호 2개 묶음.

## 4. 백엔드 설계

### 4.1. API

```
GET /api/admin/dashboard/overview
```

권한: `ROLE_ADMIN` (기존 admin API와 동일)

**Response**
```json
{
  "generatedAt": "2026-05-22T14:30:00Z",
  "actionItems": [
    {
      "code": "INGESTION_STALE",
      "severity": "HIGH",
      "title": "출처 2개가 7일 이상 미갱신",
      "detail": "onlineyouthcenter.kr, gov24.go.kr",
      "deeplink": "/admin/ingestion?filter=stale",
      "detectedAt": "2026-05-22T14:30:00Z"
    }
  ],
  "areas": [
    {
      "key": "ingestion",
      "label": "Ingestion",
      "status": "OK",
      "summary": "12 출처 활성",
      "sparkline": [3, 5, 4, 8, 6, 7, 4],
      "deeplink": "/admin/ingestion"
    }
  ]
}
```

- `status`: `OK` | `WARN` | `CRITICAL` (3.2의 매핑 결과)
- `sparkline`: 최근 7일 (KST 기준), 영역별로 의미가 다름
  - ingestion: 일별 신규 수집 건수
  - enrichment: 일별 완료 job 수
  - llm-cost: 일별 비용 (원)
  - email: 일별 성공 건수
  - qna-cache: 일별 적중률 (0–100)
  - policy-intake: 일별 신규 정책 건수

### 4.2. 구현 위치 (Clean Architecture)

```
admin/
├── application/
│   ├── service/
│   │   └── AdminDashboardOverviewService.java   ← 신규
│   └── dashboard/
│       ├── DashboardSignalEvaluator.java        ← 신규, 9개 신호 평가
│       ├── DashboardThresholds.java             ← @ConfigurationProperties
│       └── signals/
│           ├── DashboardSignal.java             ← interface
│           ├── IngestionFailureSignal.java
│           ├── IngestionStaleSignal.java
│           ├── EnrichmentFailureSignal.java
│           ├── EnrichmentBacklogSignal.java
│           ├── LlmCostSpikeSignal.java
│           ├── LlmWeeklyBudgetSignal.java
│           ├── EmailFailureSignal.java
│           ├── QnaCacheHitDropSignal.java
│           └── PolicyIntakeStallSignal.java
├── presentation/
│   ├── controller/
│   │   ├── AdminDashboardApi.java               ← 신규 interface
│   │   └── AdminDashboardController.java        ← 신규
│   └── dto/response/
│       ├── DashboardOverviewResponse.java
│       ├── DashboardActionItemResponse.java
│       └── DashboardAreaStatusResponse.java
```

### 4.3. 설계 원칙

- 각 `*Signal`은 **하나의 신호만** 책임 — 단위 테스트가 단순
- `DashboardSignalEvaluator`는 `List<DashboardSignal>`을 주입받아 모두 평가 (Spring이 자동 수집)
- 신호 추가/제거는 새 빈 추가/제거로 끝 (Open/Closed Principle)
- 각 Signal은 **repository 레벨**에서 직접 데이터를 가져온다 — 기존 `Admin*Service`를 호출하지 않음. 응집도 ↑, 기존 admin 페이지 회귀 위험 0
- `AreaStatusBuilder`(서비스 내부 메서드 또는 별도 클래스)가 신호 결과를 6개 영역 카드 status로 매핑

### 4.4. `DashboardSignal` 인터페이스

```java
public interface DashboardSignal {
    String code();   // INGESTION_STALE 등
    Optional<DashboardActionItem> evaluate(Instant now);
}
```

- `evaluate`가 `Optional.empty()`면 정상 (액션 큐에 안 뜸)
- 값이 있으면 `severity`, `title`, `detail`, `deeplink`, `detectedAt` 포함

### 4.5. 임계치 외부화

```yaml
# application.yml
admin:
  dashboard:
    llm:
      weekly-budget-krw: 100000
      daily-spike-multiplier: 1.5
    ingestion:
      stale-days: 7
    enrichment:
      backlog-warn: 20
    email:
      failure-rate-threshold: 0.05
      failure-count-threshold: 10
    qna-cache:
      hit-drop-threshold-pp: 10
    policy-intake:
      stall-ratio: 0.3
```

`@ConfigurationProperties("admin.dashboard")`로 묶어 각 Signal에 주입.

### 4.6. 부분 실패 처리

특정 Signal이 예외를 던지면 그 신호만 결과에서 빠지고, 나머지 응답은 정상 반환한다. `DashboardSignalEvaluator`가 신호별로 try/catch로 격리.

영역 카드는 **신호 결과를 받지 못해도 카드 자체는 항상 표시**하되, status는 보수적으로 `OK`로 두고 `summary`에 "데이터 없음"을 노출한다. 즉 응답 스키마에 별도 `degraded` 필드는 두지 않고 — 평가 실패는 **서버 로그(WARN)**에만 남긴다. 운영자가 카드 summary에서 "데이터 없음"을 보면 로그를 확인하도록 안내(운영 가이드 별도).

v2에서 알림 푸시를 추가할 때는 평가 실패도 알림 대상에 포함할지 결정 — v1 범위 아님.

### 4.7. 성능

- 30초 폴링이지만 어드민 동시 접속자는 1~수 명 수준 → 캐시 도입 불필요
- 각 Signal 쿼리는 모두 인덱스가 있는 컬럼 기반 (`created_at`, `last_synced_at`, `status` 등) — 응답은 수십 ms 예상

## 5. 프론트엔드 설계

### 5.1. 디렉토리 구조

```
frontend/src/
├── apis/
│   └── adminDashboard.api.ts                    ← 신규: getDashboardOverview()
├── hooks/queries/
│   └── useAdminDashboardOverview.ts             ← 신규: 30초 폴링 useQuery
├── types/
│   └── adminDashboard.ts                        ← 신규: DashboardOverview 타입
├── components/admin/dashboard/                  ← 신규 디렉토리
│   ├── ActionQueueSection.tsx                   ← 액션 큐 영역
│   ├── ActionItemRow.tsx                        ← 큐 1줄
│   ├── AllClearBanner.tsx                       ← 큐 비었을 때
│   ├── AreaStatusGrid.tsx                       ← 6칸 그리드
│   ├── AreaStatusCard.tsx                       ← 1칸 카드 (요약 + 스파크라인)
│   ├── StatusBadge.tsx                          ← OK/WARN/CRITICAL 배지
│   └── Sparkline.tsx                            ← SVG polyline 미니 차트
└── pages/admin/
    └── AdminDashboardPage.tsx                   ← 전면 교체
```

기존 `AdminKpiCard`, `AdminPageHeader`, `AdminPlaceholders`, `AdminSkeleton`는 **유지** — 다른 admin 페이지가 사용 중.

### 5.2. `AdminDashboardPage` 골격

```tsx
export default function AdminDashboardPage() {
  const overview = useAdminDashboardOverview();

  if (overview.isLoading) return <AdminSkeleton />;
  if (overview.isError) return <AdminErrorState onRetry={overview.refetch} />;

  const { actionItems, areas, generatedAt } = overview.data;

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="관리자 대시보드"
        description={`마지막 갱신 ${formatRelativeKst(generatedAt)}`}
        status={<RefreshIndicator isFetching={overview.isFetching} />}
      />

      {actionItems.length > 0
        ? <ActionQueueSection items={actionItems} />
        : <AllClearBanner />}

      <AreaStatusGrid areas={areas} />
    </div>
  );
}
```

### 5.3. 폴링 설정

```ts
// useAdminDashboardOverview.ts
useQuery({
  queryKey: ['admin', 'dashboard', 'overview'],
  queryFn: getDashboardOverview,
  refetchInterval: 30_000,
  refetchOnWindowFocus: true,
  staleTime: 25_000,
});
```

- React Query 기본 `refetchIntervalInBackground: false` 유지 → 백그라운드 탭에선 폴링 중단

### 5.4. 상태 처리

| 상태 | 표시 |
|------|------|
| 첫 로딩 | `AdminSkeleton` 재사용 |
| 에러 | 전체 에러 상태 + 재시도 버튼. 폴링 일시 중지 |
| 폴링 갱신 중 | 헤더 `isFetching` 인디케이터만, 화면 깜박이지 않음 |
| 액션 큐 비었음 | `AllClearBanner` (✅ "현재 이상 없음") |

### 5.5. 스파크라인

shadcn/ui 차트는 `recharts` 기반으로 무거움. **SVG polyline 직접 구현** (80×24px):

```tsx
function Sparkline({ values }: { values: number[] }) {
  const max = Math.max(...values, 1);
  const points = values
    .map((v, i) => `${(i / (values.length - 1)) * 80},${24 - (v / max) * 24}`)
    .join(' ');
  return (
    <svg viewBox="0 0 80 24" className="h-6 w-20">
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}
```

### 5.6. Status Badge 색상

| status | 색상 (Tailwind) | 아이콘 |
|--------|----------------|--------|
| OK | `bg-success-50 text-success-700` | ✓ |
| WARN | `bg-amber-50 text-amber-700` | ⚠ |
| CRITICAL | `bg-error-50 text-error-700` | ⚠⚠ |

## 6. 테스트 전략

### 6.1. 백엔드 (`spring-test` 컨벤션)

- `DashboardSignalEvaluatorTest` — 9개 Signal 빈을 mock해 결과 조합·정렬·부분 실패 격리 검증
- 각 `*SignalTest` — 임계치 경계값(이상/이하/같음) 단위 테스트. Repository는 stub
- `AdminDashboardControllerTest` — `@WebMvcTest` 슬라이스로 응답 스키마 + 권한(`ROLE_ADMIN`) 검증
- `AdminDashboardOverviewServiceIntegrationTest` — `@SpringBootTest` + testcontainer, fixture 9개 신호 모두 트리거되도록 데이터 셋업 → end-to-end 흐름 1회 검증

### 6.2. 프론트엔드 (Vitest + Testing Library)

- `AdminDashboardPage.test.tsx` — MSW로 4시나리오 (로딩/에러/액션큐 있음/All clear)
- `AreaStatusCard.test.tsx` — status별 배지·스파크라인 렌더링
- `ActionItemRow.test.tsx` — deeplink 클릭, severity별 색상
- `Sparkline.test.tsx` — values 길이별 SVG polyline 좌표 계산 정확성

## 7. 마이그레이션·롤아웃

- 기존 `AdminDashboardPage`는 한 번에 교체 — placeholder만 있어서 호환성 부담 없음
- 기존 공용 admin 컴포넌트(`AdminKpiCard`, `AdminPageHeader`, `AdminPlaceholders`, `AdminSkeleton`)는 유지
- 임계치 yaml은 `application.yml`에 default. `application-prod.yml`은 손대지 않음 — 필요 시점에 override
- 단일 PR로 백엔드 + 프론트엔드 함께 머지

## 8. 추후 확장 (v1 이후 검토)

- 슬랙/이메일 푸시 알림 (`DashboardSignalEvaluator` 재사용)
- 알림 음소거·확인 처리·히스토리 (DB 테이블 `dashboard_alert_acks`)
- 임계치 운영자 편집 UI (현재는 yaml)
- 신호 추가 후보: 응답 latency 이상, OAuth 실패율, 북마크 마감일 알림 발송 실패율 등
- 실시간 SSE/WebSocket (트래픽이 늘어나면 검토)

## 9. 결정 로그

| 결정 | 선택 | 근거 |
|------|------|------|
| 1순위 용도 | 이상 감지·대응 | 사용자 명시 — "지통실 느낌" |
| 신호 6+2개 | Ingestion 실패·stale, Enrichment 큐·실패, LLM 비용, 이메일, Q&A 적중률, 신규 정책 정체 | 사용자 4개 선택 + 권장 2개 채택 |
| 레이아웃 | C (액션 큐 + 상태 그리드) | 이상 감지 1순위 + 평소 트렌드 둘 다 충족 |
| 알림 채널 | 대시보드 방문 시만 | v1 범위 적정화. 30초 폴링 + 윈도우 포커스 갱신 |
| 백엔드 API | 통합 1개 (`/admin/dashboard/overview`) | 임계치 판정 로직 단일 위치, 향후 슬랙 알림 확장 시 재사용 |
| Signal 추상화 | `DashboardSignal` 인터페이스 + 빈 자동 수집 | Open/Closed, 단위 테스트 단순 |
| 데이터 접근 | Signal이 Repository 직접 사용 | 기존 `Admin*Service` 회귀 위험 0 |
| 임계치 외부화 | `@ConfigurationProperties` (yaml) | UI 없이 운영 중 조정 가능. UI는 후속 |
