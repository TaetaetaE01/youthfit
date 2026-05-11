# Q&A 답변 풍부도 강화 설계

작성일: 2026-05-11
대상 모듈: `qna` (백엔드), `frontend/src/components/qna`, `frontend/src/apis/qna.api.ts`

## 배경

온통청년 챗봇은 정책 질의에 대해 마크다운 섹션·이모지 헤더·후속 추천 질문·공식 문의처 안내를 함께 제공해 사용자가 다음 행동을 결정하기 쉽게 만든다. YouthFit의 QnA는 현재 단일 정책 컨텍스트 기반으로 동작하며 답변 본문과 출처만 송출한다. 답변 자체의 품질·환각 방지는 만족스럽지만, 시각적 구조화와 후속 행동 제안이 약하다.

본 설계는 단일 정책 Q&A 흐름을 유지하면서 다음 4가지 요소를 추가해 답변 풍부도를 끌어올린다.

- A. 답변 마크다운 형식 강화 (시스템 프롬프트)
- B. 답변별 후속 추천 질문 (별도 LLM 호출)
- C. 공식 문의처 푸터 (코드 후처리)
- D. 출처 카드 펼치기 (프론트엔드)

자유 질의(정책 미지정) 라우팅·다중 정책 답변 통합은 본 설계 범위에 포함하지 않는다.

## 목표

- 답변이 정보량에 비례해 자연스럽게 마크다운 구조를 갖는다.
- 답변 직후 사용자가 같은 정책 안에서 이어가기 쉬운 후속 질문 2~3개를 칩으로 제공한다.
- 정책 메타데이터에 공식 문의처가 있을 때 답변 끝에 코드로 푸터를 첨부한다 (LLM 환각 방지).
- 출처 카드는 컴팩트한 한 줄 표시 + 발췌 펼치기로 신뢰성과 가독성을 동시에 만족시킨다.

## 비목표

- 정책 미지정 자유 질의 처리 (별도 spec 필요).
- 후속 질문에서 다른 정책으로 점프 (환각·범위 이탈 방지).
- 카테고리별 정적 추천 질문 템플릿 (LLM 동적 생성으로 일원화).
- 기존 캐시 강제 무효화 (자연 만료까지 호환 유지).

## 아키텍처

```
[기존]
질문 → 캐시 → RAG → LLM 스트림 → SOURCES → DONE

[변경 후]
질문 → 캐시 → RAG → LLM 스트림 → SOURCES
                                      ↓ 추가 단계
                                follow-up 생성 (LLM 1회)
                                      ↓
                                푸터 첨부 (메타에 둘 다 있을 때)
                                      ↓
                                SUGGESTIONS 이벤트 → DONE
```

DDD 레이어 의존 방향(Presentation → Application → Domain)은 유지된다. follow-up은 어플리케이션 포트(`QnaLlmProvider`) 메서드로 추가하고, OpenAI 어댑터(`OpenAiQnaClient`)가 구현한다.

## 슬라이스 분할 (PR 단위)

| # | 슬라이스 | 변경 영역 | 의존성 |
|---|---|---|---|
| PR-1 | 시스템 프롬프트 마크다운 가이드 강화 (A) | 백엔드 1파일 | 없음 |
| PR-2 | 출처 카드 펼치기 (D) | 프론트엔드만 | 없음 (병렬 가능) |
| PR-3 | 공식 문의처 푸터 후처리 (C) | 백엔드만 | PR-1 권장 |
| PR-4 | 후속 추천질문 백엔드 (B 1/2) | 백엔드: 포트 메서드, SSE 이벤트, 캐시 | PR-1 |
| PR-5 | 후속 추천질문 프론트엔드 (B 2/2) | 프론트엔드: SSE 파서, Chip UI 재사용 | PR-4 |

PR-1, PR-2는 다른 PR에 영향 0. PR-4와 PR-5를 분리해 백엔드 단독 머지하면 SSE 이벤트는 흐르되 프론트가 무시(역호환).

