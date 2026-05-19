import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DecisionMetaGrid } from '../DecisionMetaGrid';

describe('DecisionMetaGrid', () => {
  it('4개 메타가 모두 있을 때 렌더', () => {
    render(
      <DecisionMetaGrid
        referenceYear={2026}
        supportCycle="1년 한도"
        provideType="현금"
        contact="02-1234-5678"
      />,
    );
    expect(screen.getByText('기준연도')).toBeInTheDocument();
    expect(screen.getByText('지원주기')).toBeInTheDocument();
    expect(screen.getByText('제공유형')).toBeInTheDocument();
    expect(screen.getByText('문의처')).toBeInTheDocument();
  });

  it('기준연도는 N년 으로 표시', () => {
    render(
      <DecisionMetaGrid
        referenceYear={2026}
        supportCycle={null}
        provideType={null}
        contact={null}
      />,
    );
    expect(screen.getByText('2026년')).toBeInTheDocument();
  });

  it('모두 null이면 컴포넌트는 null을 반환', () => {
    const { container } = render(
      <DecisionMetaGrid
        referenceYear={null}
        supportCycle={null}
        provideType={null}
        contact={null}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('contact 가 없을 때 contactFallback 으로 대체', () => {
    render(
      <DecisionMetaGrid
        referenceYear={null}
        supportCycle={null}
        provideType={null}
        contact={null}
        contactFallback="02-9999-0000"
        enrichmentSourceUrl="https://example.com"
      />,
    );
    expect(screen.getByText('02-9999-0000')).toBeInTheDocument();
  });
});
