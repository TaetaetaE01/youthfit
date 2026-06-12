# 몽땅청년 자치구 region 오분류 + n8n 2.16 published 버전이 hotfix 를 안 먹던 문제

- 작성일: 2026-06-13
- 작성자: TaetaetaE01
- 관련 커밋: `7b80576` (`fix(n8n): 몽땅청년 자치구 region 을 제목 토큰 매칭으로 확장`)
- 관련 PR: #146
- 관련 모듈: n8n (youth-seoul 크롤 워크플로우), ingestion 시드

## 한 줄 요약

> prod 배포용 로컬 시드 재구축(E2E) 중, 자치구 정책의 region 이 서울특별시로 오분류되는 버그를 발견해 고쳤다. 그런데 워크플로우 파일을 고쳐 import 해도 실제 크롤 결과가 안 바뀌는 2차 문제에 부딪혔고, 원인은 **n8n 2.16 의 draft/publish 버전 분리**(실행 정의는 `workflow_history` 의 published 버전)였다. published 버전을 직접 갱신해 해결했다.

## 1. 상황 (Context)

- "배포 전에 로컬에서 데이터 수집 후 prod 로 올린다"는 목표로, 로컬 DB 를 비우고 5개 워크플로우(youth-seoul city/district/external + 온통청년 + 복지로)를 재크롤해 깨끗한 2026 청년 시드를 만드는 중이었다.
- 재크롤 후 `YOUTH_SEOUL_CRAWL` region 분포를 보니, 제목 끝 괄호가 있는 자치구 정책("…(중랑구)")은 `중랑구`로 잘 들어갔지만, 구명이 제목 **중간**에 오는 정책이 `서울특별시`로 분류됐다.
  - ❌ "2026년 **중랑구** 청년 재테크 교육" → 서울특별시
  - ❌ "2026년 **중랑구** 청년 창업 아카데미" → 서울특별시
  - ✅ "…자격증 응시료 지원사업**(중랑구)**" → 중랑구
- 영향: 자치구 거주자 한정 정책이 서울 전체로 노출돼 적합도 판정·지역 필터가 부정확해진다. prod 로 그대로 넘어갈 시드라 품질 직결.

## 2. 원인 (Root Cause)

### 2-1. region 오분류 (1차)

`n8n/workflows/__fixtures__/youth-seoul-detail/parse-plcyinfo.mjs` 의 `regionFromDistrictTitle` 가 **제목 끝 괄호 `(○○구)` 패턴만** 추출하고 있었다.

```js
const m = String(title || '').match(/\(([가-힣]+구)\)/);
return (m && SEOUL_GU.includes(m[1])) ? m[1] : '서울특별시';
```

