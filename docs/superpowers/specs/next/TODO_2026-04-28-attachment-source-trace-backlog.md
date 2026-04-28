# 첨부 단위 출처 trace (sourceField=ATTACHMENT 확장) — Backlog

> **상태**: backlog (브레인스토밍 전)
> **연관 spec**: `docs/superpowers/specs/DONE_2026-04-28-guide-accuracy-income-bracket-design.md` §14 비범위 "청크-단위 attachment_id trace"
> **연관 사이클**: 가이드 정확도 강화 (highlights/pitfalls 도입) — 이미 머지된 PR
> **트리거**: 사용자 요청 (2026-04-28 manual smoke 후속)

---

## 배경 / 동기

가이드 highlights·pitfalls 항목은 현재 `sourceField` 라벨로 출처를 표시한다. enum은 4종:
- `SUPPORT_TARGET` / `SELECTION_CRITERIA` / `SUPPORT_CONTENT` / `BODY`

이 4종은 모두 정책 API의 **구조화 필드**를 가리킨다. 첨부 PDF 텍스트는 RAG 청크에 합쳐져 LLM 입력에 포함되지만, 항목별로 "이 정보는 첨부 N의 어디서 왔다"를 추적할 방법이 없다.

사용자가 manual smoke 중 발견한 시나리오:
- 정책 7번 첨부 PDF 35페이지에 "비슷한 정책 중복 수혜 안 되는 리스트"가 있음 — 가이드의 highlights/pitfalls에 들어가야 사용자가 PDF를 안 보고도 의사결정 가능.
- 정보가 가이드에 들어왔을 때 사용자가 "어디서 가져온 정보인지" 클릭으로 검증할 수 있어야 신뢰도 ↑.

본 사이클(가이드 정확도 강화)에서 (a) 콘텐츠 품질 강화는 프롬프트 한 줄 추가로 즉시 반영했지만, (b) 출처 trace 구조 확장은 별도 사이클로 미뤘다.

---

## 핵심 질문 (브레인스토밍 진입 시 결정 필요)

1. **새 sourceField 추가**: `ATTACHMENT` 단일 enum vs 첨부별 식별자(`ATTACHMENT_1`, `ATTACHMENT_2` …) vs 첨부 ID + 페이지 번호 객체.
2. **trace 정밀도**: 첨부 단위(어느 PDF) / 청크 단위(어느 청크) / 페이지 단위 (어느 페이지) / 문장 단위(어떤 문장).
3. **LLM에 어떻게 입력**: 청크 헤더에 `[attachment-id=N, page=P, chunk=C]` 같은 메타 주입 → LLM이 응답에 그대로 포함하도록 강제.
4. **UI 동작**: 항목 클릭 시 첨부 PDF 열기 + 페이지 점프 (PDF viewer 통합 필요?), 또는 단순히 `AttachmentSection`의 해당 첨부로 스크롤.
5. **기존 가이드 마이그레이션**: 새 sourceField 도입 시 기존 가이드는 enum value 추가만으로 안전하게 동작? `prompt.version` 증분 → 자동 재생성 경로 활용.
6. **검증 4 (sourceField 유효성) 확장**: 첨부가 없는 정책에서 `ATTACHMENT*` sourceField가 등장하면 폐기.

---

## 참고 — 현재 시스템에서 활용 가능한 데이터

- `policy_attachment` 테이블: `id, policy_id, name, url, storage_key, extracted_text, extraction_status` 등 (Task 1 사이클에서 도입).
- `policy_document` 테이블: 청크화된 텍스트 (정책 본문 + 첨부 추출 텍스트 합쳐짐). 현재 청크에는 출처 메타 없음 — 청크 헤더에 `=== 정책 본문 ===` / `=== 첨부: {name} ===` 마커만 있음.
- LLM 입력의 `[chunk-N]` 라벨: 현재 chunk index만 있고 첨부 매핑 없음.

trace 구현 시 필요할 변경:
- `AttachmentReindexService.reindex()`의 mergedContent에 첨부 헤더 포맷 강화 (예: `=== 첨부 attachment-id=12 page=35 ===`)
- LLM user message에 "출처 라벨은 청크 헤더의 `attachment-id`·`page` 값을 그대로 인용한다" 규칙 추가
- 응답 schema에 `sourceField`를 string에서 객체로 확장 (`{ kind: ATTACHMENT, attachmentId: 12, page: 35 }`) — JSON schema migration 필요

---

