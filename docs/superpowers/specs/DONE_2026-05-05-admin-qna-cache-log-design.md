# 어드민 — Spec 3: Q&A 캐시 hit/miss 로그 설계

> **상태**: Spec 확정 (2026-05-06 brainstorming 완료)
> **작성일**: 2026-05-05 (파일명 유지)
> **시리즈**: 어드민 시리즈 5개 중 #3
> **선행**: Spec 1 (admin foundation) DONE, Spec 2 (admin email tracking) DONE

---

## 1. 목표

semantic-cache의 매칭 품질과 비용 절감 효과를 운영적으로 추적한다.
- **집계**: 일자별 hit률 / 평균 유사도 / 미스 비율 / 비용 절감 추정 추세
- **건별**: lookup 1건 단위로 어떤 질문이 어떤 캐시 항목에 매칭됐는지(또는 매칭 안 됐는지) 디버깅
- **임계값 튜닝 근거 마련**: `BELOW_THRESHOLD` 분류로 "아쉽게 미스난" 사례를 식별

semantic-cache 인프라는 이미 구현되어 있다(`docs/superpowers/specs/DONE_2026-05-01-semantic-qna-cache-design.md`). 본 spec은 그 위에 *관찰* 레이어만 얹는다.

## 2. 범위

### In
- `QnaCacheLookupLog` 엔티티 (qna 모듈) + lookup 적재
- Q&A lookup 호출부에서 `HIT` / `BELOW_THRESHOLD` / `MISS` 3분기 분류 (cache 인터페이스 변경 포함)
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 기반 비동기 적재
- 어드민 화면:
  - KPI 4개 (오늘 hit률 / 어제 hit률 / 7일 평균 유사도 / 7일 비용 절감 추정)
  - 일자별 stacked bar (HIT/BELOW_THRESHOLD/MISS) + 결과 분포 도넛
  - 미스/below-threshold 사례 리스트 (필터·페이지네이션 + CSV export)
  - lookup 상세 (HIT 매칭 디버깅 / MISS 후보 표시)
- 보관 정책 90일 일일 retention 스케줄러
- 비용 절감 추정 단가 설정값 1개

### Out
- 캐시 항목 직접 편집 / 추가 (CRUD UI) — 별도 spec
- 사용자별 lookup 히스토리 (개인정보 부담)
- top-2/top-3 후보 보관 — 임베딩 보존되므로 향후 분석 도구에서 재계산 가능
- 실시간 알림 (미스 급증 등) — 외부 모니터링(Grafana)에서 처리
- Spec 4 (LLM 비용 대시보드) 데이터와 통합한 정확한 비용 절감 산식 — Spec 4 도입 후 갱신

## 3. 핵심 결정 로그

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| 1 | `question_text` 보관 | raw 평문 + `normalized_text` 평문, 90일 보관 | 미스 큐레이션이 운영의 핵심 가치, 정책 Q&A 도메인 특성상 raw 보관 가치 큼. 어드민 계정 보호로 PII 리스크 통제 |
| 2 | lookup 결과 분류 + 후보 | top-1 (id, similarity) + HIT/MISS/BELOW_THRESHOLD 3분기. cache API 임계값 외부화 | top-1만으로 임계값 튜닝과 미스 식별 모두 가능. top-N은 v0 ROI 약함 |
| 3 | 적재 트리거 | `@TransactionalEventListener(AFTER_COMMIT)` 비동기 | Q&A 사용자 핫패스 보호. `llm-async-events` 인프라 재사용 |
| 4 | 미스 큐레이션 워크플로우 | 미스 리스트 + CSV export. 캐시 추가 액션은 별도 spec | 발견과 행동 분리. 캐시 추가 UX는 결정사항 많음 |
| 5 | 비용 절감 산식 | 단일 설정값 (`...estimated-savings-per-hit-usd`) | 추세 지표가 핵심. Spec 4 도입 후 동적 계산으로 마이그레이션 |

## 4. 데이터 모델

