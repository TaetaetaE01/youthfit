# 몽땅청년 3-카테고리 크롤 재설계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 청년몽땅정보통(youth.seoul.go.kr)의 3개 카테고리(서울시·자치구·중앙/타지역) 정책을 2026 등록분 기준으로 정확히 수집한다. 페이지네이션·region·자격요건(support_target)·externalId 를 올바르게 채우고, 카테고리 루프 버그를 워크플로우 분리로 원천 제거한다.

**Architecture:** 기존 단일 `youth-seoul-crawl.json`(카테고리 루프 + `.first()` 안티패턴으로 사실상 1페이지만 수집)을 폐기하고, 카테고리별 워크플로우 3개로 분리한다. 서울시·자치구는 동일 상세 템플릿(`plcyInfo/view.do`)을 공유하므로 파서 알고리즘을 fixture(.mjs) 로 공유·고정한다. 복지로의 external-hash 중복 스킵 패턴을 재사용하되, 그 전제인 `externalId(=plcyBizId)` 적재를 선결한다.

**Tech Stack:** n8n(httpRequest 4.2, code 노드, splitInBatches, webhook), Node fixture 러너(`node verify.mjs`), 백엔드 `/api/internal/ingestion/policies` 수신(계약 변경 없음).

**관련 스펙:** `docs/superpowers/specs/2026-06-06-mongttang-youth-crawl-redesign-design.md`
**관련 메모:** youth-seoul-loop-rearchitecture, youth-seoul-attachment-limit, n8n-local-reimport-ops, prod-seed-2026

---

## 핵심 제약·운영 규칙 (메모리 반영)

- **n8n CLI execute 불가** → 워크플로우 검증은 import + 재시작 후 `*-manual` 웹훅 POST 로만 가능 (n8n-local-reimport-ops).
- **fixture 동기화 책임**: `__fixtures__/*/*.mjs` 알고리즘은 워크플로우 노드 jsCode 의 미러다. 한쪽 변경 시 둘 다 갱신 (각 README 의 "동기화 책임").
- **첨부 한계**: 일부 첨부는 WebGate·JS 동적로딩으로 수집 불가(보류). 본문 텍스트 머지는 동작 (youth-seoul-attachment-limit).
- **source_type**: 셋 다 `YOUTH_SEOUL_CRAWL` (enum 값과 정렬). 현재 워크플로우는 `'YOUTH_SEOUL'` 을 보내 백엔드 fallback 으로 "우연히" 동작 중 → 명시적으로 정렬.

## 선결 과제 (조사로 확정된 현행 버그)

1. **externalId 미적재** → external-hash dedup 불가. `rawData.externalId = plcyBizId` 적재가 외부 hash 스킵의 전제.
2. **source type 불일치** (`YOUTH_SEOUL` → enum 없음, fallback). `YOUTH_SEOUL_CRAWL` 로 정렬.
3. **region 하드코딩** (`상세 데이터 파싱` 의 `region:'서울'`) → 카테고리별 region 주입.
4. **support_target(자격요건) 미파싱** → 적합도 룰 미생성. 상세 파서에 `additionalQualification`/`targetTags` 추출 추가.
5. **페이지네이션 미작동** (lastPage=2 오산정, 세션쿠키 미유지) → 실증 후 수정.
6. **카테고리 루프 + `.first()` 안티패턴** (url L66, jsCode L100) → 워크플로우 분리로 제거.

## 백엔드 수신 계약 (참고, 변경 없음)

- `POST {BACKEND_URL}/api/internal/ingestion/policies`, 헤더 `X-Internal-Api-Key`. body = `{ source, rawData, pipeline? }`.
- `source`: `{ url, type, fetchedAt }` 모두 필수. `type` = `YOUTH_SEOUL_CRAWL`.
- `rawData` 필수: `title`, `body`, `category`, `region`. 본 작업 핵심 추가: `externalId`(=plcyBizId), `additionalQualification`(자격요건), `applyUrl`, `referenceSites[]`, `attachments[]`, `enrichment{...}`.
- external-hash 조회: `GET {BACKEND_URL}/api/internal/ingestion/policies/external-hashes?source=YOUTH_SEOUL_CRAWL` → `{ externalId: source_hash, ... }` flat JSON.

---

## Task 1: 실증(Spike) — 사이트 동작·HTML 구조 확정

