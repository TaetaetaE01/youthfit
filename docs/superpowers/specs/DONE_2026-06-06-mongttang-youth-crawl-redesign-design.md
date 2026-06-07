# 몽땅청년(youth.seoul.go.kr) 3-카테고리 크롤 재설계

- 날짜: 2026-06-06
- 상태: 설계(브레인스토밍 산출물)
- 관련 소스: n8n 워크플로우 (`n8n/workflows/`), 백엔드 ingestion 수신 엔드포인트
- 관련 메모: youth-seoul-loop-rearchitecture, youth-seoul-attachment-limit, prod-seed-2026

## 배경 / 문제

현재 단일 워크플로우 `youth-seoul-crawl.json` 은 청년몽땅정보통의 정책을 수집한다. 하지만 구조적 버그로 사실상 **'서울시 정책' 탭의 1페이지(2026 정책 5건)만** 긁고 끝난다.

확인된 문제:
1. **카테고리 루프 버그** — `카테고리별 순차 처리`(splitInBatches) + `$('카테고리별 순차 처리').first()` 안티패턴. 실행 데이터상 '중앙정부/타지역' 탭의 상세(`youthPlcyInfo/view.do`)가 한 번도 호출되지 않고, 해당 ID 가 추출되지 않음. cat2 가 통째로 누락된다.
2. **페이지네이션 미작동** — '서울시 정책' 목록은 137페이지(과거연도 포함)인데 `lastPage` 가 2로 잘못 계산되고, 세션쿠키(JSESSIONID) 미유지로 GET `pageIndex` 가 무시되어 매 페이지 1페이지만 반환된다.
3. **자치구 정책 카테고리 자체가 누락** — 포털엔 '자치구 정책' 탭(`guList.do`, tabKind=003)이 별도로 있고 137페이지(25개 자치구) 규모인데 현재 전혀 긁지 않는다.
4. **region 하드코딩** — `상세 데이터 파싱` 노드에 `region: '서울'` 로 박혀 있어 자치구/타지역도 무조건 '서울'로 태깅된다.
5. **support_target 미파싱** — 현재 5건 모두 `support_target` 가 비어 적합도 룰이 생성되지 않는다.

## 목표

청년몽땅정보통의 **3개 카테고리 정책을 2026 등록분 기준으로 정확히 수집**한다. 페이지네이션·region·자격요건 필드를 올바르게 채우고, 참고/신청 사이트 보강과 첨부 수집(기존 동작)을 유지한다.

## 범위 결정 (확정)

- **접근법 A**: 카테고리별 워크플로우 3개로 분리 → 카테고리 루프 버그 원천 제거
- **2026 컷오프**: 목록이 `regYmd desc` 정렬이므로 페이지를 넘기다 2025 이하가 나오면 중단 (ID prefix 의 연도로도 판별)
- **참고/신청 사이트 fetch+머지**: 기존 '서울시' 플로우 그대로 인계
- **첨부 LLM 선별**: 본 spec 범위 밖 → 별도 spec(`2026-06-06-attachment-embedding-llm-gate-design.md`)
- source_type 은 셋 다 `YOUTH_SEOUL_CRAWL` 유지

## 아키텍처 — 워크플로우 3개

기존 `youth-seoul-crawl` 폐기하고 카테고리별 분리:

| 워크플로우 | 목록 endpoint | 상세 파서 | region | 페이지 | 세션쿠키 |
|-----------|--------------|----------|--------|--------|---------|
| `youth-seoul-city` (서울시) | `plcyInfo/ctList.do?key=2309150002&tabKind=002` | `plcyInfo/view.do` ⓐ | 서울특별시 | 137p, 2026컷 | 필요 |
| `youth-seoul-district` (자치구) | `plcyInfo/guList.do?key=2309150002&tabKind=003` | `plcyInfo/view.do` ⓐ **(city 와 공유)** | 구명 추출 | 137p, 2026컷 | 필요 |
| `youth-seoul-external` (중앙/타지역) | `youthPlcyInfo/list.do?key=2309160001` | `youthPlcyInfo/view.do` ⓑ | 타지역/전국 | 1p | 불필요 |

- ⓐ 서울시·자치구는 동일 상세 템플릿(`plcyInfo/view.do`, tabKind 만 다름) → 파싱 로직 공유. 목록 endpoint·ID 형식(V2026… vs 숫자20자리)·region 만 다름.
- ⓑ 중앙/타지역만 별도 파서.
- 각 워크플로우 = 단일 카테고리의 페이지 루프. 카테고리 루프(splitInBatches+`.first()`) 제거.

### 상세 URL (참고)
- 서울시 상세: `plcyInfo/view.do?plcyBizId={V2026…}&tab=001&key=2309150002&tabKind=002`
- 자치구 상세: `plcyInfo/view.do?plcyBizId={2026…숫자}&tab=001&key=2309150002&tabKind=003`
- 중앙/타지역 상세: `youthPlcyInfo/view.do?plcyBizId={2026…숫자}&key=2309160001`

## 공통 페이지 루프 메커니즘 (city & district)

