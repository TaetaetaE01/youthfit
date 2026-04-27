# 다음 작업 메모 (2026-04-28)

> 점심 후 세션에서 이어서 진행하기 위한 핸드오프 메모.

## 직전 작업

- PR #44: `[FE/BE] feat: 정책 상세 쉬운 해석 가이드 도입 (페어드 레이아웃)` — 사용자 머지 예정
- spec: `docs/superpowers/specs/2026-04-28-easy-policy-interpretation-design.md`
- plan: `docs/superpowers/plans/2026-04-28-easy-policy-interpretation.md`

---

## 다음 주요 작업: 첨부파일 추출/임베딩 파이프라인

이번 가이드 spec에서 **의도적으로 분리해둔 후속 작업**. 사용자 원래 비전(복지로 첨부 PDF → 임베딩 → 하이브리드 가이드)의 나머지 절반.

### 왜 필요한가

- 복지로 API 응답의 구조화 필드(`supportTarget`, `selectionCriteria`, `supportContent`)는 종종 짧고 디테일 부족
- 진짜 자격 예외/세부 조건은 첨부 PDF(공고문, 시행규칙)에 박혀있음
- 현재 `policy_attachment` 테이블엔 URL만 저장, 텍스트 추출 안 됨 → 가이드/Q&A가 첨부 정보를 전혀 모름
- 첨부 임베딩 파이프라인이 만들어지면 **가이드 코드 변경 없이** `RagIndexingService`를 통해 가이드 입력이 자동 풍부해짐 (이번 spec에서 옵션으로 설계해둠)

### 시작점

새 brainstorming 세션 → spec → plan → 구현 (이번 사이클과 동일).

### 결정해야 할 것들 (brainstorming 입력)

- **PDF 라이브러리**: Apache PDFBox / Apache Tika / 다른 옵션
- **HWP 처리**: hwplib (Java) 가능 여부, 또는 HWP는 v0.x로 미루기
- **다운로드 정책**: 동기 / 비동기 큐 / 재시도 / 실패 처리
- **추출 텍스트 신뢰도**: 표·이미지·스캔 PDF는 추출 품질 천차만별. 신뢰도 낮으면 어떻게 거르나
- **트리거 위치**: ingestion 후속 호출 (현재 가이드 생성과 같은 위치) vs 별도 스케줄러 backfill
- **저장 형태**: 추출 텍스트를 `PolicyAttachment.extractedText` 컬럼에 저장 vs 곧바로 `RagIndexingService`로 청크화
- **재추출 정책**: 첨부 URL 변경 / 내용 변경 감지 어떻게

### 호출 지점 (가이드 모듈에 미치는 영향)

```
ingestion → 첨부 다운로드/추출 → RagIndexingService.indexPolicyDocument(policyId, content)
                                       ↓
                                 policy_document 테이블 채워짐
                                       ↓
GuideGenerationService 실행 시 자동으로 청크 결합 입력으로 들어감 (현재 코드 그대로)
```

→ 가이드 모듈은 손댈 필요 없음. ingestion/rag 두 모듈 작업.

---

## 보조 후속 항목 (필요 시)

- **다중 출처 정책 dedup (ingestion)**: 복지로와 온통청년에서 같은 정책이 들어왔을 때 정책 row 를 어떻게 식별/머지할지. 별도 brainstorming 1사이클 필요. 결정 포인트:
  - dedup 키 (title 정규화? content hash? 자연키?)
  - 머지 정책 (어느 출처를 정본으로? 우선순위? 신선도 기반?)
  - 양쪽 source 보존 여부 (현재 PolicySource 1:N 구조라 가능)
  - 갱신 시 재판정 정책
  - 수동 오버라이드 출구 (잘못된 머지 풀기)
  - 트리거 위치: 출처 뱃지 spec(`docs/superpowers/specs/2026-04-28-policy-source-badge-design.md`) 의 후속 작업으로 사용자 명시.
- **사용자 lazy 트리거**: 미생성 정책에서 "AI 해석 받기" CTA 도입 여부 — 미생성 비율 메트릭 보고 결정. 현재는 카드 숨김.
- **그룹 접기 UX (Accordion)**: 선정기준 7그룹처럼 길어지는 케이스에 대비. 사용자 반응 보고 도입.
- **사용자 피드백 메커니즘**: "이 풀이 틀렸어요" 신고 — v1 범위.
- **"놓치기 쉬운 점" / "한 줄 요약" 카드 워딩**: spec 9.3에 후보 리스트 있음. 현재 워딩(`이 정책 한눈에` / `놓치기 쉬운 점`)으로 시작했으나 변경 가능.
- **다른 정책에 가이드 일괄 생성**: 운영 작업. 정책 30번만 가이드 있는 상태.
  ```bash
  for id in 1 2 3 ...; do
    curl -X POST http://localhost:8080/api/internal/guides/generate \
      -H "X-Internal-Api-Key: changeme" \
      -d "{\"policyId\": $id, \"policyTitle\": \"x\", \"documentContent\": \"x\"}"
  done
  ```
- **GuideValidator 강화**: 현재 로깅만. 메트릭 모니터링 후 차단/재생성 정책 도입 (v0.2).

---

## 환경 메모

- 백엔드 컨테이너 실행 중 (`docker compose up -d backend` 됨). 코드 수정 시 `docker compose up -d --build backend` 필요
- 프론트 dev 서버 5173 (`npm run dev`)
- INTERNAL_API_KEY=`changeme`, OPENAI_API_KEY는 `.env`에 설정됨
- 가이드 생성 endpoint: `POST /api/internal/guides/generate` (X-Internal-Api-Key 헤더 필수)
