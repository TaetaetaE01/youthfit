# RAG 표 인식 청킹 개선 (v1 pre-spec)

> 다음 brainstorming 세션에서 이 문서를 컨텍스트로 사용. 문제 진단·근본 원인·옵션 후보까지 정리해뒀으니 바로 옵션 결정 단계부터 시작 가능.

## 발견 경위

2026-05-11 답변 풍부도 강화(PR-1~5) 수동 검증 중, 7번 정책(청년내일저축계좌)에서 다음 질문들이 fallback 답변을 받음:

- "중복수혜 안되는 통장 리스트 알려줘" → fallback
- "안되는 통장 리스트 알려줘" → fallback
- "어떤 통장이 중복수혜인지 리스트 알려줘" → fallback

또 답변되더라도 부정확:
- "중복수혜안되는 정책도 있어?" → "디딤씨앗통장, 꿈나래통장이 불가" (사실 디딤씨앗통장·꿈나래통장은 **중복 가능** 사업)

PDF에는 명백히 정답 데이터가 있음 (page 35 "중복 참여 불가 사업" 표 + page 35-36 "중복 참여 가능 사업" 표).

## 디버깅 결과

### 데이터는 인덱싱돼 있음

```sql
SELECT chunk_index, page_start, page_end, LENGTH(content) FROM policy_document
WHERE policy_id = 7 AND content ILIKE '%중복%' ORDER BY chunk_index;
-- chunk #62 (p.34-35, 500자) -- "중복 참여 불가" 표 시작
-- chunk #63 (p.35,    146자) -- 표 중간 fragment
-- chunk #64 (p.35-36, 500자) -- "중복 참여 가능" 표 시작
```

### Retrieval은 핵심 청크를 못 잡음

`top-K=5`에서도 `top-K=10`에서도 chunk #62, #63, #64 모두 retrieved 안 됨. 인근 자연어 청크(#60 "중복관리 대상사업…")만 잡힘.

### 근본 원인: DocumentChunker.splitBySize의 hard cut

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

PDF에서 추출된 표가 한 단락(`\n\n` 미포함)으로 1000자+ 들어오면, `paragraphAwareSplit`이 단락을 청크에 못 담아 `splitBySize`로 위임. 그러면 **500자 단위 무조건 hard cut** — 표의 행 boundary, 헤더, 의미 단위 모두 무시.

결과:
- chunk #62 = 헤더 "중복 참여 불가 사업번호 사업구분 시행기관" + 1~22번 + 23번 절반
- chunk #63 = 23번 후반 + 24~29번 (헤더 잃음, "기도24 열혈청년 패키지사업…" 으로 시작 — 무슨 표인지 알 수 없음)
- chunk #64 = 다음 표 "중복 참여 가능 사업" 시작

### LLM 환각 메커니즘

- chunk #60 (헤더 "중복관리 대상사업")가 retrieved
- #60 본문 중 "디딤씨앗통장" 키워드 등장
- LLM이 #60의 헤더 맥락("중복관리/불가")과 본문 키워드("디딤씨앗")를 결합 → "디딤씨앗통장 = 불가" 잘못 추론
- 진짜 디딤씨앗통장 분류 정보가 있는 chunk #64는 못 봤기에 정정 불가

즉 단순 vector embedding 거리 문제가 아니라, **chunking이 의미 단위(표)를 깨뜨려 헤더가 분리됐고 LLM이 잘못된 컨텍스트로 추론**.

## 영향 범위

표 형태 데이터를 가진 모든 정책 영향. 자활사업안내처럼 표가 많은 PDF가 가장 큼.
- 청년내일저축계좌(id=7): "중복 참여 불가/가능 사업" 표 다수
- 다른 정책들도 신청자격/지원금액 표가 있으면 동일 영향
- "리스트 알려줘", "표 보여줘" 패턴 query 전반

또 이 패턴은 **답변 정확성 위협** — 단순 retrieval 실패가 아니라 LLM이 부분 정보로 잘못된 결론 도출.

