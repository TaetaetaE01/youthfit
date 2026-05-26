# Docs 구조 리팩토링 — Design

> **목적**: `docs/` 폴더와 3 개 `CLAUDE.md` 가 시간이 지나며 누락·중복·drift 가 누적된 상태를 정리한다. 백/프 분리 가능한 문서는 모듈로 옮기고, 컨벤션은 `.claude/rules/` 로 분해해 단일 진실 소스를 만든다.
> **상태**: spec 작성 완료, 사용자 리뷰 대기
> **브랜치**: `worktree-docs-restructure`

---

## 1. 문제 정의

현재 `docs/`, `CLAUDE.md` 들의 상태를 점검한 결과 다음 4 가지 이슈가 확인됨.

### 1-1. 미연결 문서 (실재하지만 어떤 CLAUDE.md 도 안 가리킴)

| 문서 | 비고 |
|---|---|
| `docs/DESIGN.md` | 디자인 토큰. 프론트엔드 작업의 핵심 자료지만 `frontend/CLAUDE.md` 에서 미참조 |
| `docs/ENTITIES.md` | 엔티티·스키마 레퍼런스. 백엔드 모델 작업의 핵심 자료지만 `backend/CLAUDE.md` 에서 미참조 |
| `docs/CONTENT_GENERATION_FLOW.md` | 가이드·룰·RAG·Q&A 이벤트 fan-out. **untracked** (2026-05-26 신규) |
| `docs/INGESTION_PIPELINE.md` | n8n → DB 적재. **untracked** (2026-05-26 신규) |
| `docs/ops/cost-snapshot.md` | AWS 비용 스냅샷. 어떤 CLAUDE.md 도 안 가리킴 |
| `docs/troubleshooting/` | 9 개 디버깅 회고 |
| `docs/superpowers/` | brainstorming 사이클 산출물 |

### 1-2. 정본이 중복된 문서

- **PRD**: `docs/PRD.md` (통합본, 2026-05-08 갱신) 와 `docs/prd/` (도메인별 10 개 분할판, 2026-04-17 이후 stale) 가 동시 존재.
- **컨벤션**: `docs/CONVENTIONS.md` 와 `backend/CLAUDE.md` 가 DTO/Lombok/Swagger/예외 처리 등을 중복 기술. drift 위험.
- **트러블슈팅/운영**: `docs/troubleshooting/` (디버깅 회고) 와 `docs/superpowers/operations/` (런북 + 활성 상태 메모) 가 한 폴더처럼 보이지만 성격이 다름.

### 1-3. 백/프 전용 문서가 공통 `docs/` 에 섞여 있음

- `DESIGN.md` 는 프론트엔드 전용
- `ENTITIES.md`, `INGESTION_PIPELINE.md`, `CONTENT_GENERATION_FLOW.md` 는 백엔드 전용
- 공통 자료 (`PRODUCT.md`, `ARCHITECTURE.md`, `OPS.md`, `PRD.md`) 와 같은 레벨에 놓여 있어 탐색·소유권이 불명확.

### 1-4. CLAUDE.md 가 비대해지고 본인 책임 경계가 모호함

- 루트 `CLAUDE.md` 102 줄, `backend/CLAUDE.md` 108 줄, `frontend/CLAUDE.md` 84 줄.
- 안에 컨벤션·아키텍처·문서맵·작업방식이 다 들어있어 한 곳을 고치면 다른 곳도 같이 고쳐야 하는 의존이 생김.

---

## 2. 결정 로그

brainstorming 단계에서 사용자와 합의된 결정.

