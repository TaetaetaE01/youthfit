# 청년몽땅정보통(youth-seoul) 파이프라인 — 평탄화·enrichment 브랜치 main 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 브랜치 `worktree-youth-seoul-enrichment` 의 평탄화·enrichment·첨부승격·카테고리2 구현을 현재 main 위로 누락 없이 통합하고, 브랜치에 없는 fetch retry(ECONNABORTED 내성)와 수동 webhook 트리거를 더해 main 에서 전체 페이지 안정 수집이 동작하게 한다.

**Architecture:** 충돌 파일은 `youth-seoul-crawl.json` 단 하나(merge-base `032de71` 이후 main 은 `c07665a` 한 커밋만 이 파일을 건드림). 따라서 rebase/merge 가 아닌 **파일 이식** 방식으로 통합한다 — 비충돌 49개 파일은 브랜치에서 그대로 checkout, `youth-seoul-crawl.json` 은 브랜치 완전판을 채택한 뒤 retry 노드 옵션과 webhook 트리거 노드를 JSON 패치로 더한다. 작업은 현재 어수선한 working tree(미커밋 변경 4건)와 격리하기 위해 깨끗한 새 worktree 에서 수행한다.

**Tech Stack:** n8n 워크플로우 JSON, Node.js fixture 검증(`verify.mjs`, ESM), git worktree, Python3(대형 JSON 안전 패치용)

**선행 문서:**
- spec: `docs/superpowers/specs/2026-06-03-youth-seoul-pipeline-merge-to-main-design.md` (이 plan 의 단일 기준)
- 노드별 상세: `docs/superpowers/specs/2026-05-30-youth-seoul-enrichment-and-attachments-design.md`

---

## File Structure

통합으로 변경/생성되는 파일과 책임:

| 파일 | 책임 | 통합 처리 |
|---|---|---|
| `n8n/workflows/youth-seoul-crawl.json` | 크롤 워크플로우 본체 | 브랜치 채택 + retry/webhook 패치 (유일한 충돌 파일) |
| `n8n/workflows/youth-center-seoul.json` | 온통청년 워크플로우 | 브랜치의 promote-attachments 키워드 필터(+4) 만 이식 |
| `n8n/workflows/__fixtures__/youth-seoul-parse-body/` | parse-detail 회귀 테스트 스위트 | 브랜치에서 신규 이식 (`parse-body.mjs`/`verify.mjs`/README + cases-*) |
| `n8n/workflows/__fixtures__/enrichment-merge/` | enrichment selectUrls 테스트 | 브랜치에서 신규 이식 (`enrich.mjs` + case) |
| `n8n/workflows/__fixtures__/promote-attachments/` | 첨부 키워드 필터 테스트 | 브랜치에서 신규 이식 (`promote.mjs` + 4 case) |
| `docs/runbooks/youth-seoul-pipeline-verification.md` | E2E 검증 런북 | 브랜치에서 신규 이식 |
| `docker-compose.yml` | n8n 런타임 env | `NODE_FUNCTION_ALLOW_BUILTIN` 에 `http` 추가 |
| `.gitignore` | TEST 워크플로우 무시 | `n8n/workflows/youth-seoul-crawl.TEST.json` 추가 |

**통합 대상이 아닌 working tree 미커밋 변경(건드리지 않음):**
- `.idea/vcs.xml`, `n8n/workflows/bokjiro-central-welfare.json` — 무관한 잡음
- `n8n/workflows/youth-center-seoul.json` 의 working tree 변경(`slice(0,25)→slice(0,10)` TEST 캡, `upstream→item` 버그수정) — 이번 통합 범위 밖. 새 worktree 작업으로 자동 격리됨.
- `docker-compose.yml` 의 working tree 변경 — 브랜치 변경과 **완전히 동일**(http 추가)하므로, 통합 커밋에 정식 반영되면 메인 worktree 쪽 변경은 추후 버려도 무방.
- `backend_study/` — untracked, 무관.

---

