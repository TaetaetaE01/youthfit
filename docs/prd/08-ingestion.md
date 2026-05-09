# PRD — 정책 수집 (ingestion 모듈)

> **모듈**: `com.youthfit.ingestion`
> **우선순위**: P1
> **구현 상태**: 백엔드 수신 API 확장 완료 / 복지로 중앙부처 n8n 워크플로우 완료 / 온통청년 지자체 n8n 워크플로우 완료 (서울 스코프, 풀 페이징)

---

## 유저 스토리

이 모듈은 사용자 직접 대면 기능이 아닌, 내부 데이터 파이프라인이다. n8n 워크플로우가 공공데이터포털 Open API에서 수집한 정책 원천 데이터를 YouthFit 백엔드로 전달하는 수신 표면을 제공한다.

v0부터 HTML 크롤링이 아닌 **API 기반 수집** 방식을 채택한다.

---

## 수집 대상

### v0-A — 복지로 중앙부처 복지서비스 API (구현 완료)

| 항목 | 내용 |
|------|------|
| **제공기관** | 한국사회보장정보원 |
| **도메인** | apis.data.go.kr/B554287/NationalWelfareInformationsV001 |
| **sourceType** | `BOKJIRO_CENTRAL` |
| **data.go.kr 서비스 ID** | 15090532 |
| **응답 포맷** | XML |
| **커버리지** | 전국 단위 중앙부처 복지서비스 |
| **선정 이유** | 풍부한 서술형 본문, 기관·연락처 구조화, 첨부 PDF/HWP 다운로드 링크 제공 |

#### 엔드포인트

**목록 조회**:
```
GET https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfarelistV001
  ?serviceKey={인증키}
  &callTp=L
  &pageNo={페이지}
  &numOfRows=100
  &srchKeyCode=001
  &lifeArray=004        # 생애주기 코드: 004=청년
```

**상세 조회**:
```
GET https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfaredetailedV001
  ?serviceKey={인증키}
  &callTp=D
  &servId={서비스 ID, 예: WLF00004661}
```

#### 수집 가능 데이터

| 필드 | 출처 | 매핑 대상 |
|------|------|----------|
| `servId` | 상세 응답 | `rawData.externalId` |
| `servNm` | 상세 응답 | `rawData.title` |
| `wlfareInfoOutlCn` | 상세 응답 | `rawData.summary` |
| `tgtrDtlCn` + `slctCritCn` + `alwServCn` + `wlfareInfoOutlCn` | 상세 응답 | `rawData.body` (섹션 결합) |
| `jurMnofNm` | 상세 응답 | `rawData.organization` |
| `rprsCtadr` | 상세 응답 | `rawData.contact` |
| `lifeArray` (쉼표 구분) | 상세 응답 | `rawData.lifeTags` |
| `intrsThemaArray` (쉼표 구분) | 상세 응답 | `rawData.themeTags` → `category` 결정 |
| `trgterIndvdlArray` (쉼표 구분) | 상세 응답 | `rawData.targetTags` |
| `basfrmList` (servSeCode=040) | 상세 응답 | `rawData.attachments[]` (PDF·HWP 등) |
| — | 고정 | `rawData.region` = "전국" |

#### 카테고리 매핑 (intrsThemaArray → YouthFit Category)

| 복지로 관심주제 | YouthFit Category |
|----------------|-------------------|
| 주거 | HOUSING |
| 일자리 | JOBS |
| 교육 | EDUCATION |
| 서민금융 | FINANCE |
| 문화·여가 | CULTURE |
| 생활지원 | WELFARE |
| (그 외 매칭 없음) | WELFARE (기본값) |

### v0-B — 온통청년 API (구현 완료)

