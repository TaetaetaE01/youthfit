# Q&A 품질 개선 사이클 — 검증 단계에서 드러난 spec 디자인 결함과 hot fix

- 작성일: 2026-05-02
- 작성자: TaetaetaE01
- 관련 커밋: `7b0e1a8` (Task 7 — passing.isEmpty 시 LLM 호출), `8476814` (Task 8 — threshold 0.78 + 안전망 + 메타 sources), `6aedd2b` (spec/plan 갱신), `93a53f8` (DONE_ rename)
- 관련 PR: #56 (`feat(qna): Q&A 품질 개선 (출처 정합성 + 메타 질문 대응)`)
- 관련 모듈: `backend/qna`

## 한 줄 요약

> Q&A 품질 개선 spec 의 두 핵심 가정 — (a) `passing.isEmpty()` 시 LLM 호출 안 해도 메타 답변이 가능하다, (b) `relevance-distance-threshold=0.5~0.7` 이 충분히 느슨하다 — 모두 실측과 달랐다. 도커 수동 검증 단계에서 결함이 드러나 같은 사이클·같은 PR 안에서 Tasks 7, 8 로 hot fix 했다.

## 1. 상황 (Context)

### 사이클 진입 배경

PR #55 (Q&A 의미 캐시 v0) 머지 직후, v0 검증 중 발견된 두 이슈를 다음 사이클로 가져왔다.

- **이슈 1 (출처 정합성)**: LLM 이 "관련 내용 없음" 으로 답해도 sources 박스에 청크 5개가 그대로 표시되는 모순.
- **이슈 2 (메타 질문)**: "이 정책 뭐야?", "누가 대상자야?" 같은 메타 질문에 항상 "찾을 수 없다" 응답.

spec (`docs/superpowers/specs/DONE_2026-05-01-qna-quality-improvements-design.md`) 의 결정은:

- 이슈 1 → `relevance-distance-threshold` 0.7 → **0.5** 로 조이면 미관련 청크가 LLM 까지 도달하지 못해 출처 모순 해소.
- 이슈 2 → `Policy` 9필드를 `PolicyMetadata` record 로 묶어 user message 에 포함, 시스템 프롬프트를 "본문 우선, 메타 보강" 으로 변경.

Tasks 1~5 가 TDD 흐름으로 진행되어 단위 테스트 18개 PASS, PR 후보 상태로 도커 검증에 진입.

### 검증 중 발견된 증상

도커 환경에서 같은 정책(청년내일저축계좌, policyId=7)에 메타 질문을 던지자:

```
"누가 대상자야?"
→ 해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.

"어떤 사람이 받을 수 있어?"
→ 해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.

"무슨 정책이야?"
→ 해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.
```

본 사이클이 정확히 **고치려고 했던 증상이 그대로 재현**됐다. 사용자 표현으로 "고장났는디".

## 2. 원인 (Root Cause)

두 가지 잘못된 spec 가정이 직렬로 작용했다.

### 원인 A — `passing.isEmpty()` 분기 LLM 호출 누락 (Task 7)

`QnaService.processQuestion` 의 흐름:

```java
// backend/.../qna/application/service/QnaService.java
List<PolicyDocumentChunkResult> passing = chunks.stream()
        .filter(c -> c.distance() <= threshold)
        .toList();

if (passing.isEmpty()) {
    rejectAndComplete(emitter, historyId, NO_RELEVANT_MESSAGE, QnaFailedReason.NO_RELEVANT_CHUNK);
    return;  // ← LLM 호출 전에 early return
}
```

spec § 5 의 해결책 ("정책 메타데이터를 user message 에 포함") 은 LLM 이 호출되어야 의미가 있다. 하지만 메타 질문은 본문 청크와 의미적 거리가 멀어 청크 통과율 0% 가 되기 쉽고, 이 경우 LLM 호출 자체가 안 일어나 메타데이터가 LLM 에 도달할 기회가 없었다. **spec 작성 시 이 흐름을 인지하지 못했다.**