> 외부 사이트 의존 항목(세션쿠키 필요 여부, 페이지네이션 동작, 상세 셀렉터, region 위치)을 실제 응답으로 확정한다. 이 산출물이 이후 파서/노드 코드를 결정한다. **코드 작성 전 반드시 수행.**

**Files:**
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/samples/` (실제 HTML 샘플 저장 디렉토리)
- Create: `docs/superpowers/specs/2026-06-06-mongttang-spike-findings.md` (실증 결과 기록)

- [ ] **Step 1: 서울시 목록 페이지네이션·세션쿠키 실증**

식별 가능한 User-Agent 로 1·2페이지를 받아 비교한다 (`.claude/rules/common.md` 크롤링 규칙 준수):

```bash
UA="YouthFitBot/1.0 (+https://youthfit; contact: gkgk123563@gmail.com)"
BASE="https://www.youth.seoul.go.kr/infoData/plcyInfo/ctList.do?key=2309150002&tabKind=002"

# 쿠키 없이 pageIndex=1 vs 2 비교 (응답 본문 첫 plcyBizId 가 같으면 pageIndex 무시 → 세션쿠키 필요)
curl -sS -A "$UA" -c /tmp/yf_cookie.txt "${BASE}&pageIndex=1" -o /tmp/p1.html
curl -sS -A "$UA" -b /tmp/yf_cookie.txt "${BASE}&pageIndex=2" -o /tmp/p2_withcookie.html
curl -sS -A "$UA" "${BASE}&pageIndex=2" -o /tmp/p2_nocookie.html

grep -oE "goView\('[A-Za-z0-9]+'\)" /tmp/p1.html | head -3
echo "--- p2 with cookie ---"; grep -oE "goView\('[A-Za-z0-9]+'\)" /tmp/p2_withcookie.html | head -3
echo "--- p2 no cookie ---";   grep -oE "goView\('[A-Za-z0-9]+'\)" /tmp/p2_nocookie.html | head -3
echo "--- lastPage 단서 ---";  grep -oE "fn_egov_link_page\([0-9]+\)" /tmp/p1.html | sort -u | tail -5
echo "--- Set-Cookie ---";     curl -sS -A "$UA" -D - "${BASE}&pageIndex=1" -o /dev/null | grep -i "set-cookie"
```

**확정 기준:**
- p2_withcookie ≠ p2_nocookie 이고 p2_nocookie == p1 이면 → **세션쿠키(JSESSIONID) 유지 필요** (Task 3 에서 세션 노드 구현).
- p2_nocookie ≠ p1 이면 → 쿠키 불필요, GET `pageIndex` 만으로 동작.

- [ ] **Step 2: 자치구 목록 endpoint 확인**

```bash
GU="https://www.youth.seoul.go.kr/infoData/plcyInfo/guList.do?key=2309150002&tabKind=003"
curl -sS -A "$UA" -b /tmp/yf_cookie.txt "${GU}&pageIndex=1" -o /tmp/gu1.html
grep -oE "goView\('[A-Za-z0-9]+'\)" /tmp/gu1.html | head -5
grep -oE "fn_egov_link_page\([0-9]+\)" /tmp/gu1.html | sort -u | tail -3
# region(구명)이 목록 항목 어디에 있는지: 자치구명 패턴 탐색
grep -oE "(종로구|중구|용산구|성동구|광진구|동대문구|중랑구|성북구|강북구|도봉구|노원구|은평구|서대문구|마포구|양천구|강서구|구로구|금천구|영등포구|동작구|관악구|서초구|강남구|송파구|강동구)" /tmp/gu1.html | sort -u | head
```

**확정 기준:** 목록에 구명이 노출되면 목록에서 region 추출, 아니면 상세에서 추출 (Step 4 에서 확인).

- [ ] **Step 3: 상세 HTML 샘플 저장 (서울시·자치구·중앙/타지역 각 1건)**

Step1·2 에서 얻은 `plcyBizId` 로 상세를 받아 fixture 샘플로 저장:

```bash
mkdir -p n8n/workflows/__fixtures__/youth-seoul-detail/samples
# 서울시 (V2026… id), 자치구 (2026…숫자 id), 중앙/타지역
curl -sS -A "$UA" -b /tmp/yf_cookie.txt \
  "https://www.youth.seoul.go.kr/infoData/plcyInfo/view.do?plcyBizId=<CITY_ID>&tab=001&key=2309150002&tabKind=002" \
  -o n8n/workflows/__fixtures__/youth-seoul-detail/samples/city-sample.html
