# PR 머지 전 셀프 리뷰 플로우 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** YouthFit 레포에 `/cr [base]` 슬래시 커맨드와 `code-reviewer` 서브에이전트를 추가해, PR 머지 전 현재 브랜치를 base 와 비교한 단일 리뷰어 마크다운 리포트를 메인 세션 컨텍스트 오염 없이 받을 수 있게 한다.

**Architecture:** 슬래시 커맨드(`/cr`)가 base 결정·변경 규모 사전 진단을 수행한 뒤 `Agent(subagent_type="code-reviewer", …)` 를 1회 호출. 서브에이전트는 독립 컨텍스트에서 diff·컨벤션 문서를 직접 읽어 8개 카테고리·3단계 심각도 기준의 마크다운 리포트를 생성. 메인 세션은 리포트를 그대로 패스스루 (요약·재해석 금지).

**Tech Stack:** Claude Code 서브에이전트 정의 (`.claude/agents/*.md` frontmatter), Claude Code 슬래시 커맨드 정의 (`.claude/commands/*.md` frontmatter + `$ARGUMENTS`), git CLI (`git diff`, `git rev-parse`, `git diff --shortstat`), Bash.

**Spec:** `docs/superpowers/specs/2026-05-14-code-review-flow-design.md`

---

## File Structure

| 파일 | 책임 | 신규/수정 |
|---|---|---|
| `.claude/agents/code-reviewer.md` | 서브에이전트 정의: 페르소나, 툴 화이트리스트, 모델, 점검 카테고리 8개, 심각도, 리포트 포맷 | 신규 |
| `.claude/commands/cr.md` | 슬래시 커맨드 정의: base 결정, 사전 진단, `Agent` 호출 프롬프트, 결과 패스스루 | 신규 |

YouthFit 의 `.claude/agents/` 폴더에는 이미 `frontend-developer.md`, `ui-ux-designer.md` 가 존재한다 (Claude Code 표준 서브에이전트 frontmatter 형식). `.claude/commands/` 폴더는 현재 비어 있다.

---

## Task 1: `code-reviewer` 서브에이전트 정의 파일 작성

**Files:**
- Create: `.claude/agents/code-reviewer.md`
- Reference: `.claude/agents/frontend-developer.md` (frontmatter 패턴 확인용)
- Reference: `docs/CONVENTIONS.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md` (리뷰어 페르소나에서 인용)

- [ ] **Step 1: 기존 서브에이전트 frontmatter 패턴 확인**

Run:
```bash
head -15 .claude/agents/frontend-developer.md
```

Expected: `---` 로 시작하는 YAML frontmatter, `name:`, `description:`, `tools:`, `model:` 필드 형식 확인. 우리는 동일 형식을 따른다.

- [ ] **Step 2: `code-reviewer.md` 생성**

Write `.claude/agents/code-reviewer.md`:

````markdown
---
name: code-reviewer
description: Use when reviewing the current branch's diff against a base branch before merging — single-reviewer self-review that produces a markdown report with severity-tagged issues. Triggered by the /cr slash command; do not invoke for other-people PR review (use built-in /review instead).
tools: Read, Grep, Glob, Bash, WebFetch
model: opus
---

# Code Reviewer

당신은 YouthFit 의 PR 머지 전 셀프 리뷰를 수행하는 시니어 백엔드/풀스택 리뷰어다. 메인 세션은 당신을 1회 호출하고 당신의 마크다운 리포트를 그대로 사용자에게 보여준다.

## 선험적 컨텍스트
- 아키텍처: DDD + Clean Architecture
- 백엔드 모듈 경계: `ingestion`, `policy`, `rag`, `guide`, `eligibility`, `qna`, `auth`, `user`, `common` — 이 경계는 `backend/CLAUDE.md` 에 정의됨
- 컨벤션: `docs/CONVENTIONS.md` (네이밍, DTO 경계, 예외 처리, Lombok)
- 프런트엔드 규칙: `frontend/CLAUDE.md`
- 작업 원칙: 작고 되돌리기 쉬운 변경, 한 슬라이스/한 모듈, 비로그인 핫패스에서 LLM 직접 유발 금지, 변경 감지·캐시·비용 방어

## 입력 (메인 세션이 전달)
- `BASE` 브랜치명 (예: `main`)
- `HEAD` 브랜치명 (현재 브랜치)
- `git diff --name-status $BASE...HEAD` 결과 (변경 파일 분류)