## 백엔드 상세

### A-1: 시스템 프롬프트 강화

`OpenAiQnaClient.SYSTEM_PROMPT`에 출력 형식 가이드 추가. 환각 금지 룰은 그대로 유지.

```
출력 형식 가이드:
- 정보가 여러 항목이면 글머리 기호(`-`)로 정리하세요.
- 핵심 수치(금액·기간·연령·횟수)는 굵게(`**...**`) 표시하세요.
- 본문이 2개 이상의 명확한 섹션으로 나뉠 때만 `###` 헤더를 쓰세요.
  헤더 앞에 이모지를 1개까지 쓸 수 있으나, 정확히 맞는 경우에만.
- 짧은 답이면 평문 한두 줄로도 충분합니다 — 형식을 위한 형식은 피하세요.
```

### B-2: 후속 질문 생성 (별도 LLM 호출)

포트 인터페이스 확장 (`QnaLlmProvider`):

```java
List<String> generateFollowUpQuestions(String policyTitle, String question, String answer);
```

- 어플리케이션 레이어가 호출, 실패 시 빈 리스트 반환 → graceful degrade.
- temperature 0.3, max_tokens ~150.
- 응답 형식: 시스템 프롬프트가 `["질문1", "질문2", "질문3"]` JSON 배열만 출력하도록 강제 → `objectMapper.readValue(..., String[].class)` 파싱.
- LLM 비용 메트릭(`LlmCallRecorded`) 발행 — 본문 호출과 동일.
- timeout 10초 — 본문 응답 후 사용자 대기 무한 늘어나는 것 방지.

스킵 조건:
- fallback 답변 (`isFallbackAnswer == true`).
- chunks 0건 거절 (`NO_INDEXED_DOCUMENT`).
- 답변 캐시 히트 (캐시에 follow-up까지 함께 저장돼 있음).

### C-1: 공식 문의처 푸터

`QnaService.processQuestion`에서 LLM 응답 직후 후처리:

```java
if (metadata.organization() != null && metadata.contact() != null
    && !isFallbackAnswer(answer)) {
    answer = answer + "\n\n---\n\n📞 문의: " + organization + " · " + contact;
}
```

- LLM이 만들지 않고 코드에서 결정 → 환각 0%.
- fallback 답변에는 안 붙임 (이미 본문에 "공식 문의처에서 확인" 포함, 중복 방지).
- `\n\n---\n\n` 구분선으로 본문과 시각 분리 (마크다운 `<hr>` 컴포넌트 매핑 이미 있음).
- `organization` 또는 `contact` 한쪽이라도 비어 있으면 푸터 자체 생략.

### 새 SSE 이벤트: SUGGESTIONS

기존 4종(CHUNK / SOURCES / DONE / ERROR)에 1종 추가:

```json
{ "type": "SUGGESTIONS", "questions": ["...", "...", "..."] }
```

송출 순서: `CHUNK*` → `SOURCES` → `SUGGESTIONS`(있을 때만) → `DONE`.
빈 배열이면 송출 자체 생략 → 프론트는 `SUGGESTIONS` 미수신 시 칩 영역 안 그림.

### 캐시 모델 확장

`CachedAnswer` record 필드 추가:

```java
public record CachedAnswer(
    String answer,
    List<QnaSourceResult> sources,
    List<String> followUpQuestions,
    Instant cachedAt
) {}
```

- Redis · pgvector 의미 캐시 둘 다 같은 record 직렬화 → 두 캐시 모두 follow-up 포함.
- 마이그레이션: 기존 entry는 `followUpQuestions` 키 없음 → 역직렬화 시 `Optional.ofNullable(...).orElse(List.of())` 방어.
- TTL 변경 없음.
- 강제 무효화 X — 기존 entry는 자연 만료까지 follow-up 빈 리스트로 노출.

### Fallback 답변 일관성

이미 `isFallbackAnswer`로 sources 비우는 룰이 있음. 같은 분기에 follow-up·푸터 스킵 로직을 묶는다.

```java
if (isFallbackAnswer(fullAnswer)) {
    sources = List.of();
    followUps = List.of();
    // 푸터 안 붙임
} else {
    if (passing.isEmpty()) {
        sources = List.of(/* 메타데이터 출처 entry */);
    }
    fullAnswer = appendContactFooterIfPossible(fullAnswer, metadata);
    followUps = generateFollowUpsSafely(...);
}
```

"답할 내용 없음" 답변에 액션 유도 칩이 따라붙는 부조화를 원천 차단.

## 프론트엔드 상세

### 타입 확장 (`types/qna.ts`)

```typescript
export interface QnaSource {
  policyId: number;
  attachmentLabel: string | null;
  pageStart: number | null;
  pageEnd: number | null;
  excerpt: string | null;
}