## 옵션 후보

| 옵션 | 무엇 | 비용 | 효과 |
|---|---|---|---|
| **F1. maxChunkSize 확대** | 500 → 1500 등 | 작음 (코드 1줄). 재인덱싱 필요 | 한 표를 한 청크에 담을 수 있으나 큰 표는 여전히 cut. embedding 정밀도 일부 ↓, OpenAI 비용 ↑ |
| **F2. 표/리스트 패턴 인식** | `splitBySize` 호출 전 markdown table 또는 "번호 \| ..." 패턴 매칭 → 표는 통째 한 청크로 (또는 행 boundary로 split하면서 헤더 보존) | 중간 (코드 100줄+테스트) | 근본 해결. 패턴 정의 필요 |
| **F3. Overlap chunking** | 청크 간 50~100자 overlap 두기 | 작음 (코드 ~10줄). 재인덱싱 필요 | boundary 깨도 인접 청크가 일부 회수. 부분 해결 |
| **F4. 줄 단위 보존** | `\n` 단위 split + 누적, 줄 자체는 절대 자르지 않음 | 중간 | 표 행이 \n로 분리된 경우만 효과 |
| **F5. PDF 추출 단계 개선** | extractor가 표를 markdown table 형식으로 추출 → \n\n로 자연 분리 → 청커는 그대로 | 큰 작업 | 근본 해결, 모든 정책 영향. ingestion pipeline 큰 변경 |

## 다음 brainstorming 결정 사항

1. **선택할 옵션** — F1~F5 중 단독 또는 조합. 추천 시작점: F2(표 인식) + F3(overlap)
2. **maxChunkSize 결정** — F1 채택 시 몇 자가 적절한가 (1000? 1500? 2000?)
3. **표 인식 패턴** — F2 채택 시 어떤 패턴을 인식할지
   - markdown 표 (`|...|`)
   - "번호 \\d+ ..." 반복 패턴
   - "사업구분 시행기관" 같은 헤더 키워드
4. **재인덱싱 범위와 비용** — 모든 정책 vs 영향 큰 정책만. OpenAI embedding 비용 추정 필요
5. **회귀 검증 전략** — 다른 정책 답변 품질이 떨어지지 않는지
6. **Source-hash 변경 처리** — chunker 변경 시 모든 청크의 source_hash가 바뀌어 의미 캐시 자연 만료. 기존 캐시 영향 평가
7. **점진적 rollout** — 한 정책에 먼저 적용해서 결과 비교 후 전체 확대

## 시급도

**중간**. 답변 풍부도 강화(PR-1~5)는 잘 작동하고 fallback 자체는 graceful degrade. 단 사용자 신뢰도 저하 위험 있음 (잘못된 답변 케이스 발견됨). 다음 sprint 또는 별도 spike 권장.

## 임시 완화책 (이미 적용됨)

- `RagSearchService.DEFAULT_TOP_K` 5 → 10 확대 (`9b1aa86` 또는 인접 commit)
- 효과: 표 청크는 여전히 못 잡지만 인근 자연어 청크 retrieval 안정성 ↑

## 시작 시점 컨텍스트 (다음 세션용)

1. 이 문서를 읽고 옵션 평가 시작
2. `DocumentChunker.java`와 `RagSearchService.java`가 핵심 코드
3. 7번 정책(청년내일저축계좌)이 재현하기 좋은 테스트 케이스
4. 검증 query 모음:
   - "중복수혜 안되는 통장 리스트 알려줘"
   - "어떤 통장이 중복수혜인지 리스트 알려줘"
5. PR 단위로 분할 시 — 청커 변경 + 재인덱싱은 한 PR(F1) vs 여러 PR(F2 + 재인덱싱 분리) 결정 필요

## 관련 코드/파일

- `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` (현 청킹 로직)
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` (retrieval, top-K)
- `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` (재인덱싱 트리거)
- `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java` (있다면 — 청크 boundary 테스트 베이스)
- `backend/src/main/resources/application.yml` (rag/qna properties)
