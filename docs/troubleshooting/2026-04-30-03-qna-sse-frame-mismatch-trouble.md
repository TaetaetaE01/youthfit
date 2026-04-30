# Q&A SSE 답변이 빈 박스로 끊기던 문제 — 백엔드/프론트 이벤트 형식 불일치

- 작성일: 2026-04-30
- 작성자: TaetaetaE01
- 관련 커밋: `5d4ff1d` (`fix(frontend): Q&A SSE 이벤트 형식을 백엔드 spec 과 일치`)
- 관련 PR: #53
- 관련 모듈: `frontend/apis/qna.api.ts`, `backend/qna` (참고)

## 한 줄 요약

> Q&A 답변 박스가 빈 채로 표시되는 같은 증상에 두 개의 독립 root cause 가 있었음.
> 01 번 문서가 백엔드 SecurityContext 미전파를 다뤘다면, 본 건은 그 SSE 가 정상적으로
> 흘러도 **프론트가 이벤트를 모두 silently discard** 하던 별개 버그. 백엔드는 PRD spec
> 대로 `CHUNK/SOURCES/DONE/ERROR` 대문자 + `content` 필드로 보내는데 프론트는 OpenAI
> 호환 형식 (`content/sources` 소문자, `text` 필드, `[DONE]` 문자열) 을 가정하고 파싱.

## 1. 상황 (Context)

- 작업: Q&A v0 출시 마무리 디버깅 중. 01 번 문서의 SecurityContext fix 적용 후에도
  사용자 화면에 답변이 여전히 빈 박스로 보이고 곧 닫힘.
- 증상: SSE response 자체는 200 OK 로 시작되고 백엔드 로그상 `processQuestion` 정상 진행
  (RAG 검색 → LLM 호출 → 청크 emit). 그러나 프론트 채팅창에는 단 한 글자도 표시되지 않음.
- `onError` 콜백이 호출됐다면 "답변을 생성하지 못했습니다…" 메시지로 채워졌어야 하지만
  그것도 없음 → onError 미호출. `onDone` 만 호출되어 `loading=false` 로 전환,
  `content=''` 그대로 — 빈 박스 + 로딩 끝의 정확한 패턴.
- 영향 범위: 인증 통과한 모든 Q&A 호출 (즉 SecurityContext fix 후의 모든 정상 케이스).

## 2. 원인 (Root Cause)

- 백엔드 (`QnaService.sendChunkEvent` 등) 는 PRD `docs/prd/05-qna.md` spec 그대로
  대문자 type + `content` 필드로 emit:
  ```java
  Map.of("type", "CHUNK", "content", content)
  Map.of("type", "SOURCES", "sources", sources)
  Map.of("type", "DONE")
  Map.of("type", "ERROR", "content", message)
  ```
- 프론트 `frontend/src/apis/qna.api.ts` 는 OpenAI Chat Completions 의 SSE 형식을 기준으로
  파싱 (참조 코드를 다른 프로젝트에서 가져온 것으로 추정):
  ```ts
  if (parsed.type === 'content') {       // 소문자 'content' 기대
    onChunk(parsed.text);                 // 'text' 필드 기대
  } else if (parsed.type === 'sources') { // 소문자 'sources' 기대
    onSources(parsed.sources);
  }
  if (data === '[DONE]') { onDone(); return; }
  ```
- 결과:
  - `JSON.parse(data)` 자체는 성공 (백엔드가 valid JSON 발행).
  - `parsed.type === 'CHUNK'` 인데 프론트는 `'content'` 와만 비교 → 모든 분기 실패.
  - catch 블록에 들어갈 가능성도 없음 (parse 실패가 아님) → **모든 이벤트가 silent discard**.
  - response stream 이 끝나면 `while (true) { reader.read() }` 가 종료되며 fall-through 로
    `onDone()` 호출. UI 는 `loading=false, content=''` → 빈 박스.

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. 프론트를 백엔드 spec 에 맞춤 (대문자 type + `content` 필드 + `{type:'DONE'}` 분기) | PRD spec 이 백엔드 형식이고 운영 노트도 그 기준. 변경 범위 한 파일. | _(채택)_ |
| B. 백엔드를 OpenAI 호환 형식 (소문자 + text + `[DONE]`) 으로 변경 | 프론트 무변경. | PRD spec 위반. 운영 노트·테스트·캐시 직렬화 형식까지 영향. 변경 범위 크고 spec 일관성 깨짐. |
| C. 두 형식을 모두 지원하는 어댑터 레이어 | 호환성 보존. | 진단 시점에 한쪽이 옳다는 게 명확한데 양쪽 동시 지원은 과도. 미래에 형식이 갈라질 위험까지 새로 만듦. |

