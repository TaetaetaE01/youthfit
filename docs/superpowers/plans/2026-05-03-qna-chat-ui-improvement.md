# Q&A 채팅 UI 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 상세 페이지의 Q&A 채팅 UI 를 종합적으로 리프레시한다 — 차분한 가독성(ChatGPT 톤) + 마이크로 모션(Linear 톤), 새 navy/sky 4컬러 팔레트, 마크다운 렌더, SSE 2단계 인디케이터, 복사/재시도/jump-to-bottom/추천 칩.

**Architecture:** `pages/PolicyDetailPage.tsx` 내부 인라인 정의(약 170줄) 를 `components/qna/` 도메인 폴더로 추출한다. 메시지 상태/SSE/abort 로직을 `hooks/useQnaChat.ts` 훅에 캡슐화. 디자인 토큰 7개와 keyframe 5종을 챗봇 전용 네임스페이스(`--color-chat-*`, `qna-*`)로 `index.css` 에 추가하되 시스템 전역 brand 컬러는 건드리지 않는다.

**Tech Stack:** React 19 + TypeScript 6, Tailwind CSS v4 (`@theme`/`@utility` 패턴), Vitest + Testing Library, react-markdown (신규), zustand (기존 authStore).

**Spec:** `docs/superpowers/specs/2026-05-03-qna-chat-ui-improvement-design.md`

---

## Task 1: 디자인 토큰 + 키프레임 추가

**목적:** 후속 컴포넌트가 참조할 챗봇 전용 CSS 토큰과 애니메이션 유틸리티를 `index.css` 에 먼저 정의한다. 시스템 전역 brand 컬러는 건드리지 않는다.

**Files:**
- Modify: `frontend/src/index.css`

- [ ] **Step 1: `@theme` 블록 끝에 챗봇 전용 토큰 7개 추가**

`frontend/src/index.css` 파일에서 기존 `@theme { ... }` 블록 (1-26줄 근처) 의 마지막 닫는 `}` 직전에 다음을 추가한다:

```css
  /* Chat (Q&A) — 챗봇 전용 토큰. 시스템 전역 brand 마이그레이션은 별도 spec */
  --color-chat-surface-deep: #0F2854;
  --color-chat-surface: #1C4D8D;
  --color-chat-soft: #4988C4;
  --color-chat-accent: #BDE8F5;
  --color-chat-bubble: #FFFFFF;
  --color-chat-bubble-text: #0F2854;
  --color-chat-source-bg: #EFF7FA;
```

- [ ] **Step 2: 챗봇 keyframe 5종 추가**

기존 `@keyframes highlight-fade {...}` 블록 다음 줄 (52줄 근처) 에 추가:

```css
@keyframes qna-msg-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes qna-typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-5px); opacity: 1; }
}
@keyframes qna-cursor-blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}
@keyframes qna-check-pop {
  0% { transform: scale(0.5); opacity: 0; }
  60% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}
@keyframes qna-jump-in {
  from { opacity: 0; transform: translateY(8px) scale(0.9); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
```

- [ ] **Step 3: `@utility` 5종 + 스크롤바 유틸리티 추가**

기존 `@utility animate-cta-glow {...}` 블록 다음 (72줄 근처) 에 추가:

```css
@utility animate-qna-msg-in {
  animation: qna-msg-in 320ms ease-out both;
}
@utility animate-qna-typing-bounce {
  animation: qna-typing-bounce 1.2s infinite ease-in-out;
}
@utility animate-qna-cursor-blink {
  animation: qna-cursor-blink 1s infinite;
}
@utility animate-qna-check-pop {
  animation: qna-check-pop 280ms ease-out;
}
@utility animate-qna-jump-in {
  animation: qna-jump-in 240ms ease-out;
}

/* 챗봇 스크롤바: 6px sky 톤 반투명 */
@utility scrollbar-qna {
  scrollbar-width: thin;
  scrollbar-color: rgba(189, 232, 245, 0.3) transparent;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb {
    background: rgba(189, 232, 245, 0.3);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: rgba(189, 232, 245, 0.5);
  }
}
```

- [ ] **Step 4: `prefers-reduced-motion` 미디어 쿼리 추가**

파일 끝 `body { ... }` 블록 다음에 추가:

```css
/* a11y: 모션 줄임 모드에서 챗봇 애니메이션 비활성/정적 처리 */
@media (prefers-reduced-motion: reduce) {
  @keyframes qna-msg-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @keyframes qna-typing-bounce {
    0%, 100% { opacity: 1; transform: translateY(0); }
  }
  @keyframes qna-cursor-blink {
    0%, 100% { opacity: 1; }
  }
  @keyframes qna-jump-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @keyframes qna-check-pop {
    from, to { transform: scale(1); opacity: 1; }
  }
}
```

- [ ] **Step 5: 빌드 검증**

```bash
cd frontend && npm run build
```

기대: 에러 없이 dist 생성. Tailwind v4 가 새 `@utility` 와 `@keyframes` 를 빌드함.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/index.css
git commit -m "$(cat <<'EOF'
style(qna): 챗봇 전용 디자인 토큰 + 키프레임 추가

