# Docs 구조 리팩토링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docs/` 폴더와 3 개 `CLAUDE.md` 의 미연결·중복·drift 를 정리한다. 컨벤션을 `.claude/rules/` 로 분해하고, 백/프 전용 docs 를 모듈 디렉토리로 이동.

**Architecture:** 5 개 PR 로 자른 incremental refactor. 본 plan 은 PR1~4 만 다룬다. PR5 (PRD 통합본 → 분할판 backport) 는 1030 줄 수동 매핑 작업이라 별도 plan 으로 분리.

**Tech Stack:** 마크다운 파일 · `git mv` · shell 검증. 코드 동작 변경 없음.

**Spec:** `docs/superpowers/specs/2026-05-26-docs-restructure-design.md`

**Worktree:** `worktree-docs-restructure` (이미 진입 상태)

**Out of scope (별도 plan):** PR5 — `docs/PRD.md` 의 5월 변경분을 `docs/prd/0X-*.md` 에 backport. 작업량과 위험이 다른 PR 과 달라 분리.

---

## File Structure

**신설 (PR1):**
- `.claude/rules/common.md`
- `.claude/rules/backend/architecture.md`
- `.claude/rules/backend/naming.md`
- `.claude/rules/backend/dto.md`
- `.claude/rules/backend/swagger.md`
- `.claude/rules/backend/lombok.md`
- `.claude/rules/frontend/directory.md`
- `.claude/rules/frontend/state-management.md`
- `.claude/rules/frontend/styling.md`

**이동 (PR2 + PR3):**
- `docs/DESIGN.md` → `frontend/docs/DESIGN.md`
- `docs/ENTITIES.md` → `backend/docs/ENTITIES.md`
- `docs/INGESTION_PIPELINE.md` → `backend/docs/INGESTION_PIPELINE.md`
- `docs/CONTENT_GENERATION_FLOW.md` → `backend/docs/CONTENT_GENERATION_FLOW.md`
- `docs/superpowers/operations/*.md` (4 개) → `docs/runbooks/*.md`

**수정 (PR4):**
- `CLAUDE.md` (루트) — 슬림화
- `backend/CLAUDE.md` — 슬림화 + `@.claude/rules/backend/*` 참조
- `frontend/CLAUDE.md` — 슬림화 + `@.claude/rules/frontend/*` 참조
- `docs/superpowers/README.md` — operations 위치 변경 반영 (PR3 에서 진행)

**삭제 (PR4):**
- `docs/CONVENTIONS.md`
- `docs/superpowers/operations/` 빈 폴더 (PR3 에서)

---

## PR 의존 관계

```
PR1 (rules 신설)  ─────┐
                       ├──→ PR4 (CLAUDE.md 슬림화 + CONVENTIONS.md 삭제)
PR2 (백/프 docs 이동) ──┘
PR3 (runbooks 추출)   ── 독립
```

PR1·2·3 는 병렬 머지 가능. PR4 는 PR1·2 머지 후. 본 plan 은 직렬로 실행 (worktree 안에서 작업 → PR 별 별도 브랜치는 의미 없음. 단일 브랜치 `worktree-docs-restructure` 위에 PR 단위 커밋만 분리).

---

# PR1: `.claude/rules/` 신설

목적: 9 개 rules 파일 신설. 기존 `CLAUDE.md`/`CONVENTIONS.md` 는 손대지 않음 (이 PR 머지 후 의도적 중복 상태 — PR4 에서 해소).

검증 전략: 파일 존재 + `wc -l` 합리적 범위. 동작 검증은 PR4 머지 후 새 세션에서.

## Task 1.1: `.claude/rules/common.md` 작성

**Files:**
- Create: `.claude/rules/common.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 공통 규칙

> YouthFit 전사 공통 규칙. 백/프/공통 모든 작업에 적용. 루트 `CLAUDE.md` 가 자동 로드.

## 작업 방식
- 작고 되돌리기 쉬운 변경을 선호한다.
- 한 번에 하나의 기능 슬라이스 또는 하나의 모듈 경계만 수정한다.
- 여러 모듈에 걸치는 변경이면 먼저 아키텍처 문서를 갱신한다.
- 요구사항이 불명확하면 가정을 명시한다.
- 각 작업(태스크)이 완료되면 반드시 빌드/타입체크 확인 후 커밋한다.

## Conventional Commits
커밋 메시지는 다음 prefix 를 따른다:
- `feat:` 새 기능 추가
- `fix:` 버그 수정
- `refactor:` 동작 변경 없는 구조 개선
- `chore:` 빌드·설정·도구
- `docs:` 문서 변경
- `test:` 테스트 추가·수정

## 연동 규칙
- n8n 은 여러 도메인 엔드포인트를 직접 호출하지 말고, 작은 내부 수신 표면만 통해 백엔드와 통신한다.
- LLM 및 임베딩 호출에는 변경 감지, 캐시, 비용 방어 장치를 둔다.
- 비로그인 사용자 핫패스에서 비싼 LLM 생성을 직접 유발하지 않는다.
- 비밀값은 절대 커밋하지 않는다.

## 크롤링 및 소스 처리
- 가능한 범위에서 `robots.txt` 및 출처 정책을 준수한다.
- 식별 가능한 User-Agent 를 사용한다.
- Rate limit 을 적용하고 보수적으로 수집한다.
- 요약이나 인용으로 충분한 경우 원문 전체를 그대로 노출하지 않는다.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/common.md`
Expected: 파일 존재.

## Task 1.2: `.claude/rules/backend/architecture.md` 작성

**Files:**
- Create: `.claude/rules/backend/architecture.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 백엔드 아키텍처 규칙

> 백엔드 코드 수정 전 반드시 확인. `backend/CLAUDE.md` 가 자동 로드.

## DDD + Clean Architecture
- 의존 방향은 반드시 **Presentation → Application → Domain** 을 유지한다.
- Infrastructure 는 포트를 구현할 수 있지만, 의존 방향을 역전시키면 안 된다.
- 트랜잭션 경계는 오직 **Application Service** 에만 둔다.

## 레이어 간 침투 금지
- Controller 응답에 Entity 를 직접 노출하지 않는다.
- `presentation` DTO 를 `application` 또는 `domain` 에서 import 하지 않는다.
- `domain` 레이어에 Spring, JPA, OpenAI SDK 등 프레임워크 의존을 넣지 않는다.
- Controller 가 Repository 에 직접 접근하지 않는다.
- 비즈니스 규칙을 표현하는 동작은 가능하면 도메인 모델 안에 둔다.

## 코드 리뷰 체크 기준
코드 마무리 전에 확인한다:
- 이름이 충분히 명시적인가?
- 도메인 규칙이 Controller 나 Infrastructure 로 새어 나가지 않았는가?
- 프레임워크 타입이 Domain 으로 침투하지 않았는가?
- Entity 가 API 응답으로 직접 노출되지 않았는가?
- 요구사항이 바뀌어도 되돌리기 쉬운 변경인가?
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/backend/architecture.md`
Expected: 파일 존재.

