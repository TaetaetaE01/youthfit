# Q&A 채팅 UI 개선 디자인 스펙

- **Date**: 2026-05-03
- **Status**: Draft (사용자 리뷰 대기)
- **Scope**: Frontend only — `frontend/src/` 하위
- **Out-of-scope**: 백엔드 API 변경, 시스템 전역 브랜드 컬러 마이그레이션

## 1. 배경 및 목표

현재 정책 상세 페이지(`pages/PolicyDetailPage.tsx`)의 Q&A 영역은 "최소 기능 동작" 수준에 머물러 있다.

- 채팅 영역이 `max-h-80` (320px) 로 답변 1-2개만 와도 답답함
- 본문 글씨 `text-sm` (14px / line-height 1.5) — 한국어 장문 답변에 호흡 부족
- 스크롤바: 브라우저 기본 (16px, 다크 컨테이너 위에서 둔탁)
- SSE 인디케이터: 작은 흰색 막대 1개 펄스 — "준비 중" 과 "스트리밍 중" 구분 안 됨
- 마크다운 미지원: LLM 이 출력한 `**강조**`, `- 목록` 마커가 평문으로 보임
- `QnaChatSection` 정의가 `PolicyDetailPage.tsx` 내부에 인라인 (≈170줄), 단일 파일이 939줄로 비대
- `QnaMessage` 타입이 `types/policy.ts` 에 잘못 위치
- 복사·재시도·jump-to-bottom·추천 질문 같은 "챗봇답다" 고 느껴지는 마이크로 UX 부재

**목표**: 정책 가이드 도메인에 어울리는 차분한 신뢰 톤을 유지하면서, 종합적인 챗봇 UX 리프레시를 수행한다. ChatGPT 의 차분한 가독성 + Linear 의 마이크로 모션을 절충한다.

## 2. 요약

| 영역 | Before | After |
|---|---|---|
| 스코프 | 인라인 컴포넌트, 단일 파일 | `components/qna/` 도메인 추출 + `useQnaChat` 훅 |
| 영역 크기 | `max-h-80` 고정 (320px) | `min-h-[50vh] max-h-[70vh]` 반응형 |
| 본문 글씨 | text-sm 14px / leading 1.5 | text-[15px] / leading-7 (1.75) |
| 스크롤바 | 기본 16px | 커스텀 6px, sky 톤 반투명 |
| 컨테이너 배경 | `bg-brand-800/80` (현 indigo 톤) | `linear-gradient(135deg, #0F2854, #1C4D8D)` (새 navy 팔레트) |
| User 말풍선 | `bg-indigo-500/40` 반투명 | `#1C4D8D` 솔리드 + 그림자 |
| Assistant 말풍선 | `bg-white/20` (반투명) | `#FFFFFF` (불투명 카드) + 본문 `#0F2854` |
| 콘텐츠 렌더 | `whitespace-pre-wrap` | react-markdown |
| SSE 인디케이터 | 흰색 막대 1개 펄스 | 2단계: typing dots → 스트리밍 커서 |
| 빈 상태 | 단일 안내 문구 | 안내 + 추천 질문 칩 4개 |
| 메시지 액션 | 없음 | 복사 버튼, 에러 시 재시도 버튼 |
| 스크롤 동작 | 항상 자동 스크롤 | bottom ±80px 정책 + jump-to-bottom 버튼 |

## 3. 컴포넌트 아키텍처

### 3.1 신규 파일

```
frontend/src/
├── types/qna.ts                          # QnaMessage 등 도메인 타입
├── hooks/useQnaChat.ts                   # 메시지 상태/SSE/재시도 관리
└── components/qna/
    ├── QnaChatSection.tsx                # 컨테이너 (hook 연결, 인증 분기, 섹션 chrome)
    ├── QnaMessageList.tsx                # 스크롤 영역 + jump-to-bottom 버튼
    ├── QnaMessageBubble.tsx              # 단일 말풍선 + markdown + 액션 + 출처 + 커서
    ├── QnaTypingIndicator.tsx            # 3-dot bouncing
    ├── QnaComposer.tsx                   # 입력창 + 전송 + IME 처리
    ├── QnaSuggestionChips.tsx            # 빈 상태 추천 질문 칩
    └── __tests__/
        ├── QnaMessageBubble.test.tsx
        ├── QnaSuggestionChips.test.tsx
        ├── QnaMessageList.test.tsx
        ├── QnaComposer.test.tsx
        └── useQnaChat.test.ts
```