export interface QnaMessage {
  id: string;
  role: QnaRole;
  content: string;
  sources?: QnaSource[];
  followUpQuestions?: string[];
  status: QnaStatus;
  questionRef?: string;
}
```

`sources` 타입을 `string[]` → `QnaSource[]`로 변경한다. 사용처는 `QnaMessageBubble` 한 군데뿐.

### SSE 파서 (`apis/qna.api.ts`)

콜백 인터페이스 확장:

```typescript
export interface QnaCallbacks {
  onChunk: (text: string) => void;
  onSources: (sources: QnaSource[]) => void;
  onSuggestions: (questions: string[]) => void;
  onDone: () => void;
  onError: (error: Error) => void;
}
```

`SUGGESTIONS` 분기 추가:

```typescript
} else if (parsed.type === 'SUGGESTIONS') {
  onSuggestions(parsed.questions ?? []);
}
```

`SUGGESTIONS` 미수신은 정상 흐름 — 콜백 호출 안 함, 메시지의 `followUpQuestions`는 `undefined` 유지.

### 출처 카드 펼치기 (D-1)

기존 `<ul>`에 단순 string 매핑하던 부분을 컴팩트 라인 + 펼치기로 교체.

```
출처
📄 청년월세_지원사업_신청서.pdf · p.12-13   [▼]
   ↓ 클릭하면 펼침
   "본 사업은 만 19~34세 무주택 청년 중..."
