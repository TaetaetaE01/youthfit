# RAG Query Rewriting 디자인

> 작성일: 2026-05-16
> 작성자: TaetaetaE01 (with Claude)
> 모듈: `qna`, `rag`
> 상태: design
> 관련 작업: [`2026-05-15-rag-hybrid-search-design.md`](2026-05-15-rag-hybrid-search-design.md) (선행 완료)

## 1. 배경

Hybrid 검색 (벡터 + pg_trgm) 도입 후에도 retrieval 이 실패하는 케이스가 발견됨. 진단으로 확인된 대표 케이스:

**질문**: "근로사업소득이 작년이 기준이야 올해가 기준이야?" (정책 7 = 청년내일저축계좌)
**기대 답변**: "최근 3개월 평균 근로·사업소득 기준"
**실제 결과**: "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다" (fallback)

원인 분석:
- 정답 청크는 인덱스에 **존재** (`청크 287, 166, 168, 278` 등 "3개월 평균 근로·사업소득" 명시)
- 그러나 사용자 표현 "작년/올해" vs 원문 표현 "직전/최근/3개월 평균" 의 **의미 갭**
- 벡터 임베딩이 이 의미 동치성을 못 잡음
- "작년", "올해" 단어가 정책 7번 PDF 전체에 **0건** → trigram 매칭도 도움 안 됨
- top-10 안에 정답 청크 못 들어오거나, 들어와도 `relevanceDistanceThreshold=0.78` 컷에 걸려 fallback

Hybrid 검색은 "고유명사·코드 매칭" 시나리오에 강하지만, "사용자 표현 ↔ 원문 표현 의미 갭" 시나리오는 해결 못 함. 본 작업은 이를 보완하기 위해 **LLM 기반 query rewriting** 을 도입한다.

## 2. 목표 / 비목표

### 목표
- 사용자 질문을 LLM 으로 정책 도메인 표준 용어로 자동 재작성
- 재작성된 query 로 임베딩·hybrid 검색 수행
- Feature flag (`rag.query-rewrite.enabled`) 로 점진 출시·즉시 롤백
- 기존 캐시·CostGuard 와 호환

### 비목표 (out of scope)
- A-2 multi-query (여러 변형 질문 생성·합집합)
- A-3 HyDE (가상 답변 기반 검색)
- Heuristic 사전 분류 (질문 패턴으로 rewrite 필요성 판단)
- 도메인 용어 사전 (synonym dictionary) 관리 시스템
- 사용자에게 rewrite 결과 노출 UI
- rewrite 품질 평가 인프라 (별도 후속 작업)

## 3. 핵심 결정사항

| 결정 항목 | 선택 | 근거 |
|---------|------|------|
| 변형 | **A-1 단일 rewrite** | 구현 단순, 응답 시간 예측 가능, 비용 ~$0.0001/회 |
| 트리거 조건 | **모든 질문에 항상 적용** | 일관된 UX, 캐시 hit 시 자동 skip 되므로 실비용 제한적 |
| 캐시 키 | **원래 질문 기준** (기존 유지) | 캐시 hit 시 rewrite 자체도 skip |
| Rewrite 컨텍스트 | **사용자 질문 + 정책 title** | 정책 도메인 용어로 변환 유도 |
| 답변 LLM 컨텍스트 | **원래 질문만 사용 (rewrite 결과는 검색용으로만)** | 답변의 자연스러움 보존, 단순화, hallucination 위험 최소화 |
| LLM 모델 | **gpt-4o-mini** (기존 QnA 와 동일) | 비용·속도 균형 |
| Feature flag | **`rag.query-rewrite.enabled`** (기본 OFF) | 안전 머지, 환경변수만으로 출시·롤백 |

## 4. 아키텍처

### 4.1 호출 흐름

