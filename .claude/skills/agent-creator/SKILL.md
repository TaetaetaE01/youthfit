---
name: agent-creator
description: Claude Code sub-agent를 대화형으로 설계하고 공식 frontmatter 형식에 맞는 agent Markdown 파일을 생성한다. 사용자가 "에이전트 만들어줘", "sub-agent 추가", "agent md 생성", "Claude Code agent 설계"처럼 요청할 때 사용한다.
---

# agent-creator — Claude Code sub-agent 생성 스킬

## 목적

Claude Code의 공식 sub-agent 형식에 맞춰 새 agent 파일을 설계하고 생성한다.  
사용자의 요구가 모호하면 추측하지 말고 단계적으로 질문하여 `name`, `description`, `tools`, `model`, `permissionMode`, `skills`, 저장 위치를 확정한다.

이 스킬은 다음 상황에서 사용한다.

- 사용자가 Claude Code용 custom agent 또는 sub-agent 생성을 요청할 때
- 기존 agent md를 개선하거나 공식 포맷에 맞게 정리해달라고 할 때
- 특정 워크플로우를 자동 위임 가능한 agent로 분리하고 싶어 할 때
- `.claude/agents/` 또는 `~/.claude/agents/`에 저장할 agent 파일이 필요할 때

## 핵심 원칙

1. **공식 필드 우선**
   - 필수: `name`, `description`
   - 선택: `tools`, `disallowedTools`, `model`, `permissionMode`, `maxTurns`, `skills`, `mcpServers`, `hooks`, `memory`, `background`, `effort`, `isolation`, `color`, `initialPrompt`

2. **자동 위임 품질은 description이 결정한다**
   - `description`에는 “무엇을 하는지”뿐 아니라 “언제 사용해야 하는지”를 명확히 쓴다.
   - 자동 호출을 원하면 `Use proactively`, `MUST BE USED`, `Use when...` 같은 명확한 트리거 문장을 포함한다.

3. **도구는 최소 권한으로 명시한다**
   - 생략하면 기본적으로 사용 가능한 도구를 상속할 수 있으나, 안전성과 예측 가능성을 위해 `tools` 또는 `disallowedTools`를 명시하는 것을 우선한다.
   - 쓰기 작업이 필요 없는 agent에는 `Write`, `Edit`를 주지 않는다.
   - sub-agent 내부에서 사용할 수 없는 도구를 무리하게 넣지 않는다.

4. **모호하면 추천안을 제시하고 확인한다**
   - 사용자가 잘 모르겠다고 하면 목적 기반 추천안을 제시한다.
   - 단, 저장 전에는 반드시 최종 파일 미리보기를 보여주고 승인받는다.

5. **직접 파일 수정 시 로딩 조건을 안내한다**
   - `/agents` UI로 생성한 agent는 즉시 사용 가능하다.
   - 디스크에 직접 `.md` 파일을 추가하거나 수정한 경우, 현재 Claude Code 세션에서 바로 인식되지 않을 수 있으므로 세션 재시작이 필요할 수 있다.

---

## 작업 절차

### 1단계 — 요구사항 수집

한 번에 모든 질문을 던지지 말고 1~2개씩 확인한다.

필수로 확인할 항목:

| 항목 | 설명 | 예시 |
|---|---|---|
| 목적 | agent가 수행할 일 | “Spring API 설계 리뷰” |
| 트리거 | 언제 자동 호출되어야 하는지 | “Controller/Service 변경 후” |
| 이름 | lowercase + kebab-case | `api-design-reviewer` |
| 도구 | 필요한 Claude Code tools | `Read, Grep, Glob, Bash` |
| 모델 | `sonnet`, `opus`, `haiku`, `inherit` | `sonnet` |
| 권한 모드 | 권한 처리 방식 | `default` |
| 스킬 | 미리 로드할 skill | `api-conventions` |
| 위치 | 프로젝트/사용자 범위 | `.claude/agents/` |

질문 예시:

```text
어떤 agent를 만들고 싶으세요? 한 줄로 목적을 적어주세요.
예: “Spring Boot API 설계와 예외 응답 구조를 리뷰하는 agent”
```

목적을 받으면 `name` 후보를 kebab-case로 제안한다.

```text
이름은 `spring-api-reviewer`가 적절해 보입니다. 이 이름으로 갈까요?
```

