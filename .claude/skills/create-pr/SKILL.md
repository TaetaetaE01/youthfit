---
name: create-pr
description: >
  YouthFit 프로젝트의 PR(Pull Request)을 Conventional Commits 컨벤션에 맞게 생성한다.
  "PR 만들어줘", "PR 작성해줘", "풀리퀘 생성해줘" 라고 하면 이 스킬을 사용한다.
---

# PR 생성 스킬

## 영역 태그 규칙 (FE / BE / META)

**PR 제목과 커밋 메시지 모두** 작업 영역을 구분해 태그를 단다.

### 판별 기준
- 변경 파일이 `frontend/` 하위에만 있으면 → **FE**
- 변경 파일이 `backend/` 하위에만 있으면 → **BE**
- 둘 다 걸쳐 있으면 → **FE/BE** (가능하면 PR을 분리)
- 변경 파일이 `.claude/` 하위(스킬, 에이전트, 훅, 설정 등 Claude Code 하네스) 또는 개발 툴링 메타 영역이면 → **META**
- 그 외(`docs/`, 루트 설정 파일 등)는 태그 생략 또는 주된 영향 영역으로 표기

`.claude/` 와 제품 코드(`frontend/`·`backend/`)가 같이 바뀌었다면, 가능하면 META 변경만 별도 PR로 분리한다. 하네스 변경은 리뷰 포인트가 완전히 다르기 때문이다.

### PR 제목 포맷
```
[FE] <type>: <설명>
[BE] <type>: <설명>
[FE/BE] <type>: <설명>
[META] <type>: <설명>

예시:
[FE] feat: 랜딩 페이지 네비게이션 연결
[BE] fix: 정책 목록 null 날짜 처리
[FE/BE] feat: 카카오 로그인 플로우 구현
[META] chore: create-pr 스킬에 스펙 DONE_ 처리 단계 추가
```

### 커밋 메시지 포맷
```
<type>(fe): <설명>
<type>(be): <설명>
<type>(fe,be): <설명>
<type>(meta): <설명>

예시:
feat(fe): 로그인 유도 모달 추가
fix(be): 적합도 판정 NPE 수정
refactor(fe,be): 프로필 DTO 필드명 통일
chore(meta): pr 스킬에 스펙 DONE_ 처리 단계 추가
```

## 브랜치 네이밍 규칙

작업 전 현재 브랜치를 확인하고, 브랜치명이 아래 규칙을 따르는지 검증한다.

```
<type>/<영역>-<패키지명>-<짧은-설명>

예시:
feat/be-ingestion-raw-endpoint
fix/be-rag-embedding-null-check
feat/fe-landing-hero-section
refactor/fe-mypage-bookmark-list
chore/docker-compose-redis
chore/meta-create-pr-skill-enhancements
```

## 커밋 타입 기준

| 타입 | 사용 시점 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드·설정·의존성 변경 |
| `docs` | 문서 변경 |

## PR 작성 절차

0. **`/cr` 셀프 리뷰 선행**

   PR 본문을 작성하기 전에 `/cr` 슬래시 커맨드를 호출해 현재 브랜치를 base(보통 `main`) 와 비교한 셀프 리뷰 리포트를 받는다. base 결정은 `/cr` 자체가 수행하므로 별도 인자 없이 호출해도 된다.

   리포트의 **Verdict** 에 따라 분기:

   - 🔴 **Critical (>0)** — PR 생성을 **중단**한다. 리포트를 그대로 사용자에게 보여주고, 다음 문장을 그대로 출력한다:

     > Critical 이슈가 있어 PR 생성을 중단했습니다. 수정 후 다시 PR 생성을 요청해 주세요.

     이후 단계로 진행하지 않는다.

   - 🟡 **Major (>0, Critical=0)** — 리포트를 그대로 보여주고 다음 문장을 그대로 출력한 뒤 명시적 확인을 받는다:

     > Major 이슈가 N건 있습니다. 그대로 PR 을 생성할까요?

     사용자가 "예 / yes / 진행" 류로 답하면 1단계로 진행. "아니오 / 수정 / no" 류면 중단.

   - 🟢 **Critical=0, Major=0** — 리포트를 보여주고 별도 확인 없이 곧바로 1단계로 진행한다.

   리포트는 **콘솔에만** 출력한다. PR 본문에는 포함하지 않는다.

   `/cr` 호출이 실패하거나 비교할 diff 가 없는 경우, 사용자에게 이유를 1줄 알리고 1단계로 진행한다.

