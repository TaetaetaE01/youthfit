# 청년서울(youth-seoul) 파이프라인 — 본문 정제·참고사이트 enrichment·첨부 임베딩

> 작성: 2026-05-30
> 상태: spec

## 1. 배경

- 현재 `n8n/workflows/youth-seoul-crawl.json` 워크플로우는 청년서울(`youth.seoul.go.kr`) 의 정책 목록·상세 HTML 을 긁어서 백엔드 `/api/internal/ingestion/policies` 로 raw 전송한다. **LLM 호출도, 참고사이트 fetch 도, 첨부파일 처리도 없다.**
- 반면 `youth-center-seoul.json` (온통청년) 워크플로우는 `pick-link`(URL 3개 fetch + cheerio cleanedText 추출 + 첨부 후보 수집) 와 `promote-attachments`(확장자 화이트리스트 기반 attachment 승격) 노드를 통해 enrichment 와 첨부 임베딩 후보를 백엔드로 전달하고 있다.
- 사용자 보고: (a) 청년서울 출처 정책의 본문에 `----->` 같은 잔재 마커가 남는다. (b) 참고사이트 영역을 fetch 해서 본문을 풍부하게 만들고 싶다. (c) 정책 페이지의 첨부파일 7개 중 정보성 첨부만 골라 RAG 임베딩에 포함하고 싶다. (d) 어드민 5단계 status 패널에서 어느 단계에서 실패했는지 정확히 표시되는지 확인.
- 분석 결과 (`/tmp/youth-seoul-tab1.html` 직접 fetch):
  - 청년서울 페이지에는 한 줄짜리 HTML 주석(`<!-- ... -->`) 잔재가 다수 있으며, 본문 td 안에도 등장한다.
  - `extractByTh` 의 `/<[^>]+>/g` 정규식은 한 줄 안에서만 매칭되므로 줄바꿈을 포함한 멀티라인 주석을 못 지운다.
  - 같은 정규식이 본문 텍스트의 `<민원인이 제출해야 하는 서류>` 같은 비태그 토큰도 잘못 지운다.
  - 사용자가 준 URL `/infoData/youthPlcyInfo/view.do?key=2309160001` 은 청년서울의 "중앙정부/타지역 정책" 카테고리이며, 현재 워크플로우(`/plcyInfo/view.do?key=2309150002` "서울 자체 정책") 와 **다른 path/key 라서 수집 자체가 안 된다**.
  - 청년서울 정책 페이지에는 `참고 사이트 Ⅰ/Ⅱ`, `신청 사이트`, `관련 사이트` 4개 영역에 외부 URL 이 들어있어 enrichment fetch 대상으로 충분하다.

## 2. 목표 / 비목표

### 목표
- youth-seoul-crawl 워크플로우가 **두 카테고리**(서울 자체 + 중앙정부/타지역) 를 한 번에 순회한다.
- 상세 파싱이 멀티라인 HTML 주석을 제거하고, 본문 내 `<>` 텍스트를 보존하며, 잔재 dash·dash-화살표를 제거한다.
- 참고사이트 / 신청 사이트 / 관련 사이트 URL 을 모아 fetch(`pick-link`) 후 cleanedText 와 첨부 후보를 추출한다.
- 첨부 후보를 확장자 화이트리스트 + 파일명 키워드 필터로 선별해 `rawData.attachments[]` 에 승격한다.
- 백엔드의 기존 ingestion → enrichment 단계가 청년서울 출처에서 정확하게 SUCCESS/SKIPPED/FAILED 로 마킹되는지 확인하고, 부족하면 후속 spec 으로 분리한다.

### 비목표 (이번 spec 범위 밖)
- 백엔드 신규 `PolicyEnrichmentService` (브레인스토밍 옵션 B). 이번에는 n8n 에서 fetch 한다.
- 어드민 패널 UI 변경. 점검만 한다.
- 청년서울 외 출처(온통청년·복지로) 의 동작 변경.
- 첨부 다운로드 / PDF·HWP 텍스트 추출 / RAG 인덱싱 자체. 이미 `AttachmentDownloadService`, `AttachmentExtractionScheduler`, `AttachmentReindexService` 가 존재하며 백엔드는 받은 `attachments[]` 를 기존 흐름으로 처리한다.
- enrichment LLM 섹션 추출(온통청년의 `gpt-4o-mini` 단계). 청년서울 본문은 이미 잘 구조화되어 있어 LLM 비용을 추가할 가치가 낮다.
- 페이지 수 제한 해제. 현재 `maxPage = Math.min(maxPage, 2)` TEST 캡은 별도 정리 (메모리: `n8n_test_workflow_state`).

## 3. 결정 사항 (브레인스토밍 결과)