| 항목 | 내용 |
|------|------|
| **제공기관** | 청년정책조정위원회 (국무조정실) |
| **도메인** | www.youthcenter.go.kr/go/ythip |
| **sourceType** | `YOUTH_CENTER` |
| **data.go.kr 서비스 ID** | 15128179 (청년정책 통합검색 API) |
| **응답 포맷** | JSON |
| **커버리지** | 지자체(시·도·구) 청년 정책 보충 (복지로 중앙부처와 상호보완) |
| **호출 전략** | 전체 페이징 (zipCd 필터 없음) + 응답측 서울 필터 |
| **스코프 (v0)** | 서울특별시(11000) + 서울 25개 자치구(11110~11740) |

#### 엔드포인트

```
GET https://www.youthcenter.go.kr/go/ythip/getPlcy
  ?apiKeyNm={인증키}
  &rtnType=json
  &pageNum={페이지}
  &pageSize=100
```

#### 응답 → DB 매핑 (실응답 기반 검증)

| 응답 필드 | 매핑 대상 | 비고 |
|-----------|-----------|------|
| `plcyNo` | `policy_source.external_id` | UNIQUE 키 |
| `plcyNm` | `policy.title` | 인코딩 정제 (᭼→·) |
| `plcyExplnCn` | `policy.summary` | 빈 값이면 title 폴백 |
| `lclsfNm` | `policy.category` | `･` 구분 다중값 → 첫 매치, 5종 분류 |
| `mclsfNm` + `plcyKywdNm` | `policy.theme_tags` | 표준 17·17종 |
| `aplyYmd` ("YYYYMMDD ~ YYYYMMDD") | `policy.apply_start` / `apply_end` | 빈 값/수시 → null |
| `zipCd` (콤마 구분 다중값) | `policy.region_code` | 서울 자치구 25개 모두 → "서울특별시", 단일 → "서울특별시 ○○구" |
| `sprvsnInstCdNm` + `operInstCdNm` | `policy.organization` | 200자 컷 |
| `sprvsnInstPicNm` | `policy.contact` | "담당: {이름}" |
| `aplyUrlAddr`, `refUrlAddr1/2` | `policy.reference_sites` | jsonb |
| `plcyAplyMthdCn` | `policy.apply_methods` | 단일 entry |
| `frstRegDt` 연도 | `policy.reference_year` | |
| `mrgSttsCd`, `jobCd`, `schoolCd`, `plcyMajorCd`, `sbizCd`, `plcyPvsnMthdCd`, `bizPrdSeCd` | `policy.body` 본문 풀이 | data.go.kr 공식 코드 사전(`docs/prd/reference/youth-center-codes.xlsx`)으로 한글 풀이 |
| `sprtTrgtMinAge/MaxAge`, `earnMin/MaxAmt`, `sbmsnDcmntCn`, `etcMttrCn` | `policy.body` 본문 섹션 | [지원대상]/[제출서류]/[기타] |
| — | `policy.life_tags` | `["청년"]` 고정 |
| — | `attachments` | 항상 빈 배열 (응답에 첨부 필드 없음) |

### 미래 확장

- v1: 고용노동부 청년내일채움공제 등 부처별 전문 API 추가
- v1: 첨부파일 실제 다운로드·본문 추출(RAG용)

---

## 중복 제거 전략

복지로 중앙부처와 온통청년 모두 동일 정책을 포함할 수 있다 (예: `청년월세 지원사업`은 양쪽 모두 존재).

### 정책 — 복지로 우선

1. 복지로(`BOKJIRO_CENTRAL`)에 등록된 정책이 **우선권**을 갖는다.
2. 온통청년(`YOUTH_CENTER`) 수집 시, 동일 정책명이 복지로에 이미 존재하면 **건너뛴다**.
3. 동일성 판단은 제목 정규화(공백·특수문자 제거, 소문자화) 후 완전 일치로 수행한다.

### 구현 위치