### 4.1 엔티티 — `qna/domain/model/QnaCacheLookupLog`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | `BIGINT PK` | auto |
| `policy_id` | `BIGINT` | 정책 컨텍스트 (캐시 lookup 단위) |
| `question_text` | `TEXT` | raw 평문 |
| `normalized_text` | `TEXT` | 정규화 결과 |
| `result` | `VARCHAR(20)` enum | `HIT` \| `MISS` \| `BELOW_THRESHOLD` |
| `matched_cached_id` | `BIGINT` nullable | top-1 후보의 `qna_question_cache.id`. MISS 시 NULL |
| `similarity_score` | `DECIMAL(6,5)` nullable | top-1 유사도. MISS 시 NULL |
| `threshold_applied` | `DECIMAL(6,5)` | 적재 시점 임계값 (튜닝 추적용) |
| `llm_call_made` | `BOOLEAN` | 캐시 미스 후 LLM 호출 여부 |
| `looked_up_at` | `TIMESTAMP` | 호출 시점 |

### 4.2 FK 정책

`matched_cached_id` 는 FK 제약 *없이* 단순 `BIGINT` 컬럼으로 둔다. 캐시 항목이 만료/삭제돼도 과거 lookup 로그는 그대로 보존돼야 하므로(보관 90일 < 캐시 항목 회전 주기보다 짧을 수 있음) 참조 무결성 비강제.

### 4.3 인덱스

- `idx_qna_cache_lookup_at` on `(looked_up_at DESC)`
- `idx_qna_cache_lookup_result_at` on `(result, looked_up_at DESC)`
- `idx_qna_cache_lookup_policy_at` on `(policy_id, looked_up_at DESC)`

### 4.4 유사도 환산

pgvector `cosine_distance` (0~2 범위) → 유사도 `(0~1 범위) = 1 - distance / 2`. plan 단계에서 현재 `PgVectorSemanticQnaCache` 가 사용하는 거리 함수 확인 후 환산식 단언 — 단순 cosine 일 경우 `1 - distance` 적용.

### 4.5 이벤트 — `qna/application/event/QnaCacheLookupEvent`

```
record QnaCacheLookupEvent(
  Long policyId,
  String questionText,
  String normalizedText,
  LookupResultType result,        // HIT | MISS | BELOW_THRESHOLD
  Long matchedCachedId,           // nullable
  BigDecimal similarityScore,     // nullable
  BigDecimal thresholdApplied,
  boolean llmCallMade,
  Instant lookedUpAt
)
```

### 4.6 마이그레이션

신규 Flyway 파일 1개 (`V*__add_qna_cache_lookup_log.sql`). 백필 없음 — 과거 lookup은 추적 불가, 새로 적재 시작.

## 5. 아키텍처 & 적재 흐름

### 5.1 모듈 경계

| 위치 | 책임 |
|---|---|
| `qna/domain/model/QnaCacheLookupLog` | 엔티티 |
| `qna/infrastructure/repository/QnaCacheLookupLogRepository` | JPA repo |
| `qna/application/event/QnaCacheLookupEvent` | record 이벤트 |
| `qna/application/event/QnaCacheLookupEventListener` | `AFTER_COMMIT` 적재 listener |
| `qna/application/service/QnaService` | 분류 후 이벤트 발행 |
| `qna/infrastructure/cache/PgVectorSemanticQnaCache` | API 변경 (§ 6) |
| `admin/presentation/controller/AdminQnaCacheController` | 조회 전용 컨트롤러 |
| `admin/presentation/dto/response/QnaCacheLookup*Response` | 어드민 응답 DTO |
| `qna/infrastructure/scheduler/QnaCacheLookupRetentionScheduler` | 90일 retention (기존 일일 스케줄러 재사용 우선) |

### 5.2 적재 흐름

```
QnaService.processQuestion()
  └─ semanticQnaCache.findSimilar(...) → LookupResult { closest, cachedAnswer? }
  └─ 호출부 분류: similarity vs threshold → HIT | BELOW_THRESHOLD | MISS
  └─ applicationEventPublisher.publishEvent(QnaCacheLookupEvent {...})
       └─ (트랜잭션 AFTER_COMMIT)
       └─ Listener → repository.save(QnaCacheLookupLog)
            └─ 적재 실패 시 try/catch + warn 로그 (사용자 경로 영향 없음)
```

> **트랜잭션 컨텍스트 주의**: `@TransactionalEventListener(AFTER_COMMIT)` 는 이벤트 publish 시점에 활성 트랜잭션이 있어야 발화한다(기본 `fallbackExecution = false`). `QnaService.processQuestion()` 이 `@Transactional` 경계 안에서 실행되도록 plan 단계에서 확인 필수. 만약 비트랜잭션 경로가 있다면 `fallbackExecution = true` 로 `ApplicationEvent` 폴백 처리.