```
QnaService.processQuestion()
   ① exact 캐시 lookup       ← 변경 없음 (캐시 hit 시 rewrite skip)
   ② 임베딩 1회 (원래 질문)  ← 변경 없음
   ③ semantic 캐시 lookup    ← 변경 없음 (cache hit 시 rewrite skip)
   ④ [신규 분기] rag.query-rewrite.enabled = true:
         ├─ QueryRewriter.rewrite(question, policyTitle) → rewrittenQuery
         ├─ embeddingProvider.embed(rewrittenQuery) → 재계산 임베딩 (추가 1회)
         └─ ragSearchService.searchRelevantChunks(SearchChunksCommand(policyId, rewrittenQuery), 재계산 임베딩)
      [기존 path] enabled = false:
         └─ ragSearchService.searchRelevantChunks(SearchChunksCommand(policyId, 원래 질문), 원래 임베딩)
   ⑤ LLM 답변 생성           ← system prompt 에는 원래 질문만 전달 (rewrite 결과 미노출)
   ⑥ 캐시 저장               ← 캐시 키는 원래 질문 (변경 없음)
```

### 4.2 컴포넌트 분담

| 컴포넌트 | 위치 | 책임 |
|---------|------|------|
| `QueryRewriter` | `qna/application/port` (interface) | LLM 으로 query 재작성하는 도메인 포트 |
| `OpenAiQueryRewriter` | `qna/infrastructure/external` | OpenAI Chat API 호출 구현체 |
| `QueryRewriteProperties` | `qna/infrastructure/config` | `rag.query-rewrite.*` 설정 바인딩 |
| `QnaService` | `qna/application/service` | feature flag 분기, rewrite 호출, 재임베딩 호출 |

### 4.3 의존 방향

`qna` 모듈이 `rag` 모듈에 의존하는 기존 구조 유지. `QueryRewriter` 는 `qna` 모듈 안에 둠 — Q&A 흐름에서만 사용되고, 일반 RAG 검색 (예: 가이드 생성) 에는 영향 없음.

## 5. 설정

`application.yml` 의 `rag:` 블록 안에 추가:

```yaml
rag:
  hybrid:                                              # (선행 spec)
    enabled: ${RAG_HYBRID_ENABLED:false}
    # ...
  query-rewrite:                                       # (이번 spec)
    enabled: ${RAG_QUERY_REWRITE_ENABLED:false}
    model: ${RAG_QUERY_REWRITE_MODEL:gpt-4o-mini}
    max-tokens: ${RAG_QUERY_REWRITE_MAX_TOKENS:80}
    temperature: ${RAG_QUERY_REWRITE_TEMPERATURE:0.3}
    timeout-ms: ${RAG_QUERY_REWRITE_TIMEOUT_MS:5000}
```

`QueryRewriteProperties` (Java record, `@ConfigurationProperties("rag.query-rewrite")`):
```java
public record QueryRewriteProperties(
        boolean enabled,
        String model,
        int maxTokens,
        double temperature,
        int timeoutMs
)
```

## 6. Rewrite 프롬프트

### 6.1 System prompt

```
당신은 한국 청년 정책 문서 검색을 돕는 query 재작성 어시스턴트입니다.

규칙:
1. 사용자 질문을 정책 표준 용어로 변환하세요.
   예: "작년/올해" → "직전 N개월/최근/당해연도"
   예: "받을 수 있어?" → "지원 자격 / 신청 조건"
2. 의미를 추측·확장하지 마세요. 동의어·표준 용어 변환만 허용.
3. 정책명을 query 에 포함하세요.
4. 100자 이내, 검색용 키워드 중심.
5. 결과만 출력. 부가 설명 금지.
```

### 6.2 User prompt 템플릿

```
정책: {policyTitle}
질문: {userQuestion}

재작성된 검색 query:
```

### 6.3 LLM 파라미터

- `temperature = 0.3` — 일관성 우선, 약간의 표현 다양성 허용
- `max_tokens = 80` — 100자 이내 보장 (한글 ~1.5 토큰/자)
- `model = gpt-4o-mini` — 기존 QnA·가이드 와 동일

## 7. 에러 처리 / 폴백

