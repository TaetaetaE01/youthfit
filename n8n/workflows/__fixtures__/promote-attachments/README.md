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
