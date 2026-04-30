# Q&A 첨부 라벨 fix 후에도 옛 형식이 보이던 문제 — Redis 정확 매칭 캐시 히트

- 작성일: 2026-04-30
- 작성자: TaetaetaE01
- 관련 커밋: `95b1aac` (`feat(qna): 출처 attachmentLabel 을 첨부 실제 이름(확장자 제거)으로 표시`)
- 관련 PR: #53
- 관련 모듈: `backend/qna` (캐시 어댑터), Redis 운영

## 한 줄 요약

> `QnaService.buildSources` 의 attachmentLabel 매핑을 "첨부 #57" → 실제 첨부명으로
> 바꾸고 docker 재빌드까지 완료했지만, 사용자 화면에는 여전히 "첨부 #57" 이 보임.
> 원인은 Redis 정확 매칭 캐시(TTL 24h) 가 직전 답변+sources 를 그대로 들고 있어
> **새 코드의 buildSources 가 호출조차 안 되고 캐시된 옛 sources 가 그대로 반환**되던 것.
> 운영 노트의 무효화 명령으로 키를 제거해 해결.

## 1. 상황 (Context)

- 작업: Q&A 출처 라벨 사용자 친화 개선. `첨부 #57` 같은 ID 라벨이 사용자에게 의미가
  없으므로 `PolicyAttachment.name` 으로 대체 (확장자 제거).
- 적용 흐름:
  1. `QnaService` 에 `PolicyAttachmentRepository` 주입
  2. `buildSources` 가 정책의 모든 첨부를 한 번 조회 후 `Map<id, name>` 으로 lookup
  3. 단위 테스트 통과 (399/399), docker 재빌드 + 컨테이너 재시작
- 사용자 검증 결과: 같은 질문 재시도 → **여전히 "첨부 #57 p.121-122" 그대로 표시**.
- 영향: fix 가 적용된 척 보이지만 실제로는 캐시 히트 경로로 우회되어 시각적 회귀처럼 보임.
  운영자가 fix 가 안 됐다고 오인할 위험 큼.

## 2. 원인 (Root Cause)

- `QnaService.processQuestion` 흐름의 첫 분기:
  ```java
  Optional<CachedAnswer> cached = qnaAnswerCache.get(policyId, question);
  if (cached.isPresent()) {
      sendCachedAnswer(emitter, cached.get(), historyId);  // 캐시된 sources 그대로 emit
      return;
  }
  ```
- 직전에 같은 질문을 한 번 했고 그때 답변+sources(옛 형식) 가 Redis 에 캐시됨
  (TTL 24h, 키: `qna:answer:{policyId}:{sha256(normalizedQuestion)}`).
- 같은 정책 + 같은 정규화 질문으로 재호출 → 캐시 히트 → `buildSources` 호출 자체 skip →
  옛 라벨 그대로.
- 캐시는 의도된 비용 방어 장치 (PRD §"비용 방어"). 즉 **버그가 아니라 정책 변경 후 데이터를
  무효화하는 절차가 누락된 것**.
- 확인:
  ```bash
  docker exec youthfit-redis redis-cli KEYS "qna:answer:*"
  # qna:answer:7:1adcb430...
  # qna:answer:7:3ff55f51...
  ```
  정책 7 의 캐시 2개 hit.

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. 운영 노트의 무효화 명령으로 영향 받은 정책의 캐시 키만 삭제 | 표적 무효화. 다른 정책 캐시는 보존. 운영 노트가 이미 명시. | _(채택)_ |
| B. Redis 전체 FLUSHDB | 한 줄. | 다른 모듈/세션 캐시 동반 삭제 — 부수 피해. dev 한정이면 OK 지만 prod 에서 동일 패턴 적용 못함. |
| C. `CachedAnswer` 에 schema version 필드 추가 + buildSources 변경 시 version bump → 옛 버전 캐시는 자동 무시 | 미래의 schema 변경에서도 자동 무효화. | 추가 인프라(version 필드 + check 로직). v0 범위 외. 단 후속 spec 으로 가치 있음. |
| D. 캐시 TTL 을 더 짧게 (예: 1h) | "코드 fix 후 1 시간만 기다리면 자연 만료" 함정 회피. | 비용 방어 효과 감소. 캐시는 PRD 가 명시한 항목이라 TTL 단축은 trade-off. |

