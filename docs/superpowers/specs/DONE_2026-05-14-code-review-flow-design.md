# 코드 리뷰 플로우 설계 — PR 머지 전 셀프 리뷰

- 작성일: 2026-05-14
- 상태: Draft (사용자 검토 대기)
- 범위: YouthFit 레포의 PR 머지 전 셀프 리뷰 자동화

## 1. 배경 / 목표

YouthFit 작업은 작고 되돌리기 쉬운 단위로 끊어 PR 을 올리는 흐름이다. 머지 직전에 본인이 다각도로 점검하고 싶지만, 빌트인 `/review` 는 *남이 만든 PR 리뷰* 에 맞춰져 있고, `/ultrareview` 는 사용자 트리거·과금 부담이 있다. 가벼운 **셀프 리뷰 전용 슬래시 커맨드**가 필요하다.

목표: 현재 브랜치의 변경분을 YouthFit 컨벤션(DDD + Clean Architecture, 모듈 경계, Conventional Commits, `docs/CONVENTIONS.md`) 기준으로 단일 리뷰어가 점검하고, 메인 세션의 컨텍스트 오염 없이 마크다운 리포트 한 장을 돌려주는 흐름을 만든다.

## 2. 결정 사항 요약

| 항목 | 결정 | 비고 |
|---|---|---|
| 리뷰 시나리오 | PR 머지 전 셀프 리뷰 | 다른 시나리오는 빌트인 `/review`, `/security-review`, `/ultrareview` 가 커버 |
| 리뷰 구조 | 단일 리뷰어 서브에이전트 | 멀티 관점 병렬은 YAGNI |
| 출력 | 메인 세션 콘솔에 마크다운 리포트만 | 자동 수정·자동 PR 코멘트 없음 |
| 트리거 | 수동 슬래시 커맨드 `/cr [base]` | hook 자동 트리거 없음 |
| 컨텍스트 격리 | 서브에이전트로 분리 | diff·문서 탐색이 메인 세션을 오염시키지 않음 |

## 3. 아키텍처 & 파일 레이아웃

```
youthfit/
└── .claude/
    ├── agents/
    │   └── code-reviewer.md      # 서브에이전트 정의 (페르소나·툴·체크리스트·리포트 포맷)
    └── commands/
        └── cr.md                 # 슬래시 커맨드 /cr (사전 진단 + 서브에이전트 호출 + 결과 패스스루)
```

### 역할 분리
- `code-reviewer.md` — **무엇을** 보고, **어떻게** 보고하는지. 리뷰어 페르소나, 점검 카테고리, 심각도, 리포트 포맷.
- `cr.md` — **언제·어떤 컨텍스트로** 호출하는지. base 결정, 변경 규모 사전 진단, 서브에이전트 호출 prompt 구성.

### 데이터 흐름
1. 사용자가 메인 세션에서 `/cr [base]` 실행.
2. 슬래시 커맨드가 `git rev-parse $BASE` 로 base 유효성 확인, `git diff --shortstat $BASE...HEAD` 로 변경 규모 1줄 보고.
3. 슬래시 커맨드가 `Agent(subagent_type="code-reviewer", prompt=…)` 1회 호출.
4. 서브에이전트는 자체 컨텍스트에서 `git diff`, `Read`, `Grep`, `docs/CONVENTIONS.md` 등 참조 후 마크다운 리포트 생성.
5. 메인 세션은 리포트를 **그대로** 사용자에게 표시 (요약·재해석 금지).

## 4. `code-reviewer` 서브에이전트 정의

### 페르소나
시니어 백엔드/풀스택 리뷰어. YouthFit 의 DDD + Clean Architecture, 모듈 경계(ingestion · policy · rag · guide · eligibility · qna · auth · user · common), `docs/CONVENTIONS.md` 를 선험적 컨텍스트로 사용한다. 칭찬·전반적 요약은 생략하고 **변경된 코드의 위험만** 짚는다.

