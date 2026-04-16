---
name: create-pr
description: >
  YouthFit 프로젝트의 PR(Pull Request)을 Conventional Commits 컨벤션에 맞게 생성한다.
  "PR 만들어줘", "PR 작성해줘", "풀리퀘 생성해줘" 라고 하면 이 스킬을 사용한다.
---

# PR 생성 스킬

## 영역 태그 규칙 (FE / BE)

**PR 제목과 커밋 메시지 모두** 작업 영역을 구분해 태그를 단다.

### 판별 기준
- 변경 파일이 `frontend/` 하위에만 있으면 → **FE**
- 변경 파일이 `backend/` 하위에만 있으면 → **BE**
- 둘 다 걸쳐 있으면 → **FE/BE** (가능하면 PR을 분리)
- 그 외(`docs/`, 루트 설정 파일 등)는 태그 생략 또는 주된 영향 영역으로 표기

### PR 제목 포맷
```
[FE] <type>: <설명>
[BE] <type>: <설명>
[FE/BE] <type>: <설명>

예시:
[FE] feat: 랜딩 페이지 네비게이션 연결
[BE] fix: 정책 목록 null 날짜 처리
[FE/BE] feat: 카카오 로그인 플로우 구현
```

### 커밋 메시지 포맷
```
<type>(fe): <설명>
<type>(be): <설명>
<type>(fe,be): <설명>

예시:
feat(fe): 로그인 유도 모달 추가
fix(be): 적합도 판정 NPE 수정
refactor(fe,be): 프로필 DTO 필드명 통일
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

1. `git diff main...HEAD` 로 변경 파일 목록 확인
2. `git log main...HEAD --oneline` 으로 커밋 내역 확인
3. 변경 경로 기준으로 **영역 태그 판별** (`frontend/` → FE, `backend/` → BE, 혼합 → FE/BE)
4. 변경된 패키지 파악
   - BE: `ingestion` / `policy` / `rag` / `guide` / `eligibility` / `qna` / `auth` / `user` / `common`
   - FE: `pages` / `components` / `api` / `hooks` / `stores` 등
5. 제목 앞에 `[FE]` / `[BE]` / `[FE/BE]` 태그를 붙여 PR 생성
6. 아래 PR 템플릿에 맞게 본문 작성

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