---

### 2단계 — description 설계

`description`은 Claude가 agent를 자동 호출할지 판단하는 핵심이다.

반드시 포함할 내용:

- agent의 전문 역할
- 자동 호출 조건
- 명시적 호출 문구
- 호출하면 안 되는 상황이 있다면 제외 조건

좋은 description 예시:

```yaml
description: Spring Boot API 설계와 예외 응답 구조를 리뷰하는 specialist. Use proactively when Controller, Service, DTO, ExceptionHandler, ApiResponse 코드가 추가되거나 수정된 뒤 품질, 일관성, 보안, 트랜잭션 경계를 점검해야 할 때. 단순 문법 질문이나 한 줄 코드 설명에는 사용하지 않는다.
```

나쁜 description 예시:

```yaml
description: 코드 리뷰
```

---

### 3단계 — tools 추천

목적에 따라 최소 권한 도구 세트를 추천한다.

| 유형 | 추천 tools | 설명 |
|---|---|---|
| 코드 리뷰 | `Read, Grep, Glob, Bash` | 수정 없이 분석 중심 |
| 기능 개발 | `Read, Write, Edit, Bash, Grep, Glob` | 파일 생성/수정 필요 |
| 디버깅 | `Read, Edit, Bash, Grep, Glob` | 테스트 실행 + 최소 수정 |
| 테스트 작성 | `Read, Edit, Write, Bash, Grep, Glob` | 테스트 파일 생성/수정 |
| 문서화 | `Read, Write, Edit, Grep, Glob` | 문서 생성/수정 |
| 리서치 | `Read, Grep, Glob, WebFetch, WebSearch` | 외부 정보 조사 포함 |
| 읽기 전용 탐색 | `Read, Grep, Glob` | 안전한 코드베이스 분석 |

도구 선택 기준:

- 파일을 수정해야 하면 `Edit` 필요
- 새 파일을 만들어야 하면 `Write` 필요
- 테스트/빌드/grep 외 명령 실행이 필요하면 `Bash` 필요
- 외부 문서 확인이 필요하면 `WebFetch`, `WebSearch` 필요
- MCP 연동이 필요하면 해당 MCP tool 또는 `mcpServers` 필드 검토

---

### 4단계 — model 추천

목적에 따라 모델을 추천한다.

| 작업 유형 | 추천 model | 이유 |
|---|---|---|
| 복잡한 설계/아키텍처/보안 리뷰 | `opus` | 깊은 추론 필요 |
| 일반 구현/리팩토링/테스트 작성 | `sonnet` | 품질과 속도 균형 |
| 단순 분류/문서 정리/검색 | `haiku` | 빠르고 가벼움 |
| 목적이 섞였거나 불명확 | `inherit` | 현재 세션 모델 상속 |

기본 추천:

- 대부분의 개발 agent: `sonnet`
- 리뷰/설계 중심이면서 품질이 중요함: `opus`
- 반복적이고 단순한 정리 작업: `haiku`
- 팀/환경별 모델 정책을 따르고 싶음: `inherit`

비용/품질 경고:

- 단순 작업에 `opus`를 선택하면 비용 과다 가능성을 알린다.
- 복잡한 리뷰/설계 작업에 `haiku`를 선택하면 품질 저하 가능성을 알린다.

---

### 5단계 — permissionMode 설정

기본값은 `default`를 권장한다.

| mode | 사용 상황 |
|---|---|
| `default` | 일반적인 권한 확인. 기본 추천 |
| `acceptEdits` | 파일 수정은 자동 승인하고 싶을 때 |
| `auto` | 자동 분류 기반 권한 처리를 사용할 때 |
| `dontAsk` | 프롬프트가 필요한 작업은 자동 거절하고 싶을 때 |
| `bypassPermissions` | 매우 신뢰된 자동화에서만 사용. 위험 |
| `plan` | 읽기 전용 분석/계획 전용 |

주의:

- `bypassPermissions`는 매우 신중히 사용한다.
- 리뷰/리서치 agent는 보통 `default` 또는 `plan`이 적합하다.
- 구현 agent는 `default` 또는 제한적으로 `acceptEdits`를 검토한다.

---

### 6단계 — 추가 옵션 확인

명시적 필요가 있을 때만 추가한다.

