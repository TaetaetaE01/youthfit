# PRD — 정책 수집 (ingestion 모듈)

> **모듈**: `com.youthfit.ingestion`
> **우선순위**: P1
> **구현 상태**: 백엔드 수신 API 완료 / n8n 크롤링 워크플로우 미구현

---

## 유저 스토리

이 모듈은 사용자 직접 대면 기능이 아닌, 내부 데이터 파이프라인이다. n8n 워크플로우가 외부 출처에서 수집한 정책 원천 데이터를 YouthFit 백엔드로 전달하는 수신 표면을 제공한다.

---

## 크롤링 대상

### v0 — 청년몽땅정보통 (서울시 청년포털)

| 항목 | 내용 |
|------|------|
| **사이트명** | 청년몽땅정보통 |
| **도메인** | youth.seoul.go.kr |
| **sourceType** | `YOUTH_SEOUL` |
| **robots.txt** | 없음 (404) — 별도 크롤링 제한 파일 없음 |
| **선정 이유** | 서울시 청년 정책 전문 포털, 카테고리·신청기간 등 구조화된 데이터 제공 |

#### URL 패턴

**목록 페이지**:
```
GET https://youth.seoul.go.kr/infoData/plcyInfo/ctList.do
  ?key=2309150002
  &tabKind=002
  &pageIndex={페이지 번호, 1-based}
  &orderBy=regYmd+desc
  &blueWorksYn=N
  &sw={검색어, 선택}
```

**상세 페이지**:
```
GET https://youth.seoul.go.kr/infoData/plcyInfo/view.do
  ?plcyBizId={정책 ID, 예: V202600006}
  &tab=001
  &key=2309150002
  &tabKind=002
```

- `plcyBizId` 패턴: `V` + 연도(4자리) + 시퀀스(5자리) (예: `V202600006`)
- `tab=001`: 기본 정보 탭

#### 수집 가능 데이터

| 필드 | 출처 위치 | 매핑 대상 |
|------|----------|----------|
| 정책명 | 상세 페이지 제목 | `rawData.title` |
| 사업 개요·지원 대상·지원 내용·신청 방법 | 상세 페이지 본문 | `rawData.body` |
| 정책 유형 (일자리/주거/교육/복지·문화/참여·권리) | 목록 카테고리 라벨 | `rawData.category` |
| 신청 시작일 | 상세 페이지 신청 기간 | `rawData.applyStart` |
| 신청 마감일 | 상세 페이지 신청 기간 | `rawData.applyEnd` |
| 담당 기관 | 상세 페이지 운영 기관 | `rawData.region` → "서울" 고정 |
| 상세 페이지 URL | `view.do?plcyBizId=...` | `source.url` |
| 첨부파일 | 상세 페이지 내 다운로드 링크 (일부 정책) | 별도 저장 (v1 검토) |

#### 카테고리 매핑

| 청년몽땅정보통 분류 | YouthFit Category |
|-------------------|-------------------|
| 일자리 | JOBS |
| 주거 | HOUSING |
| 교육 | EDUCATION |
| 복지·문화 | WELFARE |
| 참여·권리 | PARTICIPATION |

#### 기술 제약 사항

- **AJAX 기반 동적 렌더링**: 목록의 상세 링크가 `javascript:void(0)`으로 되어 있어, 목록 페이지에서 `plcyBizId`를 추출하려면 렌더링된 HTML 파싱 또는 별도 API 호출 필요
- **페이지네이션**: `pageIndex` 파라미터로 순차 탐색, 마지막 페이지 감지 로직 필요

### v1 — 공공데이터포털 Open API

| 항목 | 내용 |
|------|------|
| **사이트명** | 공공데이터포털 |
| **도메인** | data.go.kr |
| **sourceType** | `DATA_GO_KR` |
| **방식** | Open API 호출 (크롤링 아님) |
| **비고** | API 키 발급 필요, 호출 제한 있음, 구조화된 JSON 응답 |

v1에서 공공데이터포털 API를 추가 연동하여 전국 단위 정책 데이터를 수집한다.

---

## 기능 요구사항

### 정책 데이터 수신 (백엔드 — 구현 완료)

**설명**: n8n 또는 수집 워크플로우가 호출하는 내부 intake 엔드포인트. 원천 정책 데이터를 수신하고, 검증 후 policy 모듈로 전달한다.

