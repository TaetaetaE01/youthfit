# 어드민 — Spec 4: LLM 비용 대시보드 설계 (Outline)

> **상태**: Outline (구현 직전 brainstorming 필요)
> **작성일**: 2026-05-05
> **시리즈**: 어드민 시리즈 5개 중 #4
> **선행**: Spec 1 (admin foundation, 완료)

---

## 1. 목표

LLM/임베딩 호출 비용을 운영적으로 추적해 비용 방어 장치를 갖춘다 (CLAUDE.md 명시 원칙).
- **집계만**: 일자별·모듈별 호출 수, 토큰 수, 추정 비용 (USD/KRW)
- 건별 적재는 양이 많으므로 하지 않음 (Spec 1 brainstorming에서 결정됨)

## 2. 범위

### In
- 표준 LLM metrics 인터셉터 신설 — 모든 OpenAI 호출(chat, embedding) 통과 지점에 install
- 적재 단위: 호출 1건당 1 row — 호출 수가 많으므로 5분 또는 1시간 버킷으로 사전 집계 후 적재 (구체 결정 보류)
- 어드민 화면:
  - 시간별 / 일자별 비용 라인 차트
  - 모듈별 stacked bar (Q&A / 가이드 / 임베딩)
  - 비용 KPI: 오늘, 이번주, 이번달
  - 모델별 분포 (gpt-4o, gpt-4o-mini, text-embedding-3 등)

### Out
- 사용자별 비용 (개인정보 + 분석 가치 적음)
- 실시간 알림 (비용 급증) — Grafana 등 외부에서 처리
- 비용 한도 설정 / 차단 — 별도 spec

## 3. 데이터 모델 outline

```
LlmCostBucket (admin 모듈 또는 신규 metrics 모듈)
- id
- bucket_at (집계 시간 — 5분 또는 1시간 단위)
- module: enum (QNA, GUIDE, EMBEDDING)
- model: string (예: gpt-4o-mini)
- call_count
- prompt_tokens
- completion_tokens
- total_tokens
- estimated_cost_usd  -- numeric(10, 4)
- created_at
```

UNIQUE (bucket_at, module, model) — upsert로 누적.

## 4. 인터셉터 설계 outline

옵션:
- **a. 각 application service에 명시 호출** — `metrics.record(module, model, tokens, ...)`
- **b. OpenAI 클라이언트 wrapper** — wrap once, 호출 시 자동 적재. 추천.
- **c. Spring AOP** — annotation 기반. 가장 침투적이지만 boilerplate 적음.

→ b 권장: `OpenAiClientFacade`(또는 동등) 한 곳에서 토큰 추출 + metrics 발행. application은 그대로 호출.

## 5. 어드민 화면 outline

### 5.1 라우트
- `/admin/llm-cost`

### 5.2 메인 화면
- 기간 선택: 7D / 30D / 90D / 사용자 정의
- KPI 카드 4개: 오늘 비용 / 이번주 / 이번달 / 호출 수
- 라인 차트: 시간별 비용 추이 (hover 시 모델·모듈 breakdown)
- Stacked bar: 일자별 모듈 분포
- 테이블: 모델별 합계 (호출 수, 토큰, 비용)

## 6. 비용 계산 outline

OpenAI 가격은 모델별로 다르고 변동됨. 가격표를 코드 또는 DB에 보관 후 계산.
- `LlmModelPricing` 엔티티 또는 정적 맵
- 가격 변경 시 *과거 데이터*는 그대로 두고 *신규 적재*에만 새 가격 적용

## 7. 보관 정책

- 일별 집계: 무기한 (양 적음, 트렌드 분석 가치)
- 시간별 집계: 90일 이후 일별로 롤업

## 8. 테스트 전략 outline

- 단위: 토큰 → 비용 변환, 모델별 가격 조회
- 통합: OpenAi 클라이언트 wrapper → 적재 호출
- 슬라이스: 어드민 컨트롤러 (필터/집계 쿼리)
- 프론트: 차트/KPI 렌더 + 기간 필터 동작

## 9. 의존성

- Spec 1 (admin foundation) 완료 ← 의존
- 다른 spec은 의존 안 함 (단, 같은 차트 라이브러리/디자인 패턴 재사용)

## 10. 열린 질문 (구현 직전 brainstorming에서 결정)

- 버킷 단위: 5분 vs 1시간 — 분석 해상도 vs 적재량 트레이드오프
- 가격표 보관: DB(Pricing 엔티티) vs 정적 코드 — 운영 변경 빈도 고려
- 실시간 적재 vs 배치 — 매 호출마다 vs N건마다 flush
- 통화: USD만 vs USD + KRW 환산 (환율은 어디서?)
- 적재 위치: 신규 `metrics` 모듈 vs admin 모듈 내 — DDD 관점에서 도메인 구분 필요
- 차트 라이브러리 (Spec 2와 통일)

## 11. 변경 영향 범위 (예상)

- 신규 `metrics`(또는 `admin.metrics`) 모듈 — `LlmCostBucket` 엔티티/리포지토리
- 기존 OpenAI 클라이언트 통합 지점 (`qna`, `guide`, `rag` 모듈의 OpenAi 호출 코드)
  - 패치 vs 새 facade 신설 — brainstorming에서 결정
- `admin/presentation/controller/AdminLlmCostController`
- 프론트: `pages/admin/AdminLlmCostPage`
