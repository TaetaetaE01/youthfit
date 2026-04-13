---
name: create-pr
description: >
  YouthFit 프로젝트의 PR(Pull Request)을 Conventional Commits 컨벤션에 맞게 생성한다.
  "PR 만들어줘", "PR 작성해줘", "풀리퀘 생성해줘" 라고 하면 이 스킬을 사용한다.
---

# PR 생성 스킬

## 브랜치 네이밍 규칙

작업 전 현재 브랜치를 확인하고, 브랜치명이 아래 규칙을 따르는지 검증한다.

```
<type>/<패키지명>-<짧은-설명>

예시:
feat/ingestion-raw-endpoint
fix/rag-embedding-null-check
refactor/policy-dedup-logic
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
3. 변경된 패키지 파악 (`ingestion` / `policy` / `rag` / `guide` / `eligibility` / `qna` / `user` / `common`)
4. 아래 PR 템플릿에 맞게 작성

## PR 템플릿

```markdown
## 작업 목적
## 아키텍처 및 설계 결정 (Trade-off)
## 핵심 변경 사항
- `클래스명`: [어떤 역할을 하도록 추가/수정됨]
- `클래스명`: [어떤 역할을 하도록 추가/수정됨]

## 리뷰 포인트
- [ex: PDF 파싱 정규식이 완벽하지 않을 수 있으니 `PdfExtractService`를 확인해 주세요.]

## YouthFit 가드레일 자가 점검
- [ ] 레이어 격리: Domain 패키지에 Spring/JPA/OpenAI SDK 등 외부 의존성 침투 없음
- [ ] 단방향 의존: Controller → Service → Repository 흐름 엄수 (순환 참조 없음)
- [ ] 비용 방어: LLM·임베딩 호출 전 `source_hash` 멱등성 검증 로직 포함
- [ ] 보안: `.env` 값이나 API Key가 코드에 하드코딩되지 않음

## 테스트 전략
## 스크린샷 (선택)
```


## 주의 사항

- `.env`, API 키, DB 비밀번호가 커밋에 포함되지 않았는지 반드시 확인
- 한 PR은 하나의 패키지 또는 하나의 기능 슬라이스만 포함
- ARCHITECTURE.md 변경이 필요한 경우 해당 PR에 같이 포함
