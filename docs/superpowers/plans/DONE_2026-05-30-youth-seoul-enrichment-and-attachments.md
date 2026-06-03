# 청년서울(youth-seoul) 파이프라인 보강 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** youth-seoul-crawl 워크플로우에 (1) 카테고리 2개 루프, (2) 본문 정제 보강, (3) 참고사이트 enrichment + 첨부 추출 + 필터링을 추가한다. 백엔드 코드 변경 없음. 어드민 5단계 status 는 점검만.

**Architecture:** n8n 워크플로우 JSON 의 인라인 코드는 `n8n/workflows/__fixtures__/` 의 ES 모듈과 1:1 동기화하는 기존 규약을 그대로 따른다. 새 정제 로직(`parse-body.mjs`) fixture 를 만들고 TDD 로 검증한 뒤 워크플로우 JSON 의 인라인 jsCode 에 같은 알고리즘을 복사한다. 기존 `enrichment-merge` 와 `promote-attachments` fixture 는 시그니처를 확장(refUrls[]) 하고 키워드 필터를 추가한 뒤 양 워크플로우 (`youth-seoul-crawl.json`, `youth-center-seoul.json`) 인라인 코드와 sync 한다.

**Tech Stack:** n8n (workflow JSON + n8n-nodes-base.code), Node.js ES modules (fixture 테스트), cheerio (HTML 파싱, n8n 컨테이너에 설치됨), 정규식 기반 본문 정제

**Spec:** `docs/superpowers/specs/2026-05-30-youth-seoul-enrichment-and-attachments-design.md`

---

## 작업 순서 개요

| Task | 범위 | 파일 |
|---|---|---|
| 1 | `parse-body.mjs` fixture 모듈 신설 (TDD) | `__fixtures__/youth-seoul-parse-body/` |
| 2 | `enrichment-merge` 의 `selectUrls` 시그니처 확장 | `__fixtures__/enrichment-merge/enrich.mjs` |
| 3 | `promote-attachments` 키워드 필터 추가 | `__fixtures__/promote-attachments/promote.mjs` |
| 4 | `youth-center-seoul.json` inline 코드 sync (키워드 필터) | `n8n/workflows/youth-center-seoul.json` |
| 5 | `youth-seoul-crawl.json` 카테고리 init + loop 노드 | `n8n/workflows/youth-seoul-crawl.json` |
| 6 | `youth-seoul-crawl.json` 목록/상세 URL 파라미터화 | (same) |
| 7 | `youth-seoul-crawl.json` parse-detail 인라인 코드 갱신 | (same) |
| 8 | `youth-seoul-crawl.json` pick-link 노드 추가 | (same) |
| 9 | `youth-seoul-crawl.json` enrichment-meta 노드 추가 | (same) |
| 10 | `youth-seoul-crawl.json` promote-attachments 노드 추가 | (same) |
| 11 | 검증 runbook 작성 (백엔드 점검 항목) | `docs/runbooks/youth-seoul-pipeline-verification.md` |

---

## Task 1: `parse-body.mjs` fixture 모듈 신설 (TDD)

**목표:** `extractByTh` (멀티라인 주석 제거 + 영문 태그만 + dash 정제), `extractRefUrls`, `extractSelfAttachments` 3개 함수를 fixture 로 작성하고 케이스 단위 테스트를 돌린다.

**Files:**
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/parse-body.mjs`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/verify.mjs`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/README.md`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th/case-multiline-comment.{input.html,expected.json,meta.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th/case-text-angle-brackets.{input.html,expected.json,meta.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th/case-dash-only-line.{input.html,expected.json,meta.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th/case-dash-arrow.{input.html,expected.json,meta.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-ref-urls/case-four-sections.{input.html,expected.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-ref-urls/case-empty.{input.html,expected.json}`
- Create: `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-self-attachments/case-mixed-doc-and-image.{input.html,expected.json}`

- [ ] **Step 1.1: 디렉토리 생성 + 빈 모듈 스켈레톤**

Run:
```bash
mkdir -p n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th
mkdir -p n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-ref-urls
mkdir -p n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-self-attachments
```

Create `n8n/workflows/__fixtures__/youth-seoul-parse-body/parse-body.mjs`:
```js
// 동기화 책임: 이 파일과 youth-seoul-crawl.json 의 "상세 데이터 파싱" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

export function extractByTh(html, thText) {
  return null; // intentionally empty for TDD red
}

export function extractRefUrls(html) {
  return [];
}

export function extractSelfAttachments(html) {
  return [];
}
```

- [ ] **Step 1.2: verify.mjs 작성 (다중 디렉토리 케이스 러너)**

Create `n8n/workflows/__fixtures__/youth-seoul-parse-body/verify.mjs`:
```js
import { extractByTh, extractRefUrls, extractSelfAttachments } from './parse-body.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const ROOT = new URL('./', import.meta.url);

async function loadCases(dir) {
  const url = new URL(`${dir}/`, ROOT);
  const entries = await readdir(url);
  return entries.filter(e => e.endsWith('.input.html')).sort()
    .map(input => ({
      name: input.replace('.input.html', ''),
      input,
      expected: input.replace('.input.html', '.expected.json'),
      meta: input.replace('.input.html', '.meta.json'),
      url,
    }));
}

function eq(a, b) {
  try { deepStrictEqual(a, b); return true; } catch { return false; }
}

async function readJson(url) {
  try { return JSON.parse(await readFile(url, 'utf8')); } catch { return null; }
}

let failed = 0;
let passed = 0;

async function runDir(dir, fn) {
  const cases = await loadCases(dir);
  for (const c of cases) {
    const html = await readFile(new URL(c.input, c.url), 'utf8');
    const expected = JSON.parse(await readFile(new URL(c.expected, c.url), 'utf8'));
    const meta = await readJson(new URL(c.meta, c.url));
    const actual = fn(html, meta);
    if (eq(actual, expected)) {
      console.log(`PASS  ${dir}/${c.name}`);
      passed++;
    } else {
      console.log(`FAIL  ${dir}/${c.name}`);
      console.log(`  expected: ${JSON.stringify(expected)}`);
      console.log(`  actual:   ${JSON.stringify(actual)}`);
      failed++;
    }
  }
}

await runDir('cases-extract-by-th', (html, meta) => extractByTh(html, meta?.thText || ''));
await runDir('cases-ref-urls', html => extractRefUrls(html));
await runDir('cases-self-attachments', html => extractSelfAttachments(html));

if (failed > 0) {
  console.error(`\n${failed} case(s) failed (passed ${passed})`);
  process.exit(1);
}
console.log(`\nAll ${passed} case(s) passed`);
```

