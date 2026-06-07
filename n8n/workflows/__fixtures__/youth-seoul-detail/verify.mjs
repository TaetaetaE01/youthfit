import { parsePlcyInfoDetail, regionFromDistrictTitle } from './parse-plcyinfo.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const HTML_CASES_DIR = new URL('./cases-html/', import.meta.url);

function deepEqual(a, b) {
  try {
    deepStrictEqual(a, b);
    return true;
  } catch {
    return false;
  }
}

const entries = await readdir(HTML_CASES_DIR);
const inputs = entries.filter((e) => e.endsWith('.input.html')).sort();

if (inputs.length === 0) {
  console.error(`no fixtures found in ${HTML_CASES_DIR.pathname}`);
  process.exit(1);
}

let failed = 0;
for (const inputFile of inputs) {
  const name = inputFile.replace('.input.html', '');
  const metaFile = `${name}.meta.json`;
  const expectedFile = `${name}.expected.json`;
  const html = await readFile(new URL(inputFile, HTML_CASES_DIR), 'utf8');
  const meta = JSON.parse(await readFile(new URL(metaFile, HTML_CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, HTML_CASES_DIR), 'utf8'));
  const actual = parsePlcyInfoDetail(html, meta);
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    console.log(`FAIL  ${name}`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

// ---------------------------------------------------------------------------
// regionFromDistrictTitle 단위 검증 (자치구 region 은 제목 끝 (○○구) 에서만 잡는다)
// ---------------------------------------------------------------------------
const regionUnitCases = [
  // 제목 끝 (구명) → 해당 구
  ['2026년 미취업청년 자격증 응시료 지원사업(중랑구)', '중랑구'],
  ['청년 월세 지원(강남구)', '강남구'],
  // 제목에 구명 없음 → 본청/전체 정책 = 서울특별시
  ['2026년 서울청년정책네트워크 하반기 모집', '서울특별시'],
  // footer 주소에 "중구" 가 있어도 제목에 구 없으면 서울특별시(오매칭 방지)
  ['서울시 청년 마음건강 지원 (우) 04520 서울특별시 중구 세종대로 124', '서울특별시'],
  // 빈/누락 입력 → 서울특별시
  ['', '서울특별시'],
  [null, '서울특별시'],
];

let unitFailed = 0;
for (const [title, want] of regionUnitCases) {
  const got = regionFromDistrictTitle(title);
  if (got === want) {
    console.log(`PASS  regionFromDistrictTitle(${JSON.stringify(title)}) === ${want}`);
  } else {
    unitFailed++;
    console.log(`FAIL  regionFromDistrictTitle(${JSON.stringify(title)})`);
    console.log(`  expected: ${want}`);
    console.log(`  actual:   ${got}`);
  }
}

if (failed > 0 || unitFailed > 0) {
  console.error(`\n${failed} fixture case(s) + ${unitFailed} unit case(s) failed`);
  process.exit(1);
}
console.log(
  `\nAll ${inputs.length} fixture case(s) + ${regionUnitCases.length} regionFromDistrictTitle unit case(s) passed`
);
