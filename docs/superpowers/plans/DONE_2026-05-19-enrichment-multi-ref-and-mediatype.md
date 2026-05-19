# 온통청년 enrichment 멀티 reference URL 머지 + promote mediaType 휴리스틱 강화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년(YOUTH_CENTER) n8n 워크플로우에서 (1) 여러 reference URL 의 본문·첨부 후보를 모두 모아 머지하고, (2) URL 확장자 외 텍스트/path 신호로 mediaType 을 추론해 한국 정부 사이트 다운로드 핸들러 URL 도 첨부 승격 통과시킨다.

**Architecture:**
- **Phase A (멀티 URL 머지)**: 기존 3개 노드(`링크 선택`, `외부 페이지 fetch`, `boilerplate 제거 + 첨부 후보`) + `링크 존재` IF 를 단일 mega-node `링크 fetch + 머지` 로 통합. mega-node 안에서 URL 선택 → 병렬 `https.get` → cheerio 본문/첨부 추출 → 결과 머지를 처리. spec 의 제안(splitInBatches)을 채택하지 않은 이유: (a) splitInBatches 경계로 정책 메타데이터를 캐리하는 mechanism 이 복잡, (b) 기존 LLM 노드가 이미 `require('https')` 패턴을 사용하므로 일관됨, (c) 단일 노드의 알고리즘은 `enrich.mjs` 한 파일로 fixture 검증 가능.
- **Phase B (mediaType 휴리스틱)**: `promote.mjs` + `attachments 승격` 노드 jsCode 에 텍스트/path 키워드 추론을 추가. URL → name → path 우선순위. 기존 동작은 보존(URL 확장자가 있으면 그대로).
- **TDD**: 각 fixture 케이스 추가 → 검증 실패 확인 → 알고리즘 갱신 → 검증 통과 → 노드 jsCode 미러 → 커밋.
- **단일 PR**: `feat(n8n): 온통청년 enrichment 멀티 reference URL 머지 + promote mediaType 추론 강화` 로 묶어 한 번에 리뷰.

**Tech Stack:** Node.js 18+ (top-level await, `node:fs/promises`, `node:assert/strict`), n8n Code 노드 (cheerio 사전 설치 모듈), `npm` 없음 (fixture verify 는 단일 파일 실행 `node verify.mjs`).

---

## 파일 구조

### 신규 파일
| 경로 | 책임 |
|---|---|
| `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs` | 순수 함수 `selectUrls(policy)` + `mergeFetchResults(results)`. n8n mega-node 의 jsCode 와 동기화 미러. |
| `n8n/workflows/__fixtures__/enrichment-merge/verify.mjs` | `cases/*.input.json` 읽어 `selectUrls` + `mergeFetchResults` 실행, `expected.json` 비교. |
| `n8n/workflows/__fixtures__/enrichment-merge/README.md` | 디렉토리 사용법 + 동기화 책임 + 운영 절차. |
| `n8n/workflows/__fixtures__/enrichment-merge/cases/case-*.{input,expected}.json` | 6개 케이스. |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-has-pdf-suffix.{input,expected}.json` | 신규 케이스 (mediaType heuristic). |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-paren-keyword.{input,expected}.json` | 신규 케이스. |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-img-alt-fallback.{input,expected}.json` | 신규 케이스. |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-path-keyword.{input,expected}.json` | 신규 케이스. |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-no-signal.{input,expected}.json` | 신규 케이스. |
| `n8n/workflows/__fixtures__/promote-attachments/cases/case-conflicting-signals.{input,expected}.json` | 신규 케이스. |

### 수정 파일
| 경로 | 변경 요지 |
|---|---|
| `n8n/workflows/youth-center-seoul.json` | 노드 3개 삭제 + 1개 IF 삭제 + 1개 mega-node 추가 (`링크 선택` 자리 차지) + `attachments 승격` 노드 jsCode 휴리스틱 확장 + connections 갱신 |
| `n8n/workflows/__fixtures__/promote-attachments/promote.mjs` | `extractExt`, `mapExt`, `inferMediaType` 분리 + 텍스트/path 추론 함수 추가 |

---

## Phase A: 멀티 URL 머지

### Task 1: enrichment-merge fixture 스켈레톤 + 빈 verify 실패 확인

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/README.md`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/.gitkeep`

- [ ] **Step 1: `enrich.mjs` 에 export stub 작성**

```js
// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "링크 fetch + 머지" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';

export function selectUrls(p) {
  return [];
}

export function mergeFetchResults(results) {
  return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
}
```

- [ ] **Step 2: `verify.mjs` 작성** (promote-attachments 패턴 차용)

```js
import { selectUrls, mergeFetchResults } from './enrich.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const CASES_DIR = new URL('./cases/', import.meta.url);

function deepEqual(a, b) {
  try { deepStrictEqual(a, b); return true; } catch { return false; }
}

const entries = await readdir(CASES_DIR);
const inputs = entries.filter(e => e.endsWith('.input.json')).sort();

if (inputs.length === 0) {
  console.error(`no fixtures found in ${CASES_DIR.pathname}`);
  process.exit(1);
}