## Task 0: 착수 전 점검 + 통합 worktree 생성

**Files:**
- 없음 (git 작업)

- [ ] **Step 1: 브랜치 미머지 사유 점검 (spec §7 리스크)**

브랜치 E2E 런북의 미완 항목을 확인한다. main HEAD 기준 런북은 아직 없으므로 브랜치 버전을 직접 읽는다.

Run:
```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git show worktree-youth-seoul-enrichment:docs/runbooks/youth-seoul-pipeline-verification.md
```
Expected: 런북 내용 출력. "미해결"·"TODO"·"page2 미검증" 류 표현을 메모한다. **이번 세션 미해결분(page2+ 실처리)이 통합 후 E2E(Task 7)에서 검증 대상임을 기억**한다. 차단 이슈(예: 카테고리2 적재 실패 기록)가 보이면 사용자에게 보고 후 진행 여부를 확인한다.

- [ ] **Step 2: 통합 baseline 재확인**

Run:
```bash
git fetch origin
git merge-base main worktree-youth-seoul-enrichment
git diff --name-only $(git merge-base main worktree-youth-seoul-enrichment)..main | grep youth-seoul-crawl.json
```
Expected: merge-base = `032de71...`, grep 결과로 `n8n/workflows/youth-seoul-crawl.json` 1줄 출력(= main 이 분기 이후 이 파일만 건드림 확인). 다른 충돌 후보 파일이 없는지 확인.

- [ ] **Step 3: 깨끗한 통합 worktree 생성**

main HEAD 에서 새 통합 브랜치 worktree 를 만든다. (현재 메인 working tree 의 미커밋 변경과 격리)

Run:
```bash
git worktree add -b merge/youth-seoul-pipeline ../youthfit-merge-youth-seoul main
cd ../youthfit-merge-youth-seoul
git status --short
```
Expected: 새 worktree 생성, `git status` 가 clean(미커밋 변경 0). 이후 모든 작업은 이 디렉토리에서 수행한다.

> 이후 Task 의 모든 명령은 `../youthfit-merge-youth-seoul` 작업 디렉토리 기준이다.

---

## Task 1: 비충돌 파일 49개 이식

**Files:**
- Create/Modify: fixture 스위트 전체, runbook, `youth-center-seoul.json`, `docker-compose.yml`, `.gitignore`

- [ ] **Step 1: 브랜치가 바꾼 파일 목록에서 youth-seoul-crawl.json 만 제외하고 전부 checkout**

`youth-seoul-crawl.json` 은 Task 2 에서 별도 처리하므로 여기선 제외한다.

Run:
```bash
git diff --name-only main...worktree-youth-seoul-enrichment \
  | grep -v '^n8n/workflows/youth-seoul-crawl.json$' \
  > /tmp/portable_files.txt
wc -l /tmp/portable_files.txt
xargs git checkout worktree-youth-seoul-enrichment -- < /tmp/portable_files.txt
```
Expected: `/tmp/portable_files.txt` 에 49줄, checkout 후 에러 없음.

- [ ] **Step 2: 이식 결과 stat 확인**

Run:
```bash
git status --short | head -60
git diff --cached --stat | tail -5
```
Expected: 49개 파일이 staged. fixture(`__fixtures__/...`), runbook, `youth-center-seoul.json`, `docker-compose.yml`, `.gitignore` 가 모두 보인다.

- [ ] **Step 3: docker-compose http 빌트인 반영 확인**

Run:
```bash
grep NODE_FUNCTION_ALLOW_BUILTIN docker-compose.yml
```
Expected: `crypto,https,http` (http 포함). 참고사이트 fetch code 노드가 `require('http')` 를 쓰므로 필수.

- [ ] **Step 4: youth-center promote-attachments 이식 범위 확인 (회귀 점검 대비)**

브랜치가 `youth-center-seoul.json` 에서 바꾼 것이 promote-attachments 키워드 필터(+4)만인지 확인한다.