### 원인 B — 한국어 임베딩 실측 distance 분포 vs spec 가정 불일치 (Task 8)

원인 A 를 fix 하니 메타 질문은 답변되기 시작했지만, 본문에 명백히 있는 정보 ("중복 수혜 안 되는 통장", "프리랜서 가능 여부") 도 답변 못 했다. 운영 백엔드 로그 확인:

```
2026-05-01T18:37:19.611Z INFO ...RagSearchService :
  RAG 검색 결과: policyId=7, top5=[0.709, 0.714, 0.725, 0.726, 0.732]
```

가장 가까운 청크조차 distance **0.709**. **OpenAI `text-embedding-3-small` 의 한국어 짧은 질문 vs 긴 정책 본문 청크의 cosine distance 는 본질적으로 0.7~0.75 가 기본 분포**다. spec § 4.1 의 "0.7 이 느슨하다 → 0.5 로 조이면 적당히 빡빡" 추정은 실측 분포와 정반대였다 — 0.5 도 0.7 도 사실상 빡빡한 임계값이었다.

이 결과로:

- 메타 질문: passing 0건 → empty context → 메타에 정보 있으면 답변, 없으면 fallback
- **본문 질문**: passing 0건 → empty context → 메타에 그 정보 없음 → fallback ❌
- 본문 청크가 사실상 사용 불가 상태

### 원인 C — 메타 답변 sources 부재의 사용자 경험 결함

spec § 7 은 "메타데이터는 sources 에 포함하지 않음 (정형 필드라 인용 단위가 아님)" 으로 결정했지만, 검증 중 사용자 피드백:

> "메타데이터에서 뽑았으면 메타데이터를 소스로 줘야 할 거 같아"

답변에 출처가 없으면 사용자 신뢰감이 떨어진다. spec 의 디자인 결정이 사용자 경험 측면에서 잘못된 절충이었다.

### 잘못된 가정 정리

| spec 가정 | 실측 | 영향 |
|---|---|---|
| `passing.isEmpty()` 시 reject 가 자연스럽다 | 메타 질문 답변에 LLM 호출이 필수 | 메타 질문 항상 fallback |
| 한국어 임베딩 distance 0.7 은 느슨, 0.5 적정 | 실측 0.7~0.75 가 기본 분포 | 본문 청크가 거의 통과 못 함 |
| 메타데이터는 sources 단위가 아님 | 사용자는 답변에 출처가 있어야 신뢰 | 출처 없는 답변 |

## 3. 고려한 대안 (Alternatives)

### 원인 A 해결 — Task 7 의 대안

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A1. `passing.isEmpty()` 시 빈 컨텍스트 + 메타데이터로 LLM 호출 (채택) | spec 의도(메타로 답변)가 정상 동작. 무관 질문은 LLM 이 fallback 메시지 반환 | 무관 질문에도 LLM 호출 비용 발생 (의미 캐시가 반복 보호) |
| A2. threshold 만 풀어서 청크 통과율을 높이는 우회 | 흐름 변경 없음 | 메타 질문 본질 해결 안 됨 (본문 청크와 메타 질문은 임베딩 거리 자체가 멈) |
| A3. 메타 질문 사전 분기 (질문 분류 LLM 추가) | 정확한 분기 | LLM 호출 1번 추가 → 비로그인 핫패스 비용 증가, 룰 유지 부담. 의미 캐시 v1 (intent 기반) 에서 자연 통합 가능. 본 사이클엔 과함 |

### 원인 B 해결 — Task 8 의 대안

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| B1. threshold 0.78 (실측 분포 + 약간 위) | 본문 청크가 LLM 에 도달, 답변 정상화 | 무관 질문도 청크 통과 → 옛 출처 모순 재발 우려 (안전망으로 차단 필요) |
| B2. 임베딩 모델 교체 (한국어 강한 모델) | 근본 해결 | 변경 범위 매우 큼. 본 사이클 외부 |
| B3. 하이브리드 검색 (BM25 + vector) | 한국어 키워드 매칭 강화 | 인프라 큼. 본 사이클 외부 |
| B4. Cross-encoder reranking | 정확도 향상 | 추가 모델 호출 → 비용·지연. 본 사이클 외부 |