curl -sS -A "$UA" -b /tmp/yf_cookie.txt \
  "https://www.youth.seoul.go.kr/infoData/plcyInfo/view.do?plcyBizId=<DISTRICT_ID>&tab=001&key=2309150002&tabKind=003" \
  -o n8n/workflows/__fixtures__/youth-seoul-detail/samples/district-sample.html
curl -sS -A "$UA" \
  "https://www.youth.seoul.go.kr/infoData/youthPlcyInfo/view.do?plcyBizId=<EXTERNAL_ID>&key=2309160001" \
  -o n8n/workflows/__fixtures__/youth-seoul-detail/samples/external-sample.html
```

- [ ] **Step 4: 상세 셀렉터 확정**

저장한 샘플에서 다음 필드의 DOM 위치(라벨 텍스트·테이블 구조)를 기록한다:
- 제목, 본문, **지원대상/자격요건**(support_target 출처), 신청기간(applyStart/End), 신청 사이트(applyUrl)
- 참고/신청 사이트 섹션 라벨(`관련 사이트`/`신청 사이트`/`참고 사이트 Ⅰ`/`참고 사이트 Ⅱ`)
- 자치구: region(구명) 위치
- 중앙/타지역(`youthPlcyInfo/view.do`)은 구조가 다르므로 별도 기록

Run: `grep -nE "지원\s*대상|자격|신청\s*기간|관련\s*사이트|신청\s*사이트|참고\s*사이트" n8n/workflows/__fixtures__/youth-seoul-detail/samples/city-sample.html | head -20`

- [ ] **Step 5: 실증 결과 문서화**

`docs/superpowers/specs/2026-06-06-mongttang-spike-findings.md` 에 기록:
- 세션쿠키 필요 여부(결론 + 근거)
- 서울시/자치구 lastPage 규모, 2026 컷 판정 방법(ID prefix `V2026`/`2026` vs 등록일)
- 상세 필드별 셀렉터 표 (서울시·자치구 공유 / 중앙·타지역 별도)
- 자치구 region 추출 규칙

- [ ] **Step 6: Commit**

```bash
git add -f n8n/workflows/__fixtures__/youth-seoul-detail/samples \
        docs/superpowers/specs/2026-06-06-mongttang-spike-findings.md
git commit -m "docs(spike): 몽땅청년 사이트 동작·상세 HTML 구조 실증 결과"
```

---

## Task 2: 공유 상세 파서 fixture (서울시·자치구)

> `plcyInfo/view.do` 상세 HTML → `rawData` 변환 알고리즘을 fixture(.mjs)로 고정한다. 이 `.mjs` 가 Task 3·4 워크플로우의 `상세 데이터 파싱` 노드 jsCode 의 권위 미러가 된다. externalId/region/support_target 적재를 여기서 보장한다.

**Files:**
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/parse-plcyinfo.mjs` — 파서 알고리즘
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/verify.mjs` — 케이스 러너
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/README.md` — 동기화 책임 명시
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/cases-html/city-2026.input.html` (Task1 샘플 축소본)
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/cases-html/city-2026.expected.json`
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/cases-html/district-2026.input.html`
- Create: `n8n/workflows/__fixtures__/youth-seoul-detail/cases-html/district-2026.expected.json`

- [ ] **Step 1: 파서 함수 작성 (실증 셀렉터 반영)**

`parse-plcyinfo.mjs`. cheerio 를 쓰는 기존 `enrichment-merge/enrich.mjs` 패턴을 따른다. **셀렉터는 Task 1 Step 4 의 실측값으로 채운다.**

```javascript
import * as cheerio from 'cheerio';

/**
 * plcyInfo/view.do 상세 HTML → rawData.
 * @param {string} html  상세 페이지 HTML
 * @param {object} ctx   { plcyBizId, region, sourceUrl }
 *   - region: 'city' 면 '서울특별시', 'district' 면 호출자가 추출한 구명을 그대로 전달
 */
