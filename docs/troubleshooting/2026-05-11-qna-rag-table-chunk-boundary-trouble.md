# Q&A 표 데이터 RAG retrieval 실패 — DocumentChunker 의 500자 hard cut 이 표 boundary 를 깬 문제

- 작성일: 2026-05-11
- 작성자: TaetaetaE01
- 관련 커밋: `f958965` (`feat(qna): Q&A 답변 풍부도 강화 (마크다운/푸터/follow-up/출처 펼치기) + RAG retrieval 개선 (#88)`)
- 관련 PR: #88
- 관련 모듈: `backend/rag`, `backend/qna`

## 한 줄 요약

> 답변 풍부도 강화(PR-1~5) 수동 검증 중 7번 정책(청년내일저축계좌)에서 "어떤 통장이 중복수혜인지 리스트 알려줘" 같은 표 기반 질문이 fallback 으로 떨어지는 현상 발견. 디버깅 결과 PDF 의 표가 한 단락(`\n\n` 미포함)으로 추출되어 `DocumentChunker.splitBySize` 의 500 자 hard cut 에 의해 헤더/의미 단위가 분리됨이 근본 원인. 즉시 완화로 retrieval `top-K 5→10` 확대만 적용하고, 표 인식 청킹은 별도 spec 으로 분리.

## 1. 상황 (Context)

- 작업: PR-1~5 (Q&A 답변 풍부도 강화) 머지 직전 도커 환경에서 수동 검증 진행.
- 검증 시나리오: 7번 정책(청년내일저축계좌) 상세 페이지에서 다양한 질문 입력.
- 증상:
  - "어떤 통장이 중복수혜인지 리스트 알려줘" → fallback (`해당 정책 원문에 관련 내용이 명시되어 있지 않습니다…`)
  - "안되는 통장 리스트 알려줘" → fallback
  - "중복수혜 안되는 통장 리스트 알려줘" → fallback
  - "중복수혜안되는 정책도 있어?" → 답변은 나왔으나 **사실 오답** — `디딤씨앗통장`/`꿈나래통장`을 "중복 불가"로 잘못 단언 (실제로는 "중복 참여 가능" 사업 리스트의 항목)
- 사용자 의문: PDF 본문에 `중복 참여 불가 사업` 표가 있는데도 못 찾는 게 이상하다는 지적. "임베딩 거리가 그냥 멀어서?" 가 아니라 chunking 로직 문제 가능성 제기.
- 영향: 표·리스트 형식의 데이터를 가진 모든 정책에서 retrieval 신뢰성 저하. 단순 retrieval 실패가 아니라 LLM 환각으로 이어져 **답변 정확성까지 위협**.

## 2. 원인 (Root Cause)

### 데이터 자체는 정확히 인덱싱돼 있음

```sql
-- 7번 정책 chunk 60~65 head/tail
chunk #60 (p.33-34, 499자) "123 중복관리 대상사업…"  -- 자연어 단락
chunk #61 (p.34,    94자) "목적으로 지자체…"           -- 짧은 단락
chunk #62 (p.34-35, 500자) "13| 중복 참여 불가 사업번호 사업구분 시행기관1 청년재직자내일채움공제…"
                            -- 한계 도달, 23번 행 도중 cut
chunk #63 (p.35,    146자) "기도24 열혈청년 패키지사업 충청남도25 반짝자립통장…"
                            -- 헤더 잃음, 무슨 표인지 알 수 없음
chunk #64 (p.35-36, 500자) "14* 이 외에도 … | 중복 참여 가능 사업번호 사업구분 시행기관30 …"
                            -- 새 표(중복 가능) 시작
```

표 1개("중복 참여 불가 사업")가 **#62 + #63 두 청크로 split**, 그 후 인접 표("중복 참여 가능 사업")가 #64 시작.

### 근본 원인: `DocumentChunker.splitBySize` 의 무조건 hard cut

`backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java:216-223`

```java
private void splitBySize(int start, int end, List<int[]> ranges) {
    int cursor = start;
    while (cursor < end) {
        int next = Math.min(cursor + maxChunkSize, end);
        ranges.add(new int[]{cursor, next});
        cursor = next;
    }
}
```

청킹 흐름:
1. `paragraphAwareSplit` 이 단락(`\n\n`)을 기준으로 청크 누적.
2. 단락 하나가 `maxChunkSize = 500`(`DocumentChunker.java:16`)을 초과하면 `splitBySize` 위임.
3. `splitBySize` 는 **`cursor + 500` 으로 무조건 잘라냄** — 표의 행 boundary, 번호 체계, 의미 단위를 전혀 고려 안 함.

