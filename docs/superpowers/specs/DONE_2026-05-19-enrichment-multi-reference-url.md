# 온통청년 enrichment — 여러 reference URL 머지 — Design Spec

- 날짜: 2026-05-19
- 스코프: 온통청년(YOUTH_CENTER) 워크플로우 한정
- 후속 작업 기반: `2026-05-16-youth-center-attachment-promotion-design.md` 의 단대단 검증 중 발견된 한계

## 1. 배경

`youth-center-seoul.json` 의 "링크 선택" 노드는 외부 안내 URL 후보 중 **첫 번째 하나만** enrichment 대상으로 고른다:

```js
const url = (p.aplyUrlAddr && p.aplyUrlAddr.trim()) ||
            (p.refUrlAddr1 && p.refUrlAddr1.trim()) ||
            (p.refUrlAddr2 && p.refUrlAddr2.trim()) || null;
```

따라서 다음 두 가지가 발생한다:

- **풍부도 손실**: 첫 번째 URL이 SPA거나 본문이 빈약하면 enrichment 가 TOO_SHORT 로 빠지고, fallback 위치에 있는 더 풍부한 URL은 fetch조차 안 된다.
- **첨부 누락**: 후순위 URL에 있는 다운로드 가능한 PDF/HWP 첨부 후보가 `extraAttachments` 에 들어오지 못한다.

### 1.1 실제 사례 (2026-05-19 단대단 검증 중 발견)

정책 id=160 "심리상담 바우처 사업":

```
reference_sites:
  1. https://www.socialservice.or.kr:444/       ← 워크플로우가 선택, 본문 OK + ie_manual.pdf 1건만 발견
  2. https://www.mohw.go.kr/menu.es?mid=a10706040800   ← 사용자 확인: 정보가 훨씬 풍부, 그러나 fetch 안 됨
```

mohw.go.kr 페이지는 보건복지부의 공식 안내라 본문/첨부 모두 더 풍부한데, 우리 워크플로우는 보지 못했다.

## 2. 목표

여러 reference URL 의 본문과 첨부 후보를 enrichment 단계에서 **모두 수집해 머지**, RAG/Guide 컨텍스트의 풍부도를 높인다.

비목표:
- 외부 도메인 화이트리스트 / SSRF 가드 (별도 후속)
- 백엔드 변경 (이번도 워크플로우 한정)
- enrichment 의 LLM 구조화 추출 알고리즘 변경

## 3. 접근 후보 (brainstorming 시 선택)

| | A. 다중 URL fetch + 머지 (n8n 분기) | B. 별도 reference 단위로 enrichment 분리 | C. 첫 URL 실패 시 fallback 시도만 |
|---|---|---|---|
| 풍부도 | 가장 풍부 (모든 URL 통합) | 풍부 (URL별 분리 컨텍스트) | 보통 |
| 워크플로우 복잡도 | 중 (분기 + merge) | 큼 (각 URL 마다 LLM 호출) | 작음 |
| LLM 비용 | 1회 (머지 후 추출) | N회 (URL 마다) | 1-2회 (fallback 횟수만큼) |
| 백엔드 영향 | 없음 | 있음 (PolicyEnrichment 모델에 reference별 분리) | 없음 |

**기본 권장**: A. 다중 URL fetch + cleanedText 머지 + extraAttachments union (dedup by URL).

## 4. 후보 A 의 아키텍처 (제안)

```
[정책 → 링크 선택 변형]
   │  reference URL 후보 N개 추출 (aplyUrlAddr, refUrlAddr1, refUrlAddr2 등)
   │  배열로 출력: [{ _enrichUrl: url1 }, { _enrichUrl: url2 }, ...]
   ▼
[URL별 fetch loop] (splitInBatches batchSize=1)
   │  각 URL 별로 HTTP GET + boilerplate 제거 + 첨부 후보 추출
   │  → 각 항목: { _cleanedText, _extraAttachments[], _enrichmentStatus }
   ▼
[URL별 결과 머지 노드]
   │  cleanedText: 모든 OK URL 의 본문을 separator 로 연결
   │  extraAttachments: union, URL 기준 dedup
   │  status: 어느 하나라도 OK 면 OK, 모두 실패면 가장 심한 상태 코드 (FETCH_FAILED > TOO_SHORT 등)
   │  sourceUrl: 우선순위 첫 URL 로 유지 (호환성)
   ▼
[LLM 구조화 추출]  ← 머지된 cleanedText 를 단일 입력으로 추출
   ▼
[enrichment 객체 조립]
   ▼
[IngestPolicyRequest 변환]
   ▼
[attachments 승격] ← 이번 작업 (2026-05-16 spec)
   ▼
[백엔드 API 전송]
```

## 5. 입출력 계약 (제안)

### 5.1 변경 노드: "링크 선택"

