# Lombok · Entity · 예외 · Controller 규칙

## Lombok 허용
- `@Getter`
- `@Builder`
- `@RequiredArgsConstructor`
- 필요한 경우 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

## Lombok 지양
- `@Data`
- `@Setter`
- Domain Entity 에 대한 public all-args constructor

## Entity 및 도메인 모델
- 비즈니스 상태 변경을 위한 public setter 를 두지 않는다.
- 상태 변경은 의미 있는 도메인 메서드로 표현한다.
- 반복되는 비즈니스 규칙은 실제로 aggregate 에 속하는 경우 도메인 모델로 이동한다.
- Entity 를 단순 데이터 컨테이너로만 사용하지 않는다.

## 예외 처리
- 도메인 전용 커스텀 예외를 사용한다.
- 전역 예외 핸들러에서 일관되게 매핑한다.
- 안정적인 API 에러 구조를 반환한다.
- persistence 또는 프레임워크 내부 오류 상세를 그대로 클라이언트에 노출하지 않는다.

## Controller 규칙
- Controller 는 HTTP 관심사만 처리한다.
- 검증은 요청 경계에서 수행한다.
- Controller 안에 비즈니스 규칙을 넣지 않는다.
- Controller 가 Repository 에 직접 접근하지 않는다.
