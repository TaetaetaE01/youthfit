# 청년몽땅정보통(youth-seoul) 파이프라인 — 평탄화·enrichment 브랜치를 main 에 통합

> 작성: 2026-06-03
> 상태: spec
> 선행 spec: `2026-05-30-youth-seoul-enrichment-and-attachments-design.md` (노드별 상세 설계의 원본)
> 선행 작업 브랜치: `worktree-youth-seoul-enrichment`

## 1. 배경

`n8n/workflows/youth-seoul-crawl.json` 의 페이지네이션이 **중첩 splitInBatches flush 버그**로 사실상 page1(5건)만 수집하고 page2 부터는 목록을 가져와도 항목을 처리 없이 흘려보낸다(2026-06-03 세션 exec 104 실증). 그래서 운영 자동 크롤로 몽땅청년 전체(332건/67페이지)를 수집할 수 없다.

이 문제를 포함한 전면 개선은 **이미 브랜치 `worktree-youth-seoul-enrichment` 에 구현**되어 있으나 main 에 머지되지 않았다. 본 spec 은 그 브랜치 구현을 **현재 main 위로 통합·완성**하는 작업을 정의한다. (노드별 알고리즘 상세는 선행 spec 2026-05-30 에 있으므로 본 spec 은 통합·reconcile·누락 검증에 집중한다.)

### 1.1 현재 main 상태 (commit `c07665a` 기준)
- parse-detail 본문 정제 **일부** 반영: `<!--[\s\S]*?-->` 멀티라인 주석 제거 + `<\/?[a-zA-Z][^>]*>` 영문 시작 태그만 매칭. **단 dash 잔재 규칙 2개는 미반영** (`-{3,}`-only 줄, `-{4,}>`).
- extract-ids: 정규식 `[A-Za-z0-9]+` (숫자형 20자리 ID 포함), currentPage 를 목록 HTML `name="pageIndex"` hidden input 에서 파싱.
- fetch-list / fetch-detail 에 `retryOnFail`(maxTries=5, waitBetweenTries=5000) 추가.
- 수동 webhook 트리거(`youth-seoul-manual`) 노드 존재.
- **평탄화 미적용**(page2 flush 버그 잔존), enrichment·첨부·카테고리2 **없음**.
- maxPage TEST 캡·slice(0,10) 는 제거되어 자연 전체 크롤 상태.

### 1.2 브랜치 `worktree-youth-seoul-enrichment` 상태
- **평탄화 완성**: `카테고리 초기화 → 카테고리별 순차 처리(카테고리 루프) → (페이지 루프는 ID만 static data 누적) → 수집 결과 펼치기 → 정책별 순차 처리(단일 처리 루프)`. 페이지네이션 중 per-item 처리를 하지 않아 flush 버그 구조적으로 제거.
- parse-detail 정제 **완전판**(dash 규칙 2개 포함 — main 부분본의 상위 집합).
- 카테고리 2개: 서울 자체(`plcyInfo`, key=2309150002) + 중앙정부/타지역(`youthPlcyInfo/list.do`, key=2309160001). **카테고리2 URL 실측 10건 수신 확인**.
- enrichment: 참고/관련/신청 사이트 URL 추출 → 참고사이트 fetch+머지 → enrichment 메타 합성, applyUrl/referenceSites 분리 저장. buildBody 로 본문 섹션 확대.
- 첨부 승격: 확장자 화이트리스트 + 파일명 키워드 필터.
- 코드리뷰 반영(commit `b15ddae`), fixture 테스트 스위트, E2E 검증 런북 존재.
- **결함(이번 세션 발견 갭)**: fetch 노드에 **retry 없음** → page2 목록 요청의 `ECONNABORTED`(youth.seoul 가 n8n 요청을 자주 끊음, 2026-06-03 실증)에 워크플로우 전체가 에러로 중단될 수 있음.

### 1.3 2026-06-03 세션에서 추가로 확정된 사실
- page1=5건(전부 `V`형식), page2=5건(`V`형식 2 + 20자리 숫자형 3). 숫자형 상세페이지도 `view.do` 로 정상 파싱.
- page2 목록 요청은 n8n httpRequest 에서 `ECONNABORTED` 가 잦다(호스트 wget 은 대부분 성공). → **retry 필수**.
- 본문 `제출서류: -->` 잔재의 원인은 주석 내부에 `>`(`<!-- <ul> -->`)가 있어 `/<[^>]+>/g` 가 첫 `>`까지만 지우기 때문. 주석 사전 제거로 해결됨(main·브랜치 모두 반영).

## 2. 목표 / 비목표

### 목표
- 브랜치 구현을 현재 main 위로 통합해 다음을 main 에서 동작시킨다:
  1. **전체 페이지 안정 수집** (평탄화로 page2+ flush 버그 제거)
  2. **enrichment** (참고/신청 사이트 fetch → cleanedText·referenceSites·applyUrl)
  3. **첨부 임베딩 후보 승격** (확장자+키워드 필터)
  4. **카테고리 2개 순회** (서울 자체 + 중앙정부/타지역)
  5. **ECONNABORTED 내성** — 브랜치에 없는 fetch retry 를 추가