## 절차

1. 위 입력을 받아 변경 파일 목록을 파악한다.
2. 필요한 만큼 `git diff $BASE...HEAD -- <path>` 를 직접 실행해 각 파일의 실제 diff 를 읽는다. 한 번에 모든 diff 를 가져오지 말고 카테고리별로 나눠 읽는다.
3. 의심스러운 변경이 보이면 주변 파일(`Read`)·연관 심볼(`Grep`)·관련 컨벤션 문서를 추가로 확인한다.
4. 외부 라이브러리 변경/CVE 확인이 필요하면 `WebFetch` 사용 (필수 아님).
5. 8개 카테고리를 순서대로 점검하고 발견된 이슈만 리포트에 적는다.
6. 마지막으로 아래 "리포트 포맷" 그대로 마크다운만 출력한다. 메타 코멘트("리뷰를 시작합니다" 등) 금지.

## 점검 카테고리 (8개)

| # | 카테고리 | 점검 내용 |
|---|---|---|
| 1 | 모듈 경계 & 의존 방향 | `backend/CLAUDE.md` 모듈 경계 위반, application→domain 역방향 의존, RAG/Guide 가 비로그인 핫패스에서 LLM 직접 유발 |
| 2 | 컨벤션 | 네이밍, DTO 경계, 예외 처리, Lombok 사용 규칙 (`docs/CONVENTIONS.md`) |
| 3 | 정확성 / 엣지케이스 | null·empty·timeout, 트랜잭션 경계, 동시성, 캐시 키 충돌 |
| 4 | 보안 | 커밋된 시크릿, SQL/XSS, 외부 API 호출 시 PII 노출, robots.txt · Rate limit 준수 |
| 5 | 테스트 | 슬라이스/통합/단위 선택 적절성, 의미 없는 assert, mock 과다 |
| 6 | 비용 / 성능 | LLM·임베딩 캐싱·변경 감지, N+1, 불필요한 리렌더, 큰 페이로드 |
| 7 | 변경 스코프 | 한 슬라이스/한 모듈 원칙 위반, 무관한 리팩토링 끼워넣기 |
| 8 | 문서 / 마이그레이션 | 모듈 경계 변경 시 `docs/ARCHITECTURE.md` 갱신 필요성, ADR · troubleshooting 누락 |

## 심각도

- **Critical** — 머지하면 안 됨 (보안 · 아키텍처 위반 · 데이터 손상 가능성)
- **Major** — 머지 전 수정 권장 (논리 버그 · 테스트 부재 · 중대한 컨벤션 위반)
- **Minor** — 후속 PR 가능 (네이밍 · 스타일 · 작은 개선)

## 리포트 포맷

마크다운만 출력. 본문 외 어떤 메타 코멘트도 붙이지 마라.

````
# Code Review Report

**Base**: <BASE> … **Head**: <HEAD>
**Scope**: <N> files, +<add> / -<del>
**Verdict**: 🟢 머지 가능 / 🟡 Major 이슈 해결 후 머지 / 🔴 Critical 차단

---

## 🔴 Critical (<n>)
_(0건이면 이 섹션 자체를 생략)_

### C1 · [<카테고리>] <파일경로>:<라인>
- **문제**: 한 줄 설명
- **제안**: 구체적 수정 방향 (필요 시 코드 스니펫)
- **근거**: `docs/...` 또는 `backend/CLAUDE.md` 인용

## 🟡 Major (<n>)
_(0건이면 섹션 생략, 구조는 Critical 과 동일)_

## 🟢 Minor (<n>)
_(0건이면 섹션 생략)_

---

## 카테고리 점검 노트
- ✅ 모듈 경계 & 의존 방향  _(또는 ⚠️ → Critical/Major/Minor n개)_
- ✅ 컨벤션
- ✅ 정확성 / 엣지케이스
- ✅ 보안
- ✅ 테스트
- ✅ 비용 / 성능
- ✅ 변경 스코프
- ✅ 문서 / 마이그레이션

## 추천 후속 명령
- 실행 가능한 1-2줄. 예: `./gradlew :backend:test`, `git rebase -i <BASE>` 등
````

## 규칙 (반드시 지킴)