## Task 1.3: `.claude/rules/backend/naming.md` 작성

**Files:**
- Create: `.claude/rules/backend/naming.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 백엔드 네이밍 규칙

> 비즈니스 의도가 드러나는 이름을 사용한다.

## Service 메서드 동사
명확한 동사를 선호한다:
- `find...` — 조회
- `register...` / `create...` — 생성
- `change...` / `update...` — 상태/값 변경
- `cancel...` / `delete...` — 취소/삭제
- `judge...` / `evaluate...` — 적합도 또는 규칙 평가
- `search...` / `retrieve...` — 검색
- `generate...` — LLM 또는 파생 콘텐츠 생성
- `send...` — 외부 알림 발송

아래처럼 모호한 이름은 피한다: `get()`, `save()`, `check()`, `list()`.

## Swagger Api 인터페이스 네이밍
- 새 Controller 를 추가할 때 반드시 같은 패키지에 `{도메인}Api` 인터페이스를 먼저 만든다.
- Controller 는 그 인터페이스를 `implements` 한다.
- 예: `PolicyApi` 인터페이스 → `PolicyController implements PolicyApi`.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/backend/naming.md`
Expected: 파일 존재.

## Task 1.4: `.claude/rules/backend/dto.md` 작성

**Files:**
- Create: `.claude/rules/backend/dto.md`

- [ ] **Step 1: 파일 작성**

```markdown
# DTO 규칙

> DTO 는 반드시 Java `record` 로 생성한다. 클래스 기반 DTO 를 사용하지 않는다.

## Presentation Layer
- Request DTO 이름은 `Request` 로 끝난다.
- Response DTO 이름은 `Response` 로 끝난다.

## Application Layer
- 입력 DTO 이름은 `Command` 로 끝난다.
- 출력 DTO 이름은 `Result` 로 끝난다.

## 변환 책임
- Request DTO 는 Command 로 변환한다.
- Response DTO 는 Result 로부터 생성한다.

## 예시
```java
public record CreatePolicyRequest(String title, ...) {}
public record CreatePolicyCommand(String title, ...) {}
public record PolicyResult(Long id, String title, ...) {}
public record PolicyResponse(Long id, String title, ...) {}
```
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/backend/dto.md`
Expected: 파일 존재.

## Task 1.5: `.claude/rules/backend/swagger.md` 작성

**Files:**
- Create: `.claude/rules/backend/swagger.md`

- [ ] **Step 1: 파일 작성**

```markdown
# Swagger (OpenAPI) 규칙

> Swagger 어노테이션은 Controller 가 아닌 `{도메인}Api` 인터페이스에 작성한다.

## 인터페이스 작성
- 새 Controller 를 추가할 때 반드시 같은 패키지에 `{도메인}Api` 인터페이스를 먼저 만들고, Controller 가 이를 `implements` 한다.
- 인터페이스에 `@Tag` 를 클래스 레벨에, `@Operation` 을 각 메서드에 붙인다.
  - `@Tag(name = "한글 그룹명", description = "한 줄 설명")`
  - `@Operation(summary = "동작 요약", description = "상세 설명")`
- PathVariable, 필수 RequestParam 에는 인터페이스 메서드 파라미터에 `@Parameter(description = "...")` 를 붙인다.
- Request/Response DTO record 필드에는 `@Schema(description = "...")` 를 필요에 따라 붙인다.
- 인증이 불필요한 엔드포인트에는 `@SecurityRequirements` (빈 값) 를 붙여 Swagger UI 에서 자물쇠를 제거한다.

## Controller 측 규칙
- Controller 에는 Swagger 어노테이션(`@Tag`, `@Operation`, `@Parameter`, `@ApiResponses`) 을 두지 않는다.
- Controller 에는 Spring MVC 어노테이션(`@GetMapping`, `@RequestParam` 등) 만 둔다.

## 에러 응답 명세
각 메서드에 `@ApiResponses` 로 에러 응답을 명세한다. 해당 엔드포인트가 실제로 발생시킬 수 있는 `ErrorCode` 에 맞춰 작성한다.

- `@ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)")`
- `@ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)")`
- `@ApiResponse(responseCode = "403", description = "접근 권한이 없습니다 (YF-003)")`
- `@ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")`
- `@ApiResponse(responseCode = "409", description = "이미 존재하는 리소스입니다 (YF-005)")`
- `@ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")`

에러 응답 description 에는 `ErrorCode` 의 코드(YF-xxx) 를 괄호로 함께 표기한다.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/backend/swagger.md`
Expected: 파일 존재.

## Task 1.6: `.claude/rules/backend/lombok.md` 작성

**Files:**
- Create: `.claude/rules/backend/lombok.md`

- [ ] **Step 1: 파일 작성**

```markdown
# Lombok · Entity · 예외 · Controller 규칙

## Lombok 허용
- `@Getter`
- `@Builder`
- `@RequiredArgsConstructor`
- 필요한 경우 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

## Lombok 지양
- `@Data`
- `@Setter`
- Domain Entity 에 대한 public all-args constructor

## Entity 및 도메인 모델
- 비즈니스 상태 변경을 위한 public setter 를 두지 않는다.
- 상태 변경은 의미 있는 도메인 메서드로 표현한다.
- 반복되는 비즈니스 규칙은 실제로 aggregate 에 속하는 경우 도메인 모델로 이동한다.
- Entity 를 단순 데이터 컨테이너로만 사용하지 않는다.

## 예외 처리
- 도메인 전용 커스텀 예외를 사용한다.
- 전역 예외 핸들러에서 일관되게 매핑한다.
- 안정적인 API 에러 구조를 반환한다.
- persistence 또는 프레임워크 내부 오류 상세를 그대로 클라이언트에 노출하지 않는다.

