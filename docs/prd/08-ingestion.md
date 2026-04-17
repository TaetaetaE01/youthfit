# PRD — 정책 수집 (ingestion 모듈)

> **모듈**: `com.youthfit.ingestion`
> **우선순위**: P1
> **구현 상태**: 백엔드 수신 API 확장 완료 / 복지로 중앙부처 n8n 워크플로우 완료 / 온통청년 지자체 n8n 워크플로우 대기 (API 키 발급 중)

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

### v0-B — 온통청년 API (스펙 정의, 구현 대기)

| 항목 | 내용 |
|------|------|
| **제공기관** | 청년정책조정위원회 (국무조정실) |
| **도메인** | www.youthcenter.go.kr/go/ythip |
| **sourceType** | `YOUTH_CENTER` |
| **data.go.kr 서비스 ID** | 15128179 (청년정책 통합검색 API) |
| **응답 포맷** | JSON |
| **커버리지** | 지자체(시·도·구) 청년 정책 보충 (복지로 중앙부처와 상호보완) |
| **스코프 (v0)** | 서울특별시(11000) + 서울 25개 자치구(11110~11740) |

#### 엔드포인트 (예정)

```
GET https://www.youthcenter.go.kr/go/ythip/getPlcy
  ?apiKeyNm={인증키}
  &rtnType=json
  &pageNum={페이지}
  &pageSize=100
  &zipCd={행정표준코드, 11110~11740 중 하나}
  &lclsfNm=청년
```

> **주의**: 실제 엔드포인트·파라미터명은 공공데이터포털 인증키 발급 후 응답 샘플로 검증 필요.

#### 수집 예정 데이터

| 필드 | 매핑 대상 | 비고 |
|------|----------|------|
| `plcyNo` | `rawData.externalId` | 온통청년 정책번호 |
| `plcyNm` | `rawData.title` | |
| `plcyExplnCn` | `rawData.summary` | |
| `plcySprtCn` + 관련 텍스트 | `rawData.body` | |
| `sprvsnInstCdNm` | `rawData.organization` | 소관 기관명 |
| `sprtTrgtMinAge` / `sprtTrgtMaxAge` | `rawData.body` 내 포함 | 구조화 연령 필드 (추후 활용) |
| `aplyYmdBgn` / `aplyYmdEnd` | `rawData.applyStart/End` | |
| `zipCd` | `rawData.region` | "서울특별시 종로구" 등 한글 매핑 |
| `plcyKywdNm` | `rawData.themeTags` | 쉼표/공백 구분 |

#### 서울 자치구 코드 (행정표준)

| 코드 | 지역 | 코드 | 지역 |
|------|------|------|------|
| 11000 | 서울특별시 | 11410 | 서대문구 |
| 11110 | 종로구 | 11440 | 마포구 |
| 11140 | 중구 | 11470 | 양천구 |
| 11170 | 용산구 | 11500 | 강서구 |
| 11200 | 성동구 | 11530 | 구로구 |
| 11215 | 광진구 | 11545 | 금천구 |
| 11230 | 동대문구 | 11560 | 영등포구 |
| 11260 | 중랑구 | 11590 | 동작구 |
| 11290 | 성북구 | 11620 | 관악구 |
| 11305 | 강북구 | 11650 | 서초구 |
| 11320 | 도봉구 | 11680 | 강남구 |
| 11350 | 노원구 | 11710 | 송파구 |
| 11380 | 은평구 | 11740 | 강동구 |

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
- 순서에 관계없이 결과가 같도록 `YOUTH_CENTER`가 먼저 들어온 뒤 `BOKJIRO_CENTRAL`이 들어오는 경우에는 `BOKJIRO_CENTRAL`이 별개 정책으로 등록된 후, 다음 주기 스케줄에서 `YOUTH_CENTER` 중복건은 자연 스킵된다.

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

#### 온통청년 수집 (`n8n/workflows/youth-center-seoul.json` — 대기)

```
[Schedule Trigger]          매일 새벽 04:00 (복지로 이후)
       ↓
[Code Node]                 서울+자치구 코드 목록 생성 (11000, 11110..11740)
       ↓
[SplitInBatches]            자치구별 순차 처리
       ↓
[HTTP Request]              /go/ythip/getPlcy 호출 (zipCd별)
       ↓
[Code Node]                 JSON 파싱 → plcyNo 추출
       ↓
[SplitInBatches]            정책별 순차 처리
       ↓
[Wait 1s]                   Rate limit
       ↓
[Code Node]                 IngestPolicyRequest JSON 구성 (sourceType=YOUTH_CENTER)
       ↓
[HTTP Request]              POST /api/internal/ingestion/policies
                            (중복 감지 시 백엔드가 SKIPPED_DUPLICATE 응답)
```

**환경변수 (예정)**:
- `YOUTH_CENTER_API_KEY`: data.go.kr 인증키 (발급 대기 중)

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
- v0-B: 온통청년 지자체 API — 서울 스코프 (대기)
- v1: 온통청년 전국 확대, 부처별 전문 API(청년내일채움공제 등), 첨부파일 다운로드·본문 추출, 비동기 이벤트 기반 파이프라인 분리