- 모든 이슈는 `파일:라인` 인용을 포함한다. 라인 정보를 찾을 수 없으면 그 이슈는 적지 않는다.
- 칭찬·전반 요약·"전반적으로 좋아 보입니다" 같은 문구 금지.
- 변경 없는 카테고리는 점검 노트에 ✅ 로만 표시. Critical/Major/Minor 섹션에 0건이면 해당 섹션 자체를 생략.
- 추천 후속 명령은 실행 가능한 1-2줄만.
- 코드 수정·파일 작성·재위임 금지. 당신은 리포트 전용이다.
````

- [ ] **Step 3: frontmatter 형식 검증**

Run:
```bash
head -6 .claude/agents/code-reviewer.md
```

Expected:
```
---
name: code-reviewer
description: Use when reviewing the current branch's diff against a base branch before merging — single-reviewer self-review that produces a markdown report with severity-tagged issues. Triggered by the /cr slash command; do not invoke for other-people PR review (use built-in /review instead).
tools: Read, Grep, Glob, Bash, WebFetch
model: opus
---
```

Run:
```bash
grep -c "^## " .claude/agents/code-reviewer.md
```

Expected: `7` (선험적 컨텍스트, 입력, 절차, 점검 카테고리, 심각도, 리포트 포맷, 규칙 — 7개 `##` 섹션)

- [ ] **Step 4: 커밋**

```bash
git add .claude/agents/code-reviewer.md
git commit -m "$(cat <<'EOF'
feat(claude): code-reviewer 서브에이전트 정의 추가

PR 머지 전 셀프 리뷰 전용 단일 리뷰어. 8개 카테고리·3단계 심각도 기준
마크다운 리포트만 생성, 코드 수정·재위임은 차단.
EOF
)"
```

Expected: 커밋 1개 추가, `git log -1 --stat` 에서 `.claude/agents/code-reviewer.md` 1 파일 추가 확인.

---

## Task 2: `/cr` 슬래시 커맨드 정의 파일 작성

**Files:**
- Create: `.claude/commands/cr.md`

- [ ] **Step 1: 슬래시 커맨드 frontmatter 형식 확인**

Run:
```bash
mkdir -p .claude/commands && ls .claude/commands/
```

Expected: 빈 디렉터리 (커맨드 파일 없음). 새로 만든다.

- [ ] **Step 2: `cr.md` 생성**

Write `.claude/commands/cr.md`:

````markdown
---
description: PR 머지 전 셀프 리뷰. 현재 브랜치를 base 와 비교해 code-reviewer 서브에이전트가 마크다운 리포트를 생성한다.
argument-hint: "[base-branch]"
allowed-tools: Bash, Agent
---

PR 머지 전 셀프 리뷰를 수행한다. `code-reviewer` 서브에이전트를 1회 호출해 받은 마크다운 리포트를 **그대로** 사용자에게 출력한다. 추가 해석·요약을 덧붙이지 않는다.

## 1. base 결정

`$ARGUMENTS` 가 있으면 그 값을 BASE 로 사용. 없으면 `main`.

Run:
```bash
BASE="${ARG:-main}"
git rev-parse --verify "$BASE" >/dev/null 2>&1 && echo "BASE=$BASE" || echo "ERR: base branch '$BASE' not found locally — fetch 하거나 다른 base 를 지정하세요."
```

`ERR` 가 출력되면 사용자에게 그 한 줄만 보여주고 중단한다.

## 2. 변경 규모 사전 진단

Run:
```bash
git diff --shortstat "$BASE"...HEAD
git diff --name-status "$BASE"...HEAD
```

`--shortstat` 결과를 1줄 보고하고, 50 파일 또는 2000 라인 초과 시 다음 경고를 1줄 추가:

> ⚠️ 큰 변경이라 리뷰 정확도가 떨어질 수 있습니다. 가능하면 작은 PR 로 분리하세요.

## 3. `code-reviewer` 서브에이전트 호출

`Agent` 도구를 다음 파라미터로 호출:
- `subagent_type`: `code-reviewer`
- `description`: `PR self-review`
- `prompt`: 아래 템플릿. `<BASE>`, `<HEAD>`, `<NAME_STATUS>` 는 실제 값으로 치환.

