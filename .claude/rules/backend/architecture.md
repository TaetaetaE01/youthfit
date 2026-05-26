# 백엔드 아키텍처 규칙

> 백엔드 코드 수정 전 반드시 확인. `backend/CLAUDE.md` 가 자동 로드.

## DDD + Clean Architecture
- 의존 방향은 반드시 **Presentation → Application → Domain** 을 유지한다.
- Infrastructure 는 포트를 구현할 수 있지만, 의존 방향을 역전시키면 안 된다.
- 트랜잭션 경계는 오직 **Application Service** 에만 둔다.

## 레이어 간 침투 금지
- Controller 응답에 Entity 를 직접 노출하지 않는다.
- `presentation` DTO 를 `application` 또는 `domain` 에서 import 하지 않는다.
- `domain` 레이어에 Spring, JPA, OpenAI SDK 등 프레임워크 의존을 넣지 않는다.
- Controller 가 Repository 에 직접 접근하지 않는다.
- 비즈니스 규칙을 표현하는 동작은 가능하면 도메인 모델 안에 둔다.

## 코드 리뷰 체크 기준
코드 마무리 전에 확인한다:
- 이름이 충분히 명시적인가?
- 도메인 규칙이 Controller 나 Infrastructure 로 새어 나가지 않았는가?
- 프레임워크 타입이 Domain 으로 침투하지 않았는가?
- Entity 가 API 응답으로 직접 노출되지 않았는가?
- 요구사항이 바뀌어도 되돌리기 쉬운 변경인가?