- [ ] **Step 1.3: 첫 번째 fail 케이스 작성 — 멀티라인 주석 제거**

Create `n8n/workflows/__fixtures__/youth-seoul-parse-body/cases-extract-by-th/case-multiline-comment.input.html`:
```html
<table><tbody><tr>
<th scope="row">지원 내용</th>
<td colspan="3">
<!--
  ----- 사업개요 -----
-->
ㅇ 지원금액 : 최대 40만원<br>ㅇ 지원내용 : 실비
</td>
</tr></tbody></table>
```

Create `case-multiline-comment.expected.json`:
```json
"ㅇ 지원금액 : 최대 40만원\nㅇ 지원내용 : 실비"
```

Create `case-multiline-comment.meta.json`:
```json
{ "thText": "지원 내용" }
```

- [ ] **Step 1.4: verify 실행 → fail 확인**

Run: `cd n8n/workflows/__fixtures__/youth-seoul-parse-body && node verify.mjs`
Expected: `FAIL cases-extract-by-th/case-multiline-comment` (actual is `null`)

- [ ] **Step 1.5: `extractByTh` 구현 (멀티라인 주석 + 영문 태그만 + dash 정제)**

Edit `parse-body.mjs`:
```js
export function extractByTh(html, thText) {
  if (!html || !thText) return null;
  const escapedTh = thText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(
    '<th[^>]*>' + escapedTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>',
    'i'
  );
  const match = html.match(regex);
  if (!match) return '';
  return match[1]
    .replace(/<!--[\s\S]*?-->/g, '')              // 멀티라인 HTML 주석 제거
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/?[a-zA-Z][^>]*>/g, '')           // 영문 시작 태그만 — 본문 <민원인...> 보존
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\t/g, '')
    .replace(/^[  ]*-{3,}[  ]*$/gm, '') // dash 만 있는 줄 제거
    .replace(/-{4,}>/g, '')                       // dash 화살표 잔재
    .replace(/\n\s*\n/g, '\n')
    .trim();
}
```

- [ ] **Step 1.6: verify 실행 → pass 확인**

Run: `cd n8n/workflows/__fixtures__/youth-seoul-parse-body && node verify.mjs`
Expected: `PASS cases-extract-by-th/case-multiline-comment`

- [ ] **Step 1.7: 텍스트 보존 케이스 추가**

Create `cases-extract-by-th/case-text-angle-brackets.input.html`:
```html
<tr>
<th scope="row">제출서류</th>
<td colspan="3">
<!-- <ul> -->
&lt;민원인이 제출해야 하는 서류&gt;<br>○ 신청서, 통장 사본
</td>
</tr>
```

`case-text-angle-brackets.expected.json`:
```json
"<민원인이 제출해야 하는 서류>\n○ 신청서, 통장 사본"
```

`case-text-angle-brackets.meta.json`:
```json
{ "thText": "제출서류" }
```

Run: `node verify.mjs` → expect both PASS.

- [ ] **Step 1.8: dash-only line / dash-arrow 케이스 추가**

Create `cases-extract-by-th/case-dash-only-line.input.html`:
```html
<tr><th scope="row">신청절차</th><td>
○ 1단계: 온라인 신청
---------------
○ 2단계: 서류 심사
</td></tr>
```

`case-dash-only-line.expected.json`:
```json
"○ 1단계: 온라인 신청\n○ 2단계: 서류 심사"
```

`case-dash-only-line.meta.json`:
```json
{ "thText": "신청절차" }
```

Create `cases-extract-by-th/case-dash-arrow.input.html`:
```html
<tr><th scope="row">기타사항</th><td>
신청 -----&gt; 심사 -----&gt; 발표
</td></tr>
```

`case-dash-arrow.expected.json`:
```json
"신청  심사  발표"
```

`case-dash-arrow.meta.json`:
```json
{ "thText": "기타사항" }
```

Run: `node verify.mjs` → all 4 cases PASS.

- [ ] **Step 1.9: `extractRefUrls` 케이스 작성 + 구현**

Create `cases-ref-urls/case-four-sections.input.html`:
```html
<table>
<tr><th>관련 사이트</th><td><a href="https://www.gov.kr/portal/a">link1</a></td></tr>
<tr><th>신청 사이트</th><td><a href="https://www.gov.kr/portal/b">link2</a></td></tr>
<tr><th>참고 사이트 Ⅰ</th><td><a href="https://www.gov.kr/portal/a">dup</a></td></tr>
<tr><th>참고 사이트 Ⅱ</th><td><a href="https://www.gov.kr/portal/c">link3</a></td></tr>
</table>
```

`case-four-sections.expected.json`:
```json
["https://www.gov.kr/portal/a", "https://www.gov.kr/portal/b", "https://www.gov.kr/portal/c"]
```

Create `cases-ref-urls/case-empty.input.html`:
```html
<table>
<tr><th>관련 사이트</th><td><!-- empty --></td></tr>
<tr><th>참고 사이트 Ⅰ</th><td></td></tr>
</table>
```

`case-empty.expected.json`:
```json
[]
```

Run: `node verify.mjs` → ref-urls FAIL (current impl returns `[]` for both, but `case-four-sections` expects 3 urls)

Edit `parse-body.mjs`:
```js
const REF_SECTIONS = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];

export function extractRefUrls(html) {
  if (!html) return [];
  const urls = [];
  const seen = new Set();
  for (const th of REF_SECTIONS) {
    const escTh = th.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp('<th[^>]*>' + escTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>', 'i');
    const m = html.match(re);
    if (!m) continue;
    const hrefRe = /href="(https?:\/\/[^"]+)"/gi;
    let hm;
    while ((hm = hrefRe.exec(m[1])) !== null) {
      const u = hm[1];
      if (seen.has(u)) continue;
      seen.add(u);
      urls.push(u);
    }
  }
  return urls.slice(0, 3);
}
```

Run: `node verify.mjs` → ref-urls cases PASS.

- [ ] **Step 1.10: `extractSelfAttachments` 케이스 작성 + 구현**