→ 괄호가 없으면 무조건 `서울특별시` 폴백. 이전 커밋 `fad81d5`(PR #145)에서 "footer 주소(`중구 세종대로`)의 구명 오매칭 방지"를 위해 일부러 괄호로만 좁혔던 것이, 정작 구명이 제목 본문에 있는 케이스를 놓치는 부작용을 낳았다.

### 2-2. 수정해도 반영이 안 됨 (2차 — 진짜 함정)

district.json 의 인라인 파서와 fixture 미러를 토큰 매칭으로 고치고 `import:workflow` 했는데(“Successfully imported” 출력), 재크롤 결과가 **여전히 서울특별시**였다. 실제 상세 HTML 을 직접 fetch 해 파서를 단독 실행하면 `중랑구`가 나오는데(로직은 맞음), DB 적재만 구로직 결과였다.

원인은 **n8n 2.16.0 의 draft/publish 워크플로우 버전 분리**:
- `workflow_entity.nodes` = draft(편집본)
- `workflow_entity.activeVersionId` → `workflow_history` 의 한 행 = **published(실행에 실제로 쓰이는 정의)**
- 시작 로그의 `Processed N draft workflows, M published workflows` 가 이 구조의 신호였다.

즉 `import:workflow` 도, `workflow_entity.nodes` 직접 UPDATE 도 draft 만 건드렸고, active 실행은 `workflow_history[activeVersionId].nodes`(구로직)를 계속 사용했다.

### 2-3. (부수) sqlite 직접 수정 후 크래시

draft 를 고치려 호스트에서 `database.sqlite` 를 수정·`docker cp` 로 복원하니, 파일 소유자가 호스트 uid(`501:0`)로 바뀌어 n8n(uid 1000)이 `SQLITE_READONLY: attempt to write a readonly database` 로 **부팅 직후 크래시**했다(healthz 가 잠깐 떴다 죽음).

## 3. 고려한 대안 (Alternatives)

### region 추출 로직

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| A. 괄호 + 제목 토큰 정확매칭 (+ footer 가드) | 본문 구명 케이스 해결, footer 오매칭 방지 | 비공백 인접 구명("강남구청년센터") 미처리 |
| B. 제목 전체 부분문자열 검색 | 모든 구명 포착 | "진로"→종로구, "집중구역"→중구 등 오인 폭발 |
| C. 제목 앞 N글자만 검색 | footer(뒤쪽) 회피 | 임계값 취약, 근거 약함 |

### 자치구 정책을 어느 워크플로우가 책임지나 (city 변경 여부)

| 대안 | 장점 | 단점 / 채택 안 한 이유 |
|---|---|---|
| city·district 둘 다 토큰 매칭 | 어느 쪽이 긁어도 일관 | city 본청 정책("노원구 일자리 거점…")이 자치구로 오분류 위험 |
| district 만 토큰 매칭, city 는 고정 | 본청 오분류 차단 | (데이터로 검증 필요) |

## 4. 선택과 이유 (Decision)

### region 로직 → 대안 A (3단계)

```js
// 1) 제목 끝 괄호 (○○구) 최우선
// 2) footer 주소 섞임(우편번호 \d{5} · "(우)") 감지 시 토큰 탐색 생략
// 3) split + 정확매칭으로 자치구명 토큰 (부분문자열 오인 없음)
```

- **핵심 근거**: footer 오매칭은 우편번호/`(우)` 라는 **명확한 시그널**로 배제 가능하고(B의 폭발 회피), 토큰 정확매칭은 부분문자열 오인이 구조적으로 없다.
- **트레이드오프**: 공백 없이 붙은 구명("강남구청년센터")은 시 단위로 폴백 — **오분류보다 안전한 방향**으로 의도적 선택(함수 주석·테스트에 명시).

### city 는 변경하지 않음 (셀프 리뷰 `/cr` M1 반영)

처음엔 city·district 둘 다 토큰 매칭으로 바꿨으나, `/cr` 셀프 리뷰가 "city 동작 변경이 의도된 것인지, 본청 오분류 위험은 없는지"를 지적(Major). **데이터로 검증**한 결과:

```
V2026(서울시 ctList tabKind=002): 8건 전부 서울특별시
20자리(자치구/중앙): 전부 district/external 이 적재
```

→ city 탭(tabKind=002)은 서울시 본청 정책만 적재하고 **자치구를 긁지 않음**이 확인됐다. 따라서 city 의 토큰 매칭은 불필요했고 본청 오분류 위험만 추가하므로 `init.region='서울특별시'` 고정으로 **원복**했다.

- **가역성**: region 로직은 fixture 미러 + 워크플로우 인라인 1:1 동기화라 추후 변경 쉬움. city 탭이 향후 자치구 정책을 노출하기 시작하면(엔드포인트 정책 변경) city 도 토큰 매칭 재검토.

## 5. 해결 (Solution)

### 코드 (PR #146)

- `n8n/workflows/youth-seoul-district.json` "상세 데이터 파싱": `regionFromDistrictTitle` 3단계 로직
- `n8n/workflows/__fixtures__/youth-seoul-detail/parse-plcyinfo.mjs`: 동기화 미러 + 비공백 한계 주석
- `n8n/workflows/__fixtures__/youth-seoul-detail/verify.mjs`: 단위 케이스 12건(토큰·부분문자열·footer·비공백 negative)
- `.gitignore`: `.local-backups/` (시드 작업용 임시 DB 백업)
- city.json 은 **무변경**(원복)

### n8n published 버전 반영 (로컬 — 반복 불가능한 부수 효과)

PR 파일 수정만으로는 **로컬 실행 인스턴스에 반영되지 않는다.** 다음 절차로 published 버전을 직접 갱신했다.

```bash
docker stop youthfit-n8n
docker cp youthfit-n8n:/home/node/.n8n/database.sqlite /tmp/edit.sqlite
# python: workflow_entity.nodes + workflow_history[activeVersionId].nodes 둘 다 신로직으로 UPDATE
docker cp /tmp/edit.sqlite youthfit-n8n:/home/node/.n8n/database.sqlite
# 소유권 복구 (안 하면 SQLITE_READONLY 크래시)
docker run --rm -u root --volumes-from youthfit-n8n alpine sh -c \
  "cd /home/node/.n8n; chown 1000:1000 database.sqlite; chmod 644 database.sqlite; \
   rm -f database.sqlite-wal database.sqlite-shm crash.journal"
docker start youthfit-n8n
```

### YOUTH_SEOUL 데이터 삭제 (FK 주의)

`policy_source` 등이 `policy` 를 참조하는 FK 가 **CASCADE 가 아니라** 재크롤 전 삭제 시 `violates foreign key constraint` 가 났다. (전체 `TRUNCATE … CASCADE` 는 동작하지만, 특정 source 만 `DELETE` 할 땐 CASCADE 안 됨.) 자식 테이블(guide·eligibility_rule·policy_document·policy_processing_step·policy_attachment·*_tag·policy_source)을 먼저 DELETE 한 뒤 `policy` 를 삭제했다.

## 6. 검증 (Result)

- **fixture 회귀**: `node n8n/workflows/__fixtures__/youth-seoul-detail/verify.mjs` → HTML 4 + 단위 12 전부 PASS (footer 우편번호 케이스, 비공백 negative 포함).
- **로컬 재크롤 실증**: published 갱신 후 재크롤 → 자치구 region 정확 매핑. **중랑구 2 → 5건** ("중랑구 청년 …" 3건이 서울특별시→중랑구로 교정), 2026컷 `others=0`, guide 27/27.
- **dedup 재실행**: 27건 있는 상태에서 재트리거 → 상세 fetch 0·적재 0, 27건 유지(신규 plcyBizId 필터 + source_hash 정상).

## 7. 후속 / 미결 (Follow-ups)

- **소스 간 중복 11건 정리 완료(별건)**: 같은 plcyBizId 가 `YOUTH_CENTER`·`YOUTH_SEOUL_CRAWL` 양쪽에 **별도 policy 2개**로 적재(소스 간 dedup 미적용). 전 지표(룰 8 vs 0~3, 본문 길이, region 정확도, RAG)에서 YOUTH_CENTER 우위 → YOUTH_SEOUL 중복 11건 삭제. **최종 시드 69건**(YOUTH_CENTER 49 + YOUTH_SEOUL 16 + BOKJIRO 4).
- **근본 소스 간 dedup 은 미구현**: 향후 두 소스를 계속 크롤하면 매번 중복 발생. plcyBizId 소스 간 비교 로직은 별도 작업으로 유예.
- **prod 이관 미완**: 로컬 69건 시드 → prod RDS `pg_dump`/restore 가 다음 단계. AWS 는 Plan E(backend 빌드·배포) 선행 필요.
- **재발 방지 가드레일**: n8n 2.16 draft/publish 함정·sqlite 소유권 복구 절차를 메모리(`n8n_local_reimport_ops`)에 기록함.

## 8. 참고 (References)

- PR #146, 커밋 `7b80576`
- 선행: PR #145(몽땅청년 3카테고리 재설계), `fad81d5`(자치구 region 괄호 추출 — 본 버그의 직접 원인)
- 런북: `docs/runbooks/youth-seoul-pipeline-verification.md`
- 결정적 검증 쿼리: city 적재가 V2026(서울시 탭)만임을 확인 → city 무변경 근거
