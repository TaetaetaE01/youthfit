# Q&A SSE 답변이 빈 채로 끊기던 문제 — virtual-thread 로의 SecurityContext 미전파

- 작성일: 2026-04-30
- 작성자: TaetaetaE01
- 관련 커밋: `7a537cb` (`fix(qna): SecurityContext 를 virtual-thread executor 로 전파`)
- 관련 PR: _(아직 없음, feat/qna-v0-ready 브랜치 작업 중)_
- 관련 모듈: `backend/qna`, `backend/auth` (Spring Security 연동)

## 한 줄 요약

> 로그인 사용자의 Q&A 호출이 controller 진입까지는 통과하는데 SSE async dispatch 시점에서
> Spring Security 가 SecurityContext 를 다시 검사하다 익명 사용자로 떨어져 답변이 빈 박스로
> 끊기던 문제. virtual-thread executor 를 `DelegatingSecurityContextExecutorService` 로
> 래핑해 caller 의 SecurityContext 를 task 실행 시점에 전파하도록 수정.

## 1. 상황 (Context)

- 작업: Q&A v0 출시 마무리. 정책 상세에서 "이 정책에 대해 질문" 흐름 검수 중.
- 증상: 로그인된 사용자가 `/api/qna` 로 질문을 보내면 프론트의 답변 박스가 **빈 채로** 표시되고
  곧바로 닫힘. 비로그인 사용자는 정상적으로 401 응답.
- 백엔드 로그 (반복):
  ```
  AuthorizationDeniedException: Access Denied
  IllegalStateException: response is already committed
  ```
- 영향 범위: production 빌드 기준 **로그인 사용자 전체**의 Q&A 가 동작 불능. 비로그인은 의도대로
  거절. 즉 인증된 호출이 SSE async dispatch 단계에서만 무너지는 형태.

## 2. 원인 (Root Cause)

- `QnaService` 는 SSE 스트리밍을 위해 자체 `ExecutorService` 로 비동기 처리를 분리.
  ```java
  // 변경 전
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  ```
- Spring Security 의 `SecurityContextHolder` 기본 strategy 는 `ThreadLocal` 기반.
  Servlet thread 에서 가지고 있던 인증 정보는 caller 스레드의 ThreadLocal 에 들어 있는데,
  **virtual thread 로 task 가 옮겨가면 ThreadLocal 이 따라가지 않음**.
- SSE 의 async dispatch 가 실행될 때 Spring Security 필터가 다시 한 번 SecurityContext 를
  확인하는데, 이 시점에서 컨텍스트가 비어 있어 익명 사용자로 인식 → `AuthorizationDeniedException`.
- emitter 는 이미 헤더를 commit 한 상태라 예외 핸들러가 응답을 재작성하려 하면서
  `response is already committed` 까지 연쇄.
- 핵심 코드 경로: `backend/.../qna/application/service/QnaService.java:63` (executor 선언),
  `:81` (executor.execute 진입점).

## 3. 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. `DelegatingSecurityContextExecutorService` 로 wrap | Spring Security 가 공식으로 제공하는 표준 패턴. 기존 virtual-thread executor 를 그대로 두고 한 줄 wrap 만 추가. 한 곳만 바뀜. | virtual thread 위에 한 단계 래퍼가 더 생기지만 오버헤드 미미. |
| B. SecurityContextHolder strategy 를 `MODE_INHERITABLETHREADLOCAL` 로 변경 | 글로벌하게 한 번만 설정하면 끝. | platform thread 가정의 InheritableThreadLocal 동작이 virtual thread 풀에서 신뢰성이 떨어지고, 부수 효과 범위가 너무 넓음(전 모듈에 영향). |
| C. `@Async` + Spring 의 SecurityContext propagating task decorator 로 재작성 | 프레임워크 패턴에 더 가까움. | 현재 SSE/emitter 흐름은 직접 `executor.execute()` 로 짠 상태라 `@Async` 로 옮기면 SseEmitter 라이프사이클을 다시 짜야 함. 변경 범위 과대. |
| D. SSE 를 Reactor `Flux<ServerSentEvent>` 로 재작성 | 컨텍스트 전파를 Reactor Context 로 깔끔하게 처리 가능. | v0 마무리 단계에서 도입 비용 과대. 스택 변경 (Web MVC → WebFlux 부분 도입). |

## 4. 선택과 이유 (Decision)

- **채택한 대안: A. `DelegatingSecurityContextExecutorService` wrap.**
- **결정의 핵심 근거**:
  1. 변경 범위가 한 줄 — `QnaService` 의 executor 선언만 바뀜. 다른 모듈·전역 설정 영향 없음.
  2. Spring Security 가 공식으로 제공하는 표준 wrapper 라 가역성·신뢰도 모두 높음.
  3. v0 출시 마무리 단계의 시간 제약. B/C/D 는 모두 v0 이후의 리팩토링 시점에 검토할 만함.
- **트레이드오프로 받아들인 것**:
  - virtual thread executor 위에 SecurityContext-aware wrapper 한 단계가 더 생김.
  - "SecurityContext 가 필요한 비동기 작업이라면 같은 패턴을 매번 손으로 적용"해야 함 — 향후
    유사 케이스가 늘면 공통 task decorator 를 두는 방향으로 일반화 검토.
- **가역성**: 매우 높음. 한 줄 되돌리면 원복.
- **재검토 신호**: 다른 모듈에서도 같은 패턴이 두 곳 이상 등장하면 공통 `SecurityContextAware
  VirtualThreadExecutor` 빈으로 끌어올림.

## 5. 해결 (Solution)

- 변경 파일: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- 변경 내용:
  ```java
  // 변경 후 (라인 63~64)
  private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
          Executors.newVirtualThreadPerTaskExecutor());
  ```
- import 추가: `org.springframework.security.concurrent.DelegatingSecurityContextExecutorService`
- 부수 효과 (마이그레이션·환경변수): _(없음)_

## 6. 검증 (Result)

- 재현 시나리오 (수정 후):
  1. 카카오 로그인 → 정책 상세 진입 → "이 정책에 대해 질문" → 임의의 질문 입력
  2. 답변 박스에 토큰 단위 스트리밍 출력 확인
- 백엔드 로그: 직전 반복 출력되던 `AuthorizationDeniedException` / `response is already committed`
  소거 확인.
- 회귀 위험: 낮음. SSE 외 일반 동기 호출 경로는 변경 없음.
- 모니터링 포인트:
  - `qna_history.status` 분포 — `IN_PROGRESS` 잔존 비율이 비정상적으로 높아지면 다른 비동기
    경로 누수 가능성. (운영 노트 `2026-04-30-qna-v0-ready-runbook.md` §5 참조)

## 7. 후속 / 미결 (Follow-ups)

- (선택) virtual thread + SecurityContext 패턴이 다른 모듈에서도 필요해지면
  공통 `TaskExecutor` 빈으로 추출.
- (선택) SSE 통합 테스트 커버 — 인증 사용자/비인증 사용자 양쪽의 Q&A 호출 e2e 시나리오.
  현재는 수동 검증.
- (스택 정비) v0 이후 검토: SSE 를 Reactor 기반으로 재작성할지 — 컨텍스트 전파/취소 시그널을
  Reactor Context 로 일원화 가능하나 본 마일스톤 이후 작업.

## 8. 참고 (References)

- 관련 spec: `docs/superpowers/specs/2026-04-30-qna-v0-ready.md`
- 관련 운영 노트: `docs/superpowers/operations/2026-04-30-qna-v0-ready-runbook.md`
- Spring Security 문서: `DelegatingSecurityContextExecutorService` (Concurrency support)