Create `cases-self-attachments/case-mixed-doc-and-image.input.html`:
```html
<div>
<a href="/board/download.do?fileId=1" title="첨부">2026년 공고문.pdf</a>
<a href="https://youth.seoul.go.kr/files/notice.hwp">신청서.hwp</a>
<a href="/images/logo.png">로고</a>
<a href="https://example.com/intro.docx">사업 안내서</a>
</div>
```

`case-mixed-doc-and-image.expected.json`:
```json
[
  { "name": "2026년 공고문.pdf", "url": "https://youth.seoul.go.kr/board/download.do?fileId=1" },
  { "name": "신청서.hwp", "url": "https://youth.seoul.go.kr/files/notice.hwp" },
  { "name": "사업 안내서", "url": "https://example.com/intro.docx" }
]
```

Edit `parse-body.mjs`:
```js
const ATTACHMENT_EXT_PATTERN = /\.(pdf|hwp|hwpx|docx?|xlsx?|zip)(\?|$|#)/i;
const BASE_ORIGIN = 'https://youth.seoul.go.kr';

export function extractSelfAttachments(html) {
  if (!html) return [];
  const out = [];
  const seen = new Set();
  const linkRe = /<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let lm;
  while ((lm = linkRe.exec(html)) !== null) {
    const href = lm[1];
    if (!ATTACHMENT_EXT_PATTERN.test(href)) continue;
    const abs = /^https?:\/\//i.test(href)
      ? href
      : BASE_ORIGIN + (href.startsWith('/') ? href : '/' + href);
    if (seen.has(abs)) continue;
    seen.add(abs);
    const text = lm[2].replace(/<[^>]+>/g, '').trim().slice(0, 200);
    out.push({ name: text || abs.split('/').pop(), url: abs });
  }
  return out;
}
```

Run: `node verify.mjs` → all PASS.

- [ ] **Step 1.11: README.md 작성 (동기화 책임 명시)**

Create `n8n/workflows/__fixtures__/youth-seoul-parse-body/README.md`:
```markdown
# youth-seoul-parse-body fixture

`youth-seoul-crawl.json` 의 "상세 데이터 파싱" 노드 jsCode 와 동일 알고리즘.

## sync 규칙
- `parse-body.mjs` 의 `extractByTh / extractRefUrls / extractSelfAttachments` 가 truth.
- 워크플로우 JSON 의 인라인 함수를 수정하면 여기도 같이 수정해서 verify 가 통과해야 한다.

## 실행
```bash
cd n8n/workflows/__fixtures__/youth-seoul-parse-body
node verify.mjs
```
```

- [ ] **Step 1.12: Commit**

```bash
git add n8n/workflows/__fixtures__/youth-seoul-parse-body/
git commit -m "test(n8n): youth-seoul-parse-body fixture (정제·refUrls·selfAttachments)"
```

---

## Task 2: `enrichment-merge` `selectUrls` 시그니처 확장

**목표:** 청년서울이 `refUrls[]` 배열로 URL 을 명시 전달할 수 있게 한다. 기존 온통청년의 키 기반 호출은 유지.