let failed = 0;
for (const inputFile of inputs) {
  const name = inputFile.replace('.input.json', '');
  const expectedFile = `${name}.expected.json`;
  const input = JSON.parse(await readFile(new URL(inputFile, CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, CASES_DIR), 'utf8'));
  const actual = {
    selectedUrls: selectUrls(input.policy),
    merged: mergeFetchResults(input.fetchResults)
  };
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    console.log(`FAIL  ${name}`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

if (failed > 0) {
  console.error(`\n${failed} case(s) failed`);
  process.exit(1);
}
console.log(`\nAll ${inputs.length} case(s) passed`);
```

- [ ] **Step 3: `README.md` 작성**

```markdown
# enrichment-merge fixtures

`youth-center-seoul.json` 의 `링크 fetch + 머지` 노드가 만족해야 하는 입출력 계약을 픽스처로 고정한다.

## 구조

- `enrich.mjs` — n8n 노드의 jsCode 와 **동일 알고리즘** 을 담은 ES module. 단위 검증 전용.
- `verify.mjs` — `cases/*.input.json` 을 읽어 `selectUrls` + `mergeFetchResults` 를 실행하고 `*.expected.json` 과 비교.
- `cases/case-*.input.json` 은 `{ policy: {...}, fetchResults: [{ url, status, cleanedText, extraAttachments }] }` 형태로, `selectUrls` 에 들어가는 정책 메타와 `mergeFetchResults` 에 들어가는 (모의된) fetch 결과를 함께 담는다.

> `enrich.mjs` 와 `youth-center-seoul.json` 의 `링크 fetch + 머지` 노드 jsCode 는 동기화된 미러다. 한쪽이 권위 있는 단일 출처가 아니므로, 변경 시 두 곳을 함께 갱신해야 한다.

요구 런타임: Node.js 18+ (top-level await, `node:fs/promises`, `node:assert/strict`).

## 실행

```bash
node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs
```

전체 케이스 PASS 면 exit 0, 한 건이라도 FAIL 이면 exit 1.

## 동기화 책임

⚠ `enrich.mjs` 와 `youth-center-seoul.json` 의 `링크 fetch + 머지` 노드 jsCode 는 항상 동일 로직이어야 한다. 노드 jsCode 가 변경됐는데 픽스처 검증이 깨지면, 의도된 변경이라면 픽스처를 갱신하고, 아니면 노드 jsCode 를 되돌려라.
```

- [ ] **Step 4: `cases/.gitkeep` 생성**

빈 파일 (디렉토리만 commit).

```bash
: > n8n/workflows/__fixtures__/enrichment-merge/cases/.gitkeep
```

- [ ] **Step 5: 빈 cases 실행 결과 확인**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: exit 1 + `no fixtures found in .../cases/` 메시지.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "test(n8n): enrichment-merge fixture 스켈레톤 추가"
```

---

### Task 2: case-single-url-ok (단일 URL 회귀 baseline)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-single-url-ok.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-single-url-ok.expected.json`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://www.example.com/apply",
    "refUrlAddr1": "",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://www.example.com/apply",
      "status": null,
      "cleanedText": "정책 안내 페이지 본문. 신청 자격 및 절차는 다음과 같다. 만 19~34세 청년이 대상이며 소득 기준은 중위 100% 이하다. 자세한 내용은 본 페이지를 참고하라.",
      "extraAttachments": [
        { "name": "신청서.pdf", "url": "https://www.example.com/file/apply.pdf" }
      ]
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "selectedUrls": ["https://www.example.com/apply"],
  "merged": {
    "cleanedText": "정책 안내 페이지 본문. 신청 자격 및 절차는 다음과 같다. 만 19~34세 청년이 대상이며 소득 기준은 중위 100% 이하다. 자세한 내용은 본 페이지를 참고하라.",
    "extraAttachments": [
      { "name": "신청서.pdf", "url": "https://www.example.com/file/apply.pdf" }
    ],
    "status": null
  }
}
```

- [ ] **Step 3: verify 실행 → FAIL 확인 (stub 미구현)**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `FAIL  case-single-url-ok` + exit 1.

- [ ] **Step 4: `selectUrls` 와 `mergeFetchResults` 첫 구현**

`enrich.mjs` 의 두 함수를 교체:

```js
export function selectUrls(p) {
  const candidates = [p?.aplyUrlAddr, p?.refUrlAddr1, p?.refUrlAddr2]
    .map(s => (typeof s === 'string' ? s.trim() : ''))
    .filter(Boolean);
  return candidates.slice(0, MAX_URLS);
}

export function mergeFetchResults(results) {
  if (!Array.isArray(results) || results.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  const ok = results.filter(r => r && !r.status);
  if (ok.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  let cleanedText = ok.map(r => r.cleanedText || '').join(TEXT_SEPARATOR);
  if (cleanedText.length > MAX_CLEANED_LEN) cleanedText = cleanedText.slice(0, MAX_CLEANED_LEN);
  const extraAttachments = ok.flatMap(r => Array.isArray(r.extraAttachments) ? r.extraAttachments : []);
  return { cleanedText, extraAttachments, status: null };
}
```

- [ ] **Step 5: verify 재실행 → PASS 확인**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `PASS  case-single-url-ok` + exit 0.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "test(n8n): enrichment-merge case-single-url-ok 추가"
```

---

### Task 3: case-multi-url-all-ok (2개 URL 머지)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-ok.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-ok.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://a.example.com/apply",
    "refUrlAddr1": "https://b.example.com/info",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://a.example.com/apply",
      "status": null,
      "cleanedText": "신청 페이지: 자격은 만 19~34세 청년.",
      "extraAttachments": [
        { "name": "신청서.pdf", "url": "https://a.example.com/file/sin.pdf" }
      ]
    },
    {
      "url": "https://b.example.com/info",
      "status": null,
      "cleanedText": "정책 안내: 지원 내용은 월 30만원, 12개월간 지급.",
      "extraAttachments": [
        { "name": "안내문.hwp", "url": "https://b.example.com/file/info.hwp" }
      ]
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

`---` separator 가 들어간 머지 본문 + extras union.

```json
{
  "selectedUrls": ["https://a.example.com/apply", "https://b.example.com/info"],
  "merged": {
    "cleanedText": "신청 페이지: 자격은 만 19~34세 청년.\n\n---\n\n정책 안내: 지원 내용은 월 30만원, 12개월간 지급.",
    "extraAttachments": [
      { "name": "신청서.pdf", "url": "https://a.example.com/file/sin.pdf" },
      { "name": "안내문.hwp", "url": "https://b.example.com/file/info.hwp" }
    ],
    "status": null
  }
}
```

- [ ] **Step 3: verify 실행 → PASS 확인**

현재 `mergeFetchResults` 가 이미 union + join 으로 처리하므로 PASS.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `PASS  case-multi-url-all-ok`, `PASS  case-single-url-ok`, exit 0.

> Step 3 가 FAIL 이면 separator 또는 union 로직 미구현이므로 enrich.mjs 의 mergeFetchResults 수정.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/cases/
git commit -m "test(n8n): enrichment-merge case-multi-url-all-ok 추가"
```

---

### Task 4: case-multi-url-mixed (1개 OK + 1개 TOO_SHORT → status=OK, OK 본문만 사용)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-mixed.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-mixed.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://a.example.com/apply",
    "refUrlAddr1": "https://b.example.com/info",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://a.example.com/apply",
      "status": "TOO_SHORT",
      "cleanedText": "짧음",
      "extraAttachments": []
    },
    {
      "url": "https://b.example.com/info",
      "status": null,
      "cleanedText": "정책 안내: 지원 내용은 월 30만원, 12개월간 지급. 신청은 온라인.",
      "extraAttachments": [
        { "name": "안내문.hwp", "url": "https://b.example.com/file/info.hwp" }
      ]
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

`selectedUrls` 는 정책 메타에서 추출되므로 입력의 2개 URL 모두 포함. `merged` 는 OK 인 1개만 사용.

```json
{
  "selectedUrls": ["https://a.example.com/apply", "https://b.example.com/info"],
  "merged": {
    "cleanedText": "정책 안내: 지원 내용은 월 30만원, 12개월간 지급. 신청은 온라인.",
    "extraAttachments": [
      { "name": "안내문.hwp", "url": "https://b.example.com/file/info.hwp" }
    ],
    "status": null
  }
}
```

- [ ] **Step 3: verify 실행 → PASS 확인**

현재 `ok.filter(r => !r.status)` 가 TOO_SHORT 를 자동 배제하므로 PASS 예상.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `PASS  case-multi-url-mixed` 포함 전체 PASS, exit 0.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/cases/
git commit -m "test(n8n): enrichment-merge case-multi-url-mixed 추가"
```

---

### Task 5: case-multi-url-all-fail (2개 모두 FETCH_FAILED → status=FETCH_FAILED)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-fail.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-fail.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://a.example.com/apply",
    "refUrlAddr1": "https://b.example.com/info",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://a.example.com/apply",
      "status": "FETCH_FAILED",
      "cleanedText": "",
      "extraAttachments": []
    },
    {
      "url": "https://b.example.com/info",
      "status": "FETCH_FAILED",
      "cleanedText": "",
      "extraAttachments": []
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "selectedUrls": ["https://a.example.com/apply", "https://b.example.com/info"],
  "merged": {
    "cleanedText": "",
    "extraAttachments": [],
    "status": "FETCH_FAILED"
  }
}
```

- [ ] **Step 3: verify 실행 → 결과 확인**

현재 구현은 `ok.length === 0` 이면 항상 `FETCH_FAILED` 반환. 모두 FETCH_FAILED 인 경우 PASS.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: 전체 PASS, exit 0.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/cases/
git commit -m "test(n8n): enrichment-merge case-multi-url-all-fail 추가"
```

---

### Task 6: case-multi-url-all-too-short (모두 TOO_SHORT → status=TOO_SHORT)

기존 단일 URL 동작에서 `cleaned.length < 200` 이면 TOO_SHORT 였다. 멀티에서도 모두 TOO_SHORT 면 그 신호를 유지해야 다운스트림 IF 가 적절히 skip 한다.

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-too-short.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-multi-url-all-too-short.expected.json`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://a.example.com/apply",
    "refUrlAddr1": "https://b.example.com/info",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://a.example.com/apply",
      "status": "TOO_SHORT",
      "cleanedText": "짧음",
      "extraAttachments": []
    },
    {
      "url": "https://b.example.com/info",
      "status": "TOO_SHORT",
      "cleanedText": "또 짧음",
      "extraAttachments": []
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "selectedUrls": ["https://a.example.com/apply", "https://b.example.com/info"],
  "merged": {
    "cleanedText": "",
    "extraAttachments": [],
    "status": "TOO_SHORT"
  }
}
```

- [ ] **Step 3: verify 실행 → FAIL 확인**

현재 구현은 `ok.length === 0` 이면 항상 `FETCH_FAILED` 를 돌려준다. 모두 TOO_SHORT 인 경우도 FETCH_FAILED 가 돌아와서 FAIL.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `FAIL  case-multi-url-all-too-short`.

- [ ] **Step 4: `mergeFetchResults` 수정 — 실패 모드 우선순위 구분**

`enrich.mjs` 의 `mergeFetchResults` 의 "all failed" 분기를 교체:

```js
  if (ok.length === 0) {
    const allTooShort = results.length > 0 && results.every(r => r && r.status === 'TOO_SHORT');
    return {
      cleanedText: '',
      extraAttachments: [],
      status: allTooShort ? 'TOO_SHORT' : 'FETCH_FAILED'
    };
  }
```

- [ ] **Step 5: verify 재실행 → 전체 PASS**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: 전체 PASS, exit 0.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "test(n8n): enrichment-merge TOO_SHORT 우선 유지 + 신규 케이스"
```

---

### Task 7: case-duplicate-url (aplyUrl == refUrl1 → URL 1개만 선택)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-duplicate-url.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-duplicate-url.expected.json`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`

- [ ] **Step 1: input.json 작성** (대소문자 + trailing slash 차이 포함)

```json
{
  "policy": {
    "aplyUrlAddr": "https://www.Example.com/apply/",
    "refUrlAddr1": "https://www.example.com/apply",
    "refUrlAddr2": "https://other.example.com/info"
  },
  "fetchResults": [
    {
      "url": "https://www.Example.com/apply/",
      "status": null,
      "cleanedText": "본문 충분히 길게 들어가 있다. 정책 안내가 자세하다. 신청 자격은 청년 19~34세.",
      "extraAttachments": []
    },
    {
      "url": "https://other.example.com/info",
      "status": null,
      "cleanedText": "참고 사이트 본문. 안내 내용 풍부. 절차와 서류 안내.",
      "extraAttachments": []
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "selectedUrls": ["https://www.Example.com/apply/", "https://other.example.com/info"],
  "merged": {
    "cleanedText": "본문 충분히 길게 들어가 있다. 정책 안내가 자세하다. 신청 자격은 청년 19~34세.\n\n---\n\n참고 사이트 본문. 안내 내용 풍부. 절차와 서류 안내.",
    "extraAttachments": [],
    "status": null
  }
}
```

`selectedUrls` 는 dedup 후 2개. 첫 매칭 표현(`https://www.Example.com/apply/`) 을 유지 — refUrl1 은 `https://www.example.com/apply` 와 정규화 키가 같아 dedup 됨.

- [ ] **Step 3: verify 실행 → FAIL 확인**

현재 `selectUrls` 는 dedup 없음 → `selectedUrls` 가 3개로 나옴 → FAIL.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `FAIL  case-duplicate-url`.

- [ ] **Step 4: `selectUrls` 에 정규화 dedup 추가**

`enrich.mjs` 의 `selectUrls` 교체:

```js
function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

export function selectUrls(p) {
  const candidates = [p?.aplyUrlAddr, p?.refUrlAddr1, p?.refUrlAddr2]
    .map(s => (typeof s === 'string' ? s.trim() : ''))
    .filter(Boolean);
  const seen = new Set();
  const out = [];
  for (const u of candidates) {
    const key = normalizeUrlKey(u);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(u);
    if (out.length >= MAX_URLS) break;
  }
  return out;
}
```

- [ ] **Step 5: verify 재실행 → 전체 PASS**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: 전체 PASS, exit 0.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "test(n8n): enrichment-merge URL 정규화 dedup + case-duplicate-url"
```

---

### Task 8: case-dedup-attachments (두 URL 에 동일 첨부 → 1회만 등록)

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-dedup-attachments.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-dedup-attachments.expected.json`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "https://a.example.com/apply",
    "refUrlAddr1": "https://b.example.com/info",
    "refUrlAddr2": null
  },
  "fetchResults": [
    {
      "url": "https://a.example.com/apply",
      "status": null,
      "cleanedText": "본문 A 충분히 길게.",
      "extraAttachments": [
        { "name": "공통 안내.pdf", "url": "https://cdn.example.com/file/common.pdf" }
      ]
    },
    {
      "url": "https://b.example.com/info",
      "status": null,
      "cleanedText": "본문 B 충분히 길게.",
      "extraAttachments": [
        { "name": "공통 안내.PDF", "url": "https://cdn.example.com/file/COMMON.pdf" },
        { "name": "추가 양식.hwp", "url": "https://cdn.example.com/file/extra.hwp" }
      ]
    }
  ]
}
```

- [ ] **Step 2: expected.json 작성**

대소문자 차이는 dedup 키에서 정규화. 먼저 만난 항목(`https://cdn.example.com/file/common.pdf`) 만 살리고 뒤의 동일 URL 은 버린다.

```json
{
  "selectedUrls": ["https://a.example.com/apply", "https://b.example.com/info"],
  "merged": {
    "cleanedText": "본문 A 충분히 길게.\n\n---\n\n본문 B 충분히 길게.",
    "extraAttachments": [
      { "name": "공통 안내.pdf", "url": "https://cdn.example.com/file/common.pdf" },
      { "name": "추가 양식.hwp", "url": "https://cdn.example.com/file/extra.hwp" }
    ],
    "status": null
  }
}
```

- [ ] **Step 3: verify 실행 → FAIL 확인**

현재 `mergeFetchResults` 는 `flatMap` 만 — dedup 없음. extras 가 3개로 나와서 FAIL.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: `FAIL  case-dedup-attachments`.

- [ ] **Step 4: `mergeFetchResults` 에 attachment dedup 추가**

`enrich.mjs` 의 OK 분기 마지막 부분 교체:

```js
  const seenAttachments = new Set();
  const extraAttachments = [];
  for (const r of ok) {
    const items = Array.isArray(r.extraAttachments) ? r.extraAttachments : [];
    for (const a of items) {
      if (!a || typeof a.url !== 'string') continue;
      const key = a.url.toLowerCase();
      if (seenAttachments.has(key)) continue;
      seenAttachments.add(key);
      extraAttachments.push(a);
    }
  }
  return { cleanedText, extraAttachments, status: null };
```

- [ ] **Step 5: verify 재실행 → 전체 PASS**

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: 전체 PASS, exit 0.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "test(n8n): enrichment-merge attachment dedup + case-dedup-attachments"
```

---

### Task 9: case-no-url (정책에 reference URL 부재 → 빈 선택)

mega-node 의 NO_LINK 분기는 `selectedUrls.length === 0` 일 때 발동된다. 픽스처는 그 갈래를 검증한다.

**Files:**
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-no-url.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-no-url.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "policy": {
    "aplyUrlAddr": "",
    "refUrlAddr1": null,
    "refUrlAddr2": "   "
  },
  "fetchResults": []
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "selectedUrls": [],
  "merged": {
    "cleanedText": "",
    "extraAttachments": [],
    "status": "FETCH_FAILED"
  }
}
```

> **메모**: `merged.status` 가 `FETCH_FAILED` 가 되는 이유는 픽스처가 `mergeFetchResults([])` 를 호출하기 때문이다. mega-node 자체는 `selectedUrls.length === 0` 일 때 `mergeFetchResults` 를 거치지 않고 `_enrichmentStatus = 'NO_LINK'` 를 직접 세팅한다 (Task 10). 픽스처는 머지 함수의 동작만 보존하면 충분.

- [ ] **Step 3: verify 실행 → 전체 PASS**

현재 구현: `selectUrls` 는 빈 배열, `mergeFetchResults([])` 는 `FETCH_FAILED`. PASS.

Run: `node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
Expected: 전체 PASS.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/cases/
git commit -m "test(n8n): enrichment-merge case-no-url 추가"
```

---

### Task 10: youth-center-seoul.json mega-node 통합

기존 노드 3개를 삭제하고 1개로 통합. n8n JSON 파일을 직접 편집한다.

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: 현재 노드/연결 상태 백업 위치 확인**

```bash
git status
git diff --stat n8n/workflows/youth-center-seoul.json
```

작업 시작 전 워킹 트리 clean 이어야 한다.

- [ ] **Step 2: `링크 선택` 노드를 `링크 fetch + 머지` 로 교체**

`n8n/workflows/youth-center-seoul.json` 에서 `"id": "pick-link"` 노드의 `parameters.jsCode` 와 `name` 을 다음으로 교체. **CRITICAL: JSON 문자열로 임베드 — `\n` 이스케이프, 큰따옴표 이스케이프 주의.**

신규 jsCode (가독성 있는 원본):

```js
// 동기화 책임: n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs 와
// 동일 알고리즘이어야 한다. 한 곳을 수정하면 다른 곳도 같은 변경을 반영해야 한다.
const cheerio = require('cheerio');
const https = require('https');
const http = require('http');

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';
const FETCH_TIMEOUT_MS = 10000;

function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

function selectUrls(p) {
  const candidates = [p?.aplyUrlAddr, p?.refUrlAddr1, p?.refUrlAddr2]
    .map(s => (typeof s === 'string' ? s.trim() : ''))
    .filter(Boolean);
  const seen = new Set();
  const out = [];
  for (const u of candidates) {
    const key = normalizeUrlKey(u);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(u);
    if (out.length >= MAX_URLS) break;
  }
  return out;
}

function mergeFetchResults(results) {
  if (!Array.isArray(results) || results.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  const ok = results.filter(r => r && !r.status);
  if (ok.length === 0) {
    const allTooShort = results.length > 0 && results.every(r => r && r.status === 'TOO_SHORT');
    return {
      cleanedText: '',
      extraAttachments: [],
      status: allTooShort ? 'TOO_SHORT' : 'FETCH_FAILED'
    };
  }
  let cleanedText = ok.map(r => r.cleanedText || '').join(TEXT_SEPARATOR);
  if (cleanedText.length > MAX_CLEANED_LEN) cleanedText = cleanedText.slice(0, MAX_CLEANED_LEN);
  const seenAttachments = new Set();
  const extraAttachments = [];
  for (const r of ok) {
    const items = Array.isArray(r.extraAttachments) ? r.extraAttachments : [];
    for (const a of items) {
      if (!a || typeof a.url !== 'string') continue;
      const key = a.url.toLowerCase();
      if (seenAttachments.has(key)) continue;
      seenAttachments.add(key);
      extraAttachments.push(a);
    }
  }
  return { cleanedText, extraAttachments, status: null };
}

function absUrl(href, pageUrl) {
  if (!href) return href;
  if (/^https?:\/\//i.test(href)) return href;
  const m = pageUrl.match(/^(https?:\/\/[^/]+)/);
  const origin = m ? m[1] : '';
  if (href.startsWith('//')) {
    const proto = (pageUrl.match(/^(https?:)/) || ['', 'https:'])[1];
    return proto + href;
  }
  if (href.startsWith('/')) return origin + href;
  return origin + '/' + href.replace(/^\.?\//, '');
}

function extractCleanedAndAttachments(rawHtml, pageUrl) {
  const $ = cheerio.load(rawHtml);
  $('script, style, nav, footer, aside, header, noscript').remove();
  const root = $('main').first().length ? $('main').first()
            : $('article').first().length ? $('article').first()
            : $('[role="main"]').first().length ? $('[role="main"]').first()
            : $('#content').first().length ? $('#content').first()
            : $('body').first();
  let cleaned = root.text().replace(/\s+/g, ' ').trim();
  if (cleaned.length > 8000) cleaned = cleaned.slice(0, 8000);

  const seen = new Set();
  const extras = [];
  $('a[href]').each((_, el) => {
    const $a = $(el);
    const href = $a.attr('href') || '';
    const text = $a.text().trim();
    const imgAlt = $a.find('img').first().attr('alt') || '';
    const lowerHref = href.toLowerCase();
    const extPattern = /\.(pdf|hwp|hwpx|docx|xlsx|zip)(\?|$|#)/i;
    const hasExt = extPattern.test(href);
    const textHasExt = /\.(pdf|hwp|hwpx|docx|xlsx|zip)$/i.test(text);
    const imgIsFile = /^(pdf|hwp|hwpx|docx|xlsx|zip)$/i.test(imgAlt);
    const hrefHasDownloadKw = /(download|filedown|attach)/i.test(lowerHref);
    const looksLikeFile = hasExt || textHasExt || imgIsFile || (hrefHasDownloadKw && text.length > 0 && text.length < 200);
    if (!looksLikeFile) return;
    const url = absUrl(href, pageUrl);
    if (seen.has(url)) return;
    seen.add(url);
    let name = text;
    if (!name || name.length < 2) {
      name = imgAlt ? `attachment.${imgAlt}` : url.split('/').pop().slice(0, 200);
    }
    name = name.replace(/\s*미리보기\s*$/, '').trim().slice(0, 200);
    extras.push({ name, url });
  });
  return { cleaned, extras };
}

function httpGetText(url) {
  return new Promise((resolve) => {
    let lib;
    try { lib = url.startsWith('https://') ? https : http; }
    catch (e) { return resolve({ ok: false }); }
    const req = lib.request(url, {
      method: 'GET',
      headers: {
        'User-Agent': 'YouthFit-Bot/1.0 (+https://youthfit.kr/bot)',
        'Accept': 'text/html,application/xhtml+xml'
      },
      timeout: FETCH_TIMEOUT_MS
    }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        // 간단 follow (1 hop). 더 깊은 redirect 는 보수적으로 포기.
        return httpGetText(absUrl(res.headers.location, url)).then(resolve);
      }
      if (res.statusCode < 200 || res.statusCode >= 300) {
        res.resume();
        return resolve({ ok: false });
      }
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => resolve({ ok: true, body: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('error', () => resolve({ ok: false }));
    req.on('timeout', () => { req.destroy(); resolve({ ok: false }); });
    req.end();
  });
}

async function fetchAndExtract(url) {
  const res = await httpGetText(url);
  if (!res.ok || !res.body) {
    return { url, status: 'FETCH_FAILED', cleanedText: '', extraAttachments: [] };
  }
  try {
    const { cleaned, extras } = extractCleanedAndAttachments(res.body, url);
    return {
      url,
      status: cleaned.length < 200 ? 'TOO_SHORT' : null,
      cleanedText: cleaned,
      extraAttachments: extras
    };
  } catch (e) {
    return { url, status: 'FETCH_FAILED', cleanedText: '', extraAttachments: [] };
  }
}

const p = $input.first().json;
const urls = selectUrls(p);

if (urls.length === 0) {
  return [{
    json: {
      ...p,
      _enrichUrl: null,
      _enrichUrls: [],
      _cleanedText: '',
      _extraAttachments: [],
      _enrichmentStatus: 'NO_LINK'
    }
  }];
}

const results = await Promise.all(urls.map(u => fetchAndExtract(u)));
const merged = mergeFetchResults(results);

return [{
  json: {
    ...p,
    _enrichUrl: urls[0],
    _enrichUrls: urls,
    _cleanedText: merged.cleanedText,
    _extraAttachments: merged.extraAttachments,
    _enrichmentStatus: merged.status
  }
}];
```

노드 `name` 도 `링크 fetch + 머지` 로 변경, `id` 는 `pick-link` 유지 (n8n 그래프 내 ID 충돌 방지). position 변경 불필요.

- [ ] **Step 3: `링크 존재`, `외부 페이지 fetch`, `boilerplate 제거 + 첨부 후보` 노드 삭제**

`n8n/workflows/youth-center-seoul.json` 의 `nodes` 배열에서 다음 객체 3개 통째로 삭제:
- `"id": "if-link"` (이름 `링크 존재`)
- `"id": "fetch-external-page"` (이름 `외부 페이지 fetch`)
- `"id": "cheerio-clean"` (이름 `boilerplate 제거 + 첨부 후보`)

또한 `enrichment skip: NO_LINK` 노드(`"id": "skip-no-link"`)도 삭제 — 새 mega-node 가 `NO_LINK` 상태를 직접 만들고, downstream `cleaned 통과 여부` IF 가 NO_LINK 도 비-empty 로 보아 skip 경로로 라우팅한다.

- [ ] **Step 4: connections 갱신**

`connections` 객체에서 다음 키 삭제:
- `"링크 존재"`
- `"외부 페이지 fetch"`
- `"boilerplate 제거 + 첨부 후보"`
- `"enrichment skip: NO_LINK"`

다음 키 교체:
```json
"링크 fetch + 머지": {
  "main": [
    [
      { "node": "cleaned 통과 여부", "type": "main", "index": 0 }
    ]
  ]
},
```

기존 `"링크 선택"` 키를 위 키로 rename. 다음 키의 들어오는 참조도 갱신: `"enrichment 여부"` 의 main[0][0].node 가 `"링크 선택"` → `"링크 fetch + 머지"` 로 바꾼다.

- [ ] **Step 5: `enrichment skip: cleaned` 의 `_enrichmentStatus` 처리 확인**

`"id": "skip-cleaned"` 의 jsCode 는 `p._enrichmentStatus || 'FETCH_FAILED'` 를 그대로 둔다. `NO_LINK` 가 들어오면 그대로 보존됨 — 변경 없음.

- [ ] **Step 6: JSON 유효성 검증**

```bash
node -e "JSON.parse(require('fs').readFileSync('n8n/workflows/youth-center-seoul.json', 'utf8'))"
```

Expected: 출력 없음, exit 0. SyntaxError 면 누락된 `,` 또는 따옴표 escape 점검.

- [ ] **Step 7: 노드 jsCode 와 enrich.mjs 알고리즘 일치 확인 (수동 비교)**

`enrich.mjs` 의 `selectUrls`, `mergeFetchResults` 함수 본문이 mega-node 의 같은 이름 함수 본문과 정확히 일치하는지 시각적으로 비교. 매개변수, 상수(`MAX_URLS`, `MAX_CLEANED_LEN`, `TEXT_SEPARATOR`), 반환 형태 모두 동일해야 한다.

- [ ] **Step 8: fixture 재실행 (회귀 확인)**

```bash
node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs
```

Expected: 전체 PASS, exit 0.

- [ ] **Step 9: 커밋**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): 온통청년 enrichment 멀티 reference URL 머지 mega-node"
```

---

## Phase B: promote mediaType 휴리스틱 강화

### Task 11: promote.mjs 함수 분리 (리팩토링 — 동작 변화 없음)

기존 `extractExt` 한 함수에서 mediaType 도출. 향후 텍스트/path 추론을 추가하기 위해 함수 분리.

**Files:**
- Modify: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`

- [ ] **Step 1: `promote.mjs` 의 mediaType 도출 부분을 분리**

기존:
```js
const ext = extractExt(ex.url);
if (!ext) continue;
const mediaType = EXT_TO_MEDIA_TYPE[ext];
if (!mediaType) continue;
```

변경 — 함수 분리:

```js
function mapExt(ext) {
  if (!ext) return null;
  return EXT_TO_MEDIA_TYPE[ext.toLowerCase()] || null;
}

function inferMediaType(item) {
  const fromUrl = mapExt(extractExt(item.url));
  if (fromUrl) return fromUrl;
  return null;
}
```

루프 안:
```js
const mediaType = inferMediaType(ex);
if (!mediaType) continue;
```

- [ ] **Step 2: 기존 fixture 회귀 확인**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 기존 6개 케이스 전체 PASS, exit 0.

- [ ] **Step 3: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/promote.mjs
git commit -m "refactor(n8n): promote.mjs mediaType 추론 함수 분리"
```

---

### Task 12: case-name-has-pdf-suffix (URL ext 없음 + name 끝이 .pdf → application/pdf)

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-has-pdf-suffix.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-has-pdf-suffix.expected.json`
- Modify: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/100", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-100",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/100",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서_2026.pdf", "url": "https://gov.example.com/afile/fileDownload/abc123" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "source": { "url": "https://example.com/p/100", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-100",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [
      { "name": "신청서_2026.pdf", "url": "https://gov.example.com/afile/fileDownload/abc123", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/100",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서_2026.pdf", "url": "https://gov.example.com/afile/fileDownload/abc123" }
      ]
    }
  }
}
```

- [ ] **Step 3: verify 실행 → FAIL 확인**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: `FAIL  case-name-has-pdf-suffix`. URL 확장자가 없어 현재 추론 실패 → skip.

- [ ] **Step 4: `extractExtFromText` + `inferMediaType` 업데이트**

`promote.mjs` 에 함수 추가:

```js
const TEXT_EXT_PATTERN = /\.(pdf|hwpx?|docx?|xlsx?)\b/i;
const PAREN_EXT_PATTERN = /[\(\[]\s*(pdf|hwpx?|docx?|xlsx?)\s*[\)\]]/i;