1. **세션 확립**: 첫 요청에서 `Set-Cookie`(JSESSIONID) 캡처 → 이후 목록/상세 요청에 `Cookie` 헤더로 전달. (쿠키 없으면 GET `pageIndex` 가 무시되어 1페이지 반복됨을 실증 확인함)
2. **페이지 루프**: `pageIndex` 1→N. 종료 조건 = 2026 컷오프 도달 OR `fn_egov_link_page` 최댓값(lastPage) 도달.
3. **2026 컷오프**: 목록 항목별 등록일/ID prefix 로 2026 여부 판정. 한 페이지가 전부 2025 이하면 중단. 혼합 페이지에서는 2026 항목만 채택 후 중단.
4. **external-hash 중복 스킵**: 복지로에 적용한 패턴 재사용. 상세 호출 전에 이미 적재된 ID 를 `/api/internal/ingestion/policies/external-hashes?source=YOUTH_SEOUL_CRAWL` 로 받아 스킵 → 재수집 비용·쿼터 절약.

## 상세 파싱 / 필드 추출

### 서울시·자치구 (공유 `plcyInfo/view.do` 파서)
기존 `상세 데이터 파싱` 로직 인계 + 보강:
- 제목, 본문, **`support_target`(지원대상/자격요건) — 현재 누락분 파싱 추가** (적합도 룰 생성 위해 필수)
- 신청기간(applyStart/applyEnd), `applyUrl`(신청 사이트)
- `_refUrls`: `['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ']` 섹션 링크 (최대 3개)
- 첨부: self 첨부 + 참고사이트 첨부
- **region**: city → `서울특별시`; district → **구명**(제목/상세에서 추출, 예: '중랑구'). 하드코딩 제거.

### 중앙/타지역 (별도 `youthPlcyInfo/view.do` 파서)
- 상세 구조가 plcyInfo 와 달라 ref/apply 섹션 라벨·필드 추출을 youthPlcyInfo 구조에 맞춰 별도 구현
- region: 타지역/전국 (상세에서 판별)

### 참고/신청 사이트 fetch + 머지 (인계)
- `참고사이트 fetch + 머지` 노드 로직 유지: refUrls/applyUrl 을 실제 HTTP fetch → 본문 텍스트 머지(캡 유지), 첨부 수집
- `attachments 승격` 화이트리스트 유지
- 주의: 메모리(youth-seoul-attachment-limit) — 일부 첨부는 WebGate·JS 동적로딩으로 수집 불가(보류 항목). 본문 텍스트 머지는 동작.

## 중복 / 교차소스 처리

- '중앙/타지역'은 온통청년(YOUTH_CENTER)과 약 50% 중복(청년미래적금·농식품 바우처 등). 
- 본 spec 에서는 **소스 내 중복만** external-hash 로 처리. 교차소스(YOUTH_CENTER ↔ YOUTH_SEOUL) 제목 중복 제거는 범위 밖 — 알려진 한계로 기록하고, 필요 시 조회 레이어 또는 후속 작업에서 처리.

## 적재

- 기존 `/api/internal/ingestion/policies` 로 POST (백엔드 계약 변경 없음).
- 백엔드 첨부 재추출 멱등화(PR `fix/ingestion-reextraction-and-crawl-pagination`)로 재적재 500 방지 확인됨.

## 에러 처리

- API/페이지 오류: n8n `retryOnFail`. 정책 1건 실패가 전체 크롤을 중단시키지 않게 함.
- 빈 페이지: 캐리어 항목으로 pagination 상태(pageNo) 유지.
- 세션쿠키 만료 시 재확립.

## 테스트

- 로컬 E2E 는 webhook 트리거(n8n CLI execute 불가). import + 재시작 후 `*-manual` 웹훅 POST.
- 검증 항목:
  - 카테고리별 수집 건수 (서울시/자치구/중앙·타지역)
  - 2026 컷오프 (2025 이하 정책이 안 들어오는지)
  - region 정확성 (자치구는 구명, 중앙/타지역은 타지역/전국)
  - `support_target` 채워짐 → 적합도 룰 생성됨
  - 참고/신청 사이트 본문 머지 확인
  - external-hash 스킵으로 재실행 시 신규만 적재

## 마이그레이션

1. 기존 `youth-seoul-crawl` 워크플로우 비활성/폐기
2. 신규 3개 워크플로우 import + 활성화 + 재시작
3. 각 webhook 트리거로 초기 수집

## 미해결 / 구현 시 확정할 점

- 자치구 region 추출 규칙(제목에 구명이 항상 있는지, 없으면 상세 어디서)
- 중앙/타지역 상세 파서의 ref/apply 섹션 매핑(youthPlcyInfo 실제 구조 확인 필요)
- 세션쿠키를 n8n httpRequest 에서 유지하는 정확한 방법(첫 응답 fullResponse 로 Set-Cookie 추출 → 헤더 주입)
- 2026 컷오프를 등록일 파싱 vs ID prefix 중 무엇으로 할지(둘 다 가능, ID prefix 가 단순)
