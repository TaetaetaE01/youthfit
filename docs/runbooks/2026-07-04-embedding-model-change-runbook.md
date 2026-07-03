# 임베딩 모델 변경 런북

> 관련: 이슈 #167 · spec `docs/superpowers/specs/2026-07-03-embedding-model-experiment-design.md`
> · 회고 `docs/troubleshooting/2026-07-04-embedding-experiment-pitfalls-trouble.md`
> 현행 기본값: `text-embedding-3-large` @ 1536d, QnA relevance threshold 0.48 (2026-07-04, PR 참조)

임베딩 모델(또는 dimensions)을 바꿀 때 반드시 함께 해야 하는 작업 목록.
**모델만 바꾸고 끝나는 변경이 아니다** — 기존 벡터·캐시·임계값이 전부 구모델 기준이다.

## 왜 전부 같이 바꿔야 하나

| 대상 | 이유 |
|---|---|
| `policy_document` 임베딩 | 구모델 벡터와 신모델 쿼리 벡터의 거리 비교는 무의미 — 전량 재인덱싱 필수 |
| `qna_question_cache` | 캐시된 질문 벡터가 구모델 기준 — 섞이면 의미 캐시 거리 계산 오염 |
| `youthfit.qna.relevance-distance-threshold` | distance 분포 스케일이 모델마다 다름 (relevantDistanceAvg 기준 3-small 0.455 vs 3-large 0.361 — 검증 리포트 20260704-002737) — 재보정 없이는 근거없음 필터가 무력화되거나 과차단 |
| `youthfit.qna.semantic-distance-threshold` | 위와 동일 — 질문쌍 분포 실측 후 조정 (#166) |
| 소스 해시 게이트 | 내용 기반 해시라 **모델 교체를 감지하지 못함** — 일반 재인덱싱 경로는 전부 스킵됨. 반드시 delete→reindex 경로 사용 |

## 절차

### 0. 사전 확인

- 대상 모델이 `LlmModelPricing`(metrics)에 등록돼 있는가 — 없으면 비용 집계가 UNKNOWN 으로 유실
- `TokenCounter.encoderFor` 가 모델명 prefix 를 아는가 (모르면 o200k fallback WARN)
- dimensions 를 바꾸는 경우: `PolicyDocument`·`QnaQuestionCache` 의 `columnDefinition = "vector(1536)"`
  2곳과 `backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql` 마이그레이션 필요 (별도 계획 권장)
- OpenAI 외 프로바이더로 가는 경우: `OpenAiEmbeddingClient` 의 엔드포인트가 하드코딩 —
  어댑터 신규 구현 필요

### 1. 설정 변경

```
# 기본값: backend/src/main/resources/application.yml (openai.embedding.model)
# 로컬 오버라이드: .env 의 OPENAI_EMBEDDING_MODEL — 기본값과 다르면 로컬이 구모델로 돌게 되니 주의
```

### 2. 전량 재인덱싱 (dev/로컬 — eval reindex 모드)

```bash
cd backend
# dry-run 으로 대상 확인 (LLM/임베딩 호출 0회)
SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=reindex'
# 실제 실행 — 정책당 트랜잭션 delete→재인덱싱, 실패 시 해당 정책 롤백 후 계속
SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=reindex --eval.confirm=true'
```

- 소요·비용 참고 실측(84정책/1,379청크): 3-large 기준 약 80초 / 약 $0.06
- prod 재배포 후에는 동일 원리로: 앱이 뜬 뒤 eval 프로파일 일회 실행(별도 프로세스, web-type none 이라 포트 충돌 없음)
  또는 향후 admin 일괄 재인덱싱 API 가 생기면 그쪽 사용

### 3. 의미 캐시 무효화

```bash
docker compose exec -T postgres psql -U youthfit -d youthfit -c "TRUNCATE qna_question_cache;"
```

(prod 은 해당 DB 에 동일 실행. 정확 캐시(Redis QnaAnswerCache)는 TTL 24h 라 자연 소멸 —
즉시 정리하려면 Redis flushdb 대신 키 패턴 삭제 권장)

### 4. threshold 재보정

1. 같은 평가셋으로 측정: `--eval.mode=run --eval.scenarios=baseline --eval.label=<모델명>`
2. 리포트의 케이스별 `top1Distance` 로 threshold 스캔 — positive 통과율 + NEGATIVE 차단율 합이
   최대인 지점을 찾는다 (회고 문서 §5 에 스캔 방법·근거)
3. `youthfit.qna.relevance-distance-threshold` 기본값 갱신 + yml 주석에 근거 기록

### 5. 검증

- 평가셋 실행 73/73 OK (STALE/NO_CHUNKS 0) — 재인덱싱 정합 확인
- 리포트 지표가 사전 실험치와 일치하는지 확인 (다르면 코퍼스·모델 불일치 의심 —
  회고 문서 "함정 1" 참고: reindex 경로는 첨부 포함 조립이라 청크 수가 1차 인덱싱과 다를 수 있음)
- negFP 가 재보정 threshold 기준으로 리포트에 찍히는지 확인 (러너가 QnaProperties 값 사용)

### 6. 롤백

역순으로 동일: env/기본값 원복 → 재인덱싱 → 캐시 truncate → threshold 원복.
구모델 질문 임베딩 캐시(`backend/eval/cache/embeddings-<model>.json`)는 남아 있어 평가 재실행 비용 0.