Run:
```bash
git diff --cached -- n8n/workflows/youth-center-seoul.json
```
Expected: promote-attachments 노드의 키워드 필터 관련 +2/−2(약 4줄)만 변경. TEST 캡/`upstream→item` 같은 무관한 변경이 섞여 있지 않은지 확인(섞였다면 그건 메인 working tree 의 미커밋 변경이 잘못 들어온 것 → 새 worktree 에선 발생하지 않아야 정상).

- [ ] **Step 5: 커밋**

```bash
git add -A
git commit -m "feat(n8n): youth-seoul enrichment/첨부/카테고리2 fixture·런북 이식 (브랜치 비충돌분)

브랜치 worktree-youth-seoul-enrichment 의 49개 비충돌 파일 이식.
- youth-seoul-parse-body / enrichment-merge / promote-attachments fixture 스위트
- youth-seoul-pipeline-verification 런북
- youth-center-seoul promote-attachments 키워드 필터 sync
- docker-compose http 빌트인 허용, .gitignore TEST 워크플로우 무시

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: youth-seoul-crawl.json 브랜치 완전판 채택

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json`

- [ ] **Step 1: 브랜치 버전을 통째로 채택**

reconcile 매트릭스(spec §3): 페이지네이션 구조·parse-detail 정제·카테고리2 모두 브랜치 채택. main 의 `c07665a` 부분 정제는 브랜치 완전판의 부분집합이므로 버린다. retry·webhook 은 Task 3·4 에서 다시 더한다.

Run:
```bash
git checkout worktree-youth-seoul-enrichment -- n8n/workflows/youth-seoul-crawl.json
python3 -c "import json; json.load(open('n8n/workflows/youth-seoul-crawl.json')); print('JSON valid')"
```
Expected: `JSON valid`.

- [ ] **Step 2: 노드 구성이 평탄화 흐름과 일치하는지 확인 (spec §5.1)**

Run:
```bash
python3 -c "
import json
d=json.load(open('n8n/workflows/youth-seoul-crawl.json'))
names=[n['name'] for n in d['nodes']]
required=['카테고리 초기화','카테고리별 순차 처리','페이지 초기화','목록 페이지 요청',
'plcyBizId 추출','다음 페이지 확인','다음 페이지 존재?','다음 페이지 이동',
'수집 결과 펼치기','정책별 순차 처리','3초 대기 (Rate Limit)','상세 페이지 요청',
'상세 데이터 파싱','참고사이트 fetch + 머지','enrichment 메타 합성','attachments 승격',
'백엔드 API 전송','크롤링 완료']
missing=[r for r in required if r not in names]
print('총 노드:',len(names))
print('누락:',missing if missing else '없음')
"
```
Expected: 누락 없음. (이 시점엔 webhook 노드가 없는 게 정상 — Task 4 에서 추가)

- [ ] **Step 3: 커밋**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul-crawl 평탄화·enrichment 완전판 채택 (브랜치)

페이지네이션 평탄화(수집/처리 분리), parse-detail 정제 완전판(dash 규칙 2개 포함),
카테고리 2개 순회(서울 자체 + 중앙정부/타지역), enrichment·첨부 승격 노드 채택.
retry·webhook 트리거는 후속 커밋에서 추가.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: fetch retry(ECONNABORTED 내성) 추가

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json`

브랜치 fetch 노드엔 retry 가 없다. main 설정(`maxTries=5, waitBetweenTries=5000`)을 spec §3·§5.1 대상 노드에 더한다: **목록 페이지 요청·상세 페이지 요청·참고사이트 fetch + 머지**.

- [ ] **Step 1: httpRequest 2노드에 retryOnFail 추가**

거대 JSON 수동 편집은 위험하므로 Python 패치로 적용한다.

Run:
```bash
python3 - <<'PY'
import json
P='n8n/workflows/youth-seoul-crawl.json'
d=json.load(open(P))
targets={'목록 페이지 요청','상세 페이지 요청'}
patched=[]
for n in d['nodes']:
    if n['name'] in targets:
        n['retryOnFail']=True
        n['maxTries']=5
        n['waitBetweenTries']=5000
        patched.append(n['name'])
