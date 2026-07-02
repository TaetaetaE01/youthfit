# enrichment-merge fixtures

`youth-center-seoul.json` 의 `링크 fetch + 머지` 노드가 만족해야 하는 입출력 계약을 픽스처로 고정한다.

## 구조

- `enrich.mjs` — n8n 노드의 jsCode 와 **동일 알고리즘** 을 담은 독립 ES module. 이 파일은 단위 검증 전용이며, 실제 데이터 흐름에는 사용되지 않는다.
- `verify.mjs` — `cases/` 와 `cases-html/` 두 디렉토리를 읽어 검증한다.
- `cases/case-*.input.json` / `cases/case-*.expected.json` — 헬퍼(`selectUrls` + `mergeFetchResults`) 단위 케이스.
- `cases-html/case-*.input.html` + `cases-html/case-*.meta.json` + `cases-html/case-*.expected.json` — cheerio 추출(`extractCleanedAndAttachments`) 단위 케이스. `cheerio` 가 import 가능한 환경에서만 실행되며, 미설치 환경에서는 SKIP 메시지가 출력된다.

헬퍼 케이스 입력: `{ policy: {...}, fetchResults: [{ url, status, cleanedText, extraAttachments }] }`. 기대 출력: `{ selectedUrls: [...], merged: { cleanedText, extraAttachments, status } }`.
HTML 케이스 입력: 원시 HTML(.input.html) + `{ pageUrl }` (.meta.json). 기대 출력: `{ cleaned, extras: [{name,url}] }`.

> `enrich.mjs` 와 `youth-center-seoul.json` 의 `링크 fetch + 머지` 노드 jsCode 는 동기화된 미러다. 한쪽이 권위 있는 단일 출처가 아니므로, 변경 시 두 곳을 함께 갱신해야 한다 (아래 "동기화 책임" 참조).

요구 런타임: Node.js 18+ (top-level await, `node:fs/promises`, `node:assert/strict` 사용).

## 실행

```bash
# 호스트(헬퍼 케이스만 — cheerio 미설치 시 HTML 케이스 SKIP)
node n8n/workflows/__fixtures__/enrichment-merge/verify.mjs

# n8n 컨테이너(헬퍼 + HTML 케이스 모두 검증, cheerio 사전 설치돼 있음)
docker cp n8n/workflows/__fixtures__/enrichment-merge/. youthfit-n8n:/tmp/em-fix/
docker compose exec -T n8n sh -c 'cd /tmp/em-fix && node verify.mjs'
```

전체 케이스 PASS 면 exit 0, 한 건이라도 FAIL 이면 exit 1.

> 비고: 정책에 reference URL 이 하나도 없는 케이스의 `NO_LINK` 상태는 mega-node 의 `urls.length === 0` 분기에서 직접 세팅되며, `mergeFetchResults` 를 거치지 않는다. `case-empty-fetch-results` 는 머지 헬퍼만 단위로 검증한다.

## 동기화 책임

fetch 노드 알고리즘의 단일 원본은 `n8n/workflows/node-src/link-fetch-merge.js` 다.
- 워크플로우 4개(youth-seoul-city/district/external 의 "참고사이트 fetch + 머지",
  youth-center-seoul 의 "링크 fetch + 머지")의 jsCode 는
  `node node-src/sync-link-fetch-merge.mjs` 로 재생성한다. JSON 손편집 금지.
- 이 디렉토리의 `enrich.mjs` 는 순수 함수 미러다. 원본의 순수 함수를 수정하면
  같은 변경을 여기에도 반영하고 `node verify.mjs` 로 검증한다.
