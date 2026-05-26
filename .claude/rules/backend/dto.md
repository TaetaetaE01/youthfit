# DTO 규칙

> DTO 는 반드시 Java `record` 로 생성한다. 클래스 기반 DTO 를 사용하지 않는다.

## Presentation Layer
- Request DTO 이름은 `Request` 로 끝난다.
- Response DTO 이름은 `Response` 로 끝난다.

## Application Layer
- 입력 DTO 이름은 `Command` 로 끝난다.
- 출력 DTO 이름은 `Result` 로 끝난다.

## 변환 책임
- Request DTO 는 Command 로 변환한다.
- Response DTO 는 Result 로부터 생성한다.

## 예시
```java
public record CreatePolicyRequest(String title, ...) {}
public record CreatePolicyCommand(String title, ...) {}
public record PolicyResult(Long id, String title, ...) {}
public record PolicyResponse(Long id, String title, ...) {}
```