function extractExtFromText(text) {
  if (typeof text !== 'string' || text.length === 0) return null;
  const m1 = text.match(TEXT_EXT_PATTERN);
  if (m1) return m1[1].toLowerCase();
  const m2 = text.match(PAREN_EXT_PATTERN);
  if (m2) return m2[1].toLowerCase();
  return null;
}
```

`inferMediaType` 교체:

```js
function inferMediaType(item) {
  const fromUrl = mapExt(extractExt(item.url));
  if (fromUrl) return fromUrl;
  const fromName = mapExt(extractExtFromText(item.name));
  if (fromName) return fromName;
  return null;
}
```

- [ ] **Step 5: verify 재실행 → 전체 PASS**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 7개 PASS.

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "test(n8n): promote name 텍스트 확장자 추론 + case-name-has-pdf-suffix"
```

---

### Task 13: case-name-paren-keyword (name "(HWP) 신청 양식" → application/x-hwp)

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-paren-keyword.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-paren-keyword.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/101", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-101",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/101",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "(HWP) 신청 양식", "url": "https://gov.example.com/common/fileDown.do?key=42" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "source": { "url": "https://example.com/p/101", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-101",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [
      { "name": "(HWP) 신청 양식", "url": "https://gov.example.com/common/fileDown.do?key=42", "mediaType": "application/x-hwp" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/101",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "(HWP) 신청 양식", "url": "https://gov.example.com/common/fileDown.do?key=42" }
      ]
    }
  }
}
```

- [ ] **Step 3: verify 실행 → PASS**

Task 12 에서 추가한 `PAREN_EXT_PATTERN` 가 이미 처리.

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 PASS.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/cases/
git commit -m "test(n8n): promote case-name-paren-keyword 추가"
```