export function parsePlcyInfoDetail(html, ctx) {
  const $ = cheerio.load(html);

  // === 셀렉터는 Task1 실증으로 확정한 값으로 교체 ===
  const title = $('SELECTOR_TITLE').first().text().trim();
  const body = $('SELECTOR_BODY').text().replace(/\s+\n/g, '\n').trim();
  const supportTarget = labelValue($, '지원대상') || labelValue($, '자격요건') || '';
  const applyStart = normalizeDate(labelValue($, '신청기간', 'start'));
  const applyEnd = normalizeDate(labelValue($, '신청기간', 'end'));
  const applyUrl = $('SELECTOR_APPLY_LINK').attr('href') || '';

  const refLabels = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];
  const refUrls = refLabels
    .map(l => sectionLink($, l))
    .filter(Boolean)
    .slice(0, 3);

  const selfAttachments = $('SELECTOR_ATTACHMENT a')
    .map((_, el) => ({ name: $(el).text().trim(), url: abs($(el).attr('href'), ctx.sourceUrl) }))
    .get();

  return {
    externalId: ctx.plcyBizId,           // ← external-hash dedup 전제
    title,
    body,
    category: '복지',                     // 백엔드 mapCategory 가 본문/태그로 재분류
    region: ctx.region,                  // ← 하드코딩 제거, 호출자 주입
    additionalQualification: supportTarget, // ← support_target → 적합도 룰 생성
    applyStart,
    applyEnd,
    applyUrl,
    _refUrls: refUrls,
    _selfAttachments: selfAttachments,
  };
}

function labelValue($, label, which) { /* 실증 구조 기반 라벨→값 추출. which='start'|'end' 면 범위 분해 */ return ''; }
function sectionLink($, label) { return ''; }
function normalizeDate(s) { if (!s) return null; const m = String(s).match(/(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})/); return m ? `${m[1]}-${m[2].padStart(2,'0')}-${m[3].padStart(2,'0')}` : null; }
function abs(href, baseUrl) { if (!href) return ''; try { return new URL(href, baseUrl).href; } catch { return href; } }
```

> `labelValue`/`sectionLink`/`SELECTOR_*` 는 Task 1 실증 HTML 구조로 **실제 구현해야 한다**. placeholder 로 커밋하지 말 것. 본문 추출은 기존 `enrich.mjs` 의 텍스트 정제 로직(`_cleanedText` 만드는 부분)을 참고해 동일 정제(공백/스크립트 제거)를 적용한다.

- [ ] **Step 2: 케이스 입출력 작성**

Task 1 샘플 HTML 을 `cases-html/city-2026.input.html` 로 축소 저장(상세 핵심 영역만). 파서를 한 번 실행해 얻은 결과를 검토·교정 후 `city-2026.expected.json` 으로 저장. 자치구도 동일. expected 에는 `externalId`, `region`(city=`서울특별시`, district=구명), `additionalQualification` 이 비어있지 않아야 한다.

- [ ] **Step 3: verify 러너 작성**

`verify.mjs` 는 `enrichment-merge/verify.mjs`(cases-html 처리) 패턴을 복제하되 `parsePlcyInfoDetail` 을 호출한다. region 은 케이스별 `.meta.json`(`{ "region": "서울특별시" }` 또는 `{ "region": "중랑구" }`)에서 주입.

- [ ] **Step 4: 테스트 실행 (실패 → 셀렉터 교정 → 통과)**

Run: `cd n8n/workflows/__fixtures__/youth-seoul-detail && node verify.mjs`
Expected: 처음엔 FAIL(셀렉터 미확정) → Step1 셀렉터 채우고 expected 교정 → 전부 `PASS`

- [ ] **Step 5: README 동기화 책임 명시**

`promote-attachments/README.md` 형식을 따라, `parse-plcyinfo.mjs` 와 `youth-seoul-city.json`/`youth-seoul-district.json` 의 `상세 데이터 파싱` 노드가 동기화 미러임을 명시.

- [ ] **Step 6: Commit**

```bash
git add -f n8n/workflows/__fixtures__/youth-seoul-detail
git commit -m "test(n8n): plcyInfo 상세 파서 fixture (externalId·region·support_target)"
```

---

## Task 3: youth-seoul-city 워크플로우 (서울시)

> 기존 `youth-seoul-crawl.json` 을 복제해 **단일 카테고리**로 축소한다. 카테고리 루프(`카테고리별 순차 처리` splitInBatches + `.first()`)를 제거하고, 세션쿠키(실증 결과 시)·페이지네이션·2026컷·external-hash 스킵·externalId 적재·region 주입·source type 정렬을 반영한다.

**Files:**
- Create: `n8n/workflows/youth-seoul-city.json`

- [ ] **Step 1: 베이스 복제**

```bash
cp n8n/workflows/youth-seoul-crawl.json n8n/workflows/youth-seoul-city.json
```

워크플로우 `name` → `youth-seoul-city`, 웹훅 `path` → `youth-seoul-city-manual`, `webhookId` → `youth-seoul-city-manual-trigger`. 스케줄 트리거 cron 유지(`0 3 * * *`).

- [ ] **Step 2: 카테고리 루프 제거**

`카테고리 초기화`·`카테고리별 순차 처리`·`수집 결과 펼치기` 노드를 삭제하고, 트리거 → `세션 확립`(Step3) → `페이지 초기화` 로 직접 연결한다. 카테고리 상수는 `페이지 초기화` 또는 신규 `설정` 노드에 인라인:

```javascript
// 페이지 초기화 (code)
return [{ json: {
  pageIndex: 1,
  listBase: 'https://www.youth.seoul.go.kr/infoData/plcyInfo/ctList.do?key=2309150002&tabKind=002',
  detailBase: 'https://www.youth.seoul.go.kr/infoData/plcyInfo/view.do',
  detailSuffix: '&tab=001&key=2309150002&tabKind=002',
  region: '서울특별시',
  sourceType: 'YOUTH_SEOUL_CRAWL',
} }];
```

`목록 페이지 요청` url 의 `$('카테고리별 순차 처리').first().json.listBase` (L66) → `$json.listBase` 로, `plcyBizId 추출` jsCode 의 `const cat = $('카테고리별 순차 처리').first().json;` (L100) → `const cat = $('페이지 초기화').first().json;` 로 교체. **`.first()` 안티패턴 완전 제거.**

- [ ] **Step 3: 세션쿠키 노드 (실증 결과가 "필요"일 때만)**

Task1 Step1 이 세션쿠키 필요로 결론났으면, 트리거 직후 `세션 확립`(httpRequest) 추가:
- url: `={{ $json.listBase }}&pageIndex=1` 또는 별도 홈 GET
- options.response: `{ response: { response: { fullResponse: true } } }` (헤더 접근용)
- 다음 `JSESSIONID 추출`(code):

```javascript
// JSESSIONID 추출 (code)
const res = $input.first().json;
const setCookie = (res.headers && (res.headers['set-cookie'] || res.headers['Set-Cookie'])) || [];
const arr = Array.isArray(setCookie) ? setCookie : [setCookie];
const m = arr.join(';').match(/JSESSIONID=([^;]+)/);
const init = $('페이지 초기화').first().json;
return [{ json: { ...init, cookie: m ? `JSESSIONID=${m[1]}` : '' } }];
```

이후 `목록 페이지 요청`·`상세 페이지 요청` httpRequest 의 headerParameters 에 `Cookie: ={{ $json.cookie }}` 추가. (실증이 "불필요"면 이 Step 생략)

- [ ] **Step 4: 페이지네이션 + 2026 컷오프**

`plcyBizId 추출`(code) 를 교체. lastPage 계산 + 2026 항목만 채택 + 전부 2025↓면 중단 신호:

```javascript
// plcyBizId 추출 (code)
const cat = $('페이지 초기화').first().json;
const html = $input.first().json.data || $input.first().json.body || '';
const ids = [...html.matchAll(/goView\('([A-Za-z0-9]+)'\)/g)].map(m => m[1]);