- --color-chat-* 7종 토큰 추가 (#0F2854 / #1C4D8D / #4988C4 / #BDE8F5 / 흰색 / 본문 / 출처 bg)
- qna-msg-in / typing-bounce / cursor-blink / check-pop / jump-in 5종 keyframe
- animate-qna-* 5종 utility + scrollbar-qna 유틸
- prefers-reduced-motion 모드 처리

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 도메인 타입 신설 (`types/qna.ts`)

**목적:** `QnaMessage` 를 `types/policy.ts` 에서 분리해 `types/qna.ts` 신설, `loading: boolean` 을 `status: QnaStatus` 3-state 로 확장. 기존 사용처는 임포트 경로만 변경.

**Files:**
- Create: `frontend/src/types/qna.ts`
- Modify: `frontend/src/types/policy.ts` (QnaMessage 정의 제거)
- Modify: `frontend/src/pages/PolicyDetailPage.tsx` (QnaMessage 임포트 경로 변경, `loading: true/false` 사용처를 `status` 로 변경)

- [ ] **Step 1: `types/qna.ts` 신설**

```ts
// frontend/src/types/qna.ts

export type QnaRole = 'user' | 'assistant';
export type QnaStatus = 'streaming' | 'done' | 'error';

export interface QnaMessage {
  id: string;
  role: QnaRole;
  content: string;
  sources?: string[];
  status: QnaStatus;
  /** assistant 메시지가 어느 user 질문에 속하는지 — retry 시 question 복원용 */
  questionRef?: string;
}
```

- [ ] **Step 2: `types/policy.ts` 에서 `QnaMessage` 인터페이스 제거**

`frontend/src/types/policy.ts` 의 148-156줄 (`/* ── Q&A ── */` 주석과 `QnaMessage` 인터페이스 전체) 를 삭제한다.

- [ ] **Step 3: `PolicyDetailPage.tsx` 임포트 경로 변경**

`frontend/src/pages/PolicyDetailPage.tsx` 의 45-51줄 import 블록에서 `QnaMessage` 만 새 경로로 분리:

```tsx
import type {
  PolicyDetail,
  EligibilityResponse,
  EligibilityResult,
  CriterionItem,
} from '@/types/policy';
import type { QnaMessage } from '@/types/qna';
```

- [ ] **Step 4: 인라인 `QnaChatSection` 의 `loading` 사용처를 `status` 로 변환**

`PolicyDetailPage.tsx` 의 인라인 `QnaChatSection` (현재 약 448-617줄) 안에서 다음 위치를 변경:

- 469-473줄 (userMsg 생성):
```tsx
const userMsg: QnaMessage = {
  id: `user-${Date.now()}`,
  role: 'user',
  content: text,
  status: 'done',
};
```

- 475-481줄 (assistantMsg 생성):
```tsx
const assistantId = `assistant-${Date.now()}`;
const assistantMsg: QnaMessage = {
  id: assistantId,
  role: 'assistant',
  content: '',
  status: 'streaming',
  questionRef: userMsg.id,
};
```

- 504-509줄 (onDone 콜백):
```tsx
() => {
  setMessages((prev) =>
    prev.map((m) =>
      m.id === assistantId ? { ...m, status: 'done' as const } : m,
    ),
  );
},
```

- 511-519줄 (onError 콜백):
```tsx
() => {
  setMessages((prev) =>
    prev.map((m) =>
      m.id === assistantId
        ? { ...m, status: 'error' as const, content: '답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.' }
        : m,
    ),
  );
},
```

- 580-582줄 (loading 표시 로직):
```tsx
{msg.status === 'streaming' && (
  <span className="mt-1 inline-block h-4 w-1 animate-pulse bg-white/60" />
)}
```

- [ ] **Step 5: 타입체크 + 빌드**

```bash
cd frontend && npm run build
```

기대: `tsc -b` 가 타입 에러 없이 통과, vite build 성공. 기존 `loading` 필드 참조가 어디에도 남아있지 않다는 것을 확인.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/types/qna.ts frontend/src/types/policy.ts frontend/src/pages/PolicyDetailPage.tsx
git commit -m "$(cat <<'EOF'
refactor(qna): QnaMessage 타입 분리 + status 3-state 로 확장

- types/qna.ts 신설, types/policy.ts 에서 분리
- loading: boolean → status: 'streaming' | 'done' | 'error'
- questionRef 필드 추가 (retry 시 question 복원용)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: react-markdown 의존성 추가

**목적:** 마크다운 렌더링용 단일 의존성 도입. 다른 변경 없이 `package.json` + `package-lock.json` 만 갱신.

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json` (npm 자동 갱신)

- [ ] **Step 1: react-markdown 설치**

```bash
cd frontend && npm install react-markdown@^10
```

기대: `frontend/package.json` 의 `dependencies` 에 `"react-markdown": "^10.x.x"` 추가, lockfile 갱신.

- [ ] **Step 2: 설치 검증**

```bash
cd frontend && node -e "console.log(require('react-markdown/package.json').version)"
```

기대: 버전 번호 (예: `10.1.0`) 가 출력. 모듈 해석 정상.

- [ ] **Step 3: 빌드**

```bash
cd frontend && npm run build
```

기대: 에러 없이 dist 생성.

- [ ] **Step 4: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "$(cat <<'EOF'
chore(deps): react-markdown 추가

Q&A 답변의 마크다운 렌더링용. remark-gfm/rehype-raw 는 추가하지 않음 (정책 답변은 표/코드블록 불필요, raw HTML 차단으로 XSS 방지).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: SSE API 시그니처 변경 (`apis/qna.api.ts`)

**목적:** 콜백 5개를 callbacks 객체로 묶고 `AbortSignal` 인자 추가. 기존 호출처(`PolicyDetailPage.tsx` 의 인라인 `QnaChatSection`)도 같은 PR 에서 새 시그니처로 동기화 → 빌드 항상 그린.

**Files:**
- Modify: `frontend/src/apis/qna.api.ts`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx` (인라인 QnaChatSection 의 fetchQnaAnswer 호출부)
- Test: `frontend/src/apis/__tests__/qna.api.test.ts` (신규)

- [ ] **Step 1: 실패 테스트 작성 (`apis/__tests__/qna.api.test.ts`)**

```ts
// frontend/src/apis/__tests__/qna.api.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchQnaAnswer } from '../qna.api';

function makeSseStream(events: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      events.forEach((line) => controller.enqueue(encoder.encode(line + '\n')));
      controller.close();
    },
  });
}

describe('fetchQnaAnswer', () => {
  beforeEach(() => {
    vi.spyOn(global, 'fetch');
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('CHUNK / SOURCES / DONE 이벤트를 파싱해 콜백을 호출한다', async () => {
    const stream = makeSseStream([
      'data: {"type":"CHUNK","content":"안녕"}',
      'data: {"type":"CHUNK","content":"하세요"}',
      'data: {"type":"SOURCES","sources":[{"policyId":1,"attachmentLabel":"청년정책 시행계획","pageStart":12,"pageEnd":13}]}',
      'data: {"type":"DONE"}',
    ]);

    (global.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(
      new Response(stream, { status: 200 }),
    );

    const onChunk = vi.fn();
    const onSources = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await fetchQnaAnswer(
      1,
      '신청 자격은?',
      { onChunk, onSources, onDone, onError },
      'token-abc',
    );

    expect(onChunk).toHaveBeenNthCalledWith(1, '안녕');
    expect(onChunk).toHaveBeenNthCalledWith(2, '하세요');
    expect(onSources).toHaveBeenCalledWith(['청년정책 시행계획 p.12-13']);
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });

  it('AbortSignal 로 취소되면 silent 하게 종료한다', async () => {
    const controller = new AbortController();
    (global.fetch as unknown as ReturnType<typeof vi.fn>).mockImplementationOnce(
      (_url, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            reject(new DOMException('aborted', 'AbortError'));
          });
        }),
    );

    const onError = vi.fn();
    const onDone = vi.fn();

    const p = fetchQnaAnswer(
      1,
      'q',
      { onChunk: vi.fn(), onSources: vi.fn(), onDone, onError },
      'token',
      controller.signal,
    );
    controller.abort();
    await p;

    expect(onError).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });

  it('인증 토큰이 없으면 onError 호출하고 fetch 안 함', async () => {
    const onError = vi.fn();
    await fetchQnaAnswer(
      1,
      'q',
      { onChunk: vi.fn(), onSources: vi.fn(), onDone: vi.fn(), onError },
      null,
    );
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: '인증이 필요합니다' }));
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/apis/__tests__/qna.api.test.ts
```

기대: FAIL — 시그니처 불일치 (현재는 콜백을 객체가 아닌 개별 인자로 받음).

- [ ] **Step 3: `apis/qna.api.ts` 시그니처 변경**

`frontend/src/apis/qna.api.ts` 전체를 다음으로 교체:

```ts
const QNA_URL = '/api/v1/qna/ask';

export interface QnaCallbacks {
  onChunk: (text: string) => void;
  onSources: (sources: string[]) => void;
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
  const { onChunk, onSources, onDone, onError } = callbacks;

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
      return; // 의도적 취소: silent
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
            const sourceStrings: string[] = Array.from(
              new Set(
                (parsed.sources ?? []).map(
                  (s: {
                    policyId?: number;
                    attachmentLabel?: string | null;
                    pageStart?: number | null;
                    pageEnd?: number | null;
                  }) => {
                    const label = s.attachmentLabel ?? `정책 #${s.policyId}`;
                    const page =
                      s.pageStart && s.pageEnd
                        ? s.pageStart === s.pageEnd
                          ? ` p.${s.pageStart}`
                          : ` p.${s.pageStart}-${s.pageEnd}`
                        : '';
                    return `${label}${page}`;
                  },
                ),
              ),
            );
            onSources(sourceStrings);
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
      return; // 의도적 취소: silent
    }
    onError(e instanceof Error ? e : new Error('스트림 읽기 오류'));
    return;
  }

  onDone();
}
```

- [ ] **Step 4: 인라인 호출처 (`PolicyDetailPage.tsx`) 동기 업데이트**

`PolicyDetailPage.tsx` 의 인라인 `QnaChatSection` 안 (현재 약 485-521줄) 의 `fetchQnaAnswer` 호출부를 다음으로 교체:

```tsx
fetchQnaAnswer(
  policyId,
  text,
  {
    onChunk: (chunk) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, content: m.content + chunk }
            : m,
        ),
      );
    },
    onSources: (sources) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, sources } : m,
        ),
      );
    },
    onDone: () => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, status: 'done' as const } : m,
        ),
      );
    },
    onError: () => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, status: 'error' as const, content: '답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.' }
            : m,
        ),
      );
    },
  },
  accessToken,
);
```

- [ ] **Step 5: 테스트 + 빌드 검증**

```bash
cd frontend && npx vitest run src/apis/__tests__/qna.api.test.ts && npm run build
```

기대: 테스트 3개 PASS, 빌드 성공.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/apis/qna.api.ts frontend/src/apis/__tests__/qna.api.test.ts frontend/src/pages/PolicyDetailPage.tsx
git commit -m "$(cat <<'EOF'
refactor(qna): SSE API 시그니처 개선 (callbacks 객체 + AbortSignal)

- 4개 콜백을 callbacks 객체로 묶어 시그니처 정리
- AbortSignal 인자 추가, AbortError 는 silent return
- 단위 테스트 추가 (CHUNK/SOURCES/DONE 파싱, abort, 미인증)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `useQnaChat` 훅 + 단위 테스트

**목적:** 메시지 상태/SSE/abort/retry 로직을 단일 훅에 캡슐화. TDD 로 작성.

**Files:**
- Create: `frontend/src/hooks/useQnaChat.ts`
- Test: `frontend/src/hooks/__tests__/useQnaChat.test.ts`

- [ ] **Step 1: 테스트 디렉토리 생성**

```bash
mkdir -p frontend/src/hooks/__tests__
```

- [ ] **Step 2: 실패 테스트 작성**

```ts
// frontend/src/hooks/__tests__/useQnaChat.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useQnaChat } from '../useQnaChat';
import * as qnaApi from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';

