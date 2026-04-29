# Q&A v0 출시 품질 마무리 — 운영 노트 (2026-04-30)

## 1. prod 배포 전 ALTER

본 프로젝트는 Flyway/Liquibase 미도입. prod 는 `ddl-auto: validate` 라 사전 ALTER 가 필요하다.

```sql
ALTER TABLE qna_history
  ADD COLUMN status        VARCHAR(20),
  ADD COLUMN failed_reason VARCHAR(30);

UPDATE qna_history SET status = 'COMPLETED' WHERE status IS NULL;

ALTER TABLE qna_history
  ALTER COLUMN status SET NOT NULL;
```

검증:

```sql
SELECT status, COUNT(*) FROM qna_history GROUP BY status;
-- 기대: COMPLETED 만 출력 (백필 결과)
```

## 2. Redis 캐시

- 키 패턴: `qna:answer:<policyId>:<sha256(normalizedQuestion)>`
- TTL: `youthfit.qna.cache-ttl-hours` (기본 24h)
- 강제 무효화: `redis-cli --scan --pattern "qna:answer:<policyId>:*" | xargs redis-cli del`
  - 정책 원문이 갱신되었는데 캐시가 오래된 답변을 들고 있을 때 사용

## 3. CostGuard

기존 `youthfit.cost-guard.policy-allowlist` 환경변수 그대로. Q&A 진입점에서도 동일 allowlist 적용된다.

## 4. 유사도 임계값

- 기본값 0.4 (`youthfit.qna.relevance-distance-threshold`)
- 0.4 의 의미: pgvector 코사인 거리 <= 0.4 인 청크만 컨텍스트로 사용
- 운영 데이터 확보 후 조정. 메트릭 `qna_relevance_distance{quantile=...}` 분포를 보고 결정 (메트릭 도입은 후속 작업)

## 5. status 별 운영 의미

| status | 의미 | 운영 액션 |
|---|---|---|
| IN_PROGRESS | 답변 생성 중 또는 SSE 도중 끊김 후 잔존 | 잔존 비율이 높으면 SSE 타임아웃·abort 빈도 점검 (v0+ 운영 견고성) |
| COMPLETED | 정상 종료 또는 캐시 히트 | - |
| FAILED + failed_reason | 거절·LLM 에러 | failed_reason 분포로 정책별 인덱싱 누락(NO_INDEXED_DOCUMENT)·OpenAI 장애(LLM_ERROR) 모니터링 |

## 6. 후속 작업

- semantic 캐시 (spec §9.1)
- 운영 견고성 묶음 (spec §9.2)
- 인덱싱 헤딩 추출 (spec §9.3)
- 테스트 인프라 도입 (spec §9.4)