→ 본 사이클 안에서 가능한 빠른 해결은 **B1**.

### B1 의 부작용 — fallback 안전망 갈래

threshold 0.78 로 풀면 무관 질문도 청크가 통과해 sources 에 노출 → 옛 이슈 1 모순 재발. 이를 막기 위한 안전망:

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| 안전망 A. 안전망 없음 (spec § 9 옵션 A) | 단순 | threshold 0.78 부작용 그대로 노출. 옛 이슈 1 재발 |
| 안전망 B. LLM 응답 패턴 매칭 (`"명시되어 있지 않"`) (채택) | 단순, system prompt 의 fallback 메시지가 deterministic 함 | 표현 변형 시 매칭 실패 가능 (system prompt 가 정확한 문장 강제하니 안정적) |
| 안전망 C. NO_ANSWER 토큰 합의 | 깔끔한 시그널 | LLM 이 합의 토큰 항상 지킨다는 보장 없음 (확률적). 시스템 프롬프트도 변경 |

### 원인 C 해결 — 메타 답변 sources 갈래

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| C1. `passing.isEmpty()` 시 "정책 기본 정보" sources entry 1개 추가 (채택) | 사용자 출처 가시성, 단순, 기존 record 구조 그대로 | 제목·발췌가 단순 (정책 상세 페이지 링크 없음) |
| C2. 메타 필드별 entry 분리 (요약 entry, 지원 대상 entry, ...) | 풍부한 출처 | 복잡, 사용자에게 과한 정보 |
| C3. 새 필드 `sourceType` 추가 (BODY / METADATA) | 명시적 구분 | DTO 시그니처 변경, 변경 범위 큼 |

## 4. 선택과 이유 (Decision)

### 채택한 종합 fix

- **Task 7**: 대안 A1 — `passing.isEmpty()` 시 빈 컨텍스트 + 메타로 LLM 호출
- **Task 8 / Fix A**: 대안 B1 — threshold 0.5 → **0.78**
- **Task 8 / Fix B**: 안전망 B — `isFallbackAnswer(answer)` 패턴 매칭 (`"명시되어 있지 않"`) 시 sources 비움
- **Task 8 / Fix C**: 대안 C1 — `(policyId, null, "정책 기본 정보", null, null, "정책 메타데이터 기반 답변")` entry 1개

### 결정의 핵심 근거

1. **사이클 동질성**: 두 결함 모두 본 사이클의 두 이슈와 직결. 별도 사이클로 미루면 PR #56 의 가치 자체가 의심됨. 같은 PR 안에서 fix 가 옳다.
2. **변경 범위 최소화**: 임베딩 모델 교체나 하이브리드 검색 같은 큰 변경은 위험. 가장 작은 변경(yml 값, 흐름 한 분기, 헬퍼 1개)으로 본질 해결.
3. **운영 가역성**: threshold 는 env var 로 즉시 튜닝. fix B 패턴 매칭은 system prompt 와 deterministic. fix C 는 sources 한 줄 추가/제거.

### 트레이드오프로 받아들인 것

- 무관 질문도 LLM 호출되어 비용 증가 — 의미 캐시 보호 + per-user QPS 제한은 follow-up 으로.
- fix B 의 패턴 매칭은 system prompt 변경 시 함께 갱신 필요 (Javadoc 에 명시).
- fix C 의 sources entry 는 단순 형태 — 정책 상세 페이지 링크 같은 풍부화는 후속.

### 가역성

