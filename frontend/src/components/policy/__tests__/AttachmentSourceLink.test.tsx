import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { AttachmentSourceLink } from '../AttachmentSourceLink';
import type { AttachmentRef, PolicyAttachment } from '@/types/policy';

describe('AttachmentSourceLink', () => {
  const attachments: PolicyAttachment[] = [
    { id: 12, name: '시행규칙.pdf', url: 'https://orig/12.pdf', mediaType: 'application/pdf' },
    { id: 13, name: '안내문.hwp', url: 'https://orig/13.hwp', mediaType: 'application/x-hwp' },
  ];

  it('renders single page label', () => {
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 35 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/시행규칙\.pdf/)).toBeInTheDocument();
    expect(screen.getByText(/35페이지/)).toBeInTheDocument();
  });

  it('renders page range label', () => {
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 37 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/35-37페이지/)).toBeInTheDocument();
  });

  it('renders without page when pageStart is null (HWP)', () => {
    const ref: AttachmentRef = { attachmentId: 13, pageStart: null, pageEnd: null };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/안내문\.hwp/)).toBeInTheDocument();
    expect(screen.queryByText(/페이지/)).not.toBeInTheDocument();
  });

  it('returns null when attachment id not found', () => {
    const ref: AttachmentRef = { attachmentId: 999, pageStart: 1, pageEnd: 1 };
    const { container } = render(
      <AttachmentSourceLink attachmentRef={ref} attachments={attachments} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('opens new tab with #page=N on click', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 37 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);

    await userEvent.click(screen.getByRole('button'));

    expect(openSpy).toHaveBeenCalledWith(
      '/api/policies/attachments/12/file#page=35',
      '_blank',
      'noopener,noreferrer',
    );
    openSpy.mockRestore();
  });

  it('opens new tab without fragment when pageStart is null', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const ref: AttachmentRef = { attachmentId: 13, pageStart: null, pageEnd: null };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);

    await userEvent.click(screen.getByRole('button'));

    expect(openSpy).toHaveBeenCalledWith(
      '/api/policies/attachments/13/file',
      '_blank',
      'noopener,noreferrer',
    );
    openSpy.mockRestore();
  });
});