```
당신은 code-reviewer 서브에이전트로 호출되었습니다. 입력은 다음과 같습니다.

BASE: <BASE>
HEAD: <HEAD>

변경 파일 목록 (git diff --name-status <BASE>...HEAD):
<NAME_STATUS>

작업:
1. 각 파일의 실제 diff 는 `git diff <BASE>...HEAD -- <path>` 로 직접 가져오세요.
2. 컨벤션 판단은 docs/CONVENTIONS.md, backend/CLAUDE.md, frontend/CLAUDE.md 를 참조하세요.
3. 정의된 8개 카테고리를 순서대로 점검하고, 발견된 이슈만 리포트에 적으세요.
4. 마지막에 정해진 리포트 포맷 마크다운만 출력하세요. 메타 코멘트 금지.
```

## 4. 결과 패스스루

서브에이전트가 돌려준 마크다운을 **그대로** 사용자에게 보여준다. 메인 세션은 어떤 추가 코멘트·요약·재해석도 붙이지 않는다.
````

- [ ] **Step 3: frontmatter / 본문 구조 검증**

Run:
```bash
head -5 .claude/commands/cr.md
```

Expected:
```
---
description: PR 머지 전 셀프 리뷰. 현재 브랜치를 base 와 비교해 code-reviewer 서브에이전트가 마크다운 리포트를 생성한다.
argument-hint: "[base-branch]"
allowed-tools: Bash, Agent
---
```

Run:
```bash
grep -c "^## [1-4]\." .claude/commands/cr.md
```

Expected: `4` (1. base 결정, 2. 변경 규모 사전 진단, 3. code-reviewer 호출, 4. 결과 패스스루)

- [ ] **Step 4: 커밋**

```bash
git add .claude/commands/cr.md
git commit -m "$(cat <<'EOF'
feat(claude): /cr 슬래시 커맨드 추가

base 결정 + 변경 규모 사전 진단 후 code-reviewer 서브에이전트를 1회 호출,
마크다운 리포트를 메인 세션에 그대로 패스스루.
EOF
)"
```

Expected: 커밋 1개 추가, `.claude/commands/cr.md` 1 파일 추가.

---

## Task 3: 수동 스모크 검증

**Files:**
- No file changes (검증 전용). 검증 중 일시적으로 만든 임시 변경은 작업 종료 시 폐기.

- [ ] **Step 1: 현재 브랜치에서 `/cr` 실행**

이 태스크는 사용자가 Claude Code 세션에서 직접 수행한다. 다음을 입력:

```
/cr
```

Expected output 형식:
1. `git diff --shortstat` 1줄 (예: ` 7 files changed, 312 insertions(+), 84 deletions(-)`)
2. (큰 변경이면) ⚠️ 경고 1줄 — 현재 브랜치 규모에 따라 출력 여부 달라짐
3. `code-reviewer` 가 만든 마크다운 리포트 (Code Review Report 헤더, Verdict, Critical/Major/Minor 섹션, 카테고리 점검 노트)

점검:
- [ ] `# Code Review Report` 헤더가 있다
- [ ] `**Base**` / `**Head**` / `**Scope**` / `**Verdict**` 4줄이 모두 있다
- [ ] 발견된 모든 이슈에 `파일:라인` 인용이 들어 있다 (라인 번호 누락 X)
- [ ] 변경 없는 카테고리는 점검 노트에 ✅ 로만 표시되어 있고, 본문 섹션(Critical/Major/Minor)에 0건인 섹션은 통째로 생략되어 있다
- [ ] 메인 세션이 리포트 위/아래에 추가 해석·요약을 덧붙이지 않았다

리포트가 위 조건을 모두 만족하면 통과. 하나라도 실패면 해당 정의 파일(`code-reviewer.md` 또는 `cr.md`)을 수정 후 재실행.

- [ ] **Step 2: 작은 변경 케이스 검증**

새 임시 브랜치를 만들어 1 파일만 수정 후 `/cr` 실행:

```bash
git checkout -b tmp/cr-smoke-small
# 임의 파일 1개에 주석 한 줄 추가
echo "// smoke test comment" >> README.md
git add README.md && git commit -m "chore: smoke test"
```

세션에서:
```
/cr
```

점검:
- [ ] 카테고리 점검 노트가 8줄(8개 카테고리)을 모두 유지하되 대부분 ✅ 다
- [ ] 본문 Critical/Major/Minor 섹션이 통째로 생략되거나, 있다면 Minor 1건 정도로 짧다
- [ ] 추천 후속 명령이 1-2줄이고 실행 가능한 형태다

검증 끝나면:
```bash
git checkout - && git branch -D tmp/cr-smoke-small
```

- [ ] **Step 3: 큰 변경 가드 검증 (선택, 작은 케이스로 임계치 통과 확인)**

