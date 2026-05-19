# 온통청년 enrichment 첨부 승격 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년 워크플로우(`n8n/workflows/youth-center-seoul.json`)의 `정책 → IngestPolicyRequest 변환` 뒤에 `attachments 승격` Code 노드를 끼워 넣어, `rawData.enrichment.extraAttachments` 중 확장자로 mediaType 추론이 가능한 항목을 `rawData.attachments` 에 머지한다. 결과적으로 백엔드의 기존 첨부 다운로드/추출/임베딩/인용 파이프라인을 그대로 태운다.

**Architecture:** n8n Code 노드 sandbox 는 단위 테스트 인프라가 없으므로, 동일 로직을 `promote.mjs` 라는 독립 ES module 로도 두고 Node.js 로 픽스처 6 케이스를 자동 검증한다(`verify.mjs`). 노드 jsCode 는 이 모듈을 그대로 인라인화한 형태로 둔다. README 에 두 곳을 함께 수정해야 한다는 동기화 책임을 명시한다.

**Tech Stack:** n8n Code 노드(JavaScript ES2022), Node.js ≥ 18 (`node --version` 으로 확인 가능), 픽스처는 plain JSON.

**Spec:** `docs/superpowers/specs/2026-05-16-youth-center-attachment-promotion-design.md`

---

## 사전 확인

- [ ] **Step 0-1: 작업 디렉토리 / Node 버전 점검**

Run:
```bash
node --version
ls n8n/workflows/youth-center-seoul.json
```

Expected: Node 버전이 v18 이상이고, 워크플로우 파일이 존재함.

---

