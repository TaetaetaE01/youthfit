# Q&A 답변 풍부도 강화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 단일 정책 Q&A 답변에 마크다운 형식 강화 / 후속 추천질문 / 공식 문의처 푸터 / 출처 카드 펼치기 4가지를 추가해 답변 풍부도와 사용자 후속 행동성을 끌어올린다.

**Architecture:** 기존 단일 정책 Q&A 흐름(질문 → 캐시 → RAG → LLM 스트림 → SOURCES → DONE) 뒤에 follow-up 생성·푸터 첨부·SUGGESTIONS SSE 이벤트 3단계를 추가한다. DDD 의존 방향(Presentation → Application → Domain)은 유지하며, follow-up은 어플리케이션 포트(`QnaLlmProvider`) 메서드로 추가하고 OpenAI 어댑터(`OpenAiQnaClient`)가 구현한다.

**Tech Stack:** 백엔드 — Java 21, Spring Boot 4.0.5, JUnit 5, Mockito, AssertJ, PostgreSQL + pgvector, Redis. 프론트엔드 — React 19, TypeScript 5, Vitest, Testing Library, ReactMarkdown.

**Spec:** [`docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md`](../specs/DONE_2026-05-11-qna-rich-answer-design.md)

---

## File Structure

### 백엔드 신규/수정

| 파일 | 책임 | 변경 |
|---|---|---|
| `qna/infrastructure/external/OpenAiQnaClient.java` | OpenAI Chat Completions 호출 어댑터 | 시스템 프롬프트 강화, `generateFollowUpQuestions` 구현 추가 |
| `qna/application/port/QnaLlmProvider.java` | LLM 추상화 포트 | `generateFollowUpQuestions` 메서드 추가 |
| `qna/application/dto/result/CachedAnswer.java` | 캐시 직렬화용 record | `followUpQuestions` 필드 추가 |
| `qna/application/service/QnaService.java` | Q&A 유스케이스 오케스트레이션 | 푸터 첨부 + follow-up 호출 + SUGGESTIONS 이벤트 송출 |
| `qna/application/service/QnaContactFooter.java` | 푸터 후처리 헬퍼 (신규) | `appendIfPossible(answer, organization, contact)` 정적 메서드 |
| `qna/domain/model/QnaQuestionCache.java` | 의미 캐시 엔티티 | `followUpsJson` 컬럼 추가 |
| `qna/domain/model/SimilarCachedAnswer.java` | 의미 캐시 조회 결과 record | `followUpsJson` 필드 추가 |
| `qna/infrastructure/external/PgVectorSemanticQnaCache.java` | pgvector 의미 캐시 어댑터 | follow-up 직렬화/역직렬화 |
| `qna/infrastructure/persistence/QnaQuestionCacheRepositoryImpl.java` | native SQL → record 매핑 | `follow_ups_json` 컬럼 추가 (있으면) |
| `resources/sql/2026-05-11-qna-follow-ups-column.sql` (신규) | 마이그레이션 SQL | `qna_question_cache.follow_ups_json` NULL 허용 컬럼 추가 |

### 프론트엔드 신규/수정

| 파일 | 책임 | 변경 |
|---|---|---|
| `frontend/src/types/qna.ts` | QnA 타입 정의 | `QnaSource` 신규, `QnaMessage.sources` 타입 변경, `followUpQuestions` 추가 |
| `frontend/src/apis/qna.api.ts` | SSE 파서 + 콜백 dispatch | sources raw 객체 그대로 전달, `SUGGESTIONS` 이벤트 분기, `onSuggestions` 콜백 |
| `frontend/src/hooks/useQnaChat.ts` | 채팅 상태 훅 | `onSuggestions`로 메시지 `followUpQuestions` 갱신 |
| `frontend/src/components/qna/QnaSourceItem.tsx` (신규) | 출처 한 항목 — 컴팩트/펼치기 | 첨부파일명·페이지·발췌 표시 |
| `frontend/src/components/qna/QnaSuggestionChips.tsx` | 추천 질문 칩 그룹 | `questions` prop 일반화, default fallback 유지 |
| `frontend/src/components/qna/QnaMessageBubble.tsx` | 답변 버블 렌더링 | 출처 렌더링 위임, follow-up 칩 표시 |
| 테스트: `frontend/src/apis/__tests__/qna.api.test.ts`, `frontend/src/hooks/__tests__/useQnaChat.test.ts`, 신규 컴포넌트 테스트 | | sources 형식 변경, SUGGESTIONS 파서, 칩 prop |

---

## PR-1: 시스템 프롬프트 마크다운 가이드 강화 (A-1)

**범위:** 백엔드 1파일. 자동 테스트 어려움 (LLM 동작 검증 불가) — 빌드만 확인.