| 필드 | 사용 상황 |
|---|---|
| `skills` | agent 시작 시 특정 skill 내용을 미리 주입해야 할 때 |
| `memory` | agent가 프로젝트/사용자 단위로 학습 내용을 누적해야 할 때 |
| `isolation: worktree` | 독립 작업 복사본에서 안전하게 수정해야 할 때 |
| `maxTurns` | 무한 루프 방지를 위해 턴 수 제한이 필요할 때 |
| `background: true` | 항상 백그라운드로 실행하고 싶을 때 |
| `effort` | 모델의 reasoning effort를 명시해야 할 때 |
| `color` | UI에서 agent를 구분하고 싶을 때 |
| `hooks` | 특정 도구 실행 전후 검증을 강제해야 할 때 |
| `mcpServers` | 특정 MCP 서버 접근이 필요할 때 |

기본적으로는 간단한 agent부터 시작하고, 필요할 때만 추가한다.

---

### 7단계 — system prompt 작성

frontmatter 아래 본문은 agent의 system prompt다. 다음 구조를 기본으로 사용한다.

```markdown
# <Agent Display Name>

## 역할
<agent의 전문 역할을 한 문장으로 정의한다.>

## 작업 시작 전 확인
1. 관련 파일과 변경 범위를 먼저 파악한다.
2. 프로젝트의 CLAUDE.md, README, 컨벤션 문서가 있으면 우선 확인한다.
3. 요구사항이 불명확하면 임의 구현하지 말고 가정을 명시한다.

## 절차
1. 현재 작업 맥락과 관련 파일을 탐색한다.
2. 핵심 이슈 또는 구현 대상을 분류한다.
3. 필요한 경우 테스트/빌드/정적 검사를 실행한다.
4. 결과를 우선순위별로 정리한다.
5. 수정이 허용된 agent라면 최소 변경으로 반영한다.

## 출력 형식
- 요약
- 주요 발견 사항
- 권장 조치
- 수정/검증 결과
- 남은 리스크

## 금지사항
- 근거 없는 추측으로 파일을 수정하지 않는다.
- 필요 이상의 대규모 리팩토링을 하지 않는다.
- secret, token, key 값을 출력하지 않는다.
- 사용자 승인 없이 위험한 destructive command를 실행하지 않는다.
```

agent 목적에 맞춰 “절차”, “출력 형식”, “금지사항”을 구체화한다.

---

### 8단계 — 저장 위치 결정

| 위치 | 경로 | 적합한 경우 |
|---|---|---|
| 프로젝트 | `.claude/agents/<name>.md` | 특정 repo 전용, 팀 공유, 버전 관리 |
| 사용자 | `~/.claude/agents/<name>.md` | 여러 프로젝트에서 개인적으로 재사용 |

기본 추천:

- 팀 프로젝트/레포 컨벤션 기반 agent: `.claude/agents/`
- 개인 업무 습관 기반 agent: `~/.claude/agents/`

필요시 디렉토리를 생성한다.

```bash
mkdir -p .claude/agents
mkdir -p ~/.claude/agents
```

---

### 9단계 — dry-run 미리보기

저장 전 반드시 완성된 파일을 코드 블록으로 보여준다.

```text
아래 내용으로 저장할까요? 수정할 부분이 있으면 말해주세요.
```

사용자의 응답에 따라 처리한다.

- `저장` → 파일 생성
- `수정` → 해당 섹션만 다시 질문
- `취소` → 생성 중단

---

### 10단계 — 저장 후 안내

저장 후 다음을 안내한다.

```text
저장 완료: <path>

사용 방법:
- 자동 호출: description 조건에 맞는 작업을 요청하면 Claude가 자동 위임할 수 있습니다.
- 명시 호출: "Use the <name> agent to ..." 또는 @mention으로 호출할 수 있습니다.
- 직접 파일로 추가한 경우 현재 세션에서 인식되지 않으면 Claude Code를 재시작하세요.
```

---

## 출력 agent 파일 템플릿

```markdown
---
name: <kebab-case-name>
description: <역할 + 자동 호출 조건 + 제외 조건>
tools: Read, Grep, Glob, Bash
model: sonnet
permissionMode: default
---

# <Agent Display Name>

## 역할
<역할 설명>

## 작업 시작 전 확인
1. <확인 사항>
2. <확인 사항>

## 절차
1. <절차>
2. <절차>
3. <절차>

## 출력 형식
- 요약
- 주요 발견 사항
- 권장 조치
- 검증 결과
- 남은 리스크

## 금지사항
- <금지사항>
- <금지사항>
```

