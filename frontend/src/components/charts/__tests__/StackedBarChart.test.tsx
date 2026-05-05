import { render } from '@testing-library/react';
import { describe, it } from 'vitest';
import { StackedBarChart } from '../StackedBarChart';

describe('StackedBarChart', () => {
  it('render without crash', () => {
    render(
      <StackedBarChart
        data={[{ date: '2026-05-01', sent: 10, failed: 1 }]}
        xKey="date"
        series={[
          { key: 'sent', name: 'Sent', color: '#6366f1' },
          { key: 'failed', name: 'Failed', color: '#ef4444' },
        ]}
      />
    );
  });
});
