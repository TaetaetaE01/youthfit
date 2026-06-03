# youth-seoul-parse-body fixture

`youth-seoul-crawl.json` 의 "상세 데이터 파싱" 노드 jsCode 와 동일 알고리즘.

## sync 규칙
- `parse-body.mjs` 의 `extractByTh / extractRefUrls / extractSelfAttachments / extractApplyUrl / extractReferenceSites / buildBody` 가 truth.
- 워크플로우 JSON 의 인라인 함수를 수정하면 여기도 같이 수정해서 verify 가 통과해야 한다.

## buildBody 동작 메모
- `BODY_SECTIONS`(th 라벨 → 본문 라벨)를 페이지 자연 순서로 순회하며 `extractByTh` 결과가 빈 칸이 아닌 것만 `"<라벨>: <값>"` 줄로 이어 붙인다.
- 청년몽땅정보통 상세는 빈 칸이 많아, 의미있는 텍스트 칸(주관기관·지원규모·심사발표·참여제한·기타사항 등)을 폭넓게 담는다.
- 정책 유형(category)·사업신청기간(applyStart/End)·각종 사이트 URL(applyUrl/referenceSites)은 별도 필드로 처리하므로 `BODY_SECTIONS` 에서 제외한다.

## extractSelfAttachments 동작 메모
- 첨부 후보는 **a[href] 의 확장자**(pdf/hwp/hwpx/doc(x)/xls(x)/zip)로만 판정한다.
- `download.do?fileId=1` 처럼 href 에 확장자가 없는 링크는 링크 텍스트가 `*.pdf` 여도 제외된다 (spec §5.1 의 의도).

## 실행
```bash
cd n8n/workflows/__fixtures__/youth-seoul-parse-body
node verify.mjs
```
