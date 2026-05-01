# 의미 기반 Q&A 캐시 v1 — 의도(Intent) 기반 캐시 + Canonical Question

- **상태**: TODO (v1 후보, 시점 미정)
- **대상 모듈**: `backend/qna`, `backend/rag`
- **선행 작업**: v0 출시 완료 (`docs/superpowers/specs/2026-05-01-semantic-qna-cache-design.md`, `docs/superpowers/plans/2026-05-01-semantic-qna-cache.md`)

## 1. 배경

v0 의미 캐시는 출시되었지만 다음과 같은 한계가 관찰된다.

- 한국어 짧은 의문문(예: "재학생도 가능?", "재학생도 신청할 수 있나요?")에서 OpenAI `text-embedding-3-small` 임베딩 코사인 거리가 **같은 의미 질문 사이에서도 0.50 수준**으로 측정된다.
- v0 임계값(`distance ≤ 0.15`, similarity ≥ 0.85)에서는 사실상 paraphrase 가 의미 캐시에 히트하지 않는다.
- 정확 캐시(Redis)는 글자 단위 SHA-256 키이므로 표현이 조금만 달라도 미스된다.

결과: 의미가 같은 질문이 들어와도 LLM 호출이 매번 발생한다. v0 hit/miss 로그에서 `distance` 분포가 0.40~0.60 구간에 몰린다면 임계값만 완화하는 것은 위험하다 (의미가 살짝 다른 질문에 잘못된 답을 재사용할 가능성).

## 2. v1 제안 방향 — 의도 기반 캐시 + canonical question

핵심 아이디어: **사용자 질문을 임베딩 직전에 LLM 으로 정규화**해서 표현 차이를 흡수한다.

1. **의도 분류**: 사용자 질문을 정해진 의도 카테고리로 분류한다.
   - 예시 카테고리: `ELIGIBILITY` (지원 대상/자격), `BENEFIT` (혜택/지원 금액), `APPLICATION_METHOD` (신청 방법), `DURATION` (기간/마감), `DOCUMENTS` (제출 서류), `CONTACT` (문의처).
2. **canonical question 변환**: 의도별 템플릿으로 canonical question 을 생성한다.
   - 예: `ELIGIBILITY` → `"{정책명}의 지원 대상과 신청 자격은 무엇인가?"`
   - 예: `APPLICATION_METHOD` → `"{정책명}의 신청 방법과 절차는 어떻게 되는가?"`
3. **임베딩 + 캐시 키**: canonical question 을 임베딩하고, 캐시 조회·저장 시 다음 키로 묶는다.
   - 키: `(policy_id, source_hash, intent)` — DB 컬럼 필터로 적용
   - 같은 그룹 안에서만 vector similarity 계산 → 표현 차이가 거의 없으므로 distance 가 작게 나올 것으로 기대
4. **저장**: `qna_question_cache` 에 `intent VARCHAR(32) NOT NULL` 컬럼 추가.

흐름:

```
사용자 질문
  ├─ ① 정확 캐시 조회 (기존)
  ├─ ② 의도 분류 LLM 호출 → intent (+ confidence)
  │     confidence < threshold → 캐시 미스 처리(안전 폴백)
  ├─ ③ canonical question 생성 (템플릿 적용)
  ├─ ④ canonical question 임베딩 1회
  ├─ ⑤ 의미 캐시 조회: WHERE policy_id=? AND source_hash=? AND intent=? ORDER BY <=>
  ├─ ⑥ 미스 시 RAG → LLM (사용자 원문 질문 그대로 전달)
  └─ ⑦ 저장: (policy_id, source_hash, intent, canonical_question, embedding, answer)
```

## 3. 검증 가설

- 의도 분류기 정확도(top-1) ≥ 90%
- 같은 의도 그룹 안 paraphrase 거리 ≤ 0.20
- 운영 의미 캐시 전체 hit율 80% 이상

위 셋이 모두 충족되어야 v1 도입의 ROI 가 정당화된다.

## 4. 알려진 위험

