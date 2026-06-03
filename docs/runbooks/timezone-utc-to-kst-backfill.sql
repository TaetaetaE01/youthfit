-- =============================================================================
-- 기존 데이터 타임존 보정: UTC → KST (+9시간)
-- =============================================================================
-- 배경
--   prod 컨테이너의 JVM 기본 타임존이 UTC 였던 동안, JPA Auditing(@CreatedDate/
--   @LastModifiedDate)과 LocalDateTime.now() 로 채워진 timestamp 가 모두 UTC
--   벽시계값으로 저장됐다(실제 시각보다 9시간 과거). Dockerfile 에 TZ=Asia/Seoul
--   을 적용해 재배포한 뒤, 그 이전에 쌓인 데이터를 +9시간 보정한다.
--
-- ⚠️ 보정 대상은 "LocalDateTime + timestamp without time zone" 컬럼만이다.
--   LocalDateTime 은 JVM 기본 타임존의 벽시계로 저장되므로, JVM 이 UTC 였을 때
--   9시간 과거로 찍혔다. JVM 을 KST 로 바꾸면 이후 데이터는 KST 로 저장된다.
--
-- ⛔ Instant(= timestamp WITH time zone) 컬럼은 절대 보정하지 않는다.
--   Instant.now() 는 절대 시점이고 timestamptz 는 그 시점을 그대로 저장하므로,
--   JVM 타임존과 무관하게 항상 올바른 시각이다. 여기에 +9 를 하면 시점 자체가
--   9시간 미래로 밀려 데이터가 손상된다. 해당 컬럼들(아래 "보정 금지")은 제외한다.
--
-- 대상 (Group A·B): created_at / updated_at 등 LocalDateTime 컬럼
--   - Group A  BaseTimeEntity 상속 13개 테이블의 created_at / updated_at
--   - Group B  LocalDateTime.now() 로 채우는 컬럼
--
-- 보정 금지 (Instant / timestamptz, JVM TZ 무관하게 시점 저장):
--   - ingestion_run_log(received_at, processed_at, created_at)
--   - ingestion_item_failure(created_at, last_retried_at)
--   - policy_processing_step(created_at, updated_at, started_at, finished_at)
--   - llm_cost_bucket(bucket_at, created_at, updated_at)
--   - scheduled_task_run(started_at, finished_at)  ← Instant
--
-- 그 밖의 보정 제외:
--   - LocalDate 컬럼(policy.apply_start/end, business_period_start/end): 날짜만 → 무관
--   - enrichment_job / email_send_attempt: Clock·외부 주입 등으로 출처가 모호 →
--     필요 시 컬럼 타입(without tz 여부)을 확인하고 개별 검증 후 추가
--   - 외부에서 들어온 원본 값(정책 발행일 등)
--
-- ⚠️ 반드시 한 번만 실행한다. 두 번 실행하면 +18시간이 되어 데이터가 더 망가진다.
--    아래 멱등 가드(tz_backfill_marker)가 재실행을 자동 차단하지만, 그래도 주의한다.
-- =============================================================================
--
-- 실행 절차 (자세한 내용은 같은 폴더의 timezone-utc-to-kst-backfill.md 참고)
--   1) DB 스냅샷/백업을 먼저 만든다 (RDS 수동 스냅샷 권장).
--   2) Dockerfile(TZ=Asia/Seoul) 을 prod 에 재배포한다.
--   3) ⚠️ 재배포 직후 ~ 보정 완료까지 쓰기 트래픽을 멈춘다(아래 cutoff 레이스 방지).
--   4) 아래 :cutoff 를 "재배포를 완료한 실제 시각(KST)" 으로 교체한다.
--      이 시각 이후로 새로 저장되는 데이터는 이미 KST 이므로 보정에서 제외된다.
--   5) 먼저 맨 아래 [DRY-RUN] 블록으로 영향 행수를 확인한 뒤, 본 트랜잭션을 실행한다.
--
-- 실행:  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f timezone-utc-to-kst-backfill.sql
-- =============================================================================

