# 임베딩 모델 교체 실험(3-large) — 코퍼스 오염과 고정 임계값 왜곡, 두 함정과 비용 실측

- 작성일: 2026-07-04
- 작성자: TaetaetaE01
- 관련 커밋: 없음 (PR #176, #178 참고)
- 관련 PR: #176, #178
- 관련 모듈: `backend/eval`, `backend/ingestion`, `backend/rag`

## 한 줄 요약

> `text-embedding-3-small` 이 한국어 정답/비정답 청크를 구분하지 못하는 문제(#167)를
> 검증하려고 `text-embedding-3-large`(1536차원 축소) 로 재인덱싱해 같은 평가셋으로
> 비교했다. 재인덱싱 경로 차이로 코퍼스 자체가 바뀐 상태에서 비교했고, 고정
> distance 임계값으로 오탐률을 판정해 결과가 왜곡됐다. 코퍼스를 맞추고 임계값을
> 스캔으로 재계산해 공정한 비교를 확보했다.

## 1. 상황 (Context)

- 이슈 #167: `text-embedding-3-small` 이 한국어 질의에서 정답 청크와 비정답 청크의
  distance 를 잘 갈라내지 못한다. 정답/비정답 평균 distance 갭이 0.012 수준으로
  작아, distance 만으로는 근거 없는 질문을 걸러내기 어렵다.
- 우회책 두 가지를 먼저 평가셋 실험으로 검토했으나 모두 기각됐다.
  - #163 하이브리드 검색
  - #164 쿼리 재작성
- 1단계 대안으로 `text-embedding-3-large` 를 `dimensions=1536` 으로 축소해(스키마
  변경 없음) 같은 평가셋(73케이스 / 19정책)으로 재측정하기로 했다. 실험 설계는
  `docs/superpowers/specs/2026-07-03-embedding-model-experiment-design.md` 에 있다.
- 실험 인프라는 PR #176 에서 준비했다.
  - `--eval.mode=reindex` 플래그: 정책 단위로 `@Transactional` 내에서 기존 청크를
    delete 한 뒤 `AttachmentReindexService.reindexWithoutEvents` 를 호출. dry-run 이
    기본값이라 명시적으로 켜야 실제 삭제·재생성이 일어난다.
  - 임베딩 캐시 라벨을 실제 호출 모델(`OpenAiEmbeddingProperties.getModel()`)로
    기록하도록 수정 — 이전에는 평가셋 파일의 `embeddingModel` 필드를 라벨로 썼기
    때문에, env 로 모델을 바꿔 호출해도 이전 모델 라벨의 캐시를 재사용해 잘못된
    결과가 나올 수 있었다.
- 이 재인덱싱·재측정 과정에서 실험 설계 자체와는 별개로 두 가지 함정이 드러났다.

## 2. 원인 (Root Cause)

### 함정 1 — 코퍼스 오염 (측정 혼입변수)

- 3-large 로 재인덱싱하자 총 청크 수가 1,178 → 1,379(+201) 로 바뀌었다.
- 기존 인덱스는 두 개의 서로 다른 경로가 섞여 만들어진 상태였다.
  - 1차 인덱싱 경로(`RagIndexingEventListener`): 정책 본문 + enrichment 만 인덱싱.
  - 첨부 이벤트 경로: 이후 첨부 처리 시점에 추가로 붙는 첨부 청크. 전체 청크의
    44% 를 차지한다.
- 반면 `--eval.mode=reindex` 가 사용하는 `AttachmentReindexService` 의 content 조립은
  본문 + 선별 첨부 + enrichment 를 한 번에 일괄 적용한다. 그 결과 이전에는
  첨부 청크가 없던 일부 정책에 새로 첨부 청크가 포함됐다.
- 즉 모델을 3-large 로 바꾸는 동시에 코퍼스 구성도 함께 바뀌어, 기존 3-small
  baseline 리포트(1,178청크 기준)와 신규 3-large 리포트를 직접 비교하면 "모델
  차이"와 "코퍼스 차이"가 뒤섞여 어느 쪽이 원인인지 알 수 없는 상태였다.

### 함정 2 — 고정 threshold 의 negFP 왜곡

- NEGATIVE(근거 없는 질문) 오탐률 지표(top-1 distance ≤ 0.78 비율)가 3-small
  0.789 → 3-large 0.895 로, 얼핏 "악화"된 것처럼 보였다.
- 원인은 3-large 의 distance 분포 자체가 전반적으로 아래로 이동했기 때문이다.
  - 정답 청크 평균 distance: 0.455 → 0.351
  - 비정답 청크 평균 distance: 0.467 → 0.413
- 분포 전체가 이동한 상태에서 3-small 기준으로 고정된 절대 임계값 0.78 을 그대로
  적용하면 두 모델을 공정하게 비교할 수 없다. 이 리스크는 실험 설계 스펙 §6 에
  이미 예고돼 있었다.

## 3. 고려한 대안 (Alternatives)

| 항목 | 대안 | 채택 여부 | 사유 |
|---|---|---|---|
| 재인덱싱 경로 | A. body 만 재인덱싱(1차 인덱싱 경로 재사용) | 기각 | 첨부 청크 44% 가 코퍼스에서 유실됨 |
| | B. `AttachmentReindexService.reindex` 그대로 사용 | 기각 | `PolicyAttachmentReindexedEvent` 가 발행돼 가이드·룰 LLM 재생성 리스너를 깨움 — 실험용 재인덱싱이 불필요한 LLM 생성을 유발 |
| | C. 이벤트 미발행 변형 `reindexWithoutEvents` 오버로드 추출 | **채택** | 기존 `reindex` 경로 동작은 그대로 두고, 실험 전용 경로만 분리. 회귀 테스트로 기존 동작 고정 |
| 공정 비교 방법 | A. 기존 3-small baseline 리포트(1,178청크)와 3-large 리포트(1,379청크)를 그대로 비교 | 기각 | 모델 차이와 코퍼스 차이가 뒤섞인 혼입변수 상태 |
| | B. 3-small 도 동일한 `reindexWithoutEvents` 경로로 재인덱싱해 동일 코퍼스로 재측정 | **채택** | 추가 비용 약 $0.009 로 공정 비교 확보 |
| 오탐률 판정 기준 | A. 실험 설계 스펙 §5 의 기계적 기준(distance 갭 ≥0.10, negFP ≤0.40 @고정 0.78) 그대로 적용 | 기각(문자 그대로는 미달) | distance 분포 자체가 이동해 고정 절대 임계값 비교가 무의미해짐 |
| | B. 케이스별 top-1 distance 로 threshold 스캔(0.200~0.895, 0.005 간격) 후 재판정 | **채택** | positive 통과율 + NEGATIVE 차단율 합이 최대가 되는 지점 기준으로 각 모델의 실제 분리 성능을 계산 |

## 4. 선택과 이유 (Decision)

- 재인덱싱 경로는 실험만을 위해 기존 이벤트 발행 경로를 건드리지 않도록
  `reindexWithoutEvents` 를 별도로 뽑아 사용했다. 실험 도구가 운영 경로(가이드·룰
  재생성)에 부수효과를 일으키지 않아야 한다는 원칙을 지켰다.
- 3-small 도 같은 경로로 재인덱싱해 동일 코퍼스(1,379청크)를 만든 뒤 재측정했다.
  추가 비용이 크지 않고(§6 비용 실측 참고), 이렇게 하지 않으면 실험 결과 자체를
  신뢰할 수 없기 때문이다.
- 오탐률은 스펙에 정의된 고정 임계값 기준을 폐기하지 않되, 그 기준이 전제하는
  "두 모델의 distance 분포가 같은 스케일"이라는 가정이 깨졌으므로 threshold 를
  스캔해 각 모델의 최적 분리 지점을 직접 계산하는 방식을 병행했다. 최종 판단은
  스캔 결과와 랭킹 지표(recall, MRR) 개선을 함께 근거로 삼았다.

## 5. 해결 (Solution)

- **코퍼스 오염 대응**: `--eval.mode=reindex` 로 3-small 도 3-large 와 동일하게
  재인덱싱해 1,379청크로 코퍼스를 맞춘 뒤 재측정. 실험 종료 후 로컬 DB 는 다시
  3-small 상태로 원복했다(재인덱싱 자체가 되돌리기 가능한 조작).
- **고정 threshold 왜곡 대응**: 평가 리포트의 케이스별 top-1 distance 를 이용해
  threshold 를 0.200 ~ 0.895 구간에서 0.005 간격으로 스캔하고, positive 통과율과
  NEGATIVE 차단율의 합이 최대가 되는 지점을 모델별 최적 threshold 로 계산했다.
  - 3-small 최적 threshold 0.56 → positive 통과율 94.4% / NEGATIVE 차단율 94.7%
  - 3-large 최적 threshold 0.48 → positive 통과율 94.4% / NEGATIVE 차단율 **100%**
  - 즉 재보정을 전제하면 3-large 는 근거 없는 질문을 전량 차단할 수 있지만,
    3-small 은 어떤 threshold 를 잡아도 100% 차단이 불가능했다.
- **부수 조치**: 재인덱싱 실행 중 `llm_cost_bucket_module_check` 제약 위반이
  발생해(§7 참고) 로컬 DB 에는 임시로 `ALTER` 를 적용해 실험을 계속 진행했다.

## 6. 검증 (Result)

- 재인덱싱: 84/84 정책 성공 (3-large 82초, 3-small 66초).
- 평가: 73/73 케이스 정상 처리, STALE/NO_CHUNKS 0건.
- 리포트 파일 (PR #178):
  - `backend/eval/reports/20260703-235346-3large-experiment.json`
  - `backend/eval/reports/20260703-235602-3small-samecorpus.json`

동일 코퍼스(1,379청크) 기준 최종 결과 (73케이스):

| 지표 | 3-small | 3-large@1536 |
|---|---|---|
| recall@1 | 0.759 | 0.833 |
| recall@3 | 0.833 | 0.907 |
| recall@5 | 0.889 | 0.944 |
| recall@10 | 0.944 | 0.944 |
| MRR@10 | 0.812 | 0.877 |
| COLLOQUIAL R@1 | 0.750 | 0.875 |
| distance 갭 (정답 평균 - 비정답 평균) | 0.012 | 0.052 |

비용 실측 (`llm_cost_bucket`, 2026-07-03 재인덱싱 84정책 / 1,379청크 기준):

| 모델 | 토큰 | 비용(전체) | 정책 1건당 |
|---|---|---|---|
| 3-small | 450,541 tokens | $0.009010 | $0.000107 (약 0.15원) |
| 3-large | 452,272 tokens (평가 질의 임베딩 73건 포함) | $0.058791 | 약 $0.000697 (약 0.96원) |

- 단가비는 6.5배(0.02 vs 0.13 USD/1M tokens)지만 정책당 절대 차는 약 0.8원 수준.
  환율은 1,380원/USD 가정.
- 실험 설계 스펙 §5 의 기계적 기준(distance 갭 ≥0.10, negFP ≤0.40 @고정 0.78)은
  문자 그대로는 미달이었으나, threshold 스캔 결과(3-large 100% NEGATIVE 차단
  가능)와 전 구간 랭킹 지표 개선을 근거로 이슈 #167 코멘트에 **교체 추진 권고**를
  남겼다. prod 적용 여부는 별도 결정 대기.

## 7. 후속 / 미결 (Follow-ups)

- prod 적용은 아직 결정되지 않았다. 필요한 후속 작업:
  - 임베딩 모델 env 기본값 변경
  - QnA 판정 threshold 재보정(0.78 → 약 0.48 근처, #166 의미 캐시 threshold 작업과
    함께 검토 필요)
  - 배포 시점 재인덱싱 런북 작성
- 이슈 #177: `llm_cost_bucket_module_check` DB check 제약에 `LlmModule.ATTACHMENT_GATE`
  가 누락돼 있다(enum 에는 존재). 이 상태로는 첨부 게이트 비용 집계가 유실된다.
  로컬은 `ALTER` 로 임시 조치. → **근본 수정 완료**:
  `backend/src/main/resources/sql/2026-07-04-llm-cost-bucket-module-check-attachment-gate.sql` (#177).
- 기존 1,178청크 baseline 리포트들(`20260703-100826` 등)은 이번에 밝혀진 구
  코퍼스 기준이므로, 이후 다른 실험과 비교할 때 코퍼스 청크 수를 먼저 확인해야
  한다.

## 8. 참고 (References)

- 이슈 #167 (실험 결과 코멘트), #177
- PR #176 (`--eval.mode=reindex` 인프라), #178 (실험 결과)
- 실험 설계 스펙 · 플랜: `docs/superpowers/specs/2026-07-03-embedding-model-experiment-design.md`,
  `docs/superpowers/plans/2026-07-03-embedding-model-experiment*.md`
- 관련 모듈: `backend/eval`, `backend/ingestion`, `backend/rag`
