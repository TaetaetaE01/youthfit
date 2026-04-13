# ARCHITECTURE.md

## 아키텍처 스타일
YouthFit은 **DDD + Clean Architecture**를 따른다.

주요 목표:
- 모듈 경계를 보존한다.
- 비즈니스 규칙을 프레임워크 관심사로부터 분리한다.
- 외부 연동을 교체 가능하게 유지한다.
- 향후 확장 시 거대한 단일 서비스 레이어로 붕괴하지 않도록 한다.

## 레이어 규칙
각 도메인 중심 모듈은 충분히 커지면 아래 레이어 구조를 따른다.

```text
com.youthfit.{module}/
├── domain/
│   ├── model/
│   └── repository/
├── application/
│   ├── service/
│   └── dto/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   └── config/
└── presentation/
    ├── controller/
    └── dto/
```

### 의존 방향
- `presentation` 은 `application` 에 의존한다.
- `application` 은 `domain` 에 의존한다.
- `infrastructure` 는 필요한 포트 또는 어댑터를 구현한다.
- `domain` 은 프레임워크 비의존 상태를 유지해야 한다.

## 모듈 책임
### `ingestion`
- n8n 또는 수집 워크플로우로부터 원천 정책 데이터를 수신한다.
- 입력 형태를 검증한다.
- 정규화 전 raw 데이터를 정책 파이프라인으로 넘긴다.

### `policy`
- 정책 aggregate 및 메타데이터를 관리한다.
- 정규화와 중복 제거를 담당한다.
- 게시 상태와 생명주기 규칙을 관리한다.

### `rag`
- 문서 저장소 참조를 관리한다.
- 청크 분할을 수행한다.
- 임베딩을 생성한다.
- 벡터 조회를 수행한다.

### `guide`
- 원문 기반의 구조화된 가이드를 사전 생성한다.
- 사용자 설명용 캐시된 요약을 관리한다.

### `eligibility`
- 사용자 프로필과 정책 조건을 바탕으로 규칙 기반 판정을 수행한다.
- 불명확한 조건을 명시적으로 추적한다.

### `qna`
- 검색 + 생성 오케스트레이션을 담당한다.
- 스트리밍 응답을 조합한다.
- 근거 출처를 응답에 연결한다.

### `auth`
- 소셜 로그인(카카오 OAuth)을 담당한다.
- JWT 발급, 갱신, 검증을 관리한다.
- `user.domain`에 의존하여 사용자 조회 및 생성을 수행한다.

### `user`
- 프로필을 관리한다.
- 북마크를 관리한다.
- 알림 선호와 스케줄링 데이터를 관리한다.

## 트랜잭션 경계
- 트랜잭션은 오직 `application/service` 에 둔다.
- 도메인 모델은 규칙을 강제하지만, 트랜잭션을 시작하거나 관리하지 않는다.
- Controller는 HTTP 관심사만 처리한다.

## DTO 경계
예상 흐름:

```text
HTTP Request
-> presentation request DTO
-> application command DTO
-> application service
-> application result DTO
-> presentation response DTO
-> HTTP Response
```

규칙:
- Controller에서 Entity를 직접 반환하지 않는다.
- Domain 로직 안에서 presentation DTO를 사용하지 않는다.
- Infrastructure의 persistence 모델이 API 응답으로 새어 나오지 않게 한다.

## 외부 연동
### n8n
n8n은 여러 도메인 엔드포인트를 직접 건드리지 않고, 제한된 내부 ingestion 표면만 호출해야 한다.

권장 패턴:
- raw source ingestion용 단일 intake endpoint
- 공용 internal API key
- 실제 도메인 처리는 모두 백엔드 내부에서 수행

### LLM 사용
LLM 및 임베딩 처리는 비용을 고려해야 한다.

필수 방어 장치:
- source hash 또는 동등한 방식의 변경 감지
- 가능한 경우 생성 결과 캐시
- 비로그인 사용자 요청 핫패스에서 고비용 생성 직접 금지
- 재사용 가능한 guide 데이터는 사전 생성 우선

## 데이터 진화 원칙
MVP에서 시작해 더 고도화된 파이프라인으로 확장 가능한 설계를 선호한다.

예시:
- 현재의 순차 ingestion은 이후 비동기 단계로 분리 가능해야 한다.
- 현재의 규칙 기반 적합도 판정은 이후 더 풍부한 설명과 감사 메타데이터를 붙일 수 있어야 한다.
- 현재의 벡터 단독 검색은 이후 하이브리드 검색으로 확장 가능해야 한다.

## 아키텍처 의사결정 체크 질문
구현 또는 수정 전에 아래를 확인한다:
1. 이 동작은 어느 모듈이 소유하는가?
2. 이것은 비즈니스 규칙인가, 오케스트레이션 관심사인가, 인프라 관심사인가?
3. 현재 의존 방향은 올바른가?
4. 이 변경이 이후 분리나 확장을 더 어렵게 만들지 않는가?