- threshold: env var `QNA_RELEVANCE_DISTANCE_THRESHOLD` 로 즉시 변경 가능 (운영에서 0.74 등으로 미세 조정).
- 안전망 B 끄기: `isFallbackAnswer` 가 항상 false 반환하도록 1줄 변경.
- 메타 sources entry 끄기: 1 분기 제거.

재검토 트리거:

- LLM TTFT 가 운영에서 너무 길면 → top-K 5 → 3 검토
- fallback false positive 가 누적되면 → 패턴 정밀화 또는 NO_ANSWER 토큰 합의
- 무관 질문 비용이 의미 있는 수준이 되면 → per-user QPS / 입력 검증

## 5. 해결 (Solution)

### 5.1 Task 7 — `QnaService.processQuestion` 흐름 변경

`backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`:

```java
// 변경 전
if (passing.isEmpty()) {
    rejectAndComplete(emitter, historyId, NO_RELEVANT_MESSAGE, QnaFailedReason.NO_RELEVANT_CHUNK);
    return;
}
String context = buildContext(passing);
List<QnaSourceResult> sources = buildSources(command.policyId(), passing);

// 변경 후
String context;
List<QnaSourceResult> sources;
if (passing.isEmpty()) {
    context = "(본문에서 관련 청크를 찾지 못했습니다.)";
    sources = List.of();
} else {
    context = buildContext(passing);
    sources = buildSources(command.policyId(), passing);
}
```

부수: 미사용 `NO_RELEVANT_MESSAGE` 상수 제거, 옛 테스트 `Reject.allChunksOverThreshold_failsWithNoRelevantChunk` 삭제, 새 테스트 `Happy.emptyPassingChunks_addsMetaSourceEntry` 로 대체. `QnaFailedReason.NO_RELEVANT_CHUNK` enum 값은 다른 테스트 참조로 보존.

### 5.2 Task 8 — application.yml threshold

```yaml
qna:
  # 한국어 임베딩 cosine distance. 실측 분포는 0.7~0.75. 더 엄격: 0.72~0.75, 더 관대: 0.80~0.85.
  relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.78}
```

### 5.3 Task 8 — 안전망 + 메타 sources entry

`QnaService.java` 의 LLM 호출 후, `sendSourcesEvent` 전:

```java
// Fix B + C
if (isFallbackAnswer(fullAnswer)) {
    sources = List.of();
} else if (passing.isEmpty()) {
    sources = List.of(new QnaSourceResult(
            command.policyId(), null, "정책 기본 정보", null, null,
            "정책 메타데이터 기반 답변"
    ));
}
```

```java
private static boolean isFallbackAnswer(String answer) {
    return answer != null && answer.contains("명시되어 있지 않");
}
```

### 5.4 마이그레이션·부수 효과

- DB 스키마 변경 **없음**.
- env var `QNA_RELEVANCE_DISTANCE_THRESHOLD` 로 0.5 또는 0.7 명시 설정한 환경이 있다면 0.78 로 갱신 권장.
- 기존 `qna_question_cache` (의미 캐시) 의 row 는 옛 답변 그대로 — 새 코드 동작 확인 위해 검증 시 `TRUNCATE TABLE qna_question_cache` + Redis `qna:answer:*` DEL 권장.

## 6. 검증 (Result)

### 6.1 자동 테스트

`./gradlew build` BUILD SUCCESSFUL, 18 tests PASS:

- `PolicyMetadataTest` (2): record 9필드 매핑
- `OpenAiQnaClientTest` (4): user message 포맷 + null 처리
- `QnaServiceTest` (12): 메타 captor, 안전망, 메타 sources entry, 회귀 검증

### 6.2 도커 환경 수동 검증

| 카테고리 | 질문 | 결과 |
|---|---|---|
| 메타 | "누가 대상자야?" | 본문 + 메타 종합 답변 + 본문 출처 정상 ✅ |
| 메타 | "이 정책 간략하게 설명해줘" | 메타 기반 답변 + 출처 정상 ✅ |
| 본문 | "프리랜서도 가능이야?" | 본문 기반 답변 + 출처 정상 ✅ |
| 무관/인사 | "고마웡" | fallback 메시지 + sources **빈 배열** ✅ |

