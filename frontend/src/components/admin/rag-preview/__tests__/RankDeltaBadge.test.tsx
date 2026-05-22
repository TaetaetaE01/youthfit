import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RankDeltaBadge } from '../RankDeltaBadge';

describe('RankDeltaBadge', () => {
  it('NEW 표시', () => {
    render(<RankDeltaBadge delta="NEW" />);
    expect(screen.getByText('NEW')).toBeInTheDocument();
  });
  it('DROPPED 표시', () => {
    render(<RankDeltaBadge delta="DROPPED" />);
    expect(screen.getByText('DROPPED')).toBeInTheDocument();
  });
  it('+2 → ↓2 (하락)', () => {
    render(<RankDeltaBadge delta="+2" />);
    expect(screen.getByText('↓2')).toBeInTheDocument();
  });
  it('-1 → ↑1 (상승)', () => {
    render(<RankDeltaBadge delta="-1" />);
    expect(screen.getByText('↑1')).toBeInTheDocument();
  });
  it('0 → 렌더링 안 함', () => {
    const { container } = render(<RankDeltaBadge delta="0" />);
    expect(container).toBeEmptyDOMElement();
  });
});