vi.mock('@/apis/qna.api');

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: 'test-token', isAuthenticated: true });
});

describe('useQnaChat', () => {
  it('send 시 user/assistant 메시지가 push 된다', () => {
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async () => {});
    const { result } = renderHook(() => useQnaChat(1));

    act(() => {
      result.current.send('신청 자격은?');
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0]).toMatchObject({
      role: 'user',
      content: '신청 자격은?',
      status: 'done',
    });
    expect(result.current.messages[1]).toMatchObject({
      role: 'assistant',
      content: '',
      status: 'streaming',
    });
    expect(result.current.messages[1].questionRef).toBe(result.current.messages[0].id);
  });

  it('onChunk 콜백으로 assistant content 가 누적된다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));

    await waitFor(() => expect(callbacks).not.toBeNull());

    act(() => callbacks!.onChunk('안녕'));
    act(() => callbacks!.onChunk('하세요'));

    expect(result.current.messages[1].content).toBe('안녕하세요');
  });

  it('onError 시 status 가 error 로 바뀐다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    act(() => callbacks!.onError(new Error('boom')));

    expect(result.current.messages[1].status).toBe('error');
    expect(result.current.messages[1].content).toContain('답변을 생성하지 못했습니다');
  });

  it('onDone 시 isStreaming 이 false 가 된다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    expect(result.current.isStreaming).toBe(true);
    act(() => callbacks!.onDone());
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.messages[1].status).toBe('done');
  });

  it('retry 는 같은 assistant 메시지를 제자리에서 reset 한다', async () => {
    const calls: qnaApi.QnaCallbacks[] = [];
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      calls.push(cb);
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(calls).toHaveLength(1));
    act(() => calls[0].onError(new Error('boom')));
    expect(result.current.messages[1].status).toBe('error');

    const assistantId = result.current.messages[1].id;
    act(() => result.current.retry(assistantId));

    expect(result.current.messages).toHaveLength(2); // 새 메시지 추가 안 됨
    expect(result.current.messages[1].id).toBe(assistantId);
    expect(result.current.messages[1].status).toBe('streaming');
    expect(result.current.messages[1].content).toBe('');
    await waitFor(() => expect(calls).toHaveLength(2));
  });

  it('연속 send 시 직전 요청을 abort 한다', async () => {
    const aborts: AbortSignal[] = [];
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, _cb, _t, signal) => {
      if (signal) aborts.push(signal);
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q1'));
    expect(aborts[0].aborted).toBe(false);
    act(() => result.current.send('q2'));
    expect(aborts[0].aborted).toBe(true);
  });
});
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/hooks/__tests__/useQnaChat.test.ts
```

기대: FAIL — `useQnaChat` 모듈 없음.

- [ ] **Step 4: `useQnaChat.ts` 구현**

```ts
// frontend/src/hooks/useQnaChat.ts
import { useCallback, useRef, useState } from 'react';
import { fetchQnaAnswer, type QnaCallbacks } from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';
import type { QnaMessage } from '@/types/qna';

export interface UseQnaChat {
  messages: QnaMessage[];
  isStreaming: boolean;
  send: (question: string) => void;
  retry: (assistantMessageId: string) => void;
  copy: (content: string) => Promise<void>;
}

const ERROR_FALLBACK = '답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.';