// 2026 판정: id prefix (서울시 'V2026…', 자치구 '2026…') — 실증으로 확정
const is2026 = (id) => /^V?2026/.test(id);
const ids2026 = ids.filter(is2026);
const anyOlder = ids.some(id => !is2026(id));   // 2025↓ 출현 → 마지막 페이지

const pages = [...html.matchAll(/fn_egov_link_page\((\d+)\)/g)].map(m => +m[1]);
const lastPage = pages.length ? Math.max(...pages) : (cat.pageIndex || 1);

const store = $getWorkflowStaticData('global');
store.collected = store.collected || [];
for (const id of ids2026) store.collected.push({ plcyBizId: id });

const reachedCut = anyOlder || ids2026.length === 0;
return [{ json: {
  ...cat,
  currentPage: cat.pageIndex,
  lastPage,
  hasNext: !reachedCut && cat.pageIndex < lastPage,
  collectedCount: store.collected.length,
} }];
```

`다음 페이지 확인`/`다음 페이지 존재?`(if `={{ $json.hasNext === true }}`)/`다음 페이지 이동`(`pageIndex` 증가, cookie·cat 유지) 연결 유지.

- [ ] **Step 5: external-hash 중복 스킵 (bokjiro 패턴 복제)**

페이지 루프 종료 후, `정책별 순차 처리` 진입 전에 bokjiro 의 `외부 hash 조회`·`신규 servId 필터` 노드를 복제·치환:

`외부 hash 조회`(httpRequest, executeOnce:true, retryOnFail):
- url: `={{ $env.BACKEND_URL || 'http://backend:8080' }}/api/internal/ingestion/policies/external-hashes`
- queryParameters: `source = YOUTH_SEOUL_CRAWL`
- header: `X-Internal-Api-Key: {{ $env.INTERNAL_API_KEY }}`, response json, timeout 15000

`신규 plcyBizId 필터`(code) — bokjiro `신규 servId 필터` 의 키를 plcyBizId 로:

```javascript
const hashMap = $('외부 hash 조회').first().json || {};
const store = $getWorkflowStaticData('global');
const items = store.collected || [];
const out = [];
for (const p of items) {
  if (hashMap[p.plcyBizId] == null) out.push({ json: p }); // DB 미존재 신규만
}
store.collected = []; // 다음 실행 위해 리셋
if (out.length === 0) return [{ json: { empty: true } }];
return out;
```

`정책별 순차 처리` 앞에 `상세 대상 여부 (if)` (`={{ $json.plcyBizId ? true : false }}`) 추가 — 빈 캐리어는 상세 스킵.

- [ ] **Step 6: 상세 파싱 노드를 fixture 미러로 교체 + region/externalId 주입**

`상세 데이터 파싱` jsCode 를 Task2 `parse-plcyinfo.mjs` 의 `parsePlcyInfoDetail` 본문과 **동일 알고리즘**으로 교체(n8n code 노드는 import 불가 → 함수 본문 인라인). `region: '서울특별시'`, `externalId: plcyBizId` 가 출력 `rawData` 에 포함되도록. `상세 페이지 요청` url:

```
={{ $('페이지 초기화').first().json.detailBase }}?plcyBizId={{ $json.plcyBizId }}{{ $('페이지 초기화').first().json.detailSuffix }}
```

- [ ] **Step 7: source type 정렬 + 백엔드 전송**

`백엔드 API 전송` 직전 노드(또는 `enrichment 메타 합성`)에서 최종 payload 의 `source.type` 을 `YOUTH_SEOUL_CRAWL` 로, `source.url` = 상세 URL, `source.fetchedAt` = `={{ $now.toISO() }}` 로 구성. `참고사이트 fetch + 머지`·`enrichment 메타 합성`·`attachments 승격` 노드는 기존 로직 유지(인계).

- [ ] **Step 8: import + 웹훅 E2E (로컬)**

메모리 n8n-local-reimport-ops 절차:

```bash
# n8n 컨테이너에 import + 재시작 (정확한 명령은 기존 운영 메모/스크립트 확인)
# import 후:
curl -sS -X POST "http://localhost:5678/webhook/youth-seoul-city-manual" -H "Content-Type: application/json" -d '{}'
```

검증(psql 은 `-i` 필수):

```bash
docker exec -i <pg> psql -U <user> -d <db> -c \
"SELECT source_type, region, count(*), count(additional_qualification) AS with_target
 FROM policy p JOIN policy_source s ON s.policy_id=p.id
 WHERE s.source_type='YOUTH_SEOUL_CRAWL' GROUP BY 1,2;"