**비즈니스 규칙**:
- 내부 API key 인증 (공용 internal API key)
- 입력 형태(필수 필드, 포맷) 검증
- n8n은 이 단일 intake 엔드포인트만 호출하고, 다른 도메인 엔드포인트를 직접 건드리지 않음
- raw 데이터를 수신하고, 정규화는 policy 모듈에서 처리

**API 스펙**:

```
POST /api/internal/ingestion/policies
X-Internal-Api-Key: {apiKey}
Content-Type: application/json
```

**요청**:
```json
{
  "source": {
    "url": "https://youth.seoul.go.kr/infoData/plcyInfo/view.do?plcyBizId=V202600006&tab=001&key=2309150002&tabKind=002",
    "type": "YOUTH_SEOUL",
    "fetchedAt": "2026-04-14T03:00:00"
  },
  "rawData": {
    "title": "2026 청년 부동산 중개보수 및 이사비 지원사업",
    "body": "사업개요: 이사가 잦은 청년가구의 주거비 부담을 완화...\n지원대상: 서울로 전입 또는 서울 내에서 이사한 19~39세 청년\n지원내용: 최대 40만원 한도 내 실비 지원 (생애 1회)\n신청방법: 청년몽땅정보통 온라인 신청",
    "category": "주거",
    "region": "서울",
    "applyStart": "2026-04-01",
    "applyEnd": "2026-04-14"
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

### n8n 크롤링 워크플로우 (미구현)

**설명**: 청년몽땅정보통 사이트를 주기적으로 크롤링하여 정책 데이터를 수집하고, 백엔드 intake API로 전달하는 n8n 워크플로우.

**워크플로우 흐름**:

```
[Schedule Trigger]          매일 새벽 03:00
       ↓
[HTTP Request]              목록 페이지 요청 (pageIndex=1부터 순차)
       ↓
[HTML Extract]              정책 항목에서 plcyBizId 추출
       ↓
[Loop / Split In Batches]   각 정책별 반복 처리
       ↓
[Wait 2~3초]                Rate limit (요청 간 딜레이)
       ↓
[HTTP Request]              상세 페이지 요청 (view.do?plcyBizId=...)
       ↓
[HTML Extract]              제목, 본문, 카테고리, 신청기간 추출
       ↓
[Code Node]                 IngestPolicyRequest JSON 변환
       ↓
[HTTP Request]              POST /api/internal/ingestion/policies
                            Header: X-Internal-Api-Key
       ↓
[IF 다음 페이지 존재]        pageIndex 증가 후 목록 요청 반복
       ↓
[Error Handler]             실패 시 로깅 (Slack/Email 알림)
```

**비즈니스 규칙**:
- 전체 페이지 순차 탐색 (pageIndex 1부터 마지막까지)
- 요청 간 2~3초 딜레이 (Rate limit)
- SHA-256 해시 기반 중복 제거는 백엔드에서 처리
- 워크플로우 JSON은 `n8n/workflows/` 디렉토리에 Git 관리
- 민감값(API 키 등)은 n8n 환경변수로 분리, JSON에 포함 금지

---

## 크롤링 운영 원칙

- `robots.txt`가 없으나, 수집 예절을 준수한다.
- 식별 가능한 User-Agent를 사용한다: `YouthFit-Bot/1.0 (+https://youthfit.kr/bot)`
- Rate limit을 적용하고 보수적으로 수집한다 (요청 간 2~3초 딜레이).
- 요약이나 인용으로 충분한 경우 원문 전체를 그대로 노출하지 않는다.
- source URL, source type, source hash를 기록하여 추적 가능성을 확보한다.
- 첨부파일은 v0에서 URL만 기록하고, 실제 다운로드·저장은 v1에서 검토한다.

---

## 의존 관계

- `ingestion` → `policy.application`: 정규화 전 데이터 전달
- n8n → `ingestion`: HTTP 호출 (단일 intake 엔드포인트)

## 확장 방향

- v0: 청년몽땅정보통 HTML 크롤링 (n8n 워크플로우) + 동기 순차 수신
- v1: 공공데이터포털 Open API 연동, 첨부파일 다운로드·저장, 비동기 이벤트 기반 파이프라인 분리
