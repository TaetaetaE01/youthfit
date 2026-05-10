import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QnaSourceItem } from '../QnaSourceItem';

describe('QnaSourceItem', () => {
  it('첨부파일명과 페이지 범위를 한 줄로 표시한다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '청년정책 시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: '본 사업은 만 19~34세...',
        }}
      />,
    );

    expect(screen.getByText(/청년정책 시행계획/)).toBeInTheDocument();
    expect(screen.getByText(/p\.12-13/)).toBeInTheDocument();
  });

  it('pageStart 와 pageEnd 가 같으면 단일 페이지로 표시', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 5,
          pageEnd: 5,
          excerpt: '본문',
        }}
      />,
    );
    expect(screen.getByText(/p\.5\b/)).toBeInTheDocument();
    expect(screen.queryByText(/p\.5-5/)).not.toBeInTheDocument();
  });

  it('attachmentLabel 이 null 이면 "정책 #{policyId}"로 표시', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 42,
          attachmentLabel: null,
          pageStart: null,
          pageEnd: null,
          excerpt: null,
        }}
      />,
    );
    expect(screen.getByText(/정책 #42/)).toBeInTheDocument();
  });

  it('excerpt 가 있으면 펼치기 토글 버튼이 렌더되고 클릭 시 발췌가 표시된다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: '본 사업은 만 19~34세 무주택 청년 중...',
        }}
      />,
    );
    const button = screen.getByRole('button', { name: /발췌/ });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText(/본 사업은 만 19~34세/)).not.toBeInTheDocument();

    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/본 사업은 만 19~34세/)).toBeInTheDocument();
  });

  it('excerpt 가 null 이면 펼치기 토글 버튼이 렌더되지 않는다', () => {
    render(
      <QnaSourceItem
        source={{
          policyId: 1,
          attachmentLabel: '시행계획',
          pageStart: 12,
          pageEnd: 13,
          excerpt: null,
        }}
      />,
    );
    expect(screen.queryByRole('button', { name: /발췌/ })).not.toBeInTheDocument();
  });
});