사용자 평가: "이정도면 꽤나 잘뜨는 거 같네".

### 6.3 회귀 위험과 모니터링 포인트

- threshold 0.78 부작용으로 LLM TTFT 증가 가능 — qna_history 의 작성→완료 시간 차이 또는 actuator 추가로 추적.
- fallback 패턴 false positive 누적 — `qna_history.answer LIKE '%명시되어 있지 않%'` 비율 모니터링.
- 무관 질문 LLM 호출 비용 — 같은 답변 텍스트가 의미 캐시 hit 으로 재사용되는지 확인.

## 7. 후속 / 미결 (Follow-ups)

본 사이클에서 의도적으로 뒤로 미룬 항목:

- **운영 KPI 측정** — `qna_history` 5개 SQL 쿼리 (fallback 비율, 거절률, sources 빈 배열 비율, 메타 entry 사용 빈도, 청크 통과 분포) 를 1~2주 누적 후 측정. spec § 14.4 참조.
- **무관 질문 비용 방어** — per-user QPS 제한 또는 입력 길이/언어 검증 (입력 길이 < 3 거절 등). v1 후보. 자세한 분석은 PR #56 의 code review 응답 기록 참조.
- **fallback 패턴 vs system prompt 동기화** — `OpenAiQnaClient.SYSTEM_PROMPT` 와 `QnaService.isFallbackAnswer` 가 동일 문구를 알아야 함. 공통 상수로 추출하면 동기화 안전성 ↑. 다음 qna 터치 시 정리.
- **top-K 5 → 3 검토** — threshold 0.78 + top-K 5 = 청크 5개 모두 LLM 컨텍스트로. 응답 지연이 운영 KPI 에서 임계 초과 시 top-K 줄이기.
- **임베딩 모델 / 하이브리드 검색** — 한국어 distance 분포 자체를 개선. v1+ 후보. ROI 운영 데이터 보고 결정.
- **Micrometer + Prometheus + Grafana** — 자동 메트릭/대시보드. 현재 actuator 는 `health` 만 노출. v0.5 후보.

### 가드레일 (재발 방지)

- spec 작성 시 임베딩 distance 같은 운영 의존 값은 **실측 데이터 확인 후** 결정. 추정만으로 임계값 정하지 말 것.
- spec 의 해결책 흐름이 코드의 early-return 분기와 충돌하지 않는지 **흐름도** 로 확인. Task 7 의 결함은 단위 테스트만으론 못 잡았다 — 통합 흐름 점검 필요.

## 8. 참고 (References)

- spec: `docs/superpowers/specs/DONE_2026-05-01-qna-quality-improvements-design.md` (§ 14 검증 후 발견 섹션)
- plan: `docs/superpowers/plans/DONE_2026-05-01-qna-quality-improvements.md` (Tasks 7, 8 추가 섹션)
- PR: [#56](https://github.com/TaetaetaE01/youthfit/pull/56) (`feat(qna): Q&A 품질 개선 (출처 정합성 + 메타 질문 대응)`)
- 선행 사이클 spec: `docs/superpowers/specs/2026-05-01-semantic-qna-cache-design.md` (PR #55, 의미 캐시 v0)
- 관련 v1 후보 spec: `docs/superpowers/specs/v1-semantic-cache-intent-based.md` (의도 기반 캐시 — 메타 질문 사전 분류와 자연 통합)

### 결정적이었던 사용자 메시지 인용

- "고장났는디" — Task 6 검증 단계에서 spec 결함이 드러난 순간.
- "메타데이터에서 뽑았으면 메타데이터를 소스로 줘야할 거 같아" — Fix C 의 직접적 트리거.
- "이정도면 꽤나 잘뜨는 거 같네" — Tasks 7, 8 fix 후 검증 OK 신호.
