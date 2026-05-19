import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DecisionMetaGrid } from '../DecisionMetaGrid';

describe('DecisionMetaGrid', () => {
  it('4개 메타가 모두 있을 때 렌더', () => {
    render(
      <DecisionMetaGrid
        applyEnd="2026-05-29"
        supportScale={60000}
        referenceYear={2026}
        supportCycle="1년 한도"
      />,
    );
    expect(screen.getByText('마감일')).toBeInTheDocument();
    expect(screen.getByText('지원규모')).toBeInTheDocument();
    expect(screen.getByText('기준연도')).toBeInTheDocument();
    expect(screen.getByText('지원주기')).toBeInTheDocument();
  });

  it('지원규모는 toLocaleString으로 표시', () => {
    render(
      <DecisionMetaGrid
        applyEnd={null}
        supportScale={60000}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(screen.getByText('60,000명')).toBeInTheDocument();
  });

  it('모두 null이면 컴포넌트는 null을 반환', () => {
    const { container } = render(
      <DecisionMetaGrid
        applyEnd={null}
        supportScale={null}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('마감일에는 D-day가 함께 표시된다', () => {
    const future = new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)
      .toISOString().slice(0, 10);
    render(
      <DecisionMetaGrid
        applyEnd={future}
        supportScale={null}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(screen.getByText(/D-10|D-9|D-11/)).toBeInTheDocument();
  });
});
