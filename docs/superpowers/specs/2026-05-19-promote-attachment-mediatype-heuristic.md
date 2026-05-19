# promote 노드 — mediaType 추론 휴리스틱 강화 — Design Spec

- 날짜: 2026-05-19
- 스코프: `n8n/workflows/__fixtures__/promote-attachments/promote.mjs` + `youth-center-seoul.json` 의 `attachments 승격` 노드
- 후속 작업 기반: `2026-05-16-youth-center-attachment-promotion-design.md` 의 단대단 검증 중 발견된 한계

## 1. 배경

현재 promote 노드는 URL 의 **확장자 명시** 에만 의존해 mediaType 을 추론한다:

```js
function extractExt(url) {
  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();
  const dotIdx = cleaned.lastIndexOf('.');
  if (dotIdx === -1) return null;
  return cleaned.slice(dotIdx + 1);
}
// 매핑: pdf | hwp | hwpx | doc | docx | xls | xlsx
```

문제는 한국 정부/공공 사이트의 첨부 URL이 대부분 **확장자를 노출 안 하는 다운로드 핸들러 패턴**이라는 점:

```
https://www.k-startup.go.kr/afile/fileDownload/{hash}    ← 확장자 없음
https://www.saemangeum.go.kr/common/fileDown.do?key=...&type=brd
https://bokjiro.go.kr/ssis-tbu/CmmFileUtil/getDownload.do?atcflId=...&atcflSn=1
```

### 1.1 실제 사례 (2026-05-19 단대단 검증 중 발견)

YOUTH_CENTER 페이지 1 25건의 정책 enrichment 결과:
- 첨부 후보를 가진 정책: 5건
- 그 첨부 후보들의 URL 패턴 분석:
  - **23건 중 1건만 확장자 명시** (`ie_manual.pdf` — 보조 안내 PDF, 정책의 메인 첨부 아님)
  - 나머지는 모두 `fileDownload/{hash}`, `fileDown.do?key=`, `javascript:;` 등

비교: BOKJIRO 정책 30건의 PolicyAttachment URL도 모두 확장자 없는 핸들러 패턴 (`getDownload.do?atcflId=...`) 이지만 **mediaType은 정확**. 이는 BOKJIRO 공식 API 응답이 mediaType 메타를 직접 제공하기 때문 — n8n 휴리스틱과 무관.

**결론**: 우리 promote 노드는 의도적으로 보수적으로 설계됐지만, **실 환경에서 효과를 내려면 mediaType 추론을 더 적극적으로 해야 한다**.

## 2. 목표

URL 확장자 외의 보완 신호를 활용해 mediaType 을 추론, 한국 정부 사이트의 일반적인 다운로드 핸들러 URL 도 promote 통과시킨다.

비목표:
- 첨부 다운로드 자체의 검증 (그건 backend `AttachmentDownloadService` 책임)
- 잘못 추론된 mediaType 으로 인한 다운로드 실패 가능성은 backend whitelist + UNSUPPORTED_MIME skip 으로 흡수
- HTML/zip 등 광범위 타입 자동 분류 (별도 spec 권장)

## 3. 신호 후보 (brainstorming 시 선택)

| 신호 | 정확도 | 비용 | 비고 |
|---|---|---|---|
| A. link text 키워드 | 중 | 0 | "[PDF] 다운로드", "(hwp)" 같은 텍스트 |
| B. img alt 키워드 | 중 | 0 | 첨부 아이콘 alt가 확장자명 (`<img alt="pdf">`) |
| C. URL path 키워드 | 중 | 0 | URL에 `pdf`, `hwp` 등 부분 문자열이 있을 때 |
| D. URL query 파라미터 | 약 | 0 | `?type=brd` 처럼 사이트별 의미 부여 — 일반화 어려움 |
| E. HEAD 요청 Content-Type | 강 | 중 (네트워크 1회) | 가장 정확. 사이트별 redirect, CORS, 인증 이슈 가능 |
| F. backend 다운로드 시 Content-Type | 강 | 0 (스코프 변경) | 백엔드에서 multipart sniff 후 mediaType 결정. 워크플로우 책임 외 |

**기본 권장 진화 경로**:
1. 우선 **A + B (텍스트/alt 키워드)** 만 추가 — 비용 0, 일부 케이스 즉시 개선
2. 효과 부족하면 **E (HEAD 요청)** 추가 — 비용 발생하지만 정확도 큼
3. 장기적으로 **F (백엔드에서 결정)** — 워크플로우 책임 축소, 별도 큰 변경

## 4. 단계 1 설계: 텍스트/alt 휴리스틱 (이번 spec 의 본문)

### 4.1 입력 (PolicyEnrichment.extraAttachments 의 한 항목)

```ts
{
  name: string,   // boilerplate 제거 노드에서 link text 그대로 (또는 img alt 폴백)
  url: string
}
```

### 4.2 추론 우선순위

```js
function inferMediaType(item) {
  // 1) URL 확장자 (기존 로직 그대로)
  const fromUrl = mapExt(extractExtFromUrl(item.url));
  if (fromUrl) return fromUrl;

  // 2) link text/name 안의 확장자 키워드
  // 예: "신청서_2026.pdf", "참가신청양식.hwp", "(PDF) 안내문"
  const fromName = mapExt(extractExtFromText(item.name));
  if (fromName) return fromName;

  // 3) URL path 안의 확장자 키워드 (확장자 없이 path에 포함된 경우)
  // 예: ".../downloadPdf?...", ".../hwpView/..."
  const fromPath = mapExt(extractExtFromPath(item.url));
  if (fromPath) return fromPath;

  return null;
}
```