- 통합 과정에서 **브랜치가 구현한 산출물을 하나도 누락하지 않는다** (§5 인벤토리·누락 검증).

### 비목표 (이번 spec 범위 밖)
- 백엔드 신규 `PolicyEnrichmentService` (enrichment 은 n8n 에서 fetch — 선행 spec 결정 유지).
- 어드민 패널 UI 변경 (status 매핑은 점검만).
- 온통청년·복지로 등 타 출처의 동작 변경 (단, fixture 동기화로 인한 promote-attachments 키워드 필터 반영은 예외 — §5).
- enrichment LLM 섹션 추출 / 첨부 LLM 분류기.
- 운영 부하 튜닝(카테고리2·전체 페이지의 rate limit 재설계)은 §6 점검 후 필요 시 후속 spec.

## 3. Reconcile 매트릭스 (main vs 브랜치)

통합 시 요소별로 무엇을 채택할지 명시한다. 충돌 해소의 단일 기준.

| 요소 | main (c07665a) | 브랜치 | 통합 결정 |
|---|---|---|---|
| 페이지네이션 구조 | 중첩 splitInBatches(버그) | 평탄화(수집/처리 분리) | **브랜치 채택** |
| parse-detail 정제 | 주석+영문태그만 (dash 규칙 없음) | + dash 규칙 2개 | **브랜치 채택**(main 부분본 대체) |
| extract-ids 정규식·currentPage | `[A-Za-z0-9]+`, HTML 파싱 | 동일 | 동일 — 충돌 없음 |
| fetch retry | retryOnFail(maxTries=5) 있음 | **없음** | **main 의 retry 를 브랜치 fetch 노드(목록·상세·참고사이트 fetch)에 추가** |
| maxPage 캡 / slice | 제거됨(전체 수집) | (확인 필요) | 캡 없음 유지 — §5 에서 브랜치 잔존분 점검 |
| 수동 webhook 트리거 | 있음(`youth-seoul-manual`) | (확인 필요) | 병합 유지 — §5 에서 브랜치 트리거 노드 점검 |
| 카테고리2 URL | 없음 | `youthPlcyInfo/list.do?key=2309160001` | 브랜치 채택(실측 10건 OK) |
| 백엔드 | 변경 없음 | 변경 없음 | 변경 없음 — payload 에 enrichment/attachments 추가만 |

## 4. 통합 방식

- 브랜치 워크플로우·fixture 산출물을 현재 main 위로 가져오되(rebase 또는 파일 이식), 충돌 지점은 §3 매트릭스대로 해소한다. 특히 **fetch retry 는 브랜치 코드에 새로 더한다**.
- 구체적 git 절차(rebase vs cherry-pick vs 수동 이식)와 단계 분해는 **다음 세션의 implementation plan** 에서 결정한다. 본 spec 은 "무엇을·어떤 기준으로" 까지만 정의한다.

## 5. 브랜치 구현 인벤토리·누락 검증 (필수 단계)

> 통합 중 브랜치가 만든 산출물이 누락되지 않았는지 **항목 단위로 대조**한다. 구현 plan 의 마지막 단계는 이 체크리스트를 전부 통과해야 완료로 간주한다.

브랜치는 main 대비 **50 파일 / +924 −38** 를 변경했다(`git diff --stat main...worktree-youth-seoul-enrichment` 기준). 범주별로 인벤토리화한다.

### 5.1 워크플로우 노드 (youth-seoul-crawl.json)
통합본에 아래 노드가 모두 존재하고 연결이 평탄화 흐름과 일치하는지 확인:
- [ ] `카테고리 초기화` (2개 카테고리 메타 + `sd.collected=[]` 초기화)
- [ ] `카테고리별 순차 처리` (splitInBatches — 카테고리 루프)
- [ ] `페이지 초기화` / `목록 페이지 요청` / `plcyBizId 추출`(ID 를 static data 누적, per-item 처리 없음)
- [ ] `다음 페이지 확인` / `다음 페이지 존재?` / `다음 페이지 이동` (페이지 루프; false 시 카테고리 루프로 복귀)
- [ ] `수집 결과 펼치기` (static data → 개별 item 펼침, 누적 초기화)
- [ ] `정책별 순차 처리` (단일 처리 루프)
- [ ] `3초 대기` / `상세 페이지 요청` / `상세 데이터 파싱`(buildBody·정제 완전판)
- [ ] `참고사이트 fetch + 머지` / `enrichment 메타 합성` / `attachments 승격`
- [ ] `백엔드 API 전송` / `크롤링 완료`
- [ ] (병합) 수동 webhook 트리거 노드 — main 에서 가져와 유지
- [ ] (추가) 목록·상세·참고사이트 fetch 3노드에 `retryOnFail` 적용 — **브랜치엔 없으므로 통합 시 신규**