### Task 1: OpenAiQnaClient 시스템 프롬프트 강화

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java:36-47`

- [ ] **Step 1: 시스템 프롬프트 텍스트 교체**

`OpenAiQnaClient.java`의 `SYSTEM_PROMPT` 상수를 다음으로 교체:

```java
    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 Q&A 전문가입니다.
            사용자가 특정 정책에 대해 질문하면, 제공된 정책 메타데이터와 본문 컨텍스트에 근거하여 답변하세요.

            규칙:
            - 본문 컨텍스트에 답이 있으면 본문을 우선 사용하세요.
            - 본문에 답이 없으면 정책 메타데이터로 보강하세요.
            - 메타데이터와 본문 어느 쪽에도 없는 내용을 지어내지 마세요.
            - 메타데이터와 본문 모두에 답이 없으면 "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다."라고 답변하세요.
            - 쉬운 한국어로 답변하세요.
            - 답변은 간결하고 핵심적으로 작성하세요.

            출력 형식 가이드:
            - 정보가 여러 항목이면 글머리 기호(`-`)로 정리하세요.
            - 핵심 수치(금액·기간·연령·횟수)는 굵게(`**...**`) 표시하세요.
            - 본문이 2개 이상의 명확한 섹션으로 나뉠 때만 `###` 헤더를 쓰세요.
              헤더 앞에 이모지를 1개까지 쓸 수 있으나, 정확히 맞는 경우에만.
            - 짧은 답이면 평문 한두 줄로도 충분합니다 — 형식을 위한 형식은 피하세요.
            """;
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 기존 테스트 회귀 없음 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.*"`
Expected: 모든 기존 QnA 테스트 통과 (시스템 프롬프트 텍스트 자체를 단언하는 테스트는 없음)

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java
git commit -m "$(cat <<'EOF'
feat(qna): 답변 마크다운 형식 가이드 강화

시스템 프롬프트에 출력 형식 가이드를 추가해 답변이 정보량에 비례해
글머리/굵게/헤더를 자연스럽게 활용하도록 한다. 환각 금지 룰은 그대로.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: PR 생성**

PR 생성은 `/create-pr` 스킬로 한다. 사용자에게 다음을 알린다:

> "PR-1 커밋 완료. PR 생성하려면 `/create-pr` 실행해 주세요. 제목 예: `feat(qna): 답변 마크다운 형식 가이드 강화`"

---

## PR-2: 출처 카드 펼치기 (D-1)

**범위:** 프론트엔드만. `QnaSource` 타입 신설 + SSE 파서 raw 객체 전달 + `QnaSourceItem` 컴포넌트.

### Task 2: QnaSource 타입 추가

**Files:**
- Modify: `frontend/src/types/qna.ts`

- [ ] **Step 1: 타입 정의 교체**

`frontend/src/types/qna.ts` 전체 내용을 다음으로 교체:

```typescript
export type QnaRole = 'user' | 'assistant';
export type QnaStatus = 'streaming' | 'done' | 'error';

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
  /** assistant 메시지가 어느 user 질문에 속하는지 — retry 시 question 복원용 */
  questionRef?: string;
}
```

- [ ] **Step 2: 타입체크 (실패 예상 — sources 사용처가 string[] 가정 중)**

Run: `cd frontend && npx tsc --noEmit`
Expected: FAIL — `QnaMessageBubble.tsx`, `qna.api.ts`, `useQnaChat.ts`에서 `sources`를 `string[]`로 다루는 부분이 타입 에러 발생. 다음 task에서 순차 해결.

### Task 3: fetchQnaAnswer가 raw QnaSource[]를 전달하도록 변경

**Files:**
- Modify: `frontend/src/apis/qna.api.ts:75-97`
- Test: `frontend/src/apis/__tests__/qna.api.test.ts`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/apis/__tests__/qna.api.test.ts`의 첫 번째 테스트를 다음으로 교체 (assertion 부분만 변경):

```typescript
  it('CHUNK / SOURCES / DONE 이벤트를 파싱해 콜백을 호출한다', async () => {
    const stream = makeSseStream([
      'data: {"type":"CHUNK","content":"안녕"}',
      'data: {"type":"CHUNK","content":"하세요"}',
      'data: {"type":"SOURCES","sources":[{"policyId":1,"attachmentLabel":"청년정책 시행계획","pageStart":12,"pageEnd":13,"excerpt":"본 사업은..."}]}',
      'data: {"type":"DONE"}',
    ]);

    (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(
      new Response(stream, { status: 200 }),
    );

    const onChunk = vi.fn();
    const onSources = vi.fn();
    const onSuggestions = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await fetchQnaAnswer(
      1,
      '신청 자격은?',
      { onChunk, onSources, onSuggestions, onDone, onError },
      'token-abc',
    );

    expect(onChunk).toHaveBeenNthCalledWith(1, '안녕');
    expect(onChunk).toHaveBeenNthCalledWith(2, '하세요');
    expect(onSources).toHaveBeenCalledWith([
      {
        policyId: 1,
        attachmentLabel: '청년정책 시행계획',
        pageStart: 12,
        pageEnd: 13,
        excerpt: '본 사업은...',
      },
    ]);
    expect(onSuggestions).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });
```

또한 테스트 파일 내 다른 케이스에서 `QnaCallbacks` 객체 생성 시 `onSuggestions: vi.fn()`을 함께 추가한다 (없으면 타입 에러):

```typescript
    // 'AbortSignal로 취소되면 silent하게 종료한다' 테스트 안의 콜백
      { onChunk: vi.fn(), onSources: vi.fn(), onSuggestions: vi.fn(), onDone, onError },

    // '인증 토큰이 없으면 onError 호출하고 fetch 안 함' 테스트 안의 콜백
      { onChunk: vi.fn(), onSources: vi.fn(), onSuggestions: vi.fn(), onDone: vi.fn(), onError },
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd frontend && npx vitest run src/apis/__tests__/qna.api.test.ts`
Expected: FAIL — `onSuggestions`이 콜백 타입에 없거나 `onSources` assertion이 string[] vs object 불일치

- [ ] **Step 3: qna.api.ts 콜백 타입 + 파서 수정**

`frontend/src/apis/qna.api.ts` 전체 내용을 다음으로 교체:

```typescript
import type { QnaSource } from '@/types/qna';

const QNA_URL = '/api/v1/qna/ask';

export interface QnaCallbacks {
  onChunk: (text: string) => void;
  onSources: (sources: QnaSource[]) => void;
  onSuggestions: (questions: string[]) => void;
  onDone: () => void;
  onError: (error: Error) => void;
}

export async function fetchQnaAnswer(
  policyId: number,
  question: string,
  callbacks: QnaCallbacks,
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<void> {
  const { onChunk, onSources, onSuggestions, onDone, onError } = callbacks;

  if (!accessToken) {
    onError(new Error('인증이 필요합니다'));
    return;
  }

  let response: Response;
  try {
    response = await fetch(QNA_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ policyId, question }),
      signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return;
    }
    onError(e instanceof Error ? e : new Error('네트워크 오류'));
    return;
  }

  if (!response.ok) {
    onError(new Error(`Q&A 요청 실패: ${response.status}`));
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    onError(new Error('스트림을 읽을 수 없습니다'));
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data) continue;

        try {
          const parsed = JSON.parse(data);
          if (parsed.type === 'CHUNK') {
            onChunk(parsed.content ?? '');
          } else if (parsed.type === 'SOURCES') {
            const sources: QnaSource[] = (parsed.sources ?? []).map(
              (s: Partial<QnaSource>) => ({
                policyId: s.policyId ?? 0,
                attachmentLabel: s.attachmentLabel ?? null,
                pageStart: s.pageStart ?? null,
                pageEnd: s.pageEnd ?? null,
                excerpt: s.excerpt ?? null,
              }),
            );
            onSources(sources);
          } else if (parsed.type === 'SUGGESTIONS') {
            onSuggestions(parsed.questions ?? []);
          } else if (parsed.type === 'DONE') {
            onDone();
            return;
          } else if (parsed.type === 'ERROR') {
            onError(new Error(parsed.content ?? '답변 생성 중 오류가 발생했습니다'));
            return;
          }
        } catch {
          // SSE 데이터가 partial JSON 인 경우는 발생하지 않으나 안전하게 무시
        }
      }
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return;
    }
    onError(e instanceof Error ? e : new Error('스트림 읽기 오류'));
    return;
  }

  onDone();
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npx vitest run src/apis/__tests__/qna.api.test.ts`
Expected: PASS — 3개 케이스 모두 통과

### Task 4: useQnaChat에 onSuggestions 콜백 + sources 타입 갱신

**Files:**
- Modify: `frontend/src/hooks/useQnaChat.ts:41-70`
- Test: `frontend/src/hooks/__tests__/useQnaChat.test.ts`

- [ ] **Step 1: 실패 테스트 추가**

`frontend/src/hooks/__tests__/useQnaChat.test.ts` 파일 끝의 `describe('useQnaChat', ...)` 블록 내에 다음 테스트 2개 추가 (마지막 `});` 직전에):

```typescript
  it('onSuggestions 콜백으로 followUpQuestions 가 메시지에 반영된다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    act(() => callbacks!.onSuggestions(['후속A', '후속B', '후속C']));

    expect(result.current.messages[1].followUpQuestions).toEqual(['후속A', '후속B', '후속C']);
  });

  it('onSources 가 raw QnaSource[] 를 받아 메시지 sources 에 저장한다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    const sources = [
      { policyId: 1, attachmentLabel: '시행계획', pageStart: 12, pageEnd: 13, excerpt: '본문...' },
    ];
    act(() => callbacks!.onSources(sources));

    expect(result.current.messages[1].sources).toEqual(sources);
  });
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd frontend && npx vitest run src/hooks/__tests__/useQnaChat.test.ts`
Expected: FAIL — `callbacks!.onSuggestions`이 존재하지 않음 (타입 에러 또는 런타임 undefined)

- [ ] **Step 3: useQnaChat에 onSuggestions 콜백 + retry sources 초기화**

`frontend/src/hooks/useQnaChat.ts`의 `streamInto` 콜백 객체(라인 41~70)를 다음으로 교체:

```typescript
      const callbacks: QnaCallbacks = {
        onChunk: (chunk) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, content: m.content + chunk } : m,
            ),
          );
        },
        onSources: (sources) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, sources } : m)),
          );
        },
        onSuggestions: (questions) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, followUpQuestions: questions } : m,
            ),
          );
        },
        onDone: () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, status: 'done' } : m,
            ),
          );
        },
        onError: () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId
                ? { ...m, status: 'error', content: ERROR_FALLBACK }
                : m,
            ),
          );
        },
      };
```

또한 `retry` 안의 reset 로직 (라인 107~113)에서 `followUpQuestions`도 같이 초기화하도록 수정:

```typescript
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMessageId
            ? { ...m, content: '', sources: undefined, followUpQuestions: undefined, status: 'streaming' }
            : m,
        ),
      );
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npx vitest run src/hooks/__tests__/useQnaChat.test.ts`
Expected: PASS — 신규 2개 + 기존 6개 모두 통과

### Task 5: QnaSourceItem 신규 컴포넌트

**Files:**
- Create: `frontend/src/components/qna/QnaSourceItem.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaSourceItem.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/components/qna/__tests__/` 디렉토리가 없으면 만들고, `QnaSourceItem.test.tsx`를 다음으로 작성:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QnaSourceItem } from '../QnaSourceItem';

describe('QnaSourceItem', () => {
  it('첨부파일명과 페이지 범위를 한 줄로 표시한다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '청년정책 시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: '본 사업은 만 19~34세...',
        }}
      />,
    );

    expect(screen.getByText(/청년정책 시행계획/)).toBeInTheDocument();
    expect(screen.getByText(/p\.12-13/)).toBeInTheDocument();
  });

  it('pageStart 와 pageEnd 가 같으면 단일 페이지로 표시', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 5,
          pageEnd: 5,
          excerpt: '본문',
        }}
      />,
    );
    expect(screen.getByText(/p\.5\b/)).toBeInTheDocument();
    expect(screen.queryByText(/p\.5-5/)).not.toBeInTheDocument();
  });

  it('attachmentLabel 이 null 이면 "정책 #{policyId}"로 표시', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 42,
          attachmentLabel: null,
          pageStart: null,
          pageEnd: null,
          excerpt: null,
        }}
      />,
    );
    expect(screen.getByText(/정책 #42/)).toBeInTheDocument();
  });

  it('excerpt 가 있으면 펼치기 토글 버튼이 렌더되고 클릭 시 발췌가 표시된다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: '본 사업은 만 19~34세 무주택 청년 중...',
        }}
      />,
    );
    const button = screen.getByRole('button', { name: /발췌/ });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText(/본 사업은 만 19~34세/)).not.toBeInTheDocument();

    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/본 사업은 만 19~34세/)).toBeInTheDocument();
  });

  it('excerpt 가 null 이면 펼치기 토글 버튼이 렌더되지 않는다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: null,
        }}
      />,
    );
    expect(screen.queryByRole('button', { name: /발췌/ })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd frontend && npx vitest run src/components/qna/__tests__/QnaSourceItem.test.tsx`
Expected: FAIL — `QnaSourceItem` 모듈 없음

- [ ] **Step 3: 컴포넌트 구현**

`frontend/src/components/qna/QnaSourceItem.tsx`를 다음으로 작성:

```typescript
import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { QnaSource } from '@/types/qna';

interface Props {
  source: QnaSource;
}

function formatPage(start: number | null, end: number | null): string {
  if (start == null || end == null) return '';
  if (start === end) return ` · p.${start}`;
  return ` · p.${start}-${end}`;
}

export function QnaSourceItem({ source }: Props) {
  const [expanded, setExpanded] = useState(false);
  const label = source.attachmentLabel ?? `정책 #${source.policyId}`;
  const page = formatPage(source.pageStart, source.pageEnd);
  const hasExcerpt = source.excerpt != null && source.excerpt.length > 0;

  return (
    <li className="my-1">
      <div className="flex items-center gap-1">
        <span className="text-[13px] text-chat-bubble-text">
          📄 {label}
          {page}
        </span>
        {hasExcerpt && (
          <button
            type="button"
            aria-label="발췌 펼치기"
            aria-expanded={expanded}
            onClick={() => setExpanded((v) => !v)}
            className="ml-1 inline-flex h-6 w-6 items-center justify-center rounded text-chat-soft hover:bg-chat-source-bg"
          >
            <ChevronDown
              className={cn('h-3.5 w-3.5 transition-transform', expanded && 'rotate-180')}
            />
          </button>
        )}
      </div>
      {hasExcerpt && expanded && (
        <p className="mt-1 rounded bg-chat-source-bg/60 px-2 py-1.5 text-[12px] italic text-slate-600">
          {source.excerpt}
        </p>
      )}
    </li>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npx vitest run src/components/qna/__tests__/QnaSourceItem.test.tsx`
Expected: PASS — 5개 케이스 모두 통과

### Task 6: QnaMessageBubble 출처 렌더링을 QnaSourceItem으로 위임

**Files:**
- Modify: `frontend/src/components/qna/QnaMessageBubble.tsx:127-140`

- [ ] **Step 1: 출처 카드 부분 교체**

`QnaMessageBubble.tsx`에서 출처 렌더링 블록(라인 127~140)을 다음으로 교체:

```typescript
        {!isError && message.sources && message.sources.length > 0 && (
          <div className="mt-3 rounded-[10px] bg-chat-source-bg px-[14px] py-3 text-[13px] text-chat-bubble-text">
            <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-chat-surface">
              출처
            </p>
            <ul className="m-0 list-none p-0">
              {message.sources.map((src, i) => (
                <QnaSourceItem key={i} source={src} />
              ))}
            </ul>
          </div>
        )}
```

또한 파일 상단에 import 추가:

```typescript
import { QnaSourceItem } from './QnaSourceItem';
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: 타입 에러 0건 (sources가 `QnaSource[]`로 일관됨)

- [ ] **Step 3: 빌드 확인**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 전체 테스트 실행**

Run: `cd frontend && npm run test`
Expected: 모든 테스트 통과

- [ ] **Step 5: 수동 검증 — 개발 서버 띄워서 출처 카드 확인**

Run: `cd frontend && npm run dev`

브라우저에서 정책 상세 페이지의 Q&A에서 임의 질문을 던지고 응답이 도착하면 출처 카드의 컴팩트 라인 + 펼치기 동작을 확인. 모바일 뷰포트(DevTools)에서도 라인 wrap이 자연스러운지 확인.

- [ ] **Step 6: 커밋 + PR 안내**

```bash
git add frontend/src/types/qna.ts frontend/src/apis/qna.api.ts frontend/src/apis/__tests__/qna.api.test.ts frontend/src/hooks/useQnaChat.ts frontend/src/hooks/__tests__/useQnaChat.test.ts frontend/src/components/qna/QnaSourceItem.tsx frontend/src/components/qna/__tests__/QnaSourceItem.test.tsx frontend/src/components/qna/QnaMessageBubble.tsx
git commit -m "$(cat <<'EOF'
feat(fe): Q&A 출처 카드 펼치기 + QnaSource 타입 도입

기존 단순 string 리스트였던 출처 카드를 QnaSourceItem 컴포넌트로 분리해
첨부파일명·페이지 컴팩트 라인 + 발췌 펼치기 토글을 제공한다.
SSE 파서는 sources를 raw QnaSource[]로 전달하도록 변경.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

이후 사용자에게 `/create-pr` 실행 안내.

---

## PR-3: 공식 문의처 푸터 후처리 (C-1)

**범위:** 백엔드 — `QnaContactFooter` 헬퍼 신설 + `QnaService` 호출.

### Task 7: QnaContactFooter 헬퍼 + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/service/QnaContactFooter.java`
- Test: `backend/src/test/java/com/youthfit/qna/application/service/QnaContactFooterTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/qna/application/service/QnaContactFooterTest.java`:

```java
package com.youthfit.qna.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QnaContactFooter.appendIfPossible")
class QnaContactFooterTest {

    private static final String ANSWER = "신청 자격은 만 19~34세입니다.";
    private static final String FALLBACK_ANSWER = "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";

    @Nested
    @DisplayName("organization 과 contact 가 모두 있을 때")
    class BothPresent {

        @Test
        @DisplayName("일반 답변 끝에 구분선과 푸터가 첨부된다")
        void appendsFooter() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", "129", false);

            assertThat(result).isEqualTo(ANSWER + "\n\n---\n\n📞 문의: 보건복지부 · 129");
        }

        @Test
        @DisplayName("fallback 답변일 때는 푸터가 첨부되지 않는다")
        void skipsForFallback() {
            String result = QnaContactFooter.appendIfPossible(FALLBACK_ANSWER, "보건복지부", "129", true);

            assertThat(result).isEqualTo(FALLBACK_ANSWER);
        }
    }

    @Nested
    @DisplayName("organization 또는 contact 한쪽이라도 비어있으면")
    class PartialMissing {

        @Test
        @DisplayName("organization 이 null 이면 푸터 미첨부")
        void organizationNull() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, null, "129", false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("contact 가 null 이면 푸터 미첨부")
        void contactNull() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", null, false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("organization 이 빈 문자열이면 푸터 미첨부")
        void organizationBlank() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "  ", "129", false);
            assertThat(result).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("contact 가 빈 문자열이면 푸터 미첨부")
        void contactBlank() {
            String result = QnaContactFooter.appendIfPossible(ANSWER, "보건복지부", "", false);
            assertThat(result).isEqualTo(ANSWER);
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaContactFooterTest"`
Expected: FAIL — `QnaContactFooter` 클래스가 존재하지 않음

- [ ] **Step 3: QnaContactFooter 구현**

`backend/src/main/java/com/youthfit/qna/application/service/QnaContactFooter.java`:

```java
package com.youthfit.qna.application.service;

public final class QnaContactFooter {

    private static final String SEPARATOR = "\n\n---\n\n";
    private static final String PREFIX = "📞 문의: ";

    private QnaContactFooter() {
    }

    public static String appendIfPossible(String answer, String organization, String contact, boolean isFallbackAnswer) {
        if (isFallbackAnswer) return answer;
        if (isBlank(organization) || isBlank(contact)) return answer;
        return answer + SEPARATOR + PREFIX + organization + " · " + contact;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaContactFooterTest"`
Expected: PASS — 6개 케이스 모두 통과

### Task 8: QnaService에서 푸터 호출 + 통합 검증

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java:198-225`

- [ ] **Step 1: QnaService.processQuestion에서 LLM 응답 후 푸터 첨부**

`QnaService.java`의 LLM 호출 직후 — fallback 분기 처리 직전(라인 213) — 부분을 다음으로 교체:

```java
        // ⑤ LLM 스트림
        String fullAnswer;
        PolicyMetadata metadata = PolicyMetadata.from(policy);
        try {
            fullAnswer = qnaLlmProvider.generateAnswer(
                    policy.getTitle(), metadata, context, command.question(),
                    chunk -> sendChunkEvent(emitter, chunk)
            );
        } catch (Exception e) {
            log.error("LLM 호출 실패: policyId={}", command.policyId(), e);
            sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
            historyWriter.markFailed(historyId, QnaFailedReason.LLM_ERROR);
            emitter.completeWithError(e);
            return;
        }

        boolean isFallback = isFallbackAnswer(fullAnswer);

        // 푸터 첨부 (fallback 답변엔 안 붙임)
        fullAnswer = QnaContactFooter.appendIfPossible(
                fullAnswer, metadata.organization(), metadata.contact(), isFallback);

        // Fix B/C: fallback / 메타데이터 출처 분기
        if (isFallback) {
            sources = List.of();
        } else if (passing.isEmpty()) {
            sources = List.of(new QnaSourceResult(
                    command.policyId(), null, "정책 기본 정보", null, null,
                    "정책 메타데이터 기반 답변"
            ));
        }
```

> 주의: 기존 코드의 `PolicyMetadata metadata = PolicyMetadata.from(policy);`는 try 블록 안에 있었는데, 푸터 호출에서도 metadata가 필요하므로 try 블록 바깥으로 빼냈다.

> 또한 `isFallbackAnswer` 호출을 한 곳에서만 하도록 `isFallback` 지역변수에 캐싱한다 (PR-4에서 follow-up 분기에서도 재사용).

- [ ] **Step 2: 푸터 첨부가 SSE 본문 스트림 후에 일어남에 유의**

`fullAnswer`는 SSE CHUNK 이벤트로 이미 사용자에게 토큰별로 송출된 후의 누적 결과다. 푸터는 **캐시 저장용 fullAnswer 변수에만** 추가되며, 사용자 화면에는 별도 CHUNK로 송출되지 않는다.

→ 사용자에게 푸터를 보이려면 푸터 텍스트를 **별도 CHUNK 이벤트로 송출**해야 한다. 위 교체 코드 직후에 다음 추가:

```java
        // 푸터가 추가된 경우, 추가분만 별도 CHUNK 이벤트로 사용자에게 송출
        // (LLM 스트림은 푸터 미포함이므로 차이만 보냄)
        String footerSuffix = "";
        if (!isFallback && !isBlank(metadata.organization()) && !isBlank(metadata.contact())) {
            footerSuffix = "\n\n---\n\n📞 문의: " + metadata.organization() + " · " + metadata.contact();
            sendChunkEvent(emitter, footerSuffix);
        }
```

그리고 파일 하단에 헬퍼 추가:

```java
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
```

> 디자인 정합성 노트: `QnaContactFooter.appendIfPossible`은 캐시 저장용 결합된 답변을 만들고, `sendChunkEvent`는 SSE 화면 송출용. 두 곳이 같은 입력 조건(`!isFallback && org && contact`)을 따르므로 결과가 일치한다.

- [ ] **Step 3: QnaService 단위 테스트에 푸터 케이스 추가**

`backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`의 적절한 `@Nested` 클래스(또는 새 `@Nested`) 안에 다음 테스트 추가:

```java
    @Nested
    @DisplayName("푸터 첨부")
    class ContactFooter {

        @Test
        @DisplayName("정상 답변 + organization/contact 있음 → 푸터 CHUNK 송출 + 캐시에 푸터 포함")
        void footer_appended_when_metadata_present() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(eq(10L), anyString())).willReturn(Optional.empty());
            given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
            given(semanticQnaCache.findSimilar(eq(10L), anyString(), any())).willReturn(SemanticLookupResult.miss());
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS_NO_NEIGHBOR);
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    new PolicyDocumentChunkResult(1L, null, 0, "본문", 0.1, null, null)
            ));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            AskQuestionCommand command = new AskQuestionCommand(10L, "신청 자격?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().answer()).contains("📞 문의: ");
        }
    }
```

> `mockPolicy`는 `PolicyMetadata.from(policy)`이 organization/contact 모두 채워진 metadata를 반환하도록 stub되어 있어야 한다. 기존 `mockPolicy(10L, "테스트 정책")` 헬퍼 정의를 확인해서 organization/contact stub이 부족하면 다음과 같이 보강한다:

```java
    private static Policy mockPolicy(long id, String title) {
        Policy p = mock(Policy.class);
        given(p.getId()).willReturn(id);
        given(p.getTitle()).willReturn(title);
        given(p.getOrganization()).willReturn("보건복지부");
        given(p.getContact()).willReturn("129");
        // ... 기존 stub 그대로
        return p;
    }
```

(기존 헬퍼가 organization/contact를 stub하지 않으면 PolicyMetadata.from에서 null이 들어가 푸터가 안 붙는다 — 테스트 실패 시 헬퍼를 위 형태로 보강한다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaServiceTest"`
Expected: PASS — 신규 푸터 테스트 + 기존 케이스 모두 통과

- [ ] **Step 5: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋 + PR 안내**

```bash
git add backend/src/main/java/com/youthfit/qna/application/service/QnaContactFooter.java backend/src/main/java/com/youthfit/qna/application/service/QnaService.java backend/src/test/java/com/youthfit/qna/application/service/QnaContactFooterTest.java backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java
git commit -m "$(cat <<'EOF'
feat(qna): 공식 문의처 푸터 자동 첨부

정책 메타데이터에 organization과 contact가 모두 있을 때만 답변 끝에
"📞 문의: {기관} · {연락처}" 푸터를 첨부한다. fallback 답변에는 미첨부.
LLM이 만들지 않고 코드에서 후처리해 환각 0%.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

이후 사용자에게 `/create-pr` 실행 안내.

---

## PR-4: 후속 추천질문 백엔드 (B-2 1/2)

**범위:** 캐시 마이그레이션 + LLM 포트 메서드 + OpenAI 어댑터 + QnaService 통합.

### Task 9: CachedAnswer record 필드 추가 + 직렬화 호환성 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/dto/result/CachedAnswer.java`
- Test: `backend/src/test/java/com/youthfit/qna/application/dto/result/CachedAnswerCompatTest.java` (신규)

- [ ] **Step 1: 실패 테스트 작성 — 기존 entry JSON 호환성**

`backend/src/test/java/com/youthfit/qna/application/dto/result/CachedAnswerCompatTest.java`:

```java
package com.youthfit.qna.application.dto.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CachedAnswer 직렬화 호환성")
class CachedAnswerCompatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("followUpQuestions 키가 없는 기존 JSON 도 역직렬화 가능 (빈 리스트로)")
    void deserialize_legacy_entry_without_followUps() throws Exception {
        String legacyJson = """
                {
                    "answer": "신청 자격은 만 19~34세입니다.",
                    "sources": [],
                    "cachedAt": "2026-05-01T12:00:00Z"
                }
                """;

        CachedAnswer answer = objectMapper.readValue(legacyJson, CachedAnswer.class);

        assertThat(answer.answer()).isEqualTo("신청 자격은 만 19~34세입니다.");
        assertThat(answer.followUpQuestions()).isEmpty();
    }

    @Test
    @DisplayName("followUpQuestions 가 포함된 신규 JSON 도 정상 역직렬화")
    void deserialize_new_entry_with_followUps() throws Exception {
        String newJson = """
                {
                    "answer": "신청 자격은 만 19~34세입니다.",
                    "sources": [],
                    "followUpQuestions": ["서류는?", "마감일은?"],
                    "cachedAt": "2026-05-01T12:00:00Z"
                }
                """;

        CachedAnswer answer = objectMapper.readValue(newJson, CachedAnswer.class);

        assertThat(answer.followUpQuestions()).containsExactly("서류는?", "마감일은?");
    }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.dto.result.CachedAnswerCompatTest"`
Expected: FAIL — `followUpQuestions()` 메서드가 record에 없음

- [ ] **Step 3: CachedAnswer record 필드 추가**

`backend/src/main/java/com/youthfit/qna/application/dto/result/CachedAnswer.java`를 다음으로 교체:

```java
package com.youthfit.qna.application.dto.result;

import tools.jackson.annotation.JsonCreator;
import tools.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record CachedAnswer(
        String answer,
        List<QnaSourceResult> sources,
        List<String> followUpQuestions,
        Instant cachedAt
) {
    @JsonCreator
    public CachedAnswer(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QnaSourceResult> sources,
            @JsonProperty("followUpQuestions") List<String> followUpQuestions,
            @JsonProperty("cachedAt") Instant cachedAt
    ) {
        this.answer = answer;
        this.sources = sources != null ? sources : List.of();
        this.followUpQuestions = followUpQuestions != null ? followUpQuestions : List.of();
        this.cachedAt = cachedAt;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.dto.result.CachedAnswerCompatTest"`
Expected: PASS

- [ ] **Step 5: 컴파일 에러 해결**

Run: `cd backend && ./gradlew compileJava`
Expected: FAIL — `CachedAnswer` 인스턴스 생성 사이트가 새 인자를 안 줌. 수정 대상:
- `QnaService.java`의 `new CachedAnswer(fullAnswer, sources, Instant.now())` → `new CachedAnswer(fullAnswer, sources, List.of(), Instant.now())` (PR-4 다음 task에서 follow-up 채울 예정. 일단 빈 리스트.)
- `PgVectorSemanticQnaCache.java`의 `new CachedAnswer(c.answer(), sources, Instant.now())` → `new CachedAnswer(c.answer(), sources, List.of(), Instant.now())` (의미 캐시는 다음 task에서 follow-up 컬럼 처리)
- `PgVectorSemanticQnaCache.java`의 `new CachedAnswer(c.answer(), List.of(), Instant.now())` → `new CachedAnswer(c.answer(), List.of(), List.of(), Instant.now())`

각 파일에서 위 라인들 수정.

- [ ] **Step 6: 컴파일 통과 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

### Task 10: 의미 캐시 마이그레이션 (DB 컬럼 + 엔티티)

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql`
- Modify: `backend/src/main/java/com/youthfit/qna/domain/model/QnaQuestionCache.java`
- Modify: `backend/src/main/java/com/youthfit/qna/domain/model/SimilarCachedAnswer.java`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheRepositoryImpl.java`

- [ ] **Step 1: 마이그레이션 SQL 추가**

`backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql`:

```sql
-- backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql
-- Q&A 의미 캐시에 후속 추천질문 컬럼 추가. NULL 허용으로 기존 entry 호환.

ALTER TABLE qna_question_cache
    ADD COLUMN IF NOT EXISTS follow_ups_json JSONB NULL;
```

- [ ] **Step 2: QnaQuestionCache 엔티티에 필드 추가**

`backend/src/main/java/com/youthfit/qna/domain/model/QnaQuestionCache.java`의 필드 + 빌더에 추가:

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_ups_json", columnDefinition = "JSONB")
    private String followUpsJson;
```

(`sourcesJson` 필드 바로 아래에 추가)

빌더 시그니처 + 본문 수정:

```java
    @Builder
    private QnaQuestionCache(Long policyId,
                             String sourceHash,
                             String questionText,
                             float[] embedding,
                             String answer,
                             String sourcesJson,
                             String followUpsJson) {
        this.policyId = policyId;
        this.sourceHash = sourceHash;
        this.questionText = questionText;
        this.embedding = embedding;
        this.answer = answer;
        this.sourcesJson = sourcesJson;
        this.followUpsJson = followUpsJson;
    }
```

- [ ] **Step 3: SimilarCachedAnswer record에 필드 추가**

`backend/src/main/java/com/youthfit/qna/domain/model/SimilarCachedAnswer.java`:

```java
package com.youthfit.qna.domain.model;

public record SimilarCachedAnswer(
        Long id,
        String questionText,
        String sourceHash,
        String answer,
        String sourcesJson,
        String followUpsJson,
        double distance
) {
}
```

- [ ] **Step 4: QnaQuestionCacheRepositoryImpl native query 매핑 갱신**

`backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheRepositoryImpl.java`를 읽어 native SQL의 SELECT 절과 매핑 부분을 확인. `findClosestByPolicyId`가 `SimilarCachedAnswer`를 만들 때 `follow_ups_json` 컬럼을 함께 SELECT하고 record 생성 시 인자로 넘기도록 수정:

`SELECT id, question_text, source_hash, answer, sources_json, distance FROM ...`
→ `SELECT id, question_text, source_hash, answer, sources_json, follow_ups_json, distance FROM ...`

매핑 코드도 `new SimilarCachedAnswer(id, questionText, sourceHash, answer, sourcesJson, followUpsJson, distance)`로 수정.

(파일을 직접 보고 위치를 찾아 수정한다 — JdbcTemplate `RowMapper` 또는 `EntityManager` `createNativeQuery` 형태 둘 다 가능하므로 실제 패턴을 따른다.)

- [ ] **Step 5: PgVectorSemanticQnaCache 직렬화/역직렬화 수정**

`backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java`의 `toCachedAnswer` 메서드 + `put` 메서드 교체:

```java
    private CachedAnswer toCachedAnswer(Long policyId, SimilarCachedAnswer c) {
        List<QnaSourceResult> sources = parseSources(policyId, c.sourcesJson());
        List<String> followUps = parseFollowUps(policyId, c.followUpsJson());
        return new CachedAnswer(c.answer(), sources, followUps, Instant.now());
    }

    private List<QnaSourceResult> parseSources(Long policyId, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, SOURCES_TYPE);
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 sources 역직렬화 실패: policyId={}, error={}", policyId, e.toString());
            return List.of();
        }
    }

    private List<String> parseFollowUps(Long policyId, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, FOLLOW_UPS_TYPE);
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 followUps 역직렬화 실패: policyId={}, error={}", policyId, e.toString());
            return List.of();
        }
    }

    @Override
    public void put(Long policyId, String question, String sourceHash, float[] embedding, CachedAnswer answer) {
        try {
            String sourcesJson = objectMapper.writeValueAsString(answer.sources());
            String followUpsJson = answer.followUpQuestions().isEmpty()
                    ? null
                    : objectMapper.writeValueAsString(answer.followUpQuestions());
            QnaQuestionCache entity = QnaQuestionCache.builder()
                    .policyId(policyId)
                    .sourceHash(sourceHash)
                    .questionText(question)
                    .embedding(embedding)
                    .answer(answer.answer())
                    .sourcesJson(sourcesJson)
                    .followUpsJson(followUpsJson)
                    .build();
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 write 실패 (사용자 응답엔 영향 없음): policyId={}, error={}",
                    policyId, e.toString());
        }
    }
```

또한 클래스 상단 import 섹션 + 상수 추가:

```java
import java.util.ArrayList;
// ...

private static final TypeReference<List<String>> FOLLOW_UPS_TYPE = new TypeReference<>() {};
```

(SOURCES_TYPE 상수 옆에)

- [ ] **Step 6: 빌드 + 기존 테스트 통과 확인**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.*"`
Expected: 모든 기존 QnA 테스트 통과 (마이그레이션은 정적 호환만, 동작 회귀 없어야 함)

- [ ] **Step 7: 운영 DB 마이그레이션 노트**

`backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql`을 운영 환경에 수동 적용해야 함을 PR 설명에 명시한다 (다른 SQL 파일들과 같은 운영 패턴).

### Task 11: QnaLlmProvider 포트에 generateFollowUpQuestions 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java`

- [ ] **Step 1: 포트 메서드 추가**

`backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java`:

```java
package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.command.PolicyMetadata;

import java.util.List;
import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer);

    /**
     * 답변 본문을 받아 같은 정책 맥락에서 이어갈 후속 추천 질문 2~3개를 생성한다.
     * 실패 시 빈 리스트를 반환하거나 RuntimeException을 던질 수 있다 — 호출자가 graceful degrade.
     */
    List<String> generateFollowUpQuestions(String policyTitle, String question, String answer);
}
```

- [ ] **Step 2: 컴파일 (실패 예상)**

Run: `cd backend && ./gradlew compileJava`
Expected: FAIL — `OpenAiQnaClient`가 새 메서드 미구현

### Task 12: OpenAiQnaClient.generateFollowUpQuestions 구현 + 단위 테스트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`
- Test: `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientFollowUpTest.java` (신규)

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientFollowUpTest.java`:

```java
package com.youthfit.qna.infrastructure.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQnaClient.parseFollowUps")
class OpenAiQnaClientFollowUpTest {

    @Test
    @DisplayName("정상 JSON 배열을 List<String>으로 파싱한다")
    void parseValidJsonArray() {
        String response = "[\"필요 서류는?\", \"신청 마감일은?\", \"중복 수혜 가능?\"]";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("필요 서류는?", "신청 마감일은?", "중복 수혜 가능?");
    }

    @Test
    @DisplayName("코드펜스로 감싸진 JSON 도 파싱 (LLM 가끔 ```json...``` 출력)")
    void parseJsonWithCodeFence() {
        String response = "```json\n[\"질문1\", \"질문2\"]\n```";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("질문1", "질문2");
    }

    @Test
    @DisplayName("빈 응답이면 빈 리스트")
    void parseEmptyResponse() {
        assertThat(OpenAiQnaClient.parseFollowUps("")).isEmpty();
        assertThat(OpenAiQnaClient.parseFollowUps(null)).isEmpty();
    }

    @Test
    @DisplayName("JSON 이 아닌 응답은 빈 리스트")
    void parseNonJsonResponse() {
        assertThat(OpenAiQnaClient.parseFollowUps("질문이 떠오르지 않습니다.")).isEmpty();
    }

    @Test
    @DisplayName("JSON 배열이지만 string 외 요소가 섞인 경우 string 만 추출")
    void parseMixedArray() {
        String response = "[\"정상 질문\", 123, null, \"또 정상\"]";

        List<String> result = OpenAiQnaClient.parseFollowUps(response);

        assertThat(result).containsExactly("정상 질문", "또 정상");
    }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.infrastructure.external.OpenAiQnaClientFollowUpTest"`
Expected: FAIL — `parseFollowUps` 메서드 없음

- [ ] **Step 3: OpenAiQnaClient에 follow-up 메서드 + 헬퍼 추가**

`OpenAiQnaClient.java`에 다음 추가:

(1) 클래스 상수 영역에 follow-up용 시스템 프롬프트 추가:

```java
    private static final String FOLLOW_UP_SYSTEM_PROMPT = """
            당신은 청년 정책 후속 질문 제안 도우미입니다.
            방금 사용자에게 답변한 내용을 보고, 같은 정책 안에서 사용자가 자연스럽게 이어 물어볼 만한 후속 질문 2~3개를 제안하세요.

            규칙:
            - 출력은 JSON 배열만 — 다른 텍스트·설명·코드펜스 금지.
            - 예: ["질문1", "질문2", "질문3"]
            - 이미 답변에 명확히 포함된 정보를 다시 묻지 마세요.
            - 같은 정책 범위 안에서만 — 다른 정책으로 넘어가는 질문 금지.
            - 한국어, 자연스러운 의문문.
            """;

    private static final int FOLLOW_UP_MAX_TOKENS = 200;
```

(2) `generateFollowUpQuestions` 메서드 구현:

```java
    @Override
    public List<String> generateFollowUpQuestions(String policyTitle, String question, String answer) {
        String userMessage = "정책명: " + policyTitle + "\n\n사용자 질문: " + question + "\n\n방금 답변:\n" + answer;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", FOLLOW_UP_MAX_TOKENS,
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", FOLLOW_UP_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) return List.of();
            String content = choices.get(0).path("message").path("content").asText("");

            // 비용 메트릭 발행
            JsonNode usage = root.get("usage");
            int promptTokens = usage != null ? usage.path("prompt_tokens").asInt(0) : 0;
            int completionTokens = usage != null ? usage.path("completion_tokens").asInt(0) : 0;
            try {
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.QNA, properties.getModel(), promptTokens, completionTokens, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("qna follow-up LLM 비용 이벤트 발행 실패", e);
            }

            return parseFollowUps(content);
        } catch (Exception e) {
            log.warn("OpenAI follow-up 호출 실패: policyTitle={}, error={}", policyTitle, e.toString());
            return List.of();
        }
    }

    static List<String> parseFollowUps(String content) {
        if (content == null || content.isBlank()) return List.of();
        String trimmed = content.trim();
        // 코드펜스 제거
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
            trimmed = trimmed.trim();
        }
        if (!trimmed.startsWith("[")) return List.of();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode arr = mapper.readTree(trimmed);
            if (!arr.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode node : arr) {
                if (node != null && node.isString()) {
                    String s = node.asText();
                    if (!s.isBlank()) result.add(s);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.infrastructure.external.OpenAiQnaClientFollowUpTest"`
Expected: PASS — 5개 케이스 모두 통과

### Task 13: QnaService에서 follow-up 호출 + SUGGESTIONS 이벤트 송출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- Test: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`QnaServiceTest.java`에 새 `@Nested` 추가:

```java
    @Nested
    @DisplayName("후속 추천질문")
    class FollowUps {

        @Test
        @DisplayName("정상 답변 → follow-up LLM 호출 + 캐시에 저장")
        void followUps_generated_for_normal_answer() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(eq(10L), anyString())).willReturn(Optional.empty());
            given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
            given(semanticQnaCache.findSimilar(eq(10L), anyString(), any())).willReturn(SemanticLookupResult.miss());
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS_NO_NEIGHBOR);
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    new PolicyDocumentChunkResult(1L, null, 0, "본문", 0.1, null, null)
            ));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });
            given(qnaLlmProvider.generateFollowUpQuestions(anyString(), anyString(), anyString()))
                    .willReturn(List.of("후속A", "후속B"));

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            qnaService.askQuestion(new AskQuestionCommand(10L, "신청 자격?", 1L));
            Thread.sleep(200);

            verify(qnaLlmProvider).generateFollowUpQuestions(anyString(), eq("신청 자격?"), anyString());
            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().followUpQuestions()).containsExactly("후속A", "후속B");
        }

        @Test
        @DisplayName("fallback 답변 → follow-up 호출 스킵")
        void followUps_skipped_for_fallback() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(eq(10L), anyString())).willReturn(Optional.empty());
            given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
            given(semanticQnaCache.findSimilar(eq(10L), anyString(), any())).willReturn(SemanticLookupResult.miss());
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS_NO_NEIGHBOR);
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    new PolicyDocumentChunkResult(1L, null, 0, "본문", 0.1, null, null)
            ));
            String fallback = "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept(fallback);
                        return fallback;
                    });

            qnaService.askQuestion(new AskQuestionCommand(10L, "내가 받을 수 있나요?", 1L));
            Thread.sleep(200);

            verify(qnaLlmProvider, never()).generateFollowUpQuestions(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("follow-up LLM 실패 → 본문 답변/캐시는 정상, follow-up 만 빈 리스트")
        void followUps_llm_failure_graceful() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(eq(10L), anyString())).willReturn(Optional.empty());
            given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
            given(semanticQnaCache.findSimilar(eq(10L), anyString(), any())).willReturn(SemanticLookupResult.miss());
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS_NO_NEIGHBOR);
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    new PolicyDocumentChunkResult(1L, null, 0, "본문", 0.1, null, null)
            ));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });
            given(qnaLlmProvider.generateFollowUpQuestions(anyString(), anyString(), anyString()))
                    .willReturn(List.of()); // OpenAiQnaClient 가 실패 시 빈 리스트 반환하도록 설계

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            qnaService.askQuestion(new AskQuestionCommand(10L, "질문?", 1L));
            Thread.sleep(200);

            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().answer()).contains("정상 답변");
            assertThat(cacheCaptor.getValue().followUpQuestions()).isEmpty();
        }
    }
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaServiceTest"`
Expected: FAIL — `generateFollowUpQuestions`가 호출되지 않음

- [ ] **Step 3: QnaService에 follow-up 호출 + SUGGESTIONS 이벤트 통합**

`QnaService.java`의 LLM 호출 + 푸터 첨부 직후 (sources 분기 처리 후, `sendSourcesEvent` 직전) 부분을 다음으로 교체:

```java
        // ⑤ LLM 스트림
        String fullAnswer;
        PolicyMetadata metadata = PolicyMetadata.from(policy);
        try {
            fullAnswer = qnaLlmProvider.generateAnswer(
                    policy.getTitle(), metadata, context, command.question(),
                    chunk -> sendChunkEvent(emitter, chunk)
            );
        } catch (Exception e) {
            log.error("LLM 호출 실패: policyId={}", command.policyId(), e);
            sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
            historyWriter.markFailed(historyId, QnaFailedReason.LLM_ERROR);
            emitter.completeWithError(e);
            return;
        }

        boolean isFallback = isFallbackAnswer(fullAnswer);

        // 푸터 첨부 (fallback 답변엔 안 붙임)
        fullAnswer = QnaContactFooter.appendIfPossible(
                fullAnswer, metadata.organization(), metadata.contact(), isFallback);
        if (!isFallback && !isBlank(metadata.organization()) && !isBlank(metadata.contact())) {
            sendChunkEvent(emitter, "\n\n---\n\n📞 문의: " + metadata.organization() + " · " + metadata.contact());
        }

        // sources 분기
        if (isFallback) {
            sources = List.of();
        } else if (passing.isEmpty()) {
            sources = List.of(new QnaSourceResult(
                    command.policyId(), null, "정책 기본 정보", null, null,
                    "정책 메타데이터 기반 답변"
            ));
        }

        sendSourcesEvent(emitter, sources);

        // follow-up 생성 (fallback / chunks 0건 거절 시 위 분기에서 이미 return됨)
        List<String> followUps = List.of();
        if (!isFallback) {
            try {
                followUps = qnaLlmProvider.generateFollowUpQuestions(
                        policy.getTitle(), command.question(), fullAnswer);
            } catch (Exception e) {
                log.warn("follow-up 생성 실패 (정상 흐름 진행): policyId={}, error={}",
                        command.policyId(), e.toString());
            }
            if (!followUps.isEmpty()) {
                sendSuggestionsEvent(emitter, followUps);
            }
        }

        sendDoneEvent(emitter);
        emitter.complete();

        // ⑥ 캐시 저장
        CachedAnswer answer = new CachedAnswer(fullAnswer, sources, followUps, Instant.now());
```

또한 `sendSuggestionsEvent` 헬퍼 메서드 추가 (다른 send 헬퍼들 옆에):

```java
    private void sendSuggestionsEvent(SseEmitter emitter, List<String> questions) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "SUGGESTIONS", "questions", questions)));
        } catch (IOException e) {
            log.warn("SSE SUGGESTIONS 이벤트 전송 실패", e);
        }
    }
```

또한 `sendCachedAnswer` 메서드도 follow-up 송출하도록 업데이트:

```java
    private void sendCachedAnswer(SseEmitter emitter, CachedAnswer cached, Long historyId) {
        sendChunkEvent(emitter, cached.answer());
        sendSourcesEvent(emitter, cached.sources());
        if (!cached.followUpQuestions().isEmpty()) {
            sendSuggestionsEvent(emitter, cached.followUpQuestions());
        }
        sendDoneEvent(emitter);
        emitter.complete();
        try {
            String sourcesJson = objectMapper.writeValueAsString(cached.sources());
            historyWriter.markCompleted(historyId, cached.answer(), sourcesJson);
        } catch (Exception e) {
            log.error("Q&A 캐시 히트 history markCompleted 실패: historyId={}", historyId, e);
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaServiceTest"`
Expected: PASS — follow-up 신규 3개 + 푸터 1개 + 기존 케이스 모두 통과

- [ ] **Step 5: 빌드 + 전체 백엔드 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋 + PR 안내**

```bash
git add backend/src/main/java/com/youthfit/qna/ backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql backend/src/test/java/com/youthfit/qna/
git commit -m "$(cat <<'EOF'
feat(qna): 후속 추천질문 생성 + SUGGESTIONS SSE 이벤트

답변 본문 송출 후 별도 LLM 호출로 같은 정책 맥락의 후속 질문 2~3개를
생성해 SUGGESTIONS 이벤트로 송출. 캐시(Redis + pgvector)에도 저장.
fallback / chunks 0건 / LLM 실패 시 graceful degrade — 본문 답변엔 영향 없음.

DB 마이그레이션: backend/src/main/resources/sql/2026-05-11-qna-follow-ups-column.sql
운영 환경에 수동 적용 필요.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

이후 사용자에게 `/create-pr` 실행 안내 — PR 본문에 마이그레이션 SQL 적용 안내 포함하도록 권장.

---

## PR-5: 후속 추천질문 프론트엔드 (B-2 2/2)

**범위:** `QnaSuggestionChips` 일반화 + `QnaMessageBubble`에서 follow-up 칩 표시.

> **선행 조건:** PR-4가 머지되어 SSE 송출 가능한 상태여야 한다. 단, PR-4가 머지 안 돼도 프론트엔드는 안전하게 동작 (SUGGESTIONS 미수신 = 칩 미표시).

### Task 14: QnaSuggestionChips를 prop 일반화

**Files:**
- Modify: `frontend/src/components/qna/QnaSuggestionChips.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx` (신규)

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx`:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QnaSuggestionChips } from '../QnaSuggestionChips';

describe('QnaSuggestionChips', () => {
  it('questions prop 미지정 시 default 4개를 렌더한다', () => {
    render(<QnaSuggestionChips onPick={vi.fn()} />);

    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
    expect(screen.getByText('어떤 서류가 필요한가요?')).toBeInTheDocument();
    expect(screen.getByText('신청은 언제까지인가요?')).toBeInTheDocument();
    expect(screen.getByText('지원 금액은 얼마인가요?')).toBeInTheDocument();
  });

  it('questions prop 지정 시 그것만 렌더한다', () => {
    render(
      <QnaSuggestionChips questions={['후속A', '후속B']} onPick={vi.fn()} />,
    );

    expect(screen.getByText('후속A')).toBeInTheDocument();
    expect(screen.getByText('후속B')).toBeInTheDocument();
    expect(screen.queryByText('신청 자격이 어떻게 되나요?')).not.toBeInTheDocument();
  });

  it('칩 클릭 시 onPick 이 해당 질문으로 호출된다', () => {
    const onPick = vi.fn();
    render(
      <QnaSuggestionChips questions={['후속A', '후속B']} onPick={onPick} />,
    );

    fireEvent.click(screen.getByText('후속B'));

    expect(onPick).toHaveBeenCalledWith('후속B');
  });

  it('questions 가 빈 배열이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(
      <QnaSuggestionChips questions={[]} onPick={vi.fn()} />,
    );
    expect(container.querySelector('button')).toBeNull();
  });
});
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd frontend && npx vitest run src/components/qna/__tests__/QnaSuggestionChips.test.tsx`
Expected: FAIL — prop 시그니처 불일치 (현재는 prop 없음)

- [ ] **Step 3: QnaSuggestionChips 일반화**

`frontend/src/components/qna/QnaSuggestionChips.tsx`를 다음으로 교체:

```typescript
const DEFAULT_SUGGESTIONS = [
  '신청 자격이 어떻게 되나요?',
  '어떤 서류가 필요한가요?',
  '신청은 언제까지인가요?',
  '지원 금액은 얼마인가요?',
] as const;

interface Props {
  questions?: readonly string[];
  onPick: (question: string) => void;
}

export function QnaSuggestionChips({ questions, onPick }: Props) {
  const items = questions ?? DEFAULT_SUGGESTIONS;
  if (items.length === 0) return null;

  return (
    <div className="flex flex-wrap justify-center gap-2">
      {items.map((q) => (
        <button
          key={q}
          type="button"
          onClick={() => onPick(q)}
          className="rounded-full border border-chat-accent/30 bg-chat-accent/10 min-h-11 px-[14px] py-2 text-[13px] text-chat-accent transition hover:-translate-y-px hover:border-chat-accent/50 hover:bg-chat-accent/25 focus-visible:outline-2 focus-visible:outline-chat-accent focus-visible:outline-offset-2"
        >
          {q}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npx vitest run src/components/qna/__tests__/QnaSuggestionChips.test.tsx`
Expected: PASS — 4개 케이스 모두 통과

- [ ] **Step 5: 시작 화면 호출부가 그대로 동작하는지 회귀 테스트**

`QnaSuggestionChips`를 사용하는 다른 호출부(`QnaChatSection.tsx` 등)를 grep으로 찾아 prop 없이 호출하면 default fallback이 동작하는지 빌드로 확인:

Run: `cd frontend && grep -rn "QnaSuggestionChips" src/`
Expected: 호출부에서 `<QnaSuggestionChips onPick={...} />` 형태로 prop 없이 호출되는 곳이 default를 쓴다.

Run: `cd frontend && npx tsc --noEmit`
Expected: 타입 에러 0건.

### Task 15: QnaMessageBubble에 follow-up 칩 표시

**Files:**
- Modify: `frontend/src/components/qna/QnaMessageBubble.tsx`

- [ ] **Step 1: import 추가 + 칩 렌더 블록 삽입**

`QnaMessageBubble.tsx` 상단에 import 추가:

```typescript
import { QnaSuggestionChips } from './QnaSuggestionChips';
```

답변 메시지 내부, **출처 카드 블록 직후 + 복사 버튼 블록 직전** 위치에 다음 추가:

```typescript
        {!isError &&
          message.status === 'done' &&
          message.followUpQuestions &&
          message.followUpQuestions.length > 0 && (
            <div className="mt-3">
              <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-chat-soft">
                이어서 물어볼 만한 질문
              </p>
              <QnaSuggestionChips
                questions={message.followUpQuestions}
                onPick={(q) => onFollowUpPick(q)}
              />
            </div>
          )}
```

- [ ] **Step 2: onFollowUpPick prop을 컴포넌트 시그니처에 추가**

`QnaMessageBubble.tsx`의 `Props` 인터페이스를 확장:

```typescript
interface Props {
  message: QnaMessage;
  onCopy: (content: string) => Promise<void>;
  onRetry: (assistantMessageId: string) => void;
  onFollowUpPick: (question: string) => void;
}
```

함수 파라미터 destructuring도 같이 갱신:

```typescript
export function QnaMessageBubble({ message, onCopy, onRetry, onFollowUpPick }: Props) {
```

- [ ] **Step 3: 호출부(QnaMessageList)가 onFollowUpPick prop을 넘겨주도록 수정**

`frontend/src/components/qna/QnaMessageList.tsx`를 읽어 `<QnaMessageBubble ... />` 호출 부분에 `onFollowUpPick` 전달. 기존 호출부에 `onSendFollowUp` 같은 prop이 이미 있으면 재사용, 없으면 새로 추가:

```typescript
// QnaMessageList.tsx 시그니처에 onFollowUpPick 추가
interface Props {
  // ... 기존 prop
  onFollowUpPick: (question: string) => void;
}

// 그리고 매핑부에
<QnaMessageBubble
  key={msg.id}
  message={msg}
  onCopy={onCopy}
  onRetry={onRetry}
  onFollowUpPick={onFollowUpPick}
/>
```

- [ ] **Step 4: QnaChatSection (또는 useQnaChat 호출 page) 에서 send를 follow-up pick에 연결**

`QnaChatSection.tsx`(또는 사용 페이지)에서 `useQnaChat`의 `send`를 `onFollowUpPick`으로 전달:

```typescript
<QnaMessageList
  messages={messages}
  onCopy={copy}
  onRetry={retry}
  onFollowUpPick={send}
/>
```

- [ ] **Step 5: 타입체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 errors

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 6: 전체 프론트 테스트**

Run: `cd frontend && npm run test`
Expected: 모든 테스트 통과

- [ ] **Step 7: 수동 검증 — 백엔드 + 프론트 e2e**

PR-4가 머지된 환경(또는 로컬 backend 실행)에서 정책 상세 Q&A에 질문을 던지고:
1. 답변이 마크다운 구조로 흐른다
2. 답변 끝에 푸터가 붙는다 (정책에 contact 있을 때)
3. SOURCES 카드가 컴팩트 라인 + 펼치기로 보인다
4. 답변 완료 후 follow-up 칩 2~3개가 표시된다
5. 칩 클릭하면 해당 질문으로 새 답변이 흐른다

각 정책 1~2개 시도. 모바일 뷰포트 확인.

- [ ] **Step 8: 커밋 + PR 안내**

```bash
git add frontend/src/components/qna/QnaSuggestionChips.tsx frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx frontend/src/components/qna/QnaMessageBubble.tsx frontend/src/components/qna/QnaMessageList.tsx frontend/src/components/qna/QnaChatSection.tsx
git commit -m "$(cat <<'EOF'
feat(fe): Q&A 답변별 후속 추천질문 칩 표시

QnaSuggestionChips 컴포넌트를 prop 일반화해 답변 메시지 내부에서
followUpQuestions 를 렌더한다. 시작 화면 default 호출부는 그대로 동작.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

이후 사용자에게 `/create-pr` 실행 안내.

---

## Self-Review

### Spec coverage

| Spec 요구사항 | 구현 task |
|---|---|
| A. 마크다운 형식 강화 (시스템 프롬프트) | Task 1 |
| B. 후속 추천질문 (별도 LLM 호출) | Task 11 (포트), 12 (어댑터), 13 (오케스트레이션), 14-15 (프론트) |
| C. 공식 문의처 푸터 (코드 후처리) | Task 7 (헬퍼), 8 (서비스 통합) |
| D. 출처 카드 펼치기 | Task 2 (타입), 3 (파서), 4 (훅), 5 (컴포넌트), 6 (위임) |
| SUGGESTIONS SSE 이벤트 추가 | Task 13 (백엔드), Task 3 (프론트 파서) |
| 캐시 마이그레이션 (Redis + pgvector) | Task 9 (record), 10 (DB 컬럼 + 어댑터) |
| Fallback 답변에는 follow-up·푸터 미포함 | Task 7-8 (푸터), Task 13 (follow-up) |
| 사용자 disconnect 시 graceful degrade | Task 13 — IOException catch는 기존 sendChunkEvent 패턴 그대로 |
| 비용 메트릭 발행 | Task 12 — `LlmCallRecorded` 발행 |

### Placeholder scan

- "TBD/TODO" — 없음
- "유사한 작업으로 처리" — 없음 (모든 코드 명시)
- "Add appropriate error handling" — 없음 (구체적 try/catch 명시)
- "Write tests" without code — 없음 (모든 테스트 본문 포함)

### Type consistency

- `QnaSource` (frontend): policyId / attachmentLabel / pageStart / pageEnd / excerpt — Task 2~6 일관 사용
- `QnaSourceResult` (backend): policyId / attachmentId / attachmentLabel / pageStart / pageEnd / excerpt — 기존 record 그대로
- `CachedAnswer.followUpQuestions` (backend): `List<String>` — Task 9, 10, 13 일관
- `followUpQuestions` (frontend message field): `string[]` — Task 4, 15 일관
- `QnaContactFooter.appendIfPossible(answer, organization, contact, isFallbackAnswer)` — Task 7 정의, Task 8 사용 시그니처 일치
- `OpenAiQnaClient.parseFollowUps(content)` — Task 12 정의, 같은 task 내에서만 사용
- SSE 이벤트 type 문자열: `CHUNK / SOURCES / SUGGESTIONS / DONE / ERROR` — Task 13 (백엔드 송출), Task 3 (프론트 파서) 일관

### 알려진 가정

- `QnaQuestionCacheRepositoryImpl`는 native query 형태로 추정 — Task 10 Step 4에서 실제 코드를 보고 SELECT 절과 record 매핑을 함께 갱신해야 한다. JPA 자동 매핑이라면 entity 필드 추가만으로 충분할 수 있음.
- `mockPolicy` 헬퍼가 organization/contact를 stub하지 않으면 Task 8 푸터 테스트가 실패 — Step 3에서 헬퍼 보강 안내.
- `QnaChatSection`의 `useQnaChat` 호출 위치가 변경되어 있을 수 있음 — Task 15 Step 4에서 실제 파일을 보고 적용.

이 가정들이 깨지면 task 안에서 실제 코드를 확인하고 조정한다.
