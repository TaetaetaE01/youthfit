import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyApplyCta } from './PolicyApplyCta';

describe('PolicyApplyCta', () => {
  it('applyUrl 이 있으면 applyUrl 로 링크된다', () => {
    render(<PolicyApplyCta applyUrl="https://job.seoul.go.kr/apply" sourceUrl="https://ref.example.com" />);
    const link = screen.getByRole('link', { name: /공식 신청 페이지/ });
    expect(link).toHaveAttribute('href', 'https://job.seoul.go.kr/apply');
  });

  it('applyUrl 이 없으면 sourceUrl 로 폴백한다', () => {
    render(<PolicyApplyCta applyUrl={null} sourceUrl="https://ref.example.com" />);
    expect(screen.getByRole('link', { name: /공식 신청 페이지/ })).toHaveAttribute(
      'href',
      'https://ref.example.com',
    );
  });

  it('빈 문자열 applyUrl 은 폴백 처리된다', () => {
    render(<PolicyApplyCta applyUrl="" sourceUrl="https://ref.example.com" />);
    expect(screen.getByRole('link', { name: /공식 신청 페이지/ })).toHaveAttribute(
      'href',
      'https://ref.example.com',
    );
  });

  it('applyUrl 도 sourceUrl 도 없으면 아무것도 렌더하지 않는다', () => {
    render(<PolicyApplyCta applyUrl={null} sourceUrl={null} />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('데스크톱 전용(모바일 하단바와 중복 방지): hidden lg:flex 적용', () => {
    render(<PolicyApplyCta applyUrl="https://job.seoul.go.kr/apply" />);
    const link = screen.getByRole('link', { name: /공식 신청 페이지/ });
    expect(link).toHaveClass('hidden');
    expect(link).toHaveClass('lg:flex');
  });
});