- 백엔드 `IngestionService.receivePolicy`에서 처리한다.
- 신규 정책 등록 직전 `YOUTH_CENTER` 타입인 경우에 한해, 정규화된 제목이 `BOKJIRO_CENTRAL` 소스의 어떤 정책과도 일치하지 않는지 검증한다.
- 일치 시 `status = "SKIPPED_DUPLICATE"`로 응답(HTTP 202)하며 저장하지 않는다.
- 정상 스케줄에서는 BOKJIRO 03:00 → YOUTH_CENTER 04:00 순으로 BOKJIRO 가 항상 먼저 들어오므로 자연스럽게 우선권이 부여된다. 엣지 케이스(YOUTH_CENTER 가 먼저 들어가있는데 며칠 뒤 동일 제목의 BOKJIRO 가 신규 등록)에서는 별개 정책 2건이 일시적으로 공존할 수 있으며, v0 에서는 미처리한다(빈도 매우 낮음, 어드민 수동 머지 또는 v1 보강).

### 원천 보존

중복으로 스킵된 경우에도 raw 응답은 로그로 남긴다(실제 DB에는 저장하지 않음).

---

## 기능 요구사항

### 정책 데이터 수신 (백엔드 — 구현 완료)

**설명**: n8n 수집 워크플로우가 호출하는 내부 intake 엔드포인트. 원천 정책 데이터를 수신하고 검증 후 policy 모듈로 전달한다.

**비즈니스 규칙**:
- 내부 API key 인증 (`X-Internal-Api-Key`)
- 필수 필드 검증 (title, body, category, region, source.*)
- 확장 필드는 선택(optional): `externalId`, `summary`, `organization`, `contact`, `lifeTags`, `themeTags`, `targetTags`, `attachments`
- `sourceType`별 중복 제거 규칙 적용 (위 항목 참조)
- n8n은 이 단일 intake 엔드포인트만 호출한다
- 원천 JSON 전체를 `PolicySource.rawJson`에 직렬화하여 보존한다

**API 스펙**:

```
POST /api/internal/ingestion/policies
X-Internal-Api-Key: {apiKey}
Content-Type: application/json
```

**요청 (복지로 예시)**:
```json
{
  "source": {
    "url": "https://www.bokjiro.go.kr/ssis-tbu/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=WLF00004661",
    "type": "BOKJIRO_CENTRAL",
    "fetchedAt": "2026-04-17T03:00:00"
  },
  "rawData": {
    "externalId": "WLF00004661",
    "title": "청년월세 지원사업",
    "summary": "고금리·고물가 등으로 경제적 어려움을 겪는 청년층의 주거비 부담 경감을 위해 월 최대 20만원씩 최장 24개월간 월세를 지원합니다(생애1회).",
    "body": "[개요]\n...\n[지원대상]\n19세~34세 독립거주 무주택 청년...\n[선정기준]\n...\n[지원내용]\n월 최대 20만원, 최장 24개월",
    "category": "주거",
    "region": "전국",
    "applyStart": null,
    "applyEnd": null,
    "organization": "국토교통부 청년주거정책과",
    "contact": "1599-0001",
    "lifeTags": ["청년"],
    "themeTags": ["주거"],
    "targetTags": ["저소득"],
    "attachments": [
      {
        "name": "2026년 청년월세 지원사업 매뉴얼.pdf",
        "url": "https://bokjiro.go.kr/ssis-tbu/CmmFileUtil/getDownload.do?atcflId=20260325UUWBM0900380182060805&atcflSn=1",
        "mediaType": "application/pdf"
      }
    ]
  }
}
```

**응답 (202 Accepted)**:
```json
{
  "success": true,
  "data": {
    "ingestionId": "uuid-...",
    "status": "RECEIVED"
  }
}
```

상태(`status`) 값:
- `RECEIVED`: 신규 등록 또는 기존 레코드 업데이트 완료
- `SKIPPED_DUPLICATE`: 중복 감지로 저장하지 않음 (추후 구현)

### n8n 워크플로우