## Controller 규칙
- Controller 는 HTTP 관심사만 처리한다.
- 검증은 요청 경계에서 수행한다.
- Controller 안에 비즈니스 규칙을 넣지 않는다.
- Controller 가 Repository 에 직접 접근하지 않는다.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/backend/lombok.md`
Expected: 파일 존재.

## Task 1.7: `.claude/rules/frontend/directory.md` 작성

**Files:**
- Create: `.claude/rules/frontend/directory.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 프론트엔드 디렉토리·컴포넌트 규칙

## `src/` 디렉토리 구조
```
src/
├── apis/           # API 함수 (도메인별 파일)
├── hooks/
│   ├── queries/    # useQuery 래퍼
│   └── mutations/  # useMutation 래퍼
├── stores/         # Zustand 스토어
├── pages/          # 라우트 1:1 매핑 페이지
├── components/
│   ├── layout/     # AppLayout, Header, BottomNav
│   ├── ui/         # shadcn/ui 원자 컴포넌트
│   └── {domain}/   # 도메인별 컴포넌트 그룹
├── types/          # TypeScript 타입 (도메인별 파일)
└── lib/            # 유틸리티 (cn, constants, format, token)
```

## 컴포넌트 규칙
- 파일명은 PascalCase (`PolicyCard.tsx`)
- 도메인별로 `components/{domain}/` 아래에 그룹핑
- 페이지 컴포넌트는 `pages/` 아래에 `{Name}Page.tsx` 형식
- shadcn/ui 컴포넌트는 `components/ui/` 에 생성 (CLI 로 자동 생성)

## API 연동 패턴
1. `apis/{domain}.api.ts` 에 API 함수 정의
2. 조회는 `hooks/queries/use{Name}.ts` 에 useQuery 래퍼
3. 변경은 `hooks/mutations/use{Name}.ts` 에 useMutation 래퍼
4. 컴포넌트에서 훅을 직접 사용
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/frontend/directory.md`
Expected: 파일 존재.

## Task 1.8: `.claude/rules/frontend/state-management.md` 작성

**Files:**
- Create: `.claude/rules/frontend/state-management.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 프론트엔드 상태 관리 규칙

상태 유형별 도구를 일관되게 사용한다.

| 상태 유형 | 도구 | 예시 |
|-----------|------|------|
| 서버 데이터 | TanStack Query | 정책, 프로필, 북마크 |
| 인증 토큰 | Zustand + localStorage | accessToken, isAuthenticated |
| 글로벌 UI | Zustand | 모바일 메뉴, 필터 시트 |
| 로컬 UI | React useState | 입력값, 토글 |
| URL 상태 | React Router searchParams | 필터, 검색어, 페이지 |

## 결정 가이드
- 백엔드에서 오는 데이터 → **TanStack Query**. 캐시·자동 재요청·에러 핸들링.
- 토큰처럼 영속화가 필요한 인증 상태 → **Zustand + localStorage middleware**.
- 다른 페이지에서 공유하는 UI 상태 → **Zustand**.
- 한 컴포넌트 안에서만 쓰는 입력값·토글 → **useState**.
- URL 로 공유·복원돼야 하는 상태 (필터, 검색어, 페이지 번호) → **React Router searchParams**.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/frontend/state-management.md`
Expected: 파일 존재.

## Task 1.9: `.claude/rules/frontend/styling.md` 작성

**Files:**
- Create: `.claude/rules/frontend/styling.md`

- [ ] **Step 1: 파일 작성**

```markdown
# 프론트엔드 스타일 규칙

## Tailwind 우선
- Tailwind CSS 유틸리티 클래스 우선 사용
- `cn()` 유틸 (clsx + tailwind-merge) 로 조건부 클래스 조합

## 색상 토큰
- 브랜드 Blue-500 (`#3B82F6`)
- 적합도 Green / Amber / Red (`@frontend/docs/DESIGN.md` 참조)

## 반응형
- 모바일 우선 (`md:` 브레이크포인트 기준으로 데스크톱 추가)
- 터치 타겟 최소 44 × 44 px

## 디자인 토큰
컬러·타이포·간격의 단일 진실 소스는 `@frontend/docs/DESIGN.md`. 새 토큰을 추가할 때 그 파일에 먼저 반영한 뒤 컴포넌트에 적용.
```

- [ ] **Step 2: 검증**

Run: `ls -la .claude/rules/frontend/styling.md`
Expected: 파일 존재.

## Task 1.10: PR1 통합 검증 + 커밋 + PR 생성

**Files:**
- 위 9 개 모두 stage

- [ ] **Step 1: 9 개 파일 존재 확인**

Run:
```bash
find .claude/rules -type f -name "*.md" | sort
```
Expected (9 줄):
```
.claude/rules/backend/architecture.md
.claude/rules/backend/dto.md
.claude/rules/backend/lombok.md
.claude/rules/backend/naming.md
.claude/rules/backend/swagger.md
.claude/rules/common.md
.claude/rules/frontend/directory.md
.claude/rules/frontend/state-management.md
.claude/rules/frontend/styling.md
```

- [ ] **Step 2: 기존 CLAUDE.md/CONVENTIONS.md 변경 없음 확인**

Run: `git status --short | grep -E "CLAUDE\.md|CONVENTIONS\.md" || echo "OK — no changes to existing rule files"`
Expected: `OK — no changes to existing rule files`

- [ ] **Step 3: 커밋**

Run:
```bash
git add .claude/rules/
git commit -m "$(cat <<'EOF'
chore(rules): introduce .claude/rules/ split

9 개 rules 파일 신설 (common + backend/5 + frontend/3).
spec docs/superpowers/specs/2026-05-26-docs-restructure-design.md
에 정의된 분해 매핑에 따라 작성. 기존 CLAUDE.md 와
CONVENTIONS.md 는 PR4 에서 슬림화하기 전까지 의도적으로
중복 상태로 둔다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: PR 생성 (사용자 확인 후)**

