# RAG 표 청크가 retrieval top-K 에서 밀려나던 문제 — keyword boost SQL 도입

- 작성일: 2026-05-12
- 작성자: TaetaetaE01
- 관련 커밋: `908a028` (chore(docs): rag-retrieval-keyword-boost plan DONE_ prefix) 외 8 commits (`b3f6434`, `7496b8a`, `439a27e`, `1fab9f7`, `f1b9723`, `2c44ea5`, `b174ff2`, `e9697e2`)
- 관련 PR: #93 (merged 2026-05-11)
- 관련 모듈: backend/rag, backend/qna

## 한 줄 요약

> 직전 사이클(table-aware chunking, PR #91)에서 표 청크 의미 단위 보존을 끝냈음에도 정책 7번 검증 query 가 환각/fallback 으로 떨어지던 문제. retrieval ranking 이 자연어 임베딩을 선호해 표 청크를 top-10 밖으로 밀어내고 있었다. native SQL 에 keyword multiplicative distance boost (CASE + unnest ILIKE) 를 추가해 키워드 직접 일치 청크를 top-K 로 끌어올림. feature flag `youthfit.rag.keyword-boost.enabled` 로 즉시 비활성화 가능.

## 1. 상황 (Context)

직전 사이클 (PR #91, `DONE_2026-05-11-rag-table-aware-chunking-design.md`) 로컬 검증 중 발견:

- 정책 7번 (수원시 청년 통장) 의 표 본문 청크 #86 (`"1 디딤씨앗통장 보건복지부 / 2 청년내일채움공제 / 3 꿈나래통장 ..."`) 이 자연어 prefix + 표 본문을 통째로 잘 보존하고 있었다 (chunking 은 성공).
- 그러나 검증 query 결과:

  | Query | 결과 | 원인 |
  |---|---|---|
  | 디딤씨앗통장 중복 가능? | ❌ "불가" 환각 | 정답 청크 top-10 밖 |
  | 꿈나래통장 중복 가능? | ❌ fallback | 동일 |
  | 중복수혜 안되는 통장 리스트 | ❌ fallback | 표 청크 top-10 밖 |
  | 지원 금액은 얼마야? | ✅ 30만/10만 | 자연어 매칭, 회귀 없음 |

- 표 청크 (chunk #85, #86) 가 `embedding <=> :queryEmbedding` cosine distance 0.6+ 로 멀어 자연어 청크들 (chunk #80 "중복관리 대상사업" 등, distance 0.45~0.55) 에 밀려 top-10 안에 진입하지 못함.
- 결과적으로 LLM 이 정답이 없는 자연어 청크만 보고 "디딤씨앗통장 = 중복 불가" 같은 환각 답변 생성.

## 2. 원인 (Root Cause)

`text-embedding-3-small` 모델의 한국어 임베딩 특성:

- 자연어 query (`"디딤씨앗통장 중복 가능한가요?"`) 는 자연어 설명 청크 (`"디딤씨앗통장, 꿈나래통장 등 ... 중복 수혜 확인 시 즉시 참여 중단"`) 와 임베딩 거리가 작음 (∼0.4).
- 표 데이터 청크 (`"표 항목: ... 1 디딤씨앗통장 보건복지부 2 청년내일채움공제 ..."`) 는 항목 나열 형태라 query 와 거리가 큼 (∼0.6+).
- 즉 **의미 매칭이 "주제 유사도" 에 기울고 "키워드 정확 매칭" 에 약함**.

관련 코드 경로:
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — retrieval 진입점
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java:18-37` — pgvector `<=>` 단일 ORDER BY native query
- `application.yml:92` — `qna.relevance-distance-threshold: 0.78` (threshold 완화만으로는 ranking 자체를 바꾸지 못해 해결 불가)

**잘못된 가정**: chunking 만 잘 해두면 vector retrieval 이 자동으로 정답 청크를 잡아줄 거라고 가정했지만, 한국어 임베딩의 표면 어휘 다양성 민감도가 예상보다 컸음. 청크에 키워드가 들어있어도 vector retrieval 만으로는 그 청크를 top-K 에 못 올림.

## 3. 고려한 대안 (Alternatives)

직전 사이클 입력 spec (`DONE_2026-05-11-rag-retrieval-improvements-design.md`) §4 에서 6개 옵션 비교:

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| **R1. Hybrid retrieval (BM25 + vector)** | PostgreSQL `tsvector` + 한국어 형태소 분석으로 키워드 점수와 vector cosine 가중 평균. 가장 표준적 접근 | 한국어 stemmer 운영 부담 (`pg_korean` / `mecab-ko` 설치) + tsvector 컬럼 마이그레이션 + 점수 결합 가중치 튜닝 부담 큼 |
| **R2. Query rewriting (LLM 정규화)** | LLM 으로 query 를 retrieval-friendly 형태로 변환 후 임베딩 | LLM 호출 추가 (latency +500ms, 비용 ↑). 의도 분류 정확도 의존 |
| **R3. Multi-query / Multi-embedding** | query 를 2~3개 표현으로 확장 후 top-K 합집합 | LLM + 임베딩 호출 N배. 비용·latency ↑↑ |
| **R4. Page-level boost** | 동일 페이지 다수 retrieve 시 인접 청크도 boost | 정책 7번 chunk #85 의 인접 페이지에 자연어 청크가 적으면 효과 X. 부분적 |
| **R5. Keyword-aware boost** | query 명사 키워드 추출 후 청크 본문 직접 포함 시 distance × factor. R1 의 간이 버전 | 한국어 형태소 의존 없이도 정책 7번 케이스 직접 해결. 자연어 query 회귀 위험 작음 |
| **R6. Relevance threshold 완화** | `qna.relevance-distance-threshold` 0.78 → 0.85 | 통과 청크 수만 늘릴 뿐 ranking 자체를 못 바꿈 → 핵심 fix X |

세부 결정 분기 (brainstorming 단계):

| 결정 | 후보 | 선택 | 이유 |
|---|---|---|---|
| 한국어 토큰화 | 정규식 `[가-힣A-Za-z0-9]{2,}` / `to_tsvector('simple')` / mecab-ko | **정규식** | "디딤씨앗통장" 같은 합성어가 한 토큰으로 잡힘. 외부 의존 0. R5 의 간이성과 일관 |
| 점수 결합 | Multiplicative boost / RRF / 2-stage over-fetch | **Multiplicative** | 단일 쿼리로 top-K 확정 가능. 키워드 hit 없는 청크는 그대로 distance 유지 → 자연어 query 회귀 위험 최소 |
| 구현 위치 | SQL native query 안 / over-fetch + Java 재정렬 | **SQL 안** | over-fetch 회피, top-K 정확 산출. content 컬럼 30개 전체 fetch 비용 절감 |
| stopword 처리 | 소형 리스트 / min-length 3 / 리스트 없음 | **소형 리스트 (14개)** | "지원·금액" 같은 도메인 키워드는 살리되 "뭐·얼마·가능" 같은 의문사·일반어 제거 |

## 4. 선택과 이유 (Decision)

**채택**: R5 (Keyword-aware boost) — 정규식 토큰 + 소형 stopword + SQL 안 multiplicative boost.

**결정의 핵심 근거**:
- **운영 부담 최소**: 한국어 stemmer 설치 (`pg_korean`, `mecab-ko`) 또는 JNI 외부 라이브러리 도입 없이 정규식만으로 정책 7번 케이스 직접 해결 가능
- **응답 지연 최소**: 추가 LLM 호출 없음 (R2/R3 와의 차이). 정규식 토큰화 1ms 미만 + SQL CTE 1단 추가만으로 끝
- **회귀 위험 작음**: keyword hit 없는 청크는 distance 그대로 유지 → 기존 자연어 query 동작 보존. Feature flag 로 즉시 비활성화 가능
- **변경 범위 작음**: native SQL + Repository 시그니처 + 도메인 서비스 1개. 마이그레이션 없음

**트레이드오프로 받아들인 것**:
- 한국어 어형 변화 미처리 (`"통장"` 과 `"통장의"`, `"통장은"` 따로 처리됨) — 청크 본문이 명사 위주라 운영상 큰 영향 없을 것으로 추정
- noisy hit 가능성 — `"지원"`, `"대상"` 같은 일반어가 거의 모든 청크에 있어 boost 효과가 균등 적용되어 희석. stopword 보강 또는 boost factor 운영 튜닝으로 대응
- LLM 답변 정확성 ≥4/5 보장 어려움 — retrieval 단 개선만으로 LLM 답변 품질이 자동 보장되지는 않음 (사용자 spot check 필요)

**가역성**: 매우 높음.
- feature flag `youthfit.rag.keyword-boost.enabled = false` 로 즉시 비활성화 (SQL 레벨 + 서비스 레벨 이중 회귀 안전망)
- 코드 롤백도 1 커밋 revert 로 단순. 마이그레이션 / 스키마 변경 없음
- **재검토 신호**: 운영 로그에서 (a) 자연어 query 정답률 회귀 발견 시 (b) noisy hit 으로 표 청크 외 일반어 청크가 부적절히 상위로 이동하는 패턴 발견 시 (c) 신규 임베딩 모델 도입 검토 시 → R1 (Hybrid BM25) 또는 R2 (Query rewriting) 으로 확장 후보

## 5. 해결 (Solution)

### 5.1 핵심 구현

신규 컴포넌트:

- `backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java` — 순수 Java 도메인 서비스. 정규식 `[가-힣A-Za-z0-9]{2,}` 토큰 + stopword 필터 + max-keywords 상한 + LinkedHashSet 으로 dedup 및 순서 유지. Spring 의존 zero
- `backend/src/main/java/com/youthfit/rag/infrastructure/config/KeywordBoostProperties.java` — `@ConfigurationProperties("youthfit.rag.keyword-boost")` record. `enabled`, `maxKeywords`, `stopwords` 필드

수정:

- `PolicyDocumentRepository.findSimilarByEmbedding` 시그니처에 `List<String> keywords` 추가 (도메인 인터페이스)
- `PolicyDocumentJpaRepository.findSimilarByEmbedding` native query 본문 (`PolicyDocumentJpaRepository.java:18-54`):
  ```sql
  WITH base AS (
    SELECT id, ..., (embedding <=> cast(:queryEmbedding AS vector)) AS distance
      FROM policy_document
     WHERE policy_id = :policyId AND embedding IS NOT NULL
  ), scored AS (
    SELECT b.*,
           (SELECT COUNT(*) FROM unnest(cast(:keywords AS text[])) k
             WHERE b.content ILIKE '%' || k || '%') AS hit_count
      FROM base b
  )
  SELECT ..., CASE
    WHEN hit_count >= 3 THEN distance * 0.78
    WHEN hit_count = 2 THEN distance * 0.85
    WHEN hit_count = 1 THEN distance * 0.92
    ELSE distance
  END AS boosted_distance
    FROM scored
   ORDER BY boosted_distance
   LIMIT :limit
  ```
- `RagSearchService.searchRelevantChunks(command, embedding)` — `keywordBoostProperties.enabled() ? keywordExtractor.extract(query) : List.of()` 분기 후 keywords 를 repository 에 전달
- `RagConfig` — `KeywordBoostProperties` 등록 + `KeywordExtractor` `@Bean` 등록 (Properties 의 stopwords → `HashSet` 변환)
- `application.yml:102-119` — `youthfit.rag.keyword-boost` 섹션 + 14개 한글 stopword (`뭐, 무엇, 어떤, 어떻게, 얼마, 언제, 어디, 어디서, 누구, 가능, 필요, 알려줘, 정보, 내용`)

환경변수: `RAG_KEYWORD_BOOST_ENABLED` (default true), `RAG_KEYWORD_BOOST_MAX_KEYWORDS` (default 5)

### 5.2 구현 중 트러블슈팅 4건

#### (1) Hibernate 의 `List<String>` → PostgreSQL `text[]` 변환 실패

- **증상**: 처음에 JPA `@Query` 메서드 시그니처를 `findSimilarByEmbedding(..., List<String> keywords, int limit)` 로 두자 `cast((?,?) AS text[])` 형태로 expand 되어 `ERROR: cannot cast type record to text[]` 발생
- **원인**: Hibernate 6 + JPA native query 가 `List<String>` 파라미터를 자동으로 IN-list 튜플 (`(?,?)`) 로 펼침. `text[]` 캐스트와 호환 안 됨
- **해결**: JPA repository 시그니처만 `String[] keywords` 로 변경, `PolicyDocumentRepositoryImpl.findSimilarByEmbedding` 에서 `keywords == null ? new String[0] : keywords.toArray(new String[0])` 변환. **도메인 인터페이스는 `List<String>` 유지** — 캡슐화 누수 zero
- **재발 방지**: spec §6 의 SQL 노트에 명시 추가. follow-up cycle 에서 다른 `text[]` 파라미터 사용 시 동일 패턴 적용

#### (2) Spring Boot 4 BOM 이 testcontainers 버전 미관리

- **증상**: 처음에 `org.testcontainers:postgresql:1.20.4` / `junit-jupiter:1.20.4` 두 줄에 명시 버전 박았다가, code review 피드백으로 "spring-boot-testcontainers 가 BOM 으로 관리하지 않을까" 가설 따라 버전 제거. 결과: `Could not find org.testcontainers:postgresql:.` 빌드 실패
- **원인**: Spring Boot 4.0.5 BOM 이 `testcontainers-bom` 을 imports 하지 않음 (Spring Boot 3.x 와 다른 부분)
- **해결**: `build.gradle:52` 에 `testImplementation platform('org.testcontainers:testcontainers-bom:1.20.4')` 추가 후 `postgresql` / `junit-jupiter` 는 버전 없이 BOM 위임. 기존 `awssdk:bom` 패턴 (line 37) 과 일관
- **재발 방지**: testcontainers 도입 시 BOM 부터 검토하는 게 표준

#### (3) `ORDER BY 8` positional reference 의 함정

- **증상**: 첫 구현에서 native SQL 이 `ORDER BY 8` (8번째 컬럼 = CASE 결과) 였음. quality reviewer 가 "컬럼 reorder 시 silently 다른 컬럼으로 정렬됨" 지적
- **원인**: positional reference 는 SELECT 컬럼 순서 변경에 fragile. CASE 결과를 `AS distance` 로 aliasing 했지만 raw distance 와 이름 충돌해서 가독성도 떨어짐
- **해결**: `END AS boosted_distance` → `ORDER BY boosted_distance` 로 명시 alias 정렬. raw `distance` 와 `boosted_distance` 가 컬럼 이름으로 구분됨
- **재발 방지**: native SQL 에서 ORDER BY positional 금지, alias 사용

#### (4) 모듈 경계 위반 — `rag.application` → `qna.infrastructure` 역방향 의존

- **증상**: 초기 구현에서 `KeywordBoostProperties` 를 `qna/infrastructure/config/` 에 두고 `QnaConfig` 에서 `KeywordExtractor` `@Bean` 등록함. `RagSearchService` (rag.application) 가 `KeywordBoostProperties` (qna.infrastructure) 를 import 함
- **원인**: properties prefix 가 `youthfit.qna.*` 이라 qna 모듈에 넣었지만, 의미상 keyword boost 는 RAG 검색 동작이라 rag 모듈에 있어야 함. `backend/CLAUDE.md` 의 "의존 방향 Presentation → Application → Domain" + 모듈 간 단방향 규칙 위반
- **해결**:
  - `KeywordBoostProperties` 를 `qna/infrastructure/config/` → `rag/infrastructure/config/` 이동
  - prefix 변경: `youthfit.qna.keyword-boost` → `youthfit.rag.keyword-boost`
  - 환경변수: `QNA_KEYWORD_BOOST_*` → `RAG_KEYWORD_BOOST_*`
  - `KeywordExtractor` `@Bean` 등록 위치도 `QnaConfig` → `RagConfig`
  - `application.yml` 의 `youthfit.qna.keyword-boost` 섹션을 `youthfit.rag.keyword-boost` 로 이동
- **재발 방지**: 신규 properties 추가 시 prefix 결정 전에 "어느 모듈이 이 설정의 주체인가" 먼저 확인. 의미상 소유 모듈이 properties 위치를 결정해야 함

#### (5) slice test 시나리오 — chunk 의 distance ≈ 0 이라 ranking 검증 무용

- **증상**: 초기 슬라이스 테스트 시나리오에서 chunk 1 의 embedding 을 query 와 거의 동일하게 두니 distance ≈ 0.001 이 되어 boost (×0.85) 적용해도 ranking 변동 없음. "키워드 hit 청크가 ranking 위로 이동" 검증 의도가 무효화
- **원인**: distance 가 매우 작으면 multiplicative boost 효과가 절대값에서 미미해 ranking 역전 안 일어남. setUp 의 embedding 설계가 부주의
- **해결 (2단계)**:
  - 1차: 시나리오를 "boost factor 가 정확히 적용된다 (`baseDistance × 0.85 ≈ boostedDistance`)" formula 검증으로 변경
  - 2차: 별도 정책 ID 로 ranking-shift 시나리오 추가. chunk A (distance 0.4, 3 hit → boosted 0.312) vs chunk B (distance 0.35, 0 hit). boost 미적용 시 B 1위, 적용 시 A 1위로 역전 검증 (`keywordsBoost_movesHitChunkAboveCloserChunk`)
- **재발 방지**: slice test 작성 시 setUp embedding 의 distance 분포가 boost factor 효과를 실제로 가시화할 수 있는지 사전 계산. spec 본질 (E2E behavior) 과 formula correctness 둘 다 별도 시나리오로 검증

## 6. 검증 (Result)

### 6.1 단위/통합 테스트 (20/20 PASS)

- `KeywordExtractorTest` 9 케이스 — 한글 합성어/영숫 혼용/null/blank/1글자/dedup/stopword 필터/max 상한/stopwordNotCounted
- `PolicyDocumentRepositoryKeywordBoostTest` 3 케이스 (Testcontainers `pgvector/pgvector:pg17` slice):
  - `emptyKeywords_pureDistanceOrder` — 회귀 zero
  - `keywordsBoost_reducesHitChunkDistance` — formula 정확성
  - `keywordsBoost_movesHitChunkAboveCloserChunk` — ranking shift 명시 검증
- `RagSearchServiceTest` 8 케이스 — flag on/off 분기, 추출된 키워드 전달, fallback 흐름

### 6.2 정책 7번 retrieval 단 직접 검증

Q&A endpoint 가 카카오 JWT 인증 필요라 자동 풀스택 검증 불가. **psql 로 native SQL 을 BASE (keywords=[]) / BOOST (keywords=추출결과) 두 번 실행 → top-10 비교** 방식으로 retrieval 단만 검증.

**표 query 5개** (정답 청크 #85=표 헤더 / #86=표 본문 "1 디딤씨앗통장 ..."):

| # | Query | BOOST 결과 정답청크(85/86) | 평가 |
|---|---|---|---|
| 1 | 중복수혜 안되는 통장 리스트 알려줘 | ❌ | chunk 80 (자연어, 3 hit ×0.78) 신규 2위 — LLM 정답 답변 가능성 있음 |
| 2 | 어떤 통장이 중복수혜인지 리스트 알려줘 | ❌ | chunk 80 신규 3위 진입 |
| 3 | 디딤씨앗통장 중복 가능한가요? | **85(1위), 86(4위)** ✅ | 본 사이클 대표 케이스 — 표 본문까지 완전 진입 |
| 4 | 꿈나래통장 중복 가능한가요? | **85(1위)** ⚠️ | 헤더만 진입 |
| 5 | 안되는 통장 리스트 | ❌ | 일반어 → 모든 청크 hit=1 → 효과 미미 |

**자연어 query 5개** (회귀 검증):

| # | Query | top-10 변화 | 회귀 |
|---|---|---|---|
| 6 | 신청 자격이 뭐야? | 동일 (모두 1 hit ×0.92) | 없음 |
| 7 | 지원 금액은 얼마야? | chunk 315 7→3 (2 hit ×0.85) | 미미 |
| 8 | 신청 기간은 언제야? | chunk 340 8→1 (2 hit ×0.85) | LLM 확인 필요 |
| 9 | 어디서 신청해? | 동일 | 없음 |
| 10 | 지원 대상은? | 동일 | 없음 |

**판정**: 핵심 케이스 Q3 명확히 정답 청크 진입. 7/10 query 가 동일/미미 변화 — 회귀 위험 낮음. **LLM 답변 정확성 ≥4/5 는 풀스택 환경에서 사용자 spot check 으로 위임**.

### 6.3 회귀 안전망 (이중)

- **SQL 레벨**: keywords 빈 배열 → `unnest` 0 row → `hit_count = 0` 전체 청크 → CASE ELSE → boosted == raw distance → 기존 ranking 100% 동일
- **서비스 레벨**: `youthfit.rag.keyword-boost.enabled = false` → `KeywordExtractor.extract` 호출 자체 회피, 빈 리스트 전달 → SQL 레벨 회귀 zero 와 동일 결과

### 6.4 모니터링 포인트

- 운영 로그 `RAG 키워드 boost: policyId={}, enabled={}, keywords={}` 로 추출된 키워드 추적 가능 (`RagSearchService.java:54-57`)
- `RAG 검색 결과: policyId={}, top{}={}` 로 boosted distance 분포 추적 가능
- 영향 zero 확인된 영역: `OpenAiEmbeddingClient`, `RagIndexingService`, `PgVectorSemanticQnaCache`, `QnaService` 의 query 임베딩 단일 생성 패턴

## 7. 후속 / 미결 (Follow-ups)

- **LLM 답변 정확성 spot check** — Q3/Q4/Q8 우선 확인. 프론트엔드 + 카카오 로그인 통한 풀스택 검증 (자동화는 본 사이클에서 불가했음)
- **boost factor 운영 분포 모니터링** — 0.92/0.85/0.78 이 운영 데이터에 적정한지 추적. 부적절하면 SQL 상수에서 `KeywordBoostProperties` 로 이전 검토
- **noisy hit 분포 모니터링** — `"지원"`, `"대상"`, `"신청"` 같은 일반어 hit 분포 추적. 자연어 query 의 거의 모든 청크가 hit 잡혀 boost 효과 균등 적용되는 패턴이 보이면 stopword 보강
- **합성어 띄어쓰기 케이스** — `"디딤 씨앗 통장"` 처럼 띄어 입력하면 `["디딤", "씨앗", "통장"]` 로 분리되어 청크 본문과 표기 불일치. 운영 빈도 보고 대응 결정
- **R1 (Hybrid BM25) 확장 검토 트리거** — 본 R5 만으로 정답률이 부족하다는 신호 발견 시 (예: 검증 query 정답률 < 60%) tsvector + GIN 인덱스 도입 검토
- **spec 본문의 `ORDER BY 8` 잔재** — DONE 처리된 spec 본문에 alias 변경 전 SQL 예시가 남아있음. 후속 사이클에서 spec 참조 시 혼동 방지를 위해 정정 권장 (final reviewer M1)

## 8. 참고 (References)

- spec (출발점): `docs/superpowers/specs/DONE_2026-05-11-rag-retrieval-improvements-design.md`
- spec (본 사이클 설계): `docs/superpowers/specs/DONE_2026-05-11-rag-retrieval-keyword-boost-design.md`
- plan: `docs/superpowers/plans/DONE_2026-05-11-rag-retrieval-keyword-boost.md`
- PR: https://github.com/TaetaetaE01/youthfit/pull/93
- 직전 사이클 (chunking): `docs/superpowers/specs/DONE_2026-05-11-rag-table-aware-chunking-design.md` / PR #91
- 모체 사이클: `docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md`