json.dump(d,open(P,'w'),ensure_ascii=False,indent=2)
open(P,'a').write('\n')
print('patched:',patched)
PY
```
Expected: `patched: ['목록 페이지 요청', '상세 페이지 요청']` (순서 무관, 2개).

- [ ] **Step 2: 참고사이트 fetch 노드의 예외 처리 방식 확인**

참고사이트 fetch 는 httpRequest 가 아니라 `require('http')` 를 쓰는 **code 노드**다. n8n 의 `retryOnFail` 은 노드가 throw 할 때만 재시도하므로, 이 노드가 fetch 실패를 throw 하는지 best-effort(에러 무시 후 빈 결과)인지 확인해야 retry 가 의미 있다.

Run:
```bash
python3 -c "
import json
d=json.load(open('n8n/workflows/youth-seoul-crawl.json'))
for n in d['nodes']:
    if n['name']=='참고사이트 fetch + 머지':
        print(n['parameters']['jsCode'])
" | grep -nE 'catch|throw|return|Promise|reject|resolve' | head -40
```
판단 기준:
- fetch 실패를 **try/catch 로 삼키고** 빈 enrichment 로 진행한다면 → enrichment 는 설계상 best-effort(SKIP). 노드 레벨 retryOnFail 은 효과가 없으므로 **추가하지 않는다**. (spec §5.1 의 "참고사이트 fetch retry" 의도는 enrichment 품질 향상이지만, best-effort 설계와 충돌하면 best-effort 를 우선한다. 이 판단을 Step 4 커밋 메시지에 기록.)
- fetch 실패가 **노드 밖으로 throw** 되어 워크플로우를 중단시킨다면 → Step 3 으로 진행해 retryOnFail 추가.

- [ ] **Step 3: (Step 2 에서 throw 로 판단된 경우만) 참고사이트 노드에 retryOnFail 추가**

Run:
```bash
python3 - <<'PY'
import json
P='n8n/workflows/youth-seoul-crawl.json'
d=json.load(open(P))
for n in d['nodes']:
    if n['name']=='참고사이트 fetch + 머지':
        n['retryOnFail']=True
        n['maxTries']=3
        n['waitBetweenTries']=5000
        print('patched 참고사이트 fetch + 머지')
json.dump(d,open(P,'w'),ensure_ascii=False,indent=2)
open(P,'a').write('\n')
PY
```
Expected: `patched 참고사이트 fetch + 머지`. (Step 2 에서 best-effort 로 판단됐으면 이 Step 은 건너뛴다.)

- [ ] **Step 4: retry 적용 결과 검증 + 커밋**

Run:
```bash
python3 -c "
import json
d=json.load(open('n8n/workflows/youth-seoul-crawl.json'))
for n in d['nodes']:
    if n.get('retryOnFail'):
        print(n['name'],'maxTries=',n.get('maxTries'),'wait=',n.get('waitBetweenTries'))
"
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "fix(n8n): youth-seoul fetch 노드에 retryOnFail 추가 (ECONNABORTED 내성)

목록·상세 페이지 요청(httpRequest)에 maxTries=5/wait=5000 적용.
page2 목록 요청에서 잦은 ECONNABORTED 로 워크플로우 전체가 중단되던 결함 해소.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
Expected: 최소 `목록 페이지 요청`·`상세 페이지 요청` 2개가 maxTries=5 로 출력.

---

## Task 4: 수동 webhook 트리거 노드 병합

**Files:**
- Modify: `n8n/workflows/youth-seoul-crawl.json`

main 에만 있는 수동 트리거(`youth-seoul-manual`)를 가져온다. **단 평탄화 진입점은 `카테고리 초기화`** 이므로(브랜치 connections 확인: `매일 새벽 3시 실행 → 카테고리 초기화`), main 의 옛 연결(`→ 페이지 초기화`)이 아니라 **`카테고리 초기화` 로 연결**한다. 페이지 초기화로 연결하면 카테고리 메타·`sd.collected=[]` 초기화가 누락돼 카테고리 루프가 깨진다.

