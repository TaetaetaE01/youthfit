import { useState } from 'react';
import { EnrichmentSummaryCards } from './enrichment/EnrichmentSummaryCards';
import { EnrichmentCandidateTable } from './enrichment/EnrichmentCandidateTable';
import { EnrichmentReviewPanel } from './enrichment/EnrichmentReviewPanel';

export function AdminEnrichmentReviewTab() {
  const [selected, setSelected] = useState<number | null>(null);
  return (
    <div className="p-4">
      <EnrichmentSummaryCards />
      <div className="flex gap-4">
        <div className="flex-1">
          <EnrichmentCandidateTable onSelect={setSelected} />
        </div>
        {selected !== null && <EnrichmentReviewPanel policyId={selected} />}
      </div>
    </div>
  );
}