-- 재배포(TZ=KST 적용) 완료 시각, KST 기준. ⚠️ 반드시 실제 값으로 교체할 것.
-- (플레이스홀더 그대로 실행하면 timestamp 캐스트 에러로 안전하게 중단된다.)
\set cutoff 'YYYY-MM-DD HH24:MI:SS'

BEGIN;

-- -----------------------------------------------------------------------------
-- 멱등 가드: 이미 실행됐다면 PK 충돌로 트랜잭션 전체가 롤백된다(이중 보정 방지).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tz_backfill_marker (
    name        text PRIMARY KEY,
    executed_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO tz_backfill_marker (name) VALUES ('utc_to_kst_backfill_2026_06');

-- 각 UPDATE 는 해당 컬럼이 cutoff 이전(=UTC 로 저장된)인 행만 +9시간 보정한다.
-- NULL 값은 (NULL + interval = NULL, WHERE 조건 미충족) 자동으로 건너뛴다.

-- =============================================================================
-- Group A — BaseTimeEntity 상속 13개 테이블 (created_at, updated_at)
-- =============================================================================
UPDATE policy                            SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE policy                            SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE policy_attachment                 SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE policy_attachment                 SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE policy_source                     SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE policy_source                     SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE guide                             SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE guide                             SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE eligibility_rule                  SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE eligibility_rule                  SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE qna_history                       SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE qna_history                       SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE qna_question_cache                SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE qna_question_cache                SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE policy_document                   SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE policy_document                   SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE bookmark                          SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE bookmark                          SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE eligibility_profile               SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE eligibility_profile               SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE notification_setting              SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE notification_setting              SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE users                             SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE users                             SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

UPDATE policy_notification_subscription  SET created_at = created_at + interval '9 hours' WHERE created_at < :'cutoff';
UPDATE policy_notification_subscription  SET updated_at = updated_at + interval '9 hours' WHERE updated_at < :'cutoff';

-- =============================================================================
-- Group B — LocalDateTime.now() 로 채우는 컬럼 (timestamp without time zone)
-- =============================================================================
UPDATE notification_history  SET created_at   = created_at   + interval '9 hours' WHERE created_at   < :'cutoff';
UPDATE notification_history  SET sent_at      = sent_at      + interval '9 hours' WHERE sent_at      < :'cutoff';
UPDATE notification_history  SET failed_at    = failed_at    + interval '9 hours' WHERE failed_at    < :'cutoff';

UPDATE qna_cache_lookup_log  SET looked_up_at = looked_up_at + interval '9 hours' WHERE looked_up_at < :'cutoff';

COMMIT;

-- =============================================================================
-- [DRY-RUN] 본 트랜잭션 실행 전에 아래를 먼저 실행해 영향 행수를 확인한다.
--   :cutoff 를 실제 값으로 설정한 뒤, 아래 한 줄을 psql 에 붙여넣어 확인한다.
--   특히 created_at < cutoff 인데 updated_at >= cutoff 인 "경계 행"이 0 인지 확인하면
--   재배포~보정 사이 갱신으로 인한 행 단위 기준 엇갈림이 없음을 보증할 수 있다.
-- =============================================================================
-- SELECT 'policy.created_at'      AS col, count(*) FROM policy               WHERE created_at < :'cutoff'
-- UNION ALL SELECT 'policy.updated_at',      count(*) FROM policy               WHERE updated_at < :'cutoff'
-- UNION ALL SELECT 'users.created_at',       count(*) FROM users                WHERE created_at < :'cutoff'
-- UNION ALL SELECT 'qna_history.updated_at', count(*) FROM qna_history          WHERE updated_at < :'cutoff'
-- UNION ALL SELECT '!경계행(policy)',         count(*) FROM policy
--               WHERE created_at < :'cutoff' AND updated_at >= :'cutoff';