## 트레이드오프

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. 첨부 단위 trace** (`ATTACHMENT_<id>` enum) | 단순 enum 확장. UI에선 첨부 N으로 스크롤 정도면 충분 | 페이지 단위 정밀도 없음. PDF 35페이지 같은 deep link 못함 |
| **B. 페이지 단위 trace** (`{ attachmentId, page }` 객체) | 사용자 의도와 일치 — "PDF 35페이지에 이 정보 있다" 검증 | LLM 응답 schema 객체화 필요. 청크 헤더에 페이지 메타 주입해야 함 (Tika가 페이지 정보 보존?) |
| **C. 청크 단위 trace** (`{ chunkId }`) | 우리 시스템에서 가장 자연스러움 (청크는 이미 식별자 있음) | 사용자에게 "청크"는 노출 적절치 않음. UI에선 청크 → 페이지/첨부 역매핑 필요 |

---

## 권장 다음 단계

1. **별도 brainstorming 사이클** 시작 — 위 6개 질문을 한 번에 하나씩 정제
2. spec 작성: `docs/superpowers/specs/<date>-attachment-source-trace-design.md`
3. plan 작성 후 단일 PR 또는 분할 PR 결정
4. 의존: 첨부 추출 파이프라인이 페이지 메타를 보존하는지 사전 확인 (Tika는 PDF 페이지 단위 추출 가능)

---

## 비범위 / 후속의 후속

- 항목별 텍스트 highlight (PDF viewer에서 정확한 문장 강조) — v0.x+
- 다국어 PDF 처리 — 본 trace 와 무관
- OCR 스캔 PDF 텍스트화 — 현재 `SKIPPED(SCANNED_PDF)` 처리. trace 도입과 별개

---

## 별도 후속: 환산값(만원) 표기 안정화

**증상**: 시스템 프롬프트 v3 + 검증 2 (bullet 단위 강화) 도입 후, 정책 7번 paired에 "중위소득 50% 이하" 같은 표기는 들어가지만 우리 reference yaml의 환산값(예: "약 117만원")이 LLM 출력에 자주 누락된다. 검증 2가 retry를 트리거하지만 gpt-4o-mini 가 retry 후에도 환산값을 같은 bullet 안에 안 붙임.

**원인 추정**:
- 시스템 프롬프트가 길어지면서 (원칙 1~9 + 변환 예시 1~5 + [중요] 블록) LLM 주의가 분산.
- gpt-4o-mini의 instruction following 한계 — 두 마리 토끼(PDF 디테일 + 환산값) 동시 만족 어려움.
- v2(환산값 ✓ / PDF 디테일 약함) ↔ v3(PDF 디테일 ✓ / 환산값 약함) 트레이드오프 관찰.

**해결 후보** (별도 사이클에서 A/B 비교):
- (A) **모델 업그레이드** — gpt-4o-mini → gpt-4o. 비용 ~10x ↑, instruction following 강력. 운영 비용 영향 평가 필요.
- (B) **결정적 후처리** — LLM 출력에 "중위소득 N% 이하" 패턴 등장 시 우리 코드가 정규식으로 reference yaml 환산값을 자동 삽입. spec §8.4에서 도입 안 함으로 결정했지만, 환산값 안정화 가치가 더 크면 재검토.
- (C) **프롬프트 분해** — 1차 호출에서 paired/highlights/pitfalls 생성 → 2차 호출에서 paired만 환산값 강제 추가. 비용 1.x↑ but 안정성 ↑.
- (D) **환산값 표기를 위한 별도 LLM 호출** — paired 결과를 입력으로 받아 "환산값 표기"만 강제. 후처리 LLM 패턴.

**현재 작업으로 인한 부분 진보**:
- validator의 검증 2를 group 단위 → bullet 단위로 변경 → retry 트리거 정확도는 강화됨.
- retry 발동 → LLM 못 고침 → 1차 응답 저장 (현재 상태).
- 이 retry 이벤트를 메트릭으로 노출하면 환산값 누락률을 모니터링 가능. 후속 사이클의 A/B 비교 baseline 으로 활용.

**기대 효과 (향후)**: paired 의 모든 % 표기에 환산 금액이 자동 병기되어 사용자가 % 의 의미를 즉시 파악.

---

## 비고

본 backlog는 사용자가 manual smoke 도중 (2026-04-28) "PDF 35페이지의 중복 수혜 제한 리스트가 highlights에 들어가야" + "출처를 PDF 안에서 찾아 표시" 두 가지 의견을 분리한 결과. (a)는 즉시 프롬프트 강화 + commit으로 반영, (b)는 본 backlog로 별도 사이클 권장.