### 3.2 수정 파일

| 파일 | 변경 |
|---|---|
| `pages/PolicyDetailPage.tsx` | 인라인 `QnaChatSection` 정의 제거 (≈170줄 감소), `import { QnaChatSection } from '@/components/qna/QnaChatSection'` 로 교체 |
| `apis/qna.api.ts` | 시그니처 변경: 콜백을 객체 인자로 묶고 `AbortSignal?` 추가 |
| `types/policy.ts` | `QnaMessage` 정의 제거 (qna.ts 로 이동) |
| `src/index.css` | 챗봇 전용 토큰 7개, 신규 keyframe 6 종, `@utility` 6 종, scrollbar 유틸 추가, prefers-reduced-motion 처리 |
| `frontend/package.json` | `react-markdown` 의존성 추가 |

### 3.3 컴포넌트 책임

| 컴포넌트 | 입력 props | 책임 | 외부 의존 |
|---|---|---|---|
| `QnaChatSection` | `policyId, isAuthenticated, onLoginPrompt` | 인증 분기, hook 연결, 섹션 chrome (제목/배경) | `useQnaChat` |
| `QnaMessageList` | `messages, isStreaming, children?` | 스크롤 컨테이너, 자동 스크롤 정책 (bottom ±80px), jump-to-bottom 버튼 노출 | DOM scroll |
| `QnaMessageBubble` | `message, onCopy, onRetry` | 단일 메시지 렌더 (user/assistant 분기), markdown 렌더, 출처, 액션, 스트리밍 커서 | `react-markdown` |
| `QnaTypingIndicator` | (없음) | 3-dot bouncing 인디케이터 (assistant 메시지 자리 placeholder) | 없음 |
| `QnaComposer` | `disabled, placeholder, onSubmit` | 입력, 전송 버튼, IME 처리, trim/empty 가드 | 없음 |
| `QnaSuggestionChips` | `onPick` | 정적 추천 질문 4개 칩 렌더, 클릭 시 onPick 호출 | 없음 |

### 3.4 도메인 컨벤션 정합성

- `frontend/CLAUDE.md` 의 `components/{domain}/` 그룹 규칙 준수 (기존: `policy/`, `auth/`, `personal-info/`)
- 도메인 타입은 `types/{domain}.ts` 규칙 → `qna.ts` 신설
- 훅은 `hooks/` 직속 (단일 컴포넌트 전용 훅도 컨벤션상 hooks/ 에 둠)

## 4. 데이터 모델

### 4.1 `QnaMessage` (확장)

```ts
// types/qna.ts
export type QnaRole = 'user' | 'assistant';
export type QnaStatus = 'streaming' | 'done' | 'error';

export interface QnaMessage {
  id: string;
  role: QnaRole;
  content: string;
  sources?: string[];
  /** streaming: chunk 누적 중 / done: 완료 / error: 에러 */
  status: QnaStatus;
  /** assistant 메시지가 어느 user 질문에 속하는지 — retry 시 question 복원 용도 */
  questionRef?: string;
}
```

**기존 `loading: boolean` 폐기 이유**: "에러로 끝남" 상태를 표현 못해 재시도 버튼 노출 결정이 어려움. 3-state 로 의미 명확화.

## 5. `useQnaChat` 훅 설계

### 5.1 시그니처

```ts
interface UseQnaChat {
  messages: QnaMessage[];
  isStreaming: boolean;
  send: (question: string) => void;
  retry: (assistantMessageId: string) => void;
  copy: (content: string) => Promise<void>;
}

export function useQnaChat(policyId: number): UseQnaChat;
```

### 5.2 내부 구조

- 상태: `useState<QnaMessage[]>` 와 `useRef<AbortController | null>`
- 보조: `useAuthStore((s) => s.accessToken)` 으로 토큰 조회
- 내부 함수 `streamInto(assistantId, question)` — `send` / `retry` 가 공유. 호출 시 직전 `abortRef.current?.abort()` 후 새 `AbortController` 생성 (재시도 시에도 진행 중인 요청 자동 취소)

### 5.3 `send(question)` 흐름

