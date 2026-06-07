# youth-seoul-detail fixtures

청년몽땅정보통(youth.seoul.go.kr) 상세 페이지 → `rawData` 변환 계약을 픽스처로 고정한다.

서울시·자치구·중앙(타지역) **3카테고리 상세가 동일한 `<th scope="row">라벨</th><td>값</td>`
테이블 구조**임이 실증됐다(N8N-1 spike). 따라서 파서 1개(`parse-plcyinfo.mjs`)가 셋 다 처리한다.

## 구조

- `parse-plcyinfo.mjs` — `parsePlcyInfoDetail(html, ctx)` 파서. 향후 `youth-seoul-city.json` ·
  `youth-seoul-district.json` · `youth-seoul-external.json` 워크플로우의 **"상세 데이터 파싱"
  노드 jsCode 와 동기화 미러**다. 단위 검증 전용이며 실제 데이터 흐름엔 쓰이지 않는다.
  - cheerio 미설치 환경에서도 verify 가 항상 돌도록 **정규식 기반**으로 작성했다(DOM 라이브러리 의존 없음).
- `verify.mjs` — `cases-html/*.input.html` 을 읽어 `*.meta.json`(`ctx`)으로 파서를 호출하고
  `*.expected.json` 과 `deepStrictEqual` 비교한다.
- `cases-html/{city,district,external}-2026.*` — 케이스별 입력 HTML(상세 핵심 영역 축소본) ·
  주입 컨텍스트(meta) · 기대 출력.
- `samples/` — 축소 전 원본 상세 HTML(각 ~150KB). 케이스 input 의 출처.

### ctx (meta.json)

```jsonc
{
  "plcyBizId": "V202600006",        // externalId 로 적재 (external-hash dedup 전제)
  "region":    "서울특별시",          // 호출자 주입. city→'서울특별시', district→구명, external→'타지역' 등
  "sourceUrl": "https://youth.seoul.go.kr/infoData/plcyInfo/view.do" // 첨부/상대링크 절대경로화 기준
}
```

`region` 은 파서가 하드코딩하지 않고 **호출 워크플로우가 목록에서 추출해 주입**한다.

## 실행

```bash
node n8n/workflows/__fixtures__/youth-seoul-detail/verify.mjs
```

전체 케이스 PASS 면 exit 0, 한 건이라도 FAIL 이면 exit 1.

## 추출 규칙 (요약)

- `title`: `<strong class="title">…</strong>`.
- `body`: "정책 소개" + "지원 내용" td 텍스트 결합(`<br>` 만 줄바꿈 보존).
- `additionalQualification`(support_target): 연령·참여요건·학력·전공요건·취업상태·특화분야 요건·
  추가단서 사항·참여제한 대상 th 의 td 값을 "라벨: 값" 줄로 결합(빈 값 제외).
- `applyStart`/`applyEnd`: **"사업신청기간"** td 파싱. "사업운영기간"은 신청기간이 아니므로 무시.
  포맷 `YYYY.MM.DD ~ YYYY.MM.DD` 및 `YYYYMMDD ~ YYYYMMDD` 모두 → `YYYY-MM-DD`(없으면 null).
- `applyUrl`: "신청 사이트" td 링크.
- `_refUrls`: [관련 사이트, 신청 사이트, 참고 사이트 Ⅰ, 참고 사이트 Ⅱ] td 링크(최대 3, 빈·중복 제외).
- `_selfAttachments`: "파일 명" 헤더 첨부 테이블의 정적 `<a href>`(sourceUrl 기준 절대경로). **현재 샘플은
  첨부 목록이 JS(`htmlStr += …`)로 동적 렌더돼 정적 HTML 엔 비어 있어 `[]` 다**
  (youth-seoul-attachment-limit). 정적 링크가 노출될 때만 채워진다.

> ⚠ 주석 처리(`<!-- … -->`)된 영역에도 동일 `<th scope="row">` 라벨이 존재한다. 파서는 먼저 주석을
> 제거한 뒤 추출하므로, 케이스 input 을 갱신할 때 주석 구조를 임의로 펴지 말 것.

## 동기화 책임

**⚠ `parse-plcyinfo.mjs` 의 추출 로직과 각 워크플로우 JSON 의 "상세 데이터 파싱" 노드 jsCode 는
항상 동일 로직이어야 한다.** 한쪽을 수정하면 다른쪽도 같은 변경을 반영하고 `verify.mjs` 를 다시
돌린다. 노드 jsCode 변경으로 픽스처가 깨지면, 의도된 변경이면 expected 를 갱신하고, 아니면 jsCode 를
되돌린다.

## 케이스 input 갱신 절차

1. `samples/{cat}-sample.html` 를 새 원본으로 교체.
2. 상세 핵심 영역(`<div class="policy-detail">` ~ 첨부 테이블 `</table>`)만 잘라
   `cases-html/{cat}-2026.input.html` 로 저장.
3. 파서를 실행해 산출물을 검토·교정 후 `{cat}-2026.expected.json` 으로 확정.
4. `node verify.mjs` → 전부 PASS 확인.
