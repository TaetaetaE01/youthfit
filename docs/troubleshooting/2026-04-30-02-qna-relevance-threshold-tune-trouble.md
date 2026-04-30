# Q&A relevance 임계값 0.4 → 0.7 — 모든 질문이 NO_RELEVANT_CHUNK 로 떨어지던 문제

- 작성일: 2026-04-30
- 작성자: TaetaetaE01
- 관련 커밋: `d3b961a` (`chore(qna): relevance 임계값 0.4→0.7 + RAG 검색 distance 로깅`)
- 관련 PR: _(아직 없음, feat/qna-v0-ready 브랜치 작업 중)_
- 관련 모듈: `backend/qna`, `backend/rag`

## 한 줄 요약

> 운영 데이터(정책 7개, 374 청크, 한국어 OpenAI text-embedding-3-small)에서 cosine distance < 0.4
> 청크가 거의 안 잡혀 모든 질문이 `NO_RELEVANT_CHUNK` 거절로 떨어지던 문제. 임계값을 0.7 로
> 완화하고, RagSearchService 에 top-K distance INFO 로그를 심어 후속 finalize 가능하도록 함.

## 1. 상황 (Context)

- 작업: Q&A v0 출시 전 실데이터 검수.
- 증상: 정책 상세에서 어떤 질문을 던져도 응답이
  `해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.`
  로만 떨어짐 (`QnaFailedReason.NO_RELEVANT_CHUNK`).
- 데이터 규모: 정책 7개, 청크 374개, 임베딩 모델 OpenAI `text-embedding-3-small`,
  pgvector cosine distance.
- 영향: Q&A 의 핵심 가치인 "정책 원문 기반 답변"이 사실상 불능. 사용자에게 0% 적중.

## 2. 원인 (Root Cause)

- `application.yml` 의 `youthfit.qna.relevance-distance-threshold` 기본값이 `0.4`.
  이는 pgvector cosine distance ≤ 0.4 인 청크만 컨텍스트로 채택한다는 의미.
- 한국어 임베딩의 distance 분포가 영어 가이드라인과 다름. 실데이터 top-K 의 distance 가
  대체로 0.5~0.8 구간에 분포 → 임계값 0.4 에 걸리는 청크가 거의 없음.
- 결과적으로 검색 결과 자체는 있어도 "관련 있다고 인정"되는 청크가 0 개라 거절로 떨어짐.
- 관련 코드 경로:
  - `backend/.../qna/application/service/QnaService.java` — `NO_RELEVANT_CHUNK` 분기
  - `backend/.../rag/application/service/RagSearchService.java` — distance 필터링
  - `backend/src/main/resources/application.yml:75` — 임계값 설정

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. 기본 임계값을 0.4 → 0.7 로 완화 | 한 곳 변경. 즉시 검수·시연 가능. 추후 운영 데이터로 재조정 용이. | 한국어/모델 의존적 magic number 가 됨 → 로그·메모로 보완해야 함. |
| B. 임베딩 모델을 한국어 친화 모델로 교체 (`text-embedding-3-large` 또는 OSS 한국어 모델) | 분포 자체가 더 분리되어 임계값 의미가 살아남. | 비용/지연 증가, 인덱싱 전체 재실행 필요. v0 출시 직전 단계엔 부담. |
| C. 청크 분할 전략 변경 (헤딩 단위·문장 단위로 더 잘게) | recall 자체를 끌어올림. | 인덱싱 파이프라인 전체 재작업 + 재인덱싱. v0 범위 밖. |
| D. distance 절대값 대신 top-K relative ranking 으로 채택 | 모델/언어 의존성을 줄임. | 무관한 청크가 항상 채택되는 위험 (질문 자체가 정책과 무관할 때 거절 못 함). |
| E. 임계값을 환경변수만 두고 0.4 유지 + 운영자가 조정 | 코드 그대로. | 운영자가 즉시 조정할 수 있는 환경이 아직 없고 v0 출시일에는 사용자 첫 인상이 0% 적중이 됨. 부적절. |

