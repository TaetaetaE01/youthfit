# 런북: 기존 데이터 타임존 보정 (UTC → KST)

## 무엇을, 왜

prod 컨테이너의 JVM 기본 타임존이 **UTC** 였던 동안, `LocalDateTime` 으로 채워진
timestamp(`created_at`, `updated_at`, `sent_at` 등)가 실제 시각보다 **9시간 과거(UTC
벽시계값)** 로 저장됐다. `Dockerfile` 에 `TZ=Asia/Seoul` 을 적용해 재배포하면
**앞으로의 데이터**는 KST 로 맞지만, **이미 쌓인 데이터**는 그대로 9시간 어긋나 있다.
이 런북은 그 기존 데이터를 +9시간 보정한다.

- 코드 수정(재배포 대상): `backend/Dockerfile` 의 `ENV TZ=Asia/Seoul`
- 보정 스크립트: [`timezone-utc-to-kst-backfill.sql`](./timezone-utc-to-kst-backfill.sql)

## ⛔ 보정 대상은 `LocalDateTime` 컬럼만 (Instant 는 절대 보정 금지)

컬럼 타입에 따라 동작이 완전히 갈린다. **반드시 구분**한다.

| 자바 타입 | DB 타입 | JVM TZ 영향 | 보정 |
|----------|---------|------------|------|
| `LocalDateTime` | `timestamp without time zone` | **있음** — JVM 기본 TZ 의 벽시계로 저장 | ✅ +9 |
| `Instant` | `timestamp with time zone` | **없음** — 절대 시점을 그대로 저장 | ⛔ 금지 |

`Instant.now()` 는 절대 시점이라 JVM 이 UTC 든 KST 든 **항상 올바른 시각**으로 저장된다.
여기에 +9 를 하면 시점이 9시간 미래로 밀려 **데이터가 손상된다.** 다음 컬럼들은 보정하지 않는다.

- `ingestion_run_log`(`received_at`, `processed_at`, `created_at`)
- `ingestion_item_failure`(`created_at`, `last_retried_at`)
- `policy_processing_step`(`created_at`, `updated_at`, `started_at`, `finished_at`)
- `llm_cost_bucket`(`bucket_at`, `created_at`, `updated_at`)
- `scheduled_task_run`(`started_at`, `finished_at`)

> 컬럼 타입은 다음으로 확인한다:
> ```sql
> SELECT table_name, column_name, data_type FROM information_schema.columns
> WHERE column_name LIKE '%_at' AND data_type LIKE '%time zone%' ORDER BY 3,1,2;
> ```

## 핵심 주의사항

- ⚠️ **반드시 한 번만 실행한다.** 두 번 실행하면 +18시간이 되어 데이터가 더 망가진다.
  스크립트의 `tz_backfill_marker` 가드가 재실행을 자동 차단하지만, 그래도 주의한다.
- ⚠️ **재배포(TZ=KST)가 먼저다.** 재배포 전에 보정하면, 보정 후에도 앱이 계속 UTC 로
  새 데이터를 쌓아 문제가 재발한다. 반드시 `순서: 재배포 → 보정` 을 지킨다.

## 실행 절차

### 1. 백업

RDS에서 **수동 스냅샷**을 먼저 만든다. (문제 시 복구 지점)

### 2. 재배포 (TZ=KST 적용)

`TZ=Asia/Seoul` 이 들어간 `Dockerfile` 로 백엔드를 prod 에 재배포한다.
**재배포를 완료한 실제 시각(KST)을 기록**해 둔다 — 다음 단계의 `cutoff` 값이 된다.

### 3. 쓰기 트래픽 중단 (필수)

재배포 직후 ~ 보정 완료까지 **쓰기를 멈춘다.** 멈추지 않으면, 재배포 직후 옛 행이 다시
update 될 때 `updated_at` 만 KST(cutoff 이후)로 갱신되고 `created_at` 은 UTC 로 남아
한 행 안에서 기준이 엇갈릴 수 있다(아래 DRY-RUN 의 "경계 행" 검증으로 0 임을 확인).

### 4. cutoff 설정 + DRY-RUN

`.sql` 파일 상단의 다음 줄을 **2단계에서 기록한 재배포 완료 시각**으로 교체한다.

```sql
-- 예: 2026-06-03 22:00 에 재배포를 마쳤다면
\set cutoff '2026-06-03 22:00:00'
```

> `cutoff` 이전(= UTC 로 저장된)인 행만 보정하고, 이후(= 이미 KST)인 행은 건너뛴다.
> 플레이스홀더(`YYYY-MM-DD ...`)를 안 바꾸고 실행하면 timestamp 캐스트 에러로 안전하게 멈춘다.

cutoff 를 설정한 psql 세션에서 `.sql` 하단의 **[DRY-RUN]** 블록을 먼저 실행해
보정 대상 행수와 "경계 행"(0 이어야 함)을 확인한다.

### 5. 보정 실행

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f timezone-utc-to-kst-backfill.sql
```

전체가 하나의 트랜잭션(`BEGIN ... COMMIT`)이라, 중간에 실패하면 전부 롤백된다.

### 6. 검증 + 트래픽 재개

어드민 정책 처리현황에서 `업데이트` 시각이 실제 시각과 맞는지 확인한다.
(프론트는 `2026.06.03 22:30` 형태로 KST 표시, hover 시 상대시간) 정상이면 트래픽을 재개한다.

## 보정 대상 컬럼 (`LocalDateTime` / `timestamp without time zone`)

| 그룹 | 채우는 방식 | 테이블 · 컬럼 |
|------|------------|--------------|
| A | JPA Auditing (`@CreatedDate`/`@LastModifiedDate`) | `policy`, `policy_attachment`, `policy_source`, `guide`, `eligibility_rule`, `qna_history`, `qna_question_cache`, `policy_document`, `bookmark`, `eligibility_profile`, `notification_setting`, `users`, `policy_notification_subscription` 의 `created_at`, `updated_at` |
| B | `LocalDateTime.now()` 직접 호출 | `notification_history`(`created_at`, `sent_at`, `failed_at`), `qna_cache_lookup_log`(`looked_up_at`) |

### 보정 제외

- **Instant / `timestamp with time zone`**: 위 "보정 금지" 표 참조 — 시점 저장이라 JVM TZ 무관
- `LocalDate` 컬럼(`policy.apply_start/end`, `business_period_start/end`): 날짜만 → 무관
- 출처 모호(개별 검증 후 결정): `enrichment_job`, `email_send_attempt` — 컬럼 타입(without tz 여부)부터 확인
- 외부에서 들어온 원본 값(정책 발행일 등)
