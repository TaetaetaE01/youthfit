# 온통청년 풍부화 파이프라인 dev 환경 검증 중 발견된 5종 버그

- 작성일: 2026-05-12
- 작성자: TaetaetaE01
- 관련 커밋:
  - `a899e11` (`fix(n8n): workflow data flow + LLM call + abs URL + 4 new fields end-to-end`)
  - `0c534ec` (`feat(policy): extend enrichment sections with overview/eligibility/org/phone`)
  - `93f6c62` (`fix(policy): mark PolicyEnrichment.isExposable() as @JsonIgnore`)
- 관련 PR: (작성 예정)
- 관련 모듈: backend/policy, backend/ingestion, frontend, n8n workflows

## 한 줄 요약

> 온통청년 풍부화 파이프라인을 dev 환경에서 정책 1건으로 풀 검증하던 중, 단위 테스트로는 잡히지 않던 5종의 통합 결함(Jackson 직렬화 우회 필드 누출, n8n 노드 간 데이터 누락, sandbox built-in 차단, expression hang, URL constructor 실패)을 차례로 발견·수정. 모두 영구 fix 로 반영하고 회귀 테스트 추가.

## 1. 상황

- 16개 task 의 enrichment 파이프라인 구현(spec/plan: `docs/superpowers/specs/2026-05-12-youth-center-enrichment-design.md`, `docs/superpowers/plans/2026-05-12-youth-center-enrichment.md`)을 완료한 직후, 사용자가 "실제로 한 건만 흘려서 파이프라인이 작동하는지 확인" 을 요청.
- docker-compose 로 postgres·redis·backend·n8n 을 띄우고, 워크플로우 webhook 을 manual trigger 하여 한 사이클 전체(온통청년 API → 외부 페이지 fetch → cheerio → OpenAI → 백엔드 intake → DB → 응답)를 검증.
- 단위 테스트 + slice 테스트는 모두 통과한 상태였으나, **통합 환경에서만 드러나는 5종 결함**이 차례로 노출됨.
- 영향: enrichment 데이터가 응답 시점에 null 로 마스킹되거나, 워크플로우가 멈춰서 백엔드까지 도달 못 하거나, 첨부 URL 이 상대 경로로 저장되는 등 모든 단계에서 한 군데 이상 깨짐.

## 2. 원인

### 2-1. `PolicyEnrichment.isExposable()` 이 Jackson 직렬화 시 `exposable: true` 라는 잉여 필드로 jsonb 에 저장됨

- Jackson 의 record support 가 `boolean is*()` 메서드를 Java Bean property 로 인식하여 직렬화 결과에 포함시킴.
- jsonb 저장 시 `{"status":"OK", ..., "exposable": true, ...}` 가 들어가고, 다음 조회 시 역직렬화 단계에서 `UnrecognizedPropertyException: "exposable"` 발생.
- Hibernate `@JdbcTypeCode(SqlTypes.JSON)` 의 deserialize 실패 → `policy.getEnrichment()` 가 null 반환 → 응답 마스킹에서 null 처리 → 사용자 화면에서 enrichment 섹션 미노출.
- 위치: `policy/domain/model/PolicyEnrichment.java:20-24` (메서드 정의)

### 2-2. n8n `변동 판정` 노드의 `$input.all()` 이 정책 리스트 대신 hash 맵을 받음

- plan 에서 작성한 코드는 `$('JSON 파싱 + 서울 필터').all()` 이었으나 implementer 가 워크플로우 JSON 작성 시 `$input.all()` 로 변경.
- n8n 데이터 흐름에서 `$input.all()` 은 **직전 노드(`외부 hash 조회`)의 출력**을 받음 → hash 맵 단일 item.
- 코드가 그 hash 맵 객체를 정책으로 spread (`...p`) → 결과 객체에 plcyNo 가 키가 아닌 객체 속성 자리에 들어가고, 정책 필드(`plcyNo`, `plcyNm`, `refUrlAddr1`) 가 사라짐.
- 변환 노드 시점에 `p.plcyNo` 가 undefined → DB 의 external_id 가 `https://...plcyNo=undefined`, title 이 `(정책 undefined)` 로 저장됨.

### 2-3. n8n 2.16 Task Runner 가 built-in module require 를 차단

