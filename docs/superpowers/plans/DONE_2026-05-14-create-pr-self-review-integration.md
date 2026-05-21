# `create-pr` 스킬에 `/cr` 셀프 리뷰 선행 단계 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `.claude/skills/create-pr/SKILL.md` 의 "PR 작성 절차" 1번 단계 앞에 **0번 단계** "`/cr` 셀프 리뷰 선행" 을 끼워넣어, `create-pr` 트리거 시 셀프 리뷰가 먼저 돌고 Critical 차단 / Major 확인 / 깨끗 통과 분기에 따라 다음 단계 진행 여부가 결정되게 한다.

**Architecture:** SKILL.md 한 파일에 마크다운 섹션을 삽입. 신규 파일 없음. `/cr` 슬래시 커맨드와 `code-reviewer` 서브에이전트 자체는 수정하지 않는다 — `create-pr` 가 호출자 역할만 추가한다.

**Tech Stack:** Claude Code 스킬 정의 (`.claude/skills/*/SKILL.md` 마크다운).

**Spec:** `docs/superpowers/specs/2026-05-14-create-pr-self-review-integration-design.md`

---

## File Structure

| 파일 | 책임 | 신규/수정 |
|---|---|---|
| `.claude/skills/create-pr/SKILL.md` | "## PR 작성 절차" 섹션에 0번 단계 추가, 분기 안내 포함 | 수정 |

전체 SKILL.md 의 다른 섹션(영역 태그 규칙, 브랜치 네이밍, 커밋 타입, 스펙 DONE_ 처리, PR 템플릿, 주의 사항)은 수정하지 않는다.

---

## Task 1: SKILL.md 에 0번 단계 삽입

**Files:**
- Modify: `.claude/skills/create-pr/SKILL.md` — "## PR 작성 절차" 섹션 (현재 78–88 줄)

- [ ] **Step 1: 현재 "PR 작성 절차" 섹션 위치와 1번 단계 본문 확인**

Run:
```bash
grep -n "## PR 작성 절차" .claude/skills/create-pr/SKILL.md
grep -n "^1\. \`git diff" .claude/skills/create-pr/SKILL.md
```

Expected:
- `78:## PR 작성 절차` (또는 비슷한 라인 번호)
- `80:1. \`git diff main...HEAD\` 로 변경 파일 목록 확인` (또는 비슷한)

이 두 줄 사이가 0번 단계를 삽입할 위치다.

- [ ] **Step 2: 0번 단계 본문 삽입**

`Edit` 도구로 정확히 다음 치환을 수행한다 (old_string 은 SKILL.md 의 실제 텍스트와 글자·줄바꿈 모두 일치해야 한다 — 한글 공백 포함):

`old_string`:
```
## PR 작성 절차

1. `git diff main...HEAD` 로 변경 파일 목록 확인
```

`new_string`:
```
## PR 작성 절차

0. **`/cr` 셀프 리뷰 선행**

   PR 본문을 작성하기 전에 `/cr` 슬래시 커맨드를 호출해 현재 브랜치를 base(보통 `main`) 와 비교한 셀프 리뷰 리포트를 받는다. base 결정은 `/cr` 자체가 수행하므로 별도 인자 없이 호출해도 된다.

   리포트의 **Verdict** 에 따라 분기:

   - 🔴 **Critical (>0)** — PR 생성을 **중단**한다. 리포트를 그대로 사용자에게 보여주고, 한 줄로 안내한다: "Critical 이슈가 있어 PR 생성을 중단했습니다. 수정 후 다시 PR 생성을 요청해 주세요." 이후 단계로 진행하지 않는다.

   - 🟡 **Major (>0, Critical=0)** — 리포트를 그대로 보여주고 명시적 확인을 받는다: "Major 이슈가 N건 있습니다. 그대로 PR 을 생성할까요?" 사용자가 "예 / yes / 진행" 류로 답하면 1단계로 진행. "아니오 / 수정 / no" 류면 중단.

   - 🟢 **Critical=0, Major=0** — 리포트를 보여주고 별도 확인 없이 곧바로 1단계로 진행한다.

   리포트는 **콘솔에만** 출력한다. PR 본문에는 포함하지 않는다.

1. `git diff main...HEAD` 로 변경 파일 목록 확인
```

- [ ] **Step 3: 삽입 결과 검증**

Run:
```bash
grep -n "0\. \*\*\`/cr\` 셀프 리뷰 선행\*\*" .claude/skills/create-pr/SKILL.md
grep -n "^1\. \`git diff main" .claude/skills/create-pr/SKILL.md
```

Expected: 0번 단계 라인 번호가 1번 단계 라인 번호보다 작아야 한다 (예: 0번 = 80, 1번 = 96 정도).

추가:
```bash
grep -c "^[0-7]\. " .claude/skills/create-pr/SKILL.md
```

Expected: `8` (0번부터 7번까지 총 8개 번호 항목 — 기존 7개 + 신규 0번)

- [ ] **Step 4: 다른 섹션이 우발적으로 변경되지 않았는지 확인**

Run:
```bash
git diff --stat .claude/skills/create-pr/SKILL.md
```

Expected: 1 file changed, 약 16-18 insertions(+), 0 deletions(-) (0번 단계 본문 + 빈 줄 정도).

추가 확인 — 기존 섹션들이 그대로 살아있는지:
```bash
grep -c "^## " .claude/skills/create-pr/SKILL.md
```

Expected: `6` (영역 태그 규칙, 브랜치 네이밍 규칙, 커밋 타입 기준, PR 작성 절차, 스펙 문서 완료 처리, PR 템플릿, 주의 사항 — 원래 7개였는데 정확히 확인 필요). 사전에:

```bash
git show HEAD:.claude/skills/create-pr/SKILL.md | grep -c "^## "
```
로 변경 전 개수를 확인하고, 변경 후도 같은 개수인지 검증.

- [ ] **Step 5: 커밋**

```bash
git add .claude/skills/create-pr/SKILL.md
git commit -m "$(cat <<'EOF'
feat(meta): create-pr 스킬에 /cr 셀프 리뷰 선행 단계 통합

PR 작성 절차 1번 앞에 0번 단계 추가. /cr 리포트의 Verdict 분기로
Critical → PR 생성 중단, Major → 사용자 확인 후 진행, 깨끗 → 그대로
1단계로. 리포트는 콘솔에만 출력, PR 본문 자동 삽입 X.
EOF
)"
```

Expected: 1 commit 추가, `.claude/skills/create-pr/SKILL.md` 1 파일 수정.

---

## Task 2: 수동 스모크 검증 (사용자 수행)

**Files:** No file changes — 사용자가 Claude Code 세션에서 직접 수행하는 검증 절차.

이 작업은 PR #99 (`feat/code-review-flow`) 가 main 에 머지된 후, 또는 PR #99 의 head 브랜치가 활성 상태일 때 실행 가능하다. `/cr` 슬래시 커맨드가 환경에서 사용 가능해야 한다.

- [ ] **Step 1: 깨끗한 변경 (🟢) 시나리오**

작은 변경 1개(예: README 주석 1줄)만 들어간 임시 브랜치에서:

```
PR 만들어줘
```

점검:
- [ ] `/cr` 가 먼저 호출되고 리포트가 콘솔에 출력된다
- [ ] Verdict 가 🟢 라면 별도 확인 prompt 없이 곧장 다음 단계(`git diff main...HEAD`) 로 진행한다
- [ ] 최종적으로 `gh pr create` 가 실행되어 PR 이 생성된다

- [ ] **Step 2: Critical 차단 (🔴) 시나리오**

의도적으로 Critical 위반(예: `.env` 같은 시크릿 파일 커밋, 또는 application→domain 역방향 의존 1줄)을 임시 브랜치에 도입 후:

```
PR 만들어줘
```

점검:
- [ ] 리포트의 Verdict 가 🔴 로 나온다
- [ ] "Critical 이슈가 있어 PR 생성을 중단했습니다." 라는 안내가 출력된다
- [ ] `gh pr create` 가 **실행되지 않는다**
- [ ] 절차 1~7 단계로 진행되지 않는다

검증 후 임시 변경 폐기:
```bash
git checkout - && git branch -D tmp/cr-integration-critical
```

- [ ] **Step 3: Major 확인 (🟡) 시나리오**

의도적인 컨벤션 위반(예: Lombok `@Data` 사용 등 `docs/CONVENTIONS.md` 의 명시적 금지 패턴) 1줄을 임시 브랜치에 도입 후:

```
PR 만들어줘
```

점검:
- [ ] 리포트 Verdict 가 🟡 로 나온다
- [ ] "Major 이슈가 N건 있습니다. 그대로 PR 을 생성할까요?" 확인 prompt 가 표시된다
- [ ] "아니오" 라고 답하면 PR 생성이 중단된다
- [ ] (재시도) "예" 라고 답하면 1~7단계로 진행되어 PR 이 생성된다

- [ ] **Step 4: 검증 결과 정리 및 필요 시 SKILL.md 후속 수정**

검증 중 발견된 문제(예: 안내 문구 모호, 확인 prompt 가 안 뜸, PR 본문에 리포트가 새어 나옴 등)가 있으면 `SKILL.md` 를 추가 수정해 별도 커밋:

```bash
git add .claude/skills/create-pr/SKILL.md
git commit -m "fix(meta): create-pr 셀프 리뷰 통합 스모크 피드백 반영"
```

문제가 없으면 추가 커밋 없이 종료.

---

## Self-Review (작성 후 자기 점검 결과)

**Spec 커버리지:**
- ✅ 결정 사항 표 (변경 위치 / Critical 차단 / Major 확인 / 깨끗 통과 / 콘솔만) → Task 1 Step 2 본문에 모두 반영
- ✅ 변경 파일 — `SKILL.md` 한 파일만 → File Structure 표
- ✅ 단계 번호 매김 — 기존 1~7 유지, 0번 신규 → Task 1 Step 3 grep 검증
- ✅ 사용자 경험 흐름 → Task 2 의 3가지 스모크 시나리오에 1:1 대응 (🟢/🔴/🟡)
- ✅ 스코프 명시적 제외 → 어떤 태스크에도 들어가지 않았음 (PR 본문 자동 삽입 X, Major 자동 차단 X, Minor 사용자 확인 X, 다른 리뷰 커맨드 연동 X, /cr·code-reviewer 자체 수정 X, hook 자동 트리거 X)
- ✅ 의존성 (PR #99 머지 후 실제 사용) → Task 2 도입부에 명시

**Placeholder 스캔:**
- 모든 파일 경로 명시 ✅
- 모든 코드/마크다운 블록은 완성된 내용 ✅
- "TBD" / "이후 결정" 없음 ✅
- 사용자 답변 키워드 ("예/yes/진행" vs "아니오/수정/no") 가 명시적으로 열거 ✅

**Type 일관성:**
- 심각도 라벨: 🔴 Critical / 🟡 Major / 🟢 (Critical=0, Major=0) — 모든 섹션 일관 ✅
- 안내 문구: spec 의 4번 섹션과 plan Task 1 Step 2 의 new_string 본문이 글자 단위로 일치 ✅
- 단계 번호: 0번 신규, 1~7 기존 유지 — Task 1 Step 3 의 grep 패턴(`^[0-7]\. `) 과 일관 ✅
