import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import ActionItemRow from '../ActionItemRow';

const item = {
  code: 'INGESTION_STALE',
  severity: 'HIGH' as const,
  title: '출처 2개가 7일 이상 미갱신',
  detail: 'onlineyouthcenter.kr, gov24.go.kr',
  deeplink: '/admin/ingestion?filter=stale',
  detectedAt: '2026-05-22T05:00:00Z',
};

describe('ActionItemRow', () => {
  it('renders title, detail and link', () => {
    render(<BrowserRouter><ActionItemRow item={item} /></BrowserRouter>);
    expect(screen.getByText(item.title)).toBeInTheDocument();
    expect(screen.getByText(item.detail)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /확인/ })).toHaveAttribute('href', item.deeplink);
  });

  it('exposes data-severity="HIGH" on the dot', () => {
    const { container } = render(<BrowserRouter><ActionItemRow item={item} /></BrowserRouter>);
    expect(container.querySelector('[data-severity="HIGH"]')).not.toBeNull();
  });

  it('exposes data-severity="MEDIUM" on the dot', () => {
    const med = { ...item, severity: 'MEDIUM' as const };
    const { container } = render(<BrowserRouter><ActionItemRow item={med} /></BrowserRouter>);
    expect(container.querySelector('[data-severity="MEDIUM"]')).not.toBeNull();
  });
});
