# 몽땅청년 참고사이트 fetch 개선 — 설계

- 날짜: 2026-07-02
- 이슈: [#157](https://github.com/TaetaetaE01/youthfit/issues/157) (워크플로우 중단 버그, P0), [#158](https://github.com/TaetaetaE01/youthfit/issues/158) (fetch 개선 묶음)
- 접근: A안 — 현행 "참고사이트 fetch + 머지" Code 노드 내 개선 (서브워크플로우 추출·백엔드 이관은 비채택)

## 배경 (라이브 재현 실측, 2026-07-02)

몽땅청년 상세의 참고사이트(관련/신청/참고 사이트 Ⅰ·Ⅱ) fetch가 대부분 실패한다.
샘플 12개 URL 중 유효 수집 2건(~17%). 실측 원인:

| 원인 | 증거 | 현행 결과 |
|---|---|---|
| 자기 포털 순환 참조 | 서울시·자치구 탭 링크 대부분이 youth.seoul.go.kr 자기 링크. 메인은 244B 인덱스 shell, `content.do`는 WebGate JS 챌린지(HTTP 200 + 786B, `WG_StartWebGate`) | TOO_SHORT |
| 타 정책 상세 링크 | 참고 사이트 Ⅱ가 다른 정책의 `view.do`를 가리킴 | OK 판정이지만 타 정책 본문이 머지되는 교차 오염 |
| 쿠키 미보존 307 루프 | molit.go.kr — Set-Cookie 후 재요청을 기대하는 보안 장비 패턴. 쿠키 없이 5홉 캡 도달 | FETCH_FAILED |
| TLS 중간 인증서 누락 | fill4young.kinfa.or.kr — `UNABLE_TO_VERIFY_LEAF_SIGNATURE`. 브라우저는 통과, Node는 실패 | FETCH_FAILED |
| 타임아웃 | nhuf.molit.go.kr 10초 초과 | FETCH_FAILED |
| JS 렌더링 | 네이버 블로그(텍스트 22자), 구글 폼 | TOO_SHORT |
| 스킴 없는 href | `href="www.kofpi.or.kr"` 실존. `lib.request(url)`이 try 밖이라 `ERR_INVALID_URL` → Promise rejection 미처리 → Code 노드 에러 → onError 기본값(중단)이라 **워크플로우 전체 중단, 남은 정책 미수집** | 워크플로우 크래시 (#157) |

## 범위

**포함**
- "참고사이트 fetch + 머지" Code 노드 수정 (youth-seoul-city / district / external 3종 동일 코드)
- fixtures 미러 `n8n/workflows/__fixtures__/enrichment-merge/enrich.mjs` 동기화 + verify 케이스 추가
- 백엔드 `PolicyEnrichment` record에 `fetchDiagnostics` 필드 추가 (jsonb 하위 호환)
- n8n 컨테이너 TLS 인증서 번들 주입 (`NODE_EXTRA_CA_CERTS`) + OPS.md 절차 기록

**비범위**
- WebGate·JS 렌더링 돌파(헤드리스 브라우저) — 실가동 후 Q&A 실패 사례 축적을 보고 판단
- TOO_SHORT 임계(200자)·본문 보일러플레이트 제거 — 수집 안정화 후 실데이터 분포 기반 별도 작업
- fetch 로직 서브워크플로우 공용화 — force-enrich 실구현 때 함께
- 어드민 UI에 fetchDiagnostics 노출 — jsonb 저장까지만, 화면은 후속

## 설계

### 1. 생존성 3겹 방어 (#157)

1. **URL 정규화** — `selectUrls`에서 스킴 없는 URL(`www.kofpi.or.kr` 형태)은 `https://`를 부여한다.
   그래도 URL 파싱이 불가능하면 해당 URL만 `INVALID_URL`로 스킵한다.
2. **예외 격리** — `httpGetText`의 `lib.request(...)` 호출 전체를 try로 감싸고,
   `fetchAndExtract`의 await도 try로 감싼다. URL 하나의 실패가 다른 URL·다른 정책으로 번지지 않는다.
3. **노드 설정** — 노드 onError를 continue로 변경한다. 최후에 노드가 죽어도 다음 노드
   ("enrichment 메타 합성")가 `_enrichmentStatus === undefined → FETCH_FAILED` 폴백을 이미 갖고 있어
   해당 정책만 FETCH_FAILED로 흐르고 나머지 정책은 계속 수집된다.

### 2. 자기 포털 URL 사전 필터

`youth.seoul.go.kr` 호스트 전체를 fetch 대상에서 제외한다.

- 경로별 세분 규칙(메인/content.do/view.do만 제외) 대신 호스트 단위로 자른 이유:
  content.do는 WebGate라 fetch가 무의미하고, 타 정책 view.do는 교차 오염을 유발하며,
  그 외 경로도 자기 포털 텍스트는 이미 상세 파싱에서 확보했으므로 참고사이트로서 추가 가치가 없다.
- 필터로 URL이 전부 사라지면 기존 `NO_LINK` status를 재사용하고(백엔드 SKIPPED 매핑 불변),
  진단에 `SELF_PORTAL`을 남긴다.

### 3. cookie jar (리다이렉트 체인 한정)

- 리다이렉트를 따라가는 동안 `Set-Cookie`를 수집해 같은 호스트의 다음 hop에 `Cookie` 헤더로 전달한다.
  대상 패턴: molit.go.kr의 "307 + Set-Cookie 후 재요청 기대".
- 체인 밖(정책 간·실행 간)으로는 쿠키를 유지하지 않는다 — 세션 관리 복잡도 회피.
- 같은 URL을 쿠키 변화 없이 2회 재방문하면 루프로 판정하고 중단한다(기존 5홉 캡 유지).

### 4. 실패 사유 기록 (status enum 불변)

- URL별 진단을 enrichment jsonb의 새 필드로 기록한다:
  `fetchDiagnostics: [{ url, outcome }]`
- outcome 코드: `OK / SELF_PORTAL / INVALID_URL / HTTP_<코드> / TIMEOUT / TLS_ERROR / REDIRECT_LOOP / OVERSIZE / TOO_SHORT / NETWORK`
- 백엔드는 `PolicyEnrichment` record에 필드를 추가하고, 수신 경로인 `IngestPolicyRequest`의
  enrichment 페이로드 매핑에도 같은 필드를 반영한다. 기존 jsonb에 필드가 없으면 null (하위 호환).
- `EnrichmentStatus` enum과 `IngestionService.mapEnrichmentStatus`는 변경하지 않는다.
  최종 status는 기존 값(OK/NO_LINK/FETCH_FAILED/TOO_SHORT) 그대로 산출한다.

### 5. TLS 인증서 보강

- kinfa.or.kr 실측으로 누락된 중간 인증서를 수집해 `n8n/certs/` 번들 파일로 저장하고,
  docker-compose에서 `NODE_EXTRA_CA_CERTS`로 n8n 컨테이너에 주입한다.
- 인증서 검증 완화(`rejectUnauthorized=false`)는 하지 않는다.
- 번들 수집·갱신 절차를 OPS.md에 기록한다.

## 에러 흐름 정리 (변경 후)

```
URL 후보 (최대 3)
  → 정규화 실패        → 그 URL만 INVALID_URL, 다음 URL 진행
  → 자기 포털          → 그 URL만 SELF_PORTAL 스킵
  → fetch 실패(각종)   → 그 URL만 outcome 기록, 다음 URL 진행
  → 전 URL 실패        → status FETCH_FAILED (기존과 동일)
  → 전 URL 필터 제거   → status NO_LINK + SELF_PORTAL 진단
  → 노드 자체 크래시   → onError=continue → 메타 합성 폴백 FETCH_FAILED
어느 경우에도 다른 정책의 수집은 계속된다.
```

## 테스트

- **fixtures**: enrich.mjs 미러 동기화 + verify 케이스 4종 추가
  (스킴 없는 URL 정규화, 자기 포털 필터, 쿠키 리다이렉트 재요청, outcome 코드 매핑)
- **백엔드**: `PolicyEnrichment` 역직렬화 호환 테스트 (fetchDiagnostics 유/무 jsonb 모두)
- **E2E** (로컬 n8n 재기동 필요): external 탭 웹훅 실행으로
  ① kofpi 정책이 워크플로우를 중단시키지 않는지, ② molit 계열이 쿠키로 수집되는지,
  ③ 서울시 탭에서 NO_LINK+SELF_PORTAL이 기록되는지, ④ 어드민 스텝 로그 status 분포 확인

## 결정 기록

| 결정 | 선택 | 근거 |
|---|---|---|
| 작업 범위 | #157+#158 한 스펙 | 같은 노드를 두 번 수정·재검증하는 비용 회피. 플랜에서 #157 태스크를 선두 배치 |
| 구현 위치 | 노드 내 개선(A안) | 목표는 성공률·생존성이지 구조 개편이 아님. 공용화는 force-enrich 때 |
| TLS | 인증서 체인 보강 | 검증 완화는 보안 원칙 후퇴. 필요 인증서만 명시적 주입 |
| 실패 사유 | 별도 진단 필드 | status enum 확장은 백엔드 역직렬화·매핑·어드민 필터까지 파급. jsonb 필드 추가가 최소 변경 |
| 포털 필터 | 호스트 전체 제외 | 경로 세분 규칙보다 단순하고, 자기 포털은 참고사이트로서 가치 없음 |
| 품질 개선(임계 등) | 제외 | 실데이터 분포 없이 임계 조정은 추정 조정. 수집 안정화 후 별도 |