- `변동 판정` Code 노드의 `require('crypto')` 가 `Module 'crypto' is disallowed` 로 실패.
- `LLM 구조화 추출` Code 노드의 `await fetch(...)` 가 `fetch is not defined` 로 실패.
- 원인: n8n 2.16 의 새 JS Task Runner 가 **built-in 도 NODE_FUNCTION_ALLOW_BUILTIN 명시 필요**, sandbox 에 global fetch 없음.
- docker-compose 의 n8n environment 에서 `NODE_FUNCTION_ALLOW_EXTERNAL=cheerio` 만 있었고 `NODE_FUNCTION_ALLOW_BUILTIN` 미설정.

### 2-4. n8n `n8n-nodes-base.openAi` v1.5 호환 문제 + HTTP Request `jsonBody` expression hang

- 첫 fix 로 OpenAI 노드(`n8n-nodes-base.openAi` v1.5) 를 사용했으나 OpenAI API 에서 `Bad request - please check your parameters` 응답. 노드 typeVersion 1.5 와 n8n 2.16 의 통신 형식 불일치.
- HTTP Request 노드로 교체 후 `jsonBody` 안에 `={{ JSON.stringify(...) }}` expression 을 박았더니, n8n expression engine 이 외부 JSON 안의 inline expression 평가에서 hang. execution 이 20분+ `waiting` 상태로 멈춤. 백엔드 호출까지 도달 못 함.

### 2-5. n8n Code sandbox 의 `new URL()` constructor 가 silent throw

- cheerio 노드의 abs(href) 헬퍼가 `new URL(href, pageUrl)` 사용. sandbox 에서 throw → catch 의 fallback 으로 원본 href(상대 경로) 반환.
- 첨부 URL 이 `/youthpolicy/youthPolicyInfoDown.do?poly_seq=104&fileno=1` 형태로 DB 저장 → 사용자가 클릭 시 youthfit 도메인으로 잘못 라우팅.

## 3. 고려한 대안

### 2-1 Jackson `exposable` 누출

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. `@JsonIgnore` on `isExposable()` | 가장 명확한 의도, 다른 코드에 영향 0 | — |
| B. `isExposable` → `canExpose` 같은 비-bean 네이밍 | record 외부에서 호출하는 코드 수정 필요 | 의미 약화, 위험 |
| C. 전역 ObjectMapper 의 `FAIL_ON_UNKNOWN_PROPERTIES=false` | 다른 모든 모델에도 영향, 진짜 스키마 변경을 silently 통과시킬 위험 | 너무 광범위 |

**채택: A**

### 2-3 n8n built-in/external 허용 범위

| 대안 | 장점 | 단점 |
|---|---|---|
| A. `NODE_FUNCTION_ALLOW_BUILTIN=crypto,https` 만 화이트리스트 | 최소 권한 | 매번 새 모듈 추가 시 env 수정 |
| B. `NODE_FUNCTION_ALLOW_BUILTIN=*` 전체 허용 | 한 번 설정으로 끝 | 보안·격리 약화 |
| C. n8n의 helpers (`this.helpers.httpRequest`) 사용 | env 설정 불필요 | 도큐먼트 빈약, 노드 버전별 동작 차이 |

**채택: A** — 최소 권한 + 명시적 의도. 추후 추가 모듈 필요 시 docker-compose.yml 명시.

### 2-4 OpenAI 호출 방법

| 대안 | 장점 | 단점 |
|---|---|---|
| A. `n8n-nodes-base.openAi` v1.5 노드 | 노드 UI 제공 | n8n 2.16 과 호환성 문제, 400 응답 |
| B. HTTP Request 노드 + expression body | UI 친화적 | expression engine hang (20분+) |
| C. Code 노드 + `https.request` 직접 호출 | 검증된 시뮬레이션과 동일 패턴, expression 우회 | sandbox 권한 환경변수 필요 |

**채택: C** — 이미 수동 시뮬레이션(curl) 에서 검증된 흐름과 동일. expression engine 의존성 제거가 hang 재발 방지 측면에서 가장 안전.

### 2-5 URL 절대화

