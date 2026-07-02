import { selectUrls, mergeFetchResults, cheerioAvailable, extractCleanedAndAttachments, prepareUrls, applySetCookies, cookieHeaderFor } from './enrich.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const CASES_DIR = new URL('./cases/', import.meta.url);
const HTML_CASES_DIR = new URL('./cases-html/', import.meta.url);

function deepEqual(a, b) {
  try { deepStrictEqual(a, b); return true; } catch { return false; }
}

const entries = await readdir(CASES_DIR);
const inputs = entries.filter(e => e.endsWith('.input.json')).sort();

if (inputs.length === 0) {
  console.error(`no fixtures found in ${CASES_DIR.pathname}`);
  process.exit(1);
}

let failed = 0;
let total = 0;

for (const inputFile of inputs) {
  total++;
  const name = inputFile.replace('.input.json', '');
  const expectedFile = `${name}.expected.json`;
  const input = JSON.parse(await readFile(new URL(inputFile, CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, CASES_DIR), 'utf8'));
  const actual = {
    selectedUrls: selectUrls(input.policy),
    merged: mergeFetchResults(input.fetchResults)
  };
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    console.log(`FAIL  ${name}`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

// HTML 케이스 (cheerio 의존). cheerio 미설치 환경에서는 skip 한다.
let htmlEntries = [];
try {
  htmlEntries = await readdir(HTML_CASES_DIR);
} catch (_) {
  // cases-html/ 없으면 skip
}
const htmlInputs = htmlEntries.filter(e => e.endsWith('.input.html')).sort();
if (htmlInputs.length > 0) {
  const hasCheerio = await cheerioAvailable();
  if (!hasCheerio) {
    console.log(`SKIP  ${htmlInputs.length} HTML case(s) — cheerio not installed in this environment`);
  } else {
    for (const inputFile of htmlInputs) {
      total++;
      const name = inputFile.replace('.input.html', '');
      const metaFile = `${name}.meta.json`;
      const expectedFile = `${name}.expected.json`;
      const html = await readFile(new URL(inputFile, HTML_CASES_DIR), 'utf8');
      const meta = JSON.parse(await readFile(new URL(metaFile, HTML_CASES_DIR), 'utf8'));
      const expected = JSON.parse(await readFile(new URL(expectedFile, HTML_CASES_DIR), 'utf8'));
      const actual = await extractCleanedAndAttachments(html, meta.pageUrl);
      if (deepEqual(actual, expected)) {
        console.log(`PASS  ${name} (html)`);
      } else {
        failed++;
        console.log(`FAIL  ${name} (html)`);
        console.log(`  expected: ${JSON.stringify(expected)}`);
        console.log(`  actual:   ${JSON.stringify(actual)}`);
      }
    }
  }
}

// prepareUrls 케이스 (Task 1)
const PREPARE_DIR = new URL('./cases-prepare-urls/', import.meta.url);
let prepEntries = [];
try { prepEntries = await readdir(PREPARE_DIR); } catch (_) {}
for (const inputFile of prepEntries.filter(e => e.endsWith('.input.json')).sort()) {
  total++;
  const name = inputFile.replace('.input.json', '');
  const input = JSON.parse(await readFile(new URL(inputFile, PREPARE_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(`${name}.expected.json`, PREPARE_DIR), 'utf8'));
  const actual = prepareUrls(input.candidates);
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name} (prepare-urls)`);
  } else {
    failed++;
    console.log(`FAIL  ${name} (prepare-urls)`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

// cookie jar 케이스 (Task 2)
const COOKIES_DIR = new URL('./cases-cookies/', import.meta.url);
let cookieEntries = [];
try { cookieEntries = await readdir(COOKIES_DIR); } catch (_) {}
for (const inputFile of cookieEntries.filter(e => e.endsWith('.input.json')).sort()) {
  total++;
  const name = inputFile.replace('.input.json', '');
  const input = JSON.parse(await readFile(new URL(inputFile, COOKIES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(`${name}.expected.json`, COOKIES_DIR), 'utf8'));
  let jar = {};
  const headers = [];
  for (const step of input.steps) {
    if (step.apply) jar = applySetCookies(jar, step.apply.host, step.apply.setCookie);
    if (step.header) headers.push(cookieHeaderFor(jar, step.header.host));
  }
  const actual = { headers };
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name} (cookies)`);
  } else {
    failed++;
    console.log(`FAIL  ${name} (cookies)`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

if (failed > 0) {
  console.error(`\n${failed} case(s) failed`);
  process.exit(1);
}
console.log(`\nAll ${total} case(s) passed`);