### 툴 화이트리스트
- `Read`, `Grep`, `Glob` — diff·문서·연관 파일 탐색
- `Bash` — `git diff $BASE...HEAD`, `git log`, 빌드/테스트 명령어 가시화
- `WebFetch` — 외부 라이브러리 변경점·CVE 확인이 필요할 때만
- **제외**: `Write`, `Edit`, `NotebookEdit`, `Agent` (수정·재위임 금지 — 리포트 전용)

### 모델
`claude-opus-4-7` (리뷰 정확도 우선). 변경이 작을 때 `claude-sonnet-4-6` 폴백은 슬래시 커맨드에서 옵션으로만 노출 가능 (기본 채택 X).

### 점검 카테고리

| # | 카테고리 | 점검 내용 |
|---|---|---|
| 1 | 모듈 경계 & 의존 방향 | `backend/CLAUDE.md` 모듈 경계 위반, application→domain 역방향 의존, RAG/Guide 가 비로그인 핫패스에서 LLM 직접 유발 등 |
| 2 | 컨벤션 | 네이밍, DTO 경계, 예외 처리, Lombok 사용 규칙 (`docs/CONVENTIONS.md`) |
| 3 | 정확성 / 엣지케이스 | null·empty·timeout, 트랜잭션 경계, 동시성, 캐시 키 충돌 |
| 4 | 보안 | 커밋된 시크릿, SQL/XSS, 외부 API 호출 시 PII 노출, robots.txt · Rate limit 준수 |
| 5 | 테스트 | 슬라이스 / 통합 / 단위 선택 적절성, 의미 없는 assert, mock 과다 (`spring-test` 스킬 기준) |
| 6 | 비용 / 성능 | LLM·임베딩 캐싱·변경 감지, N+1, 불필요한 리렌더, 큰 페이로드 |
| 7 | 변경 스코프 | 한 슬라이스 / 한 모듈 원칙 위반, 무관한 리팩토링 끼워넣기 |
| 8 | 문서 / 마이그레이션 | 모듈 경계 변경 시 `docs/ARCHITECTURE.md` 갱신 필요성, ADR · troubleshooting 누락 |

### 심각도 분류
- **Critical** — 머지하면 안 됨 (보안 · 아키텍처 위반 · 데이터 손상 가능성)
- **Major** — 머지 전 수정 권장 (논리 버그 · 테스트 부재 · 중대한 컨벤션 위반)
- **Minor** — 후속 PR 가능 (네이밍 · 스타일 · 작은 개선)

### 리포트 포맷

````markdown
# Code Review Report

**Base**: main … **Head**: feat/guide-enrichment-bokjiro
**Scope**: 7 files, +312 / -84
**Verdict**: 🟡 Major 이슈 해결 후 머지 권장

---

## 🔴 Critical (0)
_(없으면 섹션 생략)_

## 🟡 Major (2)

### M1 · [컨벤션] backend/.../GuideValidatorTest.java:42
- **문제**: `@Mock` 으로 Repository 를 모킹하고 있으나 application 슬라이스 테스트에서는 실제 빈을 써야 함
- **제안**: `@DataJpaTest` + Testcontainers 패턴으로 전환 (`spring-test` 스킬 참고)
- **근거**: `backend/CLAUDE.md` 슬라이스 테스트 규약, 인접 `GuideListSectionTest` 와 패턴 어긋남

### M2 · [정확성] backend/.../GuideContent.java:118
…

## 🟢 Minor (3)
…

---

## 카테고리 점검 노트
- ✅ 모듈 경계 & 의존 방향
- ⚠️ 컨벤션 → Major 1
- ⚠️ 정확성 / 엣지케이스 → Major 1
- ✅ 보안
- ⚠️ 테스트 → Minor 2
- ✅ 비용 / 성능
- ✅ 변경 스코프
- ✅ 문서 / 마이그레이션

## 추천 후속 명령
- 머지 전: M1, M2 수정 후 `./gradlew :backend:test` 재실행
- 머지 후: Minor 항목은 별도 PR 가능
````