1. `userMsg` (status: `'done'`) push
2. `assistantMsg` (status: `'streaming'`, content: '', questionRef: userMsg.id) push
3. `streamInto(assistantId, question)` 실행
   - 내부에서 직전 abort 후 새 AbortController 생성, signal 을 `fetchQnaAnswer` 에 전달
   - onChunk → 해당 assistantId 의 content 누적
   - onSources → sources 갱신
   - onDone → status: `'done'`
   - onError → status: `'error'`, content: 사용자용 에러 문구

### 5.4 `retry(assistantMessageId)` 흐름

1. 대상 assistant 메시지 검색
2. `questionRef` 로 user 메시지 찾기 → content 추출 (= 원래 질문)
3. assistant 메시지를 **제자리에서** content: '', sources: undefined, status: `'streaming'` 으로 reset (메시지 순서 유지)
4. `streamInto(assistantId, question)` 재실행

→ 사용자 입장: "같은 칸이 다시 채워지는" 경험. 새 메시지 추가하지 않음.

### 5.5 `copy(content)` 흐름

- `navigator.clipboard.writeText(content)` 호출, Promise 반환
- 성공/실패 처리는 호출 측 (`QnaMessageBubble`) 책임 — 아이콘 morph + visually-hidden aria-live announce

## 6. SSE API 변경

### 6.1 새 시그니처

```ts
// apis/qna.api.ts
export async function fetchQnaAnswer(
  policyId: number,
  question: string,
  callbacks: {
    onChunk: (text: string) => void;
    onSources: (sources: string[]) => void;
    onDone: () => void;
    onError: (error: Error) => void;
  },
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<void>;
```

### 6.2 동작 변경

- `fetch` 의 `signal` 인자에 `AbortSignal` 전달
- abort 발생 시 (`AbortError`) → **silent return**, `onError` 호출 안 함 (의도적 취소이므로)
- 기존 SSE 파싱 로직(`CHUNK` / `SOURCES` / `DONE` / `ERROR`) 동일 유지
- ky 인스턴스로 전환하지 않음 — SSE 스트리밍은 raw `fetch` 가 적합

### 6.3 401 처리

- ky 의 자동 토큰 갱신은 SSE fetch 에 적용되지 않음. 401 시 `onError(new Error('인증 만료'))` 호출
- `useQnaChat` 의 `onError` 콜백에서 status 코드를 분기하기 어려우므로 (Error 객체로만 받음), 401 메시지를 별도 분기하지 않고 **일반 에러와 동일하게 처리** (재시도 시 zustand 의 최신 토큰을 다시 읽음). 토큰 갱신 자체는 ky 가 다른 API 호출에서 처리하므로 사용자가 다른 페이지 활동을 하면 자연 갱신됨

## 7. 디자인 시스템 — 챗봇 전용 토큰

### 7.1 `index.css` `@theme` 추가

```css
@theme {
  /* Chat-specific tokens (이번 스펙 한정 — 시스템 전역 브랜드 마이그레이션은 별도 spec) */
  --color-chat-surface-deep: #0F2854;
  --color-chat-surface: #1C4D8D;
  --color-chat-soft: #4988C4;
  --color-chat-accent: #BDE8F5;
  --color-chat-bubble: #FFFFFF;
  --color-chat-bubble-text: #0F2854;
  --color-chat-source-bg: #EFF7FA;
}
```

### 7.2 색상 사용 매핑

| 영역 | 토큰 |
|---|---|
| 컨테이너 그라디언트 시작 | `--color-chat-surface-deep` |
| 컨테이너 그라디언트 끝 | `--color-chat-surface` |
| User 말풍선 배경 | `--color-chat-surface` |
| User 말풍선 글자 | `#FFFFFF` |
| Assistant 말풍선 배경 | `--color-chat-bubble` |
| Assistant 말풍선 본문 | `--color-chat-bubble-text` |
| Assistant 말풍선 `<strong>` / `<em>` | `--color-chat-surface` |
| Assistant 말풍선 목록 marker | `--color-chat-soft` |
| 인라인 `<code>` 배경 | `--color-chat-source-bg` |
| 인라인 `<code>` 글자 | `--color-chat-surface` |
| 출처 박스 배경 | `--color-chat-source-bg` |
| Badge / Chip 배경 | `--color-chat-accent` (alpha 0.18) |
| Badge / Chip 글자 | `--color-chat-accent` |
| 스크롤바 thumb | `--color-chat-accent` (alpha 0.3) |
| Typing dot | `--color-chat-soft` |
| 스트리밍 커서 | `--color-chat-surface` |
| Composer placeholder | `--color-chat-accent` (alpha 0.55) |
| 포커스 링 | `--color-chat-accent` |