---

## 검증 체크리스트

저장 직전에 반드시 확인한다.

- [ ] `name`이 lowercase + kebab-case 형식인가?
- [ ] 같은 scope 안에 동일한 `name`이 없는가?
- [ ] `description`에 자동 호출 조건이 명확한가?
- [ ] 호출하면 안 되는 상황이 필요한 경우 포함했는가?
- [ ] `tools`가 목적 대비 최소 권한인가?
- [ ] `model`이 작업 난이도와 비용에 맞는가?
- [ ] `permissionMode`가 안전한가?
- [ ] `skills`는 필요한 경우에만 넣었는가?
- [ ] 본문 system prompt가 비어 있지 않은가?
- [ ] 저장 위치가 프로젝트/사용자 범위에 맞는가?
- [ ] 저장 전 dry-run을 보여주고 승인받았는가?

---

## 안티패턴

- 한 번에 6개 이상 질문을 던지는 것
- `description`을 “코드 리뷰”, “개발”, “문서화”처럼 너무 짧게 쓰는 것
- 읽기 전용 agent에 `Write`, `Edit`를 주는 것
- 단순 반복 작업에 무조건 `opus`를 쓰는 것
- 복잡한 설계/보안 리뷰에 무조건 `haiku`를 쓰는 것
- 저장 전 미리보기를 생략하는 것
- 공식 frontmatter에 없는 임의 필드를 추가하는 것
- sub-agent에서 사용할 수 없는 도구를 무리하게 명시하는 것
- 직접 파일을 추가해놓고 “무조건 즉시 로드된다”고 안내하는 것

---

## 빠른 예시

### 코드 리뷰 agent

```markdown
---
name: code-reviewer
description: Expert code review specialist. Use proactively after code changes to review quality, security, maintainability, transaction boundaries, and test coverage. Do not use for simple syntax questions or general explanations.
tools: Read, Grep, Glob, Bash
model: inherit
permissionMode: default
---

# Code Reviewer

## 역할
코드 변경 사항을 품질, 보안, 유지보수성, 테스트 관점에서 리뷰하는 전문 agent다.

## 절차
1. `git diff`와 관련 파일을 확인한다.
2. 변경 범위를 기능/테스트/설정으로 분류한다.
3. 심각도 순으로 이슈를 정리한다.
4. 수정 제안은 구체적인 파일/위치/이유와 함께 제시한다.

## 출력 형식
- 요약
- Critical / Major / Minor 이슈
- 권장 수정안
- 테스트 필요 사항

## 금지사항
- 직접 코드를 수정하지 않는다.
- 근거 없는 스타일 취향을 강요하지 않는다.
```

### Spring API 구현 agent

```markdown
---
name: spring-api-developer
description: Spring Boot API implementation specialist. Use when implementing or modifying Controller, Service, DTO, Repository, Entity, ExceptionHandler, or API response code. MUST BE USED for multi-file backend API changes.
tools: Read, Write, Edit, Bash, Grep, Glob
model: sonnet
permissionMode: default
---

# Spring API Developer

## 역할
Spring Boot 기반 API를 프로젝트 컨벤션에 맞춰 구현하는 backend development agent다.

## 작업 시작 전 확인
1. 기존 패키지 구조와 네이밍 컨벤션을 확인한다.
2. 공통 응답, 예외 처리, 트랜잭션 패턴을 확인한다.
3. 관련 테스트 또는 빌드 명령을 확인한다.

## 절차
1. 요구사항을 API 단위로 분해한다.
2. 필요한 DTO, Service, Repository, Controller 변경 범위를 정한다.
3. 최소 변경으로 구현한다.
4. 가능한 경우 테스트 또는 빌드를 실행한다.
5. 변경 파일과 검증 결과를 요약한다.

## 출력 형식
- 구현 요약
- 변경 파일
- 검증 결과
- 남은 리스크

## 금지사항
- 기존 아키텍처를 무시한 대규모 구조 변경을 하지 않는다.
- secret, token, credential을 출력하지 않는다.
- 테스트 실패를 숨기지 않는다.
```