### 리포트 규칙
- 모든 이슈는 `파일:라인` 인용을 포함한다 (없으면 리뷰가 추상적이 됨).
- 칭찬 섹션 없음. "전반적으로 좋아 보입니다" 같은 문구 금지.
- 변경이 없는 카테고리는 점검 노트에 ✅ 로만 표시. Critical / Major / Minor 섹션에 0건이면 해당 섹션 자체를 생략.
- 추천 후속 명령은 실행 가능한 1–2줄로만.

## 5. `/cr` 슬래시 커맨드 동작

### 인자
```
/cr              # base 자동: main (없으면 git default branch)
/cr main         # 명시
/cr develop      # 다른 base 비교
```

PR 번호 인자(`/cr 123`)는 도입하지 않는다 — 빌트인 `/review` 가 커버.

### 내부 흐름
1. **base 결정**: 인자 없으면 `main`. `git rev-parse --verify $BASE` 로 존재 확인. 실패 시 1줄 안내 후 중단.
2. **사전 진단**: `git diff --shortstat $BASE...HEAD` 로 파일·라인 수 출력. 50 파일 또는 2000 라인 초과 시 "큰 변경이라 리뷰 정확도가 떨어질 수 있다" 경고 1줄.
3. **서브에이전트 호출**: `Agent(subagent_type="code-reviewer", prompt=…)` 에 다음을 패키징
   - `BASE` / `HEAD` 브랜치명
   - `git diff --name-status $BASE...HEAD` 결과
   - 지시문: "diff 는 직접 `git diff $BASE...HEAD -- <path>` 로 가져와라. `docs/CONVENTIONS.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md` 를 컨벤션 기준으로 참조하라."
4. **결과 패스스루**: 서브에이전트 응답(마크다운)을 그대로 사용자에게 표시. 메인 세션은 추가 해석·요약을 덧붙이지 않는다.

## 6. 검증 (구현 후 수동 스모크)

1. 현재 브랜치 (`feat/guide-enrichment-bokjiro`) 에서 `/cr` 실행 → 리포트가 정의된 포맷대로 나오고, `파일:라인` 인용이 빠짐없이 들어가는지, 변경 없는 카테고리는 정말 생략되는지.
2. 1–2 파일 수정한 임시 브랜치에서도 깔끔히 동작하는지. 카테고리 점검 노트가 과하게 길어지지 않는지.
3. 변경 규모 가드: 50 파일 / 2000 라인 임계치 경고 문구가 실제로 출력되는지.
4. 컨벤션 적중성: 의도적으로 Lombok 규칙·DTO 경계 위반을 1줄 만들어서 `Major [컨벤션]` 으로 잡히는지 (테스트 후 변경 폐기).

단위 테스트 대상이 아닌, 마크다운 정의의 동작 확인이므로 수동 스모크로 충분하다.

## 7. 운영 / 유지보수

- `code-reviewer.md` 의 카테고리 1 모듈 리스트는 `backend/CLAUDE.md` 의 모듈 경계가 바뀔 때 함께 갱신한다. 두 파일이 어긋나면 리뷰가 잘못된 기준으로 잡음.
- `docs/CONVENTIONS.md` 변경 시 리뷰어 페르소나에서 컨벤션 인용 경로가 여전히 유효한지 확인.

## 8. 스코프 — 포함 / 제외

### 포함
- `.claude/agents/code-reviewer.md`
- `.claude/commands/cr.md`

### 제외 (이번 작업에서 안 함)
- 자동 트리거 (hook, git pre-push)
- PR description / GitHub 코멘트 자동 게시
- 리포트 기반 자동 코드 수정
- 멀티 에이전트 병렬 리뷰
- PR 번호 인자 (`/cr 123`)
- 신규 스킬(SKILL.md) 추가 — 슬래시 커맨드 + 서브에이전트로 충분

## 9. 의존성 / 가정

- `git` 만 있으면 동작 (gh CLI 불필요)
- 사용자가 base 브랜치를 사전에 fetch 해 두었다고 가정 — fetch 누락 시 `/cr` 가 1줄로 안내