---

### Task 14: case-img-alt-fallback (boilerplate 노드 폴백 name="attachment.pdf" → application/pdf)

boilerplate 제거 노드(mega-node 안 `extractCleanedAndAttachments`)는 link text 가 부족하면 `attachment.{imgAlt}` 폴백을 사용한다. 그 이름이 promote 노드까지 흘러 왔을 때 mediaType 을 잡아야 한다.

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-img-alt-fallback.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-img-alt-fallback.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/102", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-102",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/102",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "attachment.pdf", "url": "https://www.k-startup.go.kr/afile/fileDownload/abc" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "source": { "url": "https://example.com/p/102", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-102",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [
      { "name": "attachment.pdf", "url": "https://www.k-startup.go.kr/afile/fileDownload/abc", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/102",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "attachment.pdf", "url": "https://www.k-startup.go.kr/afile/fileDownload/abc" }
      ]
    }
  }
}
```

- [ ] **Step 3: verify 실행 → PASS**

`TEXT_EXT_PATTERN` 가 `attachment.pdf` 끝 `.pdf` 를 매칭.

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 PASS.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/cases/
git commit -m "test(n8n): promote case-img-alt-fallback 추가"
```

---

### Task 15: case-path-keyword (URL `.../downloadPdf?id=123` → application/pdf)

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-path-keyword.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-path-keyword.expected.json`
- Modify: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/103", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-103",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/103",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "다운로드", "url": "https://gov.example.com/board/downloadPdf?id=42" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "source": { "url": "https://example.com/p/103", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-103",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [
      { "name": "다운로드", "url": "https://gov.example.com/board/downloadPdf?id=42", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/103",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "다운로드", "url": "https://gov.example.com/board/downloadPdf?id=42" }
      ]
    }
  }
}
```