export function useQnaChat(policyId: number): UseQnaChat {
  const [messages, setMessages] = useState<QnaMessage[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const accessTokenRef = useRef<string | null>(null);
  accessTokenRef.current = useAuthStore((s) => s.accessToken);

  const isStreaming = messages.some((m) => m.status === 'streaming');

  const streamInto = useCallback(
    (assistantId: string, question: string) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

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

      void fetchQnaAnswer(
        policyId,
        question,
        callbacks,
        accessTokenRef.current,
        controller.signal,
      );
    },
    [policyId],
  );

  const send = useCallback(
    (question: string) => {
      const userId = `user-${Date.now()}`;
      const assistantId = `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

      setMessages((prev) => [
        ...prev,
        { id: userId, role: 'user', content: question, status: 'done' },
        { id: assistantId, role: 'assistant', content: '', status: 'streaming', questionRef: userId },
      ]);

      streamInto(assistantId, question);
    },
    [streamInto],
  );

  const retry = useCallback(
    (assistantMessageId: string) => {
      let questionContent: string | null = null;
      setMessages((prev) => {
        const target = prev.find((m) => m.id === assistantMessageId);
        if (!target || !target.questionRef) return prev;
        const userMsg = prev.find((m) => m.id === target.questionRef);
        if (!userMsg) return prev;
        questionContent = userMsg.content;
        return prev.map((m) =>
          m.id === assistantMessageId
            ? { ...m, content: '', sources: undefined, status: 'streaming' }
            : m,
        );
      });
      if (questionContent !== null) {
        streamInto(assistantMessageId, questionContent);
      }
    },
    [streamInto],
  );

  const copy = useCallback(async (content: string) => {
    await navigator.clipboard.writeText(content);
  }, []);

  return { messages, isStreaming, send, retry, copy };
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/hooks/__tests__/useQnaChat.test.ts
```

기대: 6개 테스트 모두 PASS.

- [ ] **Step 6: 빌드 검증**

```bash
cd frontend && npm run build
```

기대: 타입체크 + 빌드 성공.

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/hooks/useQnaChat.ts frontend/src/hooks/__tests__/useQnaChat.test.ts
git commit -m "$(cat <<'EOF'
feat(qna): useQnaChat 훅 추가 — 메시지/SSE/abort/retry 캡슐화

- send/retry/copy 액션, isStreaming 파생 상태
- 직전 요청 자동 abort (연속 send 또는 retry 시)
- retry 는 새 메시지 추가 없이 같은 assistant id 의 메시지를 제자리 reset
- 6개 단위 테스트 추가

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `QnaTypingIndicator` 컴포넌트 + 테스트

**목적:** 3-dot bouncing 인디케이터. 가장 단순한 컴포넌트부터 시작.

**Files:**
- Create: `frontend/src/components/qna/QnaTypingIndicator.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaTypingIndicator.test.tsx`

- [ ] **Step 1: 디렉토리 생성**

```bash
mkdir -p frontend/src/components/qna/__tests__
```

- [ ] **Step 2: 실패 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaTypingIndicator.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { QnaTypingIndicator } from '../QnaTypingIndicator';

describe('QnaTypingIndicator', () => {
  it('3 dot 으로 status 라벨을 가진다', () => {
    const { container } = render(<QnaTypingIndicator />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', '답변 준비 중');
    expect(container.querySelectorAll('span[aria-hidden="true"]')).toHaveLength(3);
  });
});
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaTypingIndicator.test.tsx
```

기대: FAIL — 모듈 없음.

- [ ] **Step 4: 구현**

```tsx
// frontend/src/components/qna/QnaTypingIndicator.tsx
export function QnaTypingIndicator() {
  return (
    <div
      role="status"
      aria-label="답변 준비 중"
      className="inline-flex items-center gap-1.5 rounded-2xl rounded-bl-md bg-[--color-chat-bubble] px-[18px] py-[14px]"
    >
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-[--color-chat-soft] animate-qna-typing-bounce" />
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-[--color-chat-soft] animate-qna-typing-bounce [animation-delay:0.15s]" />
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-[--color-chat-soft] animate-qna-typing-bounce [animation-delay:0.3s]" />
    </div>
  );
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaTypingIndicator.test.tsx
```

기대: PASS.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/components/qna/QnaTypingIndicator.tsx frontend/src/components/qna/__tests__/QnaTypingIndicator.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaTypingIndicator 컴포넌트 추가

3-dot bouncing 인디케이터 (RAG 조회 중 표시). role=status + aria-label.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: `QnaMessageBubble` 컴포넌트 + 테스트

**목적:** 단일 말풍선. user/assistant 분기, 마크다운 렌더, 출처 박스, 복사 버튼, 재시도 버튼, 스트리밍 커서.

**Files:**
- Create: `frontend/src/components/qna/QnaMessageBubble.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaMessageBubble.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaMessageBubble.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaMessageBubble } from '../QnaMessageBubble';
import type { QnaMessage } from '@/types/qna';

describe('QnaMessageBubble', () => {
  const baseAssistant: QnaMessage = {
    id: 'a1',
    role: 'assistant',
    content: '**만 19세** 이상 청년이면 가능합니다.',
    status: 'done',
    questionRef: 'u1',
  };
  const baseUser: QnaMessage = {
    id: 'u1',
    role: 'user',
    content: '신청 자격은?',
    status: 'done',
  };

  it('user 메시지는 본문을 평문으로 렌더한다', () => {
    render(<QnaMessageBubble message={baseUser} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.getByText('신청 자격은?')).toBeInTheDocument();
  });

  it('assistant 메시지는 마크다운 강조를 렌더한다', () => {
    render(<QnaMessageBubble message={baseAssistant} onCopy={vi.fn()} onRetry={vi.fn()} />);
    const strong = screen.getByText('만 19세');
    expect(strong.tagName).toBe('STRONG');
  });

  it('출처가 있으면 출처 박스 렌더', () => {
    render(
      <QnaMessageBubble
        message={{ ...baseAssistant, sources: ['청년정책 시행계획 p.12-13'] }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByText('출처')).toBeInTheDocument();
    expect(screen.getByText('청년정책 시행계획 p.12-13')).toBeInTheDocument();
  });

  it('복사 버튼 클릭 시 onCopy 호출', () => {
    const onCopy = vi.fn().mockResolvedValue(undefined);
    render(<QnaMessageBubble message={baseAssistant} onCopy={onCopy} onRetry={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '답변 복사' }));
    expect(onCopy).toHaveBeenCalledWith(baseAssistant.content);
  });

  it('error status 면 재시도 버튼 노출', () => {
    render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'error', content: '오류' }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: '답변 재생성' })).toBeInTheDocument();
  });

  it('재시도 버튼 클릭 시 onRetry 호출', () => {
    const onRetry = vi.fn();
    render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'error' }}
        onCopy={vi.fn()}
        onRetry={onRetry}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '답변 재생성' }));
    expect(onRetry).toHaveBeenCalledWith('a1');
  });

  it('streaming 상태이면 깜빡이는 커서를 표시한다', () => {
    const { container } = render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'streaming', content: '안녕' }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(container.querySelector('[data-qna-cursor]')).not.toBeNull();
  });

  it('user 메시지에는 복사/재시도 버튼이 없다', () => {
    render(<QnaMessageBubble message={baseUser} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.queryByRole('button', { name: '답변 복사' })).toBeNull();
    expect(screen.queryByRole('button', { name: '답변 재생성' })).toBeNull();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaMessageBubble.test.tsx
```

기대: FAIL — 모듈 없음.

- [ ] **Step 3: 구현**

```tsx
// frontend/src/components/qna/QnaMessageBubble.tsx
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Copy, Check, RotateCcw } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { QnaMessage } from '@/types/qna';

interface Props {
  message: QnaMessage;
  onCopy: (content: string) => Promise<void>;
  onRetry: (assistantMessageId: string) => void;
}

export function QnaMessageBubble({ message, onCopy, onRetry }: Props) {
  const isUser = message.role === 'user';
  const isError = message.status === 'error';
  const isStreaming = message.status === 'streaming';
  const [copied, setCopied] = useState(false);
  const [announceCopy, setAnnounceCopy] = useState(false);

  const handleCopy = async () => {
    try {
      await onCopy(message.content);
      setCopied(true);
      setAnnounceCopy(true);
      window.setTimeout(() => setCopied(false), 1500);
      window.setTimeout(() => setAnnounceCopy(false), 1500);
    } catch {
      // 클립보드 실패 시 silent (드물게 권한 거부)
    }
  };

  if (isUser) {
    return (
      <div className="flex justify-end animate-qna-msg-in">
        <div className="max-w-[88%] md:max-w-[80%] rounded-2xl rounded-br-md bg-[--color-chat-surface] px-4 py-[11px] text-[15px] leading-6 text-white shadow-sm">
          <p className="whitespace-pre-wrap">{message.content}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="group flex justify-start animate-qna-msg-in">
      <div
        className={cn(
          'max-w-[88%] md:max-w-[80%] rounded-2xl rounded-bl-md px-[18px] py-[14px] text-[15px] leading-7 shadow-sm',
          isError
            ? 'border-l-[3px] border-error-500 bg-red-50 text-red-900'
            : 'bg-[--color-chat-bubble] text-[--color-chat-bubble-text]',
        )}
      >
        {isError ? (
          <p className="m-0">⚠️ {message.content}</p>
        ) : (
          <div className="qna-md">
            <ReactMarkdown
              components={{
                p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                strong: ({ children }) => (
                  <strong className="font-bold text-[--color-chat-surface]">{children}</strong>
                ),
                em: ({ children }) => (
                  <em className="italic text-[--color-chat-surface]">{children}</em>
                ),
                ul: ({ children }) => (
                  <ul className="my-1.5 list-disc pl-[1.4em] marker:text-[--color-chat-soft]">
                    {children}
                  </ul>
                ),
                ol: ({ children }) => (
                  <ol className="my-1.5 list-decimal pl-[1.4em] marker:text-[--color-chat-soft]">
                    {children}
                  </ol>
                ),
                li: ({ children }) => <li className="my-0.5">{children}</li>,
                a: ({ href, children }) => (
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium text-[--color-chat-surface] underline underline-offset-2 hover:text-[--color-chat-surface-deep]"
                  >
                    {children}
                  </a>
                ),
                code: ({ children }) => (
                  <code className="rounded bg-[--color-chat-source-bg] px-[7px] py-[2px] font-mono text-[0.88em] text-[--color-chat-surface]">
                    {children}
                  </code>
                ),
                blockquote: ({ children }) => (
                  <blockquote className="my-2 border-l-[3px] border-[--color-chat-soft] pl-3 italic text-slate-600">
                    {children}
                  </blockquote>
                ),
                hr: () => <hr className="my-3 border-t border-slate-200" />,
                table: () => null,
                pre: ({ children }) => <>{children}</>,
                img: () => null,
              }}
            >
              {message.content}
            </ReactMarkdown>
            {isStreaming && message.content !== '' && (
              <span
                data-qna-cursor
                aria-hidden="true"
                className="ml-0.5 inline-block h-[1em] w-[2px] -mb-0.5 bg-[--color-chat-surface] animate-qna-cursor-blink"
              />
            )}
          </div>
        )}

        {message.sources && message.sources.length > 0 && (
          <div className="mt-3 rounded-[10px] bg-[--color-chat-source-bg] px-[14px] py-3 text-[13px] text-[--color-chat-bubble-text]">
            <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-[--color-chat-surface]">
              출처
            </p>
            <ul className="m-0 list-disc pl-[1.2em] marker:text-[--color-chat-soft]">
              {message.sources.map((src, i) => (
                <li key={i} className="my-0.5">
                  {src}
                </li>
              ))}
            </ul>
          </div>
        )}

        {!isError && message.status === 'done' && (
          <div className="mt-2 flex justify-end gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
            <button
              type="button"
              aria-label="답변 복사"
              onClick={handleCopy}
              className={cn(
                'flex h-9 w-9 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition hover:bg-slate-100 hover:text-[--color-chat-bubble-text]',
                copied && 'bg-[--color-chat-source-bg] text-[--color-chat-surface]',
              )}
            >
              {copied ? (
                <Check className="h-4 w-4 animate-qna-check-pop" />
              ) : (
                <Copy className="h-4 w-4" />
              )}
            </button>
          </div>
        )}

        {isError && (
          <div className="mt-2">
            <button
              type="button"
              aria-label="답변 재생성"
              onClick={() => onRetry(message.id)}
              className="inline-flex items-center gap-1.5 rounded-md bg-[--color-chat-surface] px-3 py-1.5 text-[13px] font-medium text-white transition hover:bg-[--color-chat-surface-deep]"
            >
              <RotateCcw className="h-3.5 w-3.5" /> 재시도
            </button>
          </div>
        )}

        {announceCopy && (
          <span className="sr-only" role="status" aria-live="polite">
            복사되었습니다
          </span>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaMessageBubble.test.tsx
```

기대: 8개 모두 PASS.

- [ ] **Step 5: 빌드 검증**

```bash
cd frontend && npm run build
```

기대: 타입체크 + 빌드 성공.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/components/qna/QnaMessageBubble.tsx frontend/src/components/qna/__tests__/QnaMessageBubble.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaMessageBubble 컴포넌트 추가

- user/assistant/error 3-state 분기 렌더
- react-markdown 으로 강조/목록/링크/인라인 코드 렌더
- table/pre/img 차단 (정책 답변 부적합)
- 외부 링크 target=_blank + rel=noopener
- 출처 박스, 복사 버튼 (Copy → Check morph + sr-only announce), 재시도 버튼, 스트리밍 커서

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: `QnaSuggestionChips` 컴포넌트 + 테스트

**목적:** 빈 상태에서 노출되는 정적 추천 질문 칩 4개.

**Files:**
- Create: `frontend/src/components/qna/QnaSuggestionChips.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaSuggestionChips } from '../QnaSuggestionChips';

describe('QnaSuggestionChips', () => {
  it('4 개 칩을 button 으로 렌더한다', () => {
    render(<QnaSuggestionChips onPick={vi.fn()} />);
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(4);
  });

  it('칩 클릭 시 onPick 에 텍스트가 전달된다', () => {
    const onPick = vi.fn();
    render(<QnaSuggestionChips onPick={onPick} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));
    expect(onPick).toHaveBeenCalledWith('신청 자격이 어떻게 되나요?');
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaSuggestionChips.test.tsx
```

기대: FAIL — 모듈 없음.

- [ ] **Step 3: 구현**

```tsx
// frontend/src/components/qna/QnaSuggestionChips.tsx
const SUGGESTIONS = [
  '신청 자격이 어떻게 되나요?',
  '어떤 서류가 필요한가요?',
  '신청은 언제까지인가요?',
  '지원 금액은 얼마인가요?',
] as const;

interface Props {
  onPick: (question: string) => void;
}

export function QnaSuggestionChips({ onPick }: Props) {
  return (
    <div className="flex flex-wrap justify-center gap-2">
      {SUGGESTIONS.map((q) => (
        <button
          key={q}
          type="button"
          onClick={() => onPick(q)}
          className="rounded-full border border-[--color-chat-accent]/30 bg-[--color-chat-accent]/10 px-[14px] py-2 text-[13px] text-[--color-chat-accent] transition hover:-translate-y-px hover:border-[--color-chat-accent]/50 hover:bg-[--color-chat-accent]/25 focus-visible:outline-2 focus-visible:outline-[--color-chat-accent] focus-visible:outline-offset-2"
        >
          {q}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaSuggestionChips.test.tsx
```

기대: 2개 PASS.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/qna/QnaSuggestionChips.tsx frontend/src/components/qna/__tests__/QnaSuggestionChips.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaSuggestionChips 컴포넌트 추가

빈 상태에서 노출되는 정적 추천 질문 4개 — 신청자격/서류/마감일/지원금액.
button 요소로 키보드 접근 보장.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: `QnaComposer` 컴포넌트 + 테스트

**목적:** 입력창 + 전송 버튼. IME 한글 조합 중 Enter 무시. 미인증 사용자 처리는 컨테이너에서 readOnly + onFocus 처리.

**Files:**
- Create: `frontend/src/components/qna/QnaComposer.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaComposer.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaComposer.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaComposer } from '../QnaComposer';

describe('QnaComposer', () => {
  it('빈 입력은 전송 버튼이 disabled', () => {
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: '질문 전송' })).toBeDisabled();
  });

  it('submit 시 trim 된 텍스트로 onSubmit 호출 + 입력값 비움', () => {
    const onSubmit = vi.fn();
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={onSubmit} />);
    const input = screen.getByPlaceholderText('질문') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '  신청 자격은?  ' } });
    fireEvent.submit(input.closest('form')!);
    expect(onSubmit).toHaveBeenCalledWith('신청 자격은?');
    expect(input.value).toBe('');
  });

  it('IME composition 중 Enter 는 무시한다', () => {
    const onSubmit = vi.fn();
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={onSubmit} />);
    const input = screen.getByPlaceholderText('질문');
    fireEvent.change(input, { target: { value: '안녕' } });
    fireEvent.keyDown(input, { key: 'Enter', nativeEvent: { isComposing: true } });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('readOnly + onFocus 호출 (disabled 모드)', () => {
    const onFocus = vi.fn();
    render(
      <QnaComposer
        disabled={true}
        placeholder="로그인 후"
        onSubmit={vi.fn()}
        readOnly
        onFocus={onFocus}
      />,
    );
    const input = screen.getByPlaceholderText('로그인 후');
    fireEvent.focus(input);
    expect(onFocus).toHaveBeenCalled();
    expect(input).toHaveAttribute('readonly');
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaComposer.test.tsx
```

기대: FAIL.

- [ ] **Step 3: 구현**

```tsx
// frontend/src/components/qna/QnaComposer.tsx
import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import { cn } from '@/lib/cn';

interface Props {
  disabled: boolean;
  placeholder: string;
  onSubmit: (question: string) => void;
  readOnly?: boolean;
  onFocus?: () => void;
}

export function QnaComposer({ disabled, placeholder, onSubmit, readOnly, onFocus }: Props) {
  const [value, setValue] = useState('');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSubmit(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && (e.nativeEvent as KeyboardEvent['nativeEvent'] & { isComposing?: boolean }).isComposing) {
      e.preventDefault();
    }
  };

  return (
    <form onSubmit={handleSubmit} className="relative">
      <input
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onFocus={onFocus}
        readOnly={readOnly}
        placeholder={placeholder}
        className={cn(
          'h-11 w-full rounded-[14px] border border-[--color-chat-accent]/20 bg-[--color-chat-accent]/10 pl-4 pr-12 text-[15px] text-white outline-none transition-colors placeholder:text-[--color-chat-accent]/55 focus:bg-[--color-chat-accent]/18 focus-visible:outline-2 focus-visible:outline-[--color-chat-accent]',
          readOnly && 'cursor-pointer',
        )}
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        aria-label="질문 전송"
        className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-[--color-chat-accent] transition hover:bg-[--color-chat-accent]/15 disabled:opacity-40"
      >
        <Send className="h-4 w-4" />
      </button>
    </form>
  );
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaComposer.test.tsx
```

기대: 4개 PASS.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/qna/QnaComposer.tsx frontend/src/components/qna/__tests__/QnaComposer.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaComposer 컴포넌트 추가

입력창 + 전송 버튼. IME composition 중 Enter 무시 (한글 조합 종료 시 의도치 않은 전송 방지). readOnly + onFocus 로 미인증 사용자 진입 시 컨테이너의 onLoginPrompt 와 연동.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: `QnaMessageList` 컴포넌트 + 테스트

**목적:** 메시지 스크롤 컨테이너. bottom ±80px 정책으로 자동 스크롤, jump-to-bottom 버튼 노출. typing indicator 는 streaming + 빈 content 인 마지막 assistant 메시지 자리에 렌더.

**Files:**
- Create: `frontend/src/components/qna/QnaMessageList.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaMessageList.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaMessageList.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeAll } from 'vitest';
import { QnaMessageList } from '../QnaMessageList';
import type { QnaMessage } from '@/types/qna';

beforeAll(() => {
  Element.prototype.scrollTo = vi.fn();
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
    configurable: true,
    get: function () {
      return Number((this as HTMLElement).getAttribute('data-scroll-height') ?? '500');
    },
  });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    get: function () {
      return Number((this as HTMLElement).getAttribute('data-client-height') ?? '300');
    },
  });
});

describe('QnaMessageList', () => {
  const messages: QnaMessage[] = [
    { id: 'u1', role: 'user', content: 'q', status: 'done' },
    { id: 'a1', role: 'assistant', content: '답변', status: 'done', questionRef: 'u1' },
  ];

  it('메시지를 순서대로 렌더한다', () => {
    render(<QnaMessageList messages={messages} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.getByText('q')).toBeInTheDocument();
    expect(screen.getByText('답변')).toBeInTheDocument();
  });

  it('streaming 이고 content 빈 마지막 assistant 자리에 typing indicator 표시', () => {
    const streaming: QnaMessage[] = [
      { id: 'u1', role: 'user', content: 'q', status: 'done' },
      { id: 'a1', role: 'assistant', content: '', status: 'streaming', questionRef: 'u1' },
    ];
    render(<QnaMessageList messages={streaming} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.getByRole('status', { name: '답변 준비 중' })).toBeInTheDocument();
  });

  it('스크롤이 위에 있고 메시지 push 되면 jump 버튼 노출', () => {
    const { container, rerender } = render(
      <QnaMessageList messages={messages} onCopy={vi.fn()} onRetry={vi.fn()} />,
    );
    const scroller = container.querySelector('[data-qna-scroller]') as HTMLElement;
    scroller.setAttribute('data-scroll-height', '1000');
    scroller.setAttribute('data-client-height', '300');
    Object.defineProperty(scroller, 'scrollTop', { configurable: true, value: 100 });
    fireEvent.scroll(scroller);

    rerender(
      <QnaMessageList
        messages={[...messages, { id: 'u2', role: 'user', content: 'q2', status: 'done' }]}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: '가장 최근 메시지로 이동' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaMessageList.test.tsx
```

기대: FAIL — 모듈 없음.

- [ ] **Step 3: 구현**

```tsx
// frontend/src/components/qna/QnaMessageList.tsx
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { QnaMessageBubble } from './QnaMessageBubble';
import { QnaTypingIndicator } from './QnaTypingIndicator';
import type { QnaMessage } from '@/types/qna';

const NEAR_BOTTOM_PX = 80;

interface Props {
  messages: QnaMessage[];
  onCopy: (content: string) => Promise<void>;
  onRetry: (assistantMessageId: string) => void;
}

export function QnaMessageList({ messages, onCopy, onRetry }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [isNearBottom, setIsNearBottom] = useState(true);

  const isNearBottomNow = () => {
    const el = scrollRef.current;
    if (!el) return true;
    return el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
  };

  const scrollToBottom = (smooth = true) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
  };

  useLayoutEffect(() => {
    if (isNearBottom) scrollToBottom(messages.length <= 2 ? false : true);
    // intentionally only depend on length / last content len so chunk-by-chunk
    // updates also trigger smooth follow when user is near bottom
  }, [messages.length, messages.at(-1)?.content.length, isNearBottom]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onScroll = () => setIsNearBottom(isNearBottomNow());
    el.addEventListener('scroll', onScroll);
    return () => el.removeEventListener('scroll', onScroll);
  }, []);

  const last = messages.at(-1);
  const showTyping = last?.role === 'assistant' && last.status === 'streaming' && last.content === '';

  return (
    <div className="relative">
      <div
        ref={scrollRef}
        data-qna-scroller
        role="log"
        aria-live="polite"
        aria-atomic="false"
        className="min-h-[50vh] max-h-[70vh] md:max-h-[600px] space-y-3.5 overflow-y-auto pr-2 scrollbar-qna"
      >
        {messages.map((msg, idx) => {
          const isLast = idx === messages.length - 1;
          if (isLast && showTyping) {
            return <QnaTypingIndicator key={msg.id} />;
          }
          return (
            <QnaMessageBubble
              key={msg.id}
              message={msg}
              onCopy={onCopy}
              onRetry={onRetry}
            />
          );
        })}
      </div>
      {!isNearBottom && (
        <button
          type="button"
          aria-label="가장 최근 메시지로 이동"
          onClick={() => scrollToBottom(true)}
          className="absolute bottom-3 right-3 md:bottom-4 md:right-4 flex h-9 w-9 items-center justify-center rounded-full bg-white text-[--color-chat-surface] shadow-lg shadow-[--color-chat-surface-deep]/30 transition hover:bg-slate-50 animate-qna-jump-in"
        >
          <ChevronDown className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaMessageList.test.tsx
```

기대: 3개 PASS.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/qna/QnaMessageList.tsx frontend/src/components/qna/__tests__/QnaMessageList.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaMessageList 컴포넌트 추가

- 메시지 스크롤 컨테이너 + scrollbar-qna
- bottom ±80px 정책: 사용자가 그 안에 있을 때만 자동 따라가기
- 위로 스크롤 한 상태에선 jump-to-bottom 버튼 노출
- streaming + 빈 content 인 마지막 assistant 자리에 QnaTypingIndicator 렌더

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: `QnaChatSection` 컨테이너 + 통합 테스트

**목적:** 모든 하위 컴포넌트 + `useQnaChat` 훅 연결. 인증 분기, 빈 상태에 추천 칩, 섹션 chrome (제목 + 배경).

**Files:**
- Create: `frontend/src/components/qna/QnaChatSection.tsx`
- Test: `frontend/src/components/qna/__tests__/QnaChatSection.test.tsx`

- [ ] **Step 1: 통합 테스트 작성**

```tsx
// frontend/src/components/qna/__tests__/QnaChatSection.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QnaChatSection } from '../QnaChatSection';
import * as qnaApi from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';

vi.mock('@/apis/qna.api');

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: 'token', isAuthenticated: true });
});

describe('QnaChatSection (통합)', () => {
  it('빈 상태에서 추천 칩이 노출된다', () => {
    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
  });

  it('칩 클릭 → user 메시지 push → assistant streaming 인디케이터', async () => {
    let cb: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, c) => {
      cb = c;
    });

    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));

    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
    await waitFor(() => expect(cb).not.toBeNull());
    expect(screen.getByRole('status', { name: '답변 준비 중' })).toBeInTheDocument();
  });

  it('미인증 사용자가 칩 클릭 → onLoginPrompt 호출, 메시지 push 안 됨', () => {
    const onLoginPrompt = vi.fn();
    render(<QnaChatSection isAuthenticated={false} policyId={1} onLoginPrompt={onLoginPrompt} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));
    expect(onLoginPrompt).toHaveBeenCalledTimes(1);
    expect(qnaApi.fetchQnaAnswer).not.toHaveBeenCalled();
  });

  it('전체 플로우: chunks → done → 답변 + 출처 표시', async () => {
    let cb: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, c) => {
      cb = c;
    });

    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    fireEvent.click(screen.getByText('어떤 서류가 필요한가요?'));
    await waitFor(() => expect(cb).not.toBeNull());

    fireEvent.click(document.body); // no-op
    cb!.onChunk('주민등록등본');
    cb!.onChunk('이 필요합니다.');
    cb!.onSources(['청년정책 시행계획 p.20']);
    cb!.onDone();

    await waitFor(() => {
      expect(screen.getByText(/주민등록등본이 필요합니다\./)).toBeInTheDocument();
      expect(screen.getByText('청년정책 시행계획 p.20')).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaChatSection.test.tsx
```

기대: FAIL — 모듈 없음.

- [ ] **Step 3: 구현**

```tsx
// frontend/src/components/qna/QnaChatSection.tsx
import { Sparkles, MessageSquare } from 'lucide-react';
import { useQnaChat } from '@/hooks/useQnaChat';
import { QnaMessageList } from './QnaMessageList';
import { QnaSuggestionChips } from './QnaSuggestionChips';
import { QnaComposer } from './QnaComposer';

interface Props {
  isAuthenticated: boolean;
  policyId: number;
  onLoginPrompt: () => void;
}

export function QnaChatSection({ isAuthenticated, policyId, onLoginPrompt }: Props) {
  const { messages, isStreaming, send, retry, copy } = useQnaChat(policyId);

  const handlePick = (question: string) => {
    if (!isAuthenticated) {
      onLoginPrompt();
      return;
    }
    send(question);
  };

  const isEmpty = messages.length === 0;

  return (
    <section className="overflow-hidden rounded-2xl bg-gradient-to-br from-[--color-chat-surface-deep] to-[--color-chat-surface] p-4 md:p-6">
      <header className="mb-4 flex items-center gap-2">
        <span className="inline-flex items-center gap-1 rounded-full bg-[--color-chat-accent]/18 px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-[--color-chat-accent]">
          <Sparkles className="h-3.5 w-3.5" />
          Smart Q&amp;A
        </span>
      </header>

      <div className="mb-4">
        {isEmpty ? (
          <div className="flex min-h-[40vh] flex-col items-center justify-center px-4 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-[--color-chat-accent]/15">
              <MessageSquare className="h-6 w-6 text-[--color-chat-accent]" />
            </div>
            <p className="mb-1 text-[15px] font-medium text-[--color-chat-accent]">
              이 정책에 대해 궁금한 점을 질문해보세요
            </p>
            <p className="mb-4 text-[13px] text-[--color-chat-accent]/55">
              아래 추천 질문으로 빠르게 시작할 수 있어요
            </p>
            <QnaSuggestionChips onPick={handlePick} />
          </div>
        ) : (
          <QnaMessageList messages={messages} onCopy={copy} onRetry={retry} />
        )}
      </div>

      <QnaComposer
        disabled={!isAuthenticated || isStreaming}
        readOnly={!isAuthenticated}
        onFocus={!isAuthenticated ? onLoginPrompt : undefined}
        placeholder={
          isAuthenticated
            ? isStreaming
              ? '답변을 받는 중입니다...'
              : '질문을 입력하세요...'
            : '로그인 후 질문할 수 있어요'
        }
        onSubmit={send}
      />
    </section>
  );
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd frontend && npx vitest run src/components/qna/__tests__/QnaChatSection.test.tsx
```

기대: 4개 PASS.

- [ ] **Step 5: 빌드 검증**

```bash
cd frontend && npm run build
```

기대: 타입체크 + 빌드 성공.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/components/qna/QnaChatSection.tsx frontend/src/components/qna/__tests__/QnaChatSection.test.tsx
git commit -m "$(cat <<'EOF'
feat(qna): QnaChatSection 컨테이너 + 통합 테스트

- useQnaChat 훅 연결, 인증 분기, 빈 상태 ↔ 메시지 리스트 전환
- 빈 상태: 안내 + 추천 질문 칩
- composer disabled 조건: 미인증 OR 스트리밍 중
- 미인증 사용자 칩/composer 진입 시 onLoginPrompt 호출, send 호출 안 함
- 통합 테스트 4개 (빈 상태, 칩 클릭, 미인증 분기, full flow)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: `PolicyDetailPage.tsx` 인라인 → import 교체

**목적:** 인라인 `QnaChatSection` 정의(약 170줄) 제거 + 신규 컴포넌트 import. 사용처(약 863줄) 그대로 유지. 안 쓰게 된 import 정리.

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 변경 전 빌드 확인 (안전판)**

```bash
cd frontend && npm run build
```

기대: 통과 (Task 11 후 상태).

- [ ] **Step 2: 인라인 `QnaChatSection` 정의 제거**

`frontend/src/pages/PolicyDetailPage.tsx` 의 다음 블록을 통째로 삭제 (현재 약 444-617줄, 즉 `// ----- Q&A Chat Section -----` 주석부터 인라인 `function QnaChatSection(...)` 의 마지막 닫는 `}` 까지):

```
// ---------------------------------------------------------------------------
// Q&A Chat Section
// ---------------------------------------------------------------------------

function QnaChatSection({
  ...
}) {
  ...
}
```

- [ ] **Step 3: import 추가 + 미사용 import 제거**

`frontend/src/pages/PolicyDetailPage.tsx` 의 import 영역에 추가:

```tsx
import { QnaChatSection } from '@/components/qna/QnaChatSection';
```

다음 import 들이 PolicyDetailPage 내 다른 곳에서 사용되지 않는다면 제거 (단, 다른 곳에서 쓰면 유지):
- `import { fetchQnaAnswer } from '@/apis/qna.api';` — 인라인 QnaChatSection 만 쓰던 함수, 제거
- `import { Send } from 'lucide-react';` — 인라인 composer 의 전송 아이콘, 제거 후보 (다른 곳 사용 여부 확인)
- `Sparkles` from `lucide-react` — 인라인 헤더에서 썼지만 다른 곳에서도 쓸 수 있음, grep 으로 확인 후 제거
- `QnaMessage` from `@/types/qna` — 인라인 정의에서만 썼다면 제거

확인 명령:

```bash
cd frontend && rg -n "fetchQnaAnswer|QnaMessage|<Send |<Sparkles " src/pages/PolicyDetailPage.tsx
```

검색 결과를 보고 인라인 정의 제거 후 더 이상 참조 안 되는 심볼을 import 에서 삭제. (`useState`, `useRef`, `useEffect`, `useCallback`, `useAuthStore` 는 다른 컴포넌트들도 쓰므로 보통 유지.)

- [ ] **Step 4: 빌드 검증 — 미사용 import 가 ESLint/tsc 에러 없는지**

```bash
cd frontend && npm run build && npm run lint
```

기대: 빌드 통과, 린트 에러 없음. 만약 `'X' is defined but never used` 가 뜨면 해당 import 추가 제거.

- [ ] **Step 5: 모든 테스트 실행 (회귀 검증)**

```bash
cd frontend && npm run test
```

기대: 신규 + 기존 테스트 모두 PASS.

- [ ] **Step 6: 개발 서버에서 수동 검증**

```bash
cd frontend && npm run dev
```

브라우저에서 `http://localhost:5173/policies/{id}` 로 정책 상세 페이지 진입. 다음을 확인:
- 채팅 영역이 새 그라디언트 배경 + 50-70vh 높이로 보임
- 빈 상태에 추천 칩 4개 노출
- 칩 클릭 시 user 메시지 + typing dots → 스트리밍 텍스트 + 커서 → done
- 답변에 마크다운 강조/목록이 렌더 됨
- 출처 박스가 sky 톤 배경으로 표시
- 답변 위에 마우스 hover 시 복사 버튼 노출 (데스크탑), 클릭 시 ✓ morph
- 위로 스크롤 한 상태에서 새 chunk 가 와도 자동 스크롤 안 됨, 우하단 ↓ 버튼 노출
- ↓ 클릭 시 smooth scroll 으로 마지막 메시지로 이동
- 한글 입력 중 Enter 가 전송하지 않음 (조합 종료용으로만 동작)
- 미로그인 상태로 진입 시 칩/입력창 진입 시 로그인 모달 노출

수동 검증 OK 면 다음 단계.

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "$(cat <<'EOF'
refactor(qna): PolicyDetailPage 인라인 QnaChatSection 제거 → 모듈 import

components/qna/QnaChatSection 으로 이관. PolicyDetailPage.tsx 약 170줄 감소.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: 최종 회귀 검증 + 시각 다듬기

**목적:** 전체 테스트 통과, 빌드 성공, 시각 검증 항목 체크리스트 통과 확인. 발견된 잔여 이슈만 패치.

**Files:**
- 검증 only (필요 시 수정)

- [ ] **Step 1: 전체 테스트**

```bash
cd frontend && npm run test
```

기대: 모든 테스트 PASS. 신규 테스트 27개 (qna.api 3 + useQnaChat 6 + TypingIndicator 1 + Bubble 8 + Chips 2 + Composer 4 + List 3 + Section 4 = 31) + 기존 테스트.

- [ ] **Step 2: 린트**

```bash
cd frontend && npm run lint
```

기대: 0 errors / 0 warnings (혹은 warnings 만).

- [ ] **Step 3: 프로덕션 빌드**

```bash
cd frontend && npm run build
```

기대: tsc -b + vite build 성공.

- [ ] **Step 4: prefers-reduced-motion 검증**

브라우저 DevTools → Rendering → "Emulate CSS media feature prefers-reduced-motion" → "reduce" 활성화. 정책 상세 페이지에서:
- 메시지 등장이 translate 없이 opacity 만 변경
- typing dot 이 정적
- 스트리밍 커서가 깜빡이지 않고 solid
- 복사 ✓ morph 즉시 표시/복원
- jump-to-bottom 버튼 등장이 translate/scale 없이 opacity 만

OK 면 다음 단계. 안 되면 `index.css` 의 `@media (prefers-reduced-motion: reduce)` 블록 점검.

- [ ] **Step 5: 색상 대비 자동 검증 (선택)**

브라우저 DevTools → Lighthouse → Accessibility 만 실행. 또는 axe DevTools 확장. 결과:
- 챗봇 영역 내 contrast 위반 0건
- aria 속성 위반 0건

- [ ] **Step 6: 스펙 18 항 체크리스트 검증 — 인라인으로 확인**

다음을 한 번에 체크 (스펙 § 18):
- [ ] 채팅 영역 높이: `min-h-[50vh] max-h-[70vh]` (모바일) / `md:max-h-[600px]` (데스크탑)
- [ ] 본문 글씨: `text-[15px] leading-7`
- [ ] 스크롤바: 6px sky 톤
- [ ] SSE 1단계: 빈 content 시 3 dot bouncing
- [ ] SSE 2단계: 첫 chunk 후 깜빡 커서
- [ ] 마크다운: strong/em/ul/ol/li/a/code/blockquote/hr 렌더, table/pre/img 차단
- [ ] 외부 링크 target=_blank + rel=noopener
- [ ] 빈 상태 추천 4개 칩
- [ ] 복사 morph + sr-only announce
- [ ] 에러 시 재시도 버튼
- [ ] Jump-to-bottom 노출/숨김
- [ ] 새 send 가 진행 중 SSE abort
- [ ] reduced-motion 비활성/정적
- [ ] 색상 대비 AAA
- [ ] 키보드만으로 전체 인터랙션
- [ ] IME 한글 조합 중 Enter 무시
- [ ] PolicyDetailPage 약 170줄 이상 감소
- [ ] QnaMessage 가 types/qna.ts 로 이동, 구 위치 제거
- [ ] 신규/수정 파일 모두 테스트 통과
- [ ] `npm run build` 성공

위 항목 중 미충족 항목은 그 자리에서 작은 패치 + 커밋으로 보정.

- [ ] **Step 7: 최종 커밋 (선택, 잔여 패치가 있을 때만)**

```bash
git add -A
git commit -m "$(cat <<'EOF'
chore(qna): 챗봇 UI 개선 검수 라운드 — 잔여 패치

(있을 경우 발견된 항목 한 줄로 요약)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

잔여 패치가 없으면 커밋하지 않음.

---

## Self-Review

### 1. Spec coverage

| 스펙 섹션 | 구현 태스크 |
|---|---|
| § 3 컴포넌트 아키텍처 | T2(types) + T6-T11(컴포넌트들) + T12(통합) |
| § 4 데이터 모델 | T2 |
| § 5 useQnaChat 훅 | T5 |
| § 6 SSE API 변경 | T4 |
| § 7 디자인 토큰 | T1 |
| § 8 레이아웃·타이포 | T11(컨테이너) + T7,T9(말풍선/composer 사이즈) |
| § 9 마크다운 렌더 | T7 |
| § 10 애니메이션 | T1(키프레임) + T6,T7,T10(사용처) |
| § 11 추가 기능 (칩/복사/재시도/jump) | T7,T8,T10,T11 |
| § 12 a11y | T1(reduced-motion) + T6-T11(aria) + T9(IME) |
| § 13 에러 처리 | T4(silent abort) + T5(에러 분기) + T7(에러 카드) |
| § 14 테스트 전략 | T4-T11 모두 단위/컴포넌트 + T11 통합 |
| § 15 핫패스 가드 | T11 (미인증 분기) |
| § 16 우선순위 | 태스크 순서가 P0→P3 와 일치 |
| § 17 Out-of-scope | 계획에 포함 안 함 (Stop generation 등) |
| § 18 검수 체크리스트 | T13 |

### 2. Placeholder scan

- 검증: "TBD", "TODO", "implement later", "fill in details", "Add appropriate error handling" 패턴 없음
- 모든 코드 블록은 그대로 사용 가능한 완전한 코드
- 모든 명령은 실제 실행 가능한 셸 명령

### 3. Type / signature consistency

- `QnaMessage` 필드 (T2 정의) — `id, role, content, sources?, status, questionRef?` — T5 훅에서 일관 사용, T7 bubble 에서 일관 사용
- `QnaCallbacks` 타입 (T4 정의) — `{onChunk, onSources, onDone, onError}` — T5 훅에서 일관 import
- `fetchQnaAnswer(policyId, question, callbacks, accessToken, signal?)` — T4 정의, T5 사용 일관
- `UseQnaChat` 인터페이스 — `messages, isStreaming, send, retry, copy` — T5 정의, T11 사용 일관
- 컴포넌트 props 시그니처 — Bubble/Chips/Composer/List/Section 모두 T11 의 호출부와 일치

### 4. 자체 발견 보정

없음. 위 검토 통과.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-03-qna-chat-ui-improvement.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