| 항목 | 결정 | 근거 |
|---|---|---|
| enrichment 위치 | n8n (옵션 A) | 온통청년 패턴 그대로 이식이 가장 빠르고 일관됨. fixture(`enrichment-merge/enrich.mjs`) 재사용 가능 |
| 카테고리 수집 | 단일 워크플로우에 카테고리 2개 루프 | 사용자 선택. 한 schedule, 한 백엔드 흐름. 카테고리 init 노드에서 메타 배열 정의 |
| 본문 정제 | n8n 정규식 보강 (LLM 후처리 없음) | 비용·변동성 회피. 잔재 마커는 규칙 기반 정제로 충분 |
| 태그 제거 정규식 | 영문/숫자 시작 패턴만 매칭 (`<\/?[a-zA-Z][^>]*>`) | 본문 텍스트 `<민원인...>` 보존 |
| 멀티라인 주석 | `<!--[\s\S]*?-->` 사전 제거 | 줄바꿈 포함 주석 잔재 제거 |
| 첨부 필터 | 확장자 화이트리스트 + 파일명 키워드 둘 다 | 사용자 선택. 7개 중 정보성 첨부만 선별 |
| 키워드 화이트 | `공고\|안내\|모집\|요강\|신청서\|계획서\|FAQ\|Q&A\|가이드\|설명\|일정\|참가\|운영\|평가\|선정\|채용\|지원` | 청년정책 공고 관행 빈출 어휘 |
| 키워드 블랙 | `로고\|배너\|아이콘\|썸네일\|포스터\|광고\|favicon` | 비정보성 시각 자료 명시 제외 |
| fixture sync | 양 워크플로우(`promote-attachments`) 모두 키워드 필터 추가 동기화 | 기존 `// 동기화 책임:` 코멘트 규약 준수. 온통청년에도 키워드 필터를 같이 적용 |
| 어드민 status 점검 | 코드 변경 없이 확인만, 부족하면 후속 spec | 본 spec 의 스코프 분리 |

## 4. 흐름 (수정 후)

```
[Schedule(매일 03:00)]
   ↓
[카테고리 init: 2개]   ← 신규
   ↓
[카테고리 loop]         ← 신규 (splitInBatches)
   ↓
   ├─ [페이지 초기화]
   ↓
   ├─ [목록 페이지 요청]   ← URL 의 path/key 가 카테고리 파라미터로 치환됨
   ↓
   ├─ [plcyBizId 추출]
   ↓
   ├─ [정책별 순차 처리]    ← splitInBatches(1)
   ↓
   ├──→ [다음 페이지 확인] → [다음 페이지 존재?]
   │           ↓ true                  ↓ false (카테고리 loop 다음 iteration)
   │      [다음 페이지 이동]
   │           ↑
   │      [목록 페이지 요청]
   ↓
   ├──→ [3초 대기 (Rate Limit)]
   ↓
   ├──→ [상세 페이지 요청]
   ↓
   ├──→ [상세 데이터 파싱]   ← 정규식 보강 + refUrls/selfAttachments 추출
   ↓
   ├──→ [pick-link]          ← 신규: refUrls fetch → cleanedText/extraAttachments
   ↓
   ├──→ [enrichment-meta]    ← 신규: _enrichment 객체 합성
   ↓
   ├──→ [promote-attachments] ← 신규: 확장자+키워드 필터링하여 attachments[] 승격
   ↓
   └──→ [백엔드 API 전송]
```

## 5. 변경 상세

### 5.1 `상세 데이터 파싱` 노드 (`parse-detail`) — `extractByTh` 정규식 보강

```js
function extractByTh(thText) {
  const escapedTh = thText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(
    '<th[^>]*>' + escapedTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>',
    'i'
  );
  const match = html.match(regex);
  if (!match) return '';
  return match[1]
    .replace(/<!--[\s\S]*?-->/g, '')               // (신규) 멀티라인 HTML 주석 제거
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/?[a-zA-Z][^>]*>/g, '')            // (변경) 영문 시작 태그만 — 본문 <민원인> 보존
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\t/g, '')
    .replace(/^[\s ]*-{3,}[\s ]*$/gm, '') // (신규) dash 만 있는 줄 제거
    .replace(/-{4,}>/g, '')                         // (신규) dash 화살표 잔재
    .replace(/\n\s*\n/g, '\n')
    .trim();
}
```

추가로 같은 노드에서 외부 URL / 첨부 후보 추출:

