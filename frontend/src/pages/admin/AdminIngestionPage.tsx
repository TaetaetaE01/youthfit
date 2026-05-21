import { useState } from 'react';
import { IngestionHealthTab } from './IngestionHealthTab';
import { AdminEnrichmentReviewTab } from './AdminEnrichmentReviewTab';

type TabKey = 'health' | 'enrichment';

export default function AdminIngestionPage() {
  const [tab, setTab] = useState<TabKey>('health');
  return (
    <div>
      <div role="tablist" aria-label="관리자 탭" className="flex gap-2 border-b px-4">
        <TabButton
          id="tab-health"
          active={tab === 'health'}
          aria-controls="panel-health"
          onClick={() => setTab('health')}
        >
          수집 현황
        </TabButton>
        <TabButton
          id="tab-enrichment"
          active={tab === 'enrichment'}
          aria-controls="panel-enrichment"
          onClick={() => setTab('enrichment')}
        >
          Enrichment 검토
        </TabButton>
      </div>
      <div
        role="tabpanel"
        id="panel-health"
        aria-labelledby="tab-health"
        hidden={tab !== 'health'}
      >
        <IngestionHealthTab />
      </div>
      <div
        role="tabpanel"
        id="panel-enrichment"
        aria-labelledby="tab-enrichment"
        hidden={tab !== 'enrichment'}
      >
        <AdminEnrichmentReviewTab />
      </div>
    </div>
  );
}

function TabButton({
  active,
  children,
  ...props
}: { active: boolean; children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      role="tab"
      aria-selected={active}
      {...props}
      className={`px-3 py-2 text-sm ${active ? 'border-b-2 border-blue-600 font-semibold' : ''}`}
    >
      {children}
    </button>
  );
}