### 7.3 색상 대비 (WCAG)

| 조합 | 비율 | 등급 |
|---|---|---|
| `#0F2854` on `#FFFFFF` (본문) | 13.7:1 | AAA |
| `#FFFFFF` on `#1C4D8D` (user 말풍선) | 7.0:1 | AAA |
| `#BDE8F5` on `#0F2854` (badge / chip) | 9.5:1 | AAA |
| `#1C4D8D` on `#EFF7FA` (`<code>`) | 7.4:1 | AAA |

## 8. 레이아웃 & 타이포그래피

### 8.1 컨테이너

```tsx
<section className="overflow-hidden rounded-2xl bg-gradient-to-br from-[--color-chat-surface-deep] to-[--color-chat-surface] p-4 md:p-6">
  <header className="mb-4 flex items-center gap-2">
    <span className="badge-chat">✨ Smart Q&amp;A</span>
  </header>
  <QnaMessageList ... />
  <QnaComposer ... />
</section>
```

### 8.2 사이즈

| 환경 | 컨테이너 | 패딩 | 말풍선 max-w |
|---|---|---|---|
| Mobile (`<768px`) | `min-h-[55vh] max-h-[70vh]` | `p-4` | `max-w-[88%]` |
| Desktop (`≥768px`) | `min-h-[50vh] max-h-[600px]` | `md:p-6` | `md:max-w-[80%]` |

### 8.3 말풍선 스타일

```
user      : bg #1C4D8D, text white, rounded-[18px_18px_6px_18px], px-4 py-[11px], shadow-sm, ml-auto
assistant : bg #FFFFFF, text #0F2854, rounded-[18px_18px_18px_6px], px-[18px] py-[14px], shadow-sm
error     : bg #FEF2F2, text #991B1B, rounded-[18px_18px_18px_6px], 좌측 border-l-3 #EF4444, retry 버튼
```

### 8.4 본문 타이포그래피

- `text-[15px] leading-7` (line-height 1.75) — 한국어 장문 답변 호흡
- `font-family`: Pretendard Variable (전역 토큰 그대로 상속)

## 9. 마크다운 렌더링

### 9.1 의존성

- `react-markdown` 만 추가
- `remark-gfm`, `rehype-raw` 추가하지 않음 (보안 + 의존성 최소화)

### 9.2 허용 요소 / 차단 요소

| 요소 | 처리 |
|---|---|
| `<p>`, `<strong>`, `<em>`, `<ul>`, `<ol>`, `<li>`, `<a>`, `<code>`, `<blockquote>`, `<hr>` | 허용, 스타일 매핑 |
| `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<td>`, `<th>` | **차단** (components 옵션으로 빈 컴포넌트 매핑) |
| `<pre>`, fenced code block | **차단** (정책 답변에 부적합) |
| `<img>` | **차단** |
| Raw HTML | 기본 차단 (rehype-raw 미사용) |

### 9.3 링크 보안

- `<a>` 컴포넌트 override 로 `target="_blank"` + `rel="noopener noreferrer"` 강제

### 9.4 스타일 매핑 (요약)

| 요소 | 스타일 |
|---|---|
| `<p>` | `mb-2 last:mb-0` |
| `<strong>` | `font-bold text-[--color-chat-surface]` |
| `<em>` | `italic text-[--color-chat-surface]` |
| `<ul>` / `<ol>` | `pl-[1.4em] my-1.5`, marker `text-[--color-chat-soft]` |
| `<li>` | `my-0.5` |
| `<a>` | `text-[--color-chat-surface] underline underline-offset-2 font-medium hover:text-[--color-chat-surface-deep]` |
| `<code>` | `bg-[--color-chat-source-bg] text-[--color-chat-surface] px-[7px] py-[2px] rounded text-[0.88em] font-mono` |
| `<blockquote>` | `border-l-[3px] border-[--color-chat-soft] pl-3 my-2 italic text-slate-600` |
| `<hr>` | `border-t border-slate-200 my-3` |

