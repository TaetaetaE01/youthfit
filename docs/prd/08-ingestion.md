# PRD — 정책 수집 (ingestion 모듈)

> **모듈**: `com.youthfit.ingestion`
> **우선순위**: P1
> **구현 상태**: 미구현 (package-info.java만 존재)

---

## 유저 스토리

이 모듈은 사용자 직접 대면 기능이 아닌, 내부 데이터 파이프라인이다. n8n 워크플로우가 외부 출처에서 수집한 정책 원천 데이터를 YouthFit 백엔드로 전달하는 수신 표면을 제공한다.

---

## 기능 요구사항

### 정책 데이터 수신

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
    "url": "https://www.youthcenter.go.kr/...",
    "type": "YOUTH_CENTER",
    "fetchedAt": "2026-04-13T10:00:00"
  },
  "rawData": {
    "title": "2026 청년월세지원",
    "body": "...",
    "category": "주거",
    "region": "서울",
    "applyStart": "2026-03-01",
    "applyEnd": "2026-05-31"
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

---

## 크롤링 운영 원칙

- 가능한 범위에서 `robots.txt` 및 출처 정책을 준수한다.
- 식별 가능한 User-Agent를 사용한다.
- Rate limit을 적용하고 보수적으로 수집한다.
- 요약이나 인용으로 충분한 경우 원문 전체를 그대로 노출하지 않는다.
- source URL, source type, source hash를 기록하여 추적 가능성을 확보한다.

---

## 의존 관계

- `ingestion` → `policy.application`: 정규화 전 데이터 전달
- n8n → `ingestion`: HTTP 호출 (단일 intake 엔드포인트)

## 확장 방향

- v0: 동기 순차 수신
- 이후: 비동기 이벤트 기반 파이프라인 분리