**입력**: 정책 JSON

**출력 (변경)**:
- 다중 출력 (n8n splitInBatches가 받을 수 있는 형태)
- 각 출력 item:
  - `_enrichUrl`: string (절대 URL)
  - `_enrichUrlIndex`: int (우선순위 인덱스, 0=aplyUrl, 1=refUrl1, ...)
  - 정책 메타 (...meta)

### 5.2 신규 노드: "enrichment 머지"

**입력**: URL별 fetch + boilerplate 제거 결과 N개

**동작**:
1. 각 결과의 `_enrichmentStatus` 검사
2. cleanedText 머지: status가 명시적으로 실패가 아닌 모든 URL 의 텍스트를 separator (`\n\n---\n\n` 등) 로 연결. 최대 길이 cap (기존 8000자에서 16000자로 확장 권장).
3. extraAttachments union: URL 기준 dedup (대소문자 무시).
4. 최종 status:
   - 어느 하나라도 cleanedText 가 충분(≥200자) → OK
   - 모두 TOO_SHORT → TOO_SHORT
   - 모두 FETCH_FAILED → FETCH_FAILED
   - 혼합 (일부 OK, 일부 실패) → OK (사용 가능한 텍스트가 있으므로)
5. 머지된 텍스트가 너무 길면 LLM 추출 비용 증가 → cap 신중히

**출력**: 기존 enrichment object 구조 그대로 (`_cleanedText`, `_extraAttachments`, `_enrichmentStatus`)

## 6. 에러 처리

| 시나리오 | 동작 |
|---|---|
| 모든 reference URL 부재 | 기존 "enrich 안함" 분기 그대로 통과 |
| 모든 URL fetch 실패 | status=FETCH_FAILED, extraAttachments=[] |
| 일부 fetch 성공 | 성공한 것만 머지, status=OK |
| 동일 URL 중복 | URL 정규화 (lowercase, trailing slash 제거) 후 1회만 fetch |
| URL 수가 비현실적으로 많음 | 안전을 위해 fetch URL 수 cap (예: 최대 3개) |

## 7. 테스트

### 7.1 픽스처 (n8n 단위 검증)

`n8n/workflows/__fixtures__/enrichment-merge/` 에 케이스 추가:

- `case-single-url-ok` — 기존 동작 회귀 (1개 URL만 있는 정책)
- `case-multi-url-all-ok` — 2개 URL 모두 OK, cleanedText 머지 + extraAttachments union
- `case-multi-url-mixed` — 1번 TOO_SHORT, 2번 OK → status=OK, 2번 본문/첨부 사용
- `case-multi-url-all-fail` — 모두 실패 → status=FETCH_FAILED
- `case-duplicate-url` — aplyUrl == refUrl1 → 1회만 fetch
- `case-dedup-attachments` — 두 URL 에 동일 첨부 URL 등장 → 1회만 등록

### 7.2 백엔드 회귀

PolicyEnrichment 모델은 변경 안 함. cleanedText 가 길어진 것 외 API 계약 동일. 기존 ingestion 테스트가 통과해야 한다.

### 7.3 단대단 검증

정책 160 ("심리상담 바우처 사업") 재실행:

```
이전 (단일 URL): socialservice.or.kr 만 fetch, 첨부 1건 (ie_manual.pdf)
이후 (멀티 URL): + mohw.go.kr 추가 fetch, 첨부 후보 증가 예상
```

확인:
- `policy.enrichment.cleanedText` 가 두 페이지 텍스트 머지된 형태로 늘어남
- `policy_attachment` row 수 증가 (확장자 명시 URL 기준)
- ATTACHMENT 청크 수 증가
- 가이드 품질 변화 (정성 평가)

## 8. 운영/모니터링

- enrichment 머지 후 cleanedText 평균 길이 변화 추적
- URL별 fetch 성공률 (관찰성 메트릭 추가 권장)
- LLM 추출 토큰 사용량 증가 (cleanedText cap 적정선 모니터링)

## 9. 후속 작업 후보

- reference URL 별 가중치 (공식 부처 도메인 우대)
- enrichment 결과를 URL 별로 분리 보존 (PolicyEnrichment 모델 확장)
- 동시 fetch 병렬화 (n8n 의 splitInBatches 는 직렬 — 다른 메커니즘 필요)
- robots.txt / rate-limit 동적 가드

## 10. 우선순위 / 종속성

- `2026-05-16` 첨부 승격 작업 의존: 이번 spec 의 멀티 URL 머지가 적용되어야 첨부 후보가 더 많이 잡힘 → 첨부 승격 효과 증대
- 별개 spec: `2026-05-19-promote-attachment-mediatype-heuristic.md` (mediaType 추론 강화) — 두 작업이 모두 적용되어야 한국 정부 사이트의 다양한 첨부 URL 패턴을 잡을 수 있음