Run:
```bash
git push -u origin worktree-docs-restructure
gh pr create --title "chore(rules): introduce .claude/rules/ split" --body "$(cat <<'EOF'
## Summary
- `.claude/rules/` 신설. 컨벤션의 단일 진실 소스 후보.
- 9 개 파일: `common.md` + `backend/{architecture, naming, dto, swagger, lombok}.md` + `frontend/{directory, state-management, styling}.md`.
- 본 PR 단계에선 기존 `CLAUDE.md` 들과 `docs/CONVENTIONS.md` 는 손대지 않음 (PR4 에서 슬림화).

## 관련 문서
- spec: `docs/superpowers/specs/2026-05-26-docs-restructure-design.md`
- plan: `docs/superpowers/plans/2026-05-26-docs-restructure.md`

## Test plan
- [ ] 9 개 파일 존재 확인 (`find .claude/rules -type f -name "*.md"` 9 줄)
- [ ] 기존 `CLAUDE.md`·`CONVENTIONS.md` 무변경 확인
- [ ] 머지 후 새 Claude Code 세션에서 `.claude/rules/` 자동 로드 동작 확인 (PR4 진행 전 사전 검증)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

> **주의:** `gh pr create` 실행 전 사용자 승인 필요 (PR 은 외부 가시 액션).

---

# PR2: 백/프 전용 docs 를 모듈 디렉토리로 이동

목적: `docs/` 의 백/프 전용 문서 4 개를 모듈 디렉토리 (`backend/docs/`, `frontend/docs/`) 로 이동. 외부 참조 (옛 경로 링크) 가 있으면 같이 업데이트.

## Task 2.1: `frontend/docs/` 생성 + DESIGN.md 이동

**Files:**
- Move: `docs/DESIGN.md` → `frontend/docs/DESIGN.md`

- [ ] **Step 1: 디렉토리 생성 후 이동**

Run:
```bash
mkdir -p frontend/docs
git mv docs/DESIGN.md frontend/docs/DESIGN.md
```

- [ ] **Step 2: 검증**

Run: `ls -la frontend/docs/DESIGN.md && [ ! -e docs/DESIGN.md ] && echo "OK"`
Expected: 새 위치에 존재, 옛 위치는 없음.

## Task 2.2: `backend/docs/` 생성 + 3 개 파일 이동

**Files:**
- Move: `docs/ENTITIES.md` → `backend/docs/ENTITIES.md`
- Move: `docs/INGESTION_PIPELINE.md` → `backend/docs/INGESTION_PIPELINE.md`
- Move: `docs/CONTENT_GENERATION_FLOW.md` → `backend/docs/CONTENT_GENERATION_FLOW.md`

> **참고:** `INGESTION_PIPELINE.md` 와 `CONTENT_GENERATION_FLOW.md` 는 본 워크트리 시작 시점에서 untracked 였다. `git mv` 가 untracked 파일에는 동작하지 않으니 일반 `mv` 후 `git add` 가 필요할 수 있음 — `git status` 로 상태 확인 후 분기.

- [ ] **Step 1: 디렉토리 생성**

Run: `mkdir -p backend/docs`

- [ ] **Step 2: 각 파일 상태 확인 후 이동**

Run:
```bash
for f in ENTITIES.md INGESTION_PIPELINE.md CONTENT_GENERATION_FLOW.md; do
  if git ls-files --error-unmatch "docs/$f" >/dev/null 2>&1; then
    git mv "docs/$f" "backend/docs/$f"
  else
    mv "docs/$f" "backend/docs/$f"
  fi
done
```

- [ ] **Step 3: 검증**

Run:
```bash
ls -la backend/docs/
[ ! -e docs/ENTITIES.md ] && [ ! -e docs/INGESTION_PIPELINE.md ] && [ ! -e docs/CONTENT_GENERATION_FLOW.md ] && echo "OK"
```
Expected: 3 개 파일 새 위치에 존재, 옛 위치는 없음.

## Task 2.3: ENTITIES.md 안의 자체 참조 경로 업데이트

**Files:**
- Modify: `backend/docs/ENTITIES.md`

ENTITIES.md 안에 `docs/ARCHITECTURE.md` 같은 상대 경로가 있어서 위치가 바뀌면 깨질 수 있음. 확인 후 보정.

- [ ] **Step 1: 내부 참조 검색**

Run:
```bash
grep -nE "docs/(ARCHITECTURE|PRODUCT|PRD|OPS|CONVENTIONS)" backend/docs/ENTITIES.md || echo "no internal refs"
```

- [ ] **Step 2: 발견된 참조가 있으면 보정**

`backend/docs/ENTITIES.md` 는 모듈 디렉토리 안에 있으니 루트 기준 절대 경로 형태로 적는다. 예:
- Before: `` `docs/ARCHITECTURE.md` ``
- After:  `` `docs/ARCHITECTURE.md` `` (그대로 유지 — 루트 기준 표기라 위치 무관)

> 변경이 없다면 Step 3 으로.

- [ ] **Step 3: 검증**

Run: `grep -nE "docs/(ARCHITECTURE|PRODUCT|PRD|OPS|CONVENTIONS)" backend/docs/ENTITIES.md`
Expected: 모든 매치가 루트 기준 경로 (`docs/...`) 로 표기됨.

## Task 2.4: 옛 경로 (`docs/{DESIGN,ENTITIES,INGESTION_PIPELINE,CONTENT_GENERATION_FLOW}.md`) 참조 grep

**Files:**
- (검색만)

- [ ] **Step 1: 전체 리포 grep**

Run:
```bash
grep -rnE "docs/(DESIGN|ENTITIES|INGESTION_PIPELINE|CONTENT_GENERATION_FLOW)\.md" \
  --include="*.md" \
  --exclude-dir=node_modules \
  --exclude-dir=.git \
  --exclude-dir=.claude/worktrees \
  --exclude-dir=.claude/plugins \
  . 2>/dev/null || echo "no old refs"