### 4.3 매핑 규칙 (확장 — `mapExt`)

```js
const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
};
```

기존과 동일. 텍스트/path 에서 발견된 키워드도 같은 매핑.

### 4.4 텍스트 추출 (`extractExtFromText`)

```js
function extractExtFromText(text) {
  if (!text) return null;
  // 패턴 1: 파일명 형태 "*.pdf"
  const m1 = text.match(/\.(pdf|hwpx?|docx?|xlsx?)\b/i);
  if (m1) return m1[1].toLowerCase();
  // 패턴 2: 괄호 안 "(PDF)" "(HWP)"
  const m2 = text.match(/[\(\[]\s*(pdf|hwpx?|docx?|xlsx?)\s*[\)\]]/i);
  if (m2) return m2[1].toLowerCase();
  return null;
}
```

### 4.5 path 추출 (`extractExtFromPath`)

URL 확장자가 명시되지 않았더라도 path segment 중 확장자 키워드가 있는 경우:

```js
function extractExtFromPath(url) {
  const path = url.split('?')[0].split('#')[0].toLowerCase();
  // 확장자 단어가 path 의 어딘가에 등장 (downloadPdf, hwpView 등)
  const m = path.match(/\b(pdf|hwpx?|docx?|xlsx?)\b/);
  return m ? m[1] : null;
}
```

이 규칙은 false positive 위험이 있음 (URL 안에 우연히 같은 단어). 보수적 적용 필요 — 다른 신호와 결합해서만 사용하거나, 운영 단계에서 mediaType 별 분포 모니터링.

## 5. 단계 2 설계: HEAD 요청 (선택사항, 다음 작업 후보)

n8n 워크플로우에서 promote 직전에 `Content-Type` 알기 위한 HEAD 요청 노드.

```
[extraAttachments 후보들]
   ▼
[mediaType 추론 (단계 1)]
   ├─ 추론 성공 → attachments 후보 풀에 추가
   └─ 추론 실패 → HEAD 요청 노드 (작은 timeout)
         ├─ 200 + Content-Type 매핑 OK → attachments 후보 풀에 추가
         └─ 실패/매핑 불가 → 기존처럼 skip
```

문제:
- 비용: 정책당 수 차례 HEAD 요청
- 사이트별 인증/redirect/HEAD 미지원
- backend 의 AttachmentDownloadService 가 어차피 GET 다운로드 + Content-Type 검사를 하므로 **중복** 가능성

이 때문에 단계 2는 별도 spec 으로 결정 권장 (이번 spec 범위 외).

## 6. 에러 처리

| 시나리오 | 동작 |
|---|---|
| 추론 실패 (모든 신호) | 기존처럼 skip (attachments 미승격) |
| 모호한 신호 (path 키워드 false positive) | 매핑 적용. backend whitelist + UNSUPPORTED_MIME skip 으로 흡수 |
| 동일 URL 의 mediaType 추론 결과가 두 신호에서 다름 | 우선순위 (URL > name > path) 첫 신호 채택 |

## 7. 테스트

`n8n/workflows/__fixtures__/promote-attachments/cases/` 에 케이스 추가 (기존 6개에 더해):

- `case-name-has-pdf-suffix` — `name="신청서.pdf"`, URL 확장자 없음 → application/pdf
- `case-name-paren-keyword` — `name="(HWP) 신청 양식"`, URL 확장자 없음 → application/x-hwp
- `case-img-alt` — `name="<from img alt: pdf>"`, URL 확장자 없음 → application/pdf (img alt 보존 처리 필요)
- `case-path-keyword` — URL `.../downloadPdf?id=123`, name 평범 → application/pdf
- `case-no-signal` — URL `fileDownload/abc123`, name="다운로드" → skip (기존 동작)
- `case-conflicting-signals` — URL 확장자 `.pdf` + name "(HWP) 양식" → URL 우선 → application/pdf

## 8. 운영/모니터링

- 신호별 추론 분포 추적 (URL ext / name / path / skip)
- mediaType 별 `extraction_status` 통과율 — false positive 시 `SKIPPED(UNSUPPORTED_MIME)` 비율 증가하면 휴리스틱 보수 강화
- 정책당 attachments 평균 개수 증가 추세

## 9. 후속 작업 후보

- 단계 2 (HEAD 요청) 별도 spec 화
- `name` 이 빈 문자열일 때 boilerplate 노드의 img alt 보존 강화 (현재도 폴백 있지만 확장 가능)
- backend 다운로드 시점 Content-Type 기반 mediaType 보정 (워크플로우 신호와 backend 결정의 일관성 검토)

## 10. 우선순위 / 종속성

- `2026-05-16` 첨부 승격 기반 위에 mediaType 추론을 확장하는 것이므로 그 spec/구현이 선행
- `2026-05-19-enrichment-multi-reference-url.md` 와 직교 (둘 다 적용 시 효과 합산). 권장 순서: 멀티 URL 머지 먼저 → mediaType 휴리스틱 (첨부 후보 풀이 커진 다음 더 적극적인 추론 적용)