```

Expected: `region='서울특별시'`, `source_type='YOUTH_SEOUL_CRAWL'`, 2026 정책 다수, `with_target` > 0(support_target 채워짐).

- [ ] **Step 9: 재실행 dedup 확인**

같은 웹훅을 한 번 더 POST → 신규 0건(external-hash 스킵). 로그/건수로 확인.

- [ ] **Step 10: Commit**

```bash
git add n8n/workflows/youth-seoul-city.json
git commit -m "feat(n8n): youth-seoul-city 워크플로우 (서울시, 페이지네이션·2026컷·dedup·externalId)"
```

---

## Task 4: youth-seoul-district 워크플로우 (자치구)

> Task 3 city 를 복제해 endpoint·region 만 바꾼다. 상세 파서는 city 와 **공유**(`plcyInfo/view.do`).

**Files:**
- Create: `n8n/workflows/youth-seoul-district.json`

- [ ] **Step 1: city 복제**

```bash
cp n8n/workflows/youth-seoul-city.json n8n/workflows/youth-seoul-district.json
```

`name` → `youth-seoul-district`, 웹훅 `path` → `youth-seoul-district-manual`, `webhookId` 갱신.

- [ ] **Step 2: endpoint·tabKind 치환**

`페이지 초기화` 의 listBase → `.../plcyInfo/guList.do?key=2309150002&tabKind=003`, detailSuffix → `&tab=001&key=2309150002&tabKind=003`. 2026 prefix 정규식이 자치구 ID 형식(`2026…숫자`)을 포함하는지 Task1 실증으로 확인(`/^V?2026/` 가 둘 다 커버).

- [ ] **Step 3: region 구명 추출**

`상세 데이터 파싱` 호출 시 region 을 `'서울특별시'` 고정이 아니라 **구명**으로. Task1 Step2/Step4 결과에 따라:
- 목록에 구명이 있으면 `plcyBizId 추출` 단계에서 항목별 `guName` 을 함께 수집해 상세까지 전달.
- 상세에만 있으면 `parsePlcyInfoDetail` 의 region 인자를 상세에서 추출한 구명으로.

구명 추출 헬퍼(목록/상세 텍스트에서):

```javascript
const GU = ['종로구','중구','용산구','성동구','광진구','동대문구','중랑구','성북구','강북구','도봉구','노원구','은평구','서대문구','마포구','양천구','강서구','구로구','금천구','영등포구','동작구','관악구','서초구','강남구','송파구','강동구'];
function extractGu(text){ const hit = GU.find(g => text.includes(g)); return hit || '서울특별시'; }
```

- [ ] **Step 4: import + 웹훅 E2E**

```bash
curl -sS -X POST "http://localhost:5678/webhook/youth-seoul-district-manual" -H "Content-Type: application/json" -d '{}'
```

검증: `region` 이 구명(예: `중랑구`)으로 들어가는지, 2026 자치구 정책이 적재되는지 psql 로 확인.

- [ ] **Step 5: Commit**

```bash
git add n8n/workflows/youth-seoul-district.json
git commit -m "feat(n8n): youth-seoul-district 워크플로우 (자치구, region 구명 추출)"
```

---

## Task 5: youth-seoul-external 워크플로우 (중앙/타지역)

> `youthPlcyInfo/list.do` + `youthPlcyInfo/view.do`. 상세 구조가 plcyInfo 와 달라 **별도 파서**. 페이지 1개(2026분).

**Files:**
- Create: `n8n/workflows/youth-seoul-external.json`
- Create: `n8n/workflows/__fixtures__/youth-seoul-external-detail/` (별도 파서 fixture, Task2 구조 복제)

- [ ] **Step 1: 별도 파서 fixture (youthPlcyInfo/view.do)**

Task1 Step3 의 `external-sample.html` 로 Task2 와 동일한 fixture 구조(`parse-youthplcyinfo.mjs` + `verify.mjs` + cases-html)를 만든다. ref/apply 섹션 라벨·필드 매핑을 youthPlcyInfo 실제 구조로 구현. region 은 상세에서 타지역/전국 판별. `externalId = plcyBizId` 적재.

Run: `cd n8n/workflows/__fixtures__/youth-seoul-external-detail && node verify.mjs` → PASS

- [ ] **Step 2: 워크플로우 작성**

city 를 복제하되:
- listBase → `.../youthPlcyInfo/list.do?key=2309160001`, detailBase → `.../youthPlcyInfo/view.do`, detailSuffix → `&key=2309160001`
- 세션쿠키 노드 제거(실증상 불필요), 페이지 루프는 1페이지(2026분)만
- `상세 데이터 파싱` 을 `parse-youthplcyinfo.mjs` 미러로
- source type `YOUTH_SEOUL_CRAWL`, external-hash 스킵 동일 적용

- [ ] **Step 3: import + 웹훅 E2E**

```bash
curl -sS -X POST "http://localhost:5678/webhook/youth-seoul-external-manual" -H "Content-Type: application/json" -d '{}'
```

검증: region 이 타지역/전국, externalId 적재, dedup 동작.

- [ ] **Step 4: Commit**

```bash
git add n8n/workflows/youth-seoul-external.json
git add -f n8n/workflows/__fixtures__/youth-seoul-external-detail
git commit -m "feat(n8n): youth-seoul-external 워크플로우 (중앙/타지역, 별도 파서)"
```

---

## Task 6: 기존 워크플로우 폐기

**Files:**
- Delete: `n8n/workflows/youth-seoul-crawl.json`

- [ ] **Step 1: 로컬 n8n 에서 기존 워크플로우 비활성/삭제**

메모리 n8n-local-reimport-ops 의 delete 제약 확인 후 비활성화 또는 삭제. (운영 활성 상태 메모 `docs/runbooks/` 갱신)

- [ ] **Step 2: 파일 삭제**

```bash
git rm n8n/workflows/youth-seoul-crawl.json
```

- [ ] **Step 3: 메모리 n8n-test-workflow-state 의 maxPage=2 임시 제한 정리 확인**

해당 임시 제한이 구 워크플로우에만 있었다면 폐기로 해소됨. 신규 3개에 임시 제한이 남지 않았는지 grep 확인:

Run: `grep -rn "maxPage" n8n/workflows/youth-seoul-*.json`
Expected: 임시 제한 없음(또는 의도된 1페이지 external 만)

- [ ] **Step 4: Commit**

```bash
git add -A n8n/workflows
git commit -m "chore(n8n): 구 youth-seoul-crawl 폐기, 3-카테고리 분리로 대체"
```

---

## Task 7: 통합 E2E·문서·메모리 갱신

- [ ] **Step 1: 3개 워크플로우 순차 E2E**

세 웹훅을 순차 POST 후 카테고리별 수집 건수·region·support_target·dedup 을 한 번에 검증:

```bash
for c in city district external; do
  curl -sS -X POST "http://localhost:5678/webhook/youth-seoul-${c}-manual" -H "Content-Type: application/json" -d '{}'