| # | 결정 | 대안 | 사유 |
|---|---|---|---|
| D-1 | rules 위치는 `.claude/rules/` | `docs/rules/`, 루트 `rules/` | Claude Code 전용 컨벤션. 사용자가 명시적으로 `/rules` 경로 제안. claude-plugins-official 표준 패턴. |
| D-2 | rules 쒬기 단위는 하이브리드 (`.claude/rules/{backend,frontend}/<관심사>.md` + 공통은 `common.md`) | 스택별 단일 파일, 관심사별 단일 파일 | 백/프 규칙이 본질적으로 다른 도메인. 그 안에서 관심사별 분할로 한 작업 시 1~2 개 파일만 읽으면 됨. |
| D-3 | 백/프 전용 docs 는 `backend/docs/`, `frontend/docs/` 로 이동 | `docs/backend/`, `docs/frontend/` | 모듈 코드와 같은 디렉토리에 있어 작업 중 발견·소유권이 명확. 모듈 CLAUDE.md 에서 `@backend/docs/*` 참조가 자연스러움. |
| D-4 | PRD 정본은 `docs/prd/` (분할판) | 통합본 `docs/PRD.md` 유지 | 한 모듈 작업 시 해당 도메인 파일만 읽으면 되어 효율적. 통합본 1030 줄의 5월 변경분을 분할판에 backport 한 뒤 통합본 삭제. **(D-4 는 가장 위험·작업량이 큰 결정이라 별도 PR 로 분리)** |
| D-5 | 트러블슈팅과 런북은 분리 | 한 폴더에 통합 | troubleshooting 은 백/프/인프라 모든 영역의 디버깅 회고 (사용자 명시). runbook 은 운영 절차·활성 상태. 성격이 다름. |
| D-6 | runbook 은 `docs/runbooks/` 로 이동 (superpowers/operations/ 에서) | superpowers 안에 유지 | 사이클 산출물보다 일반 운영 자료에 더 가까움. superpowers/ 는 spec/plan 사이클 산출물에 집중. |
| D-7 | superpowers/ 디렉토리는 그대로 유지 | docs/ 루트로 평탄화 | brainstorming → spec → plan 사이클 산출물로 역할이 뚜렷. README 가 흐름을 명시. |
| D-8 | 마이그레이션은 별도 워크트리 (`worktree-docs-restructure`) 에서 진행 | main 직접 작업 | 진행 중 다른 세션이 main 에서 작업해도 충돌 없음 (사용자 명시 요구사항). |

---

## 3. 최종 디렉토리 구조

```text
youthfit/
├── CLAUDE.md                       # 슬림화 — 제품 소개 + 문서맵 + 컨벤션 참조
│
├── .claude/
│   ├── rules/                      # 🆕 컨벤션 단일 진실 소스
│   │   ├── common.md               # Conventional Commits, 작업 방식, 연동·크롤링 규칙
│   │   ├── backend/
│   │   │   ├── architecture.md     # DDD 의존방향, 트랜잭션 경계, 모듈 경계
│   │   │   ├── naming.md           # Service 메서드, 파일·패키지 네이밍
│   │   │   ├── dto.md              # record + Command/Result/Request/Response
│   │   │   ├── swagger.md          # Api 인터페이스, ErrorCode 매핑
│   │   │   └── lombok.md           # Lombok 허용/지양, 예외 처리, Controller
│   │   └── frontend/
│   │       ├── directory.md        # apis/hooks/stores/pages 구조, 컴포넌트 규칙
│   │       ├── state-management.md # TanStack vs Zustand vs URL
│   │       └── styling.md          # Tailwind, cn(), 반응형, 터치 타겟
│   ├── agents/                     # 기존 유지 (code-reviewer, frontend-developer, ui-ux-designer)
│   ├── commands/                   # 기존 유지 (cr.md)
│   └── skills/                     # 기존 유지
│
├── backend/
│   ├── CLAUDE.md                   # 슬림화 — 기술 스택 + @.claude/rules/backend/ 참조
│   ├── docs/                       # 🆕 백엔드 전용 레퍼런스
│   │   ├── ENTITIES.md             # ← docs/ENTITIES.md
│   │   ├── INGESTION_PIPELINE.md   # ← docs/INGESTION_PIPELINE.md (NEW)
│   │   └── CONTENT_GENERATION_FLOW.md  # ← docs/CONTENT_GENERATION_FLOW.md (NEW)
│   └── src/
│
├── frontend/
│   ├── CLAUDE.md                   # 슬림화 — 기술 스택 + @.claude/rules/frontend/ 참조
│   ├── docs/                       # 🆕 프론트엔드 전용 레퍼런스
│   │   └── DESIGN.md               # ← docs/DESIGN.md
│   └── src/
│
└── docs/                           # 공통 (제품·아키텍처·운영)
    ├── PRODUCT.md
    ├── ARCHITECTURE.md
    ├── OPS.md
    ├── prd/                        # 정본 — 통합본 PRD.md 최신 내용 backport
    │   ├── README.md
    │   └── 00-overview.md ~ 10-release.md
    ├── ops/
    │   └── cost-snapshot.md        # 그대로 유지
    ├── troubleshooting/            # 공통 — 백/프/인프라 디버깅 회고
    └── runbooks/                   # 🆕 ← docs/superpowers/operations/
        ├── 2026-04-28-attachment-extraction.md
        ├── 2026-04-29-cost-guard-active.md
        ├── 2026-04-30-qna-v0-ready.md
        └── 2026-05-05-email-transport.md
    └── superpowers/
        ├── README.md               # 사이클 흐름에서 operations → ../runbooks/ 로 안내 갱신
        ├── specs/
        └── plans/
```

