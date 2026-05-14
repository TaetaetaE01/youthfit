import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GuideListSectionCard } from './GuideListSectionCard';
import type { GuideListSection } from '@/types/policy';

describe('GuideListSectionCard', () => {
  it('section 이 null 이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(
      <GuideListSectionCard title="신청방법" emoji="📝" section={null} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('items 가 여러 개면 불릿 리스트로 렌더', () => {
    const section: GuideListSection = {
      items: ['1단계', '2단계', '3단계'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="신청방법" emoji="📝" section={section} />);
    expect(screen.getByText('신청방법')).toBeInTheDocument();
    expect(screen.getByText('1단계')).toBeInTheDocument();
    expect(screen.getByText('2단계')).toBeInTheDocument();
    expect(screen.getByText('3단계')).toBeInTheDocument();
  });

  it('items 가 1개면 불릿 없이 한 줄로 렌더', () => {
    const section: GuideListSection = {
      items: ['2026-03-01 ~ 2026-05-31'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="신청기한" emoji="📅" section={section} />);
    expect(screen.getByText('2026-03-01 ~ 2026-05-31')).toBeInTheDocument();
  });

  it('sourceField=ENRICHMENT 면 AI 자동 수집 배지를 헤더에 노출', () => {
    const section: GuideListSection = {
      items: ['주민등록등본'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="제출서류" emoji="📂" section={section} />);
    expect(screen.getByText('AI 자동 수집')).toBeInTheDocument();
  });

  it('enrichmentSourceUrl 이 있으면 AI 자동 수집 배지 title 에 URL 이 포함됨', () => {
    const section: GuideListSection = {
      items: ['주민등록등본'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(
      <GuideListSectionCard
        title="제출서류"
        emoji="📂"
        section={section}
        enrichmentSourceUrl="https://example.gov.kr/policy/1"
      />
    );
    const badge = screen.getByText('AI 자동 수집');
    const title = badge.getAttribute('title') ?? badge.parentElement?.getAttribute('title') ?? '';
    expect(title).toContain('example.gov.kr');
  });

  it('sourceField=SUPPORT_CONTENT 면 AI 자동 수집 배지 없음', () => {
    const section: GuideListSection = {
      items: ['연락처'],
      sourceField: 'SUPPORT_CONTENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="문의처" emoji="☎" section={section} />);
    expect(screen.queryByText('AI 자동 수집')).not.toBeInTheDocument();
  });
});
