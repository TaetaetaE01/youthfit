import { useState } from 'react';
import { IngestionHealthTab } from './IngestionHealthTab';
import { AdminEnrichmentReviewTab } from './AdminEnrichmentReviewTab';

type TabKey = 'health' | 'enrichment';

export default function AdminIngestionPage() {
  const [tab, setTab] = useState<TabKey>('health');
  return (
    <div>
      <nav className="flex gap-2 border-b px-4">
        <TabButton active={tab === 'health'} onClick={() => setTab('health')}>
          수집 현황
        </TabButton>
        <TabButton
          active={tab === 'enrichment'}
          onClick={() => setTab('enrichment')}
        >
          Enrichment 검토
        </TabButton>
      </nav>
      {tab === 'health' && <IngestionHealthTab />}
      {tab === 'enrichment' && <AdminEnrichmentReviewTab />}
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
      {...props}
      className={`px-3 py-2 text-sm ${active ? 'border-b-2 border-blue-600 font-semibold' : ''}`}
    >
      {children}
    </button>
  );
}
