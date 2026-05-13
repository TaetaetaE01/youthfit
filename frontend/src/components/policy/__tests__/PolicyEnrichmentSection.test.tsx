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
    policyOverview: null,
    eligibilityCriteria: null,
    operatingOrganization: null,
    contactPhone: null,
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
        policyOverview: null,
        eligibilityCriteria: null,
        operatingOrganization: null,
        contactPhone: null,
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
        policyOverview: null,
        eligibilityCriteria: null,
        operatingOrganization: null,
        contactPhone: null,
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
    // 4 new fields also absent
    expect(screen.queryByText('정책 개요')).toBeNull();
    expect(screen.queryByText('지원자격')).toBeNull();
    expect(screen.queryByText('운영기관')).toBeNull();
    expect(screen.queryByText('문의처')).toBeNull();
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

  it('policyOverview 가 있으면 정책 개요 라벨을 렌더한다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: {
        ...BASE_ENRICHMENT.sections!,
        policyOverview: '청년 주거 안정을 위한 지원 사업입니다.',
      },
    };
    render(<PolicyEnrichmentSection enrichment={enrichment} />);

    expect(screen.getByText('정책 개요')).toBeInTheDocument();
    expect(screen.getByText('청년 주거 안정을 위한 지원 사업입니다.')).toBeInTheDocument();
  });

  it('eligibilityCriteria 가 있으면 지원자격 라벨을 렌더한다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: {
        ...BASE_ENRICHMENT.sections!,
        eligibilityCriteria: '만 19세 이상 34세 이하, 무주택 세대원',
      },
    };
    render(<PolicyEnrichmentSection enrichment={enrichment} />);

    expect(screen.getByText('지원자격')).toBeInTheDocument();
    expect(screen.getByText('만 19세 이상 34세 이하, 무주택 세대원')).toBeInTheDocument();
  });

  it('operatingOrganization 과 contactPhone 이 있으면 운영기관·문의처 라벨을 렌더한다', () => {
    const enrichment: PolicyEnrichment = {
      ...BASE_ENRICHMENT,
      sections: {
        ...BASE_ENRICHMENT.sections!,
        operatingOrganization: '인천광역시 계양구 일자리정책과',
        contactPhone: '032-450-8354',
      },
    };
    render(<PolicyEnrichmentSection enrichment={enrichment} />);

    expect(screen.getByText('운영기관')).toBeInTheDocument();
    expect(screen.getByText('인천광역시 계양구 일자리정책과')).toBeInTheDocument();
    expect(screen.getByText('문의처')).toBeInTheDocument();
    expect(screen.getByText('032-450-8354')).toBeInTheDocument();
  });

  it('4개 신규 필드가 모두 null 이면 해당 라벨을 렌더하지 않는다', () => {
    // BASE_ENRICHMENT already has all 4 new fields as null
    render(<PolicyEnrichmentSection enrichment={BASE_ENRICHMENT} />);

    expect(screen.queryByText('정책 개요')).toBeNull();
    expect(screen.queryByText('지원자격')).toBeNull();
    expect(screen.queryByText('운영기관')).toBeNull();
    expect(screen.queryByText('문의처')).toBeNull();
  });
});