```js
// 참고/관련/신청 사이트 영역 외부 URL 수집
function extractRefUrls(html) {
  const sections = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];
  const urls = new Set();
  for (const th of sections) {
    const escTh = th.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp('<th[^>]*>' + escTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>', 'i');
    const m = html.match(re);
    if (!m) continue;
    const hrefRe = /href="(https?:\/\/[^"]+)"/gi;
    let hm;
    while ((hm = hrefRe.exec(m[1])) !== null) urls.add(hm[1]);
  }
  return Array.from(urls).slice(0, 3);
}

// 정책 페이지 자체의 첨부파일 후보 (a[href] 중 문서 확장자)
function extractSelfAttachments(html) {
  const extPattern = /\.(pdf|hwp|hwpx|docx?|xlsx?|zip)(\?|$|#)/i;
  const out = [];
  const seen = new Set();
  const linkRe = /<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let lm;
  while ((lm = linkRe.exec(html)) !== null) {
    const href = lm[1];
    if (!extPattern.test(href)) continue;
    const abs = /^https?:\/\//i.test(href) ? href : 'https://youth.seoul.go.kr' + (href.startsWith('/') ? href : '/' + href);
    if (seen.has(abs)) continue;
    seen.add(abs);
    const text = lm[2].replace(/<[^>]+>/g, '').trim().slice(0, 200);
    out.push({ name: text || abs.split('/').pop(), url: abs });
  }
  return out;
}
```

이 둘의 결과를 `result.rawData._refUrls` 와 `result.rawData._selfAttachments` 로 임시 필드 저장 (downstream 노드에서 사용 후 제거).

### 5.2 `카테고리 init` 노드 (`init-category`, 신규)

```js
return [
  { json: { catKey: '2309150002', catPath: 'plcyInfo',      catLabel: '서울 자체' } },
  { json: { catKey: '2309160001', catPath: 'youthPlcyInfo', catLabel: '중앙정부/타지역' } }
];
```

- 기존 `페이지 초기화` 앞에 배치
- 카테고리 loop 는 `splitInBatches(batchSize=1)` 로 순차 처리

목록 요청 URL:
```
https://youth.seoul.go.kr/infoData/{catPath}/ctList.do?key={catKey}&tabKind=002&pageIndex={n}&orderBy=regYmd+desc&blueWorksYn=N&sw=
```

상세 요청 URL:
```
https://youth.seoul.go.kr/infoData/{catPath}/view.do?plcyBizId={id}&tab=001&key={catKey}&tabKind=002
```

### 5.3 `pick-link` 노드 (신규)

- `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs` 의 `selectUrls / mergeFetchResults / fetchAndExtract` 함수를 그대로 사용
- 청년서울에서는 `selectUrls(policy)` 가 온통청년 키(`aplyUrlAddr / refUrlAddr1 / refUrlAddr2`) 를 보던 것을 일반화해서 **`policy.refUrls[]` 배열을 우선 보고, 없으면 기존 3개 키 fallback** 으로 시그니처를 확장한다. 양 워크플로우가 같은 fixture 를 재사용한다.
- 출력 필드: `_cleanedText`, `_extraAttachments`, `_enrichmentStatus` (`OK | NO_LINK | FETCH_FAILED | TOO_SHORT`)

### 5.4 `enrichment-meta` 노드 (신규, 온통청년 동일 형태)

```js
const p = $input.first().json;
const e = {
  sourceUrl: (p.rawData._refUrls && p.rawData._refUrls[0]) || null,
  fetchedAt: new Date().toISOString(),
  extractor: 'regex',                // LLM 사용 안 함
  confidence: null,
  status: p._enrichmentStatus || 'FETCH_FAILED',
  sections: null,
  extraAttachments: p._extraAttachments || [],
  cleanedText: p._cleanedText || null
};
return [{ json: { ...p, rawData: { ...p.rawData, enrichment: e } } }];
```

### 5.5 `promote-attachments` 노드 (신규 + fixture 보강)

`__fixtures__/promote-attachments/promote.mjs` 에 키워드 필터 추가:

```js
const NAME_WHITELIST = /(공고|안내|모집|요강|신청서|계획서|FAQ|Q&A|가이드|설명|일정|참가|운영|평가|선정|채용|지원)/i;
const NAME_BLACKLIST = /(로고|배너|아이콘|썸네일|포스터|광고|favicon)/i;

function isInformationalName(name) {
  if (typeof name !== 'string' || !name) return true;   // 이름 없으면 일단 통과
  if (NAME_BLACKLIST.test(name)) return false;
  return NAME_WHITELIST.test(name) || name.length >= 5; // 키워드 없어도 5자 이상이면 통과 (보수적)
}
```

기존 확장자 매핑 후 추가 필터:
```js
const mediaType = inferMediaType(ex);
if (!mediaType) continue;
if (!isInformationalName(ex.name)) continue;          // (신규) 키워드 필터
```

`selfAttachments` 도 같은 노드에서 처리하도록 입력 합치기:
```js
const enrichmentExtras = enrichment?.extraAttachments || [];
const selfExtras = input?.rawData?._selfAttachments || [];
const extras = [...enrichmentExtras, ...selfExtras];
```

### 5.6 fixture 동기화 책임

