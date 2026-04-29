import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SourceLinkedListCard } from '../SourceLinkedListCard';

describe('SourceLinkedListCard', () => {
  it('아이템이 비어있으면 null 렌더', () => {
    const { container } = render(
      <SourceLinkedListCard
        title="테스트"
        emoji="🌟"
        tone="indigo"
        items={[]}
        attachments={[]}
        policyAttachments={[]}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('아이템과 출처 라벨 렌더', () => {
    render(
      <SourceLinkedListCard
        title="이 정책의 특징"
        emoji="🌟"
        tone="indigo"
        items={[
          { text: '월 20만원 지원', sourceField: 'SUPPORT_CONTENT', attachmentRef: null },
        ]}
        attachments={[]}
        policyAttachments={[]}
      />,
    );

    expect(screen.getByText('이 정책의 특징')).toBeInTheDocument();
    expect(screen.getByText(/월 20만원 지원/)).toBeInTheDocument();
    expect(screen.getByText(/지원내용/)).toBeInTheDocument();
  });

  it('첨부 1개일 때 새 탭 링크', () => {
    render(
      <SourceLinkedListCard
        title="이 정책의 특징"
        emoji="🌟"
        tone="indigo"
        items={[{ text: 'a', sourceField: 'BODY', attachmentRef: null }]}
        attachments={[{ id: 1, name: 'pdf', url: 'https://example.com/a.pdf' }]}
        policyAttachments={[]}
      />,
    );
    const link = screen.getByRole('link', { name: /원본 첨부/ });
    expect(link).toHaveAttribute('href', 'https://example.com/a.pdf');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('첨부 2개 이상일 때 버튼 + AttachmentSection 스크롤', () => {
    const scrollSpy = vi.fn();
    Element.prototype.scrollIntoView = scrollSpy;

    render(
      <SourceLinkedListCard
        title="t"
        emoji="🌟"
        tone="indigo"
        items={[{ text: 'a', sourceField: 'BODY', attachmentRef: null }]}
        attachments={[
          { id: 1, name: 'a', url: 'x' },
          { id: 2, name: 'b', url: 'y' },
        ]}
        policyAttachments={[]}
      />,
    );
    const button = screen.getByRole('button', { name: /원본 첨부/ });
    expect(button).toBeInTheDocument();
  });

  it('sourceField=ATTACHMENT 항목은 AttachmentSourceLink 렌더', () => {
    render(
      <SourceLinkedListCard
        title="이 정책의 특징"
        emoji="🌟"
        tone="indigo"
        items={[
          {
            text: '시행규칙 35조 참조',
            sourceField: 'ATTACHMENT',
            attachmentRef: { attachmentId: 12, pageStart: 35, pageEnd: 35 },
          },
        ]}
        attachments={[]}
        policyAttachments={[
          {
            id: 12,
            name: '시행규칙.pdf',
            url: 'https://orig/12.pdf',
            mediaType: 'application/pdf',
          },
        ]}
      />,
    );
    expect(screen.getByText(/시행규칙\.pdf/)).toBeInTheDocument();
    expect(screen.getByText(/35페이지/)).toBeInTheDocument();
  });
});
