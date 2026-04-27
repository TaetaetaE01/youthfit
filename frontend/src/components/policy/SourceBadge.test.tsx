import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SourceBadge from './SourceBadge';

describe('SourceBadge', () => {
  it('renders nothing when sourceType is null', () => {
    const { container } = render(
      <SourceBadge sourceType={null} sourceLabel={null} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when sourceLabel is null', () => {
    const { container } = render(
      <SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel={null} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders an img with alt and title for BOKJIRO_CENTRAL', () => {
    render(<SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel="복지로" />);
    const img = screen.getByRole('img', { name: '복지로' });
    expect(img).toBeInTheDocument();
    expect(img.parentElement).toHaveAttribute('title', '출처: 복지로');
  });

  it('uses different image source for each sourceType', () => {
    const { rerender } = render(
      <SourceBadge sourceType="BOKJIRO_CENTRAL" sourceLabel="복지로" />,
    );
    const bokjiroSrc = screen.getByRole('img').getAttribute('src');

    rerender(<SourceBadge sourceType="YOUTH_CENTER" sourceLabel="온통청년" />);
    const youthCenterSrc = screen.getByRole('img').getAttribute('src');

    rerender(<SourceBadge sourceType="YOUTH_SEOUL_CRAWL" sourceLabel="청년 서울" />);
    const youthSeoulSrc = screen.getByRole('img').getAttribute('src');

    expect(bokjiroSrc).not.toBe(youthCenterSrc);
    expect(bokjiroSrc).not.toBe(youthSeoulSrc);
    expect(youthCenterSrc).not.toBe(youthSeoulSrc);
  });
});
