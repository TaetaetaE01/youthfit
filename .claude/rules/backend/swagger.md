# Swagger (OpenAPI) 규칙

> Swagger 어노테이션은 Controller 가 아닌 `{도메인}Api` 인터페이스에 작성한다.

## 인터페이스 작성
- 새 Controller 를 추가할 때 반드시 같은 패키지에 `{도메인}Api` 인터페이스를 먼저 만들고, Controller 가 이를 `implements` 한다.
- 인터페이스에 `@Tag` 를 클래스 레벨에, `@Operation` 을 각 메서드에 붙인다.
  - `@Tag(name = "한글 그룹명", description = "한 줄 설명")`
  - `@Operation(summary = "동작 요약", description = "상세 설명")`
- PathVariable, 필수 RequestParam 에는 인터페이스 메서드 파라미터에 `@Parameter(description = "...")` 를 붙인다.
- Request/Response DTO record 필드에는 `@Schema(description = "...")` 를 필요에 따라 붙인다.
- 인증이 불필요한 엔드포인트에는 `@SecurityRequirements` (빈 값) 를 붙여 Swagger UI 에서 자물쇠를 제거한다.

## Controller 측 규칙
- Controller 에는 Swagger 어노테이션(`@Tag`, `@Operation`, `@Parameter`, `@ApiResponses`) 을 두지 않는다.
- Controller 에는 Spring MVC 어노테이션(`@GetMapping`, `@RequestParam` 등) 만 둔다.

## 에러 응답 명세
각 메서드에 `@ApiResponses` 로 에러 응답을 명세한다. 해당 엔드포인트가 실제로 발생시킬 수 있는 `ErrorCode` 에 맞춰 작성한다.

- `@ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)")`
- `@ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)")`
- `@ApiResponse(responseCode = "403", description = "접근 권한이 없습니다 (YF-003)")`
- `@ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")`
- `@ApiResponse(responseCode = "409", description = "이미 존재하는 리소스입니다 (YF-005)")`
- `@ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")`

에러 응답 description 에는 `ErrorCode` 의 코드(YF-xxx) 를 괄호로 함께 표기한다.