**Files:**
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs:37-51`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-explicit-refurls.input.json`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases/case-explicit-refurls.expected.json`

- [ ] **Step 2.1: 신규 케이스 작성 (refUrls[] 입력)**

Inspect existing case format first:
```bash
ls n8n/workflows/__fixtures__/enrichment-merge/cases/ | head
cat n8n/workflows/__fixtures__/enrichment-merge/cases/$(ls n8n/workflows/__fixtures__/enrichment-merge/cases/ | grep selectUrls | head -1)
```

If no `selectUrls` cases exist yet, locate where `verify.mjs` consumes them and write the input matching that contract. The `selectUrls` function takes `policy` and returns `string[]`.

Create `cases/case-explicit-refurls.input.json` (match existing test harness shape — likely `{ "fn": "selectUrls", "input": {...} }`):
```json
{
  "fn": "selectUrls",
  "input": {
    "refUrls": ["https://a.example.com", "https://b.example.com"]
  }
}
```

`case-explicit-refurls.expected.json`:
```json
["https://a.example.com", "https://b.example.com"]
```

Note: If existing verify.mjs uses a different fixture shape (e.g. directly passes JSON to `selectUrls`), match that shape instead. Check `verify.mjs` before writing the case.

- [ ] **Step 2.2: verify 실행 → fail 확인**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: `FAIL ... case-explicit-refurls` (current `selectUrls` only looks at `aplyUrlAddr/refUrlAddr1/refUrlAddr2`)

- [ ] **Step 2.3: `selectUrls` 시그니처 확장**

Edit `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs:37-51`:
```js
export function selectUrls(policy) {
  // 1) 명시 refUrls[] 가 있으면 우선 사용 (youth-seoul-crawl)
  if (policy && Array.isArray(policy.refUrls)) {
    const seen = new Set();
    const out = [];
    for (const u of policy.refUrls) {
      if (typeof u !== 'string') continue;
      const trimmed = u.trim();
      if (!trimmed) continue;
      const key = normalizeUrlKey(trimmed);
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(trimmed);
      if (out.length >= MAX_URLS) break;
    }
    return out;
  }
  // 2) fallback: 온통청년 키 기반
  const candidates = [policy?.aplyUrlAddr, policy?.refUrlAddr1, policy?.refUrlAddr2]
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

- [ ] **Step 2.4: verify 실행 → 모든 케이스 pass**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: All PASS (기존 온통청년 케이스 회귀 없음 + 새 case-explicit-refurls PASS)

- [ ] **Step 2.5: Commit**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "feat(n8n): enrichment-merge selectUrls 가 refUrls[] 입력 우선 지원"
```

---

## Task 3: `promote-attachments` 키워드 필터 추가

**목표:** 확장자 화이트리스트 + 파일명 키워드(화이트/블랙) 둘 다 적용. 보수적 fallback (이름 길이 ≥ 5자 또는 키워드 없음) 으로 false-negative 최소화.

**Files:**
- Modify: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-logo-excluded.{input,expected}.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-keyword-passes.{input,expected}.json`
- Create: `n8n/workflows/__fixtures__/promote-attachments/cases/case-name-short-no-keyword-excluded.{input,expected}.json`

- [ ] **Step 3.1: case-name-logo-excluded 작성**

Create `cases/case-name-logo-excluded.input.json`:
```json
{
  "source": { "url": "https://example.com/p/3", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-30T00:00:00" },
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
      "fetchedAt": "2026-05-30T00:00:00",
      "extractor": "regex",
      "confidence": null,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "공고문.pdf", "url": "https://ex.com/file/notice.pdf" },
        { "name": "기관 로고.pdf", "url": "https://ex.com/file/logo.pdf" }
      ]
    }
  }
}
```

`case-name-logo-excluded.expected.json`:
```json
{
  "source": { "url": "https://example.com/p/3", "type": "YOUTH_CENTER", "fetchedAt": "2026-05-30T00:00:00" },
  "rawData": {
    "externalId": "P-3",
    "title": "샘플 3",
    "summary": "s",
    "body": "b",
    "category": "교육",
    "region": "서울특별시",
    "attachments": [
      { "name": "공고문.pdf", "url": "https://ex.com/file/notice.pdf", "mediaType": "application/pdf" }
    ],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/3",
      "fetchedAt": "2026-05-30T00:00:00",
      "extractor": "regex",
      "confidence": null,
      "status": "OK",
      "sections": null,
      "extraAttachments": [
        { "name": "공고문.pdf", "url": "https://ex.com/file/notice.pdf" },
        { "name": "기관 로고.pdf", "url": "https://ex.com/file/logo.pdf" }
      ]
    }
  }
}
```

- [ ] **Step 3.2: case-name-keyword-passes 작성**

Create `cases/case-name-keyword-passes.input.json`:
```json
{
  "source": { "url": "https://example.com/p/4", "type": "YOUTH_SEOUL_CRAWL", "fetchedAt": "2026-05-30T00:00:00" },
  "rawData": {
    "externalId": "P-4", "title": "샘플 4", "summary": "s", "body": "b",
    "category": "복지", "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/4",
      "fetchedAt": "2026-05-30T00:00:00",
      "extractor": "regex", "confidence": null, "status": "OK", "sections": null,
      "extraAttachments": [
        { "name": "모집 요강.hwp", "url": "https://ex.com/file/qual.hwp" },
        { "name": "FAQ.pdf", "url": "https://ex.com/file/faq.pdf" }
      ]
    }
  }
}
```

`case-name-keyword-passes.expected.json`: 두 첨부 모두 attachments[] 로 승격된 결과 (위 case-name-logo-excluded 와 같은 구조).

- [ ] **Step 3.3: case-name-short-no-keyword-excluded 작성**

Create `cases/case-name-short-no-keyword-excluded.input.json`:
```json
{
  "source": { "url": "https://example.com/p/5", "type": "YOUTH_SEOUL_CRAWL", "fetchedAt": "2026-05-30T00:00:00" },
  "rawData": {
    "externalId": "P-5", "title": "샘플 5", "summary": "s", "body": "b",
    "category": "복지", "region": "서울특별시",
    "attachments": [],
    "enrichment": {
      "sourceUrl": "https://gov.example.com/p/5",
      "fetchedAt": "2026-05-30T00:00:00",
      "extractor": "regex", "confidence": null, "status": "OK", "sections": null,
      "extraAttachments": [
        { "name": "a.pdf", "url": "https://ex.com/file/a.pdf" }
      ]
    }
  }
}
```

`case-name-short-no-keyword-excluded.expected.json`: `attachments` 가 빈 배열로 유지된 상태 (이름이 5자 미만이고 키워드도 없으면 제외).

- [ ] **Step 3.4: verify 실행 → FAIL 확인**

Run: `cd n8n/workflows/__fixtures__/promote-attachments && node verify.mjs`
Expected:
- `FAIL case-name-logo-excluded` (현재는 `로고` 첨부가 통과)
- `FAIL case-name-short-no-keyword-excluded` (현재는 `a.pdf` 가 통과)
- `PASS case-name-keyword-passes` (정상 통과 케이스)

- [ ] **Step 3.5: `isInformationalName` 추가 + `promote` 적용**

Edit `n8n/workflows/__fixtures__/promote-attachments/promote.mjs` (after `inferMediaType` function, before `promote`):
```js
const NAME_WHITELIST_PATTERN = /(공고|안내|모집|요강|신청서|계획서|FAQ|Q&A|가이드|설명|일정|참가|운영|평가|선정|채용|지원)/i;
const NAME_BLACKLIST_PATTERN = /(로고|배너|아이콘|썸네일|포스터|광고|favicon)/i;

function isInformationalName(name) {
  if (typeof name !== 'string' || !name) return true;     // 이름이 없으면 통과 (확장자 매핑이 이미 OK)
  if (NAME_BLACKLIST_PATTERN.test(name)) return false;
  if (NAME_WHITELIST_PATTERN.test(name)) return true;
  return name.length >= 5;                                 // 보수적 fallback
}
```

Modify `promote` loop (insert after `mediaType` check):
```js
const mediaType = inferMediaType(ex);
if (!mediaType) continue;
if (!isInformationalName(ex.name)) continue;              // ← 신규
const key = ex.url.toLowerCase();
// ... 이하 동일
```

- [ ] **Step 3.6: verify 실행 → 전부 PASS**

Run: `cd n8n/workflows/__fixtures__/promote-attachments && node verify.mjs`
Expected: All cases PASS. 기존 케이스(case-mixed-extensions 등) 회귀 확인 — `신청서.pdf`, `양식.hwp` 는 키워드(`신청서`) 포함 또는 5자 이상이라 통과해야 한다.

회귀 발생 시: 어떤 기존 케이스가 깨지는지 확인하고, fallback 조건을 조정한다. 예: `양식.hwp` 가 5자 미만이라면 fallback 기준을 4자로 낮추거나, 케이스 파일명을 더 표현력 있게 변경 (단 케이스 의도 보존).

- [ ] **Step 3.7: Commit**

```bash
git add n8n/workflows/__fixtures__/promote-attachments/
git commit -m "feat(n8n): promote-attachments 에 파일명 키워드 필터 추가"
```

---

## Task 4: `youth-center-seoul.json` inline 코드 sync

**목표:** fixture 의 키워드 필터를 온통청년 워크플로우 인라인 jsCode 에도 그대로 반영. fixture 와 인라인이 동일 알고리즘 유지.

**Files:**
- Modify: `n8n/workflows/youth-center-seoul.json:167` (the `attachments 승격` node `jsCode` field)

- [ ] **Step 4.1: 워크플로우 JSON 의 promote-attachments 노드 찾기**

Run:
```bash
grep -n '"attachments 승격"' n8n/workflows/youth-center-seoul.json
```

Confirm the surrounding `jsCode` is the one matching `promote.mjs` in Task 3.

- [ ] **Step 4.2: jsCode 에 `NAME_WHITELIST_PATTERN`, `NAME_BLACKLIST_PATTERN`, `isInformationalName` 삽입**

The node `jsCode` is a single JSON-escaped string. Edit it by replacing the section right after the `inferMediaType` function definition to add the 3 new declarations and the filter call inside the promote loop. Use a string-aware edit (search for `function inferMediaType` and insert the new block before `const input = $input.first().json;`).

Pattern to insert (with proper JSON escaping `\n` → `\\n`):
```
const NAME_WHITELIST_PATTERN = /(공고|안내|모집|요강|신청서|계획서|FAQ|Q&A|가이드|설명|일정|참가|운영|평가|선정|채용|지원)/i;
const NAME_BLACKLIST_PATTERN = /(로고|배너|아이콘|썸네일|포스터|광고|favicon)/i;
function isInformationalName(name) {
  if (typeof name !== 'string' || !name) return true;
  if (NAME_BLACKLIST_PATTERN.test(name)) return false;
  if (NAME_WHITELIST_PATTERN.test(name)) return true;
  return name.length >= 5;
}
```

And in the loop, insert after `if (!mediaType) continue;`:
```
if (!isInformationalName(ex.name)) continue;
```

Recommended approach: open the file with a text editor, locate the exact section, and add the lines while preserving JSON string escaping (`\n` is two chars `\` + `n` inside the JSON string).

- [ ] **Step 4.3: JSON 유효성 확인**

Run:
```bash
jq -e '.nodes | length' n8n/workflows/youth-center-seoul.json
```
Expected: number printed (not an error). If `jq` fails → restore from `git diff` and try again.

- [ ] **Step 4.4: Commit**

```bash
git add n8n/workflows/youth-center-seoul.json
git commit -m "chore(n8n): youth-center-seoul promote-attachments 키워드 필터 sync"
```

---

## Task 5: `youth-seoul-crawl.json` 카테고리 init + loop

**목표:** schedule-trigger → `init-category` 노드 → `category-loop` (splitInBatches) → 기존 페이지 초기화 흐름 진입. `init-category` 가 2개 카테고리 메타를 발산한다.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json`

먼저 백업:
```bash
cp n8n/workflows/youth-seoul-crawl.json /tmp/youth-seoul-crawl.json.bak
```

- [ ] **Step 5.1: `init-category` 노드 정의**

Insert as a new node in the `nodes` array (place between `schedule-trigger` and `init-page`):
```json
{
  "parameters": {
    "jsCode": "return [\n  { json: { catKey: '2309150002', catPath: 'plcyInfo',      catLabel: '서울 자체' } },\n  { json: { catKey: '2309160001', catPath: 'youthPlcyInfo', catLabel: '중앙정부/타지역' } }\n];"
  },
  "id": "init-category",
  "name": "카테고리 초기화",
  "type": "n8n-nodes-base.code",
  "typeVersion": 2,
  "position": [110, 0]
}
```

- [ ] **Step 5.2: `category-loop` 노드 정의 (splitInBatches batchSize=1)**

Insert another new node:
```json
{
  "parameters": {
    "batchSize": 1,
    "options": {}
  },
  "id": "category-loop",
  "name": "카테고리별 순차 처리",
  "type": "n8n-nodes-base.splitInBatches",
  "typeVersion": 3,
  "position": [220, 0]
}
```

- [ ] **Step 5.3: connections 재배선**

Update the `connections` object:

Replace this:
```
"매일 새벽 3시 실행": { "main": [[{ "node": "페이지 초기화", "type": "main", "index": 0 }]] }
```

With:
```
"매일 새벽 3시 실행": { "main": [[{ "node": "카테고리 초기화", "type": "main", "index": 0 }]] },
"카테고리 초기화": { "main": [[{ "node": "카테고리별 순차 처리", "type": "main", "index": 0 }]] },
"카테고리별 순차 처리": {
  "main": [
    [],
    [{ "node": "페이지 초기화", "type": "main", "index": 0 }]
  ]
}
```

(Note: splitInBatches uses `main[0]` for "done" branch and `main[1]` for "loop body".)

And change the end-of-category branch — after `"크롤링 완료"` should loop back to `카테고리별 순차 처리` to advance to next category. Replace:
```
"다음 페이지 존재?": {
  "main": [
    [{ "node": "다음 페이지 이동", "type": "main", "index": 0 }],
    [{ "node": "크롤링 완료", "type": "main", "index": 0 }]
  ]
}
```

With:
```
"다음 페이지 존재?": {
  "main": [
    [{ "node": "다음 페이지 이동", "type": "main", "index": 0 }],
    [{ "node": "카테고리별 순차 처리", "type": "main", "index": 0 }]
  ]
}
```

And keep `"크롤링 완료"` reachable from `"카테고리별 순차 처리"` `main[0]` (done branch). Update:
```
"카테고리별 순차 처리": {
  "main": [
    [{ "node": "크롤링 완료", "type": "main", "index": 0 }],
    [{ "node": "페이지 초기화", "type": "main", "index": 0 }]
  ]
}
```

- [ ] **Step 5.4: JSON 유효성**

```bash
jq -e '.nodes | length' n8n/workflows/youth-seoul-crawl.json
jq -e '.connections | keys' n8n/workflows/youth-seoul-crawl.json
```
Expected: 카테고리 노드 2개 추가로 `nodes` 개수가 기존 +2. 모든 connection key 가 nodes 의 `name` 과 일치하는지 시각 확인.

- [ ] **Step 5.5: Commit**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul 카테고리 2개 루프 추가 (서울 자체 + 중앙정부/타지역)"
```

---

## Task 6: youth-seoul-crawl 목록/상세 URL 카테고리 파라미터화

**목표:** 하드코딩된 `key=2309150002` 와 `plcyInfo` path 를 카테고리 컨텍스트에서 가져온 값으로 치환.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json` 의 `목록 페이지 요청`, `상세 페이지 요청` 노드 URL

- [ ] **Step 6.1: `목록 페이지 요청` URL 치환**

Replace:
```
https://youth.seoul.go.kr/infoData/plcyInfo/ctList.do?key=2309150002&tabKind=002&pageIndex={{ $json.pageIndex }}&orderBy=regYmd+desc&blueWorksYn=N&sw=
```

With:
```
=https://youth.seoul.go.kr/infoData/{{ $('카테고리별 순차 처리').first().json.catPath }}/ctList.do?key={{ $('카테고리별 순차 처리').first().json.catKey }}&tabKind=002&pageIndex={{ $json.pageIndex }}&orderBy=regYmd+desc&blueWorksYn=N&sw=
```

- [ ] **Step 6.2: `상세 페이지 요청` URL 치환**

Replace:
```
https://youth.seoul.go.kr/infoData/plcyInfo/view.do?plcyBizId={{ $json.plcyBizId }}&tab=001&key=2309150002&tabKind=002
```

With:
```
=https://youth.seoul.go.kr/infoData/{{ $('카테고리별 순차 처리').first().json.catPath }}/view.do?plcyBizId={{ $json.plcyBizId }}&tab=001&key={{ $('카테고리별 순차 처리').first().json.catKey }}&tabKind=002
```

- [ ] **Step 6.3: `plcyBizId 추출` 노드의 `goView` regex 가 양 카테고리 ID 형식을 모두 잡는지 확인**

기존 regex: `/goView\('([A-Z]\d+)'\)/g` — `V202600006` 같은 형식.
사용자가 준 신규 카테고리 plcyBizId 예시: `20260527005400113223` (20자 숫자).

목록 페이지 HTML 의 실제 패턴을 확인해야 한다. Run:
```bash
curl -s -A "YouthFit-Bot/1.0" "https://youth.seoul.go.kr/infoData/youthPlcyInfo/ctList.do?key=2309160001&tabKind=002&pageIndex=1" \
  | grep -oE "goView\('[^']+'\)" | head -5
```

If patterns differ (`'20260527005400113223'` 등 영문자 없는 ID), update regex in `plcyBizId 추출` 노드:
```js
const regex = /goView\('([A-Za-z0-9]+)'\)/g;
```

- [ ] **Step 6.4: `다음 페이지 확인` 노드의 카테고리 컨텍스트 보존 점검**

기존 코드:
```js
const items = $('plcyBizId 추출').all();
const firstItem = items[0]?.json || {};
const currentPage = firstItem.currentPage || 1;
const lastPage = firstItem.lastPage || 1;
```

This still works (the `splitInBatches` of `카테고리별 순차 처리` keeps the category context for the inner loop). Confirm by running the workflow once in n8n (manual step).

No code change needed here unless verification fails.

- [ ] **Step 6.5: JSON 유효성 + Commit**

```bash
jq -e '.nodes | length' n8n/workflows/youth-seoul-crawl.json
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul URL 을 카테고리 catPath/catKey 로 파라미터화"
```

---

## Task 7: `parse-detail` 인라인 코드 갱신 (fixture sync)

**목표:** Task 1 에서 만든 `parse-body.mjs` 의 로직을 워크플로우 jsCode 에 그대로 복사. `_refUrls`, `_selfAttachments` 임시 필드를 `rawData` 에 추가.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json` 의 `parse-detail` 노드 jsCode

- [ ] **Step 7.1: 기존 `extractByTh` 정규식 보강**

Replace the existing `.replace(/<[^>]+>/g, '')` chain inside `extractByTh` with:
```
.replace(/<!--[\s\S]*?-->/g, '')
.replace(/<br\s*\/?>/gi, '\n')
.replace(/<\/?[a-zA-Z][^>]*>/g, '')
.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
.replace(/&quot;/g, '"').replace(/&#39;/g, "'")
.replace(/\t/g, '')
.replace(/^[  ]*-{3,}[  ]*$/gm, '')
.replace(/-{4,}>/g, '')
.replace(/\n\s*\n/g, '\n')
.trim();
```

JSON 인라인에서는 `\n` → `\\n`, `\s` → `\\s` 등 escaping 주의.

- [ ] **Step 7.2: `extractRefUrls` / `extractSelfAttachments` 함수 추가**

Add at the top of the `jsCode` (after `extractByTh` definition):
```js
const REF_SECTIONS = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];
function extractRefUrls() {
  const urls = []; const seen = new Set();
  for (const th of REF_SECTIONS) {
    const escTh = th.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp('<th[^>]*>' + escTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>', 'i');
    const m = html.match(re);
    if (!m) continue;
    const hrefRe = /href="(https?:\/\/[^"]+)"/gi;
    let hm;
    while ((hm = hrefRe.exec(m[1])) !== null) {
      const u = hm[1];
      if (seen.has(u)) continue;
      seen.add(u);
      urls.push(u);
    }
  }
  return urls.slice(0, 3);
}

const ATTACHMENT_EXT_PATTERN = /\.(pdf|hwp|hwpx|docx?|xlsx?|zip)(\?|$|#)/i;
function extractSelfAttachments() {
  const out = []; const seen = new Set();
  const linkRe = /<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let lm;
  while ((lm = linkRe.exec(html)) !== null) {
    const href = lm[1];
    if (!ATTACHMENT_EXT_PATTERN.test(href)) continue;
    const abs = /^https?:\/\//i.test(href)
      ? href
      : 'https://youth.seoul.go.kr' + (href.startsWith('/') ? href : '/' + href);
    if (seen.has(abs)) continue;
    seen.add(abs);
    const text = lm[2].replace(/<[^>]+>/g, '').trim().slice(0, 200);
    out.push({ name: text || abs.split('/').pop(), url: abs });
  }
  return out;
}
```

- [ ] **Step 7.3: result.rawData 에 `_refUrls`, `_selfAttachments` 추가**

In the final `result` object's `rawData`:
```js
rawData: {
  title: title,
  body: body,
  category: category || '복지',
  region: '서울',
  applyStart: applyStart,
  applyEnd: applyEnd,
  _refUrls: extractRefUrls(),
  _selfAttachments: extractSelfAttachments()
}
```

- [ ] **Step 7.4: JSON 유효성 + 빠른 매뉴얼 sanity 체크**

```bash
jq -e '.nodes[] | select(.id=="parse-detail") | .parameters.jsCode' n8n/workflows/youth-seoul-crawl.json | head -c 200
```

Output should start with a valid JS string (not parse error).

- [ ] **Step 7.5: Commit**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul parse-detail 정제 보강 + refUrls/selfAttachments 추출"
```

---

## Task 8: `pick-link` 노드 추가

**목표:** parse-detail 결과의 `_refUrls` 를 받아 fetch + cheerio 추출. enrichment-merge fixture 의 인라인 알고리즘을 그대로 노드 jsCode 로 복사.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json` — `pick-link` 신규 노드 + connections

- [ ] **Step 8.1: `pick-link` 노드 정의**

Reference: youth-center-seoul.json:374 has the equivalent `jsCode`. Copy that node's `jsCode` verbatim but change the `selectUrls` call to pass `_refUrls`:

In the existing code:
```js
const urls = selectUrls(p);
```

Replace with:
```js
const urls = selectUrls({ refUrls: Array.isArray(p?.rawData?._refUrls) ? p.rawData._refUrls : [] });
```

The rest of the jsCode (cheerio, fetchAndExtract, mergeFetchResults) stays identical.

Insert node into `nodes` array:
```json
{
  "parameters": { "jsCode": "<copy of youth-center pick-link jsCode with the above change>" },
  "id": "pick-link",
  "name": "참고사이트 fetch + 머지",
  "type": "n8n-nodes-base.code",
  "typeVersion": 2,
  "position": [1980, 100]
}
```

- [ ] **Step 8.2: connections 재배선 — parse-detail → pick-link → send-to-backend 사이에 끼움**

Replace:
```
"상세 데이터 파싱": { "main": [[{ "node": "백엔드 API 전송", "type": "main", "index": 0 }]] }
```

With:
```
"상세 데이터 파싱": { "main": [[{ "node": "참고사이트 fetch + 머지", "type": "main", "index": 0 }]] }
```

(Don't update `send-to-backend` connection yet — that's done in Task 9 after `enrichment-meta` is inserted.)

- [ ] **Step 8.3: JSON 유효성 + Commit**

```bash
jq -e '.nodes | length' n8n/workflows/youth-seoul-crawl.json
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul pick-link 노드 추가 (cheerio fetch + extract)"
```

---

## Task 9: `enrichment-meta` 노드 추가

**목표:** `pick-link` 결과 (`_cleanedText`, `_extraAttachments`, `_enrichmentStatus`) 를 `rawData.enrichment` 객체로 합성.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json` — `enrichment-meta` 신규 노드 + connections

- [ ] **Step 9.1: 노드 정의**

```json
{
  "parameters": {
    "jsCode": "const p = $input.first().json;\nconst e = {\n  sourceUrl: (p.rawData._refUrls && p.rawData._refUrls[0]) || null,\n  fetchedAt: new Date().toISOString(),\n  extractor: 'regex',\n  confidence: null,\n  status: p._enrichmentStatus || 'FETCH_FAILED',\n  sections: null,\n  extraAttachments: p._extraAttachments || [],\n  cleanedText: p._cleanedText || null\n};\nreturn [{ json: { ...p, rawData: { ...p.rawData, enrichment: e } } }];"
  },
  "id": "enrichment-meta",
  "name": "enrichment 메타 합성",
  "type": "n8n-nodes-base.code",
  "typeVersion": 2,
  "position": [2200, 100]
}
```

- [ ] **Step 9.2: connections 재배선 — pick-link → enrichment-meta → (다음 노드 task 10 에서 promote-attachments)**

Add:
```
"참고사이트 fetch + 머지": { "main": [[{ "node": "enrichment 메타 합성", "type": "main", "index": 0 }]] }
```

(Don't link `enrichment-meta` → `send-to-backend` yet — Task 10 inserts `promote-attachments` between them.)

- [ ] **Step 9.3: Commit**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul enrichment 메타 합성 노드 추가"
```

---

## Task 10: `promote-attachments` 노드 추가 (fixture sync)

**목표:** `enrichment.extraAttachments` 와 `rawData._selfAttachments` 를 합쳐 키워드 필터 적용 후 `rawData.attachments[]` 로 승격. youth-center-seoul.json 의 노드 jsCode 를 참고하되 `selfExtras` 합치는 부분만 추가.

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json` — `promote-attachments` 신규 노드 + connections

- [ ] **Step 10.1: 노드 정의 (fixture inline + selfExtras 합치기)**

Copy youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 를 가져와서 `extras` 추출 부분만 수정:

기존:
```js
const enrichment = input?.rawData?.enrichment;
const extras = enrichment?.extraAttachments;
if (!Array.isArray(extras) || extras.length === 0) {
  return [{ json: input }];
}
```

수정:
```js
const enrichment = input?.rawData?.enrichment;
const enrichmentExtras = Array.isArray(enrichment?.extraAttachments) ? enrichment.extraAttachments : [];
const selfExtras = Array.isArray(input?.rawData?._selfAttachments) ? input.rawData._selfAttachments : [];
const extras = [...enrichmentExtras, ...selfExtras];
if (extras.length === 0) {
  // _selfAttachments 임시 필드 제거
  const { _selfAttachments, _refUrls, ...cleanRaw } = input.rawData || {};
  return [{ json: { ...input, rawData: cleanRaw } }];
}
```

And at the very end (before `return [{ json: ... }]`), remove the temp fields:
```js
const { _selfAttachments, _refUrls, ...cleanRaw } = input.rawData;
return [{ json: { ...input, rawData: { ...cleanRaw, attachments: merged } } }];
```

The keyword filter (`isInformationalName`) must also be present in this jsCode — Task 4 already added it to the fixture. Copy from there verbatim.

Insert node:
```json
{
  "parameters": { "jsCode": "<full code as above>" },
  "id": "promote-attachments",
  "name": "attachments 승격",
  "type": "n8n-nodes-base.code",
  "typeVersion": 2,
  "position": [2420, 100]
}
```

- [ ] **Step 10.2: connections 재배선 — enrichment-meta → promote-attachments → send-to-backend**

```
"enrichment 메타 합성": { "main": [[{ "node": "attachments 승격", "type": "main", "index": 0 }]] },
"attachments 승격": { "main": [[{ "node": "백엔드 API 전송", "type": "main", "index": 0 }]] }
```

- [ ] **Step 10.3: JSON 유효성 + 노드 개수 검증**

```bash
jq -e '[.nodes[].name]' n8n/workflows/youth-seoul-crawl.json
```
Expected: 기존 노드 + `카테고리 초기화`, `카테고리별 순차 처리`, `참고사이트 fetch + 머지`, `enrichment 메타 합성`, `attachments 승격` 5개 추가.

- [ ] **Step 10.4: Commit**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul attachments 승격 노드 추가 (selfExtras + 키워드 필터)"
```

---

## Task 11: 검증 runbook 작성

**목표:** 백엔드 코드 변경 없이, 배포 후 어드민 5단계 status 가 청년서울에서 SUCCESS/SKIPPED/FAILED 로 올바르게 마킹되는지 확인할 체크리스트 문서를 남긴다. 어긋남이 있으면 후속 spec 으로 분리한다.

**Files:**
- Create: `docs/runbooks/youth-seoul-pipeline-verification.md`

- [ ] **Step 11.1: runbook 작성**

Create `docs/runbooks/youth-seoul-pipeline-verification.md`:
```markdown
# youth-seoul 파이프라인 보강 — 배포 후 검증 체크리스트

> spec: `docs/superpowers/specs/2026-05-30-youth-seoul-enrichment-and-attachments-design.md`
> 관련 plan: `docs/superpowers/plans/2026-05-30-youth-seoul-enrichment-and-attachments.md`

## 배포 직후 (워크플로우만)

1. n8n 워크플로우 1회 수동 실행 (`maxPage = 2` TEST 캡 유지 권장)
2. 백엔드 로그에서 `/api/internal/ingestion/policies` 수신 확인
3. 받은 payload 의 `rawData.enrichment` 와 `rawData.attachments` 형태가 온통청년과 동형인지 sample 5건 확인

## 어드민 5단계 status 검증

청년서울 출처 정책 5건을 `/admin/policies/processing/{id}` 패널에서 열어 다음 표를 채운다.

| 정책 ID | refUrls 개수 | 첨부 후보 개수 | enrichment.status | ENRICHMENT step | RAG_INDEXING step | 비고 |
|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |

### 기대 매핑

| `enrichment.status` | ENRICHMENT step | reason 컬럼 |
|---|---|---|
| `OK` | SUCCESS | (없음) |
| `NO_LINK` | SKIPPED | `NO_LINK` |
| `FETCH_FAILED` | FAILED | `FETCH_FAILED` |
| `TOO_SHORT` | FAILED | `TOO_SHORT` |

### 어긋남 발견 시

- 백엔드 `EnrichmentJobService` / `PolicyProcessingStep` 매핑이 청년서울 출처에서 누락되었을 가능성
- 별도 spec 으로 분리: `docs/superpowers/specs/YYYY-MM-DD-policy-enrichment-status-mapping-design.md`
- 본 plan 범위로 끌고 오지 않는다 (스코프 외)

## 본문 정제 회귀 점검

배포 전 후 청년서울 정책 본문 sample 10건의 `body` 텍스트를 비교한다.

- `<!-- ... -->` 형태의 잔재가 사라졌는가?
- 본문에 있던 `<민원인...>` 같은 텍스트 토큰이 살아있는가?
- `-----` dash 만 있는 줄이 사라졌는가?
- `----->` dash 화살표 잔재가 사라졌는가?
- 본문 안의 `→` 유니코드 화살표는 그대로 유지되는가?
- 정상 dash list (`- 항목1`, `- 항목2`) 는 영향 없는가?

## 첨부 필터 부작용 점검

온통청년 출처도 키워드 필터가 같이 적용됐다 (fixture sync). 다음을 본다:

- 배포 전 후 온통청년 정책 sample 5건의 `attachments[]` 개수 차이가 ±1 이내인가?
- 기존에 들어가던 `공고문.pdf`, `신청서.hwp` 같은 정보성 첨부가 빠지지 않았는가?
- 새로 빠진 첨부가 `로고|배너|아이콘|썸네일|포스터|광고` 패턴에 해당하는가?

## TEST 캡 해제

검증 완료 후 `n8n/workflows/youth-seoul-crawl.json` 의 `extract-ids` 노드 jsCode 에서:
```js
maxPage = Math.min(maxPage, 2); // TEST: 2페이지만 크롤링
```
줄을 제거 또는 환경변수 분기.
```

- [ ] **Step 11.2: Commit**

```bash
git add docs/runbooks/youth-seoul-pipeline-verification.md
git commit -m "docs(runbook): youth-seoul 파이프라인 배포 후 검증 체크리스트"
```

---

## Self-Review (작성자 — 실행자가 아닌 plan 작성자 본인이 함)

스펙 커버리지 / 플레이스홀더 / 타입 일관성 점검은 plan 작성 직후 작성자가 수행하고, 실행자는 이미 정리된 plan 으로 진행한다.

- ✅ 스펙 §5.1 본문 정제 → Task 1, Task 7
- ✅ 스펙 §5.2 카테고리 init → Task 5
- ✅ 스펙 §5.3 URL/첨부 추출 → Task 1 (extractRefUrls, extractSelfAttachments) + Task 7 (워크플로우 sync)
- ✅ 스펙 §5.4 pick-link → Task 8
- ✅ 스펙 §5.4 enrichment-meta → Task 9
- ✅ 스펙 §5.5 promote-attachments + 키워드 필터 → Task 3 + Task 10 + Task 4 sync
- ✅ 스펙 §5.6 fixture 동기화 → Task 4 (youth-center), Task 7/10 (youth-seoul)
- ✅ 스펙 §6 백엔드 점검 항목 → Task 11 runbook
- ✅ 스펙 §7 테스트 전략 → Task 1-3 의 TDD 사이클 + Task 11 수동 검증

플레이스홀더 없음. 함수명 일관성 (`extractByTh / extractRefUrls / extractSelfAttachments / isInformationalName / selectUrls`) 확인됨. 노드 이름 (`카테고리 초기화 / 카테고리별 순차 처리 / 참고사이트 fetch + 머지 / enrichment 메타 합성 / attachments 승격`) 일관됨.

---

## 실행 옵션

이 plan 은 `superpowers:subagent-driven-development` (권장) 또는 `superpowers:executing-plans` 로 진행한다. 본 세션에서는 spec/plan 작성까지만 진행하기로 했으므로 실행은 다음 세션에서 사용자가 직접 트리거.