done
docker exec -i <pg> psql -U <user> -d <db> -c \
"SELECT region, count(*) total, count(additional_qualification) with_target
 FROM policy p JOIN policy_source s ON s.policy_id=p.id
 WHERE s.source_type='YOUTH_SEOUL_CRAWL' GROUP BY region ORDER BY total DESC;"
```

**완료 기준(스펙 테스트 항목):**
- 카테고리별 수집 건수 > 0 (서울시·자치구·중앙/타지역)
- 2026 컷오프: 2025↓ 정책 미적재
- region 정확성: 서울시=서울특별시, 자치구=구명, 중앙/타지역=타지역/전국
- support_target 채워짐 → 적합도 룰 생성 확인 (`eligibility_rule` 류 테이블 조회)
- 참고/신청 사이트 본문 머지 확인
- 재실행 시 신규만 적재 (external-hash dedup)

- [ ] **Step 2: 적합도 룰 생성 확인**

support_target 이 채워진 정책에 대해 적합도 룰이 생성됐는지 확인 (백엔드 eligibility 처리 트리거). 구 워크플로우에서 5건 모두 룰 미생성이던 문제가 해소됐는지 대조.

- [ ] **Step 3: 문서 갱신**

- `backend/docs/INGESTION_PIPELINE.md`: 몽땅청년 3-카테고리 구조·source type·externalId 적재 반영.
- `docs/OPS.md` 또는 `docs/runbooks/`: 신규 3 웹훅 트리거 운영 절차.

- [ ] **Step 4: 메모리 갱신**

- `youth-seoul-loop-rearchitecture`: 3-카테고리 분리 완료로 갱신.
- `prod-seed-2026`: 신규 수집분 반영.
- `n8n-test-workflow-state`: maxPage=2 임시 제한 해소 반영.

- [ ] **Step 5: Commit**

```bash
git add backend/docs/INGESTION_PIPELINE.md docs/OPS.md docs/runbooks
git commit -m "docs(n8n): 몽땅청년 3-카테고리 크롤 운영 절차·파이프라인 갱신"
```

---

## 알려진 한계 / 범위 밖

- **교차소스 중복**: 중앙/타지역은 온통청년(YOUTH_CENTER)과 ~50% 제목 중복. 본 작업은 **소스 내** external-hash dedup 만 처리. 교차소스 제목 중복 제거는 조회 레이어/후속 작업.
- **첨부 일부 수집 불가**: WebGate·JS 동적로딩 첨부는 보류(youth-seoul-attachment-limit). 본문 텍스트 머지는 동작.
- **세션쿠키**: Task1 실증 결과에 따라 구현 여부 결정. 기존 참고 노드가 전무하므로 city/district 의 신규 설계.

## 의존성 메모

- 본 플랜은 백엔드 계약 변경이 없으나, `externalId` 가 `policy_source.external_id` 로 저장돼야 external-hash dedup 이 성립한다. 적재 후 `policy_source.external_id` 가 채워지는지 Task3 Step8 에서 확인.
- 첨부 임베딩 게이트 플랜(`2026-06-06-attachment-embedding-llm-gate.md`)과 **독립**. 순서 무관.
