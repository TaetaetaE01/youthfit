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

## 루프 아키텍처 (수집/처리 평탄화)

E2E 테스트 중 **원래 워크플로우의 기존 버그 2개**를 발견·수정했다 (원래는 cat1 1페이지만
수집하고 무한루프로 헛돌던 상태):

1. **pageIndex 무한루프**: `extract-ids` 가 `$('페이지 초기화').first()` 노드참조로 현재 페이지를
   계산했는데 n8n 캐싱 탓에 항상 1 → `check-next` 가 영원히 `hasNext=true`.
   → 목록 HTML 의 현재페이지 hidden input(`name="pageIndex" value="N"`) 파싱으로 교체.
2. **중첩 splitInBatches 미재처리**: 내부 item 루프가 page2·cat2 항목을 done 으로 흘려보냄.
   → **수집/처리 분리**. 카테고리×페이지 루프는 plcyBizId 를 카테고리 컨텍스트와 함께
   `$getWorkflowStaticData('global').collected` 에 누적만 하고, 카테고리 루프 완료 후
   `collect-all`(수집 결과 펼치기) 이 전부 펼쳐 **단일 splitInBatches** 로 일괄 처리.

흐름: `카테고리 루프 → (페이지 루프: fetch-list → extract-ids[누적] → check-next → next-page)`
→ 카테고리 done → `collect-all` → `loop-policies(단일) → detail → parse → pick-link → enrichment-meta → promote → backend`.
detail/parse 는 각 항목이 들고 있는 `detailBase/detailSuffix` 를 사용한다.

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

> **2026-05-30 로컬 관측**: 실제 백엔드는 `TOO_SHORT` → **SKIPPED** 로 매핑한다 (위 표의 FAILED 와 다름).
> `FETCH_FAILED` → FAILED 는 일치. 백엔드 무변경이라 이건 기존 매핑이며, 의도(짧은 본문은 enrichment
> skip)가 합리적이면 표를 SKIPPED 로 정정하고, 아니면 아래 "어긋남" 절차로 후속 spec 분리.

## 참고사이트 fetch 개선 검증 (#157, #158 — 2026-07-02)

재반영: 워크플로우 4종 재import + n8n 재시작 (n8n-local-reimport-ops 메모 참조).

1. **#157 생존성**: external 탭 웹훅 실행 → 스킴 없는 href 정책(kofpi 계열)이
   워크플로우를 중단시키지 않고, 이후 정책이 계속 수집되는지 실행 로그로 확인
2. **자기 포털 필터**: 서울시 탭 정책의 enrichment 가 `NO_LINK` +
   `fetchDiagnostics[].outcome=SELF_PORTAL` 로 적재되는지 (기존: TOO_SHORT)
3. **cookie jar**: molit.go.kr 계열 참고사이트가 REDIRECT_LOOP 대신 OK 로 수집되는지
4. **TLS**: fill4young.kinfa.or.kr 이 TLS_ERROR 없이 수집되는지 (Task 6 Step 4 선행)
5. **진단 분포**:
   ```sql
   SELECT p.enrichment->>'status' AS status, d->>'outcome' AS outcome, count(*)
   FROM policy p JOIN policy_source s ON s.policy_id = p.id
   CROSS JOIN LATERAL jsonb_array_elements(
     coalesce(nullif(p.enrichment->'fetchDiagnostics','null'::jsonb),'[]'::jsonb)) d
   WHERE s.source_type='YOUTH_SEOUL_CRAWL' GROUP BY 1,2;
   ```
   로 outcome 분포 확인 (psql 은 docker exec -i 필수)
6. **회귀**: 온통청년(youth-center-seoul) 정상 수집 + guide 재생성 여부

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

## E2E 검증 결과 (2026-05-30 로컬, 웹훅 운영모드)

TEST 사본(`youth-seoul-crawl.TEST.json`, gitignore — 수동/웹훅 트리거 + 페이지당 5건·2페이지 캡)을
n8n 에 import → 웹훅 트리거로 운영모드 실행해 검증.

- **수집/처리**: cat1 page1(5) + page2(5) + cat2 page1(5) = **15건 적재, 양 카테고리 모두**
  (서울 자체 + 중앙정부/타지역: 농식품 바우처·청년예술인 적립계좌·국가기술자격 응시료 등).
- **본문 정제**: 주석/`---` dash 잔재 0, 짧은 본문 0.
- **처리단계**: INGESTION·RULE·GUIDE·RAG_INDEXING 전부 SUCCESS.
- **ENRICHMENT**: ref페이지(youthConts.do 등) 내용이 짧/실패라 SKIPPED·FAILED 혼재 (데이터 의존, 파이프라인 정상).

### CLI vs 웹훅 주의

`n8n execute --id` (CLI) 는 splitInBatches 루프를 반복 실행하지 않아 첫 페이지만 처리된다.
루프 검증은 워크플로우를 **active** 로 두고 **웹훅/수동 트리거(운영모드)** 로 실행해야 한다.
(CLI 실행 시 task broker 포트 충돌은 `N8N_RUNNERS_TASK_BROKER_PORT` 를 바꿔 회피.)

