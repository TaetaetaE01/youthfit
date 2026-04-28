# Superpowers 작업 문서

YouthFit 의 brainstorming → spec → plan → operations 사이클로 생성되는 작업 문서를 모아둔다.

## 디렉토리 구조

```
docs/superpowers/
├── README.md           # 이 파일 — 네이밍/관리 규칙
├── specs/              # 설계 문서 (요구사항, 결정 로그, 비범위)
├── plans/              # 구현 플랜 (Task 단위, TDD 흐름, 검증 명령)
├── operations/         # 운영 런북 (배포, 모니터링, 트러블슈팅)
└── *-next-steps.md     # 세션 핸드오프 메모 (선택)
```

## 파일 네이밍 규칙

### 기본 형식

```
YYYY-MM-DD-<slug>[-design].md       # specs
YYYY-MM-DD-<slug>.md                # plans
YYYY-MM-DD-<slug>-runbook.md        # operations (런북)
YYYY-MM-DD-next-steps.md            # 핸드오프 메모
```

- `YYYY-MM-DD`: 문서를 **처음 작성한 날짜** (이후 수정해도 그대로 둔다 — 시점 추적 용)
- `<slug>`: 케밥 케이스 영문 슬러그. spec/plan 한 쌍은 같은 슬러그를 공유한다.
- spec 은 슬러그 끝에 `-design`, 런북은 `-runbook` 접미사를 붙여 구분.

### 상태 prefix

사이클이 종결되면 spec/plan 파일명 앞에 상태 prefix 를 붙여 적용 여부를 한눈에 보이도록 한다 (spec ↔ plan 한 쌍은 동일 prefix 공유).

```
DONE_<원래파일명>     # 관련 PR 머지 완료 (해당 사이클의 본체 작업 끝)
TODO_<원래파일명>     # 미진행 (specs/next/ 의 backlog 등)
```

- prefix 는 본문 내용·참조 경로에도 함께 반영한다 (`grep` 후 일괄 갱신).
- 부분 적용·후속 미결 항목이 남아도 본체 작업이 PR 로 머지됐다면 `DONE_` 로 분류한다 (후속은 plan 의 "후속/미결" 섹션 또는 `next-steps.md` 에 기록).
- 운영 메모(`operations/`)는 활성/비활성 의미가 따로라 prefix 를 붙이지 않는다.

### 같은 날짜에 여러 작업이 시작될 때 — 넘버링 규칙

같은 날짜에 둘 이상의 spec/plan 사이클을 시작하는 경우, **시작 순서대로 2자리 시퀀스 번호**를 날짜 뒤에 붙인다.

```
YYYY-MM-DD-NN-<slug>[-design].md
```

- `NN`: `01` 부터 시작하는 시퀀스. 그 날 만들어진 spec/plan 파일들 사이의 작성 순서.
- spec ↔ plan ↔ runbook 은 **같은 `NN` 을 공유**한다 (한 사이클로 묶이도록).
- `next-steps.md` 는 시퀀스를 받지 않는다 (날짜당 1개만 존재).

#### 적용 시점

- **하루에 spec/plan 이 1개뿐**이면 시퀀스 생략 (현행 형식 유지). 가독성 우선.
- **2개째가 추가되는 순간** 첫 번째 파일에도 `01` 을 소급 적용해 일관성을 맞춘다.
- **기존 파일이 시퀀스 없이 N개 있는 상태에서 새 파일을 추가**하는 경우, 기존 파일은 history 보존을 위해 그대로 두고 새 파일에 `N+1` 을 부여한다. (예: 기존 4개가 NN 없는 상태 → 새 파일은 `05` 부터 시작)
- 운영상 부담이 크면 시퀀스 없이 두고 README 에 "오늘은 두 개 있음" 메모로 대체 가능 (단, Git 머지 충돌 우려 시 시퀀스 권장).

#### 예시

```
2026-04-28-01-easy-policy-interpretation-design.md
2026-04-28-01-easy-policy-interpretation.md
2026-04-28-02-policy-source-badge-design.md
2026-04-28-02-policy-source-badge.md
2026-04-28-03-attachment-extraction-pipeline-design.md
2026-04-28-03-attachment-extraction-pipeline.md
2026-04-28-03-attachment-extraction-runbook.md
```

> **소급 적용 여부**: 기존 파일들은 그대로 두고 (history 보존), 새로 만드는 파일부터 규칙을 따른다.

## 한 사이클의 표준 흐름

1. **brainstorming** — 의도/요구/결정 포인트 정리 (대화로만, 문서 X)
2. **spec** (`specs/YYYY-MM-DD-NN-<slug>-design.md`) — 결정 로그, 도메인 변경, 비범위, 위험 명시
3. **plan** (`plans/YYYY-MM-DD-NN-<slug>.md`) — Task 단위 구현 절차, 테스트 전략, 검증 커맨드
4. **구현** — plan 의 Task 를 따라 PR 단위로 분할 (가능하면 PR 분할 제안 섹션 참고)
5. **operations** (`operations/YYYY-MM-DD-NN-<slug>-runbook.md`) — 운영 항목 있을 때만 (환경변수, 모니터링, 트러블슈팅)

## next-steps 메모

세션 핸드오프 용도. 작성 시점에서 곧 처리할 후속 작업과 환경 메모를 남긴다.

- 형식: `YYYY-MM-DD-next-steps.md`
- 시퀀스 번호 없음 (날짜당 1개)
- 후속 작업이 사이클로 발전하면 spec → plan 으로 옮긴 뒤 메모 항목 제거