## 10. 애니메이션 & 마이크로 모션

### 10.1 모션 6 종 (keyframe 5 + transition 1)

| 이름 | duration / easing | 트리거 | reduced-motion 대응 |
|---|---|---|---|
| `qna-msg-in` | 320ms ease-out | 새 메시지 push | opacity-only (translate 제거) |
| `qna-typing-bounce` | 1.2s infinite (3 dot stagger 0/0.15/0.3s) | streaming + 빈 content | 정적 dot 3개 |
| `qna-cursor-blink` | 1s infinite | streaming + content !== '' | solid 표시 (깜빡임 제거) |
| `qna-check-pop` | 280ms ease-out | copy 성공 (1.5s 후 revert) | 즉시 표시/복원 |
| `qna-jump-in` | 240ms ease-out | jump-to-bottom 버튼 등장 | opacity-only |
| `qna-chip-hover` (transition) | 180ms ease | chip hover | 색만 변경, transform 제거 |

### 10.2 `index.css` 추가 예시

```css
@keyframes qna-msg-in {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}
@utility animate-qna-msg-in {
  animation: qna-msg-in 320ms ease-out both;
}
/* (같은 패턴으로 5개 더) */

@media (prefers-reduced-motion: reduce) {
  @utility animate-qna-msg-in {
    animation: none;
  }
  /* 등 */
}
```

### 10.3 자동 스크롤 정책

- 사용자 위치가 `scrollHeight - scrollTop - clientHeight < 80px` 이면 자동 따라감
- 그렇지 않으면 자동 스크롤 안 함 + jump-to-bottom 버튼 노출
- 새 메시지 push 시점이 아닌, 매 chunk 누적 시점에도 같은 정책 적용

### 10.4 SSE 인디케이터 2단계

| 단계 | 조건 | 표시 |
|---|---|---|
| 1단계 (RAG 조회 중) | `status === 'streaming' && content === ''` | `QnaTypingIndicator` (3 dot bouncing) — assistant 말풍선 자리에 표시 |
| 2단계 (스트리밍 중) | `status === 'streaming' && content !== ''` | content + 깜빡이는 커서 (`<span aria-hidden>` 2px × 1em `--color-chat-surface`) |

## 11. 추가 기능 디자인

### 11.1 추천 질문 칩 (`QnaSuggestionChips`)

- 정적 상수 4개:
  - "신청 자격이 어떻게 되나요?"
  - "어떤 서류가 필요한가요?"
  - "신청은 언제까지인가요?"
  - "지원 금액은 얼마인가요?"
- 빈 상태(`messages.length === 0`)에서만 노출
- 일반 `<button>` 사용 (div+onclick 금지) — 키보드 Tab 접근
- 클릭 시 `onPick(text)` → `useQnaChat.send(text)` 연결
- 미인증 사용자가 칩 클릭 시 `onLoginPrompt()` 호출, send 호출 안 함

### 11.2 복사 버튼

- `QnaMessageBubble` 내 assistant 메시지 우하단
- 모바일: 항상 표시 / 데스크탑: hover 시에만 (`opacity-0 group-hover:opacity-100`)
- 클릭 → `onCopy(content)` → `navigator.clipboard.writeText`
- 성공 시 lucide `Copy` → `Check` 아이콘 swap (280ms scale morph), 1.5초 후 revert
- visually-hidden `aria-live="polite"` 영역에 "복사되었습니다" 일시 추가
- 토스트 알림 도입 안 함 (스코프/의존성 절약)

### 11.3 재시도 버튼

- `status === 'error'` 인 assistant 메시지에서만 노출
- 에러 카드 내부 (또는 우하단)에 `↻ 재시도` 버튼
- 클릭 → `onRetry(messageId)` → `useQnaChat.retry(messageId)`

### 11.4 Jump-to-bottom 버튼

- `QnaMessageList` 내부 `position: absolute` `right-3 bottom-3` (mobile) / `md:right-4 md:bottom-4`
- 노출 조건: `scrollHeight - scrollTop - clientHeight >= 80px`
- 클릭 시 `behavior: 'smooth'` 으로 마지막 메시지로 스크롤
- 36×36px 원형 흰색 배경 + `#1C4D8D` 화살표 + 그림자

