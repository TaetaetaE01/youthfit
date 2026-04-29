import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { HighlightsCard } from '../HighlightsCard';

describe('HighlightsCard', () => {
  it('비어있으면 null', () => {
    const { container } = render(
      <HighlightsCard highlights={[]} attachments={[]} policyAttachments={[]} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('헤더와 항목 렌더', () => {
    render(
      <HighlightsCard
        highlights={[
          { text: '월 20만원', sourceField: 'SUPPORT_CONTENT', attachmentRef: null },
          { text: '중복 가능', sourceField: 'SUPPORT_CONTENT', attachmentRef: null },
          { text: '2주 내 지급', sourceField: 'SUPPORT_CONTENT', attachmentRef: null },
        ]}
        attachments={[]}
        policyAttachments={[]}
      />,
    );
    expect(screen.getByText('이 정책의 특징')).toBeInTheDocument();
    expect(screen.getByText(/월 20만원/)).toBeInTheDocument();
  });
});
