# RAG retrieval 자동 평가셋 구축 설계

- **날짜**: 2026-07-02
- **이슈**: #162 (선행 조건: #163 하이브리드 실험, #164 쿼리 재작성 실험, #166 캐시 threshold, #167 임베딩 교체)
- **상태**: 설계 승인 — 구현 계획 대기

## 1. 문제와 목표

RAG Q&A 개선 실험(#163, #164, #167)이 전부 "켜면 좋아지는가"를 판단할 근거가 없다.
text-embedding-3-small 은 한국어에서 관련/무관 청크의 distance 분포가 0.7~0.75 대에
겹쳐 있어, 파라미터 변경 효과를 체감이 아닌 수치로 비교할 기준이 필요하다.

**목표**: retrieval 품질을 반복 측정할 수 있는 (1) 평가셋, (2) 지표 산출 러너,
(3) 파라미터 조합 비교 리포트를 만든다. 답변(생성) 품질 평가는 범위 밖 — retrieval
랭킹 품질만 다룬다.

## 2. 확정된 핵심 결정

| 결정 | 선택 | 근거 |
|---|---|---|
| 평가셋 생성 | LLM 역질문 생성 + 수동 검수 | 적은 노력으로 80~120쌍 확보, 생성기가 코드로 남아 #167 재인덱싱 때 재사용 |
| 러너 형태 | `@Profile("eval")` ApplicationRunner | 인증 불필요, 실제 검색 코드 직접 호출(복제 없음), 기존 ApplicationRunner 컨벤션 정합 |
| 정답 앵커 | 근거 스니펫 포함 판정 | 청크 PK 는 재인덱싱마다 바뀜. 스니펫은 청킹 변경·재인덱싱·임베딩 교체에도 생존 |
| 결과 저장 | JSON 리포트 + 콘솔 요약 | 리포트를 git 커밋해 실험 간 수치 비교·회귀 추적 |

## 3. 모듈 구조

`com.youthfit.eval` 최상위 패키지 신설, 전체 `@Profile("eval")` 가드.

- `rag` 안에 두지 않는 이유: rewrite-on 시나리오가 `qna` 의 `QueryRewriter` 포트를
  사용해야 하므로, rag 에 두면 rag→qna 역의존이 생긴다. `eval → rag, qna` 단방향 유지.
- prod/기본 프로파일에서는 빈이 아예 뜨지 않는다. 핫패스 영향 0.
- 모듈 추가이므로 `docs/ARCHITECTURE.md` 에 eval 모듈 항목을 추가한다 (dev 전용 도구임을 명시).

```
eval/
├── EvalRunner.java              # ApplicationRunner, --eval.mode=generate|run 분기
├── generate/                    # 평가셋 후보 생성
│   ├── EvalCaseGenerator.java   # 정책→청크 샘플→LLM 역질문
│   └── NegativeQuestionPool.java
├── run/                         # 평가 실행
│   ├── EvalScenario.java        # 시나리오명→HybridSearchOverrides 매핑
│   ├── RetrievalEvaluator.java  # 케이스 실행·판정
│   ├── EvalMetricsCalculator.java  # recall@k·MRR·distance 갭 (순수 함수)
│   └── QueryEmbeddingFileCache.java
├── dataset/                     # 평가셋 로드·검증 (STALE 감지)
└── report/                      # JSON 리포트 작성·콘솔 요약
```

## 4. 평가셋 스키마

파일: `backend/eval/retrieval-evalset.json` (jar 밖, git 커밋 대상)

```json
{
  "version": 1,
  "embeddingModel": "text-embedding-3-small",
  "cases": [
    {
      "id": "p123-q1",
      "policyId": 123,
      "policyTitle": "청년 월세 지원",
      "question": "재학생도 신청할 수 있나요?",
      "questionType": "KEYWORD",
      "expectedSnippets": ["대학 재학생은 신청 대상에서 제외됩니다"],
      "notes": "검수 코멘트 (선택)"
    }
  ]
}
```

- **questionType 3종** (유형별 지표를 따로 집계해 실험별 효과를 분리 판정):
  - `KEYWORD`: 정책명·기관명·금액 등 정확 매칭형 — #163 하이브리드가 노리는 케이스
  - `COLLOQUIAL`: 구어체·짧은 질문 — #164 쿼리 재작성이 노리는 케이스
  - `NEGATIVE`: 이 정책 원문에 근거가 **없어야 하는** 질문. `expectedSnippets` 빈 배열. 오탐 측정용
- **정답 판정**: 검색된 청크 content 와 스니펫 양쪽을 정규화(연속 공백·개행 → 단일
  공백, trim) 후 포함(contains) 매칭. 스니펫은 원문 발췌 1~2문장.
- **STALE 감지**: 검색은 policyId 스코프이므로 시드 재구축으로 id 가 바뀌면 조용히
  엉뚱한 정책을 평가하게 된다. 로드 시 DB 의 정책 title 과 `policyTitle` 을 대조해
  불일치 케이스는 `STALE` 로 표시하고 지표에서 제외한다.

## 5. generate 모드

실행: `SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=generate'`
(로컬 compose DB + `OPENAI_API_KEY` 필요)

1. 청크가 존재하는 정책 조회. 기본은 소스별 최대 10건 샘플링
   (`--eval.max-per-source=10`, 3소스 × 10 = 최대 30건). `--eval.policy-ids=…` 로
   명시 지정도 가능
2. 정책당 대표 청크 최대 3개 샘플링 (chunkIndex 앞쪽 우선 — 자격·금액 등 핵심이 보통 앞)
3. **정책당 LLM 1회 호출** (gpt-4o-mini): 질문 3개(KEYWORD 2 + COLLOQUIAL 1)와 근거
   발췌를 JSON 역생성. 스니펫은 **청크 원문 그대로 발췌**하도록 지시하고, 생성 직후
   코드에서 청크 content 포함 여부를 검증 — 불합격 스니펫은 제외(환각 방지)
4. NEGATIVE 는 LLM 호출 없이 공용 질문 풀에서 정책당 1개 배정
5. 출력: `backend/eval/retrieval-evalset.candidate.json` → **사람 검수 후**
   `retrieval-evalset.json` 으로 확정·커밋

**비용 방어** (`.claude/rules/common.md` 의 LLM 비용 방어 규칙 대응):
- 기본 dry-run: 예상 호출 횟수만 출력하고 종료. `--eval.confirm=true` 일 때만 실제 호출
- gpt-4o-mini + max_tokens 제한, candidate 에 이미 있는 정책은 스킵(증분 생성)
- 예상 규모: 정책 25~30건 × 1회 ≈ 30회 미만, 1회성

**규모 목표**: 정책 25~30건(YOUTH_CENTER/YOUTH_SEOUL/BOKJIRO 소스 골고루) × 4질문
(KEYWORD 2·COLLOQUIAL 1·NEGATIVE 1) ≈ **100~120 케이스**. 69건 전부를 쓰지 않는
이유: 수동 검수 부담을 감당 가능한 선으로 유지.

## 6. run 모드

실행: `SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=run --eval.scenarios=baseline,hybrid-on'`

### 시나리오 매트릭스

시나리오명 → `HybridSearchOverrides` 매핑. 초기 4종:

| 시나리오 | 설정 | 대응 이슈 |
|---|---|---|
| `baseline` | 현재 운영 기본값 그대로 (overrides 없음) | 기준선 |
| `hybrid-on` | 하이브리드 검색 ON | #163 |
| `boost-off` | 키워드 부스트 OFF (부스트 기여도 분리) | 보조 |
| `rewrite-on` | `QueryRewriter` 로 질문 변환 후 재임베딩·검색 | #164 |

- `HybridSearchOverrides` 에 하이브리드 ON/OFF 필드가 없으면 이번 작업에서 추가한다
  (keywordBoostEnabled·maxKeywords 는 존재 확인됨).
- 검색은 `RagSearchService.searchRelevantChunksWithTrace(command, embedding, overrides)`
  직접 호출. trace 가 벡터/트라이그램/병합 랭킹·distance·사용 키워드·tookMs 를 반환하므로
  지표 산출에 필요한 데이터가 전부 나온다.
- 한 번의 run 에서 여러 시나리오 실행 시 **질문 임베딩을 재사용** — 시나리오 추가 비용 0.
  (rewrite-on 만 재작성 질문 기준 임베딩 별도)

### 임베딩 캐시

`backend/eval/cache/embeddings-<model>.json` 에 `sha256(질문 텍스트) → 벡터` 저장.
첫 run 만 임베딩 API 호출(≈120회), 이후 run 은 비용 0. 캐시 파일은 git 커밋하지
않는다(.gitignore).

### 지표 (전체 + questionType 별 집계)

| 지표 | 정의 | 용도 |
|---|---|---|
| recall@k (k=1,3,5,10) | 정답 스니펫 포함 청크가 top-k 내 존재하는 케이스 비율 | 기본 품질 |
| MRR@10 | 첫 정답 청크 순위 역수의 평균 | 랭킹 품질 |
| distance 갭 | 정답 청크 distance 평균 vs 비정답 top-5 distance 평균 | #167 의 "0.7~0.75 겹침" 추적 |
| NEGATIVE 오탐률 | NEGATIVE 케이스에서 top-1 distance ≤ 0.78 인 비율 | 근거 없는데 잡는 비율 |
| tookMs 평균 | 케이스당 검색 소요 시간 | #164 지연 판정 |

- recall@k·MRR·distance 갭은 KEYWORD + COLLOQUIAL 케이스만 집계한다. NEGATIVE 는
  오탐률 전용이며 정답이 없으므로 recall 계열에서 제외.
- 0.78 은 QnA 의 `relevance-distance-threshold` 현행값. RAG 검색 자체에는 distance
  컷오프가 없으므로(순위만 산출) 오탐 판정에만 QnA 임계값을 차용한다.

### 리포트

- `backend/eval/reports/<yyyyMMdd-HHmmss>-<label>.json`: 실행 설정 스냅샷(trace 의
  EffectiveConfig), 시나리오별 집계 지표, 케이스별 상세(순위·distance·매칭 청크)
- 콘솔: 시나리오 × 지표 요약 테이블
- 리포트 파일은 실험 채택 시 git 커밋해 #163~#167 판정 근거로 남긴다

## 7. 에러 처리

| 상황 | 처리 |
|---|---|
| 임베딩 API 실패 | 해당 케이스 `SKIPPED` 기록 후 계속 |
| policyId-title 불일치 | `STALE` 기록, 지표 제외 |
| 정책 청크 0건 | `NO_CHUNKS` 기록, 지표 제외 |
| 성공 케이스 < 90% | 콘솔 경고 (평가셋 정비 신호) |
| generate 중 LLM 실패 | 해당 정책 스킵, 마지막에 실패 목록 출력 |

## 8. 테스트 전략

- **단위**: `EvalMetricsCalculator`(recall·MRR·distance 갭), 스니펫 정규화 매칭,
  시나리오→Overrides 매핑 — 순수 함수로 분리해 커버
- **통합**: Testcontainers(pgvector) + mock `EmbeddingProvider` 로 케이스 2~3개짜리
  스모크 1건 (`AdminRagPreviewIntegrationTest` 의 시딩 패턴 재사용)
- generate 모드의 LLM 호출부는 포트 뒤에 숨겨 스텁으로 검증

## 9. 범위 밖 (후속)

- 답변 생성 품질(LLM-as-judge) 평가 — retrieval 만 다룬다
- 임베딩 모델 교체 자체(#167) — 이 러너로 측정만 가능하게 함
- 의미 캐시 threshold 실측(#166) — 러너 확장으로 대응 가능하나 별도 이슈
- CI 통합 — 비용 문제로 수동 실행 도구로 유지