#### 복지로 수집 (`n8n/workflows/bokjiro-central-welfare.json` — 구현 완료)

```
[Schedule Trigger]          매일 새벽 03:00
       ↓
[HTTP Request]              NationalWelfarelistV001 목록 호출 (lifeArray=004, 100건/페이지)
       ↓
[Code Node]                 XML 파싱 → servId 추출 + totalCount 계산
       ↓
[SplitInBatches]            정책별 순차 처리 (batchSize=1)
       ↓
[Wait 1s]                   Rate limit (요청 간 1초)
       ↓
[HTTP Request]              NationalWelfaredetailedV001 상세 호출
       ↓
[Code Node]                 XML 파싱 → IngestPolicyRequest JSON 구성
                            (태그, 첨부파일, 기관, 연락처 포함)
       ↓
[HTTP Request]              POST /api/internal/ingestion/policies
       ↓
[IF 다음 페이지 존재]        pageNo 증가 후 목록 요청 반복
```

**환경변수**:
- `BOKJIRO_SERVICE_KEY`: data.go.kr 인증키 (64자리 HEX)
- `BACKEND_URL`: 백엔드 URL (기본 `http://backend:8080`)
- `INTERNAL_API_KEY`: 내부 인증키

#### 온통청년 수집 (`n8n/workflows/youth-center-seoul.json` — 구현 완료)

흐름:

```
[Schedule Trigger / Webhook]   매일 04:00 또는 수동
       ↓
[페이지 초기화]                pageNum=1
       ↓
[getPlcy 호출]                 zipCd 필터 없음, pageSize=100
       ↓
[JSON 파싱 + 서울 필터]        zipCd 에 서울 26개 코드 중 하나라도 포함되는 정책만 통과
       ↓
[SplitInBatches batchSize=1]
       ↓
[1초 대기]                     백엔드 보호
       ↓
[변환 Code 노드]               코드 사전 풀이 + 본문 섹션 결합
       ↓
[POST /api/internal/ingestion/policies]
       ↓
[페이지 루프]                  pageNum < lastPage 면 다음 페이지
```

**환경변수**:
- `YOUTH_CENTER_API_KEY`: data.go.kr 인증키

**비즈니스 규칙 (공통)**:
- 수집 간격 분리: 복지로 03:00 / 온통청년 04:00
- 요청 간 1초 딜레이
- SHA-256 해시 기반 변경 감지는 백엔드(`PolicySource`)에서 처리
- 워크플로우 JSON은 `n8n/workflows/` 디렉토리에 Git 관리
- 민감값(API 키 등)은 n8n 환경변수로 분리, JSON에 포함 금지

---

## 운영 원칙

- 공공 API 호출은 제공기관의 트래픽 정책을 준수한다 (data.go.kr 기본 호출 제한 1만 건/일).
- 식별 가능한 User-Agent를 사용한다: `YouthFit-Bot/1.0 (+https://youthfit.kr/bot)`
- 요청 간 최소 1초 딜레이를 유지한다.
- 요약이나 인용으로 충분한 경우 원문 전체를 그대로 노출하지 않는다 (첨부파일 원문은 링크만 저장).
- `source.url`, `source.type`, `source.hash`를 기록하여 추적 가능성을 확보한다.
- 첨부파일은 v0에서 URL만 기록하고, 실제 다운로드·저장·본문 추출은 v1에서 검토한다.

---

## 의존 관계

- `ingestion` → `policy.application`: 정규화 전 데이터 전달
- n8n → `ingestion`: HTTP 호출 (단일 intake 엔드포인트)

## 확장 방향

- v0-A: 복지로 중앙부처 API (완료)
- v0-B: 온통청년 지자체 API — 서울 스코프 (완료)
- v1: 온통청년 전국 확대, 부처별 전문 API(청년내일채움공제 등), 첨부파일 다운로드·본문 추출, 비동기 이벤트 기반 파이프라인 분리
