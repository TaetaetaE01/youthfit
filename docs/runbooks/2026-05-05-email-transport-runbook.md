# Email Transport 운영 런북 (2026-05-05)

> AWS SES 어댑터 + Thymeleaf 본문 + NotificationHistory 상태 전이 도입 후 운영 가이드.

## 활성 모드 확인

```bash
# 부팅 후 health check
curl -s http://localhost:8080/actuator/health

# 어댑터 빈 분기 확인 (로그)
grep -E "LoggingEmailSender|SesEmailSender" backend/logs/*.log | head -5
```

## 환경변수 점검

```bash
# 운영 컨테이너에서 키 채워졌는지만 확인 (값은 출력 X)
docker compose exec backend printenv | grep -E "EMAIL_TRANSPORT|MAIL_FROM|AWS_SES" | sed 's/=.*/=***/'
```

기대값:
- `EMAIL_TRANSPORT=ses`
- `MAIL_FROM_ADDRESS=***`
- `AWS_SES_ACCESS_KEY_ID=***`

## SES sandbox 해제 신청 (베타/공개 직전)

1. AWS 콘솔 → SES → Account dashboard → "Request production access"
2. 사용 사례: "Transactional emails — deadline reminders and weekly policy recommendations"
3. 일일 발송량 추정: 사용자 수 × 7 (마감 1일/추천 1주)
4. unsubscribe 메커니즘 설명: 풋터 설정 페이지 링크

## 발송 실패 모니터링

```bash
# 최근 1시간 SES 발송 실패 카운트
docker compose logs backend --since 1h | grep "SES 발송 실패" | wc -l

# 최근 1시간 markFailed 호출
docker compose logs backend --since 1h | grep "markFailed" | wc -l
```

## DB 상태 확인

```sql
-- 상태별 분포
SELECT status, notification_type, COUNT(*) FROM notification_history GROUP BY status, notification_type;

-- 최근 24h FAILED 원인
SELECT failed_at, failure_reason FROM notification_history
WHERE status = 'FAILED' AND failed_at > NOW() - INTERVAL '24 hours'
ORDER BY failed_at DESC LIMIT 20;

-- stale PENDING (24h 초과)
SELECT id, user_id, policy_id, notification_type, created_at FROM notification_history
WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '24 hours';
```

## 영구 실패 행 정리

운영자 판단으로 일시적 실패였다고 보면:

```sql
-- 7일 이상 된 FAILED 행 정리 → 다음 cron 에서 재시도됨
DELETE FROM notification_history WHERE status = 'FAILED' AND failed_at < NOW() - INTERVAL '7 days';

-- 24h 이상 PENDING 정리 (JVM crash 등으로 잔존)
DELETE FROM notification_history WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '24 hours';
```

## dry-run 절차

```bash
# 1. logging 모드로 띄워서 본문 확인
EMAIL_TRANSPORT=logging docker compose up -d --build backend

# 2. 스케줄러 트리거 대기 또는 수동 호출 (cron 시각 외 직접 테스트 시)
#    NotificationScheduler 의 cron 문자열 확인

# 3. 로그에서 렌더된 HTML 미리보기
docker compose logs backend | grep "이메일 발송"
```

## 비상 정지

```bash
# SES 호출만 즉시 중단 (logging 으로 fallback)
docker compose exec backend printenv EMAIL_TRANSPORT
EMAIL_TRANSPORT=logging docker compose up -d backend
```

## 후속 작업 (backlog)

- SES sandbox 해제 + 도메인 확보 (DNS 작업)
- List-Unsubscribe 헤더 + one-click unsubscribe
- stale PENDING 자동 cleanup cron
- FAILED 자동 재시도 정책
- Actuator/Prometheus metrics 노출