### 환경 요건

- `docker-compose.yml`: `NODE_FUNCTION_ALLOW_BUILTIN=crypto,https,http` (pick-link 의 `require('http')`).

---

## 3-카테고리 재설계 E2E (머지 전 필수)

> spec: `docs/superpowers/specs/2026-06-06-mongttang-spike-findings.md`
> plan: `docs/superpowers/plans/2026-06-06-mongttang-youth-crawl-redesign.md` (Task 6·7)

단일 `youth-seoul-crawl.json`(카테고리 루프 버그로 1 페이지만 수집)을 카테고리별 3 개로
분리했다: `youth-seoul-city`(서울시 `ctList`/tabKind=002), `youth-seoul-district`(자치구
`guList`/tabKind=003), `youth-seoul-external`(중앙·타지역 `youthPlcyInfo`). 셋 다
`source_type=YOUTH_SEOUL_CRAWL`, `externalId=plcyBizId`, 세션쿠키 불필요, 상세 파서 공유.

> **이 E2E 는 PR 머지 전 사용자가 직접 수행한다.** 머지 = 신규 3 개 채택 + 구
> `youth-seoul-crawl.json` 제거. PR 의 파일 변경은 구 워크플로우 제거뿐이고, 신규 검증과
> 구 워크플로우 **로컬 비활성화**는 머지 전 이 절차에서 끝낸다.

### 1) import + 재시작 후 웹훅 트리거

n8n 워크플로우 재반영은 import + 재시작이 필수다(n8n-local-reimport-ops). 3 개를 import·active
한 뒤 순차 트리거:

```bash
for c in city district external; do
  curl -sS -X POST "http://localhost:5678/webhook/youth-seoul-${c}-manual" \
    -H "Content-Type: application/json" -d '{}'
done
```

### 2) 검증 쿼리 (psql)

```bash
docker exec -i <pg> psql -U <user> -d <db>   # psql 은 -i 필수
```

- **region 분포** (서울시=`서울특별시` 고정, 자치구=상세 제목 끝 `(○○구)` 추출·없으면 `서울특별시`,
  중앙/타지역=`타지역` 고정) + support_target 채움:

  > ⚠ external(중앙/타지역) region 은 현재 **`타지역` 고정**이다. 전국/시·도(`전국`·시·도명)
  > 분기는 **미구현** — 결과에 `타지역` 외 값은 나오지 않는다.


  ```sql
  SELECT region,
         count(*)                          AS total,
         count(additional_qualification)   AS with_target
  FROM policy p
  JOIN policy_source s ON s.policy_id = p.id
  WHERE s.source_type = 'YOUTH_SEOUL_CRAWL'
  GROUP BY region ORDER BY total DESC;
  ```

- **2026 컷오프** — 2025↓ 미적재 (external_id 앞 4 자리가 2026 인지):

  ```sql
  SELECT count(*) FILTER (WHERE external_id ~ '^V?2026') AS y2026,
         count(*) FILTER (WHERE external_id !~ '^V?2026') AS others
  FROM policy_source WHERE source_type = 'YOUTH_SEOUL_CRAWL';
  ```
  → `others = 0` 이어야 한다.

- **재실행 dedup** — 위 트리거를 1 회 더 실행한 뒤 `policy_source` 행 수가 늘지 않는지 확인:

  ```sql
  SELECT count(*) FROM policy_source WHERE source_type = 'YOUTH_SEOUL_CRAWL';
  ```
  → 1·2 회차 값이 동일해야 한다 (신규 등록분 외 증가 없음).

  > dedup 은 **두 메커니즘이 별개**다 — 재실행 시 둘 다 작동해야 행 수가 그대로다:
  > 1. **미존재 skip (신규 `plcyBizId` 필터)** — n8n 측 *상세 수집 전* 필터. `신규 plcyBizId 필터`
  >    노드가 `/api/internal/ingestion/policies/external-hashes` 로 DB 의 기존 `plcyBizId` 맵을
  >    받아 **DB 에 없는 신규 `plcyBizId` 만** 상세 fetch 대상으로 남긴다(이미 있는 정책은 상세
  >    요청·적재를 아예 건너뛴다 → 네트워크 절약).
  > 2. **동일 hash skip (`source_hash`)** — 백엔드 *적재 시점* 필터. 1번을 통과한 정책이라도
  >    콘텐츠가 이전과 동일하면 `policy_source.source_hash` 일치로 `SKIPPED_DUPLICATE`. 콘텐츠가
  >    바뀌었으면 hash 가 달라져 `policy` 갱신 + 신규 source 행이 적재된다.

### 3) 검증 후 구 워크플로우 로컬 비활성화

신규 3 개 검증이 끝나면 로컬 n8n 에서 구 `youth-seoul-crawl` 워크플로우를 **비활성화**한다
(delete 제약은 n8n-local-reimport-ops 참고). 파일은 이미 PR 에서 제거됐으므로 로컬 인스턴스의
active 상태만 정리하면 된다.
