# 어드민 — Spec 5: Ingestion 헬스 설계 (Outline)

> **상태**: Outline (구현 직전 brainstorming 필요)
> **작성일**: 2026-05-05
> **시리즈**: 어드민 시리즈 5개 중 #5
> **선행**: Spec 1 (admin foundation, 완료)

---

## 1. 목표

n8n 및 외부 수집 파이프라인의 데이터 신선도와 품질을 운영적으로 추적한다.
- **집계**: 일자별 신규 수신, 정규화 성공/실패, 중복 비율
- **건별**: 정규화 실패한 정책 — 어떤 원천에서, 무슨 이유로 실패했는지

## 2. 범위

### In
- `IngestionRunLog` (수신 이벤트 단위) + `IngestionItemFailure` (개별 실패 항목) 엔티티
- 어드민 화면:
  - 일자별 신규/실패/중복 stacked bar
  - 원천(source)별 통계
  - 실패 정책 리스트 (재시도 가능한 액션)
  - 최근 수신 시점 (per source) — "마지막 24h 동안 수신 없는 source" 알람 후보

### Out
- 크롤링 트리거 / n8n 워크플로우 직접 제어 (어드민에서 수정은 안 함)
- 정책 데이터 직접 편집 (별도 컨텐츠 관리 spec 필요)

## 3. 데이터 모델 outline

```
IngestionRunLog (ingestion 모듈)
- id
- source: string (예: 'youth-center', 'gov24')
- received_count
- normalized_success_count
- normalized_failure_count
- duplicate_count
- received_at
- processed_at
- duration_ms

IngestionItemFailure
- id
- run_log_id (FK)
- source_item_id (외부 ID)
- raw_payload (JSON 또는 ref)
- failure_reason: enum (VALIDATION, PARSING, MAPPING, DEDUPLICATION_CONFLICT, OTHER)
- error_message
- created_at
```

> raw_payload 보관: 디버깅에 유용하지만 양 부담. 7일 후 hash 또는 외부 스토리지로 이전 검토.

## 4. 어드민 화면 outline

### 4.1 라우트
- `/admin/ingestion` — 메인
- `/admin/ingestion/failures/:failureId` — 실패 상세

### 4.2 메인 화면
- 상단 알람 영역: "마지막 24h 동안 수신 없는 source" 리스트 (있으면)
- KPI: 어제 신규 / 어제 실패 / 7일 평균 신규 / 7일 평균 실패율
- 차트: 일자별 stacked bar (신규 / 실패 / 중복) by source 색상
- 테이블: 원천별 — 마지막 수신 시각, 7일 합계, 실패율
- 실패 항목 리스트 (필터: 원천, 사유)

### 4.3 실패 상세
- raw_payload (JSON pretty print)
- 실패 사유 + 에러 메시지
- 액션: 재처리 (단, ingestion 도메인 트리거 필요) / 무시 / 수동 매핑(별도 화면)

## 5. 보관 정책

- 집계(`IngestionRunLog`): 무기한
- 개별 실패(`IngestionItemFailure`): 30일 (raw_payload는 7일)

## 6. 테스트 전략 outline

- 단위: 실패 사유 분류 로직, 중복 판정 로직
- 통합: 실제 ingestion 흐름 → 로그 적재
- 슬라이스: 어드민 컨트롤러
- 프론트: 알람 영역 / 차트 / 실패 리스트 placeholder + 필터

## 7. 의존성

- Spec 1 (admin foundation) 완료 ← 의존
- 다른 spec은 의존 안 함

## 8. 열린 질문 (구현 직전 brainstorming에서 결정)

- raw_payload 저장 위치 (DB JSONB vs S3 ref) — DB 부담 고려
- 재처리 액션 — 어드민에서 직접 트리거 vs ingestion 도메인 API 호출 (보안/책임)
- "마지막 수신 없음" 알람 임계 (24h vs source별 설정)
- 중복(duplicate) 판정 기준이 ingestion 도메인에서 이미 명확한지 확인 필요
- 차트 라이브러리 (Spec 2와 통일)

## 9. 변경 영향 범위 (예상)

- `ingestion/domain/model/IngestionRunLog`, `IngestionItemFailure` 엔티티
- `ingestion/application/service/*`에 적재 hook 추가 (현재는 단순 수신/정규화만 함)
- `admin/presentation/controller/AdminIngestionController`
- 프론트: `pages/admin/AdminIngestionPage`, `apis/admin.ingestion.api.ts`

---

## 부록: 시리즈 5개 spec 간 공통 사항

| 항목 | 결정 메모 |
|---|---|
| 인증/라우팅 | Spec 1에서 결정됨 (`/api/v1/admin/**`, `RequireAdmin`) |
| ReadModel 패턴 | admin 모듈은 조회만; 데이터는 각 도메인이 적재 |
| 차트 라이브러리 | Spec 2 brainstorming 시 결정 (이후 spec 동일 사용) |
| 보관 정책 | 항목별 다름 (위 각 spec § 보관 정책 참고) |
| 디자인 토큰 | Spec 1에서 적용된 다크 사이드바 + 브랜드 indigo |
