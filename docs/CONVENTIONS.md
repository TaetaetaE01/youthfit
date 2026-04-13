# CONVENTIONS.md

## 네이밍
비즈니스 의도가 드러나는 이름을 사용한다.

### Service 메서드 네이밍
명확한 동사를 선호한다:
- `find...` : 조회
- `register...` 또는 `create...` : 생성
- `change...` 또는 `update...` : 상태/값 변경
- `cancel...` 또는 `delete...` : 취소/삭제
- `judge...` 또는 `evaluate...` : 적합도 또는 규칙 평가
- `search...` 또는 `retrieve...` : 검색 및 조회
- `generate...` : LLM 또는 파생 콘텐츠 생성
- `send...` : 외부 알림 발송

아래처럼 모호한 이름은 피한다:
- `get()`
- `save()`
- `check()`
- `list()`

## DTO 규칙
- **DTO(Command, Result, Request, Response)는 반드시 Java `record`로 생성한다.** 클래스 기반 DTO를 사용하지 않는다.

### Presentation Layer
- Request DTO 이름은 `Request` 로 끝난다.
- Response DTO 이름은 `Response` 로 끝난다.

### Application Layer
- 입력 DTO 이름은 `Command` 로 끝난다.
- 출력 DTO 이름은 `Result` 로 끝난다.

### 변환 책임
- Request DTO는 Command로 변환한다.
- Response DTO는 Result로부터 생성한다.

## Entity 및 도메인 모델 규칙
- 비즈니스 상태 변경을 위한 public setter를 두지 않는다.
- 상태 변경은 의미 있는 도메인 메서드로 표현한다.
- 반복되는 비즈니스 규칙은 실제로 aggregate에 속하는 경우 도메인 모델로 이동한다.
- Entity를 단순 데이터 컨테이너로만 사용하지 않는다.

## Lombok 규칙
선별적으로 허용:
- `@Getter`
- `@Builder`
- `@RequiredArgsConstructor`
- 필요한 경우 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

기본적으로 지양:
- `@Data`
- `@Setter`
- Domain Entity에 대한 public all-args constructor

## 예외 처리
- 도메인 전용 커스텀 예외를 사용한다.
- 전역 예외 핸들러에서 일관되게 매핑한다.
- 안정적인 API 에러 구조를 반환한다.
- persistence 또는 프레임워크 내부 오류 상세를 그대로 클라이언트에 노출하지 않는다.

## Controller 규칙
- Controller는 HTTP 관심사만 처리한다.
- 검증은 요청 경계에서 수행한다.
- Controller 안에 비즈니스 규칙을 넣지 않는다.
- Controller가 Repository에 직접 접근하지 않는다.

## 코드 리뷰 체크 기준
코드 마무리 전에 확인한다:
- 이름이 충분히 명시적인가?
- 도메인 규칙이 Controller나 Infrastructure로 새어 나가지 않았는가?
- 프레임워크 타입이 Domain으로 침투하지 않았는가?
- Entity가 API 응답으로 직접 노출되지 않았는가?
- 요구사항이 바뀌어도 되돌리기 쉬운 변경인가?