## 4. 선택과 이유 (Decision)

- **채택한 대안: A. 영향 받은 정책 키만 표적 삭제** (이미 운영 노트가 정의한 절차 그대로).
- **결정의 핵심 근거**:
  1. 캐시는 의도된 동작이고, fix 직후 한 번만 무효화하면 끝나는 일회성 운영 작업.
  2. 운영 노트 `2026-04-30-qna-v0-ready-runbook.md` §2 에 명령이 이미 있음 — 이번 사건이
     운영 노트의 첫 사용 사례가 됨. 절차의 효력 검증.
  3. 다른 정책 캐시는 그대로 두므로 비용 방어 효과 손실 최소.
- **트레이드오프로 받아들인 것**:
  - 코드 변경자가 매번 "이 변경이 캐시된 응답에 영향이 있는가?" 를 의식해야 함.
    (C 대안의 schema version 이 들어오면 자동화 가능 — 후속 후보.)
- **가역성**: 매우 높음. 캐시 키는 다음 호출에서 자연 재생성.
- **재검토 신호**: 같은 사건이 반복되면 (1) C 대안 도입, (2) buildSources 등 응답 schema
  변경 시 자동으로 캐시 키 prefix 를 변경하는 빌드 단계 추가 검토.

## 5. 해결 (Solution)

- 적용 명령:
  ```bash
  docker exec youthfit-redis redis-cli DEL \
      qna:answer:7:1adcb430d94218d524ebb7af3571450900b9c6a97ce1f759b887bbf100c297ce \
      qna:answer:7:3ff55f5151a2b6f58d331452ceb5a0fa914e671d4f9f7b066cbb93b2e6e1de57
  # 결과: 2 (삭제된 키 수)
  ```
- 일반화된 패턴 (운영 노트 §2):
  ```bash
  docker exec youthfit-redis redis-cli --scan --pattern "qna:answer:<policyId>:*" \
    | xargs docker exec -i youthfit-redis redis-cli DEL
  ```
- 부수 효과: 다음 동일 질문 호출 1회는 캐시 미스 → LLM 1회 호출 비용 발생 (의도된 trade-off).

## 6. 검증 (Result)

- 무효화 직후 같은 질문 재시도 → 새 코드의 `buildSources` 가 호출되어 정책 7 의 첨부
  실제 이름 (`2026년_자활사업안내(II) 자산형성지원 통장사업_안내`) 이 라벨로 표시됨 확인.
- DB `qna_history` 에 새 row 가 status=COMPLETED 로 INSERT — 캐시 미스 경로 동작 확인.
- 회귀 위험: 매우 낮음. 캐시는 다음 호출에서 정상 형식으로 재생성.
- 모니터링 포인트:
  - 운영 메트릭 `qna_cache_hit_total` / `qna_cache_miss_total` (도입은 v0+ 후속) 으로
    "schema 변경 직후 hit 비율이 비정상적으로 높음" 같은 신호 포착 가능.

## 7. 후속 / 미결 (Follow-ups)

- (자동화 후보) `CachedAnswer` 에 `schemaVersion` 필드 추가 + 키 prefix 에 포함 →
  schema 변경 시 자동 무효화. spec §9 후속 작업 후보로 등록.
- (운영) 응답 schema 가 변경되는 PR 의 description 체크리스트에 "Redis 캐시 무효화 필요?"
  항목 추가 검토.
- (관찰) 동일 사건이 다른 모듈의 응답 캐시에서도 발생 가능 — 캐시 사용 모듈 sweep 시점에
  점검.

## 8. 참고 (References)

- 운영 노트: `docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md` §2
  (Redis 캐시 무효화)
- 같은 세션에서 함께 다룬 빈 박스 디버깅 흐름:
  - `2026-04-30-01-qna-securitycontext-virtual-thread-trouble.md`
  - `2026-04-30-03-qna-sse-frame-mismatch-trouble.md`
- spec §4.4 — 정확 매칭 캐시 정책 (TTL 24h, 정규화 키)
