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
