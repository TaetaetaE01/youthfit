# 어드민 — Spec 3: Q&A 캐시 hit/miss 로그 설계 (Outline)

> **상태**: Outline (구현 직전 brainstorming 필요)
> **작성일**: 2026-05-05
> **시리즈**: 어드민 시리즈 5개 중 #3
> **선행**: Spec 1 (admin foundation, 완료)

---

## 1. 목표

semantic-cache의 매칭 품질과 비용 절감 효과를 운영적으로 추적한다.
- **집계**: hit률, 평균 유사도, 미스 비율 추세
- **건별**: 어떤 질문이 어떤 캐시 항목에 매칭됐는지(또는 매칭 안 됐는지) 디버깅

semantic-cache 인프라는 이미 구현되어 있다(`docs/superpowers/specs/DONE_2026-05-01-semantic-qna-cache-design.md`). 이 spec은 그 위에 *추적·관찰* 레이어만 얹는다.

## 2. 범위

### In
- 매 Q&A 요청마다 캐시 조회 결과를 `QnaCacheLookupLog`에 적재
  - hit / miss / threshold-below
  - 매칭된 cached 질문 id, 유사도 점수, 비용 절감 추정치
- 어드민 화면:
  - hit률 도넛 + 시간별 추이
  - 평균 유사도 분포 히스토그램
  - 미스/threshold-below 사례 리스트 (캐시 보강 후보 발견)
  - 매칭된 hit의 상세 (질문 → 매칭 cached 질문 → 유사도)

### Out
- 캐시 항목 직접 편집 (CRUD UI)
- 사용자별 질문 히스토리 (개인정보)

## 3. 데이터 모델 outline

```
QnaCacheLookupLog (qna 모듈)
- id
- question_text (raw 또는 hashed; 개인정보 고려)
- normalized_text
- result: enum (HIT, MISS, BELOW_THRESHOLD)
- matched_cached_id (nullable, FK)
- similarity_score (nullable)
- threshold (당시 적용 임계값)
- llm_call_made: boolean (캐시 미스 → LLM 호출했나)
- looked_up_at
```

> **결정 보류**: question_text 저장 정책. 개인정보 함의가 있으면 hash 또는 임베딩만 저장. 일반적인 정책 질문은 PII 가능성 낮지만 안전하게 hash + 길이만 저장하는 옵션 검토.

## 4. 어드민 화면 outline

### 4.1 라우트
- `/admin/qna-cache` — 메인
- `/admin/qna-cache/:lookupId` — 상세 (매칭 디버깅)

### 4.2 메인 화면 구성
- KPI: 오늘 hit률, 어제 hit률, 평균 유사도, 비용 절감 추정
- 차트: 일자별 hit/miss/below-threshold stacked + hit률 라인
- 도넛: 결과 분포 (hit / below / miss)
- 테이블: 최근 미스 질문 리스트 (캐시 추가 후보)

### 4.3 상세 화면 (hit인 경우)
- 원 질문 (또는 hash)
- 매칭된 cached 질문 + 유사도
- 당시 임계값
- 사용된 답변 발췌

### 4.4 상세 화면 (miss인 경우)
- 원 질문
- top-N 후보들과 각 유사도 (왜 매칭 안 됐는지 디버깅)
- "이 질문을 캐시에 추가" 액션 (실제 추가는 별도 화면 — 본 spec 범위 외)

## 5. 보관 정책

- 90일. 미스 데이터는 캐시 보강에 가치 있으므로 별도 export 옵션 고려.

## 6. 테스트 전략 outline

- 단위: 결과 분류(HIT/MISS/BELOW_THRESHOLD) 로직
- 통합: 실제 cache lookup 호출 → 로그 적재 검증
- 슬라이스: 어드민 컨트롤러
- 프론트: 차트/테이블 placeholder + filter 동작

## 7. 의존성

- Spec 1 (admin foundation) 완료 ← 의존
- semantic-cache 인프라 (이미 있음) ← 의존
- 다른 spec은 의존 안 함

## 8. 열린 질문 (구현 직전 brainstorming에서 결정)

- question_text 보관: raw / hash / 임베딩만 — 개인정보 정책 합의
- 비용 절감 추정 산식 (캐시 hit당 절감 토큰 × 단가)
- top-N 후보 보관: lookup마다 top-3을 저장할지 (저장량 부담 vs 디버깅 가치)
- 미스 사례를 캐시에 추가하는 워크플로우 — 어드민에서 직접? 아니면 export → 외부 큐레이션?
- 차트 라이브러리 (Spec 2와 통일)

## 9. 변경 영향 범위 (예상)

- `qna/domain/model/QnaCacheLookupLog` 엔티티
- `qna/application/service/SemanticCacheService` (또는 동등)에 적재 hook 추가
- `admin/presentation/controller/AdminQnaCacheController` (조회용)
- 프론트: `pages/admin/AdminQnaCachePage`, `apis/admin.qnaCache.api.ts`
