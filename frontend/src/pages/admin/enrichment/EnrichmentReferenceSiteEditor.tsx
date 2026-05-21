import { useState } from 'react';
import type { ReferenceSite } from '@/types/adminEnrichment';

interface Props {
  initialSites: ReferenceSite[];
  onSave: (sites: ReferenceSite[]) => void;
}

const URL_REGEX = /^https?:\/\/.+/i;

export function EnrichmentReferenceSiteEditor({ initialSites, onSave }: Props) {
  const [sites, setSites] = useState<ReferenceSite[]>(initialSites);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);

  function add() {
    setError(null);
    if (!URL_REGEX.test(url)) {
      setError('올바른 URL 형식이 아닙니다 (https://)');
      return;
    }
    if (sites.some((s) => s.url === url)) {
      setError('이미 등록된 URL 입니다');
      return;
    }
    setSites([...sites, { name: name || url, url, source: 'ADMIN' }]);
    setName('');
    setUrl('');
  }

  function remove(target: string) {
    setSites(sites.filter((s) => s.url !== target));
  }

  return (
    <div className="space-y-2">
      <ul className="space-y-1">
        {sites.map((s) => (
          <li key={s.url} className="flex items-center gap-2 text-sm">
            <span
              className={`px-1.5 text-xs rounded ${
                s.source === 'ADMIN' ? 'bg-amber-200 text-amber-800' : 'bg-gray-200 text-gray-700'
              }`}
            >
              {s.source}
            </span>
            <a
              href={s.url}
              target="_blank"
              rel="noreferrer"
              className="underline text-blue-600 break-all"
            >
              {s.url}
            </a>
            <button
              type="button"
              onClick={() => remove(s.url)}
              className="ml-auto text-xs text-red-600 hover:text-red-700"
            >
              제거
            </button>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="이름 (선택)"
          className="border rounded px-2 py-1 text-sm"
        />
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="URL (https://...)"
          className="border rounded px-2 py-1 text-sm flex-1"
        />
        <button
          type="button"
          onClick={add}
          className="px-3 py-1 text-sm bg-gray-100 hover:bg-gray-200 rounded border"
        >
          추가
        </button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
      <button
        type="button"
        onClick={() => onSave(sites)}
        className="px-3 py-1 text-sm bg-blue-600 hover:bg-blue-700 text-white rounded"
      >
        저장
      </button>
    </div>
  );
}