현재 변경 규모에 따라 자동으로 가드 메시지 노출 여부가 결정되므로, 별도 큰 PR 을 만들 필요 없다. 대신 `cr.md` 의 임계치 로직이 의도대로 적혀 있는지 코드 리딩으로 확인:

Run:
```bash
grep -A1 "50 파일 또는 2000 라인" .claude/commands/cr.md
```

Expected: 해당 임계치 + ⚠️ 경고 문구 1줄이 함께 보임.

- [ ] **Step 4: 컨벤션 적중성 검증 (의도적 위반 → Major [컨벤션] 잡히는지)**

새 임시 브랜치에서 `docs/CONVENTIONS.md` 가 명시적으로 금지하는 패턴 하나(예: DTO 가 도메인 엔티티를 그대로 노출하는 응답, 또는 Lombok `@Data` 무분별 사용)를 1줄로 도입:

```bash
git checkout -b tmp/cr-smoke-convention
```

위반 1줄을 backend 소스 어딘가에 직접 추가 (`docs/CONVENTIONS.md` 의 명시적 금지 패턴 1개 선택). 커밋:

```bash
git add -p   # 위반 라인만 의도적으로 스테이징
git commit -m "chore: smoke test (intentional convention violation)"
```

세션에서:
```
/cr
```

점검:
- [ ] 리포트의 Major 섹션에 해당 위반이 `[컨벤션]` 태그로 잡혀 있다
- [ ] `근거` 필드가 `docs/CONVENTIONS.md` 의 해당 항목을 인용한다

검증 끝나면:
```bash
git checkout - && git branch -D tmp/cr-smoke-convention
```

- [ ] **Step 5: 검증 결과 요약 + 필요 시 정의 파일 수정 후 추가 커밋**

검증 중 발견된 문제(예: 라인 번호 누락, 카테고리 점검 노트가 표시되지 않음, frontmatter 인식 안 됨 등)가 있으면 `code-reviewer.md` 또는 `cr.md` 를 수정하고 별도 커밋한다:

```bash
git add .claude/agents/code-reviewer.md .claude/commands/cr.md   # 변경된 것만
git commit -m "fix(claude): code-review 플로우 스모크 피드백 반영"
```

검증이 모두 통과하면 별도 커밋 없이 끝낸다.

---

## Self-Review (작성 후 자기 점검 결과)

**Spec 커버리지:**
- ✅ 결정 사항 표 (시나리오/구조/출력/트리거/컨텍스트 격리) → Task 1·2 가 모두 반영
- ✅ 파일 레이아웃 (`.claude/agents/code-reviewer.md`, `.claude/commands/cr.md`) → Task 1·2
- ✅ 페르소나·툴 화이트리스트·모델·8 카테고리·심각도·리포트 포맷 → Task 1 본문
- ✅ `/cr` 인자·내부 흐름 (base 결정·사전 진단·서브에이전트 호출·패스스루) → Task 2 본문
- ✅ 검증 (현재 브랜치/작은 변경/큰 변경 가드/컨벤션 적중성) → Task 3
- ✅ 운영 약속 (모듈 리스트 갱신, 컨벤션 인용 경로) — 별도 태스크는 두지 않음. 향후 모듈 경계 변경 PR 의 책임. spec 9절(의존성) 도 별도 태스크 불필요.
- ✅ 스코프 제외 항목 (hook 자동 트리거/자동 게시/자동 수정/멀티 에이전트/PR# 인자/SKILL.md) — 어떤 태스크에도 들어가지 않았음

**Placeholder 스캔:**
- 모든 파일 경로 명시 ✅
- 모든 코드/마크다운 블록은 완성된 내용 ✅
- "TBD" / "이후 결정" 없음 ✅

**Type 일관성:**
- 서브에이전트 이름: `code-reviewer` (Task 1 frontmatter, Task 2 의 Agent 호출, Task 3 검증) — 일치 ✅
- 슬래시 커맨드 이름: `/cr` (Task 2 파일명, Task 3 호출) — 일치 ✅
- 8개 카테고리 명·순서: Task 1 의 표와 리포트 포맷의 점검 노트, Task 3 의 점검 항목 — 일치 ✅
- 심각도 라벨: 🔴 Critical / 🟡 Major / 🟢 Minor (Task 1 본문·리포트 포맷·Task 3 검증) — 일치 ✅