- [ ] **Step 1: webhook 노드 + connection 추가**

Run:
```bash
python3 - <<'PY'
import json
P='n8n/workflows/youth-seoul-crawl.json'
d=json.load(open(P))
names={n['name'] for n in d['nodes']}
assert '카테고리 초기화' in names, '진입점 노드 없음'
if '수동 실행 트리거' not in names:
    d['nodes'].append({
        "parameters": {"httpMethod":"POST","path":"youth-seoul-manual",
                       "responseMode":"lastNode","options":{}},
        "id":"manual-webhook-trigger",
        "name":"수동 실행 트리거",
        "type":"n8n-nodes-base.webhook",
        "typeVersion":2,
        "position":[0,200],
        "webhookId":"youth-seoul-manual-trigger"
    })
    d.setdefault('connections',{})['수동 실행 트리거']={
        "main":[[{"node":"카테고리 초기화","type":"main","index":0}]]
    }
    print('webhook 노드+connection 추가 (→ 카테고리 초기화)')
else:
    print('이미 존재 — skip')
json.dump(d,open(P,'w'),ensure_ascii=False,indent=2)
open(P,'a').write('\n')
PY
```
Expected: `webhook 노드+connection 추가 (→ 카테고리 초기화)`.

- [ ] **Step 2: 두 트리거가 같은 진입점으로 가는지 검증**

Run:
```bash
python3 -c "
import json
d=json.load(open('n8n/workflows/youth-seoul-crawl.json'))
c=d['connections']
print('스케줄:',c.get('매일 새벽 3시 실행'))
print('수동:',c.get('수동 실행 트리거'))
print('JSON valid')
"
```
Expected: 둘 다 `카테고리 초기화` 로 연결.

- [ ] **Step 3: 커밋**

```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "feat(n8n): youth-seoul 수동 webhook 트리거 병합 (평탄화 진입점 연결)

main 의 youth-seoul-manual webhook 을 가져오되, 평탄화 진입점인 '카테고리 초기화'로
연결(옛 '페이지 초기화' 연결은 카테고리 메타/static data 초기화 누락이라 폐기).
로컬 E2E 가 CLI execute 불가한 평탄화 루프를 webhook 으로 트리거 가능.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: fixture 단위 테스트 실행 (회귀 차단)

**Files:**
- Test: `n8n/workflows/__fixtures__/youth-seoul-parse-body/verify.mjs` 외

- [ ] **Step 1: youth-seoul-parse-body 전 케이스 실행**

Run:
```bash
cd n8n/workflows/__fixtures__/youth-seoul-parse-body
node verify.mjs
cd -
```
Expected: 마지막 줄 `All N case(s) passed`, exit 0. 특히 `cases-extract-by-th/case-multiline-comment`·`case-dash-arrow`·`case-dash-only-line`·`case-text-angle-brackets` 가 PASS — `-->`/`----->` 잔재 회귀 차단(spec §6).

- [ ] **Step 2: 나머지 fixture 스위트에 실행 가능한 verify 가 있으면 모두 실행**

Run:
```bash
find n8n/workflows/__fixtures__ -name 'verify.mjs' -o -name 'verify.test.mjs' | while read f; do
  echo "=== $f ==="; ( cd "$(dirname "$f")" && node "$(basename "$f")" ) || echo "FAILED: $f"
done
```
Expected: 발견된 verify 전부 통과. (enrichment-merge·promote-attachments 가 독립 verify 없이 `enrich.mjs`/`promote.mjs` 모듈만 제공한다면 이 단계는 youth-seoul-parse-body 만 돌고 끝나도 정상 — find 결과 기준으로 판단.)

- [ ] **Step 3: fixture ↔ 워크플로우 인라인 코드 동기화 대조 (spec §5.2 마지막 항목)**

`parse-body.mjs` 의 truth 함수와 `youth-seoul-crawl.json` "상세 데이터 파싱" 노드 인라인 코드가 일치하는지 확인한다.

Run:
```bash
python3 -c "
import json
d=json.load(open('n8n/workflows/youth-seoul-crawl.json'))
for n in d['nodes']:
    if n['name']=='상세 데이터 파싱':
        print(n['parameters']['jsCode'])
