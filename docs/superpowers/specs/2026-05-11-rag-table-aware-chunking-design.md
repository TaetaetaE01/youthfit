# RAG 청킹 의미 단위 보존 강화 Design

> **궁극 목표**: RAG 채팅에서 임베딩된 청크에 답이 있는데 retrieval 이 놓치는 케이스를 줄인다.
> 표 인식은 가장 두드러진 증상일 뿐, 진짜 문제는 현 chunker 가 의미 단위(표·헤더·긴 단락의 줄 boundary)를 자주 깨뜨려 임베딩 정밀도가 떨어지는 것이다.

## 1. 배경

### 1.1 발견 경위

2026-05-11 답변 풍부도 강화(PR #88) 수동 검증 중 정책 7번(청년내일저축계좌)에서 다음 질문들이 fallback 답변을 받음:

- "중복수혜 안되는 통장 리스트 알려줘" → fallback
- "안되는 통장 리스트 알려줘" → fallback
- "어떤 통장이 중복수혜인지 리스트 알려줘" → fallback

또 답변되더라도 부정확:
- "중복수혜안되는 정책도 있어?" → "디딤씨앗통장, 꿈나래통장이 불가" (사실 디딤씨앗통장·꿈나래통장은 **중복 가능** 사업)

PDF 에는 명백히 정답 데이터가 있음 (page 35 "중복 참여 불가 사업" 표 + page 35-36 "중복 참여 가능 사업" 표).

### 1.2 디버깅 결과

**데이터는 인덱싱돼 있음**:
```sql
SELECT chunk_index, page_start, page_end, LENGTH(content) FROM policy_document
WHERE policy_id = 7 AND content ILIKE '%중복%' ORDER BY chunk_index;
-- chunk #62 (p.34-35, 500자) -- "중복 참여 불가" 표 시작
-- chunk #63 (p.35,    146자) -- 표 중간 fragment
-- chunk #64 (p.35-36, 500자) -- "중복 참여 가능" 표 시작
```

**Retrieval 은 핵심 청크를 못 잡음**: top-K=10 에서도 chunk #62, #63, #64 모두 retrieved 안 됨. 인근 자연어 청크(#60 "중복관리 대상사업…")만 잡힘.

### 1.3 근본 원인 — DocumentChunker.splitBySize 의 hard cut

`backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java:216-223`:

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

PDF 에서 추출된 표가 한 단락(`\n\n` 미포함)으로 1000자+ 들어오면, `paragraphAwareSplit` 이 단락을 청크에 못 담아 `splitBySize` 로 위임. 그러면 **500자 단위 무조건 hard cut** — 표의 행 boundary, 헤더, 의미 단위 모두 무시.

결과:
- chunk #62 = 헤더 "중복 참여 불가 사업번호 사업구분 시행기관" + 1~22번 + 23번 절반
- chunk #63 = 23번 후반 + 24~29번 (헤더 잃음, "기도24 열혈청년 패키지사업…" 으로 시작 — 무슨 표인지 알 수 없음)
- chunk #64 = 다음 표 "중복 참여 가능 사업" 시작

### 1.4 LLM 환각 메커니즘

- chunk #60 (헤더 "중복관리 대상사업")가 retrieved
- #60 본문 중 "디딤씨앗통장" 키워드 등장
- LLM 이 #60 의 헤더 맥락("중복관리/불가")과 본문 키워드("디딤씨앗")를 결합 → "디딤씨앗통장 = 불가" 잘못 추론
- 진짜 디딤씨앗통장 분류 정보가 있는 chunk #64 는 못 봤기에 정정 불가

즉 단순 vector embedding 거리 문제가 아니라, **chunking 이 의미 단위를 깨뜨려 헤더가 분리됐고 LLM 이 잘못된 컨텍스트로 추론**.

### 1.5 일반화 — 표 외 케이스

표는 가장 눈에 띄는 증상이지만, 같은 메커니즘으로 다음 케이스도 함께 영향을 받는다:

| 케이스 | 현 chunker 동작 | 회수 실패 메커니즘 |
|---|---|---|
| 긴 평문 단락 (1000자+) | `splitBySize` hard cut — 줄/문장 boundary 무시 | 청크 중간에 잘려서 임베딩 의미 흐려짐 |
| 섹션 헤더 + 본문 분리 | 헤더 ("■ 지원 내용") 가 한 청크에 박히고 본문이 다음 청크로 | 헤더 없는 청크는 주제 미상 → 질문 임베딩과 매칭 실패 |
| 번호 리스트 | 표와 같은 메커니즘으로 깨짐 | 표 인식 룰이 같이 잡음 |

### 1.6 추출 형식 가정

`TikaAttachmentExtractor` 는 PDF 표를 markdown 형식이 아니라 **평문**으로 풀어 추출한다. `PageAwareContentHandler` 가 `<page=N>` sentinel 만 박을 뿐, 표 구조는 줄바꿈만 남는 평문이다. 따라서 청커는 markdown 표 패턴(`|...|`)이 아니라 **평문 표 패턴**(번호 시작 행 반복)을 식별 대상으로 삼는다.

## 2. 목표

- 표·번호 리스트가 깨질 때 헤더가 보존된 채로 retrieval 가능하도록 청킹한다.
- 긴 평문 단락이 줄 중간에서 잘리지 않도록 한다.
- 청크 boundary 가 깨져도 인접 청크가 일부 정보를 회수할 수 있도록 overlap 을 둔다.
- 회귀: 자연어 질문(자격/지원/기간/방법) 답변 품질은 떨어지지 않는다.

## 3. 비범위 (이번 사이클 X)

- Markdown extraction 으로 Tika 옵션 변경 (옵션 F5) — ingestion pipeline 전면 변경
- Retrieval 측 변경 — top-K 확대(이미 PR #88 에서 10 으로 조정됨), MMR, hybrid BM25+vector
  - **운영 검증 결과 retrieval 개선이 사실상 필요한 것으로 확인됨** (§11 참고). 후속 사이클 spec 으로 분리: `docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md`
- 자동화 retrieval 벤치마크 (ground-truth chunk 매핑 필요)
- 표 외 의미 단위 보존 — 명시적 섹션 헤더 prepend (■/▶/Ⅰ 등) 는 후속 사이클 후보

## 4. 결정 로그

| # | 결정 포인트 | 결정 | 근거 |
|---|---|---|---|
| 1 | 접근 강도 | F2(표 인식) + F3(overlap) 본격 | 근본 해결. 다음 사이클의 부담을 줄임 |
| 2 | 청킹 개선 범위 | 표 인식 + 줄 보존 + overlap | 사용자 목표("정보 놓치는 케이스 전반 감소")에 직접 기여. retrieval 변경과 효과 분리 |
| 3 | 표 식별 방식 | 일반 휴리스틱 (연속 번호 행 ≥3) | 도메인 무관 → 신규 정책 자동 커버. 키워드 사전 유지보수 부담 회피 |
| 4 | Rollout | 정책 7번 검증 → 전체 | 회귀 조기 포착, 파괴 범위 제한 |
| 5 | 검증 방법 | 수동 — Before/After 답변 비교 | 자동화는 ground-truth 세팅 부담 큼. v0 범위 X |

## 5. 알고리즘 변경 (`DocumentChunker`)

현재 흐름:
```
chunk → splitToSegments → chunkSegment(seg) → paragraphAwareSplit(text) → (단락 너무 김) → splitBySize (hard cut)
```

변경 후 흐름:
```
chunk → splitToSegments → chunkSegment(seg)
                       → identifyTableBlocks(text)         // (a) 표 block 마킹
                       → paragraphAwareSplit               // 표 block 은 단일 단위로 취급
                       → (단락 너무 김) → splitByLines     // (b) 줄 단위 누적
                       → applyOverlap                       // (c) 일반 청크에 overlap
```

### 5.1 (a) 표 인식 + 헤더 prepend

**식별 룰**:
- 텍스트를 줄(`\n`) 단위로 스캔
- 한 줄이 다음 패턴 중 하나에 매칭되면 "번호 행" 으로 간주:
  - `^\d+\s+` (예: "1 기초생활보장 복지부")
  - `^\d+\.\s+` (예: "1. 기초생활보장")
  - `^[①②③④⑤⑥⑦⑧⑨⑩]` (한글 원숫자)
- 번호 행이 **3개 이상 연속**되면 그 구간을 "표 block" 으로 마킹
- 직전 한 줄을 **헤더 후보**로 저장 — 단, 직전 줄이 명백한 평문이면(20자 초과 + 마침표/물음표/느낌표로 종결) 헤더 후보 없음

**처리 룰**:
- 표 block 이 maxChunkSize 이내면 → 통째로 한 청크
- 초과 시 → 행(`\n`) boundary 에서 분할, 각 청크 맨 앞에 헤더 후보 prepend (헤더 후보가 있을 때만, 그리고 헤더 행이 청크의 첫 줄로 들어감 — 줄바꿈으로 분리)
- 헤더 prepend 로 청크 길이가 maxChunkSize 를 살짝 초과해도 허용 (soft limit)

**예시 — chunk #62 케이스**:
```
중복 참여 불가                              ← 표 제목 (이전 단락)
사업번호 사업구분 시행기관                  ← 헤더 후보 (직전 한 줄)
1   기초생활보장   복지부                   ← 번호 행 연속 시작 (3+ 매칭 → 표 인식)
2   기초생활보장   복지부
3   ...
```
- 표 block 전체가 한 청크에 못 들어가면 분할되, 모든 분할 청크 앞에 "사업번호 사업구분 시행기관\n" 이 prepend 됨

### 5.2 (b) `splitBySize` → `splitByLines` 다운그레이드

현재 `splitBySize(int start, int end, ranges)` 의 동작 변경:
- 글자 단위가 아니라 **줄(`\n`) 단위로 누적**
- 누적 길이가 maxChunkSize 초과 직전이면 청크 flush
- 단일 줄 자체가 maxChunkSize 를 넘으면 그때만 글자 단위 cut (last-resort fallback, 거의 발생 안 함)

이로써 표가 아닌 긴 평문 단락도 줄 boundary 가 보존됨.

### 5.3 (c-1) 단락형 표 인식 + 자연어 prefix (추가 fix, PR 1차 검증 후 추가)

§1.6 가정과 달리 정책 7번 PDF 의 표는 줄바꿈 없이 한 단락으로 떨어지는 케이스가 핵심. 이를 잡기 위해 `chunkSegment` 진입 직전 `expandParagraphTables` 단계 추가.

**식별 룰** (`findSequentialNumberPositions`):
- 한 줄/단락 안에서 1, 2, 3, ... 순차 NUMBER chain 을 모두 추출 (chain 여러 개 공존 허용 — 페이지 36 에 "1~11 중복 가능" + "30~41 그 외" 두 표가 한 줄에 있는 케이스)
- 길이 ≥ MIN_TABLE_ROWS(3) 인 chain 의 NUMBER 위치들이 표 행 후보

**처리 룰** (`expandTableInLine`):
- NUMBER 마다 줄바꿈 삽입 → 줄 단위 휴리스틱 (§5.1) 활성화
- 표 항목 이름(NUMBER 직후 첫 토큰) 추출 후 청크 시작에 `"표 항목: A, B, C."` 자연어 prefix 첨가 → 표 청크가 자연어 query 와 embedding 거리상 더 가까워지도록

이미 줄로 분리된 표(`1 ...\n2 ...\n3 ...`)는 한 줄에 NUMBER 1개라 자연 skip → 기존 §5.1 휴리스틱이 처리.

### 5.4 (c) 청크 간 overlap (~80자)

**적용 대상**: 일반 평문 청크에만. 표 청크는 헤더 prepend 가 같은 역할을 하므로 overlap 안 함.

**동작**:
- 새 청크를 생성할 때 직전 청크의 끝 80자(UTF-8 글자 단위, 공백 포함)를 가져와 prepend
- 80자 경계가 공백·문장부호가 아닌 글자 한가운데(한국어는 단어 경계가 공백)에서 끊기면, 직전 공백/문장부호까지 backtrack (최대 20자). backtrack 한도를 초과해도 공백을 못 찾으면 그냥 80자 hard cut
- chunk_index 의 의미는 그대로 (overlap 이 있어도 logical chunk index 는 1씩 증가)

### 5.5 파라미터 요약

| 항목 | 값 | 비고 |
|---|---|---|
| `maxChunkSize` | 500 (유지) | 표 인식 룰이 우선 적용되므로 그대로 |
| 표 인식 minimum row count | 3 | spec 명시. 추후 운영 보고 튜닝 |
| 표 헤더 추정 행 수 | 직전 1행 | 평문 종결 조건 시 헤더 없음으로 처리 |
| Overlap 크기 | 80자 | 표 청크에는 적용 X |
| 단일 줄 글자 cut 진입 임계 | maxChunkSize 초과 | last-resort fallback |

## 6. 데이터 흐름 / 부수 효과

### 6.1 source_hash 변경 처리

`computeHash` 의 입력은 content 전체 문자열이므로 chunker 변경만으로는 hash 가 변하지 않는다. 그러나 청크 content 자체가 변경되어 의미 캐시(`qna_question_cache`) 에 저장된 답변이 옛 청크 기준이 되어 일관성이 깨진다.

**해결**: `computeHash` 입력에 **chunker version 상수**(예: `"v2"`)를 추가로 섞는다.

```java
private static final String CHUNKER_VERSION = "v2";

public String computeHash(String content) {
    String input = CHUNKER_VERSION + ":" + content;
    // ... 기존 SHA-256 계산
}
```

이로써:
- 모든 청크의 source_hash 가 자동 변경됨 → 의미 캐시 자연 만료
- 미래 chunker 변경 시도 version 만 bump 하면 자동 처리
- 명시적 `QnaCacheInvalidator.invalidateAll()` 호출 불필요

### 6.2 재인덱싱 트리거

플랜에서 신규 추가되는 internal endpoint `POST /api/internal/ingestion/reindex/{policyId}` 를 사용. 내부적으로 `AttachmentReindexService.reindex(policyId)` 가 호출되어 정책 본문 + 첨부를 합친 content 를 chunker 에 전달.

```bash
# 1단계 — 정책 7번만
curl -X POST http://localhost:8080/api/internal/ingestion/reindex/7 \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY"

# 2단계 — 전체 정책 (검증 통과 후)
while read -r id; do
  curl -X POST "http://localhost:8080/api/internal/ingestion/reindex/${id}" \
    -H "X-Internal-Api-Key: $INTERNAL_API_KEY"
done < /tmp/policy_ids.txt
```

embedding 비용: 정책 200개 기준 ~$0.05 (text-embedding-3-small, $0.02/1M tokens) — 무시 가능.

## 7. Rollout

### 7.1 1단계 — 정책 7번 검증

1. PR 머지 → 서버 배포
2. 정책 7번 reindex 1회 호출
3. 아래 검증 query 모음으로 답변 품질 확인
4. 표 5개 중 ≥4개 정답 + 자연어 5개 회귀 없음 → 통과

### 7.2 2단계 — 전체 reindex

1단계 통과 시 운영 스크립트로 전체 정책 reindex. 영향 받는 의미 캐시는 source_hash 변경으로 자연 만료.

### 7.3 롤백

회귀 발견 시:
1. 새 chunker 코드 revert → 재배포
2. `CHUNKER_VERSION` 이 v1 로 돌아오면서 source_hash 도 원복 → 청크는 다시 옛 구조로 재생성
3. 전체 reindex 1회 호출

## 8. 검증

### 8.1 표 관련 query (정답이 있어야 함)

| Query | 기대 응답 |
|---|---|
| "중복수혜 안되는 통장 리스트 알려줘" | "중복 참여 불가" 표 내용 (기초생활보장/희망키움통장 1·2 등) |
| "어떤 통장이 중복수혜인지 리스트 알려줘" | 위와 동일한 표 회수 |
| "디딤씨앗통장 중복 가능한가요?" | "가능" (중복 참여 가능 사업 표에 있음) |
| "꿈나래통장 중복 가능한가요?" | "가능" |
| "안되는 통장 리스트" | 중복 불가 표 회수 |

### 8.2 자연어 query (회귀 감지 — 이전 품질 유지)

| Query | 회귀 기준 |
|---|---|
| "신청 자격이 뭐야?" | 기존 답변과 정보량 동등 |
| "지원 금액은 얼마야?" | 기존 답변과 수치 동일 |
| "신청 기간은 언제야?" | 기존 답변과 기간 동일 |
| "어디서 신청해?" | 기존 답변과 채널 동일 |
| "지원 대상은?" | 기존 답변과 대상 동일 |

### 8.3 판정

수동 비교. 위 표 5개 중 ≥4개 정답 + 자연어 5개 모두 회귀 없음 → 2단계 진입.

## 9. 위험 & 완화

| 위험 | 영향 | 완화 |
|---|---|---|
| 휴리스틱 false positive — "1. 신청 자격이 다음과 같다" 같은 번호 리스트가 표로 오인식 | 잘못된 헤더 prepend, 의미 왜곡 가능 | 직전 행이 평문(20자 초과 + 마침표/물음표/느낌표 종결)이면 헤더 없음으로 처리 |
| 표 block 헤더 prepend 로 청크 길이가 maxChunkSize 살짝 초과 | embedding 토큰 비용 미세 ↑ (무시 가능) | maxChunkSize 를 soft limit 로 취급 |
| 캐시 무효화 누락 시 옛 답변이 그대로 노출 | 사용자 혼란 | `CHUNKER_VERSION` 을 source_hash 입력에 포함 (§6.1) |
| 줄 단위 누적이 너무 잘게 쪼개서 짧은 청크가 많이 생성됨 | retrieval 시 noise ↑ | 누적 로직: 다음 줄을 더해도 maxChunkSize 안에 들어가면 무조건 합침. 청크가 짧으면 다음 청크와 강제 병합하는 후처리는 X (구현 단순화) |
| 표 인식 룰 N=3 이 너무 보수적/공격적 | retrieval 품질 변동 | spec 에 명시된 고정값으로 시작, 운영 데이터 기반으로 후속 튜닝 사이클 |
| Tika 가 표를 줄바꿈 없이 한 줄로 떨궈주는 PDF 도 있음 | 휴리스틱이 표를 인식 못 함 | v0 범위 X — 후속 사이클에서 공백 다중 패턴 인식 추가 검토 |

## 10. 관련 코드/파일

**변경**:
- `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` — 청킹 로직 본체
- `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java` — 신규 시나리오 테스트

**무변경 / 참조용**:
- `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` — 재인덱싱 진입점
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — retrieval (이번 사이클 변경 X)
- `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java` — 추출 형식 가정 근거

## 11. 운영 검증 결과 (PR 검증 단계, 머지 전 수행)

PR #91 로컬 검증 단계에서 정책 7번 reindex 후 §8 query 모음을 던진 결과:

| Query | 결과 | 비고 |
|---|---|---|
| 디딤씨앗통장 중복 가능? | ❌ 환각 (불가) | chunk #85 retrieved 안 됨 |
| 꿈나래통장 중복 가능? | ❌ fallback | 동일 |
| 중복수혜 안되는 통장 리스트 | ❌ fallback | chunk #82~83 retrieved 안 됨 |
| 지원 금액은 얼마야? | ✅ 정답 (30만원/10만원) | 회귀 없음 |

**Chunking 측은 의도대로 동작**:
- 청크 #85 본문: "표 항목: 디딤씨앗통장, 청년내일채움공제, 꿈나래통장, 청년희망적금, ..." prefix 첨가됨
- 청크 #82 본문: "표 항목: 청년재직자내일채움공제, ..." prefix 첨가됨
- 30+ 청크에 자연어 prefix 적용

**한계 — Retrieval 우선순위 한계**:
- 표 청크의 prefix 가 들어가도 자연어 query 와 embedding 거리(0.6+)가 다른 자연어 청크보다 멀어 top-10 에서 밀려남
- 자연어 query 와 더 가까운 청크 (예: chunk #80 "중복관리 대상사업 ... 디딤씨앗통장, 꿈나래통장 등 ... 중복 수혜 사실이 확인된 경우 즉시 참여 중단") 가 retrieve 되어 LLM 이 그 일반 설명을 답변으로 잘못 채택

**의미**: 이번 사이클의 chunking 개선은 데이터 보존 측면에서는 가치 있음 (chunk #85 가 답을 통째로 담고 있음). 정책 7번의 특정 query 효과는 retrieval 측 변경 (`docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md`) 이 함께 적용되어야 측정 가능. 따라서 이번 사이클은 chunking 개선만으로 머지하고 검증 query 정답률은 후속 사이클에서 평가.

## 12. 참고

- 임시 완화책 적용 이력 (이미 머지됨): PR #88 에서 `RagSearchService.DEFAULT_TOP_K` 5 → 10 확대
- 관련 트러블슈팅 노트: `docs/troubleshooting/2026-05-11-qna-rag-table-chunk-boundary-trouble.md`
- 모체 사이클: `docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md`
- 후속 사이클 spec: `docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md`