- [ ] **Step 3: verify 실행 → FAIL**

URL 확장자 없음, name "다운로드" 에는 확장자 없음 → 현재까지는 skip → FAIL.

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: `FAIL  case-path-keyword`.

- [ ] **Step 4: `extractExtFromPath` 추가 + `inferMediaType` 갱신**

`promote.mjs` 에 함수 추가:

```js
const PATH_EXT_PATTERN = /(?:^|[^a-zA-Z])(pdf|hwpx?|docx?|xlsx?)(?:$|[^a-zA-Z])/i;

function extractExtFromPath(url) {
  if (typeof url !== 'string') return null;
  const path = url.split('?')[0].split('#')[0].toLowerCase();
  const m = path.match(PATH_EXT_PATTERN);
  return m ? m[1].toLowerCase() : null;
}
```

> 정규식 비고: `\b` 가 ASCII 단어 경계라 한글 텍스트와 결합 시 `gov.example.com` 의 `pdf` 가 path 매칭에 false positive 를 만들 수 있다. 우리는 path 만 매칭(쿼리/host 제외) 하고 단어 경계 대신 명시적 비-알파벳 경계로 강제해 false positive 를 줄인다.

`inferMediaType` 교체:

```js
function inferMediaType(item) {
  const fromUrl = mapExt(extractExt(item.url));
  if (fromUrl) return fromUrl;
  const fromName = mapExt(extractExtFromText(item.name));
  if (fromName) return fromName;
  const fromPath = mapExt(extractExtFromPath(item.url));
  if (fromPath) return fromPath;
  return null;
}
```