## 12. 접근성 (a11y)

### 12.1 ARIA 속성

| 영역 | 속성 |
|---|---|
| 메시지 리스트 | `role="log" aria-live="polite" aria-atomic="false"` |
| Typing indicator | `role="status" aria-label="답변 준비 중"` |
| 스트리밍 커서 | `aria-hidden="true"` |
| 복사 버튼 | `aria-label="답변 복사"` |
| 재시도 버튼 | `aria-label="답변 재생성"` |
| Jump-to-bottom | `aria-label="가장 최근 메시지로 이동"` |
| 추천 질문 칩 | 일반 `<button>` (텍스트가 라벨) |
| 전송 버튼 | `aria-label="질문 전송"` (현행 유지) |
| Copy 성공 알림 | visually-hidden `<div aria-live="polite">` 에 1.5초간 "복사되었습니다" |

### 12.2 키보드

- 모든 인터랙티브 요소 Tab 순회 가능
- 추천 질문 칩 Enter/Space 활성화
- Composer 의 Enter: `e.nativeEvent.isComposing === true` 면 무시 (한글 IME)

### 12.3 포커스 링

- `focus-visible:outline-2 focus-visible:outline-[--color-chat-accent] focus-visible:outline-offset-2`

### 12.4 터치 타겟

- Composer 입력 / 전송 버튼: 44×44px (frontend/CLAUDE.md 컨벤션)
- 메시지 안 액션 버튼 (복사/재시도): 36×36px (메시지 내부 보조 버튼)
- Jump-to-bottom: 36×36px

### 12.5 prefers-reduced-motion

- 6 종 keyframe 모두 `@media (prefers-reduced-motion: reduce)` 에서 동작 변경 (10.2 참고)

## 13. 에러 처리

| 케이스 | 트리거 | UI 처리 |
|---|---|---|
| 네트워크 / 5xx | `fetch` reject 또는 SSE `ERROR` 이벤트 | 빨간 카드 (`#FEF2F2` 배경 + `#EF4444` 좌측 테두리) + 재시도 버튼 |
| 401 (인증 만료) | `response.status === 401` | 일반 에러와 동일 카드 처리. 재시도 시 zustand authStore 의 최신 토큰 다시 읽음 |
| AbortError (의도적 취소) | 사용자 새 질문으로 abort 또는 페이지 unmount | **silent return**, `onError` 호출 안 함, messages 변경 없음 |

- **자동 백오프 재시도 안 함** — 사용자가 명시적으로 ↻ 재시도 버튼 눌러야만 재시도 (LLM 비용 통제)
- **재시도 횟수 제한 없음** — 같은 메시지 재시도 무한 가능 (사용자가 포기할 때까지)

## 14. 테스트 전략

| 대상 | 종류 | 검증 |
|---|---|---|
| `useQnaChat` | 단위 (Vitest) | send → user/assistant push, onChunk → content 누적, onError → status `'error'`, retry → 메시지 reset, 새 send 가 abort 트리거 |
| `apis/qna.api.ts` | 단위 | SSE 파싱 (CHUNK/SOURCES/DONE/ERROR), AbortSignal 동작 |
| `QnaMessageBubble` | 컴포넌트 (Testing Library) | role 별 렌더, markdown 강조 렌더, 복사 클릭 → onCopy 호출, error status 시 재시도 노출 |
| `QnaSuggestionChips` | 컴포넌트 | 칩 클릭 → onPick 호출, 키보드 Tab/Enter 동작 |
| `QnaMessageList` | 컴포넌트 | scrollTop 조작 후 메시지 push 시 jump 버튼 노출, bottom ±80px 일 때 자동 스크롤 |
| `QnaComposer` | 컴포넌트 | IME composition 중 Enter 무시, submit 시 trim, 빈 입력 disabled |
| `QnaTypingIndicator` | 스냅샷 | 정적 컴포넌트 |
| `QnaChatSection` | 통합 | 빈 상태 → 칩 클릭 → user msg → assistant streaming → done. SSE 는 `vi.fn()` + `ReadableStream` 직접 mock |

- **MSW 추가 안 함** — `vi.fn()` + `ReadableStream` 으로 SSE mock

## 15. 핫패스 가드

