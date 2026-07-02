# 몽땅청년 참고사이트 fetch 개선 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 참고사이트 fetch의 워크플로우 중단 버그(#157)를 없애고, 자기 포털 필터·cookie jar·TLS 보강·URL별 실패 진단(#158)으로 수집 성공률과 관측성을 올린다.

**Architecture:** fetch 알고리즘의 단일 원본을 `n8n/workflows/node-src/link-fetch-merge.js`로 추출하고 sync 스크립트로 4개 워크플로우 노드에 주입한다. 순수 로직(URL 정규화·필터·cookie jar)은 `__fixtures__/enrichment-merge/enrich.mjs` 미러에서 TDD로 먼저 만든다. 백엔드는 `PolicyEnrichment`에 `fetchDiagnostics` 필드만 추가한다(status enum 불변).

**Tech Stack:** n8n Code 노드(Node.js https/http + cheerio), Node 25 (verify.mjs 하니스), Spring Boot 4 / Java 21, docker-compose.

**Spec:** `docs/superpowers/specs/2026-07-02-refsite-fetch-improvement-design.md`

## Global Constraints

- `EnrichmentStatus` enum과 `IngestionService.mapEnrichmentStatus`는 수정 금지. 최종 status는 기존 값(OK/NO_LINK/FETCH_FAILED/TOO_SHORT)만 산출한다.
- outcome 코드는 정확히 이 집합만: `OK / SELF_PORTAL / INVALID_URL / HTTP_<코드> / TIMEOUT / TLS_ERROR / REDIRECT_LOOP / OVERSIZE / TOO_SHORT / NETWORK`
- TLS 검증 완화(`rejectUnauthorized=false`) 금지 — 인증서 체인 보강만 허용.
- 기존 verify 케이스(`cases/`, `cases-html/`)와 백엔드 기존 테스트는 수정 없이 통과해야 한다(하위 호환 증명).
- 워크플로우 JSON은 반드시 `node-src/sync-link-fetch-merge.mjs`로만 갱신한다(4벌 손편집 금지).
- 커밋 프리픽스: fix(#157 방어), feat(#158 기능), test, chore, docs.
- 몽땅 3종 노드명은 `참고사이트 fetch + 머지`, 온통청년 노드명은 `링크 fetch + 머지` — 이름이 다른 같은 알고리즘이다.

---

### Task 1: 미러(enrich.mjs)에 URL 정규화·자기 포털 필터 추가

**Files:**
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases-prepare-urls/case-scheme-less.input.json` (외 3케이스, 아래 참조)

**Interfaces:**
- Consumes: 기존 `selectUrls(policy)` (변경 없음 — 후보 수집만 담당)
- Produces: `export function normalizeCandidateUrl(raw: string): string|null`, `export function isSelfPortalUrl(url: string): boolean`, `export function prepareUrls(candidates: string[]): { urls: string[], diagnostics: {url,outcome}[] }` — Task 3의 노드 원본과 Task 2가 그대로 사용

- [ ] **Step 1: 케이스 4종 작성 (실패하는 테스트)**

`cases-prepare-urls/case-scheme-less.input.json`:
```json
{ "candidates": ["www.kofpi.or.kr"] }
```
`cases-prepare-urls/case-scheme-less.expected.json`:
```json
{ "urls": ["https://www.kofpi.or.kr"], "diagnostics": [] }
```
`cases-prepare-urls/case-self-portal-only.input.json`:
```json
{ "candidates": ["https://youth.seoul.go.kr", "https://youth.seoul.go.kr/content.do?key=2310100025"] }
```
`cases-prepare-urls/case-self-portal-only.expected.json`:
```json
{ "urls": [], "diagnostics": [ { "url": "https://youth.seoul.go.kr", "outcome": "SELF_PORTAL" }, { "url": "https://youth.seoul.go.kr/content.do?key=2310100025", "outcome": "SELF_PORTAL" } ] }
```
`cases-prepare-urls/case-invalid.input.json`:
```json
{ "candidates": ["보조금24 참조", "javascript:void(0)"] }
```
`cases-prepare-urls/case-invalid.expected.json`:
```json
{ "urls": [], "diagnostics": [ { "url": "보조금24 참조", "outcome": "INVALID_URL" }, { "url": "javascript:void(0)", "outcome": "INVALID_URL" } ] }
```
`cases-prepare-urls/case-mixed.input.json`:
```json
{ "candidates": ["https://youth.seoul.go.kr/content.do?key=1", "www.molit.go.kr/plan", "https://www.molit.go.kr/plan", "https://fill4young.kinfa.or.kr/yfs/main"] }
```
`cases-prepare-urls/case-mixed.expected.json` (정규화 후 중복 제거 — `www.molit.go.kr/plan`과 `https://www.molit.go.kr/plan`은 같은 URL):
```json
{ "urls": ["https://www.molit.go.kr/plan", "https://fill4young.kinfa.or.kr/yfs/main"], "diagnostics": [ { "url": "https://youth.seoul.go.kr/content.do?key=1", "outcome": "SELF_PORTAL" } ] }
```

- [ ] **Step 2: verify.mjs에 prepare-urls 케이스 러너 추가**

verify.mjs의 HTML 케이스 블록 뒤, `if (failed > 0)` 앞에 삽입:
```js
// prepareUrls 케이스 (Task 1)
const PREPARE_DIR = new URL('./cases-prepare-urls/', import.meta.url);
let prepEntries = [];
try { prepEntries = await readdir(PREPARE_DIR); } catch (_) {}
for (const inputFile of prepEntries.filter(e => e.endsWith('.input.json')).sort()) {
  total++;
  const name = inputFile.replace('.input.json', '');
  const input = JSON.parse(await readFile(new URL(inputFile, PREPARE_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(`${name}.expected.json`, PREPARE_DIR), 'utf8'));
  const actual = prepareUrls(input.candidates);
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name} (prepare-urls)`);
  } else {
    failed++;
    console.log(`FAIL  ${name} (prepare-urls)`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}
```
파일 최상단 import에 `prepareUrls` 추가:
```js
import { selectUrls, mergeFetchResults, cheerioAvailable, extractCleanedAndAttachments, prepareUrls } from './enrich.mjs';
```

- [ ] **Step 3: 실패 확인**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: `SyntaxError` (prepareUrls export 없음) 또는 FAIL — 실패해야 정상

- [ ] **Step 4: enrich.mjs에 구현 추가**

`normalizeUrlKey` 함수 아래에 삽입:
```js
// URL 정규화: 스킴 없는 도메인(`www.kofpi.or.kr`)에 https 를 부여한다 (#157).
// URL 로 볼 수 없는 문자열은 null — 호출부가 INVALID_URL 로 기록한다.
export function normalizeCandidateUrl(raw) {
  if (typeof raw !== 'string') return null;
  const u = raw.trim();
  if (!u) return null;
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith('//')) return 'https:' + u;
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)+([/:?#]|$)/i.test(u)) return 'https://' + u;
  return null;
}

// 자기 포털(youth.seoul.go.kr)은 fetch 하지 않는다.
// 메인은 인덱스 shell, content.do 는 WebGate JS 챌린지, view.do 는 타 정책 교차 오염원.
export function isSelfPortalUrl(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  if (!m) return false;
  return /(^|\.)youth\.seoul\.go\.kr$/i.test(m[1]);
}

// selectUrls 가 모은 후보를 정규화·필터링해 fetch 대상과 진단을 분리한다.
export function prepareUrls(candidates) {
  const urls = [];
  const diagnostics = [];
  const seen = new Set();
  for (const raw of Array.isArray(candidates) ? candidates : []) {
    const normalized = normalizeCandidateUrl(raw);
    if (!normalized) {
      diagnostics.push({ url: String(raw).slice(0, 500), outcome: 'INVALID_URL' });
      continue;
    }
    const key = normalizeUrlKey(normalized);
    if (seen.has(key)) continue;
    seen.add(key);
    if (isSelfPortalUrl(normalized)) {
      diagnostics.push({ url: normalized, outcome: 'SELF_PORTAL' });
      continue;
    }
    urls.push(normalized);
  }
  return { urls, diagnostics };
}
```

- [ ] **Step 5: 통과 확인 (기존 케이스 포함 전체)**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: `All N case(s) passed` — 기존 케이스 + prepare-urls 4케이스 전부 PASS

- [ ] **Step 6: Commit**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "feat(n8n): 참고사이트 URL 정규화·자기 포털 필터 (prepareUrls) — 미러 선행 (#157, #158)"
```

---

### Task 2: 미러(enrich.mjs)에 cookie jar 순수 헬퍼 추가

**Files:**
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs`
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/verify.mjs`
- Create: `n8n/workflows/__fixtures__/enrichment-merge/cases-cookies/case-set-and-send.input.json` (외 2케이스)

**Interfaces:**
- Consumes: 없음 (독립 순수 함수)
- Produces: `export function applySetCookies(jar: object, host: string, setCookieHeaders: string[]): object` (불변 — 새 jar 반환), `export function cookieHeaderFor(jar: object, host: string): string|null` — Task 3의 httpGetText 리다이렉트 체인이 사용. jar 형태: `{ [host]: { [name]: value } }`

- [ ] **Step 1: 케이스 3종 작성**

케이스 입력 형식: `{ "steps": [ {"apply": {"host","setCookie"}} | {"header": {"host"}} ] }` — apply는 jar 누적, header는 그 시점 헤더를 결과에 push.

`cases-cookies/case-set-and-send.input.json`:
```json
{ "steps": [ { "apply": { "host": "www.molit.go.kr", "setCookie": ["WMONID=abc123; Path=/; HttpOnly", "SESSION=xyz; Path=/"] } }, { "header": { "host": "www.molit.go.kr" } } ] }
```
`cases-cookies/case-set-and-send.expected.json`:
```json
{ "headers": ["WMONID=abc123; SESSION=xyz"] }
```
`cases-cookies/case-host-isolation.input.json`:
```json
{ "steps": [ { "apply": { "host": "a.go.kr", "setCookie": ["k=v; Path=/"] } }, { "header": { "host": "b.go.kr" } } ] }
```
`cases-cookies/case-host-isolation.expected.json`:
```json
{ "headers": [null] }
```
`cases-cookies/case-overwrite.input.json`:
```json
{ "steps": [ { "apply": { "host": "a.go.kr", "setCookie": ["k=v1"] } }, { "apply": { "host": "a.go.kr", "setCookie": ["k=v2"] } }, { "header": { "host": "a.go.kr" } } ] }
```
`cases-cookies/case-overwrite.expected.json`:
```json
{ "headers": ["k=v2"] }
```

- [ ] **Step 2: verify.mjs에 cookies 러너 추가** (prepare-urls 러너 아래)

```js
// cookie jar 케이스 (Task 2)
const COOKIES_DIR = new URL('./cases-cookies/', import.meta.url);
let cookieEntries = [];
try { cookieEntries = await readdir(COOKIES_DIR); } catch (_) {}
for (const inputFile of cookieEntries.filter(e => e.endsWith('.input.json')).sort()) {
  total++;
  const name = inputFile.replace('.input.json', '');
  const input = JSON.parse(await readFile(new URL(inputFile, COOKIES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(`${name}.expected.json`, COOKIES_DIR), 'utf8'));
  let jar = {};
  const headers = [];
  for (const step of input.steps) {
    if (step.apply) jar = applySetCookies(jar, step.apply.host, step.apply.setCookie);
    if (step.header) headers.push(cookieHeaderFor(jar, step.header.host));
  }
  const actual = { headers };
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name} (cookies)`);
  } else {
    failed++;
    console.log(`FAIL  ${name} (cookies)`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}
```
import에 `applySetCookies, cookieHeaderFor` 추가.

- [ ] **Step 3: 실패 확인**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: import 에러 — 실패해야 정상

- [ ] **Step 4: enrich.mjs에 구현 추가** (prepareUrls 아래)

```js
// 리다이렉트 체인 한정 cookie jar (#158).
// Set-Cookie 의 name=value 만 취하고 속성(Path/Domain/Expires)은 무시한다 —
// 체인 밖으로 쿠키를 유지하지 않으므로 만료·스코프 관리가 불필요하다.
export function applySetCookies(jar, host, setCookieHeaders) {
  if (!Array.isArray(setCookieHeaders) || setCookieHeaders.length === 0) return jar;
  const next = { ...jar, [host]: { ...(jar[host] || {}) } };
  for (const line of setCookieHeaders) {
    if (typeof line !== 'string') continue;
    const pair = line.split(';', 1)[0];
    const eq = pair.indexOf('=');
    if (eq <= 0) continue;
    const name = pair.slice(0, eq).trim();
    if (!name) continue;
    next[host][name] = pair.slice(eq + 1).trim();
  }
  return next;
}

export function cookieHeaderFor(jar, host) {
  const cookies = jar && jar[host];
  if (!cookies) return null;
  const entries = Object.entries(cookies);
  if (entries.length === 0) return null;
  return entries.map(([k, v]) => `${k}=${v}`).join('; ');
}
```

- [ ] **Step 5: 통과 확인**

Run: `cd n8n/workflows/__fixtures__/enrichment-merge && node verify.mjs`
Expected: `All N case(s) passed`

- [ ] **Step 6: Commit**

```bash
git add n8n/workflows/__fixtures__/enrichment-merge/
git commit -m "feat(n8n): 리다이렉트 체인 cookie jar 헬퍼 — 미러 선행 (#158)"
```

---

### Task 3: 노드 단일 원본(node-src) 작성 + sync 스크립트로 4개 워크플로우 주입

**Files:**
- Create: `n8n/workflows/node-src/link-fetch-merge.js` (아래 전체 코드)
- Create: `n8n/workflows/node-src/sync-link-fetch-merge.mjs`
- Modify: `n8n/workflows/youth-seoul-city.json`, `youth-seoul-district.json`, `youth-seoul-external.json` (노드 `참고사이트 fetch + 머지`), `youth-center-seoul.json` (노드 `링크 fetch + 머지`) — sync 스크립트로만
- Modify: `n8n/workflows/__fixtures__/enrichment-merge/README.md` (동기화 원본이 node-src 임을 명시)

**Interfaces:**
- Consumes: Task 1·2에서 확정한 `prepareUrls / applySetCookies / cookieHeaderFor` 알고리즘 (노드는 import 불가 → 동일 코드 인라인, enrich.mjs 와 문자 단위로 같은 본문 유지)
- Produces: 노드 출력 json에 `_fetchDiagnostics: {url, outcome}[]` 추가 (Task 4의 조립 노드들이 소비). 기존 `_enrichUrl/_enrichUrls/_cleanedText/_extraAttachments/_enrichmentStatus` 계약 유지.

- [ ] **Step 1: `n8n/workflows/node-src/link-fetch-merge.js` 작성 (전체)**

```js
// ⚠ 단일 원본: 이 파일이 4개 워크플로우 노드 jsCode 의 원본이다.
//   - youth-seoul-city/district/external.json :: "참고사이트 fetch + 머지"
//   - youth-center-seoul.json :: "링크 fetch + 머지"
//   워크플로우 JSON 을 손으로 수정하지 말고 sync-link-fetch-merge.mjs 를 실행한다.
// ⚠ 알고리즘 미러: __fixtures__/enrichment-merge/enrich.mjs (verify.mjs 로 검증).
//   순수 함수(selectUrls/prepareUrls/mergeFetchResults/cookie jar/추출)는
//   미러와 동일 본문이어야 한다.
const cheerio = require('cheerio');
const https = require('https');
const http = require('http');

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';
const FETCH_TIMEOUT_MS = 10000;
const MAX_RESPONSE_BYTES = 2000000;
const PER_PAGE_CAP = 8000;

function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

function selectUrls(policy) {
  // 1) 명시 refUrls[] 가 있으면 우선 사용 (youth-seoul 몽땅 크롤)
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

// URL 정규화: 스킴 없는 도메인(`www.kofpi.or.kr`)에 https 를 부여한다 (#157).
// URL 로 볼 수 없는 문자열은 null — 호출부가 INVALID_URL 로 기록한다.
function normalizeCandidateUrl(raw) {
  if (typeof raw !== 'string') return null;
  const u = raw.trim();
  if (!u) return null;
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith('//')) return 'https:' + u;
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)+([/:?#]|$)/i.test(u)) return 'https://' + u;
  return null;
}

// 자기 포털(youth.seoul.go.kr)은 fetch 하지 않는다.
// 메인은 인덱스 shell, content.do 는 WebGate JS 챌린지, view.do 는 타 정책 교차 오염원.
function isSelfPortalUrl(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  if (!m) return false;
  return /(^|\.)youth\.seoul\.go\.kr$/i.test(m[1]);
}

// selectUrls 가 모은 후보를 정규화·필터링해 fetch 대상과 진단을 분리한다.
function prepareUrls(candidates) {
  const urls = [];
  const diagnostics = [];
  const seen = new Set();
  for (const raw of Array.isArray(candidates) ? candidates : []) {
    const normalized = normalizeCandidateUrl(raw);
    if (!normalized) {
      diagnostics.push({ url: String(raw).slice(0, 500), outcome: 'INVALID_URL' });
      continue;
    }
    const key = normalizeUrlKey(normalized);
    if (seen.has(key)) continue;
    seen.add(key);
    if (isSelfPortalUrl(normalized)) {
      diagnostics.push({ url: normalized, outcome: 'SELF_PORTAL' });
      continue;
    }
    urls.push(normalized);
  }
  return { urls, diagnostics };
}

// 리다이렉트 체인 한정 cookie jar (#158).
// Set-Cookie 의 name=value 만 취하고 속성(Path/Domain/Expires)은 무시한다 —
// 체인 밖으로 쿠키를 유지하지 않으므로 만료·스코프 관리가 불필요하다.
function applySetCookies(jar, host, setCookieHeaders) {
  if (!Array.isArray(setCookieHeaders) || setCookieHeaders.length === 0) return jar;
  const next = { ...jar, [host]: { ...(jar[host] || {}) } };
  for (const line of setCookieHeaders) {
    if (typeof line !== 'string') continue;
    const pair = line.split(';', 1)[0];
    const eq = pair.indexOf('=');
    if (eq <= 0) continue;
    const name = pair.slice(0, eq).trim();
    if (!name) continue;
    next[host][name] = pair.slice(eq + 1).trim();
  }
  return next;
}

function cookieHeaderFor(jar, host) {
  const cookies = jar && jar[host];
  if (!cookies) return null;
  const entries = Object.entries(cookies);
  if (entries.length === 0) return null;
  return entries.map(([k, v]) => `${k}=${v}`).join('; ');
}

function mergeFetchResults(results) {
  if (!Array.isArray(results) || results.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  const ok = results.filter(r => r && r.status == null);
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

const ONCLICK_DOWNLOAD_RULES = [
  {
    host: /(^|\.)kofpi\.or\.kr$/i,
    fnName: 'fnNotiDownload',
    buildUrl: (origin, seq) => `${origin}/noti/download.do?fileSeq=${encodeURIComponent(seq)}`,
  },
];

function isDummyHref(href) {
  if (!href) return true;
  const trimmed = href.trim();
  return trimmed === '' || trimmed === '#' || /^javascript:/i.test(trimmed);
}

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function resolveOnclickUrl(onclick, pageUrl) {
  if (!onclick) return null;
  const m = pageUrl.match(/^(https?:\/\/([^/]+))/);
  if (!m) return null;
  const origin = m[1];
  const host = m[2];
  for (const rule of ONCLICK_DOWNLOAD_RULES) {
    if (!rule.host.test(host)) continue;
    const fnRe = new RegExp('\\b' + escapeRegExp(rule.fnName) + "\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    const am = onclick.match(fnRe);
    if (!am) continue;
    return rule.buildUrl(origin, am[1]);
  }
  return null;
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
  if (cleaned.length > PER_PAGE_CAP) cleaned = cleaned.slice(0, PER_PAGE_CAP);

  const seen = new Set();
  const extras = [];
  $('a[href]').each((_, el) => {
    const $a = $(el);
    const href = $a.attr('href') || '';
    const onclick = $a.attr('onclick') || '';
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
    let url = absUrl(href, pageUrl);
    if (isDummyHref(href)) {
      const reconstructed = resolveOnclickUrl(onclick, pageUrl);
      if (reconstructed) url = reconstructed;
    }
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

// TLS 계열 에러코드 → TLS_ERROR 로 분류 (그 외 소켓 에러는 NETWORK)
const TLS_ERROR_CODES = /^(UNABLE_TO_VERIFY_LEAF_SIGNATURE|SELF_SIGNED_CERT_IN_CHAIN|DEPTH_ZERO_SELF_SIGNED_CERT|CERT_|ERR_TLS_)/;

function hostOf(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  return m ? m[1].toLowerCase() : '';
}

// #157: lib.request 동기 throw(ERR_INVALID_URL 등)까지 전부 resolve 로 흡수한다.
// 반환: { ok:true, body } | { ok:false, outcome }
function httpGetText(url, state) {
  state = state || { hops: 0, jar: {}, visited: [] };
  if (state.hops >= 5) return Promise.resolve({ ok: false, outcome: 'REDIRECT_LOOP' });
  return new Promise((resolve) => {
    let req;
    try {
      const lib = /^https:\/\//i.test(url) ? https : http;
      const host = hostOf(url);
      const cookie = cookieHeaderFor(state.jar, host);
      // 같은 URL 을 같은 쿠키로 재방문 = 쿠키를 줘도 안 풀리는 루프 → 중단
      const visitKey = url + '|' + (cookie || '');
      if (state.visited.includes(visitKey)) {
        return resolve({ ok: false, outcome: 'REDIRECT_LOOP' });
      }
      state.visited.push(visitKey);
      const headers = {
        'User-Agent': 'YouthFit-Bot/1.0 (+https://youthfit.kr/bot)',
        'Accept': 'text/html,application/xhtml+xml',
        'Accept-Encoding': 'identity'
      };
      if (cookie) headers['Cookie'] = cookie;
      req = lib.request(url, { method: 'GET', headers, timeout: FETCH_TIMEOUT_MS }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          const jar = applySetCookies(state.jar, host, res.headers['set-cookie'] || []);
          res.resume();
          return httpGetText(absUrl(res.headers.location, url), { hops: state.hops + 1, jar, visited: state.visited }).then(resolve);
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          res.resume();
          return resolve({ ok: false, outcome: 'HTTP_' + res.statusCode });
        }
        const chunks = [];
        let total = 0;
        res.on('data', c => {
          total += c.length;
          if (total > MAX_RESPONSE_BYTES) {
            req.destroy();
            resolve({ ok: false, outcome: 'OVERSIZE' });
            return;
          }
          chunks.push(c);
        });
        res.on('end', () => resolve({ ok: true, body: Buffer.concat(chunks).toString('utf8') }));
      });
      req.on('error', (e) => {
        const code = (e && e.code) || '';
        resolve({ ok: false, outcome: TLS_ERROR_CODES.test(code) ? 'TLS_ERROR' : 'NETWORK' });
      });
      req.on('timeout', () => { req.destroy(); resolve({ ok: false, outcome: 'TIMEOUT' }); });
      req.end();
    } catch (e) {
      resolve({ ok: false, outcome: 'INVALID_URL' });
    }
  });
}

// #157: URL 하나의 실패가 다른 URL·다른 정책으로 번지지 않게 await 도 격리한다.
async function fetchAndExtract(url) {
  let res;
  try {
    res = await httpGetText(url);
  } catch (e) {
    res = { ok: false, outcome: 'NETWORK' };
  }
  if (!res.ok || !res.body) {
    return { url, status: 'FETCH_FAILED', outcome: res.outcome || 'NETWORK', cleanedText: '', extraAttachments: [] };
  }
  try {
    const { cleaned, extras } = extractCleanedAndAttachments(res.body, url);
    const tooShort = cleaned.length < 200;
    return {
      url,
      status: tooShort ? 'TOO_SHORT' : null,
      outcome: tooShort ? 'TOO_SHORT' : 'OK',
      cleanedText: cleaned,
      extraAttachments: extras
    };
  } catch (e) {
    return { url, status: 'FETCH_FAILED', outcome: 'NETWORK', cleanedText: '', extraAttachments: [] };
  }
}

// 호출부 — 몽땅(rawData._refUrls)과 온통청년(aplyUrlAddr 계열) 모두 처리하는 통합 tail.
const p = $input.first().json;
const refUrls = p && p.rawData && Array.isArray(p.rawData._refUrls) ? p.rawData._refUrls : null;
const candidates = refUrls ? selectUrls({ refUrls }) : selectUrls(p);
const prep = prepareUrls(candidates);

if (prep.urls.length === 0) {
  return [{
    json: {
      ...p,
      _enrichUrl: null,
      _enrichUrls: [],
      _cleanedText: '',
      _extraAttachments: [],
      _enrichmentStatus: 'NO_LINK',
      _fetchDiagnostics: prep.diagnostics
    }
  }];
}

const results = await Promise.all(prep.urls.map(u => fetchAndExtract(u)));
const merged = mergeFetchResults(results);
const diagnostics = prep.diagnostics.concat(results.map(r => ({ url: r.url, outcome: r.outcome })));

return [{
  json: {
    ...p,
    _enrichUrl: prep.urls[0],
    _enrichUrls: prep.urls,
    _cleanedText: merged.cleanedText,
    _extraAttachments: merged.extraAttachments,
    _enrichmentStatus: merged.status,
    _fetchDiagnostics: diagnostics
  }
}];
```

- [ ] **Step 2: sync 스크립트 작성 — `n8n/workflows/node-src/sync-link-fetch-merge.mjs`**

```js
// link-fetch-merge.js 를 4개 워크플로우의 해당 노드 jsCode 로 주입하고
// onError=continueRegularOutput(#157 3겹 방어의 최후단)를 설정한다.
import { readFile, writeFile } from 'node:fs/promises';

const SRC = new URL('./link-fetch-merge.js', import.meta.url);
const TARGETS = [
  ['../youth-seoul-city.json', '참고사이트 fetch + 머지'],
  ['../youth-seoul-district.json', '참고사이트 fetch + 머지'],
  ['../youth-seoul-external.json', '참고사이트 fetch + 머지'],
  ['../youth-center-seoul.json', '링크 fetch + 머지'],
];

const code = await readFile(SRC, 'utf8');
for (const [rel, nodeName] of TARGETS) {
  const path = new URL(rel, import.meta.url);
  const wf = JSON.parse(await readFile(path, 'utf8'));
  const node = wf.nodes.find(n => n.name === nodeName);
  if (!node) {
    console.error(`MISSING node "${nodeName}" in ${rel}`);
    process.exit(1);
  }
  node.parameters.jsCode = code;
  node.onError = 'continueRegularOutput';
  await writeFile(path, JSON.stringify(wf, null, 2) + '\n');
  console.log(`synced ${rel} :: ${nodeName}`);
}
console.log('done');
```

- [ ] **Step 3: sync 실행 및 확인**

Run: `cd n8n/workflows/node-src && node sync-link-fetch-merge.mjs`
Expected: `synced ...` 4줄 + `done`

Run: `git diff --stat n8n/workflows/*.json`
Expected: 4개 JSON 변경. `git diff n8n/workflows/youth-seoul-city.json | head -50`으로 jsCode 교체와 `"onError": "continueRegularOutput"` 추가만 있는지 확인 (다른 노드 변경 없어야 함)

- [ ] **Step 4: 미러-원본 동기화 검증 (순수 함수 본문 일치)**

Run:
```bash
node -e "
const fs = require('fs');
const src = fs.readFileSync('n8n/workflows/node-src/link-fetch-merge.js', 'utf8');
const mirror = fs.readFileSync('n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs', 'utf8');
// 미러의 export function 본문이 원본에 동일하게 존재하는지 확인
const fns = ['normalizeCandidateUrl', 'isSelfPortalUrl', 'prepareUrls', 'applySetCookies', 'cookieHeaderFor'];
let fail = 0;
for (const fn of fns) {
  const re = new RegExp('function ' + fn + '\\\\([\\\\s\\\\S]*?\\\\n}', 'm');
  const a = (src.match(re) || [''])[0];
  const b = (mirror.match(re) || [''])[0];
  if (!a || a !== b) { console.log('MISMATCH: ' + fn); fail = 1; }
}
process.exit(fail);
"
```
Expected: 출력 없음, exit 0

- [ ] **Step 5: README 동기화 문구 갱신**

`n8n/workflows/__fixtures__/enrichment-merge/README.md`의 동기화 책임 문단을 다음 내용으로 교체(기존 문구는 youth-center-seoul.json만 언급):
```markdown
## 동기화 책임

fetch 노드 알고리즘의 단일 원본은 `n8n/workflows/node-src/link-fetch-merge.js` 다.
- 워크플로우 4개(youth-seoul-city/district/external 의 "참고사이트 fetch + 머지",
  youth-center-seoul 의 "링크 fetch + 머지")의 jsCode 는
  `node node-src/sync-link-fetch-merge.mjs` 로 재생성한다. JSON 손편집 금지.
- 이 디렉토리의 `enrich.mjs` 는 순수 함수 미러다. 원본의 순수 함수를 수정하면
  같은 변경을 여기에도 반영하고 `node verify.mjs` 로 검증한다.
```

- [ ] **Step 6: Commit**

```bash
git add n8n/workflows/node-src/ n8n/workflows/*.json n8n/workflows/__fixtures__/enrichment-merge/README.md
git commit -m "fix(n8n): fetch 노드 단일 원본화 + 생존성 3겹 방어 + 필터·cookie jar·진단 주입 (#157, #158)"
```

---

### Task 4: enrichment 조립 노드 5곳에 fetchDiagnostics 전달

**Files:**
- Modify: `n8n/workflows/youth-seoul-city.json`, `youth-seoul-district.json`, `youth-seoul-external.json` — 노드 `enrichment 메타 합성`
- Modify: `n8n/workflows/youth-center-seoul.json` — 노드 `enrichment skip: cleaned`, `enrichment 객체 조립`

**Interfaces:**
- Consumes: Task 3의 `_fetchDiagnostics` (없으면 `[]`)
- Produces: 백엔드 전송 페이로드 `rawData.enrichment.fetchDiagnostics: {url,outcome}[]` — Task 5의 `EnrichmentPayload.fetchDiagnostics`가 수신

- [ ] **Step 1: 몽땅 3종 `enrichment 메타 합성` 노드 수정**

이 노드는 3파일 동일 코드·소규모라 sync 스크립트 대상이 아니다. 아래 python으로 3파일 일괄 수정:
```bash
python3 - <<'EOF'
import json
NEW_LINE = "  fetchDiagnostics: p._fetchDiagnostics || [],\n"
for f in ['youth-seoul-city', 'youth-seoul-district', 'youth-seoul-external']:
    path = f'n8n/workflows/{f}.json'
    wf = json.load(open(path))
    node = next(n for n in wf['nodes'] if n['name'] == 'enrichment 메타 합성')
    code = node['parameters']['jsCode']
    assert 'fetchDiagnostics' not in code, f'{f}: already applied'
    marker = "  cleanedText: p._cleanedText || null\n"
    assert marker in code, f'{f}: marker not found'
    node['parameters']['jsCode'] = code.replace(marker, "  cleanedText: p._cleanedText || null,\n" + NEW_LINE)
    json.dump(wf, open(path, 'w'), ensure_ascii=False, indent=2)
    open(path, 'a').write('\n')
    print('patched', f)
EOF
```
결과 코드의 `e` 객체가 다음 형태가 됐는지 `git diff`로 확인:
```js
const e = {
  sourceUrl: (p.rawData._refUrls && p.rawData._refUrls[0]) || null,
  fetchedAt: new Date().toISOString().replace('Z', ''),
  extractor: 'regex',
  confidence: null,
  status: p._enrichmentStatus === undefined ? 'FETCH_FAILED' : (p._enrichmentStatus ?? 'OK'),
  sections: null,
  extraAttachments: p._extraAttachments || [],
  cleanedText: p._cleanedText || null,
  fetchDiagnostics: p._fetchDiagnostics || [],
};
```

- [ ] **Step 2: 온통청년 2개 노드 수정**

같은 방식으로 `youth-center-seoul.json`의 두 노드에 필드 추가:
- `enrichment skip: cleaned`: `extraAttachments: p._extraAttachments || []` 뒤에 `,\n  fetchDiagnostics: p._fetchDiagnostics || []` 추가
- `enrichment 객체 조립`: `cleanedText: item._cleanedText || null` 뒤에 `,\n  fetchDiagnostics: item._fetchDiagnostics || []` 추가

```bash
python3 - <<'EOF'
import json
path = 'n8n/workflows/youth-center-seoul.json'
wf = json.load(open(path))

skip = next(n for n in wf['nodes'] if n['name'] == 'enrichment skip: cleaned')
code = skip['parameters']['jsCode']
assert 'fetchDiagnostics' not in code
marker = "  extraAttachments: p._extraAttachments || []\n"
assert marker in code, 'skip marker not found'
skip['parameters']['jsCode'] = code.replace(marker, "  extraAttachments: p._extraAttachments || [],\n  fetchDiagnostics: p._fetchDiagnostics || []\n")

asm = next(n for n in wf['nodes'] if n['name'] == 'enrichment 객체 조립')
code = asm['parameters']['jsCode']
assert 'fetchDiagnostics' not in code
marker = "  cleanedText: item._cleanedText || null\n"
assert marker in code, 'asm marker not found'
asm['parameters']['jsCode'] = code.replace(marker, "  cleanedText: item._cleanedText || null,\n  fetchDiagnostics: item._fetchDiagnostics || []\n")

json.dump(wf, open(path, 'w'), ensure_ascii=False, indent=2)
open(path, 'a').write('\n')
print('patched youth-center-seoul')
EOF
```
(marker의 정확한 공백·줄바꿈이 실제 jsCode와 다르면 assert가 멈춘다 — 그 경우 `python3 -c`로 해당 노드 jsCode를 출력해 marker를 실제 문자열로 교정한 뒤 재실행.)

- [ ] **Step 3: JSON 유효성 + 노드 코드 확인**

Run: `for f in n8n/workflows/youth-seoul-*.json n8n/workflows/youth-center-seoul.json; do python3 -c "import json; json.load(open('$f')); print('OK $f')"; done`
Expected: OK 4줄

- [ ] **Step 4: Commit**

```bash
git add n8n/workflows/*.json
git commit -m "feat(n8n): enrichment 페이로드에 fetchDiagnostics 전달 (조립 노드 5곳) (#158)"
```

---

### Task 5: 백엔드 — PolicyEnrichment.fetchDiagnostics 수용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java`

**Interfaces:**
- Consumes: n8n이 보내는 `enrichment.fetchDiagnostics: [{url, outcome}]` (Task 4)
- Produces: `PolicyEnrichment(..., List<FetchDiagnostic> fetchDiagnostics)` — canonical 생성자는 9인자, **기존 8인자 생성자 오버로드 유지**(호출부 27곳 무수정). `public record FetchDiagnostic(String url, String outcome) {}`

- [ ] **Step 1: 실패하는 테스트 작성 — PolicyEnrichmentTest에 추가**

```java
@Test
void fetchDiagnostics가_없는_기존_jsonb도_역직렬화된다() throws Exception {
    var om = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    String legacy = """
            {"sourceUrl":"https://a.go.kr","fetchedAt":"2026-07-02T00:00:00Z","extractor":"regex",
             "confidence":null,"status":"TOO_SHORT","sections":null,"extraAttachments":[],"cleanedText":null}
            """;
    PolicyEnrichment e = om.readValue(legacy, PolicyEnrichment.class);
    assertThat(e.fetchDiagnostics()).isNull();
}

@Test
void fetchDiagnostics가_있으면_역직렬화된다() throws Exception {
    var om = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    String withDiag = """
            {"sourceUrl":null,"fetchedAt":"2026-07-02T00:00:00Z","extractor":"regex",
             "confidence":null,"status":"NO_LINK","sections":null,"extraAttachments":[],"cleanedText":null,
             "fetchDiagnostics":[{"url":"https://youth.seoul.go.kr","outcome":"SELF_PORTAL"}]}
            """;
    PolicyEnrichment e = om.readValue(withDiag, PolicyEnrichment.class);
    assertThat(e.fetchDiagnostics()).hasSize(1);
    assertThat(e.fetchDiagnostics().get(0).outcome()).isEqualTo("SELF_PORTAL");
}
```
(기존 테스트 파일의 ObjectMapper 셋업 유틸이 있으면 그것을 사용한다 — 파일 상단을 먼저 읽고 스타일을 맞출 것.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd backend && ./gradlew compileTestJava 2>&1 | tail -5`
Expected: `fetchDiagnostics()` 심볼 없음 컴파일 에러

- [ ] **Step 3: PolicyEnrichment 수정**

record 컴포넌트에 `List<FetchDiagnostic> fetchDiagnostics` 추가 + 하위호환 8인자 생성자 + 중첩 record:
```java
public record PolicyEnrichment(
        String sourceUrl,
        Instant fetchedAt,
        String extractor,
        Double confidence,
        EnrichmentStatus status,
        Sections sections,
        List<ExtraAttachment> extraAttachments,
        String cleanedText,
        List<FetchDiagnostic> fetchDiagnostics
) {
    // 기존 호출부(운영 1곳 + 테스트 26곳) 하위호환용 — 진단 없는 생성
    public PolicyEnrichment(String sourceUrl, Instant fetchedAt, String extractor,
                            Double confidence, EnrichmentStatus status, Sections sections,
                            List<ExtraAttachment> extraAttachments, String cleanedText) {
        this(sourceUrl, fetchedAt, extractor, confidence, status, sections,
                extraAttachments, cleanedText, null);
    }
    // ... 기존 EXPOSURE_CONFIDENCE_THRESHOLD, isExposable(), Sections, ExtraAttachment 유지 ...

    /** URL 별 fetch 결과 진단 (#158). outcome: OK/SELF_PORTAL/INVALID_URL/HTTP_<n>/TIMEOUT/TLS_ERROR/REDIRECT_LOOP/OVERSIZE/TOO_SHORT/NETWORK */
    public record FetchDiagnostic(String url, String outcome) {}
}
```

- [ ] **Step 4: IngestPolicyRequest 수정**

`EnrichmentPayload`에 필드 추가:
```java
public record EnrichmentPayload(
        String sourceUrl,
        @NotNull LocalDateTime fetchedAt,
        @NotBlank String extractor,
        Double confidence,
        @NotBlank String status,
        @Valid EnrichmentSectionsPayload sections,
        List<@Valid ExtraAttachmentPayload> extraAttachments,
        String cleanedText,
        List<@Valid FetchDiagnosticPayload> fetchDiagnostics   // NEW — nullable
) {}

public record FetchDiagnosticPayload(
        @NotBlank String url,
        @NotBlank String outcome
) {}
```
`mapEnrichment`의 `return new PolicyEnrichment(...)`를 9인자 canonical 생성자로 교체:
```java
        List<PolicyEnrichment.FetchDiagnostic> diagnostics = p.fetchDiagnostics() == null
                ? null
                : p.fetchDiagnostics().stream()
                        .map(d -> new PolicyEnrichment.FetchDiagnostic(d.url(), d.outcome()))
                        .toList();
        return new PolicyEnrichment(
                p.sourceUrl(),
                p.fetchedAt().toInstant(ZoneOffset.UTC),
                p.extractor(),
                p.confidence(),
                status,
                sections,
                atts,
                p.cleanedText(),
                diagnostics
        );
```

- [ ] **Step 5: 전체 테스트 통과 확인 (기존 테스트 무수정 통과 = 하위 호환 증명)**

Run: `cd backend && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 기존 테스트 수정 0건

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat(backend): PolicyEnrichment 에 fetchDiagnostics 진단 필드 추가 (#158)"
```

---

### Task 6: TLS 중간 인증서 보강 (n8n 컨테이너)

**Files:**
- Create: `n8n/certs/extra-ca.pem`
- Modify: `docker-compose.yml` (n8n 서비스)
- Modify: `docs/OPS.md` (인증서 번들 수집·갱신 절차)

**Interfaces:**
- Consumes: 없음
- Produces: n8n 컨테이너 env `NODE_EXTRA_CA_CERTS=/certs/extra-ca.pem` — Task 3 노드의 https 요청이 자동 사용

- [ ] **Step 1: kinfa.or.kr 서버 체인 실측 → 누락 중간 인증서 확보**

```bash
# 서버가 보내는 체인 확인 (leaf 만 오면 중간 인증서 누락)
openssl s_client -connect fill4young.kinfa.or.kr:443 -servername fill4young.kinfa.or.kr -showcerts </dev/null 2>/dev/null | grep -c 'BEGIN CERT'
# leaf 저장 후 AIA(caIssuers) URL 에서 중간 인증서 다운로드
openssl s_client -connect fill4young.kinfa.or.kr:443 -servername fill4young.kinfa.or.kr </dev/null 2>/dev/null | sed -n '/BEGIN CERT/,/END CERT/p' > /tmp/kinfa-leaf.pem
openssl x509 -in /tmp/kinfa-leaf.pem -noout -text | grep -A3 'Authority Information Access'
# 출력의 CA Issuers URI 를 받아 (예시 — 실제 URI 는 위 출력값 사용):
curl -so /tmp/intermediate.crt '<CA Issuers URI>'
openssl x509 -inform der -in /tmp/intermediate.crt -out n8n/certs/extra-ca.pem 2>/dev/null || cp /tmp/intermediate.crt n8n/certs/extra-ca.pem
# 검증: leaf 가 이 중간 인증서로 이어지는지
openssl verify -untrusted n8n/certs/extra-ca.pem /tmp/kinfa-leaf.pem
```
Expected: 마지막 명령 `OK` (시스템 루트까지 이어짐)

- [ ] **Step 2: docker-compose.yml n8n 서비스에 반영**

environment에 추가:
```yaml
      - NODE_EXTRA_CA_CERTS=/certs/extra-ca.pem
```
volumes에 추가:
```yaml
      - ./n8n/certs:/certs:ro
```

- [ ] **Step 3: OPS.md에 절차 기록**

OPS.md 적절한 섹션(n8n 운영)에 추가:
```markdown
### n8n 크롤 대상 TLS 중간 인증서 보강

일부 공공기관 사이트(예: fill4young.kinfa.or.kr)는 중간 인증서를 보내지 않아
Node.js 기본 검증이 `UNABLE_TO_VERIFY_LEAF_SIGNATURE` 로 실패한다 (브라우저는 통과).
검증 완화 대신 누락 중간 인증서를 `n8n/certs/extra-ca.pem` 번들에 추가하고
`NODE_EXTRA_CA_CERTS` 로 주입한다.

추가 절차: `openssl s_client -showcerts` 로 체인 확인 → leaf 의
Authority Information Access(CA Issuers) URI 에서 중간 인증서 다운로드 →
`openssl verify -untrusted` 로 이어짐 확인 → extra-ca.pem 에 PEM append →
`docker compose up -d n8n` 재기동. fetchDiagnostics 의 TLS_ERROR 분포로 대상 발견.
```

- [ ] **Step 4: (로컬 n8n 기동 시) 컨테이너 내 검증**

Run: `docker compose up -d n8n && docker exec youthfit-n8n node -e "require('https').get('https://fill4young.kinfa.or.kr/yfs/main', r => console.log('HTTP', r.statusCode)).on('error', e => console.log('ERR', e.code))"`
Expected: `HTTP <2xx|3xx>` (ERR UNABLE_TO_VERIFY_LEAF_SIGNATURE 가 아니어야 함)
(docker 미기동 환경이면 이 스텝은 Task 7 E2E 때로 미룬다 — 커밋은 진행)

- [ ] **Step 5: Commit**

```bash
git add n8n/certs/ docker-compose.yml docs/OPS.md
git commit -m "chore(n8n): 공공기관 TLS 중간 인증서 번들 주입 (NODE_EXTRA_CA_CERTS) (#158)"
```

---

### Task 7: 런북 E2E 체크리스트 갱신

**Files:**
- Modify: `docs/runbooks/youth-seoul-pipeline-verification.md`

**Interfaces:**
- Consumes: Task 1~6 전부
- Produces: 로컬 n8n 재기동 후 실행할 E2E 검증 절차 (실행 자체는 n8n import + 웹훅 트리거가 필요해 별도 세션에서 사용자와 진행)

- [ ] **Step 1: 런북에 검증 섹션 추가**

status 매핑 표 아래에 추가:
```markdown
## 참고사이트 fetch 개선 검증 (#157, #158 — 2026-07-02)

재반영: 워크플로우 4종 재import + n8n 재시작 (n8n-local-reimport-ops 메모 참조).

1. **#157 생존성**: external 탭 웹훅 실행 → 스킴 없는 href 정책(kofpi 계열)이
   워크플로우를 중단시키지 않고, 이후 정책이 계속 수집되는지 실행 로그로 확인
2. **자기 포털 필터**: 서울시 탭 정책의 enrichment 가 `NO_LINK` +
   `fetchDiagnostics[].outcome=SELF_PORTAL` 로 적재되는지 (기존: TOO_SHORT)
3. **cookie jar**: molit.go.kr 계열 참고사이트가 REDIRECT_LOOP 대신 OK 로 수집되는지
4. **TLS**: fill4young.kinfa.or.kr 이 TLS_ERROR 없이 수집되는지 (Task 6 Step 4 선행)
5. **진단 분포**: `SELECT enrichment->>'status', jsonb_array_elements(enrichment->'fetchDiagnostics')->>'outcome', count(*) FROM policies WHERE source_type='YOUTH_SEOUL_CRAWL' GROUP BY 1,2;`
   로 outcome 분포 확인 (psql 은 docker exec -i 필수)
6. **회귀**: 온통청년(youth-center-seoul) 정상 수집 + guide 재생성 여부
```
(`policies` 테이블·`enrichment` 컬럼명이 실제 스키마와 다르면 `backend/docs/ENTITIES.md` 기준으로 교정할 것.)

- [ ] **Step 2: Commit**

```bash
git add docs/runbooks/youth-seoul-pipeline-verification.md
git commit -m "docs(runbook): 참고사이트 fetch 개선 E2E 검증 절차 추가 (#157, #158)"
```

---

## Self-Review 결과

- **Spec coverage**: 생존성 3겹(Task 3), 자기 포털 필터(Task 1·3), cookie jar(Task 2·3), 진단 기록(Task 3·4·5), TLS 보강(Task 6), 테스트·E2E(각 Task + Task 7) — 스펙 전 항목 매핑 확인. 스펙의 "온통청년 노드 포함" 확장도 Task 3·4에 반영.
- **Placeholder scan**: Task 6 Step 1의 `<CA Issuers URI>`는 실측값 의존이라 명령 출력에서 받아 쓰도록 명시. 그 외 TBD 없음.
- **Type consistency**: `_fetchDiagnostics`(노드) → `fetchDiagnostics`(페이로드/record), outcome 코드 집합, `prepareUrls` 반환 형태가 Task 1~5에서 일치함을 확인.