### 삭제되는 것

- `docs/PRD.md` (PR5 에서 분할판 최신화 완료 후)
- `docs/CONVENTIONS.md` (`.claude/rules/backend/` 로 분해 완료 후)
- `docs/superpowers/operations/` (`docs/runbooks/` 로 이동 완료 후 빈 폴더 제거)

---

## 4. 3 개 `CLAUDE.md` 슬림화 본문

### 4-1. 루트 `CLAUDE.md` (102 → 약 60 줄)

```markdown
# CLAUDE.md

## 프로젝트
- **이름**: YouthFit
- **목표**: 흩어진 청년 정책 정보를 한곳에 모으고, 쉬운 설명·가벼운 적합도
  판정·출처 기반 Q&A 로 자격 요건·준비사항·다음 행동을 이해할 수 있게 돕는다.
- **포지셔닝**: 공식 정책 포털을 대체하지 않는다. 정책을 더 쉽게 찾고
  이해하도록 돕고, 최종 신청은 공식 채널로 연결하는 보완형 서비스다.

## MVP (v0) 범위
포함: 정책 목록/상세/검색, 카카오 로그인, 프로필, 적합도 판정,
RAG Q&A, 북마크, 이메일 알림
제외: 커뮤니티/평점, 모바일 앱/푸시, 관리자 대시보드,
하이브리드 검색, 이벤트 드리븐, 외부 공개 API

## 프로젝트 구조
\`\`\`
youthfit/
├── CLAUDE.md              # 공통 지침
├── .claude/rules/         # 컨벤션 (백/프/공통)
├── backend/               # Spring Boot — backend/CLAUDE.md, backend/docs/
├── frontend/              # React — frontend/CLAUDE.md, frontend/docs/
├── docs/                  # 공통 제품·아키텍처 문서
└── n8n/
\`\`\`

## 모듈 경계
**백엔드**: admin, ingestion, policy, rag, guide, eligibility, qna,
auth, user, common
**프론트엔드 영역**: 정책 탐색 / 인증 / 사용자 / 적합도 / Q&A

## 컨벤션 (반드시 따른다)
@.claude/rules/common.md
백엔드 코드 수정 전: @.claude/rules/backend/
프론트엔드 코드 수정 전: @.claude/rules/frontend/

## 문서 맵
| 위치 | 내용 |
|------|------|
| `docs/PRODUCT.md` | 제품 목표·타겟·정책 해석 원칙 |
| `docs/ARCHITECTURE.md` | 모듈 경계·레이어·데이터 흐름·인프라 |
| `docs/prd/` | 도메인별 PRD (00-overview ~ 10-release) |
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
- 새 기능 구현 → `docs/prd/0X-<domain>.md`
- 사용자 해석 방식 변경 → `docs/PRODUCT.md`
- 크롤링·배포·시크릿 → `docs/OPS.md`
- 백엔드 코드 → `backend/CLAUDE.md` + 관련 `backend/docs/*`
- 프론트엔드 코드 → `frontend/CLAUDE.md` + `frontend/docs/DESIGN.md`
```

### 4-2. `backend/CLAUDE.md` (108 → 약 55 줄)

```markdown
# Backend CLAUDE.md

> Spring Boot 백엔드. 공통 규칙은 루트 `CLAUDE.md`, 컨벤션은
> `.claude/rules/backend/` 참조.

## 기술 스택
| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.5 |
| ORM | Hibernate + Spring Data JPA | - |
| Database | PostgreSQL + pgvector | 17 |
| Cache | Redis | 7 |
| Auth | Kakao OAuth2 + JWT (jjwt) | - |
| AI/LLM | OpenAI (Embedding, Chat) | - |
| API 문서 | springdoc-openapi | 2.8.6 |
| 빌드 | Gradle | - |
| 테스트 | JUnit 5 + JaCoCo | - |

## 빌드 및 실행
\`\`\`bash
cd backend
./gradlew build && ./gradlew test
./gradlew bootRun     # 포트 8080
\`\`\`

## 모듈 목록
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

### 4-3. `frontend/CLAUDE.md` (84 → 약 45 줄)

```markdown
# Frontend CLAUDE.md

> React 프론트엔드. 공통 규칙은 루트 `CLAUDE.md`, 컨벤션은
> `.claude/rules/frontend/` 참조.

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

---

## 5. `.claude/rules/` 9 개 파일 분해 매핑

| 새 파일 | 다루는 내용 | 원본 출처 |
|---|---|---|
| `common.md` | 작업 방식 (작은 변경·빌드 후 커밋), Conventional Commits, n8n 수신 표면, LLM 비용 방어, 비밀값, 크롤링 (robots/UA/rate limit/원문 비노출) | 루트 `CLAUDE.md` |
| `backend/architecture.md` | DDD + Clean Architecture, 의존 방향, 트랜잭션 경계, Entity 노출 금지, presentation DTO import 제약, domain 프레임워크 의존 금지, 코드 리뷰 체크 기준 | `backend/CLAUDE.md` + `docs/CONVENTIONS.md` |
| `backend/naming.md` | Service 동사 (find/register/change/cancel/judge/generate/send), 모호한 이름 (get/save/check/list) 금지, `{도메인}Api` 인터페이스 네이밍 | `backend/CLAUDE.md` + `docs/CONVENTIONS.md` |
| `backend/dto.md` | DTO 는 반드시 record, Presentation Request/Response, Application Command/Result, 변환 책임 (Request→Command, Result→Response) | `backend/CLAUDE.md` + `docs/CONVENTIONS.md` |
| `backend/swagger.md` | `@Tag`·`@Operation` 은 `{도메인}Api` 인터페이스에, Controller 는 Spring MVC 어노테이션만, `@Parameter`·`@Schema`, `@SecurityRequirements` 빈 값, `@ApiResponses` + ErrorCode (YF-001~YF-500) | `backend/CLAUDE.md` + `docs/CONVENTIONS.md` |
| `backend/lombok.md` | 허용 (`@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(PROTECTED)`), 지양 (`@Data`, `@Setter`, public all-args), Entity setter 금지, 예외 처리 (도메인 커스텀 + 전역 핸들러), Controller 규칙 (HTTP 관심사·Repository 직접 접근 금지) | `backend/CLAUDE.md` + `docs/CONVENTIONS.md` |
| `frontend/directory.md` | `src/` 디렉토리 구조 (apis/hooks/stores/pages/components/types/lib), 컴포넌트 PascalCase·도메인 그룹핑, `{Name}Page.tsx`, API 패턴 (`{domain}.api.ts` → useQuery/useMutation 래퍼) | `frontend/CLAUDE.md` |
| `frontend/state-management.md` | 상태 유형 결정 표 (TanStack/Zustand/useState/URL), 인증 토큰 (Zustand+localStorage), 글로벌 vs 로컬 vs URL | `frontend/CLAUDE.md` |
| `frontend/styling.md` | Tailwind 유틸리티 우선, `cn()` (clsx+tailwind-merge), 색상 토큰 (Blue-500, 적합도 Green/Amber/Red), 모바일 우선 반응형 (`md:`), 터치 타겟 44×44 | `frontend/CLAUDE.md` |

---

## 6. 마이그레이션 PR 분할

총 5 개 PR. 의존 관계: `PR1 + PR2 → PR4`, PR3·PR5 는 독립.

| # | 제목 | 범위 | 위험 | 검증 |
|---|---|---|---|---|
| 1 | `chore(rules): introduce .claude/rules/ split` | `.claude/rules/common.md` + `backend/{5}` + `frontend/{3}` 신설만. 기존 파일 손대지 않음 | 🟢 낮음 | `ls .claude/rules/**/*.md` 결과 9 개 |
| 2 | `docs: move backend/frontend-specific refs into module dirs` | `git mv` 4 건: DESIGN/ENTITIES/INGESTION_PIPELINE/CONTENT_GENERATION_FLOW → 모듈 docs/ + 참조 경로 업데이트 | 🟡 중간 | `grep -r "docs/DESIGN.md\|docs/ENTITIES.md" .` 결과 0 |
| 3 | `docs: extract runbooks/ from superpowers/operations/` | `git mv` 4 건 + superpowers/README.md 흐름 갱신 | 🟢 낮음 | `ls docs/superpowers/operations` 비어 있음 |
| 4 | `docs: slim CLAUDE.md files, point to .claude/rules/` | 3 개 CLAUDE.md 본문을 섹션 4 초안으로 교체 + `docs/CONVENTIONS.md` 삭제 | 🟡 중간 — 자기 자신을 바꾸는 변경 | (a) `grep -L "@\.claude/rules" {root,backend,frontend}/CLAUDE.md` 결과 0, (b) 새 세션에서 rules 자동 로드 확인 |
| 5 | `docs(prd): backport PRD.md updates into prd/ split` | 통합본 5월 변경분을 `docs/prd/0X-*.md` 에 반영 → 통합본 삭제 | 🔴 높음 — 1030 줄 수동 매핑 | (a) 항목 1:1 대응 diff, (b) `wc -l docs/prd/*.md` 합계 ≥ 기존 PRD.md |

### 안전장치

- PR1 머지 후 새 세션 열어 `.claude/rules/` 자동 로드 확인. 실패 시 PR4 진행 보류.
- PR4 머지 전 워크트리에서 새 Claude 세션 띄워 동작 검증.
- PR5 는 다른 PR 과 같은 날 머지 금지 (정본 전환 충돌 회피).

---

## 7. 비범위

이번 사이클에서 의도적으로 빼는 항목.

- **`docs/superpowers/` 의 specs/plans 파일명 정리·prefix 일관화**: README 의 `DONE_`/`TODO_` prefix 규칙이 일부 누락된 파일이 있을 수 있으나 이번 사이클의 본체 작업이 아님.
- **`docs/troubleshooting/` 의 9 개 기존 문서 재분류**: 그대로 두고 향후 새 문서를 작성할 때 패턴을 확립.
- **새 컨벤션 신설**: 이번엔 기존 컨벤션을 이동·분해만. 새로운 규칙을 추가하지 않음.
- **`.claude/agents/`·`commands/`·`skills/` 재정리**: 활용 흐름 점검은 별도 사이클.

---

## 8. 위험과 완화

| 위험 | 영향 | 완화책 |
|---|---|---|
| PR4 머지 후 Claude 세션이 컨벤션을 못 읽음 | 다음 작업 품질 하락 | PR1·2 머지 후 새 세션으로 사전 검증. `@.claude/rules/...` 참조 동작 확인. |
| PR5 PRD 분할판 backport 중 항목 누락 | 정책 요구사항 손실 | PR 전 통합본·분할판 1:1 diff 리뷰. 라인 수 합계 검증. |
| 워크트리에서 작업 중 main 에 다른 변경 머지 | 머지 충돌 | 각 PR 머지 직전 `git fetch && git rebase origin/main`. PR 5 개라 각각 충돌 범위가 작음. |
| 옮긴 문서의 옛 경로가 외부 링크에 박혀 있을 수 있음 (PR 본문·노션 등) | 깨진 링크 | 옮기는 PR 본문에 변경 전·후 경로 매핑표를 명시. |

---

## 9. 다음 단계

1. 사용자가 본 spec 리뷰 → 승인.
2. `writing-plans` 스킬로 PR1~5 각각의 Task 단위 구현 절차·검증 명령을 담은 plan 작성. plan 위치: `docs/superpowers/plans/2026-05-26-docs-restructure.md`.
3. plan 승인 후 PR1 부터 순차 진행. PR1~3 은 병렬 가능, PR4 는 PR1·2 머지 후, PR5 는 별도 트랙.