```
Expected: 출력 없음 (CLAUDE.md 의 옛 참조는 PR4 에서 슬림화하며 제거되므로 여기선 다루지 않는다 — 단, 옛 경로 자체가 명시적으로 적힌 곳이 있다면 발견해야 함).

- [ ] **Step 2: 발견된 경우 분기 처리**

발견되는 위치별 대응:
- 루트 `CLAUDE.md` 의 문서 맵에 있는 옛 경로 → PR4 에서 슬림화하며 새 경로로 대체. **본 PR 에선 그대로 둔다.**
- `backend/CLAUDE.md` / `frontend/CLAUDE.md` 안의 옛 경로 → 마찬가지로 PR4 에서.
- 위 3 개 CLAUDE.md 외의 곳 (`docs/PRD.md`, `docs/superpowers/specs/*.md` 본문 등) → **본 PR 에서 새 경로로 즉시 갱신.**

> 자동 일괄 갱신은 위험. 발견된 파일을 사용자에게 보고하고 1:1 검토 후 수정.

- [ ] **Step 3: 갱신이 있었다면 stage**

Run: `git diff --stat HEAD -- '*.md' | head -20`
Expected: 변경된 파일 리스트가 합리적 (CLAUDE.md 3 개 제외).

## Task 2.5: PR2 커밋 + PR 생성

- [ ] **Step 1: 전체 stage 확인**

Run: `git status --short`
Expected:
- `R  docs/DESIGN.md -> frontend/docs/DESIGN.md`
- `R  docs/ENTITIES.md -> backend/docs/ENTITIES.md`
- `A  backend/docs/INGESTION_PIPELINE.md` (untracked 였던 경우)
- `A  backend/docs/CONTENT_GENERATION_FLOW.md`
- (Task 2.4 에서 갱신된 파일들)

- [ ] **Step 2: 커밋**

Run:
```bash
git commit -m "$(cat <<'EOF'
docs: move backend/frontend-specific refs into module dirs

docs/DESIGN.md → frontend/docs/DESIGN.md
docs/ENTITIES.md → backend/docs/ENTITIES.md
docs/INGESTION_PIPELINE.md → backend/docs/INGESTION_PIPELINE.md
docs/CONTENT_GENERATION_FLOW.md → backend/docs/CONTENT_GENERATION_FLOW.md

모듈 코드와 같은 디렉토리에 두어 작업 중 발견·소유권을
명확히 한다. CLAUDE.md 의 옛 경로는 PR4 에서 슬림화하며
새 경로로 갱신한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: PR 생성 (사용자 승인 후)**

```bash
gh pr create --title "docs: move backend/frontend-specific refs into module dirs" --body "$(cat <<'EOF'
## Summary
- `docs/DESIGN.md` → `frontend/docs/`
- `docs/ENTITIES.md`, `docs/INGESTION_PIPELINE.md`, `docs/CONTENT_GENERATION_FLOW.md` → `backend/docs/`
- 옛 경로 → 새 경로 매핑은 위 4 건. CLAUDE.md 의 참조 갱신은 PR4 에서 한꺼번에.

## Test plan
- [ ] 새 위치에 4 개 파일 존재
- [ ] 옛 위치에 4 개 파일 없음
- [ ] 외부 노션·PR 본문에 옛 경로가 박혀있다면 본 PR 머지 후 갱신 필요

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

# PR3: `docs/runbooks/` 추출

목적: `docs/superpowers/operations/` 의 런북·활성 상태 메모 4 개를 `docs/runbooks/` 로 이동. `superpowers/README.md` 의 사이클 흐름 안내를 함께 갱신.

## Task 3.1: `docs/runbooks/` 생성 + 파일 이동

**Files:**
- Move: `docs/superpowers/operations/2026-04-28-attachment-extraction-runbook.md` → `docs/runbooks/`
- Move: `docs/superpowers/operations/2026-04-29-cost-guard-active.md` → `docs/runbooks/`
- Move: `docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md` → `docs/runbooks/`
- Move: `docs/superpowers/operations/2026-05-05-email-transport-runbook.md` → `docs/runbooks/`

- [ ] **Step 1: 디렉토리 생성**

Run: `mkdir -p docs/runbooks`

- [ ] **Step 2: 4 개 파일 이동**

Run:
```bash
git mv docs/superpowers/operations/2026-04-28-attachment-extraction-runbook.md docs/runbooks/
git mv docs/superpowers/operations/2026-04-29-cost-guard-active.md docs/runbooks/
git mv docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md docs/runbooks/
git mv docs/superpowers/operations/2026-05-05-email-transport-runbook.md docs/runbooks/
```

- [ ] **Step 3: 빈 폴더 삭제**

Run:
```bash
rmdir docs/superpowers/operations || ls docs/superpowers/operations
```
Expected: 빈 폴더라 `rmdir` 성공. 만약 다른 파일이 남아있으면 출력으로 알 수 있음.

- [ ] **Step 4: 검증**

Run:
```bash
ls docs/runbooks/
[ ! -d docs/superpowers/operations ] && echo "OK — operations removed"
```
Expected: 4 개 파일, operations 폴더 없음.

## Task 3.2: `docs/superpowers/README.md` 의 사이클 흐름 갱신

**Files:**
- Modify: `docs/superpowers/README.md`

기존 README 의 "한 사이클의 표준 흐름" 5 번 항목과 "디렉토리 구조" 섹션에서 `operations/` 를 `docs/runbooks/` 로 가리키도록 수정.

- [ ] **Step 1: 디렉토리 구조 섹션 수정**

`docs/superpowers/README.md` 의 디렉토리 구조 부분:

Before:
```
docs/superpowers/
├── README.md           # 이 파일 — 네이밍/관리 규칙
├── specs/              # 설계 문서 (요구사항, 결정 로그, 비범위)
├── plans/              # 구현 플랜 (Task 단위, TDD 흐름, 검증 명령)
├── operations/         # 운영 런북 (배포, 모니터링, 트러블슈팅)
└── *-next-steps.md     # 세션 핸드오프 메모 (선택)
```

After:
```
docs/superpowers/
├── README.md           # 이 파일 — 네이밍/관리 규칙
├── specs/              # 설계 문서 (요구사항, 결정 로그, 비범위)
├── plans/              # 구현 플랜 (Task 단위, TDD 흐름, 검증 명령)
└── *-next-steps.md     # 세션 핸드오프 메모 (선택)

# 운영 런북은 ../runbooks/ 에 둔다.
```

- [ ] **Step 2: 표준 흐름 5 번 항목 수정**

Before:
```
5. **operations** (`operations/YYYY-MM-DD-NN-<slug>-runbook.md`) — 운영 항목 있을 때만
```

After:
```
5. **runbook** (`../runbooks/YYYY-MM-DD-NN-<slug>-runbook.md`) — 운영 항목 있을 때만 (환경변수, 모니터링, 트러블슈팅)
```

- [ ] **Step 3: 파일 네이밍 규칙 섹션 수정**

Before:
```
YYYY-MM-DD-<slug>-runbook.md        # operations (런북)
```

After:
```
YYYY-MM-DD-<slug>-runbook.md        # runbooks/ 에 위치
```

- [ ] **Step 4: 상태 prefix 섹션의 operations 언급 갱신**

Before:
```
- 운영 메모(`operations/`)는 활성/비활성 의미가 따로라 prefix 를 붙이지 않는다.
```

After:
```
- 운영 메모(`docs/runbooks/`) 는 활성/비활성 의미가 따로라 prefix 를 붙이지 않는다.
```

- [ ] **Step 5: 검증**

Run:
```bash
grep -nE "operations/" docs/superpowers/README.md || echo "OK — no more 'operations/' references"
```
Expected: `OK — no more 'operations/' references`

## Task 3.3: PR3 커밋 + PR 생성

- [ ] **Step 1: 상태 확인**

Run: `git status --short`
Expected:
- `R  docs/superpowers/operations/2026-04-28-... -> docs/runbooks/...` × 4
- `M  docs/superpowers/README.md`

- [ ] **Step 2: 커밋**

Run:
```bash
git commit -m "$(cat <<'EOF'
docs: extract runbooks/ from superpowers/operations/

런북·활성 상태 메모 4 개를 docs/runbooks/ 로 이동.
superpowers/README.md 의 사이클 흐름에서 operations/
참조를 ../runbooks/ 로 갱신.

runbook 은 사이클 산출물보다 일반 운영 자료에 가까워
superpowers/ 와 분리한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: PR 생성 (사용자 승인 후)**

```bash
gh pr create --title "docs: extract runbooks/ from superpowers/operations/" --body "$(cat <<'EOF'
## Summary
- `docs/superpowers/operations/*.md` (4 개) → `docs/runbooks/`
- `docs/superpowers/README.md` 의 사이클 안내 갱신

## Test plan
- [ ] `docs/runbooks/` 에 4 개 파일 존재
- [ ] `docs/superpowers/operations/` 폴더 제거됨
- [ ] README 의 `operations/` 언급이 모두 `runbooks/` 로 갱신됨

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

# PR4: `CLAUDE.md` 슬림화 + `CONVENTIONS.md` 삭제

목적: 3 개 `CLAUDE.md` 를 spec 의 섹션 4 본문으로 교체. `docs/CONVENTIONS.md` 삭제. 이 PR 이 머지되어야 `.claude/rules/` 가 단일 진실 소스로 활성화됨.

**전제:** PR1·PR2 가 이미 머지됨 (rules 와 새 경로가 존재해야 참조가 동작).

## Task 4.1: 루트 `CLAUDE.md` 슬림화

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 전체 내용을 새 본문으로 교체**

`CLAUDE.md` 전체를 다음으로 교체:

```markdown
# CLAUDE.md

## 프로젝트
- **이름**: YouthFit
- **목표**: 흩어진 청년 정책 정보를 한곳에 모으고, 쉬운 설명·가벼운 적합도 판정·출처 기반 Q&A 를 통해 사용자가 자격 요건, 준비사항, 다음 행동을 이해할 수 있도록 돕는다.
- **포지셔닝**: YouthFit 은 공식 정책 포털을 대체하지 않는다. 정책을 더 쉽게 찾고 이해하도록 돕고, 최종 신청은 공식 신청 채널로 연결하는 보완형 서비스다.

## MVP (v0) 범위
**포함**: 정책 목록·상세·검색, 카카오 로그인, 프로필, 적합도 판정, RAG Q&A, 북마크, 이메일 알림
**제외**: 커뮤니티·평점, 모바일 앱·푸시, 관리자 대시보드, 하이브리드 검색, 이벤트 드리븐 아키텍처, 외부 공개 API 연동 (초기 크롤링 제외)

## 프로젝트 구조
\`\`\`
youthfit/
├── CLAUDE.md              # 공통 지침 (이 파일)
├── .claude/rules/         # 컨벤션 (공통 + backend/ + frontend/)
├── backend/               # Spring Boot — backend/CLAUDE.md, backend/docs/
├── frontend/              # React — frontend/CLAUDE.md, frontend/docs/
├── docs/                  # 공통 제품·아키텍처 문서
└── n8n/                   # 워크플로우 설정
\`\`\`

## 모듈 경계

### 백엔드 모듈
- `admin` 어드민 도구 (정책 enrichment 리뷰, RAG 미리보기, 이메일 로그, Q&A 캐시, LLM 비용, ingestion 헬스, 대시보드)
- `auth` 카카오 OAuth + JWT
- `common` 공통 유틸·횡단 관심사
- `eligibility` 규칙 기반 적합도 판정
- `guide` 구조화된 AI 가이드 콘텐츠 생성
- `ingestion` n8n·외부 수집 파이프라인 수신
- `policy` 정책 도메인·정규화·중복 제거
- `qna` 정책 Q&A·스트리밍 응답
- `rag` 임베딩·청크 분할·벡터 조회
- `user` 프로필·북마크·알림

### 프론트엔드 주요 영역
- 정책 탐색 (목록·상세·검색) / 인증 (카카오·토큰) / 사용자 (프로필·북마크·알림) / 적합도 판정 UI / Q&A 스트리밍 UI

## 컨벤션 (반드시 따른다)
@.claude/rules/common.md
백엔드 코드 수정 전: @.claude/rules/backend/
프론트엔드 코드 수정 전: @.claude/rules/frontend/

## 문서 맵
| 위치 | 내용 |
|------|------|
| `docs/PRODUCT.md` | 제품 목표·타겟·정책 해석 원칙 |
| `docs/ARCHITECTURE.md` | 모듈 경계·레이어·데이터 흐름·인프라 |
| `docs/PRD.md` | 상세 기능 요구사항 (통합본) |
| `docs/prd/` | 도메인별 PRD (분할판, 활성화 시점에 사용) |
| `docs/OPS.md` | 환경변수·배포·시크릿·운영 안전장치 |
| `docs/ops/cost-snapshot.md` | AWS prod 인프라 비용 스냅샷 |
| `docs/runbooks/` | 배포 전 절차·운영 활성 상태 메모 |
| `docs/troubleshooting/` | 백/프/인프라 디버깅 회고 |
| `docs/superpowers/` | brainstorming → spec → plan 사이클 산출물 |
| `backend/docs/ENTITIES.md` | JPA 엔티티·스키마 레퍼런스 |
| `backend/docs/INGESTION_PIPELINE.md` | n8n → DB 적재 파이프라인 |
| `backend/docs/CONTENT_GENERATION_FLOW.md` | 가이드·룰·RAG·Q&A 이벤트 흐름 |
| `frontend/docs/DESIGN.md` | 디자인 토큰 (컬러·타이포·간격) |

## 수정 전에 읽기
- 모듈 경계·의존 방향 변경 → `docs/ARCHITECTURE.md`
- 새 기능 구현 → `docs/PRD.md` (또는 활성화된 경우 `docs/prd/0X-<domain>.md`)
- 사용자 해석 방식 변경 → `docs/PRODUCT.md`
- 크롤링·배포·시크릿 → `docs/OPS.md`
- 백엔드 코드 → `backend/CLAUDE.md` + 관련 `backend/docs/*`
- 프론트엔드 코드 → `frontend/CLAUDE.md` + `frontend/docs/DESIGN.md`

## Claude Code 기능 관련 메모
- **Plan Mode** 는 큰 리팩토링이나 위험한 변경 전에 읽기 중심으로 범위를 파악하고 계획을 세울 때 유용하다.
- **Custom Subagents** 는 반복적으로 역할 분리가 필요할 때만 선택적으로 도입한다.
- 장기 유지보수 가치가 충분하지 않다면 기능별 에이전트 규칙을 여기에 추가하지 않는다.
```

- [ ] **Step 2: 검증**

Run: `wc -l CLAUDE.md && grep -c "@\.claude/rules" CLAUDE.md`
Expected: 60~70 줄, `@.claude/rules` 가 3 회 이상 등장.

## Task 4.2: `backend/CLAUDE.md` 슬림화

**Files:**
- Modify: `backend/CLAUDE.md`

- [ ] **Step 1: 전체 교체**

`backend/CLAUDE.md` 전체를 다음으로 교체:

```markdown
# Backend CLAUDE.md

> Spring Boot 백엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`, 코드 컨벤션은 `.claude/rules/backend/` 참조.

## 기술 스택
| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.5 |
| ORM | Hibernate + Spring Data JPA | - |
| Database | PostgreSQL + pgvector | 17 |
| Cache | Redis | 7 |
| Auth | Kakao OAuth2 + JWT (jjwt) | - |
| AI/LLM | OpenAI API (Embedding, Chat) | - |
| API 문서 | springdoc-openapi (Swagger UI) | 2.8.6 |
| 빌드 | Gradle | - |
| 테스트 | JUnit 5 + JaCoCo | - |

## 빌드 및 실행
\`\`\`bash
cd backend
./gradlew build && ./gradlew test
./gradlew bootRun     # 포트 8080
\`\`\`

## 백엔드 모듈 목록
- `admin` 어드민 도구 / `auth` 카카오 로그인+JWT
- `common` 공통 / `eligibility` 적합도 판정
- `guide` AI 가이드 / `ingestion` 외부 수집 수신
- `policy` 정책 도메인 / `qna` Q&A 스트리밍
- `rag` 임베딩·청크 / `user` 프로필·북마크·알림

## 모듈 내부 레이어 구조
\`\`\`
{module}/
├── presentation/   (controller, dto/request, dto/response)
├── application/    (service, dto/command, dto/result, port)
├── domain/         (model, repository, service)
└── infrastructure/ (persistence, external, config, scheduler)
\`\`\`

## 코드 규칙 (반드시 따른다)
- @.claude/rules/backend/architecture.md   # DDD, 의존방향, 트랜잭션
- @.claude/rules/backend/naming.md         # Service 메서드, 패키지
- @.claude/rules/backend/dto.md            # record + Command/Result/Request/Response
- @.claude/rules/backend/swagger.md        # Api 인터페이스, ErrorCode
- @.claude/rules/backend/lombok.md         # Lombok, 예외 처리, Controller

## 도메인 레퍼런스
- @backend/docs/ENTITIES.md                # 모듈별 엔티티·테이블 스키마
- @backend/docs/INGESTION_PIPELINE.md      # n8n → DB 흐름
- @backend/docs/CONTENT_GENERATION_FLOW.md # 가이드·룰·RAG·Q&A fan-out
```

- [ ] **Step 2: 검증**

Run: `wc -l backend/CLAUDE.md && grep -c "@\.claude/rules/backend\|@backend/docs" backend/CLAUDE.md`
Expected: 55 줄 내외, `@` 참조 8 회 이상.

## Task 4.3: `frontend/CLAUDE.md` 슬림화

**Files:**
- Modify: `frontend/CLAUDE.md`

- [ ] **Step 1: 전체 교체**

`frontend/CLAUDE.md` 전체를 다음으로 교체:

```markdown
# Frontend CLAUDE.md

> React 프론트엔드 전용 규칙. 공통 규칙은 루트 `CLAUDE.md`, 코드 컨벤션은 `.claude/rules/frontend/` 참조.

## 기술 스택
| 영역 | 기술 | 버전 |
|------|------|------|
| 프레임워크 | React + TypeScript | 19 / 5 |
| 빌드 | Vite | 6 |
| 라우팅 | React Router | v7 |
| 서버 상태 | TanStack Query | v5 |
| 클라이언트 상태 | Zustand | v5 |
| 스타일링 | Tailwind CSS + shadcn/ui | v4 |
| HTTP | ky | - |
| 폼 | React Hook Form + Zod | - |
| 폰트 | Pretendard Variable | - |
| 테스트 | Vitest + Testing Library | - |

## 빌드 및 실행
\`\`\`bash
cd frontend
npm install
npm run dev           # 포트 5173 — /api → localhost:8080 프록시
npm run build && npm run preview
npm run test
\`\`\`

## API 연동 핵심
- Vite proxy 로 `/api` → `localhost:8080`
- 모든 호출은 `apis/client.ts` 의 ky 인스턴스 경유
- 인증 토큰은 `beforeRequest` 훅에서 자동 첨부, 401 시 자동 갱신 재시도

## 코드 규칙 (반드시 따른다)
- @.claude/rules/frontend/directory.md         # apis/hooks/stores/pages 구조, 컴포넌트 규칙
- @.claude/rules/frontend/state-management.md  # TanStack vs Zustand vs URL
- @.claude/rules/frontend/styling.md           # Tailwind, cn(), 반응형, 터치 타겟

## 디자인 레퍼런스
- @frontend/docs/DESIGN.md   # 컬러·타이포·간격 토큰 (UI 작업 시 단일 진실 소스)
```

- [ ] **Step 2: 검증**

Run: `wc -l frontend/CLAUDE.md && grep -c "@\.claude/rules/frontend\|@frontend/docs" frontend/CLAUDE.md`
Expected: 45 줄 내외, `@` 참조 4 회 이상.

## Task 4.4: `docs/CONVENTIONS.md` 삭제

**Files:**
- Delete: `docs/CONVENTIONS.md`

- [ ] **Step 1: 다른 곳에서 참조하지 않는지 확인**

Run:
```bash
grep -rnE "docs/CONVENTIONS\.md" --include="*.md" \
  --exclude-dir=.claude/worktrees --exclude-dir=.claude/plugins --exclude-dir=.git \
  . 2>/dev/null
```
Expected: 본 plan / spec 파일 외의 참조 없음.

발견된 경우 (예: `docs/PRD.md` 의 헤더 참조) 새 위치 (`@.claude/rules/backend/`) 로 갱신.

- [ ] **Step 2: 삭제**

Run: `git rm docs/CONVENTIONS.md`

- [ ] **Step 3: 검증**

Run: `[ ! -e docs/CONVENTIONS.md ] && echo "OK"`
Expected: `OK`.

## Task 4.5: PR4 통합 검증

- [ ] **Step 1: 3 개 CLAUDE.md 가 `@.claude/rules` 를 참조하는지 확인**

Run:
```bash
for f in CLAUDE.md backend/CLAUDE.md frontend/CLAUDE.md; do
  count=$(grep -c "@\.claude/rules" "$f")
  echo "$f: $count refs"
done
```
Expected:
```
CLAUDE.md: 3 refs (또는 그 이상)
backend/CLAUDE.md: 5 refs
frontend/CLAUDE.md: 3 refs
```

- [ ] **Step 2: `docs/CONVENTIONS.md` 미존재**

Run: `[ ! -e docs/CONVENTIONS.md ] && echo "OK"`
Expected: `OK`.

- [ ] **Step 3: `.claude/rules/` 9 개 파일 존재 (PR1 의 결과 보존)**

Run: `find .claude/rules -type f -name "*.md" | wc -l`
Expected: `9`.

- [ ] **Step 4: 옛 `docs/{DESIGN,ENTITIES,INGESTION_PIPELINE,CONTENT_GENERATION_FLOW}.md` 미존재 (PR2 의 결과 보존)**

Run:
```bash
for f in docs/DESIGN.md docs/ENTITIES.md docs/INGESTION_PIPELINE.md docs/CONTENT_GENERATION_FLOW.md; do
  [ ! -e "$f" ] || echo "STILL EXISTS: $f"
done
echo "check done"
```
Expected: `check done` 만 출력.

- [ ] **Step 5: 새 세션 사전 검증 (사용자 개입)**

PR4 머지 전 워크트리에서 새 Claude Code 세션을 띄워 다음을 확인:
1. 세션 시작 시 `.claude/rules/` 파일들이 자동 로드되는지
2. "백엔드 DTO 규칙이 뭐야?" 같은 질문에 `.claude/rules/backend/dto.md` 의 내용을 인용하는지

문제 발견 시 본 PR 머지 보류 후 `@` 참조 문법 / 경로 점검.

## Task 4.6: PR4 커밋 + PR 생성

- [ ] **Step 1: 상태 확인**

Run: `git status --short`
Expected:
- `M  CLAUDE.md`
- `M  backend/CLAUDE.md`
- `M  frontend/CLAUDE.md`
- `D  docs/CONVENTIONS.md`

- [ ] **Step 2: 커밋**

Run:
```bash
git commit -m "$(cat <<'EOF'
docs: slim CLAUDE.md files, point to .claude/rules/

3 개 CLAUDE.md (루트·backend·frontend) 를 spec 의 슬림화
본문으로 교체. 컨벤션 본문은 .claude/rules/ 가 단일 진실
소스로 보유하고, CLAUDE.md 는 @ 참조만 남긴다.

docs/CONVENTIONS.md 는 본문이 .claude/rules/backend/ 로
완전히 이전돼 삭제. 본 PR 머지 전 새 세션에서 rules 자동
로드 동작을 사전 검증한다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: PR 생성 (사용자 승인 후)**

```bash
gh pr create --title "docs: slim CLAUDE.md files, point to .claude/rules/" --body "$(cat <<'EOF'
## Summary
- 3 개 CLAUDE.md 를 본문 → `@.claude/rules/...` 참조 위주로 슬림화
- `docs/CONVENTIONS.md` 삭제 (본문은 `.claude/rules/backend/` 로 이전됨)
- 문서 맵에 새 경로 (`backend/docs/`, `frontend/docs/`, `docs/runbooks/`) 반영

## 전제
- PR1 (`.claude/rules/` 신설), PR2 (백/프 docs 이동) 가 이미 머지돼 있어야 한다.

## Test plan
- [ ] 3 개 CLAUDE.md 가 모두 `@.claude/rules/...` 참조를 포함
- [ ] `docs/CONVENTIONS.md` 미존재
- [ ] 새 Claude Code 세션에서 `.claude/rules/` 자동 로드 동작 확인
- [ ] 백엔드 코드 작성을 요청했을 때 `@.claude/rules/backend/*.md` 의 규칙이 적용되는지 샘플 검증

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 마무리 체크리스트

본 plan 전체 완료 시:

- [ ] PR1 머지: `.claude/rules/` 9 개 파일 활성
- [ ] PR2 머지: 백/프 전용 docs 가 모듈 디렉토리에
- [ ] PR3 머지: `docs/runbooks/` 활성, superpowers README 갱신
- [ ] PR4 머지: CLAUDE.md 슬림화 + CONVENTIONS.md 삭제 — 단일 진실 소스 활성
- [ ] PR5 (별도 plan): `docs/PRD.md` → `docs/prd/0X-*.md` backport — 본 plan 범위 외

## 후속 작업 (다음 plan)

- **`docs/prd/` 분할판 backport**: `docs/PRD.md` 통합본의 5 월 변경분을 `docs/prd/0X-*.md` 에 반영. 1030 줄 수동 매핑이라 별도 사이클로 진행. spec 결정 D-4 참조.
