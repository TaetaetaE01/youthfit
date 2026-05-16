# promote-attachments fixtures

`youth-center-seoul.json` 의 `attachments 승격` 노드가 만족해야 하는 입출력 계약을 픽스처로 고정한다.

## 구조

- `promote.mjs` — n8n 노드의 jsCode 와 **동일 알고리즘** 을 담은 독립 ES module. 이 파일은 단위 검증 전용이며, 실제 데이터 흐름에는 사용되지 않는다.
- `verify.mjs` — `cases/*.input.json` 을 읽어 `promote()` 를 적용하고 `cases/*.expected.json` 과 비교한다.
- `cases/case-*.input.json` / `cases/case-*.expected.json` — 케이스별 입력/기대 출력.

> Task 6 시점에 `n8n/workflows/youth-center-seoul.json` 에 `attachments 승격` 노드가 추가된다. 그 전까지 `promote.mjs` 는 픽스처 검증 전용이며 단일 출처(single source) 다.

요구 런타임: Node.js 18+ (top-level await, `node:fs/promises`, `node:assert/strict` 사용).

## 실행

```bash
node n8n/workflows/__fixtures__/promote-attachments/verify.mjs
```

전체 케이스 PASS 면 exit 0, 한 건이라도 FAIL 이면 exit 1.

## 동기화 책임

**⚠ `promote.mjs` 와 `youth-center-seoul.json` 의 `attachments 승격` 노드 jsCode 는 항상 동일 로직이어야 한다.** 한쪽을 수정하면 다른쪽도 같은 변경을 반영해야 한다. 노드 jsCode 가 변경됐는데 픽스처 검증이 깨지면, 의도된 변경이라면 픽스처를 갱신하고, 아니면 노드 jsCode 를 되돌려라.

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