PDF 추출 단계에서 표가 줄바꿈을 잃고 한 단락(1000 자+)으로 들어오면 이 분기에 떨어진다. 결과:
- 첫 청크에만 표 헤더 존재 → 후속 청크는 컨텍스트 잃음
- vector embedding 상 후속 청크의 의미가 자연어 query 와 매우 멀어짐
- top-K retrieval 에서 영영 못 잡힘

### LLM 환각이 일어나는 이유

- chunk #60 (p.33-34) — "중복관리 대상사업" 헤더의 자연어 단락. 본문 중간에 "디딤씨앗통장" 키워드가 등장 (전체 499 자라 LEFT 80자에는 안 보이지만 ILIKE 매칭됨).
- 이 청크는 retrieved.
- LLM 입장: 헤더가 "중복관리/불가" 맥락 + 본문에 "디딤씨앗통장" 등장 → "디딤씨앗통장 = 중복 불가" 결합 추론.
- 진짜 분류 정보가 있는 chunk #64 ("중복 참여 가능 사업…디딤씨앗통장…") 는 retrieved 안 됨.
- → 단순 retrieval 실패가 아니라 **chunking 이 의미 단위를 깨뜨려 LLM 이 부분 정보로 잘못된 결론을 도출**.

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| **A. retrieval `top-K 5→10` 확대** | 코드 1줄, 즉시 적용. 인근 자연어 청크의 retrieval 안정성 ↑ | 표 청크 자체는 여전히 못 잡음 (검증 결과 #62, #64 모두 top-10 미포함). 임시 완화 수준 |
| **A2. `top-K` 를 더 늘림 (15~25)** | 약간 더 많은 후보 | 표 청크의 distance 가 본질적으로 멀어 어느 K 든 미포함 가능성. 노이즈만 증가, LLM 컨텍스트/비용만 늘어남 |
| **B. 키워드 부스트** | vector 결과에 키워드 매칭 청크를 union — 표 청크 회수 가능 | 코드 ~50 줄 + 테스트. 단어 split / stopword 처리 / 노이즈 임계값 등 결정사항이 많아 별도 spec/plan 사이클 가치 |
| **C. 인덱싱 개선 (chunker 표 인식)** | 근본 해결, 모든 정책에 효과 | 코드 100 줄+. 모든 정책 재인덱싱 필요(OpenAI embedding 비용). source_hash 변경으로 의미 캐시 자연 만료. 회귀 검증 필요 |
| **D. 일단 정리 + 별도 spec 분리** | 이번 PR 범위 유지, 다음 사이클로 안전 분리 | 표 retrieval 한계가 즉시 해결되지 않음 |

## 4. 선택과 이유 (Decision)

- **채택**: A (top-K 5→10) 즉시 적용 + D (별도 spec 분리)
- **결정의 핵심 근거**:
  - 이번 PR 의 목적은 "답변 풍부도 강화"이지 "RAG retrieval 품질 개선" 이 아님 — 한 PR 안에 두 작업을 묶는 것은 plan 의도 위배 (단일 책임).
  - B/C 는 결정사항이 많아 새 brainstorming → spec → plan 사이클 가치가 충분.
  - A 는 비용 작고(코드 1줄 + 테스트 fixture 4곳) 회귀 위험 낮으면서 표 청크가 아닌 일반 자연어 청크의 retrieval 신뢰성은 실제로 ↑.
- **트레이드오프로 받아들인 것**: 표 기반 질문에 대해 fallback / 환각 답변이 한동안 남는다. 사용자 가치 손실 있으나 "공식 문의처 안내" fallback 으로 graceful degrade.
- **가역성**: A 는 코드 한 줄이라 단순 reset. B/C 는 별도 PR 예정.
- **재검토 트리거**: 표 retrieval 실패 사용자 보고가 누적되거나, `qna_history` 의 fallback 비율이 운영 측정값으로 임계 초과 시.

## 5. 해결 (Solution)

### 즉시 완화 (PR #88 내 commit `ac6926e` 부분)

`backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java:21`

```java
- private static final int DEFAULT_TOP_K = 5;
+ private static final int DEFAULT_TOP_K = 10;
```

테스트 fixture 4곳 동기화:
`backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java` — `findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(5))` → `eq(10)` (replace_all).

### 별도 spec 분리 (PR #88 내 commit `3c350df` 부분)

다음 brainstorming 사이클에서 옵션 결정·구현 plan 단계부터 시작 가능하도록 디버깅 결과·근본 원인·옵션 후보(F1~F5)·결정 사항을 정리한 pre-spec note 작성:

- `docs/superpowers/specs/2026-05-11-rag-table-aware-chunking.md` (작성 시점 파일명: `v1-rag-table-aware-chunking.md` → 같은 세션 후속 정리에서 날짜 접두사로 rename)

### 부수 효과

- 검증 과정에서 7번 정책의 의미 캐시(`qna_question_cache`) 14건과 Redis 캐시 일부를 fallback 답변 재현 차단을 위해 수동 삭제. 운영 환경엔 영향 없음 (도커 로컬만).
- DB 마이그레이션 자체는 답변 풍부도 강화의 별개 변경(`2026-05-11-qna-follow-ups-column.sql`)이라 본 트러블슈팅과는 무관.

## 6. 검증 (Result)

### top-K 5→10 의 실제 효과

`qna_history` 의 검증 query 결과:

| query | retrieved pages | 정답 표 청크 (#62, #64) 포함? |
|---|---|---|
| "중복수혜안되는 정책도 있어?" (id=80) | 33-34, 36-37, 67-68, 81, 110, 123, 125-126, 181, 182, 190 | ❌ 둘 다 미포함 |
| "안되는 통장 리스트 알려줘" (id=81) | (fallback) | ❌ retrieval 결과 자체 없음 |
| "중복수혜 안되는 통장 리스트 알려줘" (id=82) | (fallback) | ❌ retrieval 결과 자체 없음 |

→ **A 단독으로는 표 청크 retrieval 문제 해결 못함을 확정**. 이건 가설 검증이지 완전한 fix 가 아님. 사용자에게 명시적으로 보고하고 D(별도 spec) 진행.

### 회귀 위험과 모니터링 포인트

- top-K 가 2배가 되어 LLM 컨텍스트 길이 약 2배. OpenAI 토큰 비용 ↑. `LlmCallRecorded` 메트릭으로 추적 가능.
- 비표 정책(자연어 본문 위주)에서는 top-5 → top-10 으로 noise chunk 추가될 수 있음. `qna_cache_lookup_log` 의 distance 분포로 추적.
- 회귀 모니터링 지표:
  - `qna_history` 의 fallback 비율
  - `LlmCallRecorded` 의 prompt_tokens 평균 (top-K 확대 영향)

## 7. 후속 / 미결 (Follow-ups)

- **`docs/superpowers/specs/2026-05-11-rag-table-aware-chunking.md` 기반 brainstorming 진행 필요**.
  - 결정 사항: F1(maxChunkSize 확대) / F2(표 패턴 인식) / F3(overlap chunking) / F4(줄 단위 보존) / F5(PDF extractor 개선) 중 단독 또는 조합
  - 재인덱싱 비용 추정 (정책 N개 × 청크 평균 × OpenAI text-embedding-3-small 단가)
  - source_hash 변경에 따른 의미 캐시 무효화 영향 평가
- **재발 방지 가드레일 후보**:
  - `DocumentChunkerTest` 에 표 입력에 대한 boundary 보존 테스트 추가 (현재 청커는 표 인식 책임 없음)
  - `RagSearchService` 에 distance 분포 percentile 로깅 추가 (이미 INFO log 는 있음)
  - 운영 dashboard 에 fallback 비율을 정책별로 분해해 표 위주 정책 우선 식별

## 8. 참고 (References)

- 관련 spec: `docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md` (이번 PR 의 모체)
- 관련 plan: `docs/superpowers/plans/DONE_2026-05-11-qna-rich-answer.md`
- 후속 작업 spec: `docs/superpowers/specs/2026-05-11-rag-table-aware-chunking.md`
- 관련 코드:
  - `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java:216-223` (`splitBySize`)
  - `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java:21` (`DEFAULT_TOP_K`)
  - `backend/src/main/resources/application.yml:92` (`relevance-distance-threshold`)
- 사용자 인사이트: 단순 "텍스트 임베딩 거리가 멀다" 가설을 의심하고 "그냥 텍스트간 청킹이 멀어서 그런 거야?" 라고 chunking 로직 자체를 의심한 사용자 메시지가 결정적이었음. 표면 가설(임베딩 한계)에서 실제 원인(splitBySize hard cut) 으로 전환하는 데 직접적 트리거.