## 6. Cache 인터페이스 변경

### 6.1 변경 전

```java
Optional<CachedAnswer> findSimilar(Long policyId, String question, float[] embedding);
// 임계값을 cache 내부에서 적용. 호출부는 hit/miss 2분기만 알 수 있음
```

### 6.2 변경 후

```java
LookupResult findSimilar(Long policyId, String question, float[] embedding);

record LookupResult(
  Optional<Match> closest,            // 항상 가장 가까운 후보 1건 (있으면)
  Optional<CachedAnswer> cachedAnswer // 임계값 통과한 경우에만
)

record Match(Long cachedId, BigDecimal similarity)
```

- 임계값 비교는 호출부(`QnaService`) 책임으로 이동
- `closest` 가 비어 있으면 `MISS`, 있고 `cachedAnswer` 비어 있으면 `BELOW_THRESHOLD`, 둘 다 있으면 `HIT`
- 임계값 설정 키 `youthfit.qna.semantic-distance-threshold` 는 그대로 유지, 적용 지점만 외부화

### 6.3 영향 파일

- `PgVectorSemanticQnaCache` (구현 변경)
- `QnaService` (호출부 분류 + 이벤트 발행)
- 관련 단위/통합 테스트 (Testcontainers pgvector)

## 7. 어드민 화면

### 7.1 라우트

- `/admin/qna-cache` — 메인
- `/admin/qna-cache/:lookupId` — 상세

사이드바(`AdminSidebar.tsx`)는 이미 `to: '/admin/qna-cache'` 항목이 `soon: true` 로 등록돼 있으므로 플래그 제거.

### 7.2 메인 화면 구성

**KPI 카드 4개** (Spec 2 `KpiCard` 재사용)
- 오늘 hit률 (`hits / total × 100%`)
- 어제 hit률 (전일 대비 트렌드 ↑↓)
- 7일 평균 유사도 (HIT 사례 한정)
- 7일 비용 절감 추정 (USD, `hit_count × estimated-savings-per-hit-usd`)

**차트 영역 2단**
- 좌: **일자별 stacked bar** (Spec 2 `StackedBarChart` 재사용) — HIT/BELOW_THRESHOLD/MISS 3색 적층, 14일 윈도우
- 우: **결과 분포 도넛** — 같은 윈도우 누적 비율, Recharts `PieChart` 신규 (재사용 가능한 형태로 작성)

**테이블 + 필터 + 페이지네이션** (Spec 2 패턴)
- 컬럼: 시각 / 결과 뱃지 / 정책 ID(링크) / 질문 발췌(50자 ellipsis) / 유사도 / 매칭 캐시 ID
- 필터: 결과 (전체/HIT/BELOW_THRESHOLD/MISS), 정책 ID, 기간 (시작·종료일)
- 페이지네이션: 20건/페이지, Spec 2 `Pagination` 재사용
- 우상단 **"미스 CSV export"** 버튼 — 현재 필터를 그대로 적용한 미스/below-threshold 항목 다운로드

### 7.3 상세 화면

**HIT일 때**
- 메타: 시각, 정책, 결과(HIT 뱃지)
- 질문 (raw + normalized 두 줄 비교)
- 매칭 캐시 카드: cached 질문 텍스트, 유사도, 적용 임계값
- 답변 미리보기: 매칭 캐시 답변 발췌 (lazy load)

**MISS / BELOW_THRESHOLD일 때**
- 메타: 시각, 정책, 결과 뱃지(MISS 빨강 / BELOW 주황)
- 질문 (raw + normalized)
- 가장 가까운 후보 1건 (BELOW일 때): cached 질문 텍스트, 유사도, 임계값 — 차이 강조 ("임계값에 0.03 못 미침" 등)
- LLM 호출 여부 표시

