# Q&A 의미 캐시 prefix 임베딩 시도와 false positive 롤백

- 작성일: 2026-05-03
- 작성자: TaetaetaE01
- 관련 커밋:
  - `142f4b6` (fix(qna): 의미 캐시 threshold 완화 + LLM 호출 결정성 강화) — PR #58
  - `4984fb9` (fix(qna): QuestionNormalizer 끝부호 제거로 정확 캐시 히트율 개선) — PR #59
  - `2975ea0` (fix(qna): 의미 캐시용 임베딩에 정책 컨텍스트 prefix 추가 + threshold 0.30) — PR #60
  - `232d166` (revert(qna): PR #60 의 의미 캐시 prefix 접근 되돌림 + threshold 0.20) — PR #61
- 관련 모듈: `backend/qna`

## 한 줄 요약

> 의미 동일 질문이 의미 캐시에 잡히지 않아 매번 LLM 이 호출되며 답변이 미묘하게 달라지는 현상을 두 단계로 풀어보려 했다. 1차로 threshold 완화 + LLM 결정성을, 2차로 임베딩에 정책 컨텍스트 prefix 를 추가했으나 prefix 가 같은 정책 단문을 너무 강하게 한 점에 모아 false positive 가 발생, 결국 롤백하고 query rewriting 을 후속 작업으로 미뤘다.

## 1. 상황 (Context)

### 처음 보고된 증상

- `qna_question_cache` 테이블에 의미가 같은 질문들이 별도 행으로 누적되고 있었다.
  관찰된 예:
  - "누가 대상자야 ?" / "누가 받을 수 있어 ?" / "누가 대상자라고 ?" / "신청 자격이 어떻게 되나요?"
  - "프리렌서도 가능이야 ?" / "프리렌서인데도 받을 수 있나요?"
  - "이 정책 간략하게 설명해줘" / "어떤 정책이야?"
  - "고마윙" / "감사합니다"
- 사용자 측 체감: 같은 의도의 질문에 답변 본문과 출처(인용 페이지)가 매번 미묘하게 달라짐.

### 영향 범위

- Q&A 호출 핫패스. 캐시가 미스되면 매번 `embedding + RAG + LLM` 호출이 새로 발생 → 응답 지연 + LLM 호출 비용.
- 정책 자체에 대한 답변 정확도는 RAG 가 매번 정상 동작했기 때문에 큰 문제가 없었다. 사용자가 신경 쓴 건 일관성과 비용 쪽.

## 2. 원인 (Root Cause)

핵심 원인은 **한국어 단문 + `text-embedding-3-small` 의 결합** 에서 의미 임베딩의 거리 분포가 의외로 넓다는 점이다. 운영 로그(`PgVectorSemanticQnaCache.findSimilar` log)에서 측정된 cosine distance:

```
"누가 대상자에요?" → 첫 질문 (NO_ROW)
"어떤 사람이 받을 수 있어요?" vs 위  → distance 0.530
"대상자는?" vs 위                    → distance 0.487
```

기존 threshold 가 `0.15` 였기 때문에 모든 의미 동일 변형이 미스로 떨어졌다.

추가로 `OpenAiQnaClient` 의 Chat Completions 요청에 `temperature` 가 명시되어 있지 않아 default `1.0` 으로 동작 → 미스 시점에서 LLM 답변이 비결정적이 되어 같은 컨텍스트라도 표현이 매번 달라졌다.

코드 경로:

- `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java:48` — distance > threshold 컷오프
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java:53-61` — temperature/seed 미지정
- `backend/src/main/resources/application.yml:80` — threshold 기본값

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 여부 |
|------|------|------------------|
| A. threshold 만 상향 (예: 0.22 → 0.30 → ...) | 코드 0줄, 즉시 적용 | 한국어 단문은 의미 동일 분포(0.07~0.10)와 의미 다름 분포(0.14~0.20)가 거의 겹쳐 단순 상향만으로는 false positive 위험. 1차로 0.22, 2차로 0.30 까지 시도했으나 결국 부족 |
| B. 의미 캐시용 임베딩에 정책명+카테고리 prefix 추가 (`[정책: {title} / {category}] {question}`) | 같은 정책 단문 거리만 좁히고 다른 정책과는 분리 가능 | 운영 검증에서 같은 정책 의도 다른 질문도 0.14~0.20 으로 끌어당겨 false positive 발생. 2차 시도로 채택했다가 롤백 |
| C. LLM query rewriting (짧은 질문을 표준 의문문으로 정규화 후 임베딩) | 의미 동일 클러스터링과 의도 분리 둘 다 살리는 정공법 | 추가 LLM 호출(저렴하지만) + 지연 + 구현 비용. 후속 작업으로 미룸 |
| D. 임베딩 모델 변경 (`text-embedding-3-large` 등) | 표면 어휘에 덜 민감 | 비용 ↑ + 효과 미지수. 운영 검증 부담 큼. 후순위 |
| E. 정확 일치 캐시(Redis) 정규화 강화 — 끝부호 제거 | 부호/공백 차이만 잡아도 일부 효과 | "누가 받을 수 있어 ?" vs "누가 받을 수 있어 ??" 정도만 잡힘. 보조 개선으로 채택 (PR #59) |
| F. 현 상태 유지 (캐시 효과 포기) | 변경 0 | 답변 일관성·비용 문제 그대로. 임시 fallback |

## 4. 선택과 이유 (Decision)

두 라운드의 결정과 그 결과를 분리해서 기록한다.

### 1차 라운드 — A (threshold 0.22) + LLM 결정성 + E (정규화 강화)

- 채택한 대안: A + LLM `temperature: 0.2`, `seed: 1` 추가 + E (PR #59)
- 결정의 핵심 근거:
  - 가장 작은 코드 변경으로 시작 — 가역적, 운영 위험 낮음
  - LLM 비결정성은 명백히 추가 호출이 발생할 때 답변 변동을 키우는 부수 요인. 캐시 정책과 독립적으로 유효한 변경
  - 정규화는 부호/공백 차이만 잡는 보수적 개선
- 트레이드오프: threshold 만으로 한국어 단문 한계를 못 풀 수 있다는 위험을 안고 가는 것. 운영 데이터로 결정
- 가역성: 환경변수 한 줄 변경. 즉시 회수 가능
- 결과 (PR #58, #59 머지 후 운영 검증):
  - distance 0.48~0.53 분포 확인 — threshold 0.22 로는 부족하다는 사실을 데이터로 확정

### 2차 라운드 — B (임베딩 prefix) + threshold 0.30

- 채택한 대안: B + threshold 0.22 → 0.30
- 결정의 핵심 근거:
  - 단순 상향만으로는 한계라는 운영 데이터 확인 후, 같은 정책 내 단문을 클러스터링할 직접적 수단 필요
  - C(query rewriting)는 LLM 호출 추가 + 구현 부담 → MVP 단계에서 과해 보였음
  - prefix 는 임베딩 입력만 바꾸면 되어 변경 범위가 최소
- 트레이드오프:
  - prefix 가 임베딩 공간에서 dominant 토큰이 될 위험은 인지했으나, threshold 가 함께 작동해 분리될 것으로 가정
  - 캐시 미스 시 임베딩 호출 1회 → 2회로 증가. 캐시 히트율로 상쇄될 것이라 가정
- 가역성: forward fix 또는 revert 가능
- 결과 (PR #60 머지 후 운영 검증):
  - 의미 동일군 distance: 0.073, 0.099 (threshold 0.30 의 1/3) — 의도대로 작동
  - **의미 다름군 distance: 0.140 (얼마 받을 수 있어), 0.185 (어디서 신청해), 0.202 (마감일은 언제야)** — 모두 threshold 0.30 이하라 false positive 발생
  - 사용자가 "마감일은?" 을 물었는데 "대상자" 답변이 캐시에서 반환됨

### 3차 라운드 — 롤백 + threshold 0.20 + 유저 데이터 모으기 (PR #61)

- 채택한 대안: B 되돌림 + threshold 0.20
- 결정의 핵심 근거:
  - false positive 가 답변 정확도에 직접 영향. 의미 동일 미스(응답 지연·비용)보다 우선 순위가 높음
  - 의미 동일군(0.07~0.10)과 의미 다름군(0.14~0.20)의 분포가 너무 가까워 prefix + threshold 조합으로는 안전 마진 확보 불가능
  - 안전 우선 + 후속에서 정공법(query rewriting) 적용
- 트레이드오프 (수용한 것):
  - 단문 의미 동일 캐시 효과는 사실상 포기. "누가 대상자야?" vs "대상자는?" 도 distance 0.49 권이라 미스
  - 실측 결과(2026-05-03 11:40 이후): 같은 의도 질문 3개 모두 RAG 가 새로 돌아 출처 페이지가 다 다름 → 캐시 미작동 확인됨
  - 응답 시간·LLM 비용은 한동안 그대로 부담
- 가역성: 매우 높음. environment 한 줄 + Java 30줄 변경
- 재검토 신호:
  - 운영 distance 분포가 충분히 모이면 의미 동일/다름 경계가 더 명확해질 수 있음
  - query rewriting 도입 후 다시 prefix 또는 threshold 조정 검토

## 5. 해결 (Solution)

### 적용된 변경 (시간 순)

- PR #58 (`142f4b6`): `application.yml` semantic-distance-threshold 0.15 → 0.22, `OpenAiQnaClient` 에 `temperature: 0.2`, `seed: 1` 추가
- PR #59 (`4984fb9`): `QuestionNormalizer.normalize()` 가 `[?!.]+` 끝부호를 제거하도록 변경
- PR #60 (`2975ea0`): `QnaService.processQuestion` 에 `cacheEmbedding` (prefix 포함) / `ragEmbedding` (원본) 분리, `QnaService.buildCacheQuery` 헬퍼 추가, threshold 0.22 → 0.30
- PR #61 (`232d166`): PR #60 의 코드 변경을 forward fix 로 되돌리고 threshold 0.30 → 0.20

### 반복 불가능한 부수 효과

각 단계에서 백엔드 컨테이너를 `docker compose up -d --build backend` 로 재빌드/재시작했고, Redis `qna:answer:*` 키와 Postgres `qna_question_cache` 테이블을 비웠다 (각 라운드 검증을 위해).

```bash
docker exec youthfit-redis sh -c "redis-cli --scan --pattern 'qna:answer:*' | xargs -r redis-cli DEL"
docker exec youthfit-postgres psql -U youthfit -d youthfit -c "TRUNCATE qna_question_cache RESTART IDENTITY;"
```

### 최종 코드 상태 (PR #61 머지 후)

- `QnaService.processQuestion`: 단일 `queryEmbedding` 호출, 의미 캐시·RAG 모두 같은 임베딩 사용 (PR #60 이전과 동일)
- `application.yml` `semantic-distance-threshold` 기본값: `0.20`
- `OpenAiQnaClient`: `temperature: 0.2` + `seed: 1` 유지 (PR #58 의 LLM 결정성 부분만 살아 있음)
- `QuestionNormalizer`: 끝부호 제거 유지 (PR #59)

## 6. 검증 (Result)

### 운영 distance 측정 (PR #60 적용 후)

| 비교 | distance | 의미 |
|------|----------|------|
| "대상자는?" vs "누가 대상자야?" | 0.073 | 동일 (히트 의도) |
| "어떤 사람이 받을 수 있어?" vs 위 | 0.099 | 동일 (히트 의도) |
| **"얼마 받을 수 있어?" vs 위** | **0.140** | **다름 — false positive** |
| **"어디서 신청해?" vs 위** | **0.185** | **다름 — false positive** |
| **"마감일은 언제야?" vs 위** | **0.202** | **다름 — false positive** |

→ false positive 의 데이터적 근거. PR #61 롤백의 직접 트리거.

### 운영 검증 (PR #61 적용 후)

- false positive: 사라짐. "마감일은?" / "얼마 받을 수 있어?" / "어디서 신청해?" 모두 자기 의도 답변 받음
- 의미 동일 단문 캐시: 미작동 확인. "누가 대상자야?" / "대상자는?" / "어떤 사람이 받을 수 있어?" 의 RAG 출처가 매번 다름 (각각 다른 페이지 셋 반환) → 임베딩이 다 달라 RAG 가 새로 돌고 LLM 도 새로 호출됨

### 모니터링 포인트

- `PgVectorSemanticQnaCache` 의 `Q&A 의미 캐시 히트` / `missReason=DISTANCE_OVER_THRESHOLD` 로그 → distance 분포 누적
- 의미 동일 / 의미 다름 분포의 마진이 향후 데이터 축적 후 명확해지면 threshold 재조정 가능

## 7. 후속 / 미결 (Follow-ups)

- **query rewriting 도입** (대안 C): 짧은 질문을 LLM 으로 표준 의문문으로 정규화한 뒤 의미 캐시 임베딩 입력으로 사용. 후보 모델: `gpt-4o-mini`, `temperature: 0`, `max_tokens` 짧게. `costGuard` + 실패 시 원본 질문으로 폴백
- **유저 데이터 누적**: PR #61 상태에서 `Q&A 의미 캐시` 로그를 충분 기간 수집해 분포 통계 (의미 동일군 vs 다름군의 distance 마진) 산출. 그 결과로 query rewriting 적용 후 threshold 재조정
- **재발 방지**: false positive 회귀를 막기 위해 운영에 "캐시 히트인데 답변이 의도와 어긋났다" 를 사용자 피드백으로 잡을 채널이 있으면 좋음. 현재는 로그만으로는 사용자 의도를 알 수 없으므로 한계가 있음

## 8. 참고 (References)

- 관련 PR
  - https://github.com/TaetaetaE01/youthfit/pull/58 — threshold 0.22 + LLM 결정성
  - https://github.com/TaetaetaE01/youthfit/pull/59 — QuestionNormalizer 끝부호 제거
  - https://github.com/TaetaetaE01/youthfit/pull/60 — prefix 임베딩 + threshold 0.30 (롤백 대상)
  - https://github.com/TaetaetaE01/youthfit/pull/61 — PR #60 revert + threshold 0.20
- 같은 도메인 인접 트러블슈팅
  - `docs/troubleshooting/2026-04-30-02-qna-relevance-threshold-tune-trouble.md` — RAG 관련 distance threshold 튜닝 (의미 캐시와는 다른 threshold)
  - `docs/troubleshooting/2026-05-02-qna-quality-design-flaws-trouble.md` — Q&A 답변 품질 관련 설계 결정
- 사용자 발언 인용 (결정적이었던 두 줄)
  - "아... 고쳐줘" — false positive 데이터 직후. 롤백 결정 트리거
  - "일단 이대로 가자 유저데이터로 일단 확인하고 유사도 그때 판단하자" — 의미 동일 캐시를 포기하고 데이터 누적 후 재판단하는 것에 대한 명시적 동의