```

- 컴포넌트 분리: `QnaSourceItem` 신규 (한 출처당 펼침 상태 보유).
- 접근성: `<button aria-expanded>` + 키보드(Space/Enter) 토글.
- `excerpt == null`이면 펼치기 토글 자체 미렌더.

### 후속 추천 칩 (B 프론트엔드 절반)

`QnaSuggestionChips`를 일반화 — 현재 시작 화면용 하드코딩 4개를 외부 주입 가능하게 변경.

```typescript
interface Props {
  questions?: readonly string[];   // 미지정 시 기존 DEFAULT_SUGGESTIONS 사용
  onPick: (question: string) => void;
}
```

- 시작 화면 호출부는 변경 0 (default fallback).
- 답변 메시지 내부에서 `<QnaSuggestionChips questions={msg.followUpQuestions} ... />`로 사용.
- 위치: 답변 본문 + 출처 카드 아래, 복사 버튼 위.
- 표시 조건: `status === 'done'` && `followUpQuestions?.length > 0`.
- 칩 클릭 시 기존 `useQnaChat`의 `ask()` 그대로 호출 — 재진입은 별도 LLM call.

### 상태 전파 (`useQnaChat`)

```
onChunk        → setMessages(... content 누적)
onSources      → setMessages(... sources: QnaSource[])
onSuggestions  → setMessages(... followUpQuestions: string[])
onDone         → setMessages(... status: 'done')
```

`onSuggestions`은 `onDone` 앞에 도착(백엔드 송출 순서 보장). 도착 안 해도 `onDone`은 정상 — `followUpQuestions`만 undefined로 남음.

## 에러 처리

| # | 시나리오 | 동작 | 사용자 영향 |
|---|---|---|---|
| 1 | 본문 LLM 호출 실패 | 기존 그대로 — `LLM_ERROR_MESSAGE` SSE → history `LLM_ERROR` | 변경 없음 |
| 2 | 본문 성공 + follow-up LLM 실패 | follow-up 호출 try/catch → log warn → 빈 리스트. `SUGGESTIONS` 송출 생략 | 답변·출처 정상, 칩만 미표시 |
| 3 | 본문 성공 + follow-up JSON 파싱 실패 | 동일하게 빈 리스트 처리 | 답변·출처 정상, 칩만 미표시 |
| 4 | fallback 답변 | follow-up 호출 자체 스킵, 푸터 안 붙임, sources 비움 | 답변만 — 추가 노이즈 0 |
| 5 | RAG 0건 | 기존 그대로 — 거절 메시지. follow-up·푸터 스킵 | 변경 없음 |
| 6 | CostGuard 차단 | 기존 그대로 — 차단 메시지. follow-up 도달 X | 변경 없음 |
| 7 | follow-up 송출 직전 클라이언트 disconnect | `emitter.send` IOException → log warn (기존 패턴), 캐시 저장은 계속 진행 | 다음 동일 질문 시 캐시에서 follow-up 포함 응답 |

### CostGuard

`costGuard.allows(policyId)`는 본문 호출 진입 직전 1회만 평가(기존). follow-up은 본문이 통과한 정책에서만 실행되므로 추가 가드 불필요.

### 비용 메트릭

follow-up LLM 호출도 본문과 동일하게 `LlmCallRecorded` 이벤트 발행 (`LlmModule.QNA`, 같은 모델). 어드민 KPI 대시보드는 변경 없이 follow-up 추가분이 자동 반영됨.

## 테스트 전략

### 백엔드

| 테스트 단위 | 위치/타입 | 무엇을 검증 |
|---|---|---|
| `OpenAiQnaClient.generateFollowUpQuestions` | `qna/infrastructure/external` 단위 | 정상 JSON 배열 파싱. 빈 응답·잘못된 JSON·OpenAI 5xx → 예외 던지기 |
| `QnaService` follow-up 통합 | `qna/application` 슬라이스 | mocked `QnaLlmProvider`로 두 메서드 호출 순서. SSE 시퀀스 `CHUNK* → SOURCES → SUGGESTIONS → DONE` |
| `QnaService` follow-up 실패 graceful degrade | 동일 | follow-up 예외 → 본문 SOURCES·DONE까지는 정상 송출, `SUGGESTIONS` 송출 안 됨 |
| `QnaService` fallback 답변 분기 | 동일 | sources / 푸터 / follow-up 모두 스킵 |
| 푸터 후처리 헬퍼 | package-private static 분리 → 단위 | `(answer, organization, contact)` 조합 6가지 |
| `CachedAnswer` 직렬화 호환성 | `qna/application/dto` 단위 | 기존 entry(`followUpQuestions` 키 없음) → 역직렬화 시 빈 리스트 |

### 프론트엔드

| 테스트 단위 | 위치/타입 | 무엇을 검증 |
|---|---|---|
| `fetchQnaAnswer` SUGGESTIONS 파서 | `apis/__tests__/qna.api.test.ts` 확장 | SUGGESTIONS 라인 → `onSuggestions` 호출. 미수신 시 미호출 |
| `fetchQnaAnswer` sources 타입 변경 | 동일 | `onSources` 콜백이 `QnaSource[]`로 호출. excerpt·페이지 보존 |
| `useQnaChat` follow-up 상태 전파 | `hooks/__tests__/useQnaChat.test.ts` 확장 | `onSuggestions` → 메시지 `followUpQuestions` 반영 |
| `QnaSourceItem` 펼치기 | 신규 컴포넌트 테스트 | 클릭 → `aria-expanded` 토글. excerpt null이면 토글 미렌더 |
| `QnaSuggestionChips` 일반화 | 신규 prop 테스트 | prop 미지정 → DEFAULT 사용. prop 지정 → 그것만 사용 |

### 수동 검증

| 항목 | 검증 방법 |
|---|---|
| 시스템 프롬프트 톤 변화 | 데모 정책 3~5개에 동일 질문 before/after 비교 |
| follow-up 질문 품질 | 같은 정책 5개 질문 × 각 follow-up 3개 = 15개 검토. "이미 본문 정보 재질문" / "정책 외부 이탈" 점검 |
| 푸터 시각 | 정책 메타에 contact 있는 정책 1개·없는 정책 1개로 화면 확인 |
| 출처 펼치기 모바일 UX | 발췌 200자 펼쳤을 때 가독성. 첨부파일명 길 때 라인 wrap |

### 커버리지 목표

신규 코드(`generateFollowUpQuestions`, 푸터 후처리, `QnaSourceItem`, suggestions 파서)는 JaCoCo 라인 커버리지 80%+. 기존 코드는 회귀 방지 수준.

### 테스트 우선순위 (PR 단위)

| PR | 테스트 |
|---|---|
| PR-1 (프롬프트) | 수동 검증만 |
| PR-2 (출처 펼치기) | `QnaSourceItem` 컴포넌트 + `qna.api.ts` sources 타입 변경 |
| PR-3 (푸터) | 푸터 후처리 헬퍼 단위 + `QnaService` 푸터 분기 |
| PR-4 (백엔드 follow-up) | `generateFollowUpQuestions` 단위 + `QnaService` 통합 + `CachedAnswer` 호환성 |
| PR-5 (프론트 follow-up) | SUGGESTIONS 파서 + `useQnaChat` 상태 + `QnaSuggestionChips` 일반화 |

## 영향 받는 파일

### 백엔드

- `backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java` — 메서드 추가
- `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` — follow-up 호출, 푸터 후처리, fallback 분기 통합
- `backend/src/main/java/com/youthfit/qna/application/dto/result/CachedAnswer.java` — 필드 추가
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` — 시스템 프롬프트 강화, follow-up 어댑터 구현
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/RedisQnaAnswerCache.java` — 역직렬화 호환성 (변경 최소)
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java` — 역직렬화 호환성 (변경 최소)
- 테스트: `backend/src/test/java/com/youthfit/qna/...` 신규 + 기존 확장

