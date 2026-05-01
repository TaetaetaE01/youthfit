# Q&A 품질 개선 — 출처 정합성 + 메타 질문 대응

- **상태**: 진행 (사이클 진입)
- **작성일**: 2026-05-01
- **대상 모듈**: `backend/qna`
- **선행 작업**: v0 의미 캐시 출시 (`DONE` 예정 — `2026-05-01-semantic-qna-cache-design.md`, PR #55 머지)
- **이관 출처**: `docs/superpowers/specs/v1-qna-quality-improvements.md` (본 spec 으로 흡수)

## 1. 배경

v0 의미 캐시 도커 검증 단계에서 발견된 **두 가지 기존 Q&A 품질 이슈**를 한 사이클로 해결한다. 두 이슈 모두 의미 캐시 자체와는 무관하며, 같은 모듈(`qna`) · 같은 LLM 클라이언트(`OpenAiQnaClient`) · 같은 테스트(`QnaServiceTest`) 를 건드리므로 한 PR 로 묶는다.

### 이슈 1 — "관련 내용 없음" 답변에도 출처가 표시됨

**현상**: 다음 응답이 나오는데 sources 박스에 청크 5개가 그대로 표시됨.

> 해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.

**근본 원인**:

- `application.yml` 의 `relevance-distance-threshold` 기본값이 **0.7** 로 매우 느슨함.
- RAG 검색 결과 top5 의 cosine distance 가 0.65~0.69 정도면 모두 통과.
- 통과한 청크들이 LLM 컨텍스트로 전달되지만 실제 답이 없는 내용.
- LLM 이 "관련 내용 없음" 으로 응답하지만, `QnaService` 는 이를 알 수 없어 LLM 에 준 청크를 그대로 sources 로 스트리밍.

**모순의 본질**: sources 와 답변이 독립적으로 결정된다.

- sources = "질문 임베딩과 가까운 후보 청크" (벡터 거리 기준)
- 답변 = LLM 이 "받은 청크로 답이 가능한가" 판단

임베딩 거리가 가깝다 ≠ 답에 필요한 정보가 그 청크에 있다. 따라서 모순이 발생.

### 이슈 2 — 메타 질문 처리 불가

**현상**: "이 정책 뭐야?", "어디 신청해?", "언제까지?" 같은 메타 질문에 LLM 이 "찾을 수 없다" 응답.

**근본 원인**:

- `OpenAiQnaClient.SYSTEM_PROMPT` 가 "반드시 제공된 컨텍스트에 근거하여 답변" 으로 강하게 제약.
- user message 에는 `정책명: {title}` + `정책 원문 컨텍스트: {chunks}` 만 들어감.
- 청크는 세부 절차 / 조건 위주라 정책 개요·범주·운영 정보가 부족.
- LLM 은 정책명 외에 답할 근거가 없어 "정보 없음" 으로 응답 → Q&A 실용성 저하.

## 2. 결정 로그 (브레인스토밍)

| # | 결정 포인트 | 채택안 | 근거 |
|---|---|---|---|
| 1 | 두 이슈 사이클 분리 여부 | 한 사이클 / PR 1개 | 같은 모듈·LLM 클라이언트·테스트 파일 변경. 사용자 체감 개선이 함께 가야 의미 있음. |
| 2 | LLM 에 넘길 메타 필드 범위 | 9필드 (운영 정보 포함) | 메타 질문 다양성("언제까지", "어디서") 커버. A→B 격상 비용은 호출당 ~1.2e-5 달러로 무시 가능. |
| 3 | LLM 응답 안전망 (이슈 1) | threshold 변경만 (안전망 없음) | 케이스 2(애매한 청크) 가 대부분이라는 가설. 케이스 3(관련 청크 + LLM "모른다") 잔존은 운영 데이터로 빈도 측정 후 후속 결정. YAGNI. |
| 4 | LLM 클라이언트 시그니처 변경 방식 (이슈 2) | `PolicyMetadata` record 신규 매개변수 | 메타와 RAG 청크가 의미적으로 다른 출처. 분리해두면 향후 정밀 인용 도입 시 sources 처리 로직만 청크 측에서 손보면 됨. |
| 5 | 시스템 프롬프트 변경 강도 | 명시적 우선순위 ("본문 우선, 메타 보강") | 사용자가 메타 질문과 세부 질문을 자유롭게 섞을 텐데, 우선순위 한 줄로 LLM 이 적절히 가르도록. |

## 3. 변경 범위 요약

| 영역 | 변경 |
|---|---|
| `application.yml` | `relevance-distance-threshold` 0.7 → **0.5** (env var 로 운영 후 0.45~0.55 튜닝 가능) |
| `qna.application.dto.command` | **신설** `PolicyMetadata` record + `from(Policy)` 정적 팩토리 |
| `qna.application.port` | `QnaLlmProvider.generateAnswer` 시그니처에 `PolicyMetadata` 매개변수 추가 |
| `qna.application.service` | `QnaService.processQuestion` 에서 `Policy` → `PolicyMetadata` 매핑 후 LLM 호출 |
| `qna.infrastructure.external` | `OpenAiQnaClient` user message 빌더에 메타 블록 추가, 시스템 프롬프트 변경 |
| 도메인 변경 | `Policy` 엔티티는 손대지 않음 (메타 필드 모두 이미 존재) |
| 테스트 | `QnaServiceTest`, 가능하면 `OpenAiQnaClientTest` 에 시나리오 추가 |

## 4. 이슈 1 — relevance threshold 조이기

### 4.1 변경 내용

`application.yml` 기본값만 변경. 코드 변경 없음.

```yml
qna:
  # 한국어 임베딩 cosine distance. 더 엄격: 0.45~0.5, 더 관대: 0.55~0.6.
  relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.5}
```

### 4.2 근거

0.65~0.69 의 애매한 청크가 LLM 까지 가서 "모른다" 답변을 유도하는 게 케이스 2 의 원인. threshold 0.5 로 조이면 케이스 2 가 RAG 단계에서 차단됨 → LLM 이 컨텍스트를 받으면 답할 수 있는 케이스만 도달.

부수 효과:

- 미관련 LLM 호출이 줄어 비용 절감.
- 진짜 관련 있는 청크만 sources 에 노출됨.
- 너무 빡빡하면 정상 질문도 거절될 수 있어 운영 후 0.45~0.55 사이 튜닝 가능 (env var 만 바꾸면 됨, 코드 변경 / 재배포 불필요).

### 4.3 비범위

**케이스 3** (관련 청크인데 LLM 이 답 못 추출 → "모른다" + sources 표시) 은 본 사이클에서 다루지 않음. 운영 데이터로 빈도 측정 후 후속 사이클에서 검토 (§ 9 참고).

## 5. 이슈 2 — 정책 메타데이터 user message 포함

### 5.1 신설: `PolicyMetadata` record

위치: `qna.application.dto.command.PolicyMetadata`

```java
public record PolicyMetadata(
    String category,        // Policy.category enum.name() (예: "EMPLOYMENT", "HOUSING")
    String summary,
    String supportTarget,
    String supportContent,
    String organization,
    String contact,
    LocalDate applyStart,
    LocalDate applyEnd,
    String provideType
) {
    public static PolicyMetadata from(Policy policy) {
        return new PolicyMetadata(
            policy.getCategory() == null ? null : policy.getCategory().name(),
            policy.getSummary(),
            policy.getSupportTarget(),
            policy.getSupportContent(),
            policy.getOrganization(),
            policy.getContact(),
            policy.getApplyStart(),
            policy.getApplyEnd(),
            policy.getProvideType()
        );
    }
}
```

총 9필드. `Policy` 엔티티에 모두 존재하므로 ingestion / 도메인 변경 불필요.

### 5.2 매핑 책임

`QnaService.processQuestion` 내부에서 `Policy` 엔티티로부터 `PolicyMetadata` 매핑 후 LLM 포트 호출:

```java
PolicyMetadata metadata = PolicyMetadata.from(policy);
qnaLlmProvider.generateAnswer(policyTitle, metadata, context, question, chunkConsumer);
```

도메인 → application DTO 매핑이므로 application 레이어에 둠. `Policy` 도메인 모델 자체가 application 레이어로 노출되는 부분은 기존 패턴(다른 서비스에서 `Policy` 를 직접 사용) 을 따른다.

### 5.3 user message 포맷 (`OpenAiQnaClient`)

```
정책명: {title}

정책 메타데이터:
- 분야: {category}
- 요약: {summary}
- 지원 대상: {supportTarget}
- 지원 내용: {supportContent}
- 운영 기관: {organization}
- 문의처: {contact}
- 신청 기간: {applyStart} ~ {applyEnd}
- 지급 방식: {provideType}

정책 본문 컨텍스트:
{ragChunks}

질문: {question}
```

**`null` 필드 처리**: 라인 자체를 생략 (LLM 이 "미상" 같은 placeholder 를 답변에 그대로 노출하는 사고 방지).

**날짜 포맷**: `LocalDate.toString()` 의 ISO-8601 (`2026-05-01`). 별도 한국어 변환 불필요.

## 6. 시스템 프롬프트 변경

### 6.1 변경 후

```
당신은 청년 정책 Q&A 전문가입니다.
사용자가 특정 정책에 대해 질문하면, 제공된 정책 메타데이터와 본문 컨텍스트에 근거하여 답변하세요.

규칙:
- 본문 컨텍스트에 답이 있으면 본문을 우선 사용하세요.
- 본문에 답이 없으면 정책 메타데이터로 보강하세요.
- 메타데이터와 본문 어느 쪽에도 없는 내용을 지어내지 마세요.
- 메타데이터와 본문 모두에 답이 없으면 "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다."라고 답변하세요.
- 쉬운 한국어로 답변하세요.
- 답변은 간결하고 핵심적으로 작성하세요.
```

### 6.2 의도

- 본문 우선 → 디테일한 자격·금액·절차 질문에 본문 청크의 정확한 답 사용 (기존 동작 유지).
- 메타로 보강 → 메타 질문(이게 뭐야, 누가 받아, 어디 신청해, 언제까지) 을 자연스럽게 처리.
- 양쪽 모두에 없으면 fallback → 기존 fallback 메시지 그대로 유지하여 케이스 3 의 답변 톤 보존.

## 7. sources 동작

이번 사이클에서 sources 결정 로직은 **변경하지 않는다**.

- 메타데이터는 sources 에 포함하지 않음 (정형 필드라 인용 단위가 아님).
- sources 는 기존대로 RAG 청크만 (단, threshold 0.5 통과한 것만).
- 메타 질문에서도 본문 청크가 threshold 를 통과했으면 sources 에 표시됨 — LLM 이 메타로만 답했더라도. 이 경우 사용자 입장에서 "메타 답변 + 본문 출처" 가 같이 보일 수 있는데, 본 사이클에선 의도된 동작으로 둠 (본문 청크가 메타와 함께 답변 형성에 기여했을 가능성을 인정). 정밀 인용 도입 전까지는 허용.
- 케이스 3 잔존 시 메타데이터 자체는 sources 에 안 나오므로 적어도 메타 답변에서 "메타 답변 본문 + 메타 출처" 같은 출처 모순은 구조적으로 발생하지 않음.

정밀 인용(sources 를 LLM 이 실제 사용한 청크만으로 좁히기) 은 v1+ 후보로 분리 (§ 9).

## 8. 영향 범위 (파일별)

| 파일 | 변경 |
|---|---|
| `backend/src/main/resources/application.yml` | `relevance-distance-threshold` 기본값 0.7 → 0.5 |
| `backend/src/main/java/com/youthfit/qna/application/dto/command/PolicyMetadata.java` | **신설** record + `from(Policy)` |
| `backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java` | `generateAnswer` 시그니처에 `PolicyMetadata` 추가 |
| `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` | `Policy` 조회 후 `PolicyMetadata` 매핑, LLM 호출 시 전달 |
| `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` | user message 빌더 변경, system prompt 변경 |
| `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java` | 메타 질문 / 미관련 청크 / 메타데이터 매핑 시나리오 추가 |
| `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientTest.java` (있으면) | user message 포맷 검증 |

## 9. 비범위 / 후속 / 미결

이번 사이클에서 **다루지 않는** 항목 — 트리거 조건이 충족되면 별도 사이클로.

- **케이스 3 처리** (관련 청크인데 LLM 이 답 못 추출 → "모른다" + sources 표시): A 옵션 결정대로 운영 데이터 누적 후 빈도 측정. 빈도가 높으면 LLM 응답 패턴 매칭(B) 또는 NO_ANSWER 토큰 합의(C) 를 추가하는 후속 사이클.
- **정밀 인용** (LLM 답변에 실제 사용된 청크만 sources 로 좁히기): 시스템 프롬프트에 "[1][2] 형태로 인용하세요" 지시 + 답변 텍스트에서 인용 추출 후 sources 필터링. 변경 범위 큼. v1+ 후보.
- **질문 유형 사전 분기** (메타 질문 vs 세부 질문 별도 프롬프트): 의미 캐시 v1 (intent 기반, `specs/v1-semantic-cache-intent-based.md`) 와 함께 가는 게 자연스러움. 본 사이클에선 시스템 프롬프트 우선순위 룰로 충분.
- **`Policy` 엔티티 신규 메타 필드 추가**: 9필드 모두 이미 존재. 변경 없음.

## 10. 위험·롤백

### 10.1 위험

| 위험 | 완화 |
|---|---|
| threshold 0.5 가 너무 엄격 → 정상 질문도 "관련 내용 없음" 으로 거절 | env var `QNA_RELEVANCE_DISTANCE_THRESHOLD` 즉시 0.55, 0.6 으로 완화 (코드 변경·재배포 불필요) |
| 메타 블록으로 user message 토큰 호출당 ~580 토큰 증가 | gpt-4o-mini 기준 30k 호출/월에 +$2.6 수준. 비용 영향 미미 |
| 시스템 프롬프트 변경으로 기존 세부 질문에 회귀 | 수동 검증으로 사전 점검 (§ 11.3) |
| 메타데이터의 `summary` 가 너무 짧아 LLM 이 메타 답변에서 정확성 손실 | 본문 청크가 우선이므로 세부 질문에는 영향 적음. 메타 답변은 본래 짧은 게 자연스러움 |
| `Policy` 엔티티 메타 필드에 `null` 이 많으면 메타 블록이 빈약해져 메타 질문 답변 품질 저하 | 운영 데이터로 메타 필드 충실도 모니터링. ingestion 측 보강은 별도 작업 |

### 10.2 롤백

- 단일 PR 이므로 PR revert 또는 커밋 revert 로 즉시 원복.
- threshold 만 따로 되돌리고 싶으면 env var `QNA_RELEVANCE_DISTANCE_THRESHOLD=0.7` 로 재설정 후 재배포 (코드 revert 불필요).

## 11. 테스트 / 검증

### 11.1 단위 테스트 (`QnaServiceTest`)

- 메타 질문 시나리오: "이 정책 뭐야?" 입력 → `qnaLlmProvider.generateAnswer` 가 `PolicyMetadata` 인자를 받음 검증 (Mockito argument capture).
- 미관련 청크 시나리오: 모든 청크 distance > 0.5 → RAG 결과 비어 LLM 호출 없이 fallback 메시지 + 빈 sources.
- `PolicyMetadata.from(Policy)` 9필드 매핑 정확성 (null 처리 포함, category enum.name() 변환).

### 11.2 단위 테스트 (`OpenAiQnaClientTest`, 신설 또는 보강)

- user message 포맷에 9필드 메타 블록 + 본문 컨텍스트 + 질문 모두 포함.
- `null` 필드 라인 생략 검증 (예: `contact = null` 이면 "문의처:" 라인 없음).
- 시스템 프롬프트가 명시적 우선순위 규칙 포함.

### 11.3 수동 검증 (도커 환경)

- **메타 질문 변형 5~10개**: "이 정책 뭐야?", "어디 신청해?", "언제까지?", "누가 받을 수 있어?", "얼마 받아?", "어떤 기관이 운영해?", "지급 방식은?", "신청 시작은 언제?". 각 정책에 대해 합리적 응답 + 의도된 청크 sources (없으면 빈 sources).
- **무관 질문**: "오늘 점심 뭐 먹지?", "이 정책 작성한 사람 누구야?" → "관련 내용 없음" + 빈 sources.
- **세부 질문 회귀**: 자격 조건, 지원 금액, 신청 절차 등 기존 잘 동작하던 질문이 그대로 답변되는지.

### 11.4 빌드 검증

```bash
cd backend
./gradlew build         # 전체 빌드 + 테스트
./gradlew test --tests QnaServiceTest
./gradlew test --tests OpenAiQnaClientTest
```

## 12. 그라쥬에이션 / 운영 KPI

배포 후 1~2주 운영 데이터로 측정 (로그 누적):

- LLM 답변에 "관련 내용이 명시되어 있지 않" 패턴 출현 빈도 (감소 기대 — 케이스 2 차단 효과).
- sources 비어 있는 답변 비율 (증가 기대 — fallback 흐름 정상화).
- 메타 질문 변형에서 정상 응답률 (수동 샘플 50건 이상).
- threshold 0.5 통과 청크 분포 (`RagSearchService` 디버그 로그) — 정상 질문 거절률이 너무 높으면 0.55 로 완화 검토.

운영 결과에 따라:

- 케이스 3 빈도 높음 → 후속 사이클(B 패턴 매칭 또는 C NO_ANSWER 토큰).
- 정밀 인용 ROI 가 명확 → v1+ 사이클로 분리.
- 모두 안정적 → 다음 v1 후보(의미 캐시 intent 기반) 로 이동.

## 13. 참고

- v0 의미 캐시 spec: `docs/superpowers/specs/2026-05-01-semantic-qna-cache-design.md`
- v0 의미 캐시 plan: `docs/superpowers/plans/2026-05-01-semantic-qna-cache.md`
- v1 의미 캐시 (intent 기반): `docs/superpowers/specs/v1-semantic-cache-intent-based.md`
- 본 spec 의 출발점 메모: `docs/superpowers/specs/v1-qna-quality-improvements.md` (이 spec 으로 흡수됨, history 보존 위해 유지)

## 14. 검증 후 발견 (Tasks 7, 8) 과 디자인 재결정

> 작성: 2026-05-02 도커 환경 수동 검증 단계.
> Tasks 1~5 머지 후 운영 환경에서 검증하던 중 두 가지 사항이 추가로 발견되어 같은 사이클·같은 PR 안에서 fix 함.

### 14.1 Task 7 — `passing.isEmpty()` 분기에서 LLM 호출 누락 결함

**증상**: 메타 질문 ("누가 대상자야?", "이 정책 뭐야?") 에 대해 정책 메타데이터가 LLM 까지 전혀 도달하지 못함. 응답은 항상 `NO_RELEVANT_MESSAGE` ("해당 정책 원문에서 관련 내용을 찾지 못했습니다...").

**근본 원인**: spec § 5 의 해결책 ("메타데이터를 user message 에 포함") 은 LLM 이 호출되어야 의미가 있는데, `QnaService.processQuestion` 의 `passing.isEmpty()` 분기가 LLM 호출 전에 early return 함. 메타 질문은 본문 청크와 의미적 거리가 멀어 청크 통과율이 0% 가 되기 쉬움 → LLM 자체가 호출 안 됨 → 메타데이터가 LLM 에 도달할 기회 없음.

**Fix (commit `7b0e1a8`)**: `passing.isEmpty()` 시 `rejectAndComplete` 대신 빈 본문 컨텍스트 + 메타데이터로 LLM 호출. system prompt 의 "본문에 답이 없으면 메타 보강" 룰이 동작하도록.

```java
String context;
List<QnaSourceResult> sources;
if (passing.isEmpty()) {
    context = "(본문에서 관련 청크를 찾지 못했습니다.)";
    sources = List.of();
} else {
    context = buildContext(passing);
    sources = buildSources(command.policyId(), passing);
}
```

부수 정리: 더 이상 사용 안 하는 `NO_RELEVANT_MESSAGE` 상수 제거. `QnaFailedReason.NO_RELEVANT_CHUNK` enum 값은 다른 테스트에서 참조되어 보존.

### 14.2 Task 8 — 한국어 임베딩 distance 분포 vs threshold 가정 불일치

**증상**: Task 7 fix 후에도 본문에 명백히 들어 있는 정보 ("중복 수혜 안 되는 통장", "프리랜서 가능 여부") 도 답변 못 함.

**근본 원인**: 운영 백엔드 로그에서 RAG 검색 distance 분포 확인:
```
RAG 검색 결과: policyId=7, top5=[0.709, 0.714, 0.725, 0.726, 0.732]
```

가장 가까운 청크조차 distance 0.709. **한국어 짧은 질문 vs 긴 정책 본문 청크의 OpenAI `text-embedding-3-small` distance 는 본질적으로 0.7~0.75 가 기본 분포**. spec § 4.1 의 "0.7 이 느슨하다" 는 추정은 실측과 맞지 않음 — 0.5 는 물론 0.7 도 사실상 빡빡한 임계값이었음.

**결과**: 본문 청크가 거의 통과 못 해 LLM 컨텍스트로 도달 못 함 → 메타데이터에 없는 본문 정보는 답변 불가 → 메타에 있는 정보만 답변되는 비대칭.

**또한 사용자 피드백**: "메타데이터에서 답을 뽑았으면 메타데이터를 sources 로 줘야 한다" — § 7 의 "메타데이터는 sources 에 포함하지 않음" 결정이 사용자 신뢰감 측면에서 잘못된 결정이었음.

**Fix A+B+C (commit `8476814`)**:

- **A. threshold 0.5 → 0.78** — 실측 distance 분포 (0.7~0.75) 보다 약간 위. 본문 청크가 LLM 에 도달 가능.
- **B. fallback 패턴 안전망** — threshold 풀어서 무관 질문도 청크 통과 → sources 모순 재발 우려. LLM 응답에 fallback 패턴 (`"명시되어 있지 않"`) 검출 시 `sources = List.of()`. spec § 9 에서 "안전망 없음(A) 으로 시작" 이라 했는데, 실측 distance 분포가 그 결정의 전제와 달라서 안전망을 본 사이클 안에서 채택.
- **C. 메타 답변 sources entry 추가** — `passing.isEmpty()` + non-fallback 답변일 때 sources 에 "정책 기본 정보" entry 1개 추가. 사용자 출처 가시성 제공. § 7 "메타데이터는 sources 에 포함하지 않음" 결정 변경.

```java
// QnaService.processQuestion — LLM 호출 후
if (isFallbackAnswer(fullAnswer)) {
    sources = List.of();
} else if (passing.isEmpty()) {
    sources = List.of(new QnaSourceResult(
            command.policyId(), null, "정책 기본 정보", null, null,
            "정책 메타데이터 기반 답변"
    ));
}
```

`isFallbackAnswer(String)` 헬퍼: `answer.contains("명시되어 있지 않")` 패턴 매칭.

### 14.3 변경된 결정들 정리

| 항목 | 원래 spec 결정 | 검증 후 결정 | 이유 |
|---|---|---|---|
| § 4.1 threshold 기본값 | 0.5 | **0.78** | 한국어 임베딩 실측 distance 분포 0.7~0.75 |
| § 5 메타데이터 LLM 도달 | passing.isEmpty 시 early return | **passing.isEmpty 시에도 LLM 호출** | spec 의도와 코드 흐름 불일치 (Task 7) |
| § 7 메타 답변 sources | 비포함 (사이클 의도된 동작) | **메타 답변 시 "정책 기본 정보" entry 1개** | 사용자 출처 신뢰감 |
| § 9 안전망 (LLM fallback 검출) | 비범위 — 운영 후 결정 | **본 사이클 안에서 채택 (B 패턴 매칭)** | threshold 0.78 부작용 차단 필수 |

### 14.4 운영 KPI 재정의

§ 12 의 KPI 는 그대로 유효하지만 다음을 추가:

- threshold 0.78 통과 청크 수 분포 — 평균 통과 청크 N 개. N≈5 (top-K) 면 컨텍스트 사이즈 모니터링.
- LLM TTFT (첫 토큰까지 시간) — 컨텍스트 커지면서 응답 지연 우려. 너무 길면 top-K 5 → 3 검토.
- fallback 패턴 매칭 false positive — 정상 답변에 "명시되어 있지 않" 들어가는 케이스 (있다면 패턴 정밀화).

### 14.5 후속 / 미결 (Tasks 7, 8 추가분)

- **무관 질문 비용 방어** (Task 8 부산물): threshold 0.78 부터는 무관 질문도 청크 통과해 LLM 호출. fallback 답변은 안전망으로 sources 비우지만 LLM 호출 비용은 발생. per-user QPS 제한 또는 입력 길이/언어 검증으로 보호. v1 후보.
- **메타 sources entry 의 정보 풍부화**: 현재 `attachmentLabel = "정책 기본 정보"` + `excerpt = "정책 메타데이터 기반 답변"` 의 단순 형태. 향후 정책 상세 페이지 링크 또는 메타 필드별 entry 분리 검토.
- **top-K 튜닝**: top-K 5 + threshold 0.78 = 청크 5개 모두 LLM 컨텍스트로 들어감. 응답 지연이 운영 KPI 에서 임계 초과 시 top-K 3 으로 줄이기.
