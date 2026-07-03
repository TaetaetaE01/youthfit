# 임베딩 모델 교체 실험 (1단계: text-embedding-3-large) 설계

- **날짜**: 2026-07-03
- **이슈**: #167 (선행 완료: #162 평가셋, #163·#164 우회책 기각, #170·#173 정리)
- **상태**: 설계 — 구현 계획 대기

## 1. 문제와 목표

text-embedding-3-small 은 한국어에서 관련/무관 청크의 distance 분포를 못 가른다.
평가셋 실측: 정답 청크 distance 평균 0.458 vs 비정답 0.479 — **갭 0.02**,
NEGATIVE 오탐률 0.737 (QnA threshold 0.78 기준). 우회책 두 가지(하이브리드 #163,
쿼리 재작성 #164)는 실험으로 기각됐다. 남은 레버는 임베딩 모델 자체다.

**목표**: 가장 마찰이 적은 후보(text-embedding-3-large, dimensions=1536 축소)를
같은 평가셋으로 측정해 교체 여부를 판정한다. 판정 기준 미달 시에만 2단계(한국어
특화 외부 모델)를 검토한다.

## 2. 왜 3-large 부터인가 (단계적 접근)

| 후보 | 마찰 | 이번 단계 |
|---|---|---|
| text-embedding-3-large @1536d | **스키마·코드 무변경** (env 교체만, dimensions API 지원, 가격표 등록됨) | ✅ 1단계 |
| Upstage Solar embedding | 어댑터 신규(엔드포인트 하드코딩 해제)·API 키·4096d 스키마 마이그레이션 2테이블 | 2단계 (1단계 미달 시) |
| BGE-M3 셀프호스팅 | 서빙 인프라 필요 — AWS destroy 상태·운영 단순성 기조와 불일치 | 2단계 |
| 3-large @3072d (전체 차원) | `vector(1536)` 하드코딩 2곳(policy_document·qna_question_cache) 마이그레이션 | 2단계 |

비용: 3-large 는 3-small 의 6.5배(0.13/1M tokens)지만 절대액 미미 —
재인덱싱 1,178청크 + 질문 73건 재임베딩에 수백 원 수준.

## 3. 코드 변경 (eval 모듈 + ingestion 소폭)

### 3.0 `AttachmentReindexService.reindexWithoutEvents(policyId)` 추가 (ingestion)

첨부 청크가 전체의 44%(로컬 521/1178)라 재인덱싱은 body 만이 아니라 기존
`AttachmentReindexService.reindex` 의 content 조립(본문+선별 첨부+enrichment)을
그대로 타야 한다. 그러나 기존 `reindex` 는 `PolicyAttachmentReindexedEvent` 를
발행해 가이드·룰 LLM 재생성 리스너를 깨운다 — 임베딩 실험에 부적절한 부수효과.
내부 로직을 공유하되 **이벤트를 발행하지 않는 변형**을 추가한다 (동작 변경 없는
추출 리팩토링 + 오버로드). 첨부 LLM 게이트는 이미 판정된 첨부를 캐시 재사용하므로
추가 LLM 호출 없음.

### 3.1 `--eval.mode=reindex` 신설

청크 보유 전 정책을 순회하며 `PolicyDocumentRepository.deleteByPolicyId(id)` →
`AttachmentReindexService.reindexWithoutEvents(id)` 를 호출한다 (정책당 하나의
트랜잭션). **삭제 후 인덱싱이므로 source_hash 게이트(내용 기반 해시라 모델 교체를
감지 못함)를 자연 우회한다.**

- generate 모드와 동일한 비용 방어: 기본 dry-run(대상 정책 수·예상 임베딩 청크 수 출력),
  `--eval.confirm=true` 일 때만 실행
- `--eval.policy-ids=…` 로 대상 제한 가능
- 정책 단위 실패는 로그 후 계속, 마지막에 실패 목록 출력 (기존 generate 패턴)
- 실행 후 요약: 처리 정책 수·생성 청크 수·소요 시간

### 3.2 임베딩 캐시 라벨을 실제 호출 모델로

현재 `EvalRunner` 는 evalset 파일의 `embeddingModel` 필드를 캐시 파일 라벨로 쓴다.
이를 **실제 호출 모델**(`OpenAiEmbeddingProperties.getModel()`)로 바꾼다 — env 로
모델을 바꾸면 캐시 파일(`embeddings-<model>.json`)이 자동 분리된다.
evalset 의 `embeddingModel` 필드는 문서화 용도로 유지하되, 실제 모델과 불일치하면
경고 로그를 남긴다 (평가셋이 어떤 모델 기준으로 만들어졌는지 추적).

## 4. 실험 절차 (코드 밖 — 러너 실행)

1. **전환**: `OPENAI_EMBEDDING_MODEL=text-embedding-3-large` (dimensions 는 1536 유지
   — 스키마 무변경 전제)
2. **재인덱싱**: reindex 모드 dry-run → confirm 실행 (~1,178청크 재임베딩)
3. **의미 캐시 무효화**: `TRUNCATE qna_question_cache;` (로컬 dev 데이터 —
   3-small 벡터가 남으면 거리 계산이 오염됨)
4. **측정**: 같은 평가셋으로 `--eval.mode=run --eval.scenarios=baseline` 실행 —
   질문 임베딩은 새 모델로 재호출(캐시 라벨 분리로 자동), 리포트 라벨
   `3large-experiment`
5. **비교**: 3-small baseline 리포트(20260703-100826)와 지표 대조
6. **되돌리기**(판정 미달 시): env 원복 → reindex 재실행 → 의미 캐시 truncate

## 5. 판정 기준

**교체 추진 (전부 충족)**:
- 정답-비정답 **distance 갭 ≥ 0.10** (현 0.02) — 근본 문제의 직접 지표
- **NEGATIVE 오탐률 ≤ 0.40** (현 0.737, 동일 threshold 0.78 로 비교)
- recall@1·MRR 이 baseline 대비 **0.02 이상 열화 없음** (현 0.796 / 0.853)

**충족 시 후속** (이번 스펙 범위 밖):
- prod 마이그레이션 계획: env 기본값 변경(application.yml), QnA
  relevance-distance-threshold 재조정(분포가 바뀌므로), 배포 시 재인덱싱·의미 캐시
  무효화 런북, 비용 영향 기록
- **미달 시 후속**: 2단계 검토 이슈 생성 (Solar·BGE-M3·3072d — 어댑터/스키마
  마이그레이션 포함)

## 6. 리스크

- 3-large @1536 축소는 전체 3072d 대비 성능이 낮을 수 있음 — 미달 시 3072d 실험은
  2단계로 (스키마 마이그레이션 필요)
- threshold 민감도: 분포가 전체적으로 이동하면 오탐률 비교가 왜곡될 수 있음 —
  리포트의 distance 분포(정답/비정답 평균)를 함께 보고 판정
- 로컬 DB 재인덱싱은 파괴적이지만 dev 시드 데이터고 되돌리기 절차(§4-6)가 있음

## 7. 테스트 전략

- reindex 모드: dry-run 이 LLM/임베딩을 호출하지 않는지·delete→index 순서 단위 테스트
  (mock), 캐시 라벨 변경은 기존 `EvalRunnerTest` 확장
- 실험 자체가 e2e 검증 (73케이스 OK 여부로 재인덱싱 정합 확인 — STALE/NO_CHUNKS 감지)

## 8. 범위 밖

- Solar·BGE-M3 어댑터, 3072d 스키마 마이그레이션 (2단계)
- prod 적용 (판정 후 별도)
- eval 러너의 프로바이더 추상화 확장