- [ ] **Step 5: verify 재실행 → 전체 PASS**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 PASS (특히 기존 `case-mixed-extensions`, `case-non-string-url`, `case-empty-enrichment` 회귀 없음).

- [ ] **Step 6: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "test(n8n): promote URL path 키워드 mediaType 추론 + case-path-keyword"
```

---

### Task 16: case-no-signal (URL `fileDownload/abc123` + name "다운로드" → 미승격)

신호가 없으면 skip 유지. false positive 가드 케이스.

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-no-signal.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-no-signal.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/104", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-104",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/104",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "다운로드", "url": "https://www.k-startup.go.kr/afile/fileDownload/abc123" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성** (attachments 비어있음)

```json
{
  "source": { "url": "https://example.com/p/104", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-104",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/104",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "다운로드", "url": "https://www.k-startup.go.kr/afile/fileDownload/abc123" }
      ]
    }
  }
}
```

URL 확장자 없음, name 확장자 없음, path 에 `download` 만 있고 `pdf/hwp/...` 없음 → skip.

- [ ] **Step 3: verify 실행 → PASS**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 PASS.

> Step 3 가 FAIL 이면 path 추론이 너무 공격적. `PATH_EXT_PATTERN` 의 비-알파벳 경계 강제를 재점검.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/cases/
git commit -m "test(n8n): promote case-no-signal 추가 (신호 부재 시 skip)"
```