- **추가 LLM 호출**: 의도 분류 LLM 1회 추가(예상 ~$0.00002, 답변 LLM 1회의 약 1/35). v0 가 약속한 "추가 LLM 0" 원칙은 깨진다. 비로그인 핫패스에 영향.
- **의도 오분류 시 명백한 오답 재사용**: v0 의 "거리 임계값 미스 → 안전 폴백" 과 다른 실패 모드. ELIGIBILITY 로 분류해야 할 질문이 BENEFIT 으로 분류되면 다른 답변을 자신 있게 반환한다.
- **의도 taxonomy 유지보수 부담**: 회색 케이스("이 정책 어떤가요?"), 새 카테고리 발생, 카테고리 통합/분할 시 캐시 무효화.
- **미스 경로 latency +500ms**: 의도 분류 LLM 호출이 직렬로 추가됨. 정확 캐시 미스 시 사용자 체감 지연 증가.
- **스키마 변경**: `intent` 컬럼 추가 + 기존 캐시 무효화/마이그레이션 필요.
- **canonical question 템플릿 품질**: 정책명에 비교 가능한 형용사가 들어가는 경우 (예: "청년 도약계좌", "청년 내일채움공제") 템플릿이 그대로 흡수되어 서로 다른 정책의 같은 의도가 거리상 가까워질 위험. policy_id 필터로 1차 차단되지만 source_hash 까지 동일한 변경 직후 케이스 검증 필요.

## 5. 그라쥬에이션 기준 (v0 → v1 검토 트리거)

다음 중 하나라도 충족되어야 v1 검토를 시작한다.

- 운영 의미 캐시 hit율 < 20% (예상되는 수치) — 임계값을 0.20 까지 완화해도 개선이 작을 때.
- v0 hit/miss 로그(`distance` 분포)에서 같은 의미 질문이 임계값을 자주 넘는 패턴이 누적된다 (수동 샘플링 100건 중 30건 이상).
- 사용자 LLM 호출 비용이 이 영역에서 의미 있게 발생 (월 $X 이상, 운영 후 결정).
- 사용자 피드백/CS 에서 "비슷한 질문에 매번 답변이 느리다" 가 반복 보고됨.

위 어느 것도 충족되지 않으면 v1 도입은 보류한다 (premature optimization).

## 6. 사전 작업 (v1 진입 시 필요)

- **운영 데이터 수집**: v0 hit/miss 로그 누적 분석으로 의도 분포 측정 (어떤 의도가 자주 나오는지, 빈 분류가 얼마나 많은지).
- **분류기 프롬프트**: 한국어 few-shot 프롬프트 작성. 회색 케이스("어떤 정책인가요?") → `OTHER` 처리 정책 명시.
- **canonical question 템플릿 카탈로그**: 의도별 템플릿 1~2개씩, 정책명 슬롯 포함.
- **classifier confidence threshold**: 분류 confidence 가 임계값보다 낮으면 의미 캐시 미스 처리하여 안전 폴백.
- **A/B 또는 shadow 모드**: v0 와 병렬로 돌려서 hit율·정확성·latency 비교 후 전환.

## 7. v0 와의 호환성

- `qna_question_cache` 테이블에 `intent VARCHAR(32)` 컬럼을 NULLable 로 추가하면 기존 행과 공존 가능. v1 활성화 시 `intent IS NOT NULL` 행만 조회.
- v0 의 source_hash 컬럼은 v1 에서도 그대로 사용 — 정책 본문 갱신 시 캐시 무효화는 source_hash 비교만으로 충분.
- v0 의 `QnaCacheInvalidator.invalidatePolicy(policyId)` 도 그대로 유효 (정책 단위 비움).

## 8. 참고

- v0 spec: `docs/superpowers/specs/2026-05-01-semantic-qna-cache-design.md`
- v0 plan: `docs/superpowers/plans/2026-05-01-semantic-qna-cache.md`
- v0 출시 직전 추가된 source_hash 스냅샷·로깅 강화 작업 (이 문서와 같은 커밋)
