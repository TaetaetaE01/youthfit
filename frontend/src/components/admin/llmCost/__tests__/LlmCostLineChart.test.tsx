import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { LlmCostLineChart } from '../LlmCostLineChart';

describe('LlmCostLineChart', () => {
  it('빈 시리즈는 placeholder 메시지를 표시한다', () => {
    render(<LlmCostLineChart points={[]} />);
    expect(screen.getByText(/데이터 없음/)).toBeInTheDocument();
  });

  it('points 가 있으면 차트가 렌더된다', () => {
    render(
      <LlmCostLineChart
        points={[
          { at: '2026-05-06T10:00:00Z', costByModule: { QNA: 0.12, GUIDE: 0.05 } },
          { at: '2026-05-06T11:00:00Z', costByModule: { QNA: 0.10, EMBEDDING: 0.01 } },
        ]}
      />,
    );
    expect(screen.queryByText(/데이터 없음/)).not.toBeInTheDocument();
  });
});