" > /tmp/inline-parse.js
grep -E 'function (extractByTh|extractRefUrls|extractSelfAttachments|extractApplyUrl|extractReferenceSites|buildBody)' /tmp/inline-parse.js
diff <(grep -oE 'function extractByTh.*' /tmp/inline-parse.js) <(grep -oE 'function extractByTh.*' n8n/workflows/__fixtures__/youth-seoul-parse-body/parse-body.mjs) && echo "extractByTh 시그니처 일치"
```
Expected: 6개 함수가 인라인 코드에 모두 존재. 시그니처 불일치가 보이면 fixture(truth)에 맞춰 인라인 코드를 수정하고 Step 1 을 재실행한다. (브랜치에서 가져온 그대로면 이미 동기화돼 있어야 정상.)

- [ ] **Step 4: 테스트 그린이면 진행 표시 커밋 불필요 — 코드 변경 없으면 skip**

테스트만 돌렸고 코드 변경이 없으면 커밋하지 않는다. Step 3 에서 인라인 코드를 고쳤다면:
```bash
git add n8n/workflows/youth-seoul-crawl.json
git commit -m "fix(n8n): 상세 데이터 파싱 인라인 코드를 parse-body fixture truth 에 동기화"
```

---

## Task 6: 브랜치 구현 인벤토리·누락 검증 (spec §5 — 필수 완료 게이트)

**Files:**
- 없음 (검증)

- [ ] **Step 1: 통합본 vs 브랜치 diff 가 "의도된 차이만" 남는지 확인 (spec §5.5)**

Run:
```bash
git diff --stat HEAD..worktree-youth-seoul-enrichment
```
Expected: **`youth-seoul-crawl.json` 한 파일만** 차이로 남아야 한다(= 우리가 더한 retry/webhook). fixture·runbook·youth-center·docker-compose·.gitignore 가 diff 에 보이면 이식 누락 → 해당 파일을 Task 1 방식으로 재이식.

- [ ] **Step 2: youth-seoul-crawl.json 차이가 retry/webhook 으로만 설명되는지 확인**

Run:
```bash
git diff HEAD..worktree-youth-seoul-enrichment -- n8n/workflows/youth-seoul-crawl.json | grep -E '^[+-]' | grep -iE 'retryOnFail|maxTries|waitBetweenTries|webhook|youth-seoul-manual|수동 실행|카테고리 초기화' | head
```
Expected: 차이 라인이 retryOnFail/maxTries/waitBetweenTries/webhook/수동 트리거 관련(우리가 추가) + 들여쓰기·키 순서 차이뿐. enrichment·parse·카테고리 로직 라인이 `-`(우리 쪽에서 사라짐)로 나오면 채택 누락 → Task 2 재실행.

- [ ] **Step 3: 워크플로우 노드 set 비교 (추가분 외 일치)**

Run:
```bash
python3 - <<'PY'
import json,subprocess
def names(ref):
    j=subprocess.check_output(['git','show',f'{ref}:n8n/workflows/youth-seoul-crawl.json'])
    return {n['name'] for n in json.loads(j)['nodes']}