`CLAUDE.md`: "비로그인 사용자 핫패스에서 비싼 LLM 생성을 직접 유발하지 않는다."

- `QnaSuggestionChips` 클릭: `isAuthenticated` false 면 `onLoginPrompt()` 호출, send 호출 안 함
- `QnaComposer`: 현행 동작 유지 (focus 시 모달 + readOnly)

## 16. 구현 우선순위 (Plan 단계 분할 기준)

| 우선 | 작업 | 의도 |
|---|---|---|
| P0 | 디자인 토큰 7개 + keyframe 6 종 추가 (`index.css`) | 후속 컴포넌트 토큰 의존 |
| P0 | `types/qna.ts` 신설 + `QnaMessage` 이동 + `status` 3-state 확장 | 타입 기반 |
| P1 | `apis/qna.api.ts` 시그니처 변경 (callbacks 객체 + AbortSignal) | 훅 의존 |
| P1 | `hooks/useQnaChat.ts` 작성 + 단위 테스트 | 컴포넌트 의존 |
| P2 | `react-markdown` 의존성 추가 | 다음 단계 의존 |
| P2 | `components/qna/` 6 컴포넌트 작성 (typing → bubble → list → composer → chips → section) | 작은 단위부터 조립 |
| P3 | `PolicyDetailPage.tsx` inline 정의 제거, import 로 교체 | 통합 |
| P3 | 컴포넌트 테스트 + 통합 테스트 작성 | 회귀 방지 |
| P3 | `prefers-reduced-motion` 미디어 쿼리 keyframe 적용 | a11y 마무리 |

## 17. Out of Scope

- **Stop generation 버튼** (Q5 d) — 다음 이터레이션 (백엔드 abort 검증 후)
- **시스템 전역 brand 컬러 마이그레이션** (`--color-brand-*`, `--color-indigo-*` 전체 교체) — 별도 spec 필요
- **출처 인라인 인용** (Q4 옵션 C) — 백엔드 응답 포맷 협의 필요
- **다회 턴 컨텍스트 / 답변 히스토리 영속화** — 단발 Q&A 유지
- **모바일 앱 / PWA 푸시 알림** — MVP 범위 외 (`CLAUDE.md` 명시)
- **답변 좋아요/싫어요 피드백** — 별도 백엔드 필요
- **Toast Provider 도입** — 복사 알림은 visually-hidden aria-live 로 처리

## 18. 검수 체크리스트

구현 완료 시 검증 항목:

- [ ] 채팅 영역 높이가 `min-h-[50vh] max-h-[70vh]` (모바일) / `min-h-[50vh] max-h-[600px]` (데스크탑) 으로 동작
- [ ] 본문 글씨가 `text-[15px] leading-7`
- [ ] 스크롤바가 6px sky 톤 반투명 (Webkit / Firefox 모두)
- [ ] SSE 1단계: 빈 content 일 때 3 dot bouncing
- [ ] SSE 2단계: 첫 chunk 도착 후 깜빡이는 커서
- [ ] 마크다운 `<strong>`, `<em>`, `<ul>`, `<ol>`, `<li>`, `<a>`, `<code>`, `<blockquote>`, `<hr>` 렌더
- [ ] `<table>`, `<pre>`, `<img>` 차단
- [ ] 외부 링크 `target="_blank"` + `rel="noopener noreferrer"`
- [ ] 빈 상태 추천 질문 4개 칩
- [ ] 복사 버튼 → 아이콘 morph + visually-hidden announce
- [ ] 에러 시 재시도 버튼 → `useQnaChat.retry` 호출
- [ ] Jump-to-bottom 버튼: bottom ±80px 정책으로 노출/숨김
- [ ] 새 질문 보내면 진행 중 SSE abort
- [ ] `prefers-reduced-motion` 시 6 종 모션 비활성/정적
- [ ] 색상 대비 WCAG AAA 통과 (자동 도구 / 수동)
- [ ] 키보드만으로 전체 인터랙션 가능
- [ ] IME 한글 조합 중 Enter 무시
- [ ] `PolicyDetailPage.tsx` 가 ≈170줄 이상 감소
- [ ] `QnaMessage` 가 `types/qna.ts` 로 이동, 구 위치 제거
- [ ] 신규/수정 파일 모두 단위·컴포넌트·통합 테스트 통과
- [ ] `npm run build` 성공
