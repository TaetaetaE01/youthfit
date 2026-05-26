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
BASE="${ARGUMENTS:-main}"
if ! git rev-parse --verify "$BASE" >/dev/null 2>&1; then
  echo "ERR: base branch '$BASE' not found locally — fetch 하거나 다른 base 를 지정하세요."
  exit 1
fi
HEAD=$(git rev-parse --abbrev-ref HEAD)
echo "BASE=$BASE HEAD=$HEAD"
```

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
2. 컨벤션 판단은 .claude/rules/ (common.md, backend/, frontend/), backend/CLAUDE.md, frontend/CLAUDE.md 를 참조하세요.
3. 정의된 8개 카테고리를 순서대로 점검하고, 발견된 이슈만 리포트에 적으세요.
4. 마지막에 정해진 리포트 포맷 마크다운만 출력하세요. 메타 코멘트 금지.
```

## 4. 결과 패스스루

서브에이전트가 돌려준 마크다운을 **그대로** 사용자에게 보여준다. 메인 세션은 어떤 추가 코멘트·요약·재해석도 붙이지 않는다.