## 4. 선택과 이유 (Decision)

- **채택한 대안: A (기본값 0.4 → 0.7) + 보조 작업으로 distance INFO 로깅 추가.**
- **결정의 핵심 근거**:
  1. v0 마일스톤은 "사용자가 일단 답을 받아볼 수 있어야" 하는 단계 — recall 우선, precision 은
     운영 데이터로 후속 조정.
  2. 0.7 은 검수 데이터 top-K 분포 관찰값(대다수 0.5~0.8) 의 중상단 — 무관한 청크가 새지 않도록
     하는 안전선.
  3. 0.7 이 영구 정답이 아님을 인정하고, **로그를 심어 운영 데이터로 finalize 할 수 있는 길을
     같이 열어둠**. (대안 D 의 risk 도 회피.)
- **트레이드오프로 받아들인 것**:
  - 한국어 + `text-embedding-3-small` 조합에 결합된 magic number 임계값.
  - 임베딩 모델이나 인덱싱 전략을 바꾸면 임계값을 다시 조정해야 함 → application.yml 에 그
    이유를 주석으로 남김.
- **가역성**: 매우 높음. yml 한 줄 + 환경변수 (`QNA_RELEVANT_DISTANCE_THRESHOLD`) 로 즉시 조정.
- **재검토 신호**:
  - 운영 로그의 distance 분포가 안정화되면 finalize 임계값 결정.
  - 임베딩 모델 교체 시 (대안 B) 임계값 의미가 달라지므로 동시 재조정.

## 5. 해결 (Solution)

- 변경 파일:
  - `backend/src/main/resources/application.yml` (line ~75)
    ```yaml
    # 변경 후
    # 한국어 임베딩 cosine distance. 더 엄격: 0.5~0.6, 더 관대: 0.8. RagSearchService 로그로 운영값 조정.
    relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.7}
    ```
  - `backend/.../rag/application/service/RagSearchService.java` (line 40~)
    ```java
    if (log.isInfoEnabled()) {
        String distanceSummary = similar.stream()
                .map(c -> String.format("%.3f", c.distance()))
                .toList()
                .toString();
        log.info("RAG 검색 결과: policyId={}, top{}={}", command.policyId(), similar.size(), distanceSummary);
    }
    ```
- 부수 효과: 인덱싱·캐시 재실행 불필요. yml 변경이라 재시작만 필요.

## 6. 검증 (Result)

- 재현 시나리오 (수정 후): 검수 정책 7개에 대해 정책별 대표 질문 1~2개씩 던져 모두 응답 생성
  성공 확인 (`status = COMPLETED`).
- 로그 확인:
  ```
  RAG 검색 결과: policyId=42, top5=[0.512, 0.591, 0.634, 0.701, 0.745]
  ```
  → 0.4 였다면 0건, 0.7 에서는 4건 채택. 임계값 결정 근거 로그로 잡힘.
- 회귀 위험:
  - 0.7 이 너무 관대하면 무관한 청크가 답변에 섞일 가능성 → 답변 품질 모니터링이 필요.
  - 운영 메트릭 도입은 후속(`docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md` §4
    참조).

## 7. 후속 / 미결 (Follow-ups)

- 운영 데이터 누적 후 임계값 finalize. 메트릭 `qna_relevance_distance{quantile=...}` 분포 도입은
  `qna v0 ready` spec §9 의 운영 견고성 묶음에 포함.
- 임베딩 모델/청크 전략 재검토 (대안 B/C) 는 v0 이후 별도 spec.
- 답변 품질 샘플링: 0.7 채택 후 무관 청크 섞임 비율을 사람이 표본 점검할 절차 — runbook 에 항목
  추가 검토.

## 8. 참고 (References)

- 관련 spec: `docs/superpowers/specs/2026-04-30-qna-v0-ready.md` §4.6 ("운영 데이터로 조정")
- 관련 운영 노트: `docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md` §4
- 임베딩 모델: OpenAI `text-embedding-3-small` (한국어), pgvector cosine distance