### 프론트엔드

- `frontend/src/types/qna.ts` — `QnaSource` 신규, `QnaMessage` 확장
- `frontend/src/apis/qna.api.ts` — SUGGESTIONS 파서, sources raw 전달
- `frontend/src/hooks/useQnaChat.ts` — `onSuggestions` 콜백 처리
- `frontend/src/components/qna/QnaMessageBubble.tsx` — 출처 렌더링 위임, follow-up 칩 자리
- `frontend/src/components/qna/QnaSourceItem.tsx` — 신규
- `frontend/src/components/qna/QnaSuggestionChips.tsx` — prop 일반화
- 테스트: `frontend/src/apis/__tests__/qna.api.test.ts`, `frontend/src/hooks/__tests__/useQnaChat.test.ts`, 신규 컴포넌트 테스트

## YAGNI 체크

- 정책 미지정 자유 질의 — 제외 (별도 spec)
- follow-up 칩에서 다른 정책으로 점프 — 제외 (환각·범위 이탈)
- follow-up 캐시 무효화 정책 — 제외 (답변 캐시와 함께 묶어 저장)
- 카테고리별 정적 추천 템플릿 — 제외 (LLM 동적 생성으로 일원화)
- 푸터 fallback 일반 안내 문구 — 제외 (가치 낮음, contact 메타 보강이 정공법)
