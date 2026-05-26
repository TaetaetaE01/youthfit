# Cost-Guard 활성 메모 (2026-04-29 ~ )

> **상태**: 🚨 활성 — local 환경에서 정책 ID `7, 30` 만 LLM/임베딩 호출 허용
> **해제 시점**: 미정 (테스트베드 검증 완료 후)
> **연관 PR**: #50

---

## 왜 활성화했나

dev 환경에서 새 정책 ingestion 마다 자동으로 발생하는 OpenAI 토큰 비용 (가이드 생성 gpt-4o-mini ~2K 토큰/회 + RAG 임베딩 + 첨부 추출 후 임베딩 + 신청기간 LLM 추출) 을 차단. 정책 7·30 두 개만 테스트베드로 사용.

## 현재 동작

local profile 의 application.yml default:
```yaml
youthfit:
  cost-guard:
    policy-allowlist: ${POLICY_ALLOWLIST:7,30}
```

→ 정책 7·30 외 모든 정책의 다음 호출은 진입점에서 skip + INFO 로그:
- `GuideGenerationService.generateGuide`
- `RagIndexingService.indexPolicyDocument`
- `AttachmentReindexService.reindex`
- `IngestionService.resolvePeriod` 의 신청기간 LLM 추출 (cost-guard 활성 모드면 무조건 skip)

→ Q&A (`OpenAiQnaClient`) 는 사용자 트리거이므로 가드 안 함, 정상 동작.
→ 다운로드/Tika 추출 자체는 토큰 비용 0 이라 진행됨 (다만 후속 임베딩이 차단됨).

## 해제 절차 (3가지 옵션)

### A. 환경변수 override 로 즉시 해제 (재배포 X)
```bash
# .env 또는 docker-compose 환경변수
POLICY_ALLOWLIST=

# 컨테이너 재기동
docker compose up -d backend
```

### B. application.yml local default 변경 (영구 해제)
`backend/src/main/resources/application.yml` 의 local profile section:
```yaml
youthfit:
  cost-guard:
    policy-allowlist: ${POLICY_ALLOWLIST:}   # ← 7,30 제거
```

### C. 다른 정책 추가 후 해제
```yaml
youthfit:
  cost-guard:
    policy-allowlist: ${POLICY_ALLOWLIST:7,30,42,99}
```

또는 환경변수:
```bash
POLICY_ALLOWLIST=7,30,42,99
```

## 해제 후 영향

- 새 정책 ingestion 마다 가이드 생성 + RAG 임베딩 + 첨부 추출 + 신청기간 LLM 호출 자동 발동.
- 기존 ingestion 된 정책 중 가이드 없는 것이 있으면 다음 generateGuide 트리거 시 한 번에 다 호출됨 — **점진 backfill 또는 일괄 처리 비용 사전 검토 권장**.
- prod 영향 0 (prod profile 은 default 빈 값 = 전체 허용 그대로).

## 해제 시 체크리스트

- [ ] 정책 7·30 의 가이드/임베딩이 의도대로 생성됐는지 확인 (sourceHash + content)
- [ ] OpenAI 일일/월간 사용량 한도 확인 (대량 backfill 대비)
- [ ] 본 메모 파일 archive 또는 삭제 + `docs/OPS.md` 의 cost-guard 섹션 비활성 표기 갱신

## 우회 / 디버깅

cost-guard 가 의도대로 동작하는지 확인:
```bash
docker compose logs -f backend | grep -E '(cost-guard|skipping)'
```

allowlist 정책 (7, 30) 수동 호출:
```bash
curl -X POST http://localhost:8080/api/internal/guides/generate \
  -H "X-Internal-Api-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"policyId": 7, "policyTitle": "x", "documentContent": "x"}'
```

## 관련 코드

- `backend/src/main/java/com/youthfit/common/config/CostGuard.java`
- `backend/src/main/java/com/youthfit/common/config/CostGuardProperties.java`
- 가드 호출 위치 4곳 (위 § "현재 동작" 참조)