## 4. 선택과 이유 (Decision)

- **채택한 대안: A. 프론트를 백엔드 spec 에 맞춤.**
- **결정의 핵심 근거**:
  1. PRD `docs/prd/05-qna.md` 가 백엔드 형식을 spec 으로 정의하고 있음 — 백엔드가 옳고
     프론트 작성 시 다른 프로젝트 패턴을 그대로 가져온 게 실수의 원인.
  2. 변경 범위가 한 파일 (`qna.api.ts`).
  3. 백엔드의 ERROR 이벤트도 함께 정상 처리 가능해짐 — 그동안 프론트는 ERROR 분기 자체가
     없었음.
- **트레이드오프로 받아들인 것**:
  - 백엔드 SSE 이벤트 형식이 파편적으로 다른 SDK 와 다르다는 점 (OpenAI 호환 아님).
    PRD 가 명시적으로 정의했으므로 의도된 차이지만, 새 프론트 작업자가 헷갈릴 위험은 남음.
- **가역성**: 매우 높음. 한 파일 되돌리면 원복.
- **재검토 신호**: 백엔드 SSE 형식을 OpenAI 호환으로 바꿀 일이 생기면 (예: 프론트 SDK
  교체) 프론트도 동시 변경.

## 5. 해결 (Solution)

- 변경 파일: `frontend/src/apis/qna.api.ts`
- 변경 내용 (요지):
  ```ts
  // 변경 후
  if (parsed.type === 'CHUNK') {
    onChunk(parsed.content ?? '');
  } else if (parsed.type === 'SOURCES') {
    // 객체 배열을 표시용 문자열 배열로 매핑 (attachmentLabel + p.<start>-<end>)
    const sourceStrings: string[] = (parsed.sources ?? []).map(...);
    onSources(sourceStrings);
  } else if (parsed.type === 'DONE') {
    onDone();
    return;
  } else if (parsed.type === 'ERROR') {
    onError(new Error(parsed.content ?? '답변 생성 중 오류가 발생했습니다'));
    return;
  }
  ```
- 부수 효과: 없음. 빌드/배포 외 인덱싱·캐시 무효화 불필요 — 단 04 번 문서 (Redis 캐시 히트)
  의 영향으로 같은 질문은 한 번 캐시 무효화 필요할 수 있음.

## 6. 검증 (Result)

- 재현 시나리오 (수정 후):
  1. 정책 7 에 "누가 받을 수 있어?" 질문 → 답변 박스에 토큰 단위 스트리밍 노출 확인
  2. 답변 종료 후 출처(SOURCES) 가 한 줄씩 표시되는지 확인
- 회귀 위험: 낮음. 옛 형식을 보내는 백엔드는 없고, 새 형식의 type 이름을 잘못 적었다면
  타입 에러로 즉시 발견됨.
- 모니터링 포인트:
  - 사용자 측 콘솔에서 `try { JSON.parse } catch` 분기로 떨어지는 빈도 — 0 이어야 정상.
  - SOURCES 객체 형태가 백엔드에서 다시 바뀐다면 프론트 매핑 함수도 재검토.

## 7. 후속 / 미결 (Follow-ups)

- (가드) PRD `05-qna.md` 의 SSE 응답 형식을 프론트 코드에서 인용 주석으로 박아 향후
  새 작업자가 OpenAI 호환 패턴을 그대로 가져오지 않도록 표지.
- (테스트) 프론트 SSE 파서에 단위 테스트 — 현재 없음. 형식 변경 시 회귀 보호용으로
  v0+ 후속 (frontend 테스트 인프라가 미흡한 게 더 큰 이슈 — 별도 spec 후보).
- (관찰) 같은 패턴의 프론트/백엔드 spec mismatch 가 다른 엔드포인트에서도 잠복했을 가능성 —
  v0 안정화 시기에 한 번 sweep.

## 8. 참고 (References)

- PRD: `docs/prd/05-qna.md` (SSE 응답 형식 spec)
- 함께 다뤘던 같은 증상의 다른 원인: `2026-04-30-01-qna-securitycontext-virtual-thread-trouble.md`
- 캐시 히트로 fix 효과가 가려졌던 후속 함정: `2026-04-30-04-qna-redis-cache-stale-after-fix-trouble.md`