1. `git diff main...HEAD` 로 변경 파일 목록 확인
2. `git log main...HEAD --oneline` 으로 커밋 내역 확인
3. **스펙 문서 완료 처리** (아래 "스펙 문서 완료 처리" 섹션 참조)
4. 변경 경로 기준으로 **영역 태그 판별** (`frontend/` → FE, `backend/` → BE, 혼합 → FE/BE)
5. 변경된 패키지 파악
   - BE: `ingestion` / `policy` / `rag` / `guide` / `eligibility` / `qna` / `auth` / `user` / `common`
   - FE: `pages` / `components` / `api` / `hooks` / `stores` 등
6. 제목 앞에 `[FE]` / `[BE]` / `[FE/BE]` 태그를 붙여 PR 생성
7. 아래 PR 템플릿에 맞게 본문 작성

## 스펙 문서 완료 처리

이번 PR이 `docs/superpowers/specs/` 의 어떤 스펙 작업을 마무리하는 것이라면, 해당 파일명에 `DONE_` 접두사를 붙여 PR 변경분에 포함시킨다. 이 규칙은 "어떤 스펙이 살아있고 어떤 게 끝났는지"를 파일명만으로 한눈에 알 수 있게 하기 위함이다.

### 동작 절차

1. `ls docs/superpowers/specs/` 로 현재 스펙 목록 확인
2. 다음 신호를 종합해 이번 PR과 관련된 스펙 파일 후보를 추린다
   - **브랜치명**: 예) `feat/be-youth-center-enrichment` → `*youth-center-enrichment*` 매칭
   - **커밋 메시지 키워드**: 커밋 본문에 등장하는 도메인/기능명
   - **변경 파일 경로**: 어떤 패키지/도메인이 바뀌었는지
3. 후보 스펙이 **이미 `DONE_` 접두사가 있다면 스킵**한다 (이미 완료 처리됨)
4. 후보 스펙이 **`DONE_` 접두사가 없으면** `git mv` 로 이름을 변경한다
   ```bash
   git mv docs/superpowers/specs/<original>.md docs/superpowers/specs/DONE_<original>.md
   ```
5. 변경된 파일을 PR 브랜치에 커밋한다
   ```bash
   git commit -m "docs(spec): mark <스펙명> as done"
   ```
6. 후보가 **여러 개거나 모호하면 사용자에게 어떤 스펙을 마감할지 확인**한 뒤 진행한다
7. **관련 스펙이 없다고 판단되면 이 단계는 건너뛴다** (단순 버그 픽스, 설정 변경 등은 스펙 없이 진행되는 경우가 많음)

### 판단 기준 보조

- 파일명은 `YYYY-MM-DD-<주제>-design.md` 형식이다. 주제 키워드가 브랜치/커밋과 겹치면 강한 매칭 신호다.
- `v1-*.md` 처럼 날짜가 없는 스펙은 보통 장기 트랙이라 단일 PR로 완료되지 않을 가능성이 크다. 사용자에게 확인 없이 함부로 `DONE_` 을 붙이지 않는다.
- PR이 스펙의 일부만 다루는 incremental 작업이라면 `DONE_` 을 붙이지 않는다. "스펙 전체 범위가 이번 PR로 충족되었는가" 가 기준이다.

## PR 템플릿

```markdown
## ✔️ 작업 목적
## ✔️ 아키텍처 및 설계 결정 (Trade-off)

## ✔️ 핵심 변경 사항
- `클래스명`: [어떤 역할을 하도록 추가/수정됨]
- `클래스명`: [어떤 역할을 하도록 추가/수정됨]

## ✔️ 리뷰 포인트
- [ex: PDF 파싱 정규식이 완벽하지 않을 수 있으니 `PdfExtractService`를 확인해 주세요.]

## ✔️ YouthFit 가드레일 자가 점검
- [ ] 레이어 격리: Domain 패키지에 Spring/JPA/OpenAI SDK 등 외부 의존성 침투 없음
- [ ] 단방향 의존: Controller → Service → Repository 흐름 엄수 (순환 참조 없음)
- [ ] 비용 방어: LLM·임베딩 호출 전 `source_hash` 멱등성 검증 로직 포함
- [ ] 보안: `.env` 값이나 API Key가 코드에 하드코딩되지 않음

## ✔️ 테스트 전략

## ✔️ 스크린샷 (선택)
```


## 주의 사항

- `.env`, API 키, DB 비밀번호가 커밋에 포함되지 않았는지 반드시 확인
- 한 PR은 하나의 패키지 또는 하나의 기능 슬라이스만 포함
- ARCHITECTURE.md 변경이 필요한 경우 해당 PR에 같이 포함
