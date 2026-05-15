# RAG Retrieval 정확도 개선 Design

> **궁극 목표**: 청크에 답이 있는데 retrieval 이 우선순위에서 못 잡는 케이스를 줄인다.
> 직전 사이클(`DONE_2026-05-11-rag-table-aware-chunking-design.md`)에서 chunking 측 의미 단위 보존은 이미 완료. 이번 사이클은 **retrieval ranking** 만 다룬다.

## 1. 배경

### 1.1 직전 사이클의 한계

PR #91 (rag-table-aware-chunking) 로컬 검증에서 발견:

- **Chunking 은 잘 동작**: 정책 7번 chunk #85 가 "표 항목: 디딤씨앗통장, 청년내일채움공제, 꿈나래통장, ..." 자연어 prefix + 표 본문(`1 디딤씨앗통장 보건복지부`, `3 꿈나래통장 서울특별시` 등)을 통째로 보존
- **Retrieval 이 못 잡음**: 자연어 query("디딤씨앗통장 중복 가능?")의 임베딩과 표 청크 사이의 cosine distance 가 0.6+ 로 다른 자연어 청크(chunk #80 "중복관리 대상사업")보다 멀어 top-10 retrieval 에서 밀려남
- **결과**: chunk #80 만 retrieve 되어 LLM 이 "디딤씨앗통장 = 중복 불가" 환각 답변 생성

### 1.2 검증 query 결과 (PR #91 기준)

| Query | 결과 | 원인 |
|---|---|---|
| 디딤씨앗통장 중복 가능? | ❌ 환각 (불가) | chunk #85 (정답 청크) top-10 밖 |
| 꿈나래통장 중복 가능? | ❌ fallback | 동일 |
| 중복수혜 안되는 통장 리스트 | ❌ fallback | chunk #82~83 (중복 불가 표) top-10 밖 |
| 지원 금액은 얼마야? | ✅ 정답 (30만/10만) | 자연어 청크 매칭, 회귀 없음 |

### 1.3 근본 진단

`text-embedding-3-small` 한국어 임베딩 특성:
- 자연어 query("디딤씨앗통장 중복 가능한가요?")는 자연어 설명 청크("디딤씨앗통장, 꿈나래통장 등 ... 중복 수혜 확인 시 즉시 참여 중단")와 임베딩 거리가 작음
- 표 데이터 청크("표 항목: ... 1 디딤씨앗통장 보건복지부 2 청년내일채움공제 ...")는 항목 나열 형태라 query 와 거리가 큼
- 즉 의미 매칭이 "주제 유사도" 에 기울고 "키워드 정확 매칭" 에 약함

**chunking 으로는 한계** — 청크에 키워드가 들어있어도 vector retrieval 만으로는 그 청크를 top-K 에 못 올림.

## 2. 목표

- 키워드가 정확히 들어있는 청크를 retrieval 우선순위에서 잡도록 한다
- 정책 7번 검증 query 5개 (spec §8.1) 중 ≥4개 정답
- 자연어 query 5개 (§8.2) 회귀 없음
- 다른 정책의 일반 자연어 답변 품질 영향 최소

## 3. 비범위

- Chunking 변경 (이번 사이클 X — 직전 사이클에서 완료)
- PDF 추출 변경 (옵션 F5)
- 새 embedding model 도입
- LLM 변경 (gpt-4o-mini → 다른 모델)
- 자동화 retrieval 벤치마크 인프라 구축 (ground-truth 매핑 데이터셋 등)
- Re-ranking 을 위한 cross-encoder 모델 도입 (인프라 부담 큼)

## 4. 후보 옵션

| 옵션 | 무엇 | 비용 | 효과 추정 |
|---|---|---|---|
| **R1. Hybrid retrieval (BM25 + vector)** | PostgreSQL `tsvector` + 한국어 형태소 분석(또는 GIN trigram) 으로 키워드 매칭 점수와 vector cosine 점수를 정규화·가중 평균 | 큼 (DB 인덱스 + 점수 계산 로직). 검색 latency 약간 ↑ | 키워드("디딤씨앗") 일치 청크가 vector 거리 멀어도 top-K 진입 가능. 정책 7번 케이스 핵심 |
| **R2. Query rewriting (LLM 정규화)** | 사용자 query 를 LLM 으로 "{정책명}의 {의도} 정보를 표/리스트로 알려줘" 같은 retrieval-friendly 형태로 변환 후 임베딩 | 중간 (LLM 호출 1회 추가 — 비용 약간 ↑, latency +500ms) | 표 청크 임베딩과 거리 줄여 retrieval 가능. 단 의도 분류 정확도 의존 |
| **R3. Multi-query / Multi-embedding** | 한 query 를 LLM 으로 2~3개 표현으로 확장 후 각각 임베딩 → top-K 합집합 | 큼 (LLM 호출 + 임베딩 호출 N배) | 정확도 ↑ 가능, 비용·latency ↑↑ |
| **R4. Page-level boost** | 동일 페이지 청크 다수가 retrieve 되면 그 페이지의 다른 청크 (top-K 외) 도 boost. 표 청크가 인근 페이지에 있을 때 회수 ↑ | 작음 (코드 30줄+) | 부분적 효과. 핵심 케이스 (chunk #85) 가 페이지 36 전체에 다른 자연어 청크가 없으면 효과 X |
| **R5. Keyword-aware boost** | Query 에서 명사 키워드 추출 (LLM 없이 단순 한국어 명사 토크나이즈 또는 OpenAI tokenizer) → 키워드가 청크에 직접 포함되면 distance 점수에 boost (예: distance × 0.85) | 중간 (한국어 토큰화 + 점수 가중). 외부 의존 추가 가능 | 정책 7번 케이스 직접 해결. R1 의 간이 버전 |
| **R6. Relevance threshold 완화** | `qna.relevance-distance-threshold` 0.78 → 0.85 등 | 작음 (1줄). 노이즈 청크 통과 위험 | 임계 통과 청크 수 늘리지만 ranking 자체는 안 바꿈 → 핵심 fix X |

## 5. 결정 후보 (다음 brainstorming 입력)

다음 brainstorming 세션에서 결정할 핵심 사항:

1. **메인 옵션 선택** — R1 (Hybrid) vs R5 (Keyword boost) 중심. R2/R3 는 LLM 비용 + latency 부담이라 보조 후보.
2. **한국어 키워드 토큰화 방식** — R5 채택 시
   - PostgreSQL `to_tsvector('korean', ...)` (Korean Stemmer 확장 설치 필요)
   - 또는 단순 명사 정규식 (한글 2글자+ 연속) 추출
   - 또는 외부 라이브러리 (Lucene Korean Analyzer, mecab-ko 등)
3. **점수 결합 방식** — R1/R5 채택 시 vector cosine + keyword score 가중 평균의 비율 (예: 0.7 vector + 0.3 keyword? 또는 RRF — Reciprocal Rank Fusion)
4. **검증 방법** — 수동 (직전 사이클과 동일 query 모음) vs 자동 (운영 로그에서 hit/miss 분포 측정)
5. **점진적 rollout** — feature flag 로 vector-only ↔ hybrid 전환 가능하게? 또는 즉시 전면 적용?

## 6. 검증 query (직전 사이클과 동일)

**표 관련 (정답 있어야)**:
- "중복수혜 안되는 통장 리스트 알려줘" → chunk #82~83 (불가 표) retrieve
- "어떤 통장이 중복수혜인지 리스트 알려줘"
- "디딤씨앗통장 중복 가능한가요?" → chunk #85 retrieve (가능 표)
- "꿈나래통장 중복 가능한가요?"
- "안되는 통장 리스트"

**자연어 (회귀 없어야)**:
- "신청 자격이 뭐야?"
- "지원 금액은 얼마야?"
- "신청 기간은 언제야?"
- "어디서 신청해?"
- "지원 대상은?"

**판정 기준**: 표 5개 중 ≥4개 정답 + 자연어 5개 모두 회귀 없음.

## 7. 시급도

**중간~높음**. 직전 사이클의 chunking 개선만으로는 정책 7번 검증 query 효과 없음. 사용자 신뢰도 영향 큼(잘못된 환각 답변). 다음 sprint 권장.

## 8. 관련 코드/파일

**핵심 변경 후보**:
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — retrieval 진입점
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java` (또는 동급) — 실제 SQL/native query
- `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java` — 추가 메서드 시그니처

**무변경 / 참조용**:
- `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` — 직전 사이클 결과 그대로
- `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` — 답변 흐름 그대로
- `backend/src/main/resources/application.yml:92` — `relevance-distance-threshold` 운영 튜닝 시 참고

## 9. 위험 / 사전 검토

- **한국어 형태소 분석 의존성** — R1/R5 채택 시 PostgreSQL 한국어 stemmer 확장 또는 외부 라이브러리 도입 부담. dev/prod 환경 모두 적용 가능한지 사전 확인 필요
- **검색 latency** — Hybrid 또는 query rewriting 적용 시 latency 증가. 핫패스 (qna 채팅) 영향 측정 필요
- **운영 비용** — R2/R3 채택 시 LLM 호출 추가. 비로그인 사용자 진입 가능 시점에 영향
- **점수 결합 가중치 튜닝** — Hybrid 의 vector/keyword 가중치는 운영 데이터로 튜닝 필요. 초기값에서 회귀 가능성

## 10. 사전 작업 (사이클 진입 시 필요)

- PostgreSQL 의 한국어 텍스트 검색 옵션 조사 (`to_tsvector('simple', ...)` vs `to_tsvector('korean', ...)` 확장 설치 여부, GIN trigram 인덱스 가능 여부)
- 직전 사이클의 검증 환경 (정책 7번 reindex 된 로컬 DB) 그대로 재사용 가능
- backend log 에서 retrieval distance 분포 measurement — 표 청크 vs 자연어 청크 거리 비교 평균값 산출

## 11. 참고

- 직전 사이클 spec: `docs/superpowers/specs/DONE_2026-05-11-rag-table-aware-chunking-design.md`
- 직전 사이클 PR: #91 (rag-table-aware-chunking)
- v0 의미 캐시 spec (intent 기반 v1 후보): `docs/superpowers/specs/TODO_v1-semantic-cache-intent-based.md`
- 모체 사이클: `docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md`