---

### Task 17: case-conflicting-signals (URL `.pdf` + name "(HWP) 양식" → URL 우선 application/pdf)

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-conflicting-signals.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-conflicting-signals.expected.json`

- [ ] **Step 1: input.json 작성**

```json
{
  "source": { "url": "https://example.com/p/105", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-105",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/105",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "(HWP) 양식", "url": "https://gov.example.com/file/sample.pdf" }
      ]
    }
  }
}
```

- [ ] **Step 2: expected.json 작성**

```json
{
  "source": { "url": "https://example.com/p/105", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-19T00:00:00" },
  "rawData": {
    "externalId": "P-105",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "복지",
    "region": "서울특별시",
    "attachments": [
      { "name": "(HWP) 양식", "url": "https://gov.example.com/file/sample.pdf", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/105",
      "fetchedAt": "2026-05-19T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "(HWP) 양식", "url": "https://gov.example.com/file/sample.pdf" }
      ]
    }
  }
}
```

URL `.pdf` 가 첫 번째 신호 — `inferMediaType` 의 first-wins 우선순위에 의해 `application/pdf`.

- [ ] **Step 3: verify 실행 → PASS**

Run: `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
Expected: 전체 PASS.

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/cases/
git commit -m "test(n8n): promote case-conflicting-signals 추가 (URL 우선)"
```

---

### Task 18: youth-center-seoul.json "attachments 승격" 노드 jsCode 미러

`promote.mjs` 변경분을 n8n 노드 jsCode 에 동기화한다.

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`

- [ ] **Step 1: 현재 `attachments 승격` 노드 jsCode 위치 확인**

`"id": "promote-attachments"` 노드의 `parameters.jsCode` (앞 절에서 본 line 167 부근). 동기화 코멘트 첫 줄은 유지.

- [ ] **Step 2: jsCode 교체**

기존 `extractExt` 단일 함수를 다음 패치된 셋으로 교체한다 (n8n 노드 안에서도 `mapExt`, `extractExtFromText`, `extractExtFromPath`, `inferMediaType` 함수를 정의):

```js
// 동기화 책임: n8n/workflows/__fixtures__/promote-attachments/promote.mjs 와
// 동일 알고리즘이어야 한다. 한 곳을 수정하면 다른 곳도 같은 변경을 반영해야 한다.
const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
};

const TEXT_EXT_PATTERN = /\.(pdf|hwpx?|docx?|xlsx?)\b/i;
const PAREN_EXT_PATTERN = /[\(\[]\s*(pdf|hwpx?|docx?|xlsx?)\s*[\)\]]/i;
const PATH_EXT_PATTERN = /(?:^|[^a-zA-Z])(pdf|hwpx?|docx?|xlsx?)(?:$|[^a-zA-Z])/i;

function extractExt(url) {
  if (typeof url !== 'string') return null;
  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();
  const dotIdx = cleaned.lastIndexOf('.');
  if (dotIdx === -1) return null;
  return cleaned.slice(dotIdx + 1);
}

function mapExt(ext) {
  if (!ext) return null;
  return EXT_TO_MEDIA_TYPE[ext.toLowerCase()] || null;
}

function extractExtFromText(text) {
  if (typeof text !== 'string' || text.length === 0) return null;
  const m1 = text.match(TEXT_EXT_PATTERN);
  if (m1) return m1[1].toLowerCase();
  const m2 = text.match(PAREN_EXT_PATTERN);
  if (m2) return m2[1].toLowerCase();
  return null;
}

function extractExtFromPath(url) {
  if (typeof url !== 'string') return null;
  const path = url.split('?')[0].split('#')[0].toLowerCase();
  const m = path.match(PATH_EXT_PATTERN);
  return m ? m[1].toLowerCase() : null;
}

function inferMediaType(item) {
  const fromUrl = mapExt(extractExt(item.url));
  if (fromUrl) return fromUrl;
  const fromName = mapExt(extractExtFromText(item.name));
  if (fromName) return fromName;
  const fromPath = mapExt(extractExtFromPath(item.url));
  if (fromPath) return fromPath;
  return null;
}

const input = $input.first().json;
const enrichment = input?.rawData?.enrichment;
const extras = enrichment?.extraAttachments;
if (!Array.isArray(extras) || extras.length === 0) {
  return [{ json: input }];
}
const attachments = Array.isArray(input.rawData.attachments) ? input.rawData.attachments : [];
const existingUrls = new Set(
  attachments
    .map(a => (typeof a.url === 'string' ? a.url.toLowerCase() : null))
    .filter(Boolean)
);
const merged = [...attachments];
for (const ex of extras) {
  if (!ex || typeof ex.url !== 'string') continue;
  const mediaType = inferMediaType(ex);
  if (!mediaType) continue;
  const key = ex.url.toLowerCase();
  if (existingUrls.has(key)) continue;
  merged.push({ name: ex.name, url: ex.url, mediaType });
  existingUrls.add(key);
}
return [{ json: { ...input, rawData: { ...input.rawData, attachments: merged } } }];
```

- [ ] **Step 3: JSON 유효성 검증**

```bash
node -e "JSON.parse(require('fs').readFileSync('n8n/workflows/youth-center-seoul.json', 'utf8'))"
```

Expected: exit 0.

- [ ] **Step 4: fixture 재실행 (회귀 + 신규 케이스 전체 통과 확인)**

```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs && \
  node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs
```

Expected: 두 fixture 모두 전체 PASS.

- [ ] **Step 5: 커밋**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): attachments 승격 노드 mediaType 추론 휴리스틱 강화 (name/path)"
```

---

## 최종 단계

### Task 19: 빌드/검증 종합 확인 + 커밋 정리

- [ ] **Step 1: 두 fixture 검증 일괄 실행**

```bash
node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs && \
  node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: 두 명령 모두 exit 0.