### 7.4 API 엔드포인트

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/v1/admin/qna-cache/kpi` | `QnaCacheLookupKpiResponse` |
| GET | `/api/v1/admin/qna-cache/daily-stats?days=14` | `List<QnaCacheLookupDailyStatsResponse>` |
| GET | `/api/v1/admin/qna-cache?result=&policyId=&from=&to=&page=&size=` | `Page<QnaCacheLookupSummaryResponse>` |
| GET | `/api/v1/admin/qna-cache/{id:\d+}` | `QnaCacheLookupDetailResponse` (id는 숫자 제약) |
| GET | `/api/v1/admin/qna-cache/export?result=&policyId=&from=&to=` | `text/csv` 스트리밍 (`Content-Disposition: attachment`) |

인증: Spec 1 의 `@RequireAdmin` 적용.

### 7.5 프론트 파일

- `frontend/src/pages/admin/AdminQnaCachePage.tsx`
- `frontend/src/pages/admin/AdminQnaCacheDetailPage.tsx`
- `frontend/src/apis/admin.qnaCache.api.ts`

## 8. 환경 변수 / 설정

| 키 | 기본값 | 의미 |
|---|---|---|
| `youthfit.qna.cache.estimated-savings-per-hit-usd` | `0.0015` | KPI 비용 절감 추정 단가 |
| `youthfit.qna.cache.lookup-log.retention-days` | `90` | retention 스케줄러 기준 |
| `youthfit.qna.semantic-distance-threshold` | `0.20` (기존) | 그대로 유지, 적용 지점만 호출부로 이동 |

## 9. 보관 정책 / 운영

- `qna_cache_lookup_log` 90일
- 삭제 방식: plan 단계에서 기존 일일 스케줄러(예: `llm-async-events` 또는 `notification-recommendation` 사이클의 정리 작업) 재사용 우선. 없으면 신규 `QnaCacheLookupRetentionScheduler` 추가, 매일 03:00 KST `DELETE FROM qna_cache_lookup_log WHERE looked_up_at < now() - interval '90 days'`
- Listener는 자체 `try/catch` + warn 로그. 적재 실패가 사용자 응답에 영향 없음
- 별도 Micrometer 메트릭/Grafana 대시보드는 본 spec 범위 외. 본 테이블이 1차 데이터 소스가 됨
- 별도 `operations/*-runbook.md` 작성하지 않음 (외부 의존 없음, 신규 환경 변수 default 안전). plan 의 "후속/미결" 섹션에 운영 메모만 남김

## 10. 테스트 전략

### 10.1 백엔드 단위
- `QnaCacheLookupResultClassifier` (`LookupResult` + threshold → `LookupResultType`): HIT/MISS/BELOW_THRESHOLD 3분기 + 경계값 (similarity == threshold 정확히 같을 때 → HIT 포함 명세)
- 유사도 환산식 (cosine distance → 0~1 유사도)

### 10.2 백엔드 슬라이스 / 통합
- `QnaCacheLookupEventListener` — `@SpringBootTest` + `AFTER_COMMIT` 발행 검증, 적재 실패 시 사용자 경로 영향 없음 검증
- `PgVectorSemanticQnaCache.findSimilar` 인터페이스 변경 — Testcontainers (pgvector) 매칭/비매칭 모두 closest 회신 검증
- `AdminQnaCacheController` 슬라이스 (`@WebMvcTest` + `@WithMockUser(roles="ADMIN")`): 각 엔드포인트 200/필터/페이지네이션, CSV export 응답 헤더(`Content-Type: text/csv`, `Content-Disposition: attachment`)

### 10.3 백엔드 E2E
- `QnaService.processQuestion()` 호출 → `QnaCacheLookupLog` 1건 적재 검증 (Spec 2 `EmailSendAttempt` 통합 테스트 구조 답습)
- retention 스케줄러: 90일 + 1일 데이터 셋업 후 호출 → 삭제 검증 (TTL 단축 설정으로)

### 10.4 프론트엔드
- 컴포넌트: KPI 카드, 일자별 stacked bar(빈 데이터 / 1일 / 14일 풀), 도넛 비율 합 100%, 결과 뱃지 색상 (HIT 초록 / BELOW 주황 / MISS 빨강)
- 페이지 통합: 필터 변경 → API 재호출 + 페이지네이션 reset, CSV export 버튼 → blob 다운로드 트리거 (Spec 2 패턴)
- 상세 페이지: HIT / MISS / BELOW_THRESHOLD 3 분기 렌더링

### 10.5 커버리지
- 신규 백엔드 코드 라인 커버리지 80%
- 프론트는 페이지·컴포넌트 단위 happy path + 에러 상태 1건씩

### 10.6 검증 커맨드 (plan 단계에서 정확한 명령어 명시)
- `./gradlew test`
- `cd frontend && npm run test`
- `cd frontend && npm run typecheck && npm run lint`

## 11. 의존성

- Spec 1 (admin foundation) DONE — 사이드바, `@RequireAdmin`, `/api/v1/admin/**` 라우트
- Spec 2 (admin email tracking) DONE — Recharts, `KpiCard`, `StackedBarChart`, `Pagination` 컴포넌트 / 응답 DTO 명명 규칙
- semantic-cache 인프라 DONE — `PgVectorSemanticQnaCache`, `QnaQuestionCache`
- `llm-async-events` 인프라 DONE — `@TransactionalEventListener(AFTER_COMMIT)` 패턴 / 일일 스케줄러 위치
- 다른 어드민 spec(4·5)은 의존하지 않음

## 12. 변경 영향 범위

### 12.1 신규
- `qna/domain/model/QnaCacheLookupLog`
- `qna/domain/model/LookupResultType` (enum)
- `qna/infrastructure/repository/QnaCacheLookupLogRepository`
- `qna/application/event/QnaCacheLookupEvent`
- `qna/application/event/QnaCacheLookupEventListener`
- `qna/application/service/QnaCacheLookupResultClassifier` (또는 `QnaService` 내부 메서드)
- `qna/infrastructure/scheduler/QnaCacheLookupRetentionScheduler` (기존 스케줄러 재사용 시 생략)
- `admin/presentation/controller/AdminQnaCacheController`
- `admin/presentation/api/AdminQnaCacheApi`
- `admin/presentation/dto/response/QnaCacheLookup{Kpi,DailyStats,Summary,Detail}Response`
- `admin/application/service/AdminQnaCacheService` (조회 + 집계)
- Flyway: `V*__add_qna_cache_lookup_log.sql`
- 프론트 페이지/컴포넌트/API 함수 (§ 7.5)

### 12.2 수정
- `qna/infrastructure/cache/SemanticQnaCache` (인터페이스), `PgVectorSemanticQnaCache` (구현) — `findSimilar` 시그니처 변경
- `qna/application/service/QnaService` — 분류 + 이벤트 발행
- `frontend/src/components/layout/AdminSidebar.tsx` — `soon: true` 제거
- `application.yml` (또는 동등) — 신규 설정 키 2개

## 13. 위험 / 트레이드오프

| 위험 | 완화 |
|---|---|
| Q&A 사용자 핫패스 적재 부담 | `AFTER_COMMIT` 비동기 + listener `try/catch` 격리. 적재 실패 시 사용자 응답 무영향 |
| 미스 누락(이벤트 publish 실패 등) | 추세 지표 특성상 일부 누락 허용. 측정값이 *정확*해야 하는 KPI 아님. 절대값 정확도 부차적 |
| `question_text` PII 잔존 | 어드민 계정 보호 + 90일 자동 삭제. 정책 Q&A 도메인 특성상 위험은 낮으나 0이 아님 |
| `findSimilar` 시그니처 변경의 파급 효과 | 호출 지점이 `QnaService` 한 곳이라 영향 범위 작음. 단위 테스트 + Testcontainers로 회귀 방지 |
| 비용 절감 단가 부정확 | "추세" 지표라 명시. Spec 4 도입 후 동적 산식으로 마이그레이션 예정 |

## 14. 후속 / 비범위

- 캐시 항목 직접 추가/편집 UI — 별도 spec
- top-2/top-3 후보 보관 (미스 깊은 디버깅) — v0 운영 데이터 누적 후 ROI 재평가
- Spec 4 (LLM 비용) 도입 후 비용 절감 산식을 동적 계산으로 갱신
- 사용자별 lookup 히스토리 — 개인정보 정책 합의 후 검토
- 미스 급증 알림 (Grafana 등 외부 모니터링)
- Micrometer 메트릭 노출 (lookup hit률 게이지 등)
- 임계값 동적 변경(설정 hot reload) — `findSimilar` 외부화로 인프라는 마련됨

---

## 부록: 시리즈 5개 spec 간 공통 사항

| 항목 | 결정 메모 |
|---|---|
| 인증/라우팅 | Spec 1 결정 (`/api/v1/admin/**`, `@RequireAdmin`) |
| ReadModel 패턴 | admin 모듈은 조회만; 데이터는 각 도메인이 적재 (본 spec은 qna 모듈 적재) |
| 차트 라이브러리 | **Recharts** (Spec 2 결정, 본 spec 답습) |
| 보관 정책 | 본 spec 90일 (§ 9) |
| 디자인 토큰 | Spec 1 다크 사이드바 + 브랜드 indigo (`frontend/src/index.css`) |
