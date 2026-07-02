// link-fetch-merge.js 를 4개 워크플로우의 해당 노드 jsCode 로 주입하고
// onError=continueRegularOutput(#157 3겹 방어의 최후단)를 설정한다.
import { readFile, writeFile } from 'node:fs/promises';

const SRC = new URL('./link-fetch-merge.js', import.meta.url);
const TARGETS = [
  ['../youth-seoul-city.json', '참고사이트 fetch + 머지'],
  ['../youth-seoul-district.json', '참고사이트 fetch + 머지'],
  ['../youth-seoul-external.json', '참고사이트 fetch + 머지'],
  ['../youth-center-seoul.json', '링크 fetch + 머지'],
];

const code = await readFile(SRC, 'utf8');

// 드리프트 가드 (#160): 노드에 인라인된 EXTRA_CA_PEM 이 certs/extra-ca.pem 과 동일한지 확인한다.
// 두 사본이 갈라지면 task runner(인라인)와 main 프로세스(번들)가 서로 다른 CA 를 신뢰하게 된다.
{
  const normalize = (pem) => (pem.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g) || [])
    .map(b => b.replace(/\s+/g, ''));
  const bundle = normalize(await readFile(new URL('../../certs/extra-ca.pem', import.meta.url), 'utf8'));
  const inlineMatch = code.match(/EXTRA_CA_PEM\s*=\s*`([\s\S]*?)`/);
  const inline = inlineMatch ? normalize(inlineMatch[1]) : [];
  const same = bundle.length === inline.length && bundle.every(c => inline.includes(c));
  if (!same) {
    console.error('CERT DRIFT: link-fetch-merge.js 의 EXTRA_CA_PEM 이 n8n/certs/extra-ca.pem 과 다릅니다. 두 곳을 동일하게 맞추세요 (#160).');
    process.exit(1);
  }
}

for (const [rel, nodeName] of TARGETS) {
  const path = new URL(rel, import.meta.url);
  const wf = JSON.parse(await readFile(path, 'utf8'));
  const node = wf.nodes.find(n => n.name === nodeName);
  if (!node) {
    console.error(`MISSING node "${nodeName}" in ${rel}`);
    process.exit(1);
  }
  node.parameters.jsCode = code;
  node.onError = 'continueRegularOutput';
  await writeFile(path, JSON.stringify(wf, null, 2) + '\n');
  console.log(`synced ${rel} :: ${nodeName}`);
}
console.log('done');