### Task 1: 픽스처 인프라 + 첫 케이스(no-op)

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/README.md`
- Create: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`
- Create: `n8n/workflows/__fixtures__/promote-attachments/verify.mjs`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-empty-enrichment.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-empty-enrichment.expected.json`

- [ ] **Step 1-1: README 작성**

Create `n8n/workflows/__fixtures__/promote-attachments/README.md`:

````markdown
# promote-attachments fixtures

`youth-center-seoul.json` 의 `attachments 승격` 노드가 만족해야 하는 입출력 계약을 픽스처로 고정한다.

## 구조

- `promote.mjs` — n8n 노드의 jsCode 와 **동일 알고리즘** 을 담은 독립 ES module. 이 파일은 단위 검증 전용이며, 실제 데이터 흐름에는 사용되지 않는다.
- `verify.mjs` — `cases/*.input.json` 을 읽어 `promote()` 를 적용하고 `cases/*.expected.json` 과 비교한다.
- `cases/case-*.input.json` / `cases/case-*.expected.json` — 케이스별 입력/기대 출력.

## 실행

```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

전체 케이스 PASS 면 exit 0, 한 건이라도 FAIL 이면 exit 1.

## 동기화 책임

**⚠ `promote.mjs` 와 `youth-center-seoul.json` 의 `attachments 승격` 노드 jsCode 는 항상 동일 로직이어야 한다.** 한쪽을 수정하면 다른쪽도 같은 변경을 반영해야 한다. 노드 jsCode 가 변경됐는데 픽스처 검증이 깨지면, 의도된 변경이라면 픽스처를 갱신하고, 아니면 노드 jsCode 를 되돌려라.
````

- [ ] **Step 1-2: promote.mjs 스켈레톤 작성 (no-op)**

Create `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`:

```js
// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

export function promote(input) {
  return input;
}
```

- [ ] **Step 1-3: verify.mjs 작성**

Create `n8n/workflows/__fixtures__/promote-attachments/verify.mjs`:

```js
import { promote } from './promote.mjs';
import { readFile, readdir } from 'node:fs/promises';

const CASES_DIR = new URL('./cases/', import.meta.url);

function deepEqual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

const entries = await readdir(CASES_DIR);
const inputs = entries.filter(e => e.endsWith('.input.json')).sort();

let failed = 0;
for (const inputFile of inputs) {
  const name = inputFile.replace('.input.json', '');
  const expectedFile = `${name}.expected.json`;
  const input = JSON.parse(await readFile(new URL(inputFile, CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, CASES_DIR), 'utf8'));
  const actual = promote(input);
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

- [ ] **Step 1-4: 첫 케이스 — case-empty-enrichment (no-op)**

이 케이스는 `enrichment` 가 null 일 때 promote 가 입력을 그대로 통과시킨다는 계약을 고정한다.

Create `n8n/workflows/__fixtures__/promote-attachments/cases/case-empty-enrichment.input.json`:

```json
{
  "source": {
    "url": "https://example.com/p/1",
    "type": "YOUTH_CENTER",
    "fetchedAt": "2026-05-16T00:00:00"
  },
  "rawData": {
    "externalId": "P-1",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": null
  }
}
```

Create `n8n/workflows/__fixtures__/promote-attachments/cases/case-empty-enrichment.expected.json` (입력과 동일):

```json
{
  "source": {
    "url": "https://example.com/p/1",
    "type": "YOUTH_CENTER",
    "fetchedAt": "2026-05-16T00:00:00"
  },
  "rawData": {
    "externalId": "P-1",
    "title": "샘플",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": null
  }
}
```

- [ ] **Step 1-5: 검증 실행**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected:
```
PASS  case-empty-enrichment

All 1 case(s) passed
```

스켈레톤 `promote()` 는 입력을 그대로 반환하므로 통과.

- [ ] **Step 1-6: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "test(n8n): promote-attachments 픽스처 인프라 + no-op 케이스"
```

---

### Task 2: 확장자→mediaType 매핑 + append

**Files:**
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-mixed-extensions.input.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-mixed-extensions.expected.json`
- Modify: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`

- [ ] **Step 2-1: 실패 케이스 — case-mixed-extensions**

pdf / hwp / 확장자 없는 download.do 가 섞인 입력 → pdf, hwp 두 건만 승격되고 download.do 는 enrichment 에만 잔존.

Create `cases/case-mixed-extensions.input.json`:

```json
{
  "source": { "url": "https://example.com/p/2", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-2",
    "title": "샘플 2",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/2",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서.pdf", "url": "https://gov.example.com/file/sin.pdf" },
        { "name": "양식.hwp", "url": "https://gov.example.com/file/yang.hwp" },
        { "name": "안내", "url": "https://gov.example.com/notice/download.do?id=42" }
      ]
    }
  }
}
```

Create `cases/case-mixed-extensions.expected.json`:

```json
{
  "source": { "url": "https://example.com/p/2", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-2",
    "title": "샘플 2",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "신청서.pdf", "url": "https://gov.example.com/file/sin.pdf", "mediaType": "application/pdf" },
      { "name": "양식.hwp", "url": "https://gov.example.com/file/yang.hwp", "mediaType": "application/x-hwp" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/2",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서.pdf", "url": "https://gov.example.com/file/sin.pdf" },
        { "name": "양식.hwp", "url": "https://gov.example.com/file/yang.hwp" },
        { "name": "안내", "url": "https://gov.example.com/notice/download.do?id=42" }
      ]
    }
  }
}
```

- [ ] **Step 2-2: 검증 실행해서 실패 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: `FAIL  case-mixed-extensions` 가 보이고 exit code 1. case-empty-enrichment 는 여전히 PASS.

- [ ] **Step 2-3: promote.mjs 구현**

Replace `n8n/workflows/__fixtures__/promote-attachments/promote.mjs` 전체:

```js
// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

export function promote(input) {
  const enrichment = input?.rawData?.enrichment;
  const extras = enrichment?.extraAttachments;
  if (!Array.isArray(extras) || extras.length === 0) {
    return input;
  }
  const attachments = Array.isArray(input.rawData.attachments)
    ? input.rawData.attachments
    : [];
  const merged = [...attachments];
  for (const ex of extras) {
    const dotIdx = ex.url.lastIndexOf('.');
    if (dotIdx === -1) continue;
    const ext = ex.url.slice(dotIdx + 1);
    const mediaType = EXT_TO_MEDIA_TYPE[ext];
    if (!mediaType) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
  }
  return { ...input, rawData: { ...input.rawData, attachments: merged } };
}
```

- [ ] **Step 2-4: 검증 실행해서 통과 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected:
```
PASS  case-empty-enrichment
PASS  case-mixed-extensions

All 2 case(s) passed
```

- [ ] **Step 2-5: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "feat(n8n): promote-attachments 확장자→mediaType 매핑 추가"
```

---

### Task 3: URL 정규화 (쿼리 파라미터 / 프래그먼트 / 대소문자)

**Files:**
- Create: `cases/case-mixed-case-ext.input.json` / `.expected.json`
- Create: `cases/case-query-suffix.input.json` / `.expected.json`
- Modify: `promote.mjs`

- [ ] **Step 3-1: 실패 케이스 — case-mixed-case-ext**

URL 이 `.PDF` 대문자여도 정상 인식해야 한다. 원본 url 자체는 변형 없이 그대로 보존.

Create `cases/case-mixed-case-ext.input.json`:

```json
{
  "source": { "url": "https://example.com/p/3", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-3",
    "title": "샘플 3",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/3",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "Manual", "url": "https://gov.example.com/files/Manual.PDF" }
      ]
    }
  }
}
```

Create `cases/case-mixed-case-ext.expected.json`:

```json
{
  "source": { "url": "https://example.com/p/3", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-3",
    "title": "샘플 3",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "Manual", "url": "https://gov.example.com/files/Manual.PDF", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/3",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "Manual", "url": "https://gov.example.com/files/Manual.PDF" }
      ]
    }
  }
}
```

- [ ] **Step 3-2: 실패 케이스 — case-query-suffix**

쿼리스트링/프래그먼트 뒤에 확장자가 가려진 형태(`...pdf?ts=123`, `...hwp#preview`) 도 인식. 원본 url 보존.

Create `cases/case-query-suffix.input.json`:

```json
{
  "source": { "url": "https://example.com/p/4", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-4",
    "title": "샘플 4",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/4",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "보고서", "url": "https://gov.example.com/files/report.pdf?ts=123" },
        { "name": "양식", "url": "https://gov.example.com/files/form.hwp#preview" }
      ]
    }
  }
}
```

Create `cases/case-query-suffix.expected.json`:

```json
{
  "source": { "url": "https://example.com/p/4", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-4",
    "title": "샘플 4",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "보고서", "url": "https://gov.example.com/files/report.pdf?ts=123", "mediaType": "application/pdf" },
      { "name": "양식", "url": "https://gov.example.com/files/form.hwp#preview", "mediaType": "application/x-hwp" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/4",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "보고서", "url": "https://gov.example.com/files/report.pdf?ts=123" },
        { "name": "양식", "url": "https://gov.example.com/files/form.hwp#preview" }
      ]
    }
  }
}
```

- [ ] **Step 3-3: 검증 실행해서 실패 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: `FAIL  case-mixed-case-ext` 와 `FAIL  case-query-suffix` 노출, exit 1.

- [ ] **Step 3-4: promote.mjs 에 URL 정규화 추가**

Replace `promote.mjs` 전체:

```js
// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

function extractExt(url) {
  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();
  const dotIdx = cleaned.lastIndexOf('.');
  if (dotIdx === -1) return null;
  return cleaned.slice(dotIdx + 1);
}

export function promote(input) {
  const enrichment = input?.rawData?.enrichment;
  const extras = enrichment?.extraAttachments;
  if (!Array.isArray(extras) || extras.length === 0) {
    return input;
  }
  const attachments = Array.isArray(input.rawData.attachments)
    ? input.rawData.attachments
    : [];
  const merged = [...attachments];
  for (const ex of extras) {
    const ext = extractExt(ex.url);
    if (!ext) continue;
    const mediaType = EXT_TO_MEDIA_TYPE[ext];
    if (!mediaType) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
  }
  return { ...input, rawData: { ...input.rawData, attachments: merged } };
}
```

- [ ] **Step 3-5: 검증 실행해서 4 케이스 모두 통과 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected:
```
PASS  case-empty-enrichment
PASS  case-mixed-case-ext
PASS  case-mixed-extensions
PASS  case-query-suffix

All 4 case(s) passed
```

- [ ] **Step 3-6: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "feat(n8n): promote-attachments URL 정규화(쿼리/프래그먼트/대소문자) 추가"
```

---

### Task 4: 동일 URL dedup

**Files:**
- Create: `cases/case-duplicate-url.input.json` / `.expected.json`
- Modify: `promote.mjs`

- [ ] **Step 4-1: 실패 케이스 — case-duplicate-url**

본 `attachments` 에 이미 같은 URL 이 있을 때 머지 skip. 현재 온통청년 transform 노드가 `attachments: []` 로 시작하지만, 향후 API가 첨부 메타를 제공할 가능성에 대비한 idempotency 계약.

Create `cases/case-duplicate-url.input.json`:

```json
{
  "source": { "url": "https://example.com/p/5", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-5",
    "title": "샘플 5",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "신청서.pdf", "url": "https://gov.example.com/file/sin.pdf", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/5",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서", "url": "https://gov.example.com/file/sin.pdf" },
        { "name": "양식", "url": "https://gov.example.com/file/yang.hwp" }
      ]
    }
  }
}
```

Create `cases/case-duplicate-url.expected.json`:

```json
{
  "source": { "url": "https://example.com/p/5", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-5",
    "title": "샘플 5",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "신청서.pdf", "url": "https://gov.example.com/file/sin.pdf", "mediaType": "application/pdf" },
      { "name": "양식", "url": "https://gov.example.com/file/yang.hwp", "mediaType": "application/x-hwp" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/5",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "신청서", "url": "https://gov.example.com/file/sin.pdf" },
        { "name": "양식", "url": "https://gov.example.com/file/yang.hwp" }
      ]
    }
  }
}
```

- [ ] **Step 4-2: 검증 실행해서 실패 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: `FAIL  case-duplicate-url` — `sin.pdf` 가 중복으로 두 번 들어감.

- [ ] **Step 4-3: promote.mjs 에 dedup 추가**

Replace `promote.mjs` 전체:

```js
// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

function extractExt(url) {
  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();
  const dotIdx = cleaned.lastIndexOf('.');
  if (dotIdx === -1) return null;
  return cleaned.slice(dotIdx + 1);
}

export function promote(input) {
  const enrichment = input?.rawData?.enrichment;
  const extras = enrichment?.extraAttachments;
  if (!Array.isArray(extras) || extras.length === 0) {
    return input;
  }
  const attachments = Array.isArray(input.rawData.attachments)
    ? input.rawData.attachments
    : [];
  const existingUrls = new Set(
    attachments
      .map(a => (typeof a.url === 'string' ? a.url.toLowerCase() : null))
      .filter(Boolean)
  );
  const merged = [...attachments];
  for (const ex of extras) {
    const ext = extractExt(ex.url);
    if (!ext) continue;
    const mediaType = EXT_TO_MEDIA_TYPE[ext];
    if (!mediaType) continue;
    const key = ex.url.toLowerCase();
    if (existingUrls.has(key)) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
    existingUrls.add(key);
  }
  return { ...input, rawData: { ...input.rawData, attachments: merged } };
}
```

- [ ] **Step 4-4: 검증 실행해서 5 케이스 통과 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected:
```
PASS  case-duplicate-url
PASS  case-empty-enrichment
PASS  case-mixed-case-ext
PASS  case-mixed-extensions
PASS  case-query-suffix

All 5 case(s) passed
```

- [ ] **Step 4-5: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "feat(n8n): promote-attachments 동일 URL dedup 추가"
```

---

### Task 5: url 가드 (non-string skip)

**Files:**
- Create: `cases/case-non-string-url.input.json` / `.expected.json`
- Modify: `promote.mjs`

- [ ] **Step 5-1: 실패 케이스 — case-non-string-url**

`extraAttachments[i].url` 이 null/숫자/객체 등 비-string 이면 그 항목만 skip, 나머지는 정상 처리.

현재 `promote()` 는 `ex.url.split(...)` 에서 TypeError 가 나면서 verify 가 비정상 종료된다. 가드 추가 필요.

Create `cases/case-non-string-url.input.json`:

```json
{
  "source": { "url": "https://example.com/p/6", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-6",
    "title": "샘플 6",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/6",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "깨진 항목", "url": null },
        { "name": "정상", "url": "https://gov.example.com/file/ok.pdf" }
      ]
    }
  }
}
```

Create `cases/case-non-string-url.expected.json`:

```json
{
  "source": { "url": "https://example.com/p/6", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-16T00:00:00" },
  "rawData": {
    "externalId": "P-6",
    "title": "샘플 6",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "정상", "url": "https://gov.example.com/file/ok.pdf", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/6",
      "fetchedAt": "2026-05-16T00:00:00",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.9,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "깨진 항목", "url": null },
        { "name": "정상", "url": "https://gov.example.com/file/ok.pdf" }
      ]
    }
  }
}
```

- [ ] **Step 5-2: 검증 실행해서 실패(or 예외) 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: `case-non-string-url` 에서 TypeError 가 나거나 FAIL 표시. exit code 1.

- [ ] **Step 5-3: promote.mjs 에 가드 추가**

`promote.mjs` 의 for 루프 시작부에 url 가드 추가. for 루프를 다음과 같이 교체:

```js
  for (const ex of extras) {
    if (!ex || typeof ex.url !== 'string') continue;
    const ext = extractExt(ex.url);
    if (!ext) continue;
    const mediaType = EXT_TO_MEDIA_TYPE[ext];
    if (!mediaType) continue;
    const key = ex.url.toLowerCase();
    if (existingUrls.has(key)) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
    existingUrls.add(key);
  }
```

- [ ] **Step 5-4: 검증 실행해서 6 케이스 모두 통과 확인**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected:
```
PASS  case-duplicate-url
PASS  case-empty-enrichment
PASS  case-mixed-case-ext
PASS  case-mixed-extensions
PASS  case-non-string-url
PASS  case-query-suffix

All 6 case(s) passed
```

- [ ] **Step 5-5: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "feat(n8n): promote-attachments non-string url 가드 추가"
```

---

### Task 6: n8n 워크플로우에 `attachments 승격` 노드 통합

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json`
  - `nodes` 배열에 `attachments 승격` 노드 추가
  - `connections` 에서 `정책 → IngestPolicyRequest 변환` → `백엔드 API 전송` 사이에 신규 노드 삽입

- [ ] **Step 6-1: 워크플로우 JSON 의 현재 노드 ID/이름 충돌 점검**

Run:
```bash
grep -n '"id":\|"name":' n8n/workflows/youth-center-seoul.json | grep -E 'promote-attachments|attachments 승격' || echo "no conflict"
```

Expected: `no conflict` 출력. 신규 ID `promote-attachments`, 이름 `attachments 승격` 이 기존과 겹치지 않음을 보장.

- [ ] **Step 6-2: `nodes` 배열에 신규 노드 추가**

n8n 워크플로우의 `nodes` 배열은 노드 정의 순서가 동작에 영향을 주지 않는다 (실제 실행 순서는 `connections` 가 결정). 따라서 `nodes` 배열의 어디든 추가 가능하지만, 가독성을 위해 `"정책 → IngestPolicyRequest 변환"` 노드(id: `transform`, position `[1320, 100]`) 객체 바로 뒤에 삽입한다 — 이 노드는 파일 앞쪽(line 138 근처)에 있다. position 은 transform 과 다음 노드 사이 빈 자리로 `[1540, 100]`.

jsCode 는 `promote.mjs` 의 본문과 동일 알고리즘. `export` 와 함수 wrapper 만 n8n 노드 시그니처에 맞춰 인라인화.

```json
    {
      "parameters": {
        "jsCode": "// 동기화 책임: n8n/workflows/__fixtures__/promote-attachments/promote.mjs 와\n// 동일 알고리즘이어야 한다. 한 곳을 수정하면 다른 곳도 같은 변경을 반영해야 한다.\nconst EXT_TO_MEDIA_TYPE = {\n  pdf: 'application/pdf',\n  hwp: 'application/x-hwp',\n  hwpx: 'application/x-hwp',\n  doc: 'application/msword',\n  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',\n  xls: 'application/vnd.ms-excel',\n  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'\n};\n\nfunction extractExt(url) {\n  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();\n  const dotIdx = cleaned.lastIndexOf('.');\n  if (dotIdx === -1) return null;\n  return cleaned.slice(dotIdx + 1);\n}\n\nconst input = $input.first().json;\nconst enrichment = input?.rawData?.enrichment;\nconst extras = enrichment?.extraAttachments;\nif (!Array.isArray(extras) || extras.length === 0) {\n  return [{ json: input }];\n}\nconst attachments = Array.isArray(input.rawData.attachments) ? input.rawData.attachments : [];\nconst existingUrls = new Set(\n  attachments\n    .map(a => (typeof a.url === 'string' ? a.url.toLowerCase() : null))\n    .filter(Boolean)\n);\nconst merged = [...attachments];\nfor (const ex of extras) {\n  if (!ex || typeof ex.url !== 'string') continue;\n  const ext = extractExt(ex.url);\n  if (!ext) continue;\n  const mediaType = EXT_TO_MEDIA_TYPE[ext];\n  if (!mediaType) continue;\n  const key = ex.url.toLowerCase();\n  if (existingUrls.has(key)) continue;\n  merged.push({ name: ex.name, url: ex.url, mediaType });\n  existingUrls.add(key);\n}\nreturn [{ json: { ...input, rawData: { ...input.rawData, attachments: merged } } }];\n"
      },
      "id": "promote-attachments",
      "name": "attachments 승격",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [
        1540,
        100
      ]
    },
```

> **참고:** JSON 안의 jsCode 는 한 줄 문자열이므로 `\n` 이스케이프 그대로 유지. 위 블록을 통째로 복사. nodes 배열의 마지막 노드 객체 뒤에 추가하는 경우, 직전 객체의 닫는 `}` 다음에 `,` 를 넣고 위 객체를 붙여라.

- [ ] **Step 6-3: `connections` 재배선**

`"정책 → IngestPolicyRequest 변환"` 의 연결 대상을 `"백엔드 API 전송"` 에서 `"attachments 승격"` 으로 바꾸고, `"attachments 승격"` 의 연결 대상을 `"백엔드 API 전송"` 으로 새로 추가한다.

**Before** (`youth-center-seoul.json` 의 connections 섹션 내):

```json
    "정책 → IngestPolicyRequest 변환": {
      "main": [
        [
          {
            "node": "백엔드 API 전송",
            "type": "main",
            "index": 0
          }
        ]
      ]
    },
```

**After** (해당 블록을 다음으로 교체):

```json
    "정책 → IngestPolicyRequest 변환": {
      "main": [
        [
          {
            "node": "attachments 승격",
            "type": "main",
            "index": 0
          }
        ]
      ]
    },
    "attachments 승격": {
      "main": [
        [
          {
            "node": "백엔드 API 전송",
            "type": "main",
            "index": 0
          }
        ]
      ]
    },
```

- [ ] **Step 6-4: JSON 유효성 점검**

Run:
```bash
node -e "JSON.parse(require('fs').readFileSync('n8n/workflows/youth-center-seoul.json', 'utf8')); console.log('valid JSON')"
```

Expected: `valid JSON`.

- [ ] **Step 6-5: 노드/연결 추가 확인**

Run:
```bash
grep -n '"attachments 승격"\|"promote-attachments"' n8n/workflows/youth-center-seoul.json
```

Expected: 노드 정의 1건 (`"id": "promote-attachments"`, `"name": "attachments 승격"`), connections 키 1건(`"attachments 승격": {`), 연결 대상 1건 (`"node": "attachments 승격"`). 총 4개 이상의 매치.

- [ ] **Step 6-6: 픽스처 회귀 재실행 (변경 없는 부분이지만 sanity check)**

Run:
```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

Expected: `All 6 case(s) passed`.

- [ ] **Step 6-7: 커밋**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "feat(n8n): youth-center 워크플로우에 attachments 승격 노드 추가"
```

---

### Task 7: 운영 절차 메모 + spec 후속 후보 정리

**Files:**
- Modify: `n8n/workflows/__fixtures__/promote-attachments/README.md`

- [ ] **Step 7-1: README 에 운영 절차 섹션 추가**

`n8n/workflows/__fixtures__/promote-attachments/README.md` 끝에 다음 섹션을 append:

```markdown

## 운영 절차

워크플로우 JSON 변경 후 실제 적용:

1. n8n UI 에서 기존 `Youth Center Seoul Crawl` 워크플로우를 비활성화
2. `youth-center-seoul.json` 을 import (덮어쓰기)
3. 워크플로우 활성화
4. 단일 정책으로 수동 실행 → 다음 DB 상태 확인:
   - `policy_attachment` 신규 row 들, URL 도메인이 외부 정책 안내 페이지
   - `extraction_status = EXTRACTED` 도달
   - `policy_document` 에 `source = ATTACHMENT` 청크 존재

## 후속 작업 후보 (spec 10장 참조)

- 외부 도메인 화이트리스트 / SSRF 가드
- 첨부 url 단위 fileHash 캐시 (재다운로드 비용 최적화)
- 다른 enrichment 소스 도입 시 promote 노드 패턴 재사용
- 정책당 첨부 개수 cap
- n8n 노드 자동화 테스트 (현재는 promote.mjs 와 jsCode 의 수동 동기화)
```

- [ ] **Step 7-2: 커밋**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/README.md
git commit -m "docs(n8n): promote-attachments 운영 절차/후속 후보 메모"
```

---

## 완료 체크

- [ ] `node n8n/workflows/__fixtures__/promote-attachments/verify.mjs` 통과 (6 cases)
- [ ] `n8n/workflows/youth-center-seoul.json` 이 유효 JSON 이고 `attachments 승격` 노드가 transform 과 백엔드 전송 사이에 있음
- [ ] 백엔드 코드/DB 변경 0
- [ ] 모든 커밋이 Conventional Commits 형식
