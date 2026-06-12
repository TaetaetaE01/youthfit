# 몽땅청년 크롤 실증(Spike) 결과 — N8N-1

- 날짜: 2026-06-07
- 대상: youth.seoul.go.kr (청년몽땅정보통)
- 관련 plan: `docs/superpowers/plans/2026-06-06-mongttang-youth-crawl-redesign.md`
- 샘플(축소본): `n8n/workflows/__fixtures__/youth-seoul-detail/cases-html/{city,district,external}-2026.input.html` (spike 원본 150KB HTML 은 검증 후 repo 에서 제거)

## 0. 도메인 정정 (중요)

- 정확한 호스트는 **`youth.seoul.go.kr`** (www 없음). `www.youth.seoul.go.kr` 는 **NXDOMAIN**(존재하지 않음).
- 기존 plan/조사의 일부 명령이 `www.` 를 붙여 실패했음. 모든 URL 에서 `www.` 제거.

## 1. 세션쿠키 — **불필요** (plan 가정 뒤집음)

실측:
- `pageIndex=1` 첫 ID = `V202600006`
- `pageIndex=2` (쿠키 유지) 첫 ID = `V202600001`
- `pageIndex=2` (쿠키 없음) 첫 ID = `V202600001`

→ 쿠키 유무와 무관하게 `pageIndex` GET 만으로 페이지가 바뀐다. 응답에 `YOUTHID`/`WMONID` 쿠키가 내려오지만 **페이지네이션에 필요 없다**.

**plan 변경**: Task 3/4 의 "세션 확립 / JSESSIONID 추출 / Cookie 헤더 주입" 노드 전부 **제거**. 단순 GET 루프.

## 2. 상세 템플릿 — **3카테고리 전부 동일** (파서 1개로 통합)

서울시(`plcyInfo/view.do` tabKind=002), 자치구(tabKind=003), 중앙/타지역(`youthPlcyInfo/view.do`) 세 상세의 `<th scope="row">` 라벨이 **완전 동일**:

```
정책 유형 · 주관 기관 · 정책 소개 · 지원 내용 · 사업운영기간 · 사업신청기간 · 지원규모 · 관련 사이트
연령 · 참여요건 · 학력 · 전공요건 · 취업상태 · 특화분야 요건 · 추가단서 사항 · 참여제한 대상
신청절차 · 심사 및 발표 · 제출서류 · 신청 사이트 · 기타사항 · 운영기관 · 참고 사이트 Ⅰ · 참고 사이트 Ⅱ
```

구조: `<table>` 안 `<th scope="row">라벨</th><td>값</td>`.

**plan 변경**: Task 5 의 "중앙/타지역 별도 파서(`parse-youthplcyinfo.mjs`)" 불필요. **공유 파서 1개**(`parse-plcyinfo.mjs`)가 셋 다 처리. 워크플로우는 여전히 3개(목록 endpoint·region·ID 형식만 다름)지만 상세 파싱 로직은 공유.

### 셀렉터 규칙 (파서 구현 기준)
- 범용: `th[scope=row]` 텍스트 == 라벨 → 형제 `td` 값.
- `title`: 상세 상단 제목 영역 (구현 시 정확 셀렉터 확정 — 샘플에서 `strong.tit` 외 페이지 헤더 확인).
- `body`: "정책 소개" + "지원 내용" td 결합.
- `support_target`(자격요건) = 연령·참여요건·학력·전공요건·취업상태·특화분야 요건·추가단서 사항·참여제한 대상 td 들을 라벨:값 형태로 결합 → `additionalQualification`.
- `applyStart/End`: "사업신청기간" td 파싱 (`YYYY.MM.DD ~ YYYY.MM.DD`). "사업운영기간"은 신청기간 아님 → 무시.
- `applyUrl`: "신청 사이트" td 의 링크.
- `_refUrls`: 관련 사이트 · 신청 사이트 · 참고 사이트 Ⅰ · 참고 사이트 Ⅱ td 의 링크 (최대 3).
- 첨부: "파일 명" 헤더의 첨부 테이블 링크.

## 3. 2026 컷오프 — ID prefix

- 서울시 ID: `V202600006` 형식 (`V` + `YYYY` + 일련번호).
- 자치구 ID: `20260520005400213208` (20자리, 앞 4자리 = 연도).
- 중앙/타지역 ID: `20260605005400113228` (20자리, 앞 4자리 = 연도).
- 판정: 정규식 **`/^V?2026/`** 로 세 형식 모두 2026 판별. 목록이 등록일 desc 정렬이므로 2025↓ 출현 시 중단.

## 4. region

- 서울시: `서울특별시` 고정.
- 자치구: **구명** — 목록(`guList`)에 구명이 항목별로 노출됨(예: 강남구·강동구 각 2회). 상세에도 노출(샘플은 '중구'). 목록에서 추출해 상세까지 전달 권장. 폴백 = 상세 텍스트에서 구명 정규식.
- 중앙/타지역: 상세에 `타지역` 10회 노출. `전국`/시·도명 판별. 기본 `타지역`.

## 5. 페이지 규모 / endpoint

| 카테고리 | 목록 endpoint | tabKind | lastPage(실측) | ID 형식 |
|---------|--------------|---------|---------------|---------|
| 서울시 | `plcyInfo/ctList.do?key=2309150002` | 002 | 67 | `V2026…` |
| 자치구 | `plcyInfo/guList.do?key=2309150002` | 003 | (확인필요, 상당수) | `2026…`(20자리) |
| 중앙/타지역 | `youthPlcyInfo/list.do?key=2309160001` | — | 1 | `2026…`(20자리) |

- lastPage = 목록의 `fn_egov_link_page(N)` 최댓값.
- 상세 URL:
  - 서울시: `plcyInfo/view.do?plcyBizId={id}&tab=001&key=2309150002&tabKind=002`
  - 자치구: `plcyInfo/view.do?plcyBizId={id}&tab=001&key=2309150002&tabKind=003`
  - 중앙/타지역: `youthPlcyInfo/view.do?plcyBizId={id}&key=2309160001`

## 6. plan 대비 변경 요약

1. **세션쿠키 노드 전면 제거** (불필요 — §1).
2. **상세 파서 1개로 통합** — city/district/external 공유 (§2). Task 5 의 별도 파서 삭제.
3. 호스트 `www.` 제거 (§0).
4. 나머지(externalId=plcyBizId 적재, source_type=YOUTH_SEOUL_CRAWL, region 주입, support_target, external-hash dedup, 2026 컷)는 plan 유지.

## 7. 남은 미확정 (구현 중 확정)

- 상세 제목(`title`) 정확 셀렉터 (페이지 헤더 vs `strong.tit`).
- 자치구 lastPage 실측, 목록의 구명 DOM 위치(항목별 매핑).
- 첨부 테이블의 정확한 링크 구조(WebGate/JS 동적 항목은 보류 — youth-seoul-attachment-limit).
- `_refUrls` fetch+머지는 기존 워크플로우 로직 인계.
