import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyEnrichmentSection } from '../PolicyEnrichmentSection';
import type { PolicyEnrichment } from '@/types/policy';

const BASE_ENRICHMENT: PolicyEnrichment = {
  sourceUrl: 'https://example.com/policy/1',
  fetchedAt: '2025-05-01T12:00:00Z',
  sections: {
    supportTarget: '만 19~34세 청년',
    supportContent: '월 20만원 지원',
    applyMethod: '온라인 신청',
    requiredDocuments: '주민등록등본 1부',
    deadlineNote: '2025년 6월 30일까지',
  },
  extraAttachments: [],
};

describe('PolicyEnrichmentSection', () => {
  it('enrichment 가 null 이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<PolicyEnrichmentSection enrichment={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('sections 와 attachments 모두 비어 있으면 렌더하지 않는다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: {
        supportTarget: null,
        supportContent: null,
        applyMethod: null,
        requiredDocuments: null,
        deadlineNote: null,
      },
      extraAttachments: [],
    };
    const { container } = render(<PolicyEnrichmentSection enrichment={enrichment} />);
    expect(container.firstChild).toBeNull();
  });

  it('null 인 섹션 항목은 렌더하지 않는다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: {
        supportTarget: '만 19~34세 청년',
        supportContent: null,
        applyMethod: null,
        requiredDocuments: null,
        deadlineNote: null,
      },
      extraAttachments: [],
    };
    render(<PolicyEnrichmentSection enrichment={enrichment} />);

    expect(screen.getByText('지원대상')).toBeInTheDocument();
    expect(screen.getByText('만 19~34세 청년')).toBeInTheDocument();

    expect(screen.queryByText('지원내용')).toBeNull();
    expect(screen.queryByText('신청방법')).toBeNull();
    expect(screen.queryByText('제출서류')).toBeNull();
    expect(screen.queryByText('마감안내')).toBeNull();
  });

  it('AI 자동 수집 배지와 원문 보기 링크를 렌더한다', () => {
    render(<PolicyEnrichmentSection enrichment={BASE_ENRICHMENT} />);

    expect(screen.getByText('AI 자동 수집')).toBeInTheDocument();

    const link = screen.getByRole('link', { name: /원문 보기/ });
    expect(link).toHaveAttribute('href', 'https://example.com/policy/1');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
  });

  it('첨부 파일이 새 탭으로 열리는 링크로 렌더된다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: null,
      extraAttachments: [
        { name: '공고문.pdf', url: 'https://example.com/files/공고문.pdf' },
        { name: '신청서.hwp', url: 'https://example.com/files/신청서.hwp' },
      ],
    };
    render(<PolicyEnrichmentSection enrichment={enrichment} />);

    const pdfLink = screen.getByRole('link', { name: /공고문\.pdf/ });
    expect(pdfLink).toHaveAttribute('href', 'https://example.com/files/공고문.pdf');
    expect(pdfLink).toHaveAttribute('target', '_blank');
    expect(pdfLink).toHaveAttribute('rel', expect.stringContaining('noopener'));

    const hwpLink = screen.getByRole('link', { name: /신청서\.hwp/ });
    expect(hwpLink).toHaveAttribute('href', 'https://example.com/files/신청서.hwp');
    expect(hwpLink).toHaveAttribute('target', '_blank');
  });

  it('수집 일시가 time 요소로 렌더된다', () => {
    render(<PolicyEnrichmentSection enrichment={BASE_ENRICHMENT} />);
    const timeEl = document.querySelector('time');
    expect(timeEl).not.toBeNull();
    expect(timeEl?.getAttribute('dateTime')).toBe('2025-05-01T12:00:00Z');
  });
});