| 케이스 | 동작 |
|--------|------|
| `enabled = false` | 기존 path 동작 (변경 없음) |
| rewrite LLM 호출 timeout | warning 로그 + 원래 질문으로 fallback |
| rewrite LLM 호출 예외 | warning 로그 + 원래 질문으로 fallback |
| rewrite 결과가 빈 문자열 또는 5자 미만 | 원래 질문 사용 |
| rewrite 결과 200자 초과 | 200자 truncate 후 사용 |
| exact 캐시 hit | rewrite skip (캐시 답변 그대로) |
| semantic 캐시 hit | rewrite skip (캐시 답변 그대로) |

Fallback 시에도 사용자 응답은 정상 진행 — query rewrite 가 실패해도 vector-only 또는 hybrid 검색으로 답변 생성됨.

## 8. 비용·관측

### 8.1 비용 추정

- LLM 호출 +1회/질문 (cache miss 케이스만, hit 케이스는 skip)
- gpt-4o-mini 기준:
  - 인풋: system prompt ~100토큰 + user prompt ~50토큰 = ~150토큰
  - 아웃풋: ~50토큰
  - 비용: ~$0.0001/회
- 일 1,000 cache miss 질문 가정 → ~$0.1/일, ~$3/월

### 8.2 메트릭

- `LlmCallRecorded` 이벤트 발행 (기존 패턴) — `LlmModule.QNA` 또는 신규 `QUERY_REWRITE` 모듈 사용 검토
- 로그:
  ```
  query rewrite: policyId=X, original="...", rewritten="...", duration=Nms
  query rewrite fallback: policyId=X, reason="timeout|exception|too-short|empty"
  ```

### 8.3 평가 (별도 작업)

평가 셋 — 진단에서 발견한 "의미 갭" 류 질문 10~20개 모음. 출시 전 retrieval@10 비교:
- 베이스라인: hybrid 검색만 (현재 main)
- 비교군: hybrid + query rewrite
- 측정 지표: 정답 청크 회수율, distance threshold 통과율, fallback 답변 비율

## 9. 테스트 전략

### 9.1 단위 테스트
- `OpenAiQueryRewriter` — Mock RestClient 로 API 응답
  - 정상 응답 → trim 후 반환
  - 빈 응답 / 5자 미만 → empty Optional 반환 (호출자가 fallback 처리)
  - 200자 초과 → truncate
  - timeout → empty Optional
  - 예외 → empty Optional + warn 로그
- `QnaService` 보강
  - `query-rewrite.enabled=false` → rewriter 호출 없음 (회귀 방지)
  - `enabled=true` + rewriter 정상 → rewritten query 로 임베딩·검색 호출
  - `enabled=true` + rewriter fallback → 원래 질문으로 임베딩·검색
  - 캐시 hit 시 rewriter 호출 없음

### 9.2 통합 테스트
- 기존 QnA 통합 테스트가 `enabled=false` (기본값) 에서 변경 없이 통과
- 별도 `enabled=true` 시나리오 1~2개 (rewriter mocking)

## 10. 마이그레이션 / 배포

1. **PR 1 (이 spec 기반)**: 코드 + 설정 추가, `enabled` 기본값 `false`
2. **스테이징 검증**: `RAG_QUERY_REWRITE_ENABLED=true` 환경변수 설정, 평가 셋 retrieval@10 측정
3. **운영 출시**: 환경변수 `RAG_QUERY_REWRITE_ENABLED=true` 설정 후 컨테이너 재기동 (코드 배포 없음)
4. **회귀 발견 시**: 환경변수 `false` 로 즉시 복귀

DB 스키마·인덱스 변경 없음. 청크 재생성·재임베딩 불필요.

## 11. 향후 작업 후보 (이번 범위 외)

- 평가 셋 기반 retrieval@10 정량 측정 인프라 (별도 spec)
- A-2 multi-query 또는 A-3 HyDE 비교 실험 (rewrite 효과 한계 도달 시)
- 도메인 용어 사전 (synonym dictionary) — system prompt 에 핵심 매핑 표 포함
- rewrite 결과를 사용자에게 노출하는 UX (디버깅·투명성)
- `LlmModule.QUERY_REWRITE` 신규 enum 추가로 비용 추적 세분화
- rewrite query 도 `semantic cache` 키로 보조 lookup — 캐시 hit rate ↑ 효과 측정
