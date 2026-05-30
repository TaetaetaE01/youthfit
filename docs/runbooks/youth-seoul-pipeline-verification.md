# youth-seoul 파이프라인 보강 — 배포 후 검증 체크리스트

> spec: `docs/superpowers/specs/2026-05-30-youth-seoul-enrichment-and-attachments-design.md`
> 관련 plan: `docs/superpowers/plans/2026-05-30-youth-seoul-enrichment-and-attachments.md`

## 카테고리 모델 (구현 시 plan 에서 수정됨)

plan 의 초기 가정(`catPath`/`catKey` 만 치환, 동일 `ctList.do`)이 실제 사이트와 달라 수정했다.
`init-category` 노드가 카테고리별 **전체 URL 템플릿**을 담는다.

| 카테고리 | 목록 엔드포인트 | 상세 엔드포인트 | plcyBizId 형식 |
|---|---|---|---|
| 서울 자체 | `plcyInfo/ctList.do?key=2309150002&tabKind=002` (+ `&orderBy=regYmd+desc&blueWorksYn=N&sw=`) | `plcyInfo/view.do?...&tab=001&key=2309150002&tabKind=002` | `V202600006` (영문+숫자) |
| 중앙정부/타지역 | `youthPlcyInfo/list.do?key=2309160001` (cat1 전용 파라미터 붙이면 결과 달라짐 → 붙이지 않음) | `youthPlcyInfo/view.do?...&key=2309160001` | `20260528005400113227` (20자리 숫자) |

- `youthPlcyInfo/ctList.do` 는 404 → 반드시 `list.do`.
- `extract-ids` 의 goView regex 는 `[A-Za-z0-9]+` 로 확장돼 양 형식을 모두 잡는다.
- 페이지네이션은 양쪽 모두 `pageIndex` (`fn_egov_link_page`).
- **caveat**: 중앙정부/타지역은 `list.do` 기본 목록이 ~10건(1페이지)으로 관측됨. `sc_searchSe=youthPlcy001(중앙정부)/youthPlcy002(타지역)` 세분화는 이번 범위에 넣지 않았다. 분량이 부족하면 후속 spec 으로 세분화 검토.

## 배포 직후 (워크플로우만)

1. n8n 워크플로우 1회 수동 실행
   - 이 브랜치의 `extract-ids` 에는 `maxPage` TEST 캡이 없다(메인 워킹트리의 임시 캡은 미반영). 첫 dryrun 은 부하를 줄이려면 아래 "TEST 캡" 참고.
2. 백엔드 로그에서 `/api/internal/ingestion/policies` 수신 확인
3. 받은 payload 의 `rawData.enrichment` 와 `rawData.attachments` 형태가 온통청년과 동형인지 sample 5건 확인
   - `enrichment` 키: `sourceUrl, fetchedAt, extractor('regex'), confidence(null), status, sections(null), extraAttachments, cleanedText`
   - 양 카테고리(서울 자체 / 중앙정부·타지역)에서 각각 sample 확보

## 어드민 5단계 status 검증

청년서울 출처 정책 5건을 `/admin/policies/processing/{id}` 패널에서 열어 다음 표를 채운다.

| 정책 ID | 카테고리 | refUrls 개수 | 첨부 후보 개수 | enrichment.status | ENRICHMENT step | RAG_INDEXING step | 비고 |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

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
- 정상 dash list (`- 항목1`, `- 항목2`) 는 영향 없는가? (3개 이상 dash 줄만 제거됨)

## 첨부 필터 부작용 점검

온통청년 출처도 키워드 필터가 같이 적용됐다 (fixture sync). 다음을 본다:

- 배포 전 후 온통청년 정책 sample 5건의 `attachments[]` 개수 차이가 ±1 이내인가?
- 기존에 들어가던 `공고문.pdf`, `신청서.hwp`, `보고서`, `양식`, `다운로드` 같은 정보성 첨부가 빠지지 않았는가?
  - (화이트리스트에 문서유형 단어를 확장해서 짧은 한글 정보성 이름도 통과하도록 했다.)
- 새로 빠진 첨부가 `로고|배너|아이콘|썸네일|포스터|광고|favicon` 패턴에 해당하는가?

## TEST 캡 (선택)

부하를 줄이며 dryrun 하려면 `extract-ids` 노드 jsCode 의 maxPage 계산 직후 임시 캡을 넣는다:
```js
maxPage = Math.min(maxPage, 2); // TEST: 2페이지만 크롤링
```
검증 완료 후 반드시 제거(또는 환경변수 분기)한다. 운영 적용 시 캡이 남아있으면 안 된다.

## fixture 회귀 (배포 전 로컬)

```bash
cd n8n/workflows/__fixtures__/youth-seoul-parse-body && node verify.mjs
cd ../enrichment-merge && node verify.mjs
cd ../promote-attachments && node verify.mjs
```
세 디렉토리 모두 통과해야 한다 (인라인 jsCode 와 fixture 가 동일 알고리즘).
