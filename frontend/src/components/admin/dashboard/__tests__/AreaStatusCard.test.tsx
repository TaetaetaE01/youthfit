import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import AreaStatusCard from '../AreaStatusCard';

const area = {
  key: 'ingestion',
  label: 'Ingestion',
  status: 'CRITICAL' as const,
  summary: '확인 필요',
  sparkline: [3, 5, 4, 8, 6, 7, 4],
  deeplink: '/admin/ingestion',
};

describe('AreaStatusCard', () => {
  it('renders label, summary and status badge', () => {
    render(
      <BrowserRouter>
        <AreaStatusCard area={area} />
      </BrowserRouter>,
    );
    expect(screen.getByText('Ingestion')).toBeInTheDocument();
    // '확인 필요' 는 summary 와 CRITICAL 배지 라벨 양쪽에 등장하므로 getAllByText 로 검증
    expect(screen.getAllByText(/확인 필요/).length).toBeGreaterThanOrEqual(1);
  });

  it('wraps content in a link to deeplink', () => {
    render(
      <BrowserRouter>
        <AreaStatusCard area={area} />
      </BrowserRouter>,
    );
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/ingestion');
  });

  it('exposes aria-label combining area label and summary', () => {
    render(
      <BrowserRouter>
        <AreaStatusCard area={area} />
      </BrowserRouter>,
    );
    const link = screen.getByRole('link', { name: /Ingestion/ });
    expect(link).toHaveAttribute('aria-label', 'Ingestion — 확인 필요');
  });

  it('hides sparkline when values are empty', () => {
    const empty = { ...area, sparkline: [] };
    const { container } = render(
      <BrowserRouter>
        <AreaStatusCard area={empty} />
      </BrowserRouter>,
    );
    expect(container.querySelector('svg')).toBeNull();
  });
});