### 5.2 fixture 스위트 (`n8n/workflows/__fixtures__/`)
- [ ] `youth-seoul-parse-body/` 신규 스위트: `parse-body.mjs`, `verify.mjs`, `README.md`
- [ ] `cases-extract-by-th/`: `multiline-comment`, `dash-arrow`, `dash-only-line`, `text-angle-brackets` (정제 회귀 보호 — main 부분본이 놓친 dash 케이스 포함)
- [ ] `cases-ref-urls/`, `cases-reference-sites/`, `cases-self-attachments/`, `cases-apply-url/`, `cases-build-body/` 각 케이스
- [ ] `enrichment-merge/`: `enrich.mjs` + `case-explicit-refurls` (refUrls[] 우선 + 기존 키 fallback 시그니처)
- [ ] `promote-attachments/`: `promote.mjs` 키워드 필터 + `case-name-keyword-passes`/`case-name-logo-excluded`/`case-name-short-no-keyword-excluded`/`case-non-string-url`
- [ ] **fixture ↔ 워크플로우 인라인 코드 동기화** (`// 동기화 책임:` 규약) — fixture 의 알고리즘과 JSON 노드 코드가 일치하는지 대조

### 5.3 fixture 동기화 파급 (youth-center)
- [ ] `youth-center-seoul.json` 의 `promote-attachments` 노드가 동일 키워드 필터로 갱신됐는지 (브랜치가 +4 변경). 온통청년 기존 첨부 통과율 변화 회귀 점검.

### 5.4 문서
- [ ] `docs/runbooks/youth-seoul-pipeline-verification.md` (E2E 검증 결과·평탄화 아키텍처·enrichment status 매핑 관측) 가져오기/갱신

### 5.5 누락 탐지 방법
- 통합본 vs 브랜치 diff 로 **빠진 파일 0** 확인: `git diff --stat <통합본>...worktree-youth-seoul-enrichment` 가 워크플로우 JSON 의 의도된 차이(=retry 추가)만 남는지 검사.
- 워크플로우 노드 수·이름 set 을 브랜치와 비교(추가한 retry·webhook 외 일치).
- fixture 케이스 수가 브랜치와 동일한지 카운트 비교.

## 6. 검증 (통합 후)

- **fixture 단위 테스트**: `__fixtures__/youth-seoul-parse-body/verify.mjs` 등 전 케이스 통과. 특히 `multiline-comment`·`dash-*` 케이스로 `-->`·`----->` 잔재 회귀 차단.
- **로컬 E2E**(런북 절차): 워크플로우 active + 웹훅/운영모드 트리거(평탄화 루프는 CLI execute 로 검증 불가). 2개 카테고리가 모두 순회되고 page2+ 가 실제 처리되는지(이번 세션 미해결분) 확인. `NODE_FUNCTION_ALLOW_BUILTIN` 에 `http` 필요.
- **다운스트림**: 적재 후 guide·eligibility_rule·policy_document(임베딩) 생성 확인. enrichment/attachment payload 가 백엔드 로그에 보이는지.
- **백엔드 enrichment status 매핑 점검**(코드 변경 없이): 선행 spec 2026-05-30 §6 표대로 어드민 처리현황 패널에서 SUCCESS/SKIPPED(NO_LINK)/FAILED 가 정확히 마킹되는지. 어긋나면 후속 spec 으로 분리.
- **잔재 0**: 적재된 정책 body·문서·가이드·룰에 `-->` / `----->` 0 (DB LIKE 점검).

## 7. 리스크 / 점검

- **ECONNABORTED 빈도**: retry(maxTries=5)로도 page2 가 계속 끊기면 워크플로우 실패. 빈도 관측 후 maxTries·waitBetweenTries 상향 또는 페이지 단위 부분 성공 허용 검토.
- **카테고리2 + 전체 페이지로 수집량·시간 증가**: 2 카테고리 × 다수 페이지 × 3초 대기 → 실행 시간 대폭 증가. youth.seoul robots Crawl-delay 재확인. 운영 적용 전 부하 산정.
- **브랜치 미머지 사유 불명**: 브랜치가 왜 머지 안 됐는지 기록이 없다. 통합 착수 전 사용자 확인 또는 브랜치 E2E 런북 결과로 미완 항목 파악(점검 항목).
- **fixture 동기화 회귀**: 키워드 필터를 fixture·JSON 한쪽만 고치면 다음 변경에서 회귀. PR 체크리스트로 보호.
- **정제 over-aggressive**: dash 규칙이 의도된 dash 리스트(`- 항목`)를 깨지 않도록 "dash 만 있는 줄"·"4개 이상 dash+>" 로 한정(브랜치 규칙 유지).

## 8. 후속 작업 후보 (범위 밖)
- 백엔드 enrichment status 매핑 보강(§6 에서 어긋남 발견 시).
- 운영 rate limit / 부하 튜닝 spec.
- 첨부 LLM 분류기, 다른 탭(tabKind != 002) 수집.