ours=names('HEAD'); br=names('worktree-youth-seoul-enrichment')
print('통합본에만:',ours-br)   # 기대: {'수동 실행 트리거'}
print('브랜치에만:',br-ours)   # 기대: set()
PY
```
Expected: `통합본에만: {'수동 실행 트리거'}`, `브랜치에만: set()`.

- [ ] **Step 4: fixture 케이스 수 카운트 비교 (spec §5.5)**

Run:
```bash
echo "통합본:"; find n8n/workflows/__fixtures__ -name '*.input.*' | wc -l
echo "브랜치:"; git ls-tree -r --name-only worktree-youth-seoul-enrichment n8n/workflows/__fixtures__ | grep -c '\.input\.'
```
Expected: 두 수가 동일.

- [ ] **Step 5: spec §5 체크리스트 수기 대조**

`docs/superpowers/specs/2026-06-03-youth-seoul-pipeline-merge-to-main-design.md` §5.1~§5.4 의 모든 `- [ ]` 항목을 위 Step 결과로 하나씩 체크한다. 미충족 항목이 있으면 해당 Task 로 돌아간다. **전부 통과해야 통합 완료로 간주**(spec §5 게이트).

---

## Task 7: 로컬 E2E 검증 (런북 절차 — 사용자 협조 필요)

**Files:**
- Reference: `docs/runbooks/youth-seoul-pipeline-verification.md`

> 평탄화 루프는 CLI `n8n execute` 로 검증 불가 → webhook/운영모드 트리거. n8n·백엔드·DB 가 떠 있어야 한다. 자동화 불가 구간이므로 사용자에게 실행을 요청하고 결과를 함께 확인한다.

- [ ] **Step 1: 런북 절차 확인**

Run:
```bash
cat docs/runbooks/youth-seoul-pipeline-verification.md
```
Expected: import → active → 트리거 → 적재 확인 절차. `NODE_FUNCTION_ALLOW_BUILTIN` 에 `http` 필요 명시 확인.

- [ ] **Step 2: 워크플로우 import + 재시작 (사용자에게 요청)**

사용자에게 `! docker compose ...` 형태로 실행 요청. n8n 컨테이너에 통합본 `youth-seoul-crawl.json` 을 import 하고 재시작, 워크플로우 active 화. (메모리 `n8n_local_reimport_ops`: 재반영=import+재시작 필수, execute 불가→webhook.)

- [ ] **Step 3: webhook 트리거 후 2개 카테고리 순회·page2+ 실처리 확인**

수동 트리거 호출:
```bash
curl -X POST http://localhost:5678/webhook/youth-seoul-manual
```
Expected(런북·spec §6): 실행 로그에서 (1) 카테고리 2개(서울 자체 + 중앙정부/타지역)가 모두 순회, (2) **page2+ 항목이 실제 처리**(이번 세션 미해결분 — flush 버그 해소 실증), (3) ECONNABORTED 발생 시 retry 로 복구되는지 관측.

- [ ] **Step 4: 다운스트림 적재 확인**

적재 후 guide·eligibility_rule·policy_document(임베딩) 생성과 enrichment/attachment payload 가 백엔드 로그에 보이는지 확인(spec §6).

---

## Task 8: 적재 품질 검증 (DB 점검)

**Files:**
- 없음 (DB 쿼리 — 사용자 환경)

- [ ] **Step 1: 잔재 0 확인 (spec §6)**

적재된 정책 body·문서·가이드·룰에 `-->`/`----->` 가 0 인지 LIKE 점검. (psql 은 `-i` 필수 — 메모리 `n8n_local_reimport_ops`.)

예시 쿼리:
```sql
SELECT count(*) FROM policy WHERE body LIKE '%-->%' OR body LIKE '%----->%';
SELECT count(*) FROM policy_document WHERE content LIKE '%-->%';
```
Expected: 0 건.

- [ ] **Step 2: enrichment status 매핑 점검 (코드 변경 없이)**

어드민 처리현황 패널에서 youth-seoul 정책의 enrichment status 가 선행 spec 2026-05-30 §6 표대로 SUCCESS / SKIPPED(NO_LINK) / FAILED 로 정확히 마킹되는지 확인. **어긋나면 코드를 고치지 말고** spec §8 대로 후속 spec(백엔드 enrichment status 매핑 보강)으로 분리 기록.

---

## Task 9: 통합 마무리 — PR

**Files:**
- 없음 (git/PR)

- [ ] **Step 1: 최종 빌드/검증 상태 재확인**

Run:
```bash
node n8n/workflows/__fixtures__/youth-seoul-parse-body/verify.mjs 2>/dev/null || (cd n8n/workflows/__fixtures__/youth-seoul-parse-body && node verify.mjs)
python3 -c "import json; json.load(open('n8n/workflows/youth-seoul-crawl.json')); print('crawl JSON valid')"
git diff --stat HEAD..worktree-youth-seoul-enrichment
```
Expected: fixture 그린, JSON valid, 브랜치 대비 diff 는 youth-seoul-crawl.json(retry/webhook)만.

- [ ] **Step 2: 푸시 + PR 생성**

`create-pr` 스킬 또는 수동으로 PR 생성. 본문에 spec §5 인벤토리 체크리스트 통과 결과(Task 6 출력), retry/webhook reconcile 결정(spec §3), E2E 결과(Task 7)를 요약한다.

```bash
git push -u origin merge/youth-seoul-pipeline
```
PR 제목 예: `feat(n8n): youth-seoul 평탄화·enrichment 파이프라인 main 통합 + fetch retry`

- [ ] **Step 3: 머지 후 worktree 정리**

PR 머지 확인 후:
```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git worktree remove ../youthfit-merge-youth-seoul
git branch -d worktree-youth-seoul-enrichment   # 통합 완료 시 (사용자 확인 후)
```

- [ ] **Step 4: 후속 작업 후보 기록 (spec §8)**

E2E·DB 점검에서 어긋난 항목이 있으면 후속 spec 후보로 남긴다:
- 백엔드 enrichment status 매핑 보강 (Task 8 Step 2 에서 어긋남 발견 시)
- 운영 rate limit / 부하 튜닝 (spec §7: 2 카테고리 × 다수 페이지 × 3초 대기 → 실행시간 급증, robots Crawl-delay 재확인)

---

## Self-Review (작성자 체크 — 완료)

**1. Spec 커버리지:**
- spec §2 목표 1(전체 수집/평탄화) → Task 2 / §3(reconcile) → Task 2·3·4 / 목표 2(enrichment) → Task 1·2 / 목표 3(첨부 승격) → Task 1·2 / 목표 4(카테고리2) → Task 2 / 목표 5(ECONNABORTED retry) → Task 3 ✅
- spec §5 인벤토리(5.1 노드/5.2 fixture/5.3 youth-center/5.4 문서/5.5 누락탐지) → Task 6 전 항목 + Task 1 ✅
- spec §6 검증(fixture/E2E/다운스트림/status/잔재0) → Task 5·7·8 ✅
- spec §7 리스크(미머지 사유/부하) → Task 0 Step 1, Task 9 Step 4 ✅

**2. Placeholder 스캔:** 모든 코드 step 에 실행 가능한 명령/스크립트 제공. "적절히 처리" 류 없음 ✅ (Task 3 Step 2~3 은 노드 코드 실측에 따른 분기로, 판단 기준과 양쪽 명령을 모두 명시)

**3. 타입/이름 일관성:** 노드명(`카테고리 초기화`·`수동 실행 트리거`), webhook path(`youth-seoul-manual`), retry 값(maxTries=5/wait=5000), 진입점 연결(`→ 카테고리 초기화`)을 Task 3·4·6 에서 동일하게 사용 ✅

---

## 통합 전략 요약 (왜 파일 이식인가)

- merge-base `032de71` 이후 main 은 `c07665a` 단 한 커밋만 `youth-seoul-crawl.json` 을 건드렸고, 그 변경(부분 정제)은 브랜치 완전판의 부분집합 → **rebase 18커밋은 불필요한 JSON 충돌만 양산**.
- 충돌 파일 1개 → 파일 이식이 가장 통제 가능하고 되돌리기 쉽다(공통 규칙: 작고 되돌리기 쉬운 변경).
- main 의 고유 기여(retry·webhook)는 버리지 않고 Task 3·4 에서 브랜치 완전판 위에 재적용 → reconcile 매트릭스(spec §3) 완전 충족.
