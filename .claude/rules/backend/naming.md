# 백엔드 네이밍 규칙

> 비즈니스 의도가 드러나는 이름을 사용한다.

## Service 메서드 동사
명확한 동사를 선호한다:
- `find...` — 조회
- `register...` / `create...` — 생성
- `change...` / `update...` — 상태/값 변경
- `cancel...` / `delete...` — 취소/삭제
- `judge...` / `evaluate...` — 적합도 또는 규칙 평가
- `search...` / `retrieve...` — 검색
- `generate...` — LLM 또는 파생 콘텐츠 생성
- `send...` — 외부 알림 발송

아래처럼 모호한 이름은 피한다: `get()`, `save()`, `check()`, `list()`.

## Swagger Api 인터페이스 네이밍
- 새 Controller 를 추가할 때 반드시 같은 패키지에 `{도메인}Api` 인터페이스를 먼저 만든다.
- Controller 는 그 인터페이스를 `implements` 한다.
- 예: `PolicyApi` 인터페이스 → `PolicyController implements PolicyApi`.