- [ ] **Step 2: youth-center-seoul.json 마지막 JSON parse 확인**

```bash
node -e "JSON.parse(require('fs').readFileSync('n8n/workflows/youth-center-seoul.json', 'utf8'))"
```

Expected: exit 0.

- [ ] **Step 3: git 상태 확인**

```bash
git status
git log --oneline origin/main..HEAD
```

Expected: 워킹 트리 clean, 새 커밋 12~14건 (각 task 별 1건).

- [ ] **Step 4: TODO_ 스펙 파일 rename**

작업이 완료되어 스펙은 보존하되 `TODO_` 접두사를 제거한다 (DONE_ 으로 prefix). spec 두 파일 모두:

```bash
git mv docs/superpowers/specs/TODO_2026-05-19-enrichment-multi-reference-url.md \
       docs/superpowers/specs/DONE_2026-05-19-enrichment-multi-reference-url.md
git mv docs/superpowers/specs/TODO_2026-05-19-promote-attachment-mediatype-heuristic.md \
       docs/superpowers/specs/DONE_2026-05-19-promote-attachment-mediatype-heuristic.md
```

plan 파일은 `DONE_` prefix 를 자체적으로 붙인다:

```bash
git mv docs/superpowers/plans/2026-05-19-enrichment-multi-ref-and-mediatype.md \
       docs/superpowers/plans/DONE_2026-05-19-enrichment-multi-ref-and-mediatype.md
```

- [ ] **Step 5: rename 커밋**

```bash
git commit -m "docs(spec): 온통청년 enrichment 멀티 URL + mediaType 작업 완료 표기"
```

- [ ] **Step 6: PR 생성 (create-pr 스킬 사용)**

`/cr` 셀프 리뷰가 `create-pr` 스킬 안에 통합되어 있으므로 별도 호출 불필요. 사용자에게 `create-pr` 스킬 실행 요청.

PR 제목: `feat(n8n): 온통청년 enrichment 멀티 reference URL 머지 + promote mediaType 추론 강화`

PR 본문 골자:
- 멀티 URL 머지: aplyUrlAddr/refUrlAddr1/refUrlAddr2 모두 fetch → cleanedText 머지 + extraAttachments union.
- mediaType 휴리스틱: URL 확장자 외 name 텍스트/path 키워드 추가, URL → name → path 우선순위.
- fixture 검증: enrichment-merge 7개 케이스 + promote-attachments 12개 케이스 통과.

---

## 자가 점검 (Self-review)

### 1. Spec 커버리지

#### `TODO_2026-05-19-enrichment-multi-reference-url.md`

| Spec 항목 | 구현 위치 |
|---|---|
| 2. 다중 URL fetch + 머지 + dedup | Task 2~9 (fixture), Task 10 (n8n mega-node) |
| 4. 후보 A 아키텍처 (선택) | Task 10 — splitInBatches 대신 single mega-node, Architecture 섹션에서 이유 명시 |
| 5.1 "링크 선택" 노드 출력 형식 변경 | Task 10 — `_enrichUrl` (대표 1개) + `_enrichUrls` (전체) |
| 5.2 "enrichment 머지" 노드 동작 | Task 10 의 mega-node 안 `mergeFetchResults` 호출 — 별도 노드 없음 |
| 6. 에러 처리 (NO_LINK/FETCH_FAILED/TOO_SHORT/혼합) | Task 4, 5, 6, 9 |
| 6. URL 정규화 dedup | Task 7 (`normalizeUrlKey`) |
| 6. URL 수 cap (최대 3개) | Task 2 (`MAX_URLS = 3`) |
| 7.1 fixture 케이스 6개 | Task 2~9 — 7개 (no-url 보너스 포함) |
| 7.2 백엔드 회귀 (PolicyEnrichment 모델 미변경) | 구조 변경 없음 — sourceUrl/extraAttachments/status/cleanedText 모두 기존 필드 그대로 |
| 7.3 단대단 검증 | PR 머지 후 운영 절차 (README 에 명시) |

#### `TODO_2026-05-19-promote-attachment-mediatype-heuristic.md`

| Spec 항목 | 구현 위치 |
|---|---|
| 4. 단계 1 — 텍스트/path 휴리스틱 | Task 11~18 |
| 4.2 추론 우선순위 (URL → name → path) | Task 11, 15 |
| 4.4 `extractExtFromText` 패턴 | Task 12 (`TEXT_EXT_PATTERN`, `PAREN_EXT_PATTERN`) |
| 4.5 `extractExtFromPath` 패턴 (단어 경계) | Task 15 (`PATH_EXT_PATTERN`) — `\b` 대신 비-알파벳 경계로 한글 컨텍스트 false positive 완화 |
| 5. 단계 2 (HEAD 요청) | **비범위** (spec 명시 — 별도 spec) |
| 6. 에러/우선순위 처리 | Task 17 (conflicting signals) |
| 7. fixture 케이스 6개 | Task 12~17 — 6개 (case-img-alt-fallback 포함, spec 의 `case-img-alt` 와 등가) |
| 8. 운영/모니터링 | PR 본문에 명시 (분포 모니터링은 후속 작업) |

### 2. Placeholder 스캔

- "TBD", "TODO", "fill in", "implement later", "Add appropriate", "Similar to Task" 패턴 부재 ✓
- 모든 코드 블록은 실행 가능한 완전한 코드 ✓
- "유사한 케이스를 작성" 같은 모호한 지시 없음 — 모든 케이스의 input/expected JSON 전체 제공 ✓

### 3. 타입 일관성

- `enrich.mjs` 의 `selectUrls(p)` 반환: `string[]` ↔ n8n mega-node 도 동일 ✓
- `mergeFetchResults(results)` 반환: `{ cleanedText: string, extraAttachments: Array<{name,url}>, status: null | 'FETCH_FAILED' | 'TOO_SHORT' }` — 일관 ✓
- `inferMediaType(item)` 반환: `string | null` — `promote.mjs` 와 n8n 노드 동일 시그니처 ✓
- 상수명: `MAX_URLS`, `MAX_CLEANED_LEN`, `TEXT_SEPARATOR` — 모든 location 에서 동일 이름 ✓
- n8n 노드 출력 키: `_enrichUrl` (대표 1개), `_enrichUrls` (전체), `_cleanedText`, `_extraAttachments`, `_enrichmentStatus` — 다운스트림 `enrichment 객체 조립` 노드(line 547)의 참조 (`upstream._enrichUrl`, `upstream._extraAttachments`, `upstream._cleanedText`) 와 일치 ✓
- `_enrichmentStatus` 값 도메인: `null | 'NO_LINK' | 'FETCH_FAILED' | 'TOO_SHORT'`. `cleaned 통과 여부` IF (line 497) 는 비-empty 면 skip 으로 라우팅 — `null` 만 LLM 추출 진입. ✓