- `__fixtures__/promote-attachments/promote.mjs` 와 `__fixtures__/enrichment-merge/enrich.mjs` 의 코멘트 규약("동기화 책임: ... 와 동일 알고리즘이어야 한다") 유지
- 키워드 필터를 fixture 에 반영 → **youth-center-seoul.json 의 `promote-attachments` 노드도 같은 필터로 업데이트**
- 온통청년에 키워드 필터를 적용하면 기존 통과되던 첨부 중 일부가 제외될 수 있다. 영향 범위 점검을 위해 fixture 테스트(`__fixtures__/promote-attachments/promote.test.mjs` 가 있다면) 의 픽스처 케이스를 확장한다.

### 5.7 `백엔드 API 전송` 노드 (변경 없음)

- 기존 페이로드 구조에 `attachments[]` 와 `enrichment` 가 추가될 뿐, 새 필드도 백엔드가 이미 인식 가능한 형태 (온통청년 동일 스키마)

## 6. 백엔드 점검 항목 (코드 변경 없이 확인)

수정 배포 후 청년서울 정책 5건을 어드민 `/admin/policies/processing/:id` 패널에서 열어 확인한다:

| 케이스 | 기대 상태 | 검증 포인트 |
|---|---|---|
| refUrls 있고 fetch 성공 | ENRICHMENT = SUCCESS | reference 표에 fetch 한 URL · cleanedText 길이 |
| refUrls 없음 | ENRICHMENT = SKIPPED, reason=`NO_LINK` | 백엔드가 `enrichment.status === 'NO_LINK'` → SKIPPED 매핑하는지 확인 |
| refUrls 있는데 fetch 실패 | ENRICHMENT = FAILED | reason 에 FETCH_FAILED 또는 TOO_SHORT |
| 첨부 있고 다운로드 성공 | RAG_INDEXING = SUCCESS | attachment 표에 EXTRACTED 상태 |
| 첨부 있는데 다운로드/추출 실패 | RAG_INDEXING = FAILED 또는 SKIPPED | attachment 표에 FAILED + retry_count |

만약 위 매핑이 어긋난다 (예: 청년서울 출처는 enrichment 단계가 항상 SKIPPED 로 박혀있고 reason 정보가 누락) — 별도 spec(`2026-MM-DD-policy-enrichment-status-mapping`) 으로 분리하여 `EnrichmentJobService` / `PolicyProcessingStep` 매핑 보강.

## 7. 테스트 전략

- **fixture 단위 테스트** (n8n `__fixtures__/`):
  - `extractByTh` 입력 케이스: 멀티라인 주석 + 텍스트 내 `<>` + 정상 td
  - `extractRefUrls` 입력: refUrl 영역 0~4개 케이스 (NO_LINK 경계)
  - `extractSelfAttachments` 입력: 정책 페이지 자체 첨부 a 태그
  - `promote-attachments` 입력: 7개 첨부 중 정보성 3개만 통과해야 함
- **수동 검증**: 워크플로우 dryrun(`maxPage = 1` 임시 유지) → 백엔드 로그에서 enrichment·attachment payload 확인 → 어드민 패널에서 5단계 status 가시화 확인
- **회귀 보호**: 온통청년 워크플로우의 기존 첨부 통과율 변화 모니터링 (fixture 테스트로 가시화)

## 8. 리스크 / 운영 주의

- **키워드 필터 false-negative**: 청년서울 정책 중 파일명이 단순한 경우(예: `attachment.pdf`) 과도하게 제외될 수 있다. 통과 조건에 "키워드 있거나, 이름 길이 ≥ 5자" 로 보수적 fallback 포함.
- **카테고리 추가로 인한 수집량 증가**: 두 카테고리 합산 시 처리 시간 2배. `3초 대기` 가 충분한 rate limit 인지 청년서울 robots 정책 재확인 (현재 robots.txt 의 Crawl-delay 가 그 이하인지).
- **fixture 동기화 누락 위험**: 키워드 필터를 fixture 에만 넣고 워크플로우 JSON 의 인라인 코드와 어긋나면 다음 변경에서 회귀. fixture 자동 동기화 메커니즘이 없다면 PR 체크리스트로 보호.
- **본문 정제 over-aggressive**: dash 제거가 정책 본문의 의도된 dash list 를 깰 수 있다 (예: `- 항목1`). 현재 규칙은 "**dash 만**" 으로 구성된 줄에만 적용 → safety 확보.

## 9. 후속 작업 후보 (이 spec 범위 밖)

- 백엔드 enrichment 의 status 표시 누락이 발견되면 보강 spec
- LLM 기반 첨부 분류기 (cost 검토 후)
- `maxPage = 2` TEST 캡 제거 후 운영 적용 (n8n_test_workflow_state 메모 참조)
- 청년서울 다른 탭(tabKind != 002) 의 정책 카테고리도 수집 대상으로 검토
