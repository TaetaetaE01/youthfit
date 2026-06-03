import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SourceBadges } from '../SourceBadges';

describe('SourceBadges', () => {
  it('출처가 여러 개면 전부 한글 라벨로 렌더한다', () => {
    render(
      <SourceBadges
        sources={[
          { code: 'YOUTH_SEOUL_CRAWL', label: '청년몽땅정보통' },
          { code: 'BOKJIRO_CENTRAL', label: '복지로' },
        ]}
      />,
    );
    expect(screen.getByText('청년몽땅정보통')).toBeInTheDocument();
    expect(screen.getByText('복지로')).toBeInTheDocument();
  });

  it('출처가 없으면 출처없음 뱃지를 렌더한다', () => {
    render(<SourceBadges sources={[]} />);
    expect(screen.getByText('출처없음')).toBeInTheDocument();
  });

  it('알 수 없는 코드는 라벨을 그대로 중립 색으로 렌더한다', () => {
    render(<SourceBadges sources={[{ code: 'UNKNOWN', label: '기타' }]} />);
    expect(screen.getByText('기타')).toBeInTheDocument();
  });
});
