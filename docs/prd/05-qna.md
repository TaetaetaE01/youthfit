# PRD — 정책 Q&A (qna 모듈)

> **기능 ID**: F-07
> **모듈**: `com.youthfit.qna`
> **우선순위**: P1
> **구현 상태**: 미구현 (package-info.java만 존재)
> **연관 모듈**: `com.youthfit.rag` (벡터 검색), `com.youthfit.guide` (가이드 생성)

---

## 유저 스토리

### 출처 기반 질의응답
서연(페르소나 C)이 정책 상세 페이지에서 "이 정책 재학생도 가능해요?" 라고 질문한다. 시스템이 해당 정책의 인덱싱된 원문을 검색하고, 관련 구간을 근거로 답변한다. 원문에 재학생 관련 명시적 언급이 없으면 "해당 정책 원문에 재학생 자격에 대한 명시적 언급이 없습니다. 공식 문의처(OOO)에서 확인하시는 것을 권장합니다."와 같이 불확실성을 인정하고, 원문의 자격 요건 섹션을 인용하여 보여준다.

### 스트리밍 응답
답변은 실시간 스트리밍으로 표시되어 사용자가 생성 진행 상황을 확인할 수 있다. 답변 하단에 근거가 된 원문 출처(정책명, 섹션, 문단)가 링크로 표시된다.

---

## 기능 요구사항

### F-07-1. 정책 질문

**설명**: 특정 정책에 대해 자연어로 질문하면 인덱싱된 원문을 근거로 답변을 생성한다.

**비즈니스 규칙**:
- 인증 필수 (비로그인 핫패스에서 LLM 호출 방지)
- 답변은 반드시 인덱싱된 원문에 근거
- 원문에 없는 내용을 지어내지 않음
- 불확실한 경우 불확실성을 인정하고 공식 문의처 안내
- 스트리밍 응답 지원 (SSE)

**API 스펙**:

```
POST /api/v1/qna/ask
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: text/event-stream
```

**요청**:
```json
{
  "policyId": 1,
  "question": "이 정책 재학생도 신청 가능한가요?"
}
```

**응답 (200 OK, SSE 스트림)**:
```
data: {"type": "CHUNK", "content": "해당 정책의 "}
data: {"type": "CHUNK", "content": "자격 요건을 확인한 결과, "}
data: {"type": "CHUNK", "content": "재학생에 대한 명시적 언급은 없습니다."}
data: {"type": "SOURCES", "sources": [{"policyId": 1, "section": "자격 요건", "excerpt": "만 19세~34세 청년으로서..."}]}
data: {"type": "DONE"}
```

---

## Q&A 원칙

시스템은 다음을 지켜야 한다:
1. 출처 기반으로 답변한다.
2. 원문에 없는 요구사항을 지어내지 않는다.
3. 원문이 불완전하거나 모호하면 불확실성을 인정한다.
4. 과도한 해석보다 관련 공고 구간의 인용·근거 제시를 우선한다.

---

## 데이터 모델 (미구현)

### QnaHistory

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| userId | Long | 질문자 FK |
| policyId | Long | 대상 정책 FK |
| question | TEXT | 사용자 질문 |
| answer | TEXT | 생성된 답변 |
| sources | JSON | 근거 출처 목록 |
| createdAt | LocalDateTime | 생성 시각 |

---

## 의존 관계

- `qna` → `rag`: 벡터 검색으로 관련 문서 청크 조회
- `qna` → `policy.domain`: 정책 정보 조회
- `rag` → OpenAI API: 임베딩 생성 및 LLM 답변 생성

## 연관 모듈: rag

### PolicyDocument (미구현)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| policyId | Long | 정책 FK |
| chunkIndex | int | 청크 순서 |
| content | TEXT | 청크 텍스트 |
| embedding | vector | 임베딩 벡터 |

### rag 모듈 책임
- 문서 청크 분할
- 임베딩 생성
- 벡터 조회 (유사도 검색)

## 연관 모듈: guide

### Guide (미구현)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| policyId | Long | 정책 FK |
| summaryHtml | TEXT | 구조화된 가이드 HTML |
| generatedAt | LocalDateTime | 생성 시각 |
| sourceHash | String | 변경 감지용 해시 |

### guide 모듈 책임
- 원문 기반 구조화된 가이드 사전 생성
- 캐시된 요약 관리
- source hash 기반 변경 감지 시 재생성

---

## 비용 방어

- 비로그인 사용자에게 Q&A 기능 비활성화 (LLM 호출 차단)
- 동일 질문에 대한 답변 캐시 검토
- source hash 기반 임베딩 재계산 최소화
- 가이드는 사전 생성 우선, 실시간 생성 최소화