| 대안 | 장점 | 단점 |
|---|---|---|
| A. `require('url').URL` 사용 + `NODE_FUNCTION_ALLOW_BUILTIN=url` | 표준 URL API | 권한 추가, 그래도 sandbox 에서 동작 안 할 위험 |
| B. string concat 기반 수동 prefix 처리 | 의존성 0, 항상 동작 | 엣지 케이스(`//` protocol-relative, `../`) 직접 처리 필요 |
| C. 첨부 URL 변환을 백엔드로 위임 | 워크플로우 단순화 | 백엔드 스키마에 sourceUrl base 추가 필요 |

**채택: B** — `/^https?/` / `^//` / `/` / 상대경로 4가지 케이스 명시적 처리. sandbox 의존성 없음.

## 4. 선택과 이유

다섯 결정 모두 공통 기준:

- **재발 방지**: 같은 root cause 에서 다른 증상으로 또 터지지 않도록 — 회귀 테스트 또는 명시적 권한/코드로 봉쇄.
- **격리 유지**: 격리 모델(Jackson 의 unknown property 검출, n8n sandbox)을 약화시키지 않음.
- **검증된 패턴 우선**: 수동 시뮬레이션에서 이미 성공한 흐름을 그대로 자동화에 이식.

가역성:
- 2-3 의 `crypto,https` 추가는 docker-compose.yml 한 줄 → 언제든 회수 가능.
- 2-4 의 Code 노드 → 향후 n8n openAi 노드가 안정화되면 다시 노드 기반으로 전환 가능.
- 2-1, 2-2, 2-5 는 코드 수정이라 영구 — 새로 발생할 일 자체가 차단됨.

## 5. 해결

### 코드 변경

| # | 파일 | 변경 |
|---|------|------|
| 2-1 | `backend/.../policy/domain/model/PolicyEnrichment.java:20-26` | `isExposable()` 에 `@JsonIgnore` 추가 |
| 2-2 | `n8n/workflows/youth-center-seoul.json` (변동 판정 노드) | `$input.all()` → `$('JSON 파싱 + 서울 필터').all()` |
| 2-3 | `docker-compose.yml` (n8n env) | `NODE_FUNCTION_ALLOW_BUILTIN=crypto,https` |
| 2-4 | `n8n/workflows/youth-center-seoul.json` (LLM 노드) | `n8n-nodes-base.openAi` → `n8n-nodes-base.code` (`require('https').request`) |
| 2-4 | `n8n/workflows/youth-center-seoul.json` (enrichment 객체 조립 노드) | LLM 응답 파싱을 `_llmResponse.choices[0].message.content` 로 일원화 |
| 2-5 | `n8n/workflows/youth-center-seoul.json` (cheerio 노드) | `new URL(...)` → `/^https?:\/\//` + `/^\/\//` + `/^\//` + 상대경로 4분기 string concat |
| 2-5 | 동일 노드 | `pageUrl` 을 `$('링크 선택').first()?.json?._enrichUrl` 에서 직접 읽도록 보강 |

### 회귀 테스트

- `backend/.../PolicyEnrichmentTest.java`:
  - `serialization_does_not_leak_isExposable_as_property` — `@JsonIgnore` 누락 시 jsonb 에 `exposable` 키가 들어가지 않는지 검증
  - `deserialization_succeeds_for_jsonb_roundtrip` — 실제 jsonb 라운드트립 (Jackson + JavaTimeModule) 으로 역직렬화 가능 여부 검증

### 보강된 enrichment 스키마

검증 중 사용자 피드백("운영기관/문의처가 없다", "정책 개요·지원자격이 안 보인다") 으로 `PolicyEnrichment.Sections` 에 4개 필드 추가(commit `0c534ec`):
- `policyOverview`, `eligibilityCriteria`, `operatingOrganization`, `contactPhone`

cheerio 첨부 발견 휴리스틱도 같이 확장:
- 기존: `href` 의 `.pdf|.hwp|.hwpx` 확장자만
- 추가: `<a>` text 의 확장자 / `<img alt="hwp|pdf|...">` 신호 / href 의 `download|filedown|attach` 키워드 / `docx`, `xlsx`, `zip` 확장자

## 6. 검증

### 풀 파이프라인 (execution 31~34)

`docker compose up -d` + n8n manual webhook trigger 로 정책 1건 처리:

- 백엔드 INFO 로그: `enrichment received: externalId=20260506005400213176, status=OK, confidence=0.9`
- DB `policy.enrichment` jsonb 의 9개 sections 모두 채워짐 (LLM 자동 추출)
- `extraAttachments` 2건 자동 발견, URL 절대 경로(`https://youth.incheon.go.kr/youthpolicy/...`)로 정상 저장
- `GET /api/v1/policies/128` 응답에 enrichment 노출, 내부 메타(status/confidence/extractor)는 마스킹됨
- LLM 추출 결과 합리적 (계양구 정책 데이터: 운영기관 "인천광역시 계양구 일자리정책과", 문의처 "032-450-8354", policyOverview 자연어 요약 등)

### 단위/슬라이스 테스트

- `PolicyEnrichmentTest`: 6/6 통과 (회귀 테스트 2건 포함)
- `PolicyQueryServiceEnrichmentMaskingTest`: 9/9 통과 (마스킹 분기 + 4 새 필드 propagation)
- `PolicyEnrichmentSection.test.tsx` (frontend): 10/10 통과
- 전체 백엔드 빌드: BUILD SUCCESSFUL (8개 pre-existing 통합 테스트 실패는 DB 의존 user 모듈, 본 변경 무관)

### 모니터링 포인트

- 운영 시 enrichment 적재 후 응답에서 enrichment 가 null 로 마스킹되는 비율 → 0.6 confidence 임계값 적정성 지표
- 첨부 휴리스틱이 false positive (메뉴 링크를 첨부로 오인) 비율 — 로그로 추적
- n8n executions 의 LLM Code 노드 timeout(30s) 발생률

## 7. 후속 / 미결

- **워크플로우의 test 모드 코드 (`lastPage = 1`, `slice(0, 25)`) 는 원본에 남아있음** — 운영 배포 전 풀 페이징 활성화 토글 필요 (워크플로우 안 주석에 명시되어 있음).
- **숨겨진 backfill**: 정책 128 의 `policy_source.source_hash` 가 검증 중 `force-...` 인위적 값으로 변경된 상태. 다음 04:00 정기 trigger 가 정상 hash 로 덮어쓸 것이지만, 그 전까지는 의미 없는 hash. 운영 영향 0.
- **enrichment 스키마 확장 spec 반영**: spec 문서(`docs/superpowers/specs/...-design.md`) 에 추가된 4개 필드(`policyOverview` 등) 반영이 필요 (현재 spec 은 5개 필드 기준).
- **재발 방지 가드레일**:
  - n8n 워크플로우 Code 노드를 단위 테스트하기 위한 jest 환경 — 현재는 사용자 manual webhook trigger 로만 검증. 워크플로우 변경마다 사람이 검증해야 하는 부담.
  - Jackson serialization 결과에 잉여 키가 들어가지 않는지 검증하는 패턴을 다른 도메인 record 에도 적용 검토 (예: `PolicyReferenceSite`, `PolicyApplyMethod`).
- **첨부파일 다운로드 자동화 (v1)**: 현재 `extraAttachments` 는 URL 만 저장. 기존 첨부 파이프라인(`AttachmentDownloadService`)에 연결하여 본문 텍스트까지 추출하면 RAG 품질 추가 향상 가능.

## 8. 참고

- spec: `docs/superpowers/specs/2026-05-12-youth-center-enrichment-design.md`
- plan: `docs/superpowers/plans/2026-05-12-youth-center-enrichment.md`
- 사용자 피드백 (인용):
  > "1. 온통청년에서 api로 제공하지 않았지만 홈페이지 들어가면 운영기관에 인천광역시 계양구 일자리정책과 있는데 이부분 정책 조회하는 부분에 들어가게 데이터 긁어올 때 반영됐으면 좋겠어, + 전화번호도 마찬가지"
  > "2. ai 자동수집 요약에 어떤 정책인지, 지원 자격 이런 내용은 빠져서 그래서 이게 무슨 정책이라고? 라는 생각이 들 거 같음"
  > "3. 공식 홈페이지에 첨부파일이 있는데 이 부분은 긁어오기 힘든건가?"
- 검증된 정책: `(계양구) 2026년 계양청년네트워크 위원 모집 및 운영` (plcyNo `20260506005400213176`, policy_id 128, refUrl `https://youth.incheon.go.kr/youthpolicy/youthPolicyInfoDetail.do?poly_seq=104`)
